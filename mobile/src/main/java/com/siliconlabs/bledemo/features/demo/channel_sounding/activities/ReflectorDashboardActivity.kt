package com.siliconlabs.bledemo.features.demo.channel_sounding.activities

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.base.activities.BaseActivity
import com.siliconlabs.bledemo.bluetooth.ble.GattCharacteristic
import com.siliconlabs.bledemo.bluetooth.ble.GattService
import com.siliconlabs.bledemo.databinding.ActivityReflectorDashboardBinding
import com.siliconlabs.bledemo.databinding.ItemGattCharacteristicBinding
import com.siliconlabs.bledemo.databinding.ItemGattServiceBinding
import com.siliconlabs.bledemo.features.demo.channel_sounding.services.DigitalKeyConnectionPhase
import com.siliconlabs.bledemo.features.demo.channel_sounding.services.ReflectorDigitalKeyLockState
import com.siliconlabs.bledemo.features.demo.channel_sounding.services.ReflectorMonitoringListener
import com.siliconlabs.bledemo.features.demo.channel_sounding.services.ReflectorProximityForegroundService
import com.siliconlabs.bledemo.features.demo.channel_sounding.utils.ReflectorBondLifecycleSentinel
import com.siliconlabs.bledemo.features.demo.channel_sounding.utils.ReflectorBondUtils
import com.siliconlabs.bledemo.features.demo.channel_sounding.utils.ReflectorFlowNavigator
import com.siliconlabs.bledemo.features.demo.channel_sounding.utils.ReflectorSessionPreferences
import com.siliconlabs.bledemo.features.demo.channel_sounding.viewmodels.ReflectorDashboardUiEvent
import com.siliconlabs.bledemo.features.demo.channel_sounding.viewmodels.ReflectorDashboardViewModel
import com.siliconlabs.bledemo.utils.AppUtil
import com.siliconlabs.bledemo.utils.CustomToastManager
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Digital Key Dashboard for Reflector mode. GATT and lock-state notifications are owned by
 * [ReflectorProximityForegroundService] so the BLE session survives UI teardown.
 */
@SuppressLint("MissingPermission")
class ReflectorDashboardActivity : BaseActivity() {

    private val dashboardViewModel: ReflectorDashboardViewModel by viewModels {
        ReflectorDashboardViewModel.Factory(application)
    }

    private lateinit var binding: ActivityReflectorDashboardBinding

    private var deviceAddress: String? = null
    private var deviceName: String? = null
    private var isReflectorActive = true
    private var linkUp: Boolean = false
    private var digitalKeySubscribed: Boolean = false
    private var csReflectorActive: Boolean = false
    private var lockState: ReflectorDigitalKeyLockState = ReflectorDigitalKeyLockState.UNKNOWN
    private var connectionPhase: DigitalKeyConnectionPhase = DigitalKeyConnectionPhase.DISCONNECTED
    private var phaseDetail: String? = null
    private var gattRecoverable: Boolean = false

    private var reflectorService: ReflectorProximityForegroundService? = null
    private var serviceBound = false
    private var previousLockState: ReflectorDigitalKeyLockState =
        ReflectorDigitalKeyLockState.UNKNOWN

    /** Recovery-toast debounce: pipeline reached LIVE at least once this screen session. */
    private var hadReachedLive: Boolean = false

    /** True between a range-loss disconnect and the next LIVE restore. */
    private var isRecoveringFromRangeLoss: Boolean = false
    private var shownDisconnectedToast: Boolean = false
    private var shownReconnectingToast: Boolean = false
    private var sessionLostDialog: AlertDialog? = null
    private var exitingAfterSessionLoss: Boolean = false

    /** Passed once to [ReflectorProximityForegroundService.start] after [beginNewDashboardSession]. */
    private var pendingServiceSessionReset: Boolean = false

