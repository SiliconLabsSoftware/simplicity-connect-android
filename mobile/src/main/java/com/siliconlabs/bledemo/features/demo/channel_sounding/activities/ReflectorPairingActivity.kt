package com.siliconlabs.bledemo.features.demo.channel_sounding.activities

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.base.activities.BaseActivity
import com.siliconlabs.bledemo.bluetooth.ble.TimeoutGattCallback
import com.siliconlabs.bledemo.bluetooth.services.BluetoothService
import com.siliconlabs.bledemo.databinding.ActivityReflectorPairingBinding
import com.siliconlabs.bledemo.databinding.ItemPairingStepBinding
import com.siliconlabs.bledemo.features.demo.channel_sounding.services.ReflectorProximityForegroundService
import com.siliconlabs.bledemo.features.demo.channel_sounding.utils.ReflectorSessionPreferences
import com.siliconlabs.bledemo.utils.AppUtil
import com.siliconlabs.bledemo.utils.CustomToastManager
import com.siliconlabs.bledemo.features.demo.channel_sounding.gatt.DigitalKeyGattResolver

/**
 * Activity for the 4-step pairing flow in Reflector mode.
 * 
 * Steps:
 * 1. Device Found - Verifies the target device is available
 * 2. Pairing & Bonding - Establishes secure connection
 * 3. Channel Sounding Setup - Configures CS Reflector role
 * 4. Digital Key Ready - Enables auto-unlock functionality
 * 
 * Phase 2 Feature: Part of the Digital Key for Door Lock use case.
 */
@SuppressLint("MissingPermission")
class ReflectorPairingActivity : BaseActivity() {

    private lateinit var binding: ActivityReflectorPairingBinding
    private lateinit var bluetoothBinding: BluetoothService.Binding
    private var bluetoothService: BluetoothService? = null

    private var deviceAddress: String? = null
    private var deviceName: String? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var discoveredServices: List<BluetoothGattService> = emptyList()

    private var currentStep = 0

