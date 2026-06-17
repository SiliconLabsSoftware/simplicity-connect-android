package com.siliconlabs.bledemo.features.demo.matter_demo.evse.ui

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import chip.devicecontroller.ChipClusters
import chip.devicecontroller.ChipDeviceController
import chip.devicecontroller.GetConnectedDeviceCallbackJni
import chip.devicecontroller.ChipStructs
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.features.demo.matter_demo.activities.MatterDemoActivity
import com.siliconlabs.bledemo.features.demo.matter_demo.evse.presentation.EVViewModel
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterLightFragment.Companion.ARG_DEVICE_MODEL
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterLightFragment.Companion.INIT
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterLightFragment.Companion.ON_OFF_CLUSTER_ENDPOINT
import com.siliconlabs.bledemo.features.demo.matter_demo.model.MatterScannedResultModel
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.ChipClient
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.CustomProgressDialog
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.FragmentUtils
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.MessageDialogFragment
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.SharedPrefsUtils
import com.siliconlabs.bledemo.bluetooth.beacon_utils.eddystone.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@AndroidEntryPoint
class EVFragment : Fragment() {

    private val vm: EVViewModel by viewModels()

    private lateinit var mPrefs: SharedPreferences
    private lateinit var model: MatterScannedResultModel
    private var deviceId: Long = INIT
    private var endpointId: Int = ON_OFF_CLUSTER_ENDPOINT
    private var customProgressDialog: CustomProgressDialog? = null
    private lateinit var dialog: MessageDialogFragment
    private val dialogTag = "MessageDialog"

    private val deviceController: ChipDeviceController
        get() = ChipClient.getDeviceController(requireContext())

    // Compose state holder for mode cluster once available.
    private val energyEvseModeClusterState = mutableStateOf<ChipClusters.EnergyEvseModeCluster?>(null)
    // State holder for main EnergyEvseCluster (used for VehicleID + StateOfCharge + State)
    private val energyEvseClusterState = mutableStateOf<ChipClusters.EnergyEvseCluster?>(null)