    private val bondStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            } ?: return
            val addr = deviceAddress ?: return
            if (!addr.equals(device.address, ignoreCase = true)) return
            val bondState = intent.getIntExtra(
                BluetoothDevice.EXTRA_BOND_STATE,
                BluetoothDevice.BOND_NONE,
            )
            if (bondState == BluetoothDevice.BOND_NONE) {
                runOnUiThread { handlePermanentSessionLoss("bondRemovedBroadcast") }
            }
        }
    }

    private val bluetoothAdapterStateChangeListener: BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state =
                        intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_OFF) {
                        finish()
                    }
                }
            }
        }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val s = (service as ReflectorProximityForegroundService.LocalBinder).getService()
            reflectorService = s
            serviceBound = true
            s.addListener(monitoringListener)
            syncUiFromForegroundService(s)
            updateReflectorUI()
            applyConnectionLineUi()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            reflectorService = null
            serviceBound = false
        }
    }

    private val monitoringListener = object : ReflectorMonitoringListener {
        override fun onConnectionUiState(connected: Boolean) {
            linkUp = connected
            if (!connected) {
                previousLockState = lockState
                lockState = ReflectorDigitalKeyLockState.UNKNOWN
                if (hadReachedLive) {
                    dashboardViewModel.onLinkDisconnectedAfterLive()
                }
                if (exitingAfterSessionLoss || sessionLostDialog?.isShowing == true) {
                    pushConnectionSnapshotToViewModel()
                    applyConnectionLineUi()
                    return
                }
                if (hadReachedLive) {
                    isRecoveringFromRangeLoss = true
                    shownReconnectingToast = false
                    if (!shownDisconnectedToast) {
                        shownDisconnectedToast = true
                        runOnUiThread {
                            showMessage(
                                getString(R.string.reflector_toast_disconnected),
                            )
                        }

                    }
                }
            }
            pushConnectionSnapshotToViewModel()
            applyConnectionLineUi()
        }

        override fun onServicesDiscovered(services: List<BluetoothGattService>) {
            displayServices(services)
        }

        override fun onServiceDiscoveryFailed() {
            gattRecoverable = true
            showMessage(

                getString(R.string.reflector_dashboard_service_discovery_failed)
            )
            applyConnectionLineUi()
        }

        override fun onLockStateChanged(state: ReflectorDigitalKeyLockState) {
            val wasLocked = previousLockState != ReflectorDigitalKeyLockState.UNLOCKED
            val nowUnlocked = state == ReflectorDigitalKeyLockState.UNLOCKED
            if (wasLocked && nowUnlocked && linkUp) {
                binding.digitalKeyHeroCircle.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            }
            previousLockState = lockState
            lockState = state
            applyDigitalKeyHeroUi()
            pushConnectionSnapshotToViewModel()
            applyConnectionLineUi()
        }

        override fun onDigitalKeyPhaseChanged(phase: DigitalKeyConnectionPhase, detail: String?) {
            connectionPhase = phase
            phaseDetail = detail
            if (phase == DigitalKeyConnectionPhase.BONDING && hadReachedLive && !exitingAfterSessionLoss) {
                handlePermanentSessionLoss("phaseBondingAfterLive")
                return
            }
            when (phase) {
                DigitalKeyConnectionPhase.RECONNECTING -> {
                    if (isRecoveringFromRangeLoss && !shownReconnectingToast) {
                        shownReconnectingToast = true
                        runOnUiThread {
                            showMessage(
                                getString(R.string.reflector_toast_auto_reconnecting),

                                )
                        }

                    }
                }

                DigitalKeyConnectionPhase.LIVE -> {
                    if (isRecoveringFromRangeLoss) {
                        runOnUiThread {
                            showMessage(
                                getString(R.string.reflector_toast_reconnected),
                            )
                        }

                    }
                    hadReachedLive = true
                    isRecoveringFromRangeLoss = false
                    shownDisconnectedToast = false
                    shownReconnectingToast = false
                }

                else -> {}
            }
            pushConnectionSnapshotToViewModel()
            applyConnectionLineUi()
        }

        override fun onDigitalKeyGattUnavailable(recoverable: Boolean, message: String?) {
            digitalKeySubscribed = false
            gattRecoverable = recoverable
            val needsRePair = message?.contains("re-pair", ignoreCase = true) == true
            if (hadReachedLive && (!recoverable || needsRePair)) {
                handlePermanentSessionLoss("digitalKeyGattUnavailable")
                return
            }
            if (!recoverable) {
                showMessage(
                    message ?: getString(R.string.reflector_dashboard_digital_key_gatt_missing),
                )
            }
            pushConnectionSnapshotToViewModel()
            applyConnectionLineUi()
        }

        override fun onDigitalKeyNotifySubscribed(subscribed: Boolean) {
            digitalKeySubscribed = subscribed
            if (subscribed) {
                gattRecoverable = false
            }
            pushConnectionSnapshotToViewModel()
            applyConnectionLineUi()
        }

        override fun onCsReflectorActive(active: Boolean) {
            csReflectorActive = active
            applyConnectionLineUi()
        }

        override fun onReflectorSessionLost() {
            runOnUiThread { handlePermanentSessionLoss("foregroundServiceSessionLost") }
        }
    }

    private fun applyConnectionLineUi() {
        if (linkUp) {
            binding.connectionStatus.text = getString(R.string.reflector_dashboard_connected)
            binding.connectionStatus.setTextColor(
                ContextCompat.getColor(this, R.color.silabs_green)
            )
        } else {
            binding.connectionStatus.text = getString(R.string.reflector_dashboard_disconnected)
            binding.connectionStatus.setTextColor(
                ContextCompat.getColor(this, R.color.silabs_red)
            )
        }

        binding.keyStatus.text = phaseDetail ?: phaseToDefaultStatus(connectionPhase)

        binding.proximityStatus.text = proximityLineText()
        binding.proximityStatus.isClickable =
            gattRecoverable || connectionPhase == DigitalKeyConnectionPhase.SETUP_FAILED
        binding.proximityStatus.setOnClickListener {
            if (gattRecoverable || connectionPhase == DigitalKeyConnectionPhase.SETUP_FAILED) {
                reflectorService?.retryDigitalKeySetup()
                Toast.makeText(
                    this,
                    getString(R.string.reflector_dashboard_retrying_gatt),
                    Toast.LENGTH_SHORT
                )
            }
        }

        applyDigitalKeyHeroUi()
        updateDeviceCardLockIcon()
    }

    private fun phaseToDefaultStatus(phase: DigitalKeyConnectionPhase): String = when (phase) {
        DigitalKeyConnectionPhase.DISCONNECTED ->
            getString(R.string.reflector_dashboard_disconnected)

        DigitalKeyConnectionPhase.RECONNECTING ->
            getString(R.string.reflector_dashboard_awaiting_auto_reconnect)

        DigitalKeyConnectionPhase.DISCOVERING ->
            getString(R.string.reflector_dashboard_phase_discovering)

        DigitalKeyConnectionPhase.BONDING ->
            getString(R.string.reflector_dashboard_phase_bonding)

        DigitalKeyConnectionPhase.ENABLING_NOTIFICATIONS ->
            getString(R.string.reflector_dashboard_phase_enabling_notify)

        DigitalKeyConnectionPhase.STARTING_RANGING ->
            getString(R.string.reflector_dashboard_phase_starting_ranging)

        DigitalKeyConnectionPhase.LIVE ->
            getString(R.string.reflector_dashboard_phase_live)

        DigitalKeyConnectionPhase.SETUP_FAILED ->
            getString(R.string.reflector_dashboard_phase_setup_failed)
    }

    private fun proximityLineText(): String = when {
        connectionPhase == DigitalKeyConnectionPhase.RECONNECTING || !linkUp ->
            getString(R.string.reflector_dashboard_awaiting_auto_reconnect)

        connectionPhase == DigitalKeyConnectionPhase.SETUP_FAILED ->
            getString(R.string.reflector_dashboard_tap_to_retry)

        connectionPhase != DigitalKeyConnectionPhase.LIVE && !digitalKeySubscribed ->
            getString(R.string.reflector_dashboard_awaiting_digital_key)

        connectionPhase == DigitalKeyConnectionPhase.LIVE && !csReflectorActive &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA ->
            getString(R.string.reflector_dashboard_cs_reflector_inactive)

        lockState == ReflectorDigitalKeyLockState.UNKNOWN ->
            getString(R.string.reflector_dashboard_lock_state_unknown)

        lockState == ReflectorDigitalKeyLockState.LOCKED ->
            getString(R.string.reflector_dashboard_lock_state_locked)

        else -> getString(R.string.reflector_dashboard_lock_state_unlocked)
    }

    /**
     * Same rule as [ReflectorProximityForegroundService.buildNotification] padlock art:
     * unlocked only when the GATT link is up and BBCC reports 0x00.
     */
    private fun isDigitalKeyUnlockedForUi(): Boolean =
        linkUp && lockState == ReflectorDigitalKeyLockState.UNLOCKED

    private fun syncUiFromForegroundService(service: ReflectorProximityForegroundService) {
        linkUp = service.isGattConnected()
        lockState = service.getLockState()
        previousLockState = lockState
        digitalKeySubscribed = service.isDigitalKeyNotifySubscribed()
        csReflectorActive = service.isCsRangingActive()
        connectionPhase = service.getDigitalKeyPhase()
        isReflectorActive = service.isReflectorCsEnabled()
        if (connectionPhase == DigitalKeyConnectionPhase.LIVE) {
            hadReachedLive = true
        }
        pushConnectionSnapshotToViewModel()
    }

    private fun pushConnectionSnapshotToViewModel() {
        dashboardViewModel.updateConnectionSnapshot(
            hadReachedLive = hadReachedLive,
            linkUp = linkUp,
            connectionPhase = connectionPhase,
            foregroundServiceSessionLost = reflectorService?.isReflectorSessionLost() == true,
        )
    }

    private fun updateDeviceCardLockIcon() {
        val showUnlocked = isDigitalKeyUnlockedForUi()
        binding.deviceCardLockIcon.setImageResource(
            if (showUnlocked) R.drawable.door_unlock else R.drawable.door_lock
        )
        binding.deviceCardLockIcon.imageTintList = null
    }

    /** Hero padlock art: [R.drawable.door_unlock] when firmware reports unlock, else [R.drawable.door_lock]. */
    private fun digitalKeyHeroDrawableRes(): Int =
        if (linkUp && lockState == ReflectorDigitalKeyLockState.UNLOCKED) {
            R.drawable.door_unlock
        } else {
            R.drawable.door_lock
        }

    private fun applyDigitalKeyHeroUi() {
        val showUnlocked = isDigitalKeyUnlockedForUi()

        binding.digitalKeyHeroIcon.setImageResource(digitalKeyHeroDrawableRes())
        binding.digitalKeyHeroIcon.imageTintList = null

        when {
            showUnlocked -> {
                binding.digitalKeyHeroCircle.setBackgroundResource(R.drawable.reflector_hero_circle_locked)
                binding.digitalKeyHeroCaption.setText(R.string.reflector_dashboard_hero_caption_unlocked)
                binding.digitalKeyHeroIcon.contentDescription =
                    getString(R.string.reflector_dashboard_hero_unlocked_cd)
            }

            !linkUp -> {
                binding.digitalKeyHeroCircle.setBackgroundResource(R.drawable.reflector_hero_circle_locked)
                binding.digitalKeyHeroCaption.setText(R.string.reflector_dashboard_disconnected)
                binding.digitalKeyHeroIcon.contentDescription =
                    getString(R.string.reflector_dashboard_hero_locked_cd)
            }

            lockState == ReflectorDigitalKeyLockState.UNKNOWN &&
                    !digitalKeySubscribed -> {
                binding.digitalKeyHeroCircle.setBackgroundResource(R.drawable.reflector_hero_circle_locked)
                binding.digitalKeyHeroCaption.setText(R.string.reflector_dashboard_awaiting_digital_key)
                binding.digitalKeyHeroIcon.contentDescription =
                    getString(R.string.reflector_dashboard_hero_locked_cd)
            }

            else -> {
                binding.digitalKeyHeroCircle.setBackgroundResource(R.drawable.reflector_hero_circle_locked)
                binding.digitalKeyHeroCaption.setText(R.string.reflector_dashboard_hero_caption_locked)
                binding.digitalKeyHeroIcon.contentDescription =
                    getString(R.string.reflector_dashboard_hero_locked_cd)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReflectorDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyIntentExtras(intent)
        beginNewDashboardSession(
            resetForegroundService = intent.getBooleanExtra(EXTRA_FORCE_NEW_SESSION, false),
        )
        observeDashboardViewModelEvents()
        setupBackNavigation()

        val addr = deviceAddress
        if (addr.isNullOrBlank()) {
            showMessage(getString(R.string.reflector_pairing_no_device))
            finish()
            return
        }
        if (!ReflectorBondUtils.isDeviceBonded(addr)) {
            clearSavedReflectorSession()
            ReflectorFlowNavigator.openPairingFlow(this)
            finish()
            return
        }

        setupToolbar()
        setupUI()
        setupClickListeners()
        updateReflectorUI()
        applyConnectionLineUi()

        registerReceiver(
            bluetoothAdapterStateChangeListener,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        )
        registerBondStateReceiver()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val previousAddress = deviceAddress
        setIntent(intent)
        applyIntentExtras(intent)
        val addr = deviceAddress ?: return
        val addressChanged = !addr.equals(previousAddress, ignoreCase = true)
        beginNewDashboardSession(
            resetForegroundService = intent.getBooleanExtra(EXTRA_FORCE_NEW_SESSION, false) ||
                addressChanged,
        )
        val forceReset = pendingServiceSessionReset
        pendingServiceSessionReset = false
        ReflectorProximityForegroundService.start(this, addr, deviceName, forceReset)
    }

    override fun onStart() {
        super.onStart()
        if (exitingAfterSessionLoss) return
        val addr = deviceAddress ?: return
        val forceReset = pendingServiceSessionReset
        pendingServiceSessionReset = false
        ReflectorProximityForegroundService.start(this, addr, deviceName, forceReset)
        bindService(
            Intent(this, ReflectorProximityForegroundService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onResume() {
        super.onResume()
        reflectorService?.let { service ->
            service.addListener(monitoringListener)
            syncUiFromForegroundService(service)
        } ?: pushConnectionSnapshotToViewModel()
        if (handleBondOrSessionLossAtLifecycle(ReflectorBondLifecycleSentinel.LifecycleTrigger.RESUME)) {
            return
        }
        dashboardViewModel.startForegroundSessionProbe()
        reflectorService?.let {
            applyDigitalKeyHeroUi()
            applyConnectionLineUi()
        }
    }

    override fun onPause() {
        dashboardViewModel.stopForegroundSessionProbe()
        if (!handleBondOrSessionLossAtLifecycle(ReflectorBondLifecycleSentinel.LifecycleTrigger.PAUSE)) {
            dashboardViewModel.recordPauseCheckpoint()
        }
        super.onPause()
    }

    override fun onStop() {
        reflectorService?.removeListener(monitoringListener)
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        reflectorService = null
        super.onStop()
    }

    private fun applyIntentExtras(intent: Intent?) {
        deviceAddress = intent?.getStringExtra(EXTRA_DEVICE_ADDRESS) ?: deviceAddress
        deviceName = intent?.getStringExtra(EXTRA_DEVICE_NAME) ?: deviceName
        if (deviceAddress.isNullOrBlank()) {
            ReflectorSessionPreferences.getPairedDevice(this)?.let { (addr, name) ->
                deviceAddress = addr
                deviceName = name
            }
        }
    }

    private fun setupToolbar() {
        AppUtil.setEdgeToEdge(window, this)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            onDashboardBackPressed()
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    onDashboardBackPressed()
                }
            },
        )
    }

    private fun onDashboardBackPressed() {
        // Leave the Digital Key session running; user may return from the demo screen.
        finish()
    }

    /**
     * Prepares UI/ViewModel state for this dashboard visit.
     * @param resetForegroundService When true (first open after pairing), resets the foreground
     *   service session. When false (toolbar/system back then re-enter), keeps BLE + prefs intact.
     */
    private fun beginNewDashboardSession(resetForegroundService: Boolean) {
        exitingAfterSessionLoss = false
        isRecoveringFromRangeLoss = false
        shownDisconnectedToast = false
        shownReconnectingToast = false
        pendingServiceSessionReset = resetForegroundService
        if (resetForegroundService) {
            hadReachedLive = false
            dashboardViewModel.beginDashboardSession(deviceAddress)
        } else {
            dashboardViewModel.attachToExistingDashboardSession(deviceAddress)
        }
    }

    private fun observeDashboardViewModelEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                dashboardViewModel.uiEvents.collect { event ->
                    when (event) {
                        is ReflectorDashboardUiEvent.ShowSessionLostDialog ->
                            handlePermanentSessionLoss(event.reason)
                    }
                }
            }
        }
    }

    private fun setupUI() {
        binding.deviceName.text =
            deviceName ?: getString(R.string.reflector_dashboard_unknown_device)
        binding.deviceAddress.text = deviceAddress
    }

    private fun setupClickListeners() {
        binding.btnAction.setOnClickListener {
            toggleReflectorState()
        }

        binding.btnSettings.setOnClickListener {
            showMessage(
                getString(R.string.reflector_dashboard_settings_coming_soon),
            )
        }
    }

    private fun toggleReflectorState() {
        isReflectorActive = !isReflectorActive
        reflectorService?.setReflectorCsEnabled(isReflectorActive)
        updateReflectorUI()
        if (isReflectorActive && linkUp && !csReflectorActive) {
            showMessage(
                getString(R.string.reflector_dashboard_starting_cs_reflector),
            )
        }
    }

    private fun updateReflectorUI() {
        if (isReflectorActive) {
            binding.keyTitle.text = getString(R.string.reflector_dashboard_key_active)
            binding.statusDot.visibility = View.VISIBLE
            binding.btnAction.text = getString(R.string.reflector_dashboard_stop_reflector)
            binding.btnAction.setBackgroundColor(ContextCompat.getColor(this, R.color.silabs_red))
            binding.btnAction.setIconResource(R.drawable.ic_stop)
        } else {
            binding.keyTitle.text = getString(R.string.reflector_dashboard_key_inactive)
            binding.statusDot.visibility = View.GONE
            binding.btnAction.text = getString(R.string.reflector_dashboard_start_reflector)
            binding.btnAction.setBackgroundColor(ContextCompat.getColor(this, R.color.silabs_blue))
            binding.btnAction.setIconResource(R.drawable.ic_play)
        }
    }

    private fun displayServices(services: List<BluetoothGattService>) {
        binding.servicesContainer.removeAllViews()

        for (service in services) {
            val serviceBinding = ItemGattServiceBinding.inflate(
                LayoutInflater.from(this),
                binding.servicesContainer,
                false
            )

            val serviceName = getServiceName(service.uuid.toString())
            serviceBinding.serviceName.text = serviceName
            serviceBinding.serviceUuid.text = service.uuid.toString()

            val characteristicsContainer = serviceBinding.characteristicsContainer
            for (characteristic in service.characteristics) {
                addCharacteristicView(characteristicsContainer, characteristic)
            }

            var isExpanded = false
            serviceBinding.serviceHeader.setOnClickListener {
                isExpanded = !isExpanded
                characteristicsContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE
                serviceBinding.expandIcon.rotation = if (isExpanded) 180f else 0f
            }

            binding.servicesContainer.addView(serviceBinding.root)
        }
        binding.servicesLoading.visibility = View.GONE
    }

    private fun addCharacteristicView(
        container: LinearLayout,
        characteristic: BluetoothGattCharacteristic
    ) {
        val charBinding = ItemGattCharacteristicBinding.inflate(
            LayoutInflater.from(this),
            container,
            false
        )

        val charName = getCharacteristicName(characteristic.uuid.toString())
        charBinding.characteristicName.text = charName
        charBinding.characteristicUuid.text = characteristic.uuid.toString()

        addPropertyBadges(charBinding.propertiesContainer, characteristic.properties)

        container.addView(charBinding.root)
    }

    private fun addPropertyBadges(container: LinearLayout, properties: Int) {
        container.removeAllViews()

        if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
            addPropertyBadge(container, "READ", R.color.silabs_green)
        }
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
            addPropertyBadge(container, "WRITE", R.color.silabs_blue)
        }
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
            addPropertyBadge(container, "WRITE NO RESP", R.color.silabs_blue)
        }
        if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
            addPropertyBadge(container, "NOTIFY", R.color.silabs_red)
        }
        if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
            addPropertyBadge(container, "INDICATE", R.color.silabs_red)
        }
    }

    private fun addPropertyBadge(container: LinearLayout, text: String, colorResId: Int) {
        val badge = TextView(this).apply {
            this.text = text
            textSize = 10f
            setTextColor(ContextCompat.getColor(this@ReflectorDashboardActivity, colorResId))
            setBackgroundResource(R.drawable.property_badge_background)
            setPadding(16, 4, 16, 4)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = 8
            layoutParams = params
        }
        container.addView(badge)
    }

    private fun getServiceName(uuid: String): String {
        for (service in GattService.values()) {
            if (service.number.toString().equals(uuid, ignoreCase = true)) {
                return service.name.replace("_", " ")
            }
        }
        return when (uuid.lowercase().substring(4, 8)) {
            "1800" -> "Generic Access"
            "1801" -> "Generic Attribute"
            "180a" -> "Device Information"
            "180f" -> "Battery Service"
            "1812" -> "HID Service"
            "aabb" -> "CS Initiator Lock Service"
            else -> "Unknown Service"
        }
    }

    private fun getCharacteristicName(uuid: String): String {
        for (characteristic in GattCharacteristic.values()) {
            if (characteristic.uuid.toString().equals(uuid, ignoreCase = true)) {
                return characteristic.name.replace("_", " ").lowercase()
                    .replaceFirstChar { it.uppercase() }
            }
        }
        return when (uuid.lowercase().substring(4, 8)) {
            "2a00" -> "Device Name"
            "2a01" -> "Appearance"
            "2a04" -> "Peripheral Preferred Connection Parameters"
            "2a05" -> "Service Changed"
            "2a19" -> "Battery Level"
            "2a29" -> "Manufacturer Name String"
            "2a24" -> "Model Number String"
            "2a25" -> "Serial Number String"
            "2a26" -> "Firmware Revision String"
            "2a27" -> "Hardware Revision String"
            "2a28" -> "Software Revision String"
            "bbcc" -> "Lock State"
            else -> "Unknown Characteristic"
        }
    }

    /**
     * Bond removed from phone settings, board RESET, or other terminal session loss.
     * Clears the entire reflector session preference file immediately so a later demo
     * launch does not reopen a stale Digital Key pairing.
     */
    private fun handlePermanentSessionLoss(reason: String) {
        if (exitingAfterSessionLoss || isFinishing || isDestroyed) return
        if (sessionLostDialog?.isShowing == true) return
        Timber.tag(TAG).e("Permanent reflector session loss: %s", reason)
        dashboardViewModel.markSessionLossHandled()
        clearSavedReflectorSession()
        terminateCsSessionGracefully()
        showSessionLostDialog()
    }

    private fun clearSavedReflectorSession() {
        dashboardViewModel.clearAllStoredPreferences()
    }

    private fun showSessionLostDialog() {
        if (exitingAfterSessionLoss || isFinishing || isDestroyed) return
        if (sessionLostDialog?.isShowing == true) return
        runOnUiThread {
            isRecoveringFromRangeLoss = false
            sessionLostDialog = MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reflector_session_lost_title)
                .setMessage(R.string.reflector_session_lost_message)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    dialog.dismiss()
                    exitToDemoAfterSessionLoss()
                }
                .show()
        }
    }

    private fun terminateCsSessionGracefully() {
        isRecoveringFromRangeLoss = false
        if (isReflectorActive) {
            isReflectorActive = false
            reflectorService?.setReflectorCsEnabled(false)
            updateReflectorUI()
        }
    }

    /**
     * Detects bond removal from Bluetooth settings and board reset while the dashboard was
     * backgrounded (listener detached in [onStop]).
     */
    private fun handleBondOrSessionLossAtLifecycle(
        trigger: ReflectorBondLifecycleSentinel.LifecycleTrigger,
    ): Boolean {
        if (exitingAfterSessionLoss || isFinishing || isDestroyed) return false
        pushConnectionSnapshotToViewModel()
        if (dashboardViewModel.evaluateAtLifecycle(trigger)) {
            return true
        }
        return false
    }

    private fun registerBondStateReceiver() {
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bondStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(bondStateReceiver, filter)
        }
    }

    private fun exitToDemoAfterSessionLoss() {
        if (exitingAfterSessionLoss) return
        exitingAfterSessionLoss = true
        dashboardViewModel.clearPauseCheckpoint()
        dashboardViewModel.stopForegroundSessionProbe()
        clearSavedReflectorSession()
        ReflectorProximityForegroundService.stop(this)
        ReflectorFlowNavigator.openDemoScreen(this)
        finish()
    }

    override fun onDestroy() {
        sessionLostDialog?.dismiss()
        sessionLostDialog = null
        dashboardViewModel.stopForegroundSessionProbe()
        dashboardViewModel.clearPauseCheckpoint()
        reflectorService?.removeListener(monitoringListener)
        unregisterReceiver(bluetoothAdapterStateChangeListener)
        try {
            unregisterReceiver(bondStateReceiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    companion object {
        private val TAG = ReflectorDashboardActivity::class.java.simpleName
        const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
        const val EXTRA_DEVICE_NAME = "extra_device_name"
        /** Set when opening the dashboard immediately after pairing completes. */
        const val EXTRA_FORCE_NEW_SESSION = "extra_force_new_session"

        @JvmOverloads
        fun createIntent(
            context: Context,
            deviceAddress: String,
            deviceName: String?,
            forceNewSession: Boolean = false,
        ): Intent =
            Intent(context, ReflectorDashboardActivity::class.java).apply {
                putExtra(EXTRA_DEVICE_ADDRESS, deviceAddress)
                putExtra(EXTRA_DEVICE_NAME, deviceName)
                putExtra(EXTRA_FORCE_NEW_SESSION, forceNewSession)
            }
    }
}
