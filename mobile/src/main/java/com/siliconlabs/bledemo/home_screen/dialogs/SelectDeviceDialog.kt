package com.siliconlabs.bledemo.home_screen.dialogs

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.ScanFilter
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.text.Html
import android.text.Spannable
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import by.kirich1409.viewbindingdelegate.viewBinding
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.home_screen.adapters.ScannedDevicesAdapter
import com.siliconlabs.bledemo.base.activities.BaseActivity
import com.siliconlabs.bledemo.base.fragments.BaseDialogFragment
import com.siliconlabs.bledemo.home_screen.viewmodels.SelectDeviceViewModel
import com.siliconlabs.bledemo.bluetooth.ble.*
import com.siliconlabs.bledemo.bluetooth.services.BluetoothService
import com.siliconlabs.bledemo.bluetooth.services.BluetoothService.GattConnectType
import com.siliconlabs.bledemo.features.demo.blinky.activities.BlinkyActivity
import com.siliconlabs.bledemo.features.demo.connected_lighting.activities.ConnectedLightingActivity
import com.siliconlabs.bledemo.features.demo.health_thermometer.activities.HealthThermometerActivity
import com.siliconlabs.bledemo.features.demo.throughput.activities.ThroughputActivity
import com.siliconlabs.bledemo.features.demo.throughput.utils.PeripheralManager
import com.siliconlabs.bledemo.features.demo.thunderboard_demos.base.models.ThunderBoardDevice
import com.siliconlabs.bledemo.features.demo.thunderboard_demos.demos.blinky_thunderboard.activities.BlinkyThunderboardActivity
import com.siliconlabs.bledemo.databinding.DialogSelectDeviceBinding
import com.siliconlabs.bledemo.features.demo.channel_sounding.activities.ChannelSoundingActivity
import com.siliconlabs.bledemo.features.demo.channel_sounding.dialogs.ReflectorPairingDialog
import com.siliconlabs.bledemo.features.demo.esl_demo.activities.EslDemoActivity
import com.siliconlabs.bledemo.features.demo.thunderboard_demos.demos.environment.activities.EnvironmentActivity
import com.siliconlabs.bledemo.features.demo.thunderboard_demos.demos.motion.activities.MotionActivity
import com.siliconlabs.bledemo.features.demo.wifi_commissioning.activities.WifiCommissioningActivity
import com.siliconlabs.bledemo.features.iop_test.activities.IOPTestActivity
import com.siliconlabs.bledemo.features.iop_test.models.IOPTest
import com.siliconlabs.bledemo.home_screen.activities.MainActivity
import com.siliconlabs.bledemo.utils.CustomToastManager
import timber.log.Timber

