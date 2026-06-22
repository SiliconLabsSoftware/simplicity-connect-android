package com.siliconlabs.bledemo.features.demo.matter_demo.fragments

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import chip.devicecontroller.ChipClusters
import chip.devicecontroller.ChipDeviceController
import chip.devicecontroller.GetConnectedDeviceCallbackJni
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.bluetooth.beacon_utils.eddystone.Constants.SCAN_TIMER
import com.siliconlabs.bledemo.databinding.FragmentMatterRangeHoodBinding
import com.siliconlabs.bledemo.features.demo.matter_demo.activities.MatterDemoActivity
import com.siliconlabs.bledemo.features.demo.matter_demo.model.MatterScannedResultModel
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.ChipClient
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.CustomProgressDialog
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.FragmentUtils
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.MessageDialogFragment
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.SharedPrefsUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class MatterRangeHoodFragment : Fragment() {
    private lateinit var dialog: MessageDialogFragment
    private val dialogTag = "MessageDialog"
    private val deviceController: ChipDeviceController
        get() = ChipClient.getDeviceController(requireContext())
    private lateinit var mPrefs: SharedPreferences

    private lateinit var scope: CoroutineScope
    private lateinit var binding: FragmentMatterRangeHoodBinding
    private var deviceId: Long = INIT
    private var onOffEndpointId: Int = ON_OFF_CLUSTER_ENDPOINT
    private lateinit var model: MatterScannedResultModel
    private var customProgressDialog: CustomProgressDialog? = null
    private var isRangeHoodOn = false
    private var isFanOn = false
    private var selectedFanSpeed: Int = 1
    private var fanControlEndpointId: Int = FAN_CONTROL_CLUSTER_ENDPOINT
    private var fanStatusPollingJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mPrefs = requireContext().getSharedPreferences(
            MatterDemoActivity.MATTER_PREF, AppCompatActivity.MODE_PRIVATE
        )
        if (requireArguments() != null) {
            model =
                requireArguments().getParcelable(MatterLightFragment.Companion.ARG_DEVICE_MODEL)!!
            deviceId = model.deviceId
            Timber.tag(TAG).e("deviceID: $model")
        }
        showMatterProgressDialog(getString(R.string.matter_device_status))
        CoroutineScope(Dispatchers.IO).launch {
            val resultInfo = checkForDeviceStatus()
            withContext(Dispatchers.Main) {
                if (resultInfo) {
                    println("Operation was successful")
                    removeProgress()
                }
            }
        }
    }

    private suspend fun checkForDeviceStatus(): Boolean {
        return withContext(Dispatchers.Default) {
            deviceController.getConnectedDevicePointer(
                deviceId,
                object : GetConnectedDeviceCallbackJni.GetConnectedDeviceCallback {
                    override fun onDeviceConnected(devicePointer: Long) {
                        println("----DevicePointer:$devicePointer")
                        model.isDeviceOnline = true
                        SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, true)
                    }

                    override fun onConnectionFailure(nodeId: Long, error: java.lang.Exception?) {
                        model.isDeviceOnline = false
                        removeProgress()
                        println("----nodeId:$nodeId")
                        println("----error:${error!!.message}")
                        SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, false)
                        showMessageDialog()
                    }
                })

            delay(SCAN_TIMER * COUNTDOWN_INTERVAL)
            true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = FragmentMatterRangeHoodBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scope = viewLifecycleOwner.lifecycleScope
        (activity as MatterDemoActivity).hideQRScanner()

        // Selection is visual (pill styles); both toggles stay enabled.
        binding.btnRangeHoodOn.isEnabled = true
        binding.btnRangeHoodOff.isEnabled = true
        binding.btnFanOn.isEnabled = true
        binding.btnFanOff.isEnabled = true

        setupPowerControls()
        setupFanControls()
        setupFanSpeedButtons()
        updateUI()

        // Read initial state from device
        scope.launch {
            readRangeHoodState()
            readFanState()
            readFanSpeed()
        }

        // Start background polling task for Fan status
        startFanStatusPolling()

        // Back handling
        requireActivity().onBackPressedDispatcher.addCallback(
            requireActivity(), onBackPressedCallback
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Stop background polling when view is destroyed
        stopFanStatusPolling()
    }

    /**
     * Start background task to periodically read Fan status and update UI
     */
    private fun startFanStatusPolling() {
        stopFanStatusPolling() // Ensure no duplicate polling jobs
        
        fanStatusPollingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    // Read the current Fan status from the device
                    readFanState()
                    
                    // Wait before next poll (e.g., every 5 seconds)
                    delay(FAN_STATUS_POLL_INTERVAL_MS)
                } catch (e: Exception) {
                    Timber.tag(TAG).e("Error in fan status polling: ${e.message}")
                    // Continue polling even if there's an error
                    delay(FAN_STATUS_POLL_INTERVAL_MS)
                }
            }
        }
    }

    /**
     * Stop the background fan status polling task
     */
    private fun stopFanStatusPolling() {
        fanStatusPollingJob?.cancel()
        fanStatusPollingJob = null
    }

    private fun setupPowerControls() {
        binding.btnRangeHoodOn.setOnClickListener {
            scope.launch {
                sendOnCommandClick()
            }
        }

        binding.btnRangeHoodOff.setOnClickListener {
            scope.launch {
                sendOffCommandClick()
            }
        }
    }

    private fun setupFanControls() {
        binding.btnFanOn.setOnClickListener {
            scope.launch {
                sendFanOnCommandClick()
            }
        }

        binding.btnFanOff.setOnClickListener {
            scope.launch {
                sendFanOffCommandClick()
            }
        }
    }

    private fun setupFanSpeedButtons() {
        val fanSpeedButtons = mapOf(
            1 to binding.btnFanSpeed1,
            2 to binding.btnFanSpeed2,
            3 to binding.btnFanSpeed3,
            4 to binding.btnFanSpeed4
        )

        fanSpeedButtons.forEach { (speed, button) ->
            button.setOnClickListener {
                selectedFanSpeed = speed
                updateFanSpeedUI(fanSpeedButtons)
                scope.launch {
                    sendFanSpeedCommand(speed)
                }
            }
        }
    }

    private fun applyPillToggleStyle(
        onIsActive: Boolean,
        onBtn: androidx.appcompat.widget.AppCompatButton,
        offBtn: androidx.appcompat.widget.AppCompatButton
    ) {
        val red = ContextCompat.getColor(requireContext(), R.color.silabs_redtheme_button_fill_color)
        val white = ContextCompat.getColor(requireContext(), R.color.white)
        if (onIsActive) {
            onBtn.setBackgroundResource(R.drawable.matter_range_hood_pill_filled_red)
            onBtn.setTextColor(white)
            offBtn.setBackgroundResource(R.drawable.matter_range_hood_pill_outline_red)
            offBtn.setTextColor(red)
        } else {
            onBtn.setBackgroundResource(R.drawable.matter_range_hood_pill_outline_red)
            onBtn.setTextColor(red)
            offBtn.setBackgroundResource(R.drawable.matter_range_hood_pill_filled_red)
            offBtn.setTextColor(white)
        }
        onBtn.isEnabled = true
        offBtn.isEnabled = true
    }

    private fun updatePowerUI() {
        applyPillToggleStyle(
            isRangeHoodOn,
            binding.btnRangeHoodOn,
            binding.btnRangeHoodOff
        )
        if (isRangeHoodOn) {
            binding.tvRangeHoodStatus.text = getString(R.string.matter_range_hood_on)
            binding.tvRangeHoodStatus.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.silabs_green)
            )
            binding.ivRangeHoodIcon.setImageResource(R.drawable.matter_light)
            binding.ivRangeHoodIcon.setColorFilter(
                ContextCompat.getColor(requireContext(), R.color.silabs_yellow),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
        } else {
            binding.tvRangeHoodStatus.text = getString(R.string.matter_range_hood_off)
            binding.tvRangeHoodStatus.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.silabs_red)
            )
            binding.ivRangeHoodIcon.setImageResource(R.drawable.matter_light)
            binding.ivRangeHoodIcon.setColorFilter(
                ContextCompat.getColor(requireContext(), R.color.silabs_grey),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
        }
    }

    private fun updateFanUI() {
        applyPillToggleStyle(isFanOn, binding.btnFanOn, binding.btnFanOff)
        if (isFanOn) {
            binding.tvFanStatus.text = getString(R.string.matter_range_hood_fan_on)
            binding.tvFanStatus.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.silabs_green)
            )
            binding.ivFanIcon.setColorFilter(
                ContextCompat.getColor(requireContext(), R.color.silabs_redtheme_scanner_header_text_color),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
        } else {
            binding.tvFanStatus.text = getString(R.string.matter_range_hood_fan_off)
            binding.tvFanStatus.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.silabs_red)
            )
            binding.ivFanIcon.setColorFilter(
                ContextCompat.getColor(requireContext(), R.color.silabs_redtheme_button_outline_color),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
        }
    }

    private fun updateFanSpeedUI(fanSpeedButtons: Map<Int, View>) {
        fanSpeedButtons.forEach { (speed, button) ->
            val btn = button as? androidx.appcompat.widget.AppCompatButton
            if (speed == selectedFanSpeed) {
                btn?.alpha = 1.0f
                btn?.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.silabs_dark_blue)
                )
                btn?.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.white)
                )
            } else {
                btn?.alpha = 0.6f
                btn?.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.white)
                )
                btn?.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.masala)
                )
            }
        }
    }

    private fun updateUI() {
        updatePowerUI()
        updateFanUI()
        val fanSpeedButtons = mapOf(
            1 to binding.btnFanSpeed1,
            2 to binding.btnFanSpeed2,
            3 to binding.btnFanSpeed3,
            4 to binding.btnFanSpeed4
        )
        updateFanSpeedUI(fanSpeedButtons)
    }

    private fun showMessageDialog() {
        try {
            if (isAdded && !requireActivity().isFinishing) {
                requireActivity().runOnUiThread {
                    if (!MessageDialogFragment.isDialogShowing()) {
                        dialog = MessageDialogFragment()
                        dialog.setMessage(getString(R.string.matter_device_offline_text))
                        dialog.setOnDismissListener {
                            removeProgress()
                            if (requireActivity().supportFragmentManager.backStackEntryCount > 0) {
                                requireActivity().supportFragmentManager.popBackStack()
                            } else {
                                FragmentUtils.getHost(
                                    this@MatterRangeHoodFragment, CallBackHandler::class.java
                                ).onBackHandler()
                            }
                        }
                        val transaction: FragmentTransaction =
                            requireActivity().supportFragmentManager.beginTransaction()

                        dialog.show(transaction, dialogTag)
                    }
                }
            } else {
                Timber.e("device offline")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e("device offline device offline ${e.message}")
        }
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

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (isAdded) {
                if (requireActivity().supportFragmentManager.backStackEntryCount > 0) {
                    requireActivity().supportFragmentManager.popBackStack()
                } else {
                    FragmentUtils.getHost(
                        this@MatterRangeHoodFragment,
                        MatterLightFragment.CallBackHandler::class.java
                    ).onBackHandler()
                }
            }
        }
    }

    interface CallBackHandler {
        fun onBackHandler()
    }

    private suspend fun getOnOffClusterForDevice(): ChipClusters.OnOffCluster {
        return ChipClusters.OnOffCluster(
            ChipClient.getConnectedDevicePointer(requireContext(), deviceId),
            onOffEndpointId
        )
    }

    private suspend fun getFanControlClusterForDevice(): ChipClusters.FanControlCluster {
        return ChipClusters.FanControlCluster(
            ChipClient.getConnectedDevicePointer(requireContext(), deviceId),
            fanControlEndpointId
        )
    }

    private suspend fun getFanClusterForDevice(): ChipClusters.FanControlCluster {
        return ChipClusters.FanControlCluster(
            ChipClient.getConnectedDevicePointer(requireContext(), deviceId),
            fanControlEndpointId
        )
    }

    private suspend fun sendOnCommandClick() {
        getOnOffClusterForDevice().on(object : ChipClusters.DefaultClusterCallback {
            override fun onSuccess() {
                isRangeHoodOn = true
                requireActivity().runOnUiThread {
                    updatePowerUI()
                }
                SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, true)
            }

            override fun onError(ex: Exception) {
                SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, false)
                Timber.tag(TAG).e("ON command failure: $ex")
                showMessageDialog()
            }
        })
    }

    private suspend fun sendOffCommandClick() {
        getOnOffClusterForDevice().off(object : ChipClusters.DefaultClusterCallback {
            override fun onSuccess() {
                isRangeHoodOn = false
                requireActivity().runOnUiThread {
                    updatePowerUI()
                }
                SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, true)
            }

            override fun onError(ex: Exception) {
                SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, false)
                Timber.tag(TAG).e("OFF command failure: $ex")
                showMessageDialog()
            }
        })
    }

    private suspend fun sendFanOnCommandClick() {
        // Write mode 4 to turn fan on using FanControlCluster


        try {
            getFanClusterForDevice().writeFanModeAttribute(object : ChipClusters.DefaultClusterCallback {
                override fun onSuccess() {
                    isFanOn = true
                    requireActivity().runOnUiThread {
                        updatePowerUI()
                        updateFanUI()
                    }
                    SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, true)
                }

                override fun onError(ex: Exception) {
                    SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, false)
                    Timber.tag(TAG).e("FAN ON command failure: $ex")
                    showMessageDialog()
                }
            },4)
        }catch (e: Exception) {
            Timber.tag(TAG).e("Exception in sendFanOnCommandClick: ${e.message}")
        }
    }

    private suspend fun sendFanOffCommandClick() {
        // Write mode 1 to turn fan off using FanControlCluster
        try {
            getFanClusterForDevice().writeFanModeAttribute(object : ChipClusters.DefaultClusterCallback {
                override fun onSuccess() {
                    isFanOn = false
                    requireActivity().runOnUiThread {
                        updateFanUI()
                    }
                    SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, true)
                }

                override fun onError(ex: Exception) {
                    SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, false)
                    Timber.tag(TAG).e("FAN OFF command failure: $ex")
                    showMessageDialog()
                }
            },0)
        }catch (e: Exception) {
            Timber.tag(TAG).e("Exception in sendFanOffCommandClick: ${e.message}")
        }
    }

    private suspend fun sendFanSpeedCommand(speed: Int) {
        // Set fan speed using FanControlCluster
        Timber.tag(TAG).d("Setting fan speed to: $speed using FanControlCluster")
        getFanControlClusterForDevice().writeSpeedSettingAttribute(
            object : ChipClusters.DefaultClusterCallback {
                override fun onSuccess() {
                    isFanOn = speed > 0
                    selectedFanSpeed = speed
                    requireActivity().runOnUiThread {
                        updateFanUI()
                        val fanSpeedButtons = mapOf(
                            1 to binding.btnFanSpeed1,
                            2 to binding.btnFanSpeed2,
                            3 to binding.btnFanSpeed3,
                            4 to binding.btnFanSpeed4
                        )
                        updateFanSpeedUI(fanSpeedButtons)
                    }
                    SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, true)
                }

                override fun onError(ex: Exception) {
                    SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, false)
                    Timber.tag(TAG).e("Fan speed command failure: $ex")
                    showMessageDialog()
                }
            },
            speed, 5000
        )
    }

    private suspend fun readRangeHoodState() {
        getOnOffClusterForDevice().readOnOffAttribute(object :
            ChipClusters.BooleanAttributeCallback {

            override fun onError(ex: Exception) {
                SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, false)
                Timber.tag(TAG).e("Read OnOff attribute failure: $ex")
            }

            override fun onSuccess(value: Boolean) {
                isRangeHoodOn = value ?: false
                requireActivity().runOnUiThread {
                    updatePowerUI()
                }
                SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, true)
            }
        })
    }

    private suspend fun readFanState() {
        // Read fan speed setting from FanControlCluster to determine if fan is on
        getFanControlClusterForDevice().readFanModeAttribute(object :
            ChipClusters.IntegerAttributeCallback {
            override fun onSuccess(value: Int) {
                val speed = value ?: 0
                isFanOn = speed > 0
                selectedFanSpeed = if (speed > 0) speed else 1
                requireActivity().runOnUiThread {
                    updateFanUI()
                }
                SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, true)
            }

            override fun onError(ex: Exception) {
                SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, false)
                Timber.tag(TAG).e("Read Fan SpeedSetting attribute failure: $ex")
            }
        })
    }

    private suspend fun readFanSpeed() {
        // Read current fan speed from FanControlCluster for UI update
        getFanControlClusterForDevice().readSpeedSettingAttribute(object :
            ChipClusters.FanControlCluster.SpeedSettingAttributeCallback {
            override fun onSuccess(value: Int?) {
                val speed = value ?: 0
                if (speed > 0) {
                    selectedFanSpeed = speed.coerceIn(1, 4) // Clamp to valid range
                }
                requireActivity().runOnUiThread {
                    val fanSpeedButtons = mapOf(
                        1 to binding.btnFanSpeed1,
                        2 to binding.btnFanSpeed2,
                        3 to binding.btnFanSpeed3,
                        4 to binding.btnFanSpeed4
                    )
                    updateFanSpeedUI(fanSpeedButtons)
                }
                SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, true)
            }

            override fun onError(ex: Exception) {
                SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, false)
                Timber.tag(TAG).e("Read Fan SpeedSetting attribute failure: $ex")
            }
        })
    }

    companion object {
        private val TAG = Companion::class.java.simpleName.toString()
        const val INIT = -1L
        const val COUNTDOWN_INTERVAL = 500L
        const val ON_OFF_CLUSTER_ENDPOINT = 2
        const val FAN_CONTROL_CLUSTER_ENDPOINT = 1
        const val ARG_DEVICE_MODEL = "ARG_DEVICE_MODEL"
        private const val FAN_STATUS_POLL_INTERVAL_MS = 5000L // Poll every 5 seconds

        fun newInstance(): MatterRangeHoodFragment = MatterRangeHoodFragment()
    }
}
