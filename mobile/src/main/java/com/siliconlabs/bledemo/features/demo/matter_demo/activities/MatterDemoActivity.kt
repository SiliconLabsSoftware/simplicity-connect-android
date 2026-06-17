package com.siliconlabs.bledemo.features.demo.matter_demo.activities

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import chip.devicecontroller.ChipDeviceController
import chip.devicecontroller.GetConnectedDeviceCallbackJni
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.databinding.ActivityMatterDemoBinding
import com.siliconlabs.bledemo.features.demo.matter_demo.dishwasher_demo.view.MatterDishwasherFragment
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterDoorFragment
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterLightFragment
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterOccupancySensorFragment
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterOvenFragment
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterRangeHoodFragment
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannerFragment
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterThermostatFragment
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterWifiInputDialogFragment
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterWindowCoverFragment
import com.siliconlabs.bledemo.features.demo.matter_demo.model.MatterScannedResultModel
import com.siliconlabs.bledemo.features.demo.matter_demo.model.ProvisionNetworkType
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.ChipClient
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.CustomProgressDialog
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.SharedPrefsUtils
import com.siliconlabs.bledemo.utils.AppUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@AndroidEntryPoint
class MatterDemoActivity : AppCompatActivity(),
    MatterScannerFragment.CallBack,
    MatterScannedResultFragment.Callback,
    MatterThermostatFragment.CallBackHandler,
    MatterLightFragment.CallBackHandler,
    MatterWifiInputDialogFragment.CallBackHandler,
    MatterDoorFragment.CallBackHandler,
    MatterWindowCoverFragment.CallBackHandler,
    MatterOccupancySensorFragment.CallBackHandler,
    MatterOvenFragment.CallBackHandler,
    MatterRangeHoodFragment.CallBackHandler {

    private lateinit var binding: ActivityMatterDemoBinding
    private var scannedDeviceList = ArrayList<MatterScannedResultModel>()
    private lateinit var mPrefs: SharedPreferences
    private lateinit var deviceController: ChipDeviceController
    private var customProgressDialog: CustomProgressDialog? = null
    private var networkType: ProvisionNetworkType? = null
    private var shouldContinueOperation = true
    private var count: Int = 0
    // Throttle / debounce maps to avoid spamming connection attempts & logs
    private val lastFailureTimeMs = mutableMapOf<Long, Long>()
    private val lastAttemptTimeMs = mutableMapOf<Long, Long>()
    private var pollInProgress = false
    private var pollJob: Job? = null

    // Backoff settings (tune as needed)
    private val failureBackoffMs = 15_000L // wait 15s after a failure before retrying that node
    private val attemptMinIntervalMs = 3_000L // don't attempt the same node more than once every 3s


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.tag(TAG).e(" onCreate")
        binding = ActivityMatterDemoBinding.inflate(layoutInflater)
        mPrefs = this.getSharedPreferences("your_preference_name", MODE_PRIVATE)
        setContentView(binding.root)
        AppUtil.setEdgeToEdge(window,this)
        setSupportActionBar(binding.toolbar)
        deviceController = ChipClient.getDeviceController(this)

        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setHomeAsUpIndicator(R.drawable.matter_back)
            actionBar.setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.matter_demo_title)
        }
        binding.scanQRCode.setOnClickListener {
            val fragment = MatterScannerFragment.newInstance()
            showFragment(
                fragment, fragment::class.java.simpleName
            )
        }
        binding.refresh.setOnClickListener {
            count = 0
            if (SharedPrefsUtils.retrieveSavedDevices(mPrefs).isNotEmpty()) {
                showMatterProgressDialog(getString(R.string.matter_device_status))
                // Cancel any existing poll and force immediate parallel refresh
                pollJob?.cancel()
                performLongRunningOperation(force = true)
            }
        }
        startPeriodictFunction()
        sendToScanResultFragment()
    }

    private var periodicJob: Job? = null
    
    private fun startPeriodictFunction() {
        periodicJob = CoroutineScope(Dispatchers.Default).launch {
            while (shouldContinueOperation) {
                performLongRunningOperation()
                delay(DELAY_TIMEOUT)
            }
        }
    }

    private fun stopPeriodicOperation() {
        shouldContinueOperation = false
        periodicJob?.cancel()
        periodicJob = null
        pollJob?.cancel()
        pollJob = null
        pollInProgress = false
    }

    fun stopMatterScanning() {
        Timber.tag(TAG).d("Stopping Matter scanning")
        stopPeriodicOperation()
        ChipClient.stopDnssd(this)
        removeProgress()
    }

    private fun prepareList(scannedDeviceListPrep: ArrayList<MatterScannedResultModel>) {
        val bundle = Bundle()
        bundle.putParcelableArrayList(ARG_DEVICE_LIST, scannedDeviceListPrep)
        val fragment = MatterScannedResultFragment.newInstance()
        fragment.arguments = bundle
        supportFragmentManager.beginTransaction().replace(
            binding.matterContainer.id,
            fragment,
            fragment.javaClass.simpleName
        ).addToBackStack(null).commit()
    }

    private fun removeProgress() {
        if (customProgressDialog?.isShowing == true) {
            customProgressDialog?.dismiss()
        }
    }

    private fun showMatterProgressDialog(message: String) {
        customProgressDialog = CustomProgressDialog(this)
        customProgressDialog!!.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        customProgressDialog!!.setMessage(message)
        customProgressDialog!!.setCanceledOnTouchOutside(false)
        customProgressDialog!!.show()

    }

    override fun onResume() {
        super.onResume()
        Timber.tag(TAG).e(" onResume")
        ChipClient.startDnssd(this)
        count = 0
    }

    override fun onPause() {
        super.onPause()
        Timber.tag(TAG).e(" onPause")
        stopMatterScanning()
    }

    override fun onStart() {
        super.onStart()
        Timber.tag(TAG).e(" onStart")

    }


    // Provide ability to force a poll (manual refresh) ignoring backoff/debounce.
    // Forced mode: runs probes in parallel to minimize total latency.
    private fun performLongRunningOperation(force: Boolean = false) {
        if (force) {
            // Cancel any in-flight poll first for clean parallel attempts
            pollJob?.cancel()
            pollInProgress = false
        } else if (pollInProgress) {
            Timber.tag(TAG).v("Skip poll: already running")
            return
        }
        val savedDevices = SharedPrefsUtils.retrieveSavedDevices(mPrefs)
        if (savedDevices.isEmpty()) {
            removeProgress()
            return
        }
        pollInProgress = true
        pollJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                if (force) {
                    // Parallel probes
                    coroutineScope {
                        savedDevices.forEach { dev ->
                            launch { getStatus(dev.deviceId, forceAttempt = true) }
                        }
                    }
                } else {
                    // Sequential probes (periodic)
                    for (dev in savedDevices) {
                        getStatus(dev.deviceId, forceAttempt = false)
                    }
                }
            } finally {
                pollInProgress = false
                removeProgress()
            }
        }
    }

    private suspend fun getStatus(deviceId: Long, forceAttempt: Boolean = false) = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        if (!forceAttempt) {
            val lastFail = lastFailureTimeMs[deviceId] ?: 0L
            if (now - lastFail < failureBackoffMs) {
                Timber.tag(TAG).d("Skipping device $deviceId (backoff ${(failureBackoffMs - (now - lastFail))}ms left)")
                return@withContext
            }
            val lastAttempt = lastAttemptTimeMs[deviceId] ?: 0L
            if (now - lastAttempt < attemptMinIntervalMs) {
                Timber.tag(TAG).v("Debouncing attempt for $deviceId (interval ${(attemptMinIntervalMs - (now - lastAttempt))}ms left)")
                return@withContext
            }
        } else {
            // Forced attempt: clear previous failure timing so item can re-enable instantly
            lastFailureTimeMs[deviceId] = 0L
        }
        lastAttemptTimeMs[deviceId] = now
        try {
            suspendCoroutine { continuation ->
                deviceController.getConnectedDevicePointer(deviceId, object : GetConnectedDeviceCallbackJni.GetConnectedDeviceCallback {
                    override fun onDeviceConnected(devicePointer: Long) {
                        SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, true)
                        updateListIfVisibleSingle(deviceId, true)
                        continuation.resume(Unit)
                    }
                    override fun onConnectionFailure(nodeId: Long, error: java.lang.Exception?) {
                        Timber.tag(TAG).w("nodeId $nodeId connection failure: ${error?.message}")
                        markDeviceOffline(deviceId)
                        lastFailureTimeMs[deviceId] = System.currentTimeMillis()
                        continuation.resume(Unit)
                    }
                })
            }
        } catch (ex: Exception) {
            Timber.tag(TAG).e(ex, "Unexpected exception probing device $deviceId")
            markDeviceOffline(deviceId)
        }
    }

    private fun updateListIfVisibleSingle(deviceId: Long, isOnline: Boolean) {
        val fragment = supportFragmentManager.findFragmentById(R.id.matter_container) as? com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment
        fragment?.applyDeviceOnlineStatus(deviceId, isOnline)
    }

    // Removed heavy fragment recreation from incremental updates; keep method for full rebuild if needed elsewhere.
    private fun updateListIfVisibleFull() {
        val fragment = supportFragmentManager.findFragmentById(R.id.matter_container)
        if (fragment is com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment) {
            fragment.refreshAllFromPrefs()
        }
    }

    private fun markDeviceOffline(deviceId: Long) {
        SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, false)
        updateListIfVisibleSingle(deviceId, false)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPeriodicOperation()
    }

    fun hideQRScanner() {
        binding.scanQRCode.visibility = View.GONE
        binding.refresh.visibility = View.GONE
    }

    fun showQRScanner() {
        binding.scanQRCode.visibility = View.VISIBLE
        binding.refresh.visibility = View.VISIBLE
    }

    private fun showFragment(
        fragment: Fragment, tag: String? = null,
    ) {
        val fManager = supportFragmentManager
        val fTransaction = fManager.beginTransaction()

        fTransaction.replace(binding.matterContainer.id, fragment, tag)
            .addToBackStack(null)
            .commit()

    }


    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(MatterScannerFragment.ARG_PROVISION_NETWORK_TYPE, networkType?.name)

        super.onSaveInstanceState(outState)
    }


    override fun onCommissionCompleteLoadData(matterScannedResultModel: MatterScannedResultModel) {
        Timber.tag(TAG).e("on commission complete add $matterScannedResultModel ")
        scannedDeviceList.add(matterScannedResultModel)
        SharedPrefsUtils.saveDevicesToPref(mPrefs, scannedDeviceList)
        sendToScanResultFragment()

    }

    private fun sendToScanResultFragment() {
        val bundle = Bundle()
        scannedDeviceList = SharedPrefsUtils.retrieveSavedDevices(mPrefs)
        bundle.putParcelableArrayList(ARG_DEVICE_LIST, scannedDeviceList)
        val fragment = MatterScannedResultFragment.newInstance()
        fragment.arguments = bundle
        supportFragmentManager.beginTransaction().replace(
            binding.matterContainer.id,
            fragment,
            fragment.javaClass.simpleName
        ).commit()
    }

    override fun navigateToDemo(
        matterDemoFragment: Fragment,
        model: MatterScannedResultModel
    ) {
        val bundle = Bundle()
        bundle.putParcelable(ARG_DEVICE_MODEL, model)
        matterDemoFragment.arguments = bundle

        supportFragmentManager.beginTransaction().replace(
            binding.matterContainer.id,
            matterDemoFragment,
            matterDemoFragment.javaClass.simpleName
        ).addToBackStack(null).commit()
    }


    private fun backHandler() {
        val bundle = Bundle()
        scannedDeviceList = SharedPrefsUtils.retrieveSavedDevices(mPrefs)
        bundle.putParcelableArrayList(ARG_DEVICE_LIST, scannedDeviceList)

        val fragment = MatterScannedResultFragment.newInstance()
        fragment.arguments = bundle
        supportFragmentManager.beginTransaction().replace(
            binding.matterContainer.id,
            fragment,
            fragment.javaClass.simpleName
        ).addToBackStack(null).commit()
    }


    override fun onBackHandler() {
        backHandler()
    }


    private fun processHomeAsUpAction() {
        val top = supportFragmentManager.findFragmentById(R.id.matter_container)
        when {
            top is MatterScannedResultFragment -> finish()
            top is MatterDishwasherFragment -> top.handleDishwasherBackNavigationUI()
            supportFragmentManager.backStackEntryCount > 0 -> supportFragmentManager.popBackStack()
            else -> finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        processHomeAsUpAction()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                processHomeAsUpAction()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val TAG = "MatterDemoActivity"
        private const val ARG_DEVICE_LIST = "device_list"
        private const val ARG_DEVICE_MODEL = "device_model"
        private const val DELAY_TIMEOUT = 8000L
        const val MATTER_PREF = "your_preference_name"
    }
}