    // Track if we've already processed an offline condition to avoid duplicate dialogs
    private var isDeviceMarkedOffline = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        fitsSystemWindows = true
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        mPrefs = requireContext().getSharedPreferences(
            MatterDemoActivity.MATTER_PREF,
            AppCompatActivity.MODE_PRIVATE
        )
        if (arguments != null) {
            val argModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requireArguments().getParcelable(ARG_DEVICE_MODEL, MatterScannedResultModel::class.java)
            } else {
                @Suppress("DEPRECATION")
                requireArguments().getParcelable(ARG_DEVICE_MODEL)
            }
            model = argModel!!
            deviceId = model.deviceId
            Timber.tag(TAG).e("deviceID: $model")
        }
        // Only proceed if we have a valid device id
        if (deviceId != INIT) {
            showMatterProgressDialog(getString(R.string.matter_device_status))
            lifecycleScope.launch(Dispatchers.IO) { // use fragment lifecycleScope
                val resInfo = checkForDeviceStatus()
                if (resInfo) {
                    removeProgress()
                }
            }
        }
        // back handling using fragment as LifecycleOwner
        requireActivity().onBackPressedDispatcher.addCallback(
            this@EVFragment,
            onBackPressedCallback
        )
        (activity as MatterDemoActivity).hideQRScanner()
        setContent {
            val modeCluster = energyEvseModeClusterState.value
            val evseCluster = energyEvseClusterState.value
            val uiState by vm.ui.collectAsState()

            // React to offline from ViewModel flow (watchdog / polling failures)
            LaunchedEffect(uiState.isOffline) {
                if (uiState.isOffline && !isDeviceMarkedOffline) {
                    handleOfflineFromViewModel()
                }
            }

            // Trigger vehicle ID read when cluster becomes available
            LaunchedEffect(evseCluster) {
                evseCluster?.let { readVehicleId(it) }
            }
            // Start observing StateOfCharge when cluster available (idempotent inside VM)
            LaunchedEffect(evseCluster) {
                evseCluster?.let { vm.startObservingStateOfCharge(it); vm.startObservingChargingState(it) }
            }

            // Once mode cluster becomes available, read SupportedModes + CurrentMode.
            LaunchedEffect(modeCluster) {
                modeCluster?.let { c ->
                    try {
                        c.readSupportedModesAttribute(object : ChipClusters.EnergyEvseModeCluster.SupportedModesAttributeCallback {
                            override fun onSuccess(value: MutableList<ChipStructs.EnergyEvseModeClusterModeOptionStruct>?) {
                                Timber.d("Supported Modes: $value")
                                val byId = linkedMapOf<Int, String>()
                                value?.forEach { opt ->
                                    val label = opt.label ?: return@forEach
                                    val modeId: Int? = try {
                                        val direct = try {
                                            val f = opt.javaClass.getDeclaredField("mode")
                                            f.isAccessible = true
                                            (f.get(opt) as? Int)
                                        } catch (_: Exception) { null }
                                        direct ?: run {
                                            val m = opt.javaClass.methods.firstOrNull { it.name.startsWith("getMode") }
                                            (m?.invoke(opt) as? Int)
                                        }
                                    } catch (_: Exception) {
                                        Timber.w("Unable to extract mode id from option struct")
                                        null
                                    }
                                    if (modeId != null) byId[modeId] = label
                                }
                                vm.setSupportedModeLabels(byId)
                                // After labels loaded attempt reading current mode (if not yet known)
                                if (vm.ui.value.currentModeId == null) {
                                    readCurrentMode(c)
                                }
                            }
                            override fun onError(error: Exception?) {
                                Timber.e(error, "Failed to read SupportedModes attribute")
                                handleOfflineFromClusterError(error, "readSupportedModesAttribute")
                                // Still attempt to read current mode even if supported modes failed (if not offline)
                                if (!isDeviceMarkedOffline) readCurrentMode(c)
                            }
                        })
                    } catch (e: Exception) {
                        Timber.e(e, "Exception invoking readSupportedModesAttribute")
                        handleOfflineFromClusterError(e, "readSupportedModesAttribute invocation")
                        if (!isDeviceMarkedOffline) readCurrentMode(c)
                    }
                    // Also trigger an initial current mode read immediately (race-safe)
                    if (!isDeviceMarkedOffline) readCurrentMode(c)
                }
            }

            // Show EV screen whenever mode cluster is available, even if offline dialog is showing.
            if (modeCluster != null) {
                // UI once cluster is ready
                EVScreen(vm, modeCluster, onSelectMode = { modeId ->
                    changeEvseMode(modeId, modeCluster)
                })
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        energyEvseModeClusterState.value?.let { readCurrentMode(it) }
        energyEvseClusterState.value?.let { readVehicleId(it) }
    }

    private fun changeEvseMode(modeId: Int, cluster: ChipClusters.EnergyEvseModeCluster) {
        if (isDeviceMarkedOffline) return
        // Log/print the requested new mode id before sending command
        val label = vm.ui.value.modeLabels[modeId]
        Timber.i("Invoking changeToMode with newModeId=$modeId label=$label")
        println("EVSE changeToMode request newModeId=$modeId label=$label")
        showMatterProgressDialog(getString(R.string.changing_mode_message).ifEmpty { "Changing mode..." })
        try {
            cluster.changeToMode(object : ChipClusters.EnergyEvseModeCluster.ChangeToModeResponseCallback {
                override fun onSuccess(status: Int, statusText: java.util.Optional<String?>) {
                    Timber.d("changeToMode onSuccess status=$status text=${statusText.orElse("")} newModeId=$modeId")
                    println("EVSE changeToMode success newModeId=$modeId status=$status message=${statusText.orElse("")}")
                    removeProgress()
                    if (status == 0) { // assuming 0 = success
                        vm.selectModeId(modeId)
                    } else {
                        Timber.w("Mode change reported non-zero status=$status for newModeId=$modeId")
                        handleOfflineFromClusterError(null, "changeToMode non-zero status=$status")
                    }
                }
                override fun onError(error: Exception?) {
                    Timber.e(error, "changeToMode failed for newModeId=$modeId")
                    println("EVSE changeToMode error newModeId=$modeId error=${error?.message}")
                    removeProgress()
                    handleOfflineFromClusterError(error, "changeToMode")
                }
            }, modeId)
        } catch (e: Exception) {
            Timber.e(e, "Exception invoking changeToMode newModeId=$modeId")
            println("EVSE changeToMode exception newModeId=$modeId ex=${e.message}")
            removeProgress()
            handleOfflineFromClusterError(e, "changeToMode invocation")
        }
    }

    private suspend fun checkForDeviceStatus(): Boolean {
        return withContext(Dispatchers.Default) {
            deviceController.getConnectedDevicePointer(deviceId, object :
                GetConnectedDeviceCallbackJni.GetConnectedDeviceCallback {
                override fun onDeviceConnected(devicePointer: Long) {
                    if (isDeviceMarkedOffline) return
                    model.isDeviceOnline = true
                    SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, true)
                    try {
                        energyEvseModeClusterState.value = ChipClusters.EnergyEvseModeCluster(
                            devicePointer,
                            endpointId
                        )
                        energyEvseClusterState.value = ChipClusters.EnergyEvseCluster(
                            devicePointer,
                            endpointId
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to create clusters")
                        handleOfflineFromClusterError(e, "cluster initialization")
                    }
                }

                override fun onConnectionFailure(nodeId: Long, error: java.lang.Exception?) {
                    model.isDeviceOnline = false
                    // Restore previous behavior: dismiss progress immediately
                    removeProgress()
                    SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, false)
                    handleOfflineFromClusterError(error, "connectionFailure")
                }
            })
            // Wait a short period to allow callback to update state (mirroring existing pattern)
            delay(Constants.SCAN_TIMER * 500)
            true
        }
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (requireActivity().supportFragmentManager.backStackEntryCount > 0) {
                requireActivity().supportFragmentManager.popBackStack()
            } else {
                FragmentUtils.getHost(
                    this@EVFragment,
                    CallBackHandler::class.java
                ).onBackHandler()
            }
        }
    }

    private fun showMessageDialog() {
        try {
            if (isAdded && !requireActivity().isFinishing) {
                requireActivity().runOnUiThread {
                    if (!MessageDialogFragment.isDialogShowing()) {
                        dialog = MessageDialogFragment()
                        dialog.setMessage(getString(R.string.matter_device_offline_text))
                        dialog.setOnDismissListener {
                            // Previous behavior: ensure progress is already gone; just navigate back
                            removeProgress()
                            if (requireActivity().supportFragmentManager.backStackEntryCount > 0) {
                                requireActivity().supportFragmentManager.popBackStack()
                            } else {
                                FragmentUtils.getHost(
                                    this@EVFragment,
                                    CallBackHandler::class.java
                                ).onBackHandler()
                            }
                        }
                        dialog.show(
                            requireActivity().supportFragmentManager.beginTransaction(),
                            dialogTag
                        )
                    }
                }
            } else {
                Timber.e("device offline")
            }
        } catch (e: Exception) {
            Timber.e("device offline $e")
        }
    }

    private fun handleOfflineFromViewModel() {
        if (isDeviceMarkedOffline) return
        Timber.w("Device marked offline from ViewModel flow state")
        isDeviceMarkedOffline = true
        model.isDeviceOnline = false
        try { SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, false) } catch (_: Exception) {}
        // Keep existing EV UI visible; remove any progress overlay
        removeProgress()
        showMessageDialog()
    }

    private fun handleOfflineFromClusterError(error: Exception?, action: String) {
        if (isDeviceMarkedOffline) return
        Timber.w(error, "Marking device offline due to cluster error during $action")
        isDeviceMarkedOffline = true
        model.isDeviceOnline = false
        try {
            SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, false)
        } catch (e: Exception) {
            Timber.e(e, "Failed to persist offline state for deviceId=$deviceId")
        }
        // Keep existing EV UI visible; remove any progress overlay
        removeProgress()
        showMessageDialog()
    }

    private fun removeProgress() {
        if (customProgressDialog?.isShowing == true) {
            customProgressDialog?.dismiss()
        }
    }

    private fun showMatterProgressDialog(message: String) {
        customProgressDialog = CustomProgressDialog(requireContext())
        customProgressDialog!!.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        customProgressDialog!!.setMessage(message)
        customProgressDialog!!.setCanceledOnTouchOutside(false)
        customProgressDialog!!.show()
    }

    private fun readCurrentMode(cluster: ChipClusters.EnergyEvseModeCluster) {
        if (isDeviceMarkedOffline) return
        try {
            cluster.readCurrentModeAttribute(object : ChipClusters.IntegerAttributeCallback {
                override fun onSuccess(value: Int) {
                    Timber.i("CurrentMode read from device: $value label=${vm.ui.value.modeLabels[value]}")
                    println("EVSE CurrentMode attribute value=$value label=${vm.ui.value.modeLabels[value]}")
                    vm.updateCurrentModeFromDevice(value)
                }
                override fun onError(error: Exception?) {
                    Timber.e(error, "Failed to read CurrentMode attribute")
                    handleOfflineFromClusterError(error, "readCurrentModeAttribute")
                }
            })
        } catch (e: Exception) {
            Timber.e(e, "Exception calling readCurrentModeAttribute")
            handleOfflineFromClusterError(e, "readCurrentModeAttribute invocation")
        }
    }

    private fun readVehicleId(cluster: ChipClusters.EnergyEvseCluster) {
        if (isDeviceMarkedOffline) return
        try {
            cluster.readVehicleIDAttribute(object : ChipClusters.EnergyEvseCluster.VehicleIDAttributeCallback {
                override fun onSuccess(value: String?) {
                    Timber.i("VehicleID attribute read: $value")
                    println("EVSE VehicleID attribute value=$value")
                    vm.setVehicleId(value)
                }
                override fun onError(error: Exception?) {
                    Timber.e(error, "Failed to read VehicleID attribute")
                    handleOfflineFromClusterError(error, "readVehicleIDAttribute")
                }
            })
        } catch (e: Exception) {
            Timber.e(e, "Exception calling readVehicleIDAttribute")
            handleOfflineFromClusterError(e, "readVehicleIDAttribute invocation")
        }
    }

    interface CallBackHandler {
        fun onBackHandler()
    }

    companion object {
        private val TAG = EVFragment::class.java.simpleName
        fun newInstance(): EVFragment = EVFragment()
    }
}