@SuppressLint("MissingPermission")
class SelectDeviceDialog(
    private var bluetoothService: BluetoothService?
) : BaseDialogFragment(
    hasCustomWidth = true,
    isCanceledOnTouchOutside = true,
), ScannedDevicesAdapter.DemoDeviceCallback, BluetoothService.ScanListener {

    private val binding by viewBinding(DialogSelectDeviceBinding::bind)
    private lateinit var viewModel: SelectDeviceViewModel

    private lateinit var adapter: ScannedDevicesAdapter

    private var currentDeviceInfo: BluetoothDeviceInfo? = null
    private var connectType: GattConnectType? = null
    private var isReflectorMode: Boolean = false

    private var cachedBoardType: String? = null

    private var rangeTestCallback: RangeTestCallback? = null

    private val handler = Handler(Looper.getMainLooper())

    private val timeoutGattCallback = object : TimeoutGattCallback() {

        override fun onTimeout() {
            handleDisconnection(R.string.toast_connection_timed_out)
        }

        override fun onMaxRetriesExceeded(gatt: BluetoothGatt) {
            handleDisconnection(R.string.connection_failed)
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) handleSuccessfulConnection(gatt)
                }

                BluetoothGatt.STATE_DISCONNECTED -> {
                    when (status) {
                        133 -> showReconnectionMessage()
                        else -> handleDisconnection(R.string.connection_failed)
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            gatt?.readCharacteristic(getModelNumberCharacteristic(gatt))
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if (characteristic?.uuid == GattCharacteristic.ModelNumberString.uuid) {
                when (connectType) {
                    GattConnectType.MOTION -> launchDemo(characteristic.getStringValue(0))
                    GattConnectType.BLINKY -> {
                        when (characteristic.getStringValue(0)) {
                            ThunderBoardDevice.THUNDERBOARD_MODEL_DEV_KIT_V3,
                            ThunderBoardDevice.THUNDERBOARD_MODEL_SENSE,
                            ThunderBoardDevice.THUNDERBOARD_MODEL_DEV_KIT_V1,
                            ThunderBoardDevice.THUNDERBOARD_MODEL_DEV_KIT_V2 -> {
                                connectType = GattConnectType.BLINKY_THUNDERBOARD
                                cachedBoardType = characteristic.getStringValue(0)
                                gatt?.readCharacteristic(getPowerSourceCharacteristic(gatt))
                            }

                            ThunderBoardDevice.THUNDERBOARD_MODEL_BLUE_V1,
                            ThunderBoardDevice.THUNDERBOARD_MODEL_BLUE_V2 -> {
                                launchDemo(characteristic.getStringValue(0))
                            }

                            else -> {
                                showMessage(R.string.unknown_model)
                                (activity as MainActivity).dismissModalDialog()
                                Timber.d("Unknown model")
                            }
                        }
                    }

                    else -> Unit
                }
            } else if (characteristic?.uuid == GattCharacteristic.PowerSource.uuid) {
                launchDemo(
                    cachedBoardType,
                    characteristic.getIntValue(GattCharacteristic.PowerSource.format, 0)
                )
            }
        }
    }

    private fun getModelNumberCharacteristic(gatt: BluetoothGatt?): BluetoothGattCharacteristic? {
        return gatt?.getService(GattService.DeviceInformation.number)
            ?.getCharacteristic(GattCharacteristic.ModelNumberString.uuid)
    }

    private fun getPowerSourceCharacteristic(gatt: BluetoothGatt?): BluetoothGattCharacteristic? {
        return gatt?.getService(GattService.PowerSource.number)
            ?.getCharacteristic(GattCharacteristic.PowerSource.uuid)
    }

    private fun handleSuccessfulConnection(gatt: BluetoothGatt) {
        when (connectType) {
            GattConnectType.MOTION -> setReadingDialogMsgAndDiscoverServices(gatt)
            GattConnectType.BLINKY -> {
                if (gatt.device.name.startsWith(getString(R.string.blinky_service_name))) launchDemo()
                else setReadingDialogMsgAndDiscoverServices(gatt)
            }

            else -> launchDemo()
        }
        bluetoothService?.isNotificationEnabled = true
    }

    private fun setReadingDialogMsgAndDiscoverServices(gatt: BluetoothGatt) {
        (activity as MainActivity).setModalDialogMessage(R.string.reading_board_type)
        val success = gatt.discoverServices()
        if (!success) {
            // Handle unsuccessful service discovery
            showMessage(R.string.failed_to_discover_services)
            (activity as MainActivity).dismissModalDialog()
        }
    }

    private fun launchDemo(boardType: String? = null, powerSource: Int? = null) {
        getIntent(connectType)?.let { intent ->
            boardType?.let { intent.putExtra(MODEL_TYPE_EXTRA, it) }
            powerSource?.let { intent.putExtra(POWER_SOURCE_EXTRA, it) }
            currentDeviceInfo?.let { intent.putExtra(DEVICE_ADDRESS_EXTRA, it.device.address) }
            startActivity(intent)
        }
        (activity as BaseActivity).dismissModalDialog()
        stopDiscovery()
        dismiss()
    }

    private fun showReconnectionMessage() {
        (activity as BaseActivity).apply {
            showMessage(R.string.connection_failed_reconnecting)
        }
    }

    private fun handleDisconnection(@StringRes message: Int) {
        viewModel.clearDevices()
        (activity as BaseActivity).apply {
            dismissModalDialog()
            dismiss()
            showMessage(message)
        }
        bluetoothService?.isNotificationEnabled = true
    }


    override fun onAttach(context: Context) {
        super.onAttach(context)

        arguments?.let { args ->
            connectType = GattConnectType.values()[args.getInt(CONN_TYPE_INFO, 0)]
            isReflectorMode = args.getBoolean(IS_REFLECTOR_MODE, false)
        }

        viewModel = ViewModelProvider(this)[SelectDeviceViewModel::class.java]
        val showProtocolIcon = arguments?.getBoolean(SHOW_PROTOCOL_ICON, false) ?: false
        adapter = ScannedDevicesAdapter(
            mutableListOf(),
            this,
            showProtocolIcon = showProtocolIcon,
        ).also { it.setHasStableIds(true) }

        if (bluetoothService == null) {
            bluetoothService = (activity as MainActivity).bluetoothService
        }
    }

    override fun onDemoDeviceClicked(deviceInfo: BluetoothDeviceInfo) {
        when {
            // Handle Reflector mode - show pairing dialog
            isReflectorMode -> {
                dismiss()
                val pairingDialog = ReflectorPairingDialog.newInstance(
                    deviceAddress = deviceInfo.device.address,
                    deviceName = deviceInfo.name
                )
                pairingDialog.show(
                    (activity as MainActivity).supportFragmentManager,
                    ReflectorPairingDialog.TAG
                )
            }

            connectType == GattConnectType.RANGE_TEST -> {
                dismiss()
                rangeTestCallback?.getBluetoothDeviceInfo(deviceInfo)
            }

            connectType == GattConnectType.IOP_TEST -> {
                dismiss()
                IOPTest.createDataTest(deviceInfo.name, deviceInfo.address)
                getIntent(connectType)?.let {
                    activity?.startActivity(it)
                }
            }

            else -> connect(deviceInfo)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (parentFragment as? DialogInterface.OnDismissListener)?.onDismiss(dialog)
    }

    override fun onDetach() {
        stopDiscovery()
        super.onDetach()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let { args ->
            connectType = GattConnectType.values()[args.getInt(CONN_TYPE_INFO, 0)]
            isReflectorMode = args.getBoolean(IS_REFLECTOR_MODE, false)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonCancel.setOnClickListener {
            dialog?.dismiss()
            rangeTestCallback?.onCancel()
        }
        initializeRecyclerView()
        observeChanges()
        initDemoDescription()
    }

    override fun onCancel(dialog: DialogInterface) {
        rangeTestCallback?.onCancel()
        super.onCancel(dialog)
    }

    private fun initializeRecyclerView() {
        binding.list.apply {
            layoutManager = LinearLayoutManager(requireContext(), RecyclerView.VERTICAL, false)
            adapter = this@SelectDeviceDialog.adapter
        }
    }

    private fun observeChanges() {
        viewModel.apply {
            isScanningOn.observe(viewLifecycleOwner) {
                toggleScanning(it)
                toggleRefreshInfoRunnable(it)
            }
            isAnyDeviceDiscovered.observe(viewLifecycleOwner) {
                toggleListView(it)
            }
            numberOfDevices.observe(viewLifecycleOwner) {
                binding.devicesNumber.text = getString(R.string.DEVICE_LIST, it)
            }
            deviceToInsert.observe(viewLifecycleOwner) {
                adapter.updateList(viewModel.getScannedDevicesList().toMutableList())
            }
        }
    }

    /**
     * Renders HTML with clickable links, theme red text, and no underline (default [URLSpan] underlines).
     */
    private fun spannedHtmlWithRedLinks(html: String): CharSequence {
        val ctx = context ?: return html
        val spanned = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY) as Spannable
        val linkColor = ContextCompat.getColor(ctx, R.color.silabs_redtheme_button_text_color)
        spanned.getSpans(0, spanned.length, URLSpan::class.java).toList().forEach { span ->
            val start = spanned.getSpanStart(span)
            val end = spanned.getSpanEnd(span)
            val flags = spanned.getSpanFlags(span)
            val url = span.url
            spanned.removeSpan(span)
            spanned.setSpan(NoUnderlineUrlSpan(url, linkColor), start, end, flags)
        }
        return spanned
    }

    @SuppressLint("StringFormatInvalid")
    private fun initDemoDescription() {
        binding.dialogTextInfo.apply {
            text = when (connectType) {
                GattConnectType.THERMOMETER -> getString(
                    R.string.soc_must_be_connected,
                    getString(R.string.demo_firmware_name_health_thermometer)
                )

                GattConnectType.LIGHT -> getString(
                    R.string.soc_must_be_connected,
                    getString(R.string.demo_firmware_name_connected_lighting)
                )

                GattConnectType.RANGE_TEST -> getString(
                    R.string.soc_must_be_connected,
                    getString(R.string.demo_firmware_name_range_test)
                )

                GattConnectType.BLINKY -> getString(R.string.soc_blinky_must_be_connected)
                GattConnectType.THROUGHPUT_TEST -> getString(
                    R.string.soc_must_be_connected,
                    getString(R.string.demo_firmware_name_throughput)
                )

                GattConnectType.MOTION -> getString(R.string.soc_thunderboard_must_be_connected)
                GattConnectType.ENVIRONMENT -> getString(R.string.soc_thunderboard_must_be_connected)
                GattConnectType.IOP_TEST -> getString(
                    R.string.soc_must_be_connected,
                    getString(R.string.demo_firmware_name_iop)
                )

                GattConnectType.WIFI_COMMISSIONING -> {
                    spannedHtmlWithRedLinks(
                        getString(R.string.soc_wifi_commissioning_must_be_connected)
                    )
                }

                GattConnectType.ESL_DEMO -> getString(
                    R.string.soc_must_be_connected,
                    getString(R.string.demo_firmware_name_esl)
                )

                GattConnectType.DEV_KIT_SENSOR -> getString(
                    R.string.demo_firmware_name_wifi_commission,
                    getString(R.string.demo_firmware_name_esl)
                )

                GattConnectType.AWS_DEMO -> {
                    getString(R.string.aws_iot_selection_ble_title)
                }

                GattConnectType.SMART_LOCK -> {
                    getString(R.string.smart_lock_demo_dialog_select_dialog)
                }

                GattConnectType.CHANNEL_SOUNDING_DEMO -> {
                    if (isReflectorMode) {
                        getString(R.string.reflector_scan_dialog_description)
                    } else {
                        getString(
                            R.string.channel_sounding_demo_dialog_select_dialog,
                            getString(R.string.channel_sounding_config_demo)
                        )
                    }
                }

                else -> getString(R.string.empty_description)
            }

            if (connectType == GattConnectType.WIFI_COMMISSIONING
                || connectType == GattConnectType.AWS_DEMO
            ) {
                movementMethod = LinkMovementMethod.getInstance() // react to clicking the link
            }
        }
    }

    private fun toggleScanning(isOn: Boolean) {
        if (isOn) {
            startDiscovery()
        } else {
            stopDiscovery()
        }
    }


    private fun startDiscovery() {
        viewModel.clearDevices() // Clear previous scan results before starting a new scan
        bluetoothService?.let {
            it.removeListener(this)
            it.addListener(this)
            //it.startDiscovery(applyDemoFilters())
        }
        // Add a short delay before starting scan to allow BLE stack to recover
        handler.postDelayed({
            bluetoothService?.startDiscovery(applyDemoFilters())
        }, 300)
    }

    private fun stopDiscovery() {
        bluetoothService?.let {
            it.removeListener(this)
            it.stopDiscovery()
        }
    }

    private fun toggleListView(isAnyDeviceDiscovered: Boolean) {
        binding.apply {
            if (isAnyDeviceDiscovered) {
                list.visibility = View.VISIBLE
                noDevicesFound.visibility = View.GONE
                demoScanProgressBar.visibility = View.GONE
            } else {
                list.visibility = View.GONE
                noDevicesFound.visibility = View.VISIBLE
                demoScanProgressBar.visibility = View.INVISIBLE
            }
        }
    }

    private fun toggleRefreshInfoRunnable(isOn: Boolean) {
        handler.let {
            if (isOn) it.postDelayed(updateScanInfoRunnable, SelectDeviceViewModel.SCANNED_DEVICE_LIST_UI_INTERVAL_MS)
            else it.removeCallbacks(updateScanInfoRunnable)
        }
    }

    private val updateScanInfoRunnable = object : Runnable {
        override fun run() {
            adapter.updateList(viewModel.getScannedDevicesList().toMutableList())
            handler.postDelayed(this, SelectDeviceViewModel.SCANNED_DEVICE_LIST_UI_INTERVAL_MS)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.dialog_select_device, container, false)

    override fun onResume() {
        super.onResume()
        if (dialog?.window != null) {
            viewModel.setIsScanningOn(true)
        }
    }

    private fun applyDemoFilters() = buildList {
        val manufacturerDataFilter = ManufacturerDataFilter(
            id = 71,
            data = byteArrayOf(2, 0),
        )

        when (connectType) {
            GattConnectType.CHANNEL_SOUNDING_DEMO -> {
                if (isReflectorMode) {
                    // Reflector mode: Filter for "Silabs Example" devices (CS Initiator / Door Lock)
                    add(buildFilter(BuildFilterName.SILABS_EXAMPLE))
                }
                // else: No filter for initiator mode - shows all CS Reflector devices
            }

            GattConnectType.THERMOMETER -> {
                add(buildFilter(GattService.HealthThermometer))
            }

            GattConnectType.LIGHT -> {
                add(buildFilter(GattService.ProprietaryLightService))
                add(buildFilter(GattService.ZigbeeLightService))
                add(buildFilter(GattService.ConnectLightService))
                add(buildFilter(GattService.ThreadLightService))
                add(buildFilter(GattService.TheDMP))
                add(buildFilter(GattService.TheAmazonSideWalk))
            }

            GattConnectType.RANGE_TEST -> {
                add(buildFilter(GattService.RangeTestService))
            }

            /*GattConnectType.BLINKY -> {
                add(buildFilter(BuildFilterName.BLINKY))
                add(buildFilter(manufacturerDataFilter))
            }*/

            GattConnectType.THROUGHPUT_TEST -> {
                add(buildFilter(BuildFilterName.THROUGHPUT_TEST))
            }

            GattConnectType.WIFI_COMMISSIONING -> {
                add(buildFilter(BuildFilterName.BLE_CONFIGURATOR))
            }

            GattConnectType.IOP_TEST -> {
                add(buildFilter(BuildFilterName.IOP_TEST))
                add(buildFilter(BuildFilterName.IOP_TEST_UPDATE))
                add(buildFilter(BuildFilterName.IOP_TEST_NO_1))
                add(buildFilter(BuildFilterName.IOP_TEST_NO_2))
            }

            GattConnectType.MOTION,
            GattConnectType.ENVIRONMENT -> {
                add(buildFilter(manufacturerDataFilter))
            }

            GattConnectType.ESL_DEMO -> {
                add(buildFilter(GattService.EslDemoService))
            }

            GattConnectType.DEV_KIT_SENSOR -> {
                add(buildFilter(BuildFilterName.DEV_KIT_SENSOR))
            }

            GattConnectType.AWS_DEMO -> {
                add(buildFilter(BuildFilterName.BLE_CONFIGURATOR))
            }

            GattConnectType.SMART_LOCK -> {
                add(buildFilter(BuildFilterName.BLE_CONFIGURATOR))
            }



            else -> Unit
        }
    }

    private fun buildFilter(name: BuildFilterName) = ScanFilter
        .Builder().apply {
            setDeviceName(name.value)
        }.build()

    private fun buildFilter(service: GattService) = ScanFilter
        .Builder().apply {
            setServiceUuid(ParcelUuid(service.number), ParcelUuid(GattService.UUID_MASK))
        }.build()

    private fun buildFilter(manufacturerData: ManufacturerDataFilter) = ScanFilter
        .Builder().apply {
            manufacturerData.run { setManufacturerData(id, data, mask) }
        }.build()

    override fun onPause() {
        super.onPause()
        viewModel.setIsScanningOn(false)
    }

    private fun connect(deviceInfo: BluetoothDeviceInfo) {
        currentDeviceInfo = deviceInfo

        if (connectType == GattConnectType.THROUGHPUT_TEST) {
            PeripheralManager.advertiseThroughputServer(bluetoothService)
        }

        bluetoothService?.let { service ->
            stopDiscovery()
            (activity as BaseActivity).showModalDialog(BaseActivity.ConnectionStatus.CONNECTING) {
                currentDeviceInfo?.let { service.disconnectGatt(it.address) }
            }
            service.isNotificationEnabled = false
            service.connectGatt(deviceInfo.device, false, timeoutGattCallback)
        }
    }

    private fun getIntent(connectType: GattConnectType?): Intent? {
        val clazz = when (connectType) {
            GattConnectType.THERMOMETER -> HealthThermometerActivity::class.java
            GattConnectType.LIGHT -> determineLightActivityClass()
            GattConnectType.BLINKY -> BlinkyActivity::class.java
            GattConnectType.BLINKY_THUNDERBOARD -> BlinkyThunderboardActivity::class.java
            GattConnectType.THROUGHPUT_TEST -> ThroughputActivity::class.java
            GattConnectType.WIFI_COMMISSIONING -> WifiCommissioningActivity::class.java
            GattConnectType.IOP_TEST -> IOPTestActivity::class.java
            GattConnectType.MOTION -> MotionActivity::class.java
            GattConnectType.ENVIRONMENT -> EnvironmentActivity::class.java
            GattConnectType.ESL_DEMO -> EslDemoActivity::class.java
            GattConnectType.DEV_KIT_SENSOR -> WifiCommissioningActivity::class.java
            GattConnectType.AWS_DEMO -> WifiCommissioningActivity::class.java
            GattConnectType.SMART_LOCK -> WifiCommissioningActivity::class.java
            GattConnectType.CHANNEL_SOUNDING_DEMO -> ChannelSoundingActivity::class.java


            else -> null
        }
        val intent = clazz?.let { Intent(activity, it) }
        if (connectType == GattConnectType.WIFI_COMMISSIONING
            || connectType == GattConnectType.DEV_KIT_SENSOR
            || connectType == GattConnectType.AWS_DEMO
            || connectType == GattConnectType.SMART_LOCK
            || connectType == GattConnectType.LIGHT

        ) {
            intent?.putExtra("connectType", connectType)
        }
        return intent
    }

    // Decide which Activity to launch for LIGHT connect type based on advertised services.
    // Priority: TheDMP -> WifiCommissioningActivity; otherwise any listed lighting protocol -> ConnectedLightingActivity.
    private fun determineLightActivityClass(): Class<out Any>? {
        val uuids =
            currentDeviceInfo?.scanInfo?.scanRecord?.serviceUuids?.map { it.uuid } ?: return null
        val lightingServices = listOf(
            GattService.ProprietaryLightService.number,
            GattService.ConnectLightService.number,
            GattService.ThreadLightService.number,
            GattService.ZigbeeLightService.number,
            GattService.TheAmazonSideWalk.number
        )
        // Prefer lighting protocol services if any are present
        if (uuids.any { it in lightingServices }) return ConnectedLightingActivity::class.java
        // Fall back to DMP only if no lighting protocol service was found
        return if (uuids.contains(GattService.TheDMP.number)) WifiCommissioningActivity::class.java else null
    }

    // Returns true if current selected LIGHT device advertises the DMP service UUID (unused after refactor but kept if needed elsewhere)
    private fun isDmpLightDevice(): Boolean {
        val uuids = currentDeviceInfo?.scanInfo?.scanRecord?.serviceUuids ?: return false
        return uuids.any { it.uuid == GattService.TheDMP.number }
    }



    interface RangeTestCallback {
        fun onCancel()
        fun getBluetoothDeviceInfo(info: BluetoothDeviceInfo?)
    }

    fun setCallback(rangeTestCallback: RangeTestCallback) {
        this.rangeTestCallback = rangeTestCallback
    }

    override fun handleScanResult(scanResult: ScanResultCompat) {
        viewModel.handleScanResult(scanResult, connectType, context)
    }

    override fun onDiscoveryFailed() {
        viewModel.setIsScanningOn(false)
        dismiss()
    }

    override fun onDiscoveryTimeout() {
        /* Scanning through this dialog is always indefinite. */
        CustomToastManager.show(requireContext(), "Discovery Timeout", 4000)

    }

    private class NoUnderlineUrlSpan(
        url: String,
        private val linkColor: Int
    ) : URLSpan(url) {
        override fun updateDrawState(ds: TextPaint) {
            super.updateDrawState(ds)
            ds.isUnderlineText = false
            ds.color = linkColor
        }
    }

    companion object {
        private const val CONN_TYPE_INFO = "_conn_type_info_"
        private const val IS_REFLECTOR_MODE = "_is_reflector_mode_"
        /** When true, device rows show protocol icon ([android.R.id.icon2]) and RSSI ([android.R.id.icon]). */
        private const val SHOW_PROTOCOL_ICON = "_show_protocol_icon_"
        const val CONNECTION_SUPPORTED = "connection_supported"

        const val MODEL_TYPE_EXTRA = "model_type"
        const val POWER_SOURCE_EXTRA = "power_source"
        const val DEVICE_ADDRESS_EXTRA = "device_address"

        fun newDialog(
            connectType: GattConnectType?,
            service: BluetoothService? = null,
            showProtocolIcon: Boolean = connectType == GattConnectType.LIGHT,
        ): SelectDeviceDialog {
            return SelectDeviceDialog(service).apply {
                arguments = Bundle().apply {
                    connectType?.let { putInt(CONN_TYPE_INFO, connectType.ordinal) }
                    putBoolean(SHOW_PROTOCOL_ICON, showProtocolIcon)
                }
            }
        }

        /**
         * Creates a new dialog for Reflector mode scanning.
         * Filters for "Silabs Example" devices and shows ReflectorPairingDialog on selection.
         */
        fun newReflectorDialog(
            service: BluetoothService? = null
        ): SelectDeviceDialog {
            return SelectDeviceDialog(service).apply {
                arguments = Bundle().apply {
                    putInt(CONN_TYPE_INFO, GattConnectType.CHANNEL_SOUNDING_DEMO.ordinal)
                    putBoolean(IS_REFLECTOR_MODE, true)
                    putBoolean(SHOW_PROTOCOL_ICON, false)
                }
            }
        }

    }

    private enum class BuildFilterName(val value: String) {
        BLINKY("Blinky Example"),
        THROUGHPUT_TEST("Throughput Test"),
        BLE_CONFIGURATOR("BLE_CONFIGURATOR"),
        IOP_TEST("IOP Test"),
        IOP_TEST_UPDATE("IOP Test Update"),
        IOP_TEST_NO_1("IOP_Test_1"),
        IOP_TEST_NO_2("IOP_Test_2"),
        DEV_KIT_SENSOR("WIFI_SENSOR"),
        CHANNEL_SOUNDING_TEST("CS RFLCT"),
        SILABS_EXAMPLE("Silabs Example")
    }
}