    private val bluetoothAdapterStateChangeListener: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_OFF) {
                    finish()
                }
            }
        }
    }

    private val bondStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                
                if (device?.address == deviceAddress) {
                    when (bondState) {
                        BluetoothDevice.BOND_BONDED -> {
                            updateStep(2, StepState.COMPLETED, getString(R.string.reflector_pairing_step2_success))
                            proceedToStep3()
                        }
                        BluetoothDevice.BOND_NONE -> {
                            updateStep(2, StepState.FAILED, getString(R.string.reflector_pairing_step2_failed))
                        }
                    }
                }
            }
        }
    }

    private val gattCallback = object : TimeoutGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            runOnUiThread {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        updateStep(1, StepState.COMPLETED, getString(R.string.reflector_pairing_step1_success, deviceName))
                        proceedToStep2(gatt)
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        if (currentStep < 4) {
                            handleConnectionFailure()
                        }
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            runOnUiThread {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    discoveredServices = gatt.services
                    if (DigitalKeyGattResolver.resolve(gatt) == null) {
                        updateStep(3, StepState.FAILED, getString(R.string.reflector_pairing_step3_digital_key_missing))
                        return@runOnUiThread
                    }
                    updateStep(3, StepState.COMPLETED, getString(R.string.reflector_pairing_step3_success))
                    proceedToStep4()
                } else {
                    updateStep(3, StepState.FAILED, getString(R.string.reflector_pairing_step3_failed))
                }
            }
        }

        override fun onTimeout() {
            runOnUiThread {
                handleConnectionFailure()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReflectorPairingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
        deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME)

        if (deviceAddress == null) {
            CustomToastManager.showError(this, getString(R.string.reflector_pairing_no_device))
            finish()
            return
        }

        setupToolbar()
        setupSteps()
        setupClickListeners()
        bindBluetoothService()

        registerReceiver(
            bluetoothAdapterStateChangeListener,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        )
        registerReceiver(
            bondStateReceiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        )
    }

    private fun setupToolbar() {
        /*AppUtil.setEdgeToEdge(window, this)
        setSupportActionBar(mToolbar)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setHomeAsUpIndicator(R.drawable.matter_back)
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.title = this.getString(R.string.title_Throughput)
        }*/
        AppUtil.setEdgeToEdge(window, this)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
        binding.pairingSubtitle.text = getString(R.string.reflector_pairing_subtitle, deviceName ?: "Device")
    }

    private fun setupSteps() {
        // Step 1: Device Found
        getStepBinding(1).apply {
            stepNumber.text = "1"
            stepTitle.text = getString(R.string.reflector_pairing_step1_title)
            stepStatus.text = getString(R.string.reflector_pairing_step1_pending)
        }

        // Step 2: Pairing & Bonding
        getStepBinding(2).apply {
            stepNumber.text = "2"
            stepTitle.text = getString(R.string.reflector_pairing_step2_title)
            stepStatus.text = getString(R.string.reflector_pairing_step2_pending)
        }

        // Step 3: Channel Sounding Setup
        getStepBinding(3).apply {
            stepNumber.text = "3"
            stepTitle.text = getString(R.string.reflector_pairing_step3_title)
            stepStatus.text = getString(R.string.reflector_pairing_step3_pending)
        }

        // Step 4: Digital Key Ready
        getStepBinding(4).apply {
            stepNumber.text = "4"
            stepTitle.text = getString(R.string.reflector_pairing_step4_title)
            stepStatus.text = getString(R.string.reflector_pairing_step4_pending)
        }
    }

    private fun setupClickListeners() {
        binding.btnAction.setOnClickListener {
            if (currentStep >= 4) {
                val addr = deviceAddress ?: return@setOnClickListener
                ReflectorProximityForegroundService.start(this, addr, deviceName)
                startActivity(
                    ReflectorDashboardActivity.createIntent(this, addr, deviceName, forceNewSession = true),
                )
                finish()
            }
        }
    }

    private fun bindBluetoothService() {
        bluetoothBinding = object : BluetoothService.Binding(this) {
            override fun onBound(service: BluetoothService?) {
                bluetoothService = service
                startPairingProcess()
            }
        }
        bluetoothBinding.bind()
    }

    private fun startPairingProcess() {
        currentStep = 1
        updateStep(1, StepState.IN_PROGRESS, getString(R.string.reflector_pairing_step1_in_progress))

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)

        if (device == null) {
            updateStep(1, StepState.FAILED, getString(R.string.reflector_pairing_device_not_found))
            return
        }

        // Connect to device
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private fun proceedToStep2(gatt: BluetoothGatt) {
        currentStep = 2
        updateStep(2, StepState.IN_PROGRESS, getString(R.string.reflector_pairing_step2_in_progress))

        val device = gatt.device
        
        // Check if already bonded
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            updateStep(2, StepState.COMPLETED, getString(R.string.reflector_pairing_step2_success))
            proceedToStep3()
        } else {
            // Initiate bonding
            device.createBond()
        }
    }

    private fun proceedToStep3() {
        currentStep = 3
        updateStep(3, StepState.IN_PROGRESS, getString(R.string.reflector_pairing_step3_in_progress))

        // Discover services to set up CS Reflector role
        bluetoothGatt?.discoverServices()
    }

    private fun proceedToStep4() {
        currentStep = 4
        updateStep(4, StepState.COMPLETED, getString(R.string.reflector_pairing_step4_success))
        handoffToProximityService()
        showPairingSuccess()
    }

    /** Close pairing GATT and start the foreground monitor so Digital Key + CS can run before dashboard. */
    private fun handoffToProximityService() {
        val addr = deviceAddress ?: return
        try {
            bluetoothGatt?.close()
        } catch (_: Exception) { }
        bluetoothGatt = null
        ReflectorSessionPreferences.savePairedDevice(this, addr, deviceName)
        ReflectorProximityForegroundService.start(this, addr, deviceName)
    }

    private fun showPairingSuccess() {
        binding.pairingTitle.text = getString(R.string.reflector_pairing_digital_key_ready)
        binding.pairingSubtitle.visibility = View.GONE
        binding.connectionProgress.visibility = View.GONE
        
        // Update lock icon to show connected state
        binding.lockIcon.setColorFilter(ContextCompat.getColor(this, R.color.silabs_green))

        // Enable action button
        binding.btnAction.apply {
            isEnabled = true
            text = getString(R.string.reflector_pairing_go_to_dashboard)
            setActionButtonBackgroundColor(R.color.silabs_green)
        }
    }

    private fun handleConnectionFailure() {
        val currentStepBinding = getStepBinding(currentStep)
        updateStep(currentStep, StepState.FAILED, getString(R.string.reflector_pairing_connection_failed))
        
        binding.btnAction.apply {
            isEnabled = true
            text = getString(R.string.reflector_pairing_retry)
            setActionButtonBackgroundColor(R.color.silabs_red)
            setOnClickListener {
                resetAndRetry()
            }
        }
    }

    private fun resetAndRetry() {
        bluetoothGatt?.close()
        bluetoothGatt = null
        currentStep = 0
        setupSteps()
        
        binding.btnAction.apply {
            isEnabled = false
            text = getString(R.string.reflector_pairing_in_progress)
            setActionButtonBackgroundColor(R.color.silabs_divider)
            setOnClickListener {
                if (currentStep >= 4) {
                    val addr = deviceAddress ?: return@setOnClickListener
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    ReflectorProximityForegroundService.start(this@ReflectorPairingActivity, addr, deviceName)
                    startActivity(
                        ReflectorDashboardActivity.createIntent(
                            this@ReflectorPairingActivity,
                            addr,
                            deviceName,
                            forceNewSession = true,
                        )
                    )
                    finish()
                }
            }
        }
        
        startPairingProcess()
    }

    private fun MaterialButton.setActionButtonBackgroundColor(colorResId: Int) {
        backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this@ReflectorPairingActivity, colorResId)
        )
    }

    private fun updateStep(stepNumber: Int, state: StepState, statusText: String) {
        val stepBinding = getStepBinding(stepNumber)
        
        stepBinding.apply {
            stepStatus.text = statusText
            
            when (state) {
                StepState.PENDING -> {
                    stepIconContainer.setBackgroundResource(R.drawable.circle_background_grey)
                    this.stepNumber.visibility = View.VISIBLE
                    stepProgress.visibility = View.GONE
                    stepCheck.visibility = View.GONE
                }
                StepState.IN_PROGRESS -> {
                    stepIconContainer.setBackgroundResource(R.drawable.circle_background_active)
                    this.stepNumber.visibility = View.GONE
                    stepProgress.visibility = View.VISIBLE
                    stepCheck.visibility = View.GONE
                }
                StepState.COMPLETED -> {
                    stepIconContainer.setBackgroundResource(R.drawable.circle_background_green)
                    this.stepNumber.visibility = View.GONE
                    stepProgress.visibility = View.GONE
                    stepCheck.visibility = View.VISIBLE
                }
                StepState.FAILED -> {
                    stepIconContainer.setBackgroundResource(R.drawable.circle_background_red)
                    this.stepNumber.visibility = View.VISIBLE
                    this.stepNumber.text = "!"
                    stepProgress.visibility = View.GONE
                    stepCheck.visibility = View.GONE
                }
            }
        }
    }

    private fun getStepBinding(stepNumber: Int): ItemPairingStepBinding {
        return when (stepNumber) {
            1 -> ItemPairingStepBinding.bind(binding.step1.root)
            2 -> ItemPairingStepBinding.bind(binding.step2.root)
            3 -> ItemPairingStepBinding.bind(binding.step3.root)
            4 -> ItemPairingStepBinding.bind(binding.step4.root)
            else -> throw IllegalArgumentException("Invalid step number: $stepNumber")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothGatt?.close()
        unregisterReceiver(bluetoothAdapterStateChangeListener)
        unregisterReceiver(bondStateReceiver)
        bluetoothBinding.unbind()
    }

    enum class StepState {
        PENDING, IN_PROGRESS, COMPLETED, FAILED
    }

    companion object {
        const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
        const val EXTRA_DEVICE_NAME = "extra_device_name"
    }
}
