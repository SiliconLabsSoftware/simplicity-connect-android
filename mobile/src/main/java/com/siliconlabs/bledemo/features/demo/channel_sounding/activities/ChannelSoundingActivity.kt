package com.siliconlabs.bledemo.features.demo.channel_sounding.activities

import android.Manifest.permission
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IAxisValueFormatter
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.base.activities.BaseDemoActivity
import com.siliconlabs.bledemo.bluetooth.ble.TimeoutGattCallback
import com.siliconlabs.bledemo.databinding.ActivityChannelSoundMainLayoutBinding
import com.siliconlabs.bledemo.features.demo.channel_sounding.fragments.ChannelSoundingConfigureFragment
import com.siliconlabs.bledemo.features.demo.channel_sounding.models.KalmanFilterConfig
import com.siliconlabs.bledemo.features.demo.channel_sounding.utils.ChannelSoundingConfigureParameters
import com.siliconlabs.bledemo.features.demo.channel_sounding.utils.ChannelSoundingConstant
import com.siliconlabs.bledemo.features.demo.channel_sounding.viewmodels.ChannelSoundingBleConnectViewModel
import com.siliconlabs.bledemo.features.demo.channel_sounding.viewmodels.ChannelSoundingDistanceMeasurementViewModel
import com.siliconlabs.bledemo.features.demo.channel_sounding.viewmodels.SharedViewModel
import com.siliconlabs.bledemo.features.demo.channel_sounding.views.DistanceChartMarkerView
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.CustomProgressDialog
import com.siliconlabs.bledemo.utils.AppUtil
import com.siliconlabs.bledemo.utils.CustomToastManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import androidx.lifecycle.asFlow
import timber.log.Timber
import java.text.DecimalFormat
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.BAKLAVA)
class ChannelSoundingActivity : BaseDemoActivity(),
    ChannelSoundingConfigureFragment.CallBackHandler {
    private lateinit var binding: ActivityChannelSoundMainLayoutBinding
    private val processor = GattProcessor()
    private lateinit var viewModelBle: ChannelSoundingBleConnectViewModel
    private lateinit var viewModelDM: ChannelSoundingDistanceMeasurementViewModel
    private lateinit var viewModelShared: SharedViewModel

    private var freqInfo: String = HIGH_FREQUENCY
    private var durationInfo: Int = 0
    private val DISTANCE_DECIMAL_FMT = DecimalFormat("0.00")

    private var targetedDevice: BluetoothDevice? = null
    private var pairingProgressDialog: CustomProgressDialog? = null
    private val rangeLow = 0.0..5.0
    private val rangeMid = 6.0..10.00
    private var menu: Menu? = null
    private var isManualStop = false
    private var measurementStartTime = 0L
    private var lastMeasurementTime = 0L
    private var isDeviceBonded = false
    private var connectionLostDialog: androidx.appcompat.app.AlertDialog? = null
    private var stallMonitorJob: Job? = null
    private var poorSignalJob: Job? = null
    private var isTooClose = false // Track "too close" state to suppress misleading "poor signal" message
    private var pendingRestart = false // Flag to indicate session should restart after stopping
    private var stoppedDueToStallMaxRetries = false // Avoid duplicate toast when stall dialog shown

    // Add this with other state variables (around line 91-96)
    private var stallDialogCount = 0  // Track how many times stall dialog shown
    private val MAX_STALL_RETRIES = 2 // Show reconnect dialog twice, then give up
    // Marker view for showing spike/smoothing annotations on the distance chart
    private var distanceChartMarkerView: DistanceChartMarkerView? = null

    // Chart display options (controlled by checkboxes, both disabled by default)
    private var showRawDistance = false
    private var showSpikes = false

    // === FILTER-ONLY CHANGE DETECTION ===
    // Track previous frequency/duration to detect filter-only changes (no session restart needed)
    private var lastAppliedFrequency: String = HIGH_FREQUENCY
    private var lastAppliedDuration: Int = 0

    // === HIGH-CONFIDENCE GRACE WINDOW ===
    // Track last HIGH (confidence == 2) timestamp; 10s without HIGH → show "Out of Measurements" and restart
    private var lastHighConfidenceTimestamp: Long = 0L
    private var highConfidenceGraceWindowJob: Job? = null
    private var showedWaitingToastInCurrentWindow: Boolean = false

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChannelSoundMainLayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AppUtil.setEdgeToEdge(window, this)
        setSupportActionBar(binding.toolbar)

        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setHomeAsUpIndicator(R.drawable.matter_back)
            actionBar.setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.channel_sounding_screen_name)
        }
        initViews()
    }

    private fun openConfigureScreen() {
        binding.configureContainer.visibility = View.VISIBLE
        binding.channelSoundingActivityContainer.visibility = View.GONE
        hideConfigureIcon()
        val fragment = ChannelSoundingConfigureFragment.newInstance()
        showFragment(fragment, fragment::class.java.simpleName)
    }

    fun hideConfigureIcon() {
        menu?.findItem(R.id.menu_start_stop)?.isVisible = false
        menu?.findItem(R.id.menu_configure)?.isVisible = false
    }

    fun showConfigureIcon() {
        menu?.findItem(R.id.menu_start_stop)?.isVisible = true
        menu?.findItem(R.id.menu_configure)?.isVisible = true
    }

    private fun registerReceiver() {
        val filter = android.content.IntentFilter()
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        filter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        registerReceiver(bluetoothReceiver, filter)
    }

    private fun showFragment(
        fragment: Fragment, tag: String? = null,
    ) {
        val fManager = supportFragmentManager
        val fTransaction = fManager.beginTransaction()

        fTransaction.replace(binding.configureContainer.id, fragment, tag)
            .addToBackStack(null)
            .commit()

    }




    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_channel_sounding, menu)
        this.menu = menu
        menu?.findItem(R.id.menu_configure)?.icon?.mutate()?.setTint(
            ContextCompat.getColor(this, R.color.silabs_white)
        )
        updateMenuTitle()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (this.supportFragmentManager.backStackEntryCount > 0) {
                    this.supportFragmentManager.popBackStack()
                    binding.configureContainer.visibility = android.view.View.GONE
                    binding.channelSoundingActivityContainer.visibility = android.view.View.VISIBLE
                    return true
                } else {
                    this.finish()
                }
                true
            }

            R.id.menu_configure -> {
                openConfigureScreen()
                true
            }

            R.id.menu_start_stop -> {
                handleStartStopMenuClick()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    @SuppressLint("NewApi")
    private fun handleStartStopMenuClick() {
        val currentSessionState = viewModelDM.getSessionState().value
        val currentMeasurementState = viewModelDM.getMeasurementState().value
        Timber.tag(TAG).d("CS handleStartStopMenuClick: sessionState=$currentSessionState, measurementState=$currentMeasurementState")
        
        // Prevent action if already in transition state
        if (currentSessionState == ChannelSoundingConstant.RangeSessionState.STARTING ||
            currentSessionState == ChannelSoundingConstant.RangeSessionState.STOPPING) {
            Timber.tag(TAG).w("CS Ignoring start/stop click - already transitioning: $currentSessionState")
            CustomToastManager.show(this, "Please wait...", 1000)
            return
        }

        // === SIMPLE PAUSE/RESUME LOGIC ===
        // Click START (when paused/idle) -> measurements start/resume, menu shows "Stop"
        // Click STOP (when running) -> measurements pause, menu shows "Start"
        
        when (currentMeasurementState) {
            // Currently RUNNING -> PAUSE measurements
            ChannelSoundingConstant.MeasurementState.RUNNING -> {
                Timber.tag(TAG).d("CS User clicked STOP - pausing measurements, session stays alive")
                
                // Pause measurements instead of stopping the session
                // Session remains active, only callback processing is paused
                // Kalman filter state is preserved for seamless resume
                viewModelDM.pauseMeasurements()
                updateMenuTitle()
            }
            
            // Currently PAUSED -> RESUME measurements
            ChannelSoundingConstant.MeasurementState.PAUSED -> {
                Timber.tag(TAG).d("CS User clicked START - resuming measurements, session stays alive")
                
                // Resume measurements without reinitializing session or Kalman filter
                viewModelDM.resumeMeasurements()
                updateMenuTitle()
            }
            
            // Currently IDLE (session stopped) -> START fresh
            ChannelSoundingConstant.MeasurementState.IDLE -> {
                Timber.tag(TAG).d("CS User clicked START - starting fresh measurement session")
                freqInfo = ChannelSoundingConfigureFragment.getFrequency(this) ?: HIGH_FREQUENCY
                durationInfo = ChannelSoundingConfigureFragment.getDuration(this)?.toInt() ?: 0
                viewModelDM.channelSoundingStart(TECHNOLOGY, freqInfo, durationInfo)
                // Menu will be updated by state observer when state changes to STARTED
            }
        }
    }

    @SuppressLint("NewApi")
    private fun updateMenuTitle() {
        runOnUiThread {
            val measurementState = viewModelDM.getMeasurementState().value
            val menuItem = menu?.findItem(R.id.menu_start_stop)
            Timber.tag(TAG).d("CS updateMenuTitle: measurementState=$measurementState")
            
            // Menu title logic based on measurement state:
            // - Measurements RUNNING -> Show "Stop"
            // - Measurements PAUSED or IDLE -> Show "Start"
            when (measurementState) {
                ChannelSoundingConstant.MeasurementState.RUNNING -> {
                    // Measurements active - show "Stop"
                    setMenuItemTitleWithWhiteColor(menuItem, getString(R.string.channel_sounding_menu_stop))
                }
                ChannelSoundingConstant.MeasurementState.PAUSED,
                ChannelSoundingConstant.MeasurementState.IDLE -> {
                    // Measurements paused or not started - show "Start"
                    setMenuItemTitleWithWhiteColor(menuItem, getString(R.string.channel_sounding_menu_start))
                }
            }
        }
    }

    private fun setMenuItemTitleWithWhiteColor(menuItem: MenuItem?, title: String) {
        menuItem?.let {
            val spannableString = android.text.SpannableString(title)
            spannableString.setSpan(
                android.text.style.ForegroundColorSpan(
                    ContextCompat.getColor(this, R.color.silabs_white)
                ),
                0,
                spannableString.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            it.title = spannableString
        }
    }


    @RequiresPermission(permission.BLUETOOTH_CONNECT)
    override fun onBluetoothServiceBound() {

        service?.registerGattCallback(processor)
        service?.getActiveConnections()?.let { connDev ->
            if (connDev.isNotEmpty()) {
                val dev = connDev[0].connection.gatt

                deviceInfo(gatt)
                gatt = dev
                lifecycleScope.launch {
                    gatt?.discoverServices()
                }
                service?.refreshGattServices(service?.connectedGatt)

            } else {
                Timber.tag(TAG).e("No active connections found.")
            }
        } ?: Timber.tag(TAG).e("Service is null or has no active connections.")

    }

    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun initViews() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.BAKLAVA) {
            requestAllPermissions()
        }
        registerReceiver()
        ChannelSoundingConfigureParameters.resetInstance(
            this,
            false
        )
        // Reset frequency and duration to default values
        ChannelSoundingConfigureFragment.saveFrequency(this, HIGH_FREQUENCY)
        ChannelSoundingConfigureFragment.saveDuration(this, 0)
        binding.configureContainer.visibility = android.view.View.GONE
        binding.channelSoundingActivityContainer.visibility = android.view.View.VISIBLE

        viewModelShared = ViewModelProvider(this).get(SharedViewModel::class.java)
        viewModelBle =
            ViewModelProvider(
                this,
                ChannelSoundingBleConnectViewModel.Factory(application)
            )[ChannelSoundingBleConnectViewModel::class.java]

        viewModelBle.getGattState().observe(this) { gattSate ->
            Timber.tag(TAG).d("CS Gatt state changed to $gattSate")
        }
        viewModelDM =
            ViewModelProvider(
                this,
                ChannelSoundingDistanceMeasurementViewModel.Factory(this, viewModelBle)
            )[ChannelSoundingDistanceMeasurementViewModel::class.java]

        // Initialize Kalman filter state from saved preferences (KalmanFilterConfig is the single source of truth)
        val kalmanFilterConfig = KalmanFilterConfig.load(this)
        viewModelDM.setKalmanFilterEnabled(kalmanFilterConfig.enabled)
        viewModelDM.applyKalmanFilterConfig(kalmanFilterConfig)

        // Initialize tracking values for filter-only change detection
        lastAppliedFrequency = ChannelSoundingConfigureFragment.getFrequency(this) ?: HIGH_FREQUENCY
        lastAppliedDuration = ChannelSoundingConfigureFragment.getDuration(this)?.toInt() ?: 0
        Timber.tag(TAG).d("CS Initialized tracking values: freq=$lastAppliedFrequency, duration=$lastAppliedDuration")

        // Initialize the distance chart
        setupDistanceChart()

        // Setup chart display option checkboxes (both unchecked by default)
        binding.showRawDistanceCheckbox.isChecked = showRawDistance
        binding.showRawDistanceCheckbox.setOnCheckedChangeListener { _, isChecked ->
            showRawDistance = isChecked
            // Refresh chart with current data points
            val currentDataPoints = viewModelDM.chartDataPoints.value
            if (currentDataPoints.isNotEmpty()) {
                updateDistanceChart(currentDataPoints)
            }
        }

        binding.showSpikesCheckbox.isChecked = showSpikes
        binding.showSpikesCheckbox.setOnCheckedChangeListener { _, isChecked ->
            showSpikes = isChecked
            // Refresh chart with current data points
            val currentDataPoints = viewModelDM.chartDataPoints.value
            if (currentDataPoints.isNotEmpty()) {
                updateDistanceChart(currentDataPoints)
            }
        }

        try {
            viewModelBle.getTargetDevice().observe(this) { device ->
                Timber.tag(TAG).d("CS Target device changed to ${device?.address}")
                viewModelDM.setTargetDevice(device)
                targetedDevice = device


                // Only auto-start if not already starting/started and device is not null
                if (device != null) {
                    val currentState = viewModelDM.getSessionState().value
                    if (currentState == ChannelSoundingConstant.RangeSessionState.STOPPED ||
                        currentState == ChannelSoundingConstant.RangeSessionState.STOPPING) {
                        freqInfo =
                            ChannelSoundingConfigureFragment.getFrequency(this@ChannelSoundingActivity)
                                ?: HIGH_FREQUENCY
                        durationInfo = ChannelSoundingConfigureFragment.getDuration(this)?.toInt() ?: 0
                        isManualStop = false // Starting automatically after device connection
                        viewModelDM.channelSoundingStart(TECHNOLOGY, freqInfo, durationInfo)
                    } else {
                        Timber.tag(TAG).d("CS Skipping auto-start - session already in progress: $currentState")
                    }
                }
            }
        } catch (e: SecurityException) {
            Timber.tag(TAG).e("CS Security Exception: ${e.message}")
        }

        viewModelShared.message.observe(this) { msg ->
            // Skip null or empty messages
            if (msg.isNullOrEmpty()) {
                Timber.tag(TAG).d("CS Shared ViewModel message: null or empty, skipping")
                return@observe
            }
            Timber.tag(TAG).d("CS Shared ViewModel message: $msg")

            // Clear the message immediately to prevent re-processing on configuration changes
            viewModelShared.message.value = null

            // Get new frequency and duration from saved preferences
            val newFrequency = ChannelSoundingConfigureFragment.getFrequency(this@ChannelSoundingActivity)
                ?: HIGH_FREQUENCY
            val newDuration = ChannelSoundingConfigureFragment.getDuration(this)?.toInt() ?: 0
            
            showConfigureIcon()
            binding.configureContainer.visibility = android.view.View.GONE
            binding.channelSoundingActivityContainer.visibility = android.view.View.VISIBLE

            // === FILTER-ONLY CHANGE DETECTION ===
            // Check if only Kalman filter changed (frequency and duration unchanged)
            val frequencyChanged = newFrequency != lastAppliedFrequency
            val durationChanged = newDuration != lastAppliedDuration
            val isFilterOnlyChange = !frequencyChanged && !durationChanged

            if (isFilterOnlyChange) {
                // Filter-only change: Kalman filter is already applied in ConfigureFragment
                // No session restart needed - measurements will immediately use new filter settings
                Timber.tag(TAG).d("CS Filter-only change detected - no session restart needed, filter applied immediately (freq=$newFrequency, duration=$newDuration)")
                // No action needed - filter is already active, session continues uninterrupted
                return@observe
            }

            // Frequency or duration changed - session restart is required
            Timber.tag(TAG).d("CS Frequency/duration changed (freq: $lastAppliedFrequency->$newFrequency, duration: $lastAppliedDuration->$newDuration) - session restart required")
            freqInfo = newFrequency
            durationInfo = newDuration

            // Check if session is currently running
            val currentState = viewModelDM.getSessionState().value
            if (currentState == ChannelSoundingConstant.RangeSessionState.STARTED ||
                currentState == ChannelSoundingConstant.RangeSessionState.STARTING) {
                // Session is running, stop it and mark for pending restart
                Timber.tag(TAG).d("CS Session running, stopping and scheduling restart")
                isManualStop = true // Stop triggered by config change
                pendingRestart = true // Mark that we need to restart after stop
                viewModelDM.channelSoundingStop()
            } else {
                // Session already stopped, start directly after a small delay
                Timber.tag(TAG).d("CS Session already stopped, starting directly")
                pendingRestart = false
                isManualStop = false
                lifecycleScope.launch {
                    delay(500) // Small delay for UI to settle
                    viewModelDM.channelSoundingStart(TECHNOLOGY, freqInfo, durationInfo)
                }
            }

        }

        // Observe measurement state changes to update menu (RUNNING <-> PAUSED)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModelDM.getMeasurementState().collect { measurementState ->
                    Timber.tag(TAG).d("CS Measurement state changed to: $measurementState")
                    updateMenuTitle()
                }
            }
        }

        // Observe session state changes to update menu and show toast
        viewModelDM.getSessionState().observe(this) { state ->
            Timber.tag(TAG).d("CS Session state changed to: $state, pendingRestart=$pendingRestart")
            updateMenuTitle()

            if (state == ChannelSoundingConstant.RangeSessionState.STARTED) {
                measurementStartTime = System.currentTimeMillis()
                lastMeasurementTime = measurementStartTime
                pendingRestart = false // Clear pending restart since session started
                lastHighConfidenceTimestamp = 0L
                showedWaitingToastInCurrentWindow = false
                
                // Track applied frequency/duration for filter-only change detection
                lastAppliedFrequency = freqInfo
                lastAppliedDuration = durationInfo
                Timber.tag(TAG).d("CS Session started - tracking freq=$lastAppliedFrequency, duration=$lastAppliedDuration")
                
                // Cancel any existing stall monitor before starting a new one
                stallMonitorJob?.cancel()
                // Start monitoring for measurement stall
                startMeasurementStallMonitor()
                // Start HIGH-confidence grace window monitor (10s without HIGH → dialog + restart)
                startHighConfidenceGraceWindowMonitor()
            } else if (state == ChannelSoundingConstant.RangeSessionState.STOPPED) {
                // Cancel stall monitor and poor-signal timer when session stops
                stallMonitorJob?.cancel()
                stallMonitorJob = null
                highConfidenceGraceWindowJob?.cancel()
                highConfidenceGraceWindowJob = null
                poorSignalJob?.cancel()
                poorSignalJob = null
                isTooClose = false
                lastHighConfidenceTimestamp = 0L
                showedWaitingToastInCurrentWindow = false
                binding.poorSignalMessage.visibility = View.GONE
                measurementStartTime = 0L
                lastMeasurementTime = 0L

                // Check if we need to restart the session (e.g., after config change)
                if (pendingRestart) {
                    pendingRestart = false
                    isManualStop = false
                    Timber.tag(TAG).d("CS Pending restart triggered, starting session after delay")
                    lifecycleScope.launch {
                        // Short delay for BLE stack to stabilize after session stop
                        delay(1000) // Reduced from 4s to 1s for faster restart
                        val currentState = viewModelDM.getSessionState().value
                        if (currentState == ChannelSoundingConstant.RangeSessionState.STOPPED) {
                            Timber.tag(TAG).d("CS Starting session after pending restart")
                            viewModelDM.channelSoundingStart(TECHNOLOGY, freqInfo, durationInfo)
                        } else {
                            Timber.tag(TAG).d("CS Skipping pending restart - state is $currentState")
                        }
                    }
                } else if (!isManualStop && isDeviceBonded && !stoppedDueToStallMaxRetries) {
                    // Show toast when measurements stop automatically (not manual stop) and device is bonded
                    CustomToastManager.show(
                        this@ChannelSoundingActivity,
                        getString(R.string.channel_sounding_stopped_collecting),
                        3000
                    )
                }
                stoppedDueToStallMaxRetries = false
                // NOTE: Do NOT reset isManualStop here - it's used by the platform stop event handler
                // to decide whether to auto-restart. It will be reset when user clicks START.
            }
        }

        // Observe measurement heartbeat (ALL confidence levels) to keep timing state updated
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModelDM.getMeasurementHeartbeat().collect { heartbeat ->
                    // Update timing state for ALL measurements (HIGH, MEDIUM, LOW) to prevent stall
                    val currentTime = heartbeat.timestamp
                    if (measurementStartTime == 0L) {
                        measurementStartTime = currentTime
                    }
                    lastMeasurementTime = currentTime
                    // Track last HIGH confidence timestamp for grace-window logic
                    if (heartbeat.confidence == CONFIDENCE_VALUE_HIGH) {
                        lastHighConfidenceTimestamp = currentTime
                        showedWaitingToastInCurrentWindow = false
                    }
                    Timber.tag(TAG).v("CS Heartbeat: confidence=%s, distance=%.3f m",
                        when(heartbeat.confidence) { 0 -> "LOW"; 1 -> "MEDIUM"; 2 -> "HIGH"; else -> "UNKNOWN" },
                        heartbeat.rawDistance)
                }
            }
        }
        
        // Observe HIGH confidence distance results for UI updates
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModelDM.getDistanceResult().collect { distance ->
                    val confidence = distance.confidence

                    if(confidence > 0){
                        // Track last HIGH confidence for grace-window; clear waiting-toast flag on recovery
                        if (confidence == CONFIDENCE_VALUE_HIGH) {
                            lastHighConfidenceTimestamp = System.currentTimeMillis()
                            showedWaitingToastInCurrentWindow = false
                        }
                        // Cancel poor-signal timer and hide message when we get a valid (HIGH) measurement
                        poorSignalJob?.cancel()
                        poorSignalJob = null
                        binding.poorSignalMessage.visibility = View.GONE
                        isTooClose = false // Valid measurement received, device is no longer too close
                        stallDialogCount = 0
                        // Update ripple color based on distance
                        updateRippleColor(distance.distanceMeter)

                        binding.distanceResult.setTextColor(
                            ContextCompat.getColor(
                                this@ChannelSoundingActivity,
                                R.color.silabs_white
                            )
                        )
                        binding.distanceResult.text =
                            DISTANCE_DECIMAL_FMT.format(
                                distance.distanceMeter
                            ) + " m"
                        binding.confidenceScore.visibility = View.GONE

                        // Update raw and filtered distance value boxes
                        binding.rawDistanceValue.text =
                            DISTANCE_DECIMAL_FMT.format(distance.rawDistanceMeter) + " m"
                        binding.filteredDistanceValue.text =
                            DISTANCE_DECIMAL_FMT.format(distance.distanceMeter) + " m"

                        Timber.tag(TAG).d("CS Distance result changed to $distance")


                        // Schedule "poor signal" message if no further HIGH-confidence measurement for 3s
                        if(distance.distanceMeter > 5.0){
                            poorSignalJob = lifecycleScope.launch {
                                delay(3000)
                                if (viewModelDM.getSessionState().value == ChannelSoundingConstant.RangeSessionState.STARTED && !isTooClose) {
                                    binding.poorSignalMessage.text = getString(R.string.channel_sounding_poor_signal)
                                    binding.poorSignalMessage.visibility = View.VISIBLE
                                    CustomToastManager.showError(
                                        this@ChannelSoundingActivity,
                                        getString(R.string.channel_sounding_poor_signal),
                                        3000
                                    )
                                }
                            }
                        }
                        if (distance.rawDistanceMeter == 0.0) {
                            isTooClose = true
                            CustomToastManager.showError(this@ChannelSoundingActivity,getString(R.string.channel_sounding_measurements_near_range),2000)
                        }
                    }
                }
            }
        }

        // Observe outlier events to show Toast notification when filter detects outliers
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModelDM.getOutlierEvents().collect { event ->
                    when (event) {
                        is ChannelSoundingDistanceMeasurementViewModel.OutlierEvent.OutlierDetected -> {
                            Timber.tag(TAG).w(
                                "CS Outlier detected: raw=%.3f m, filtered=%.3f m"
                                    .format(event.rawDistance, event.filteredDistance)
                            )
                            val message = getString(R.string.channel_sounding_outlier_detected) +
                                    ": raw=%.3f m, filtered=%.3f m".format(event.rawDistance, event.filteredDistance)
                            Timber.tag(TAG).d(message)
                            /*CustomToastManager.show(
                                this@ChannelSoundingActivity,
                                message,
                                1500
                            )*/
                        }
                    }
                }
            }
        }

        // Observe Kalman filter enabled state for logging and diagnostics
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModelDM.kalmanFilterEnabled.collect { isEnabled ->
                    Timber.tag(TAG).d("CS Kalman filter enabled state changed to: $isEnabled")
                    // Update distanceValuesContainer visibility based on Kalman filter state
                    binding.distanceValuesContainer.visibility = if (isEnabled) View.VISIBLE else View.GONE
                    // Chart is hidden - kept GONE regardless of filter state
                    binding.distanceChart.visibility = View.GONE
                }
            }
        }

        // Observe chart data points and update the dual-line chart in real-time
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModelDM.chartDataPoints.collect { dataPoints ->
                    if (dataPoints.isNotEmpty()) {
                        updateDistanceChart(dataPoints)
                    }
                }
            }
        }

    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun requestAllPermissions() {
        val requiredPermissions =
            arrayOf(
                permission.ACCESS_COARSE_LOCATION,
                permission.ACCESS_FINE_LOCATION,
                permission.BLUETOOTH_ADVERTISE,
                permission.BLUETOOTH_CONNECT,
                permission.BLUETOOTH_SCAN,
                permission.UWB_RANGING,
                permission.RANGING
            )
        val permissionsToRequest: MutableList<String?> = ArrayList()

        for (permission in requiredPermissions) {
            if (ActivityCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(permission)
            }
        }
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                this@ChannelSoundingActivity,
                permissionsToRequest.toTypedArray<String?>(),
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String?>,
        grantResults: IntArray,
        deviceId: Int
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults, deviceId)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            var allGranted = true
            for (r in grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    CustomToastManager.show(
                        this@ChannelSoundingActivity,
                        "All permissions are required"
                    )
                    allGranted = false
                    break
                }
            }
            if (!allGranted) {
                CustomToastManager.show(this@ChannelSoundingActivity, "Permissions not granted")
                finish()
            }
        }
    }

    override fun onBackHandler() {
        binding.configureContainer.visibility = android.view.View.GONE
        binding.channelSoundingActivityContainer.visibility = android.view.View.VISIBLE
    }

    @RequiresPermission(permission.BLUETOOTH_CONNECT)
    private fun deviceInfo(gatt: BluetoothGatt?) {
        val device: BluetoothDevice = gatt?.device!!
        Timber.tag(TAG).d("CS Device ${device.address} is connected")
        gatt.setPreferredPhy(
            BluetoothDevice.PHY_LE_1M_MASK,
            BluetoothDevice.PHY_LE_1M_MASK,
            BluetoothDevice.PHY_OPTION_NO_PREFERRED
        )
        gatt.requestMtu(GATT_MTU_SIZE)
        viewModelBle.connectedDevices.add(device)
        viewModelBle.connectedGatts.add(gatt)
        viewModelBle.setGattState(
            ChannelSoundingConstant.GattState.CONNECTED
        )
        viewModelBle.setTargetDevice(device)

    }


    @RequiresPermission(permission.BLUETOOTH_CONNECT)
    override fun onDestroy() {
        stallDialogCount = 0
        stallMonitorJob?.cancel()
        highConfidenceGraceWindowJob?.cancel()
        poorSignalJob?.cancel()
        isTooClose = false
        super.onDestroy()
        // Dismiss progress dialog if still showing
        if (pairingProgressDialog != null && pairingProgressDialog!!.isShowing) {
            pairingProgressDialog!!.dismiss()
            pairingProgressDialog = null
        }
        unregisterReceiver(bluetoothReceiver)
        if (targetedDevice != null) {
            removeBond(targetedDevice!!)
        }
        service?.unregisterGattCallback()
        gatt?.close()
    }

    private val bluetoothReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.BAKLAVA)
        override fun onReceive(context: Context, intent: Intent?) {
            Timber.tag("BroadcastReceiver").d("CS BluetoothReceiver action: ${intent?.action}")
            if (intent == null || intent.action == null) return
            val action = intent.action
            when (action) {

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {

                    val device: BluetoothDevice? = intent.getParcelableExtra(
                        BluetoothDevice.EXTRA_DEVICE,
                        BluetoothDevice::class.java
                    )
                    device?.let {
                        try {
                            Timber.tag(TAG)
                                .d("CS Bluetooth Device Name:${it.name} - Device Address: ${it.address}")
                        } catch (exe: SecurityException) {
                            Timber.tag(TAG).e("CS Security Exception: ${exe.message}")
                        }
                    }

                    val bondState = intent.getIntExtra(
                        BluetoothDevice.EXTRA_BOND_STATE,
                        BluetoothDevice.BOND_NONE
                    )
                    val prevBondState = intent.getIntExtra(
                        BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                        BluetoothDevice.BOND_NONE
                    )
                    when (bondState) {
                        BluetoothDevice.BOND_BONDING -> {
                            Timber.tag(TAG).d("CS Bonding in progress...")
                            runOnUiThread {
                                // Show progress dialog when pairing starts
                                if (pairingProgressDialog == null || !pairingProgressDialog!!.isShowing) {
                                    pairingProgressDialog =
                                        CustomProgressDialog(this@ChannelSoundingActivity)
                                    pairingProgressDialog!!.window?.setBackgroundDrawable(
                                        ColorDrawable(Color.TRANSPARENT)
                                    )
                                    pairingProgressDialog!!.setMessage(getString(R.string.channel_sounding_waiting_for_pairing))
                                    pairingProgressDialog!!.setCanceledOnTouchOutside(false)
                                    pairingProgressDialog!!.show()
                                }
                            }
                        }

                        BluetoothDevice.BOND_BONDED -> {
                            Timber.tag(TAG).d("CS Bonded successfully")
                            isDeviceBonded = true

                            runOnUiThread {
                                // Dismiss progress dialog when pairing is successful
                                if (pairingProgressDialog != null && pairingProgressDialog!!.isShowing) {
                                    pairingProgressDialog!!.dismiss()
                                    pairingProgressDialog = null
                                }
                            }

                            CustomToastManager.showSuccess(
                                this@ChannelSoundingActivity,
                                "CS Bonded successfully"
                            )

                            // Auto-start if session is stopped
                            val currentState = viewModelDM.getSessionState().value
                            if (currentState == ChannelSoundingConstant.RangeSessionState.STOPPED ||
                                currentState == ChannelSoundingConstant.RangeSessionState.STOPPING) {
                                isManualStop = false
                                freqInfo = ChannelSoundingConfigureFragment.getFrequency(this@ChannelSoundingActivity) ?: HIGH_FREQUENCY
                                durationInfo = ChannelSoundingConfigureFragment.getDuration(this@ChannelSoundingActivity)?.toInt() ?: 0
                                viewModelDM.channelSoundingStart(TECHNOLOGY, freqInfo, durationInfo)
                                lifecycleScope.launch {
                                    withContext(Dispatchers.Main) {
                                        binding.rippleAnimation.startRippleAnimation()
                                    }
                                }
                            } else {
                                Timber.tag(TAG).d("CS Skipping auto-start after bonding - session already in progress: $currentState")
                            }
                        }

                        BluetoothDevice.BOND_NONE -> {
                            Timber.tag(TAG).d("CS Bonding failed or broken")
                            isDeviceBonded = false

                            runOnUiThread {
                                // Dismiss progress dialog if bonding fails
                                if (pairingProgressDialog != null && pairingProgressDialog!!.isShowing) {
                                    pairingProgressDialog!!.dismiss()
                                    pairingProgressDialog = null
                                }
                            }
                            // Show connection lost dialog
                            Timber.tag(TAG).e("CS_DBG_001 BOND_NONE -> showConnectionLostDialog (ChannelSoundingActivity bond state)")
                            showConnectionLostDialog()
                        }
                    }
                    Timber.tag("BroadcastReceiver")
                        .d("CS Bond state changed for device ${device?.address} from $prevBondState to $bondState")
                }
            }

        }
    }

    private fun showReconnectionMessage() {
        showMessages(R.string.connection_failed_reconnecting)
    }

    private fun showMessages(strResId: Int) {
        runOnUiThread {
            CustomToastManager.show(this@ChannelSoundingActivity, getString(strResId), 5000)
        }
    }

    private fun disconnectMessage() {
        showMessages(R.string.connection_failed)
    }

    private inner class GattProcessor : TimeoutGattCallback() {
        @RequiresPermission(permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Timber.tag(TAG).d("onConnectionStateChange status:$status, newState:$newState")

            val device = gatt.device
            Timber.tag(TAG).d("CS Device ${device.address} changed state to $newState")
            if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Timber.tag(TAG).e("CS_DBG_002 GATT_DISCONNECTED -> handling BLE disconnection")
                isDeviceBonded = false
                viewModelBle.setGattState(
                    ChannelSoundingConstant.GattState.DISCONNECTED
                )
                viewModelBle.connectedDevices.remove(device)
                viewModelBle.connectedGatts.remove(gatt)
                
                // Show connection lost dialog for actual BLE disconnection
                Timber.tag(TAG).e("CS Device disconnected - showing connection lost dialog")
                showConnectionLostDialog()

                gatt.close()
            }

        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Timber.tag(TAG).d("CS MTU changed to: $mtu")
            } else {
                Timber.tag(TAG).e("CS MTU change failed: $status")
            }
        }

        @RequiresPermission(permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                for (service: BluetoothGattService in gatt.services) {
                    if (service.uuid == ChannelSoundingConstant.OOB_SERVICE) {
                        Timber.tag(TAG).e("CS Listening for PSM characteristics change")
                        val characteristic: BluetoothGattCharacteristic? =
                            service.getCharacteristic(ChannelSoundingConstant.OOB_PSM_CHARACTERISTICS)
                        if (characteristic == null) {
                            Timber.tag(TAG).e("CS Failed to get PSM characteristic")
                            return
                        }
                        gatt.setCharacteristicNotification(characteristic, true)
                    }
                }
            } else {
                Timber.tag(TAG).e("CS Service discovery failed: $status")
            }
        }


    }

    private fun removeBond(device: BluetoothDevice): Boolean {
        return try {
            return device::class.java.getMethod(REMOVE_BONDING).invoke(device) as Boolean
        } catch (e: Exception) {
            Timber.tag(TAG).e("CS removeBond exception: ${e.message}")
            false
        }
    }

    /**
     * Setup the distance chart with initial configuration for dual-line display.
     * Red line = Raw Distance (hidden by default), Green line = Kalman Filtered Distance
     * Includes marker view for tap-to-show spike/smoothing annotations.
     */
    private fun setupDistanceChart() {
        // Initialize the marker view for spike/smoothing annotations
        distanceChartMarkerView = DistanceChartMarkerView(this).apply {
            chartView = binding.distanceChart
            setSpikeThreshold(0.3f) // 0.3 meter threshold for spike detection
        }

        binding.distanceChart.apply {
            description.isEnabled = false
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(true)
            setDrawGridBackground(false)
            setNoDataText("Waiting for distance measurements...")

            // Enable highlighting on tap to show marker
            isHighlightPerTapEnabled = true
            isHighlightPerDragEnabled = false

            // Set the marker view
            marker = distanceChartMarkerView

            // Configure X axis (time in seconds)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(true)
                granularity = 1f
                textColor = Color.DKGRAY
                axisMinimum = 0f
                // Add X-axis label formatter showing time in seconds
                setDrawLabels(true)
                valueFormatter = IAxisValueFormatter { value, _ -> "${value.toInt()} s" }
            }

            // Configure left Y axis (distance in meters)
            axisLeft.apply {
                setDrawGridLines(true)
                textColor = Color.DKGRAY
                axisMinimum = 0f
                // Add Y-axis label formatter showing distance in meters
                setDrawLabels(true)
                valueFormatter = IAxisValueFormatter { value, _ -> String.format(Locale.US, "%.2f m", value) }
            }

            // Disable right Y axis
            axisRight.isEnabled = false

            // Configure legend
            legend.apply {
                isEnabled = true
                textColor = Color.DKGRAY
                textSize = 12f
            }

            // Enable description for axis labels explanation
            description.apply {
                isEnabled = true
                text = getString(R.string.channel_sounding_chart_x_axis_label) + " vs " + getString(R.string.channel_sounding_chart_y_axis_label)
                textColor = Color.DKGRAY
                textSize = 10f
            }

            // Initialize with empty data
            data = LineData()
            invalidate()
        }
        Timber.tag(TAG).d("CS Distance chart initialized with marker view")
    }

    /**
     * Update the distance chart with new data points.
     * Creates LineDataSet objects: red for raw (hidden by default), green for Kalman filtered.
     * Spike markers shown as dotted red circles (hidden by default).
     * Preserves all measurement history from the session start.
     * Updates marker view with data points for spike/smoothing annotations.
     */
    private fun updateDistanceChart(dataPoints: List<ChannelSoundingDistanceMeasurementViewModel.ChartDataPoint>) {
        if (dataPoints.isEmpty()) return

        // Update the marker view with current data points for spike/smoothing detection
        distanceChartMarkerView?.setChartDataPoints(
            dataPoints.map {
                DistanceChartMarkerView.ChartDataPointInfo(
                    timestamp = it.timestamp,
                    rawDistance = it.rawDistance,
                    filteredDistance = it.filteredDistance
                )
            }
        )

        // Create entries for raw distance (green line)
        val rawEntries = dataPoints.map { Entry(it.timestamp, it.rawDistance) }

        // Create entries for Kalman filtered distance (red line)
        val filteredEntries = dataPoints.map { Entry(it.timestamp, it.filteredDistance) }

        // Detect spike points for visual highlighting
        val spikeThreshold = 0.3f
        val spikeEntries = mutableListOf<Entry>()
        dataPoints.forEach { point ->
            val deviation = kotlin.math.abs(point.rawDistance - point.filteredDistance)
            if (deviation > spikeThreshold) {
                spikeEntries.add(Entry(point.timestamp, point.rawDistance))
            }
        }

        // Create raw distance dataset (red, thinner line) - label includes unit for clarity
        // Only visible when showRawDistance checkbox is enabled
        val rawDataSet = LineDataSet(rawEntries, "Raw (m)").apply {
            color = Color.parseColor("#F44336")  // Red color
            lineWidth = 1.5f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER  // Smooth line
            cubicIntensity = 0.2f
            isHighlightEnabled = true
            highLightColor = Color.parseColor("#FF9800")  // Orange highlight
            isVisible = showRawDistance  // Controlled by checkbox
        }

        // Create Kalman filtered dataset (green, thicker line) - label includes unit for clarity
        val filteredDataSet = LineDataSet(filteredEntries, "Filtered (m)").apply {
            color = Color.parseColor("#4CAF50")  // Green color
            lineWidth = 2f
            setDrawCircles(false)
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER  // Smooth line
            cubicIntensity = 0.2f
            isHighlightEnabled = true
            highLightColor = Color.parseColor("#FF9800")  // Orange highlight
        }

        // Create spike points dataset (dotted red) - only shown if checkbox is enabled and there are spikes
        val dataSets = mutableListOf<LineDataSet>(rawDataSet, filteredDataSet)
        if (spikeEntries.isNotEmpty() && showSpikes) {
            val spikeDataSet = LineDataSet(spikeEntries, "⚠️ Spikes").apply {
                color = Color.parseColor("#F44336")  // Red color
                lineWidth = 0f  // No line, only circles
                setDrawCircles(true)
                circleRadius = 5f
                setCircleColor(Color.parseColor("#F44336"))  // Red circles
                circleHoleRadius = 2f
                setCircleColorHole(Color.WHITE)
                setDrawValues(false)
                isHighlightEnabled = true
                highLightColor = Color.parseColor("#FF9800")
                // Enable dashed line effect for spike markers
                enableDashedLine(10f, 5f, 0f)
                enableDashedHighlightLine(10f, 5f, 0f)
            }
            dataSets.add(spikeDataSet)
        }

        // Update chart with all datasets
        val lineData = LineData(dataSets.toList())
        binding.distanceChart.apply {
            data = lineData

            // Auto-scale Y axis based on visible data to prevent outliers from distorting chart
            // Only include raw distance in scaling if it's visible
            val visibleDistances = if (showRawDistance) {
                dataPoints.flatMap { listOf(it.rawDistance, it.filteredDistance) }
            } else {
                dataPoints.map { it.filteredDistance }
            }
            val maxDistance = visibleDistances.maxOrNull() ?: 10f
            val minDistance = visibleDistances.minOrNull() ?: 0f
            axisLeft.axisMaximum = (maxDistance * 1.1f).coerceAtLeast(1f)
            axisLeft.axisMinimum = (minDistance * 0.9f).coerceAtLeast(0f)

            // Update X-axis maximum to show all data points
            val lastTimestamp = dataPoints.last().timestamp
            xAxis.axisMaximum = lastTimestamp.coerceAtLeast(30f)

            // Keep visible window to last 30 seconds if we have more data, but all data is preserved
            if (lastTimestamp > 30f) {
                setVisibleXRangeMaximum(30f)
                moveViewToX(lastTimestamp - 25f)
            }

            notifyDataSetChanged()
            invalidate()
        }
    }

    /**
     * Gets the confidence score as a readable string based on the confidence value
     * @param confidence The confidence value (0 = LOW, 1 = MEDIUM, 2 = HIGH)
     * @return String representation of the confidence level
     */
    private fun getConfidenceScore(confidence: Int): String {
        return when (confidence) {
            0 -> CONFIDENCE_LOW
            1 -> CONFIDENCE_MEDIUM
            2 -> CONFIDENCE_HIGH
            else -> {
                Timber.tag(TAG).w("Unknown confidence value: $confidence")
                CONFIDENCE_LOW // Default to LOW for unknown values
            }
        }
    }

    private fun updateRippleColor(distance: Double) {
        val color = when (distance) {
            in rangeLow -> ContextCompat.getColor(this, R.color.silabs_rebranding_2373_fly_bar_gradient)
            in rangeMid -> ContextCompat.getColor(this, R.color.ch_medium_color)
            else -> ContextCompat.getColor(this, R.color.silabs_rebranding_2373_fly_bar_gradient)
        }

        binding.rippleAnimation.setRippleColor(color)
    }

    /**
     * Shows a connection lost dialog when the device disconnects.
     * Directs users to Bluetooth Settings to manually remove the bond and restart.
     */
    private fun showConnectionLostDialog() {
        runOnUiThread {
            Timber.tag(TAG).e("CS_DBG_003 showConnectionLostDialog entered (ChannelSoundingActivity)")
            // Don't show if activity is finishing or already showing
            if (isFinishing || isDestroyed || connectionLostDialog?.isShowing == true) {
                Timber.tag(TAG).w("CS_DBG_003 showConnectionLostDialog skipped (finishing/destroyed/already showing)")
                return@runOnUiThread
            }

            // Stop any ongoing session
            viewModelDM.channelSoundingStop()

            connectionLostDialog = MaterialAlertDialogBuilder(this@ChannelSoundingActivity)
                .setTitle(R.string.channel_sounding_connection_lost_title)
                .setMessage(R.string.channel_sounding_connection_lost_message)
                .setCancelable(false)
                .setPositiveButton(R.string.channel_sounding_go_to_demo) { dialog, _ ->
                    dialog.dismiss()
                    finish()
                }
                .show()
        }
    }

    /**
     * Check if the BLE connection to the device is still active.
     * This is used to differentiate between "CS session stopped" vs "BLE connection lost".
     * @return true if BLE GATT connection is still connected, false otherwise
     */
    @SuppressLint("MissingPermission")
    private fun checkBleConnectionState(): Boolean {
        val device = gatt?.device ?: return false
        return try {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val connectionState = bluetoothManager.getConnectionState(device, BluetoothProfile.GATT)
            val isConnected = connectionState == BluetoothProfile.STATE_CONNECTED
            Timber.tag(TAG).d("CS_DBG_028 BLE connection state check: device=${device.address}, state=$connectionState, isConnected=$isConnected")
            isConnected
        } catch (e: Exception) {
            Timber.tag(TAG).e("CS_DBG_028 BLE connection state check failed: ${e.message}")
            false
        }
    }

    /**
     * Monitors for 10s without HIGH-confidence data. During 2–10s shows "Waiting for high-confidence…" toast once.
     * After 10s shows "Out of Measurements" (no-high-confidence) dialog; on OK restarts CS session (no retries).
     */
    private fun startHighConfidenceGraceWindowMonitor() {
        highConfidenceGraceWindowJob = lifecycleScope.launch {
            Timber.tag(TAG).d("CS HIGH-confidence grace window monitor started")
            while (viewModelDM.getSessionState().value == ChannelSoundingConstant.RangeSessionState.STARTED) {
                delay(2000) // Check every 2 seconds
                val currentState = viewModelDM.getSessionState().value
                if (currentState != ChannelSoundingConstant.RangeSessionState.STARTED) break
                if (lastHighConfidenceTimestamp == 0L) continue // No HIGH received yet, skip
                val now = System.currentTimeMillis()
                val timeSinceLastHigh = now - lastHighConfidenceTimestamp
                when {
                    timeSinceLastHigh >= HIGH_CONFIDENCE_GRACE_WINDOW_MS -> {
                        Timber.tag(TAG).e("CS No HIGH confidence for 10s - showing Out of Measurements dialog")
                        isManualStop = false
                        pendingRestart = false
                        stoppedDueToStallMaxRetries = true
                        viewModelDM.channelSoundingStop()
                        showNoHighConfidenceDialog()
                        break
                    }
                    timeSinceLastHigh >= HIGH_CONFIDENCE_WAITING_TOAST_THRESHOLD_MS && !showedWaitingToastInCurrentWindow -> {
                        showedWaitingToastInCurrentWindow = true
                        runOnUiThread {
                            CustomToastManager.show(
                                this@ChannelSoundingActivity,
                                getString(R.string.channel_sounding_waiting_for_high_confidence),
                                3000
                            )
                        }
                    }
                }
            }
            Timber.tag(TAG).d("CS HIGH-confidence grace window monitor exited")
        }
    }

    private fun startMeasurementStallMonitor() {
        stallMonitorJob = lifecycleScope.launch {
            Timber.tag(TAG).d("CS Stall monitor started")
            while (viewModelDM.getSessionState().value == ChannelSoundingConstant.RangeSessionState.STARTED) {
                delay(3000) // Check every 3 seconds

                val currentState = viewModelDM.getSessionState().value
                if (currentState == ChannelSoundingConstant.RangeSessionState.STARTED) {
                    val currentTime = System.currentTimeMillis()
                    val timeSinceLastMeasurement = currentTime - lastMeasurementTime
                    val sessionUptimeMs = currentTime - measurementStartTime

                    Timber.tag(TAG).d("CS Stall monitor: time since last measurement = ${timeSinceLastMeasurement}ms")

                    // If no measurements received for 8 seconds, show restart dialog
                    // Skip during grace period after session start
                    if (timeSinceLastMeasurement > 8000 && measurementStartTime > 0L &&
                        sessionUptimeMs > STALL_GRACE_PERIOD_MS) {
                        Timber.tag(TAG).e("CS Measurement stall detected - no data for 8+ seconds")
                        
                        // Stop session and show dialog asking user to restart
                        isManualStop = false
                        pendingRestart = false
                        stoppedDueToStallMaxRetries = true
                        viewModelDM.channelSoundingStop()
                        
                        // Show dialog informing user to restart the session
                        // Increment counter and show appropriate dialog
                        stallDialogCount++
                        showSessionStallDialog()
                        break
                    }
                }
            }
            Timber.tag(TAG).d("CS Stall monitor exited")
        }
    }
    
    /**
     * Shows a dialog when measurements are stuck.
     * RECONNECT button restarts the CS session to get fresh measurements.
     */
    private var sessionStallDialog: androidx.appcompat.app.AlertDialog? = null
    
    private fun showSessionStallDialog() {
        runOnUiThread {
            Timber.tag(TAG).d("CS Showing session stall dialog")
            
            // Don't show if activity is finishing or already showing
            if (isFinishing || isDestroyed || sessionStallDialog?.isShowing == true) {
                return@runOnUiThread
            }
            if (stallDialogCount >= MAX_STALL_RETRIES) {
                // Third time - show "no HIGH confidence" dialog
                showNoHighConfidenceDialog()
            }else{
                Timber.tag(TAG).d("CS Showing stall dialog (attempt $stallDialogCount)")

                sessionStallDialog = MaterialAlertDialogBuilder(this@ChannelSoundingActivity)
                    .setTitle(R.string.channel_sounding_session_stall_title)
                    .setMessage(R.string.channel_sounding_session_stall_message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.channel_sounding_reconnect) { dialog, _ ->
                        dialog.dismiss()
                        // Restart CS session to get fresh measurements
                        reconnectAndStartSession()
                    }
                    .show()
            }

        }
    }
    private var noHighConfidenceDialog: androidx.appcompat.app.AlertDialog? = null

    private fun showNoHighConfidenceDialog() {
        runOnUiThread {

            if (isFinishing || isDestroyed || noHighConfidenceDialog?.isShowing == true) {
                return@runOnUiThread
            }

            // Stop session
            viewModelDM.channelSoundingStop()

            noHighConfidenceDialog = MaterialAlertDialogBuilder(this@ChannelSoundingActivity)
                .setTitle(R.string.channel_sounding_no_high_confidence_title)
                .setMessage(R.string.channel_sounding_no_high_confidence_message)
                .setCancelable(false)
                .setPositiveButton(R.string.channel_sounding_go_to_demo) { dialog, _ ->
                    dialog.dismiss()
                    stallDialogCount = 0  // Reset counter
                    finish()  // Go back to demo screen
                }
                .show()
        }
    }
    /**
     * Reconnects and starts a fresh CS session after stall.
     * Resets timing state and starts measurements.
     */
    private fun reconnectAndStartSession() {
        Timber.tag(TAG).d("CS Reconnecting and starting fresh session")
        
        // Reset timing state for fresh session
        isManualStop = false
        pendingRestart = false
        measurementStartTime = 0L
        lastMeasurementTime = 0L
        stoppedDueToStallMaxRetries = false
        viewModelDM.channelSoundingStop()
        lifecycleScope.launch {
            delay(2000)
            if (viewModelDM.getSessionState().value == ChannelSoundingConstant.RangeSessionState.STOPPED) {
                freqInfo = ChannelSoundingConfigureFragment.getFrequency(this@ChannelSoundingActivity) ?: HIGH_FREQUENCY
                durationInfo = ChannelSoundingConfigureFragment.getDuration(this@ChannelSoundingActivity)?.toInt() ?: 0
                viewModelDM.toggleStartStop(TECHNOLOGY, freqInfo, durationInfo)
            }
        }


        CustomToastManager.show(
            this@ChannelSoundingActivity,
            getString(R.string.channel_sounding_waiting_for_high_confidence),
            3000
        )
    }

    companion object {
        private val TAG = ChannelSoundingActivity::class.java.simpleName
        private const val GATT_MTU_SIZE = 512
        private const val TECHNOLOGY = "BLE_CS"
        private const val REQUEST_CODE_PERMISSIONS = 100
        /** Grace period after session start before stall monitor can trigger (e.g. after config return). */
        private const val STALL_GRACE_PERIOD_MS = 12_000L
        const val MEDIUM_FREQUENCY = "MEDIUM"
        const val HIGH_FREQUENCY = "HIGH"
        const val LOW_FREQUENCY = "LOW"
        const val CONFIDENCE_HIGH = "HIGH"
        const val CONFIDENCE_MEDIUM = "MEDIUM"
        const val CONFIDENCE_LOW = "LOW"
        /** Confidence value for HIGH (0=LOW, 1=MEDIUM, 2=HIGH). */
        private const val CONFIDENCE_VALUE_HIGH = 2
        /** Grace window: if no HIGH confidence for this many ms, show "Out of Measurements" and restart. */
        private const val HIGH_CONFIDENCE_GRACE_WINDOW_MS = 10_000L
        /** After this many ms without HIGH, show "Waiting for high-confidence…" toast once. */
        private const val HIGH_CONFIDENCE_WAITING_TOAST_THRESHOLD_MS = 2_000L
        private const val REMOVE_BONDING = "removeBond"
    }


}