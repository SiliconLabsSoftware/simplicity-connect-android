package com.siliconlabs.bledemo.features.demo.range_test.activities

import android.annotation.SuppressLint
import android.bluetooth.*
import android.graphics.Typeface
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.base.activities.BaseDemoActivity
import com.siliconlabs.bledemo.bluetooth.ble.*
import com.siliconlabs.bledemo.bluetooth.services.BluetoothService
import com.siliconlabs.bledemo.databinding.ActivityRangeTestBinding
import com.siliconlabs.bledemo.features.demo.range_test.dialogs.RangeTestModeDialog
import com.siliconlabs.bledemo.features.demo.range_test.fragments.RangeTestFragment
import com.siliconlabs.bledemo.features.demo.range_test.models.*
import com.siliconlabs.bledemo.features.demo.range_test.presenters.RangeTestPresenter
import com.siliconlabs.bledemo.features.demo.range_test.presenters.RangeTestPresenter.Controller
import com.siliconlabs.bledemo.features.demo.range_test.presenters.RangeTestPresenter.RangeTestView
import com.siliconlabs.bledemo.home_screen.dialogs.SelectDeviceDialog
import com.siliconlabs.bledemo.utils.AppUtil
import com.siliconlabs.bledemo.utils.BLEUtils.setNotificationForCharacteristic
import com.siliconlabs.bledemo.utils.CustomToastManager
import com.siliconlabs.bledemo.utils.Notifications
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.abs

/**
 * @author Comarch S.A.
 */
@SuppressLint("MissingPermission")
class RangeTestActivity : BaseDemoActivity(), Controller {

    private var lowerLimit: Int = 0
    private var upperLimit: Int = 0
    private var descriptor2906: BluetoothGattDescriptor? = null
    private var activeDeviceId = 1

    private var advertisementHandler: RangeTestAdvertisementHandler? = null

    private var presenter = RangeTestPresenter()
    private var processor = GattProcessor()

    private var setupData = true
    private var timerStarted = false

    private var retryAttempts = 0
    private lateinit var binding: ActivityRangeTestBinding
    private var txUpdateTimer: TxUpdateTimer? = null


    inner class TxUpdateTimer(private val deviceAddress: String) :
        CountDownTimer(Long.MAX_VALUE, TRANSMISSION_INTERVAL) {
        override fun onTick(millisUntilFinished: Long) {
            /* Update sent packets when no onCharacteristicChanged callback invoked. */
            presenter.getDeviceByAddress(deviceAddress)?.let {
                it.packetsSent = it.packetsSent?.plus(1)
                presenter.onPacketSentUpdated(deviceAddress, it.packetsSent ?: 0)
            }
        }

        override fun onFinish() {
            // nothing
        }
    }

    private fun retryConnectionAttempt() {
        retryAttempts++
        runOnUiThread {
            Handler(Looper.getMainLooper()).postDelayed({
                service?.connectGatt(
                    presenter.getDeviceInfo()?.device!!,
                    false,
                    timeoutGattCallback
                )
            }, 1000)
        }
    }

    private val timeoutGattCallback = object : TimeoutGattCallback() {

        override fun onTimeout() {
            super.onTimeout()

            runOnUiThread {
                showMessage(R.string.toast_connection_timed_out)
                dismissModalDialog()
                handleConnectionError()
            }
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothAdapter.STATE_CONNECTED) {
                    retryAttempts = 0
                    initTest(service)
                }

            } else if (status == 133 && retryAttempts < RECONNECTION_RETRIES) {
                retryConnectionAttempt()
            } else {
                runOnUiThread {
                    showMessage(
                        ErrorCodes.getFailedConnectingToDeviceMessage(
                            presenter.getDeviceInfo()?.name,
                            status
                        )
                    )
                    dismissModalDialog()
                    handleConnectionError()
                }
            }
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRangeTestBinding.inflate(layoutInflater)

        setContentView(binding.root)
        //setContentView(R.layout.activity_range_test)
        prepareToolBar()
        setupUiListeners()
    }

    private fun prepareToolBar() {
        AppUtil.setEdgeToEdge(window, this)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setTitleTextAppearance(this, R.style.RangeTestToolbarTitle)
        binding.toolbar.setTitleTextColor(ContextCompat.getColor(this, R.color.silabs_white))
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setHomeAsUpIndicator(R.drawable.matter_back)
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.title = this.getString(R.string.title_Range_Test)
        }
    }

    private fun setupUiListeners() {

        binding.tvDevice1Tab.setOnClickListener {
            changeDevice(binding.tvDevice1Tab, 1)
        }

        binding.tvDevice2Tab.setOnClickListener {
            changeDevice(binding.tvDevice2Tab, 2)
        }

        binding.btnRangeTestEmptyConnect.setOnClickListener {
            showRangeTestSelectDeviceDialog(activeDeviceId)
        }
    }

    override fun onBluetoothServiceBound() {
        selectTab(binding.tvDevice1Tab)
        changeDevice(binding.tvDevice1Tab, 1)
    }


    private fun switchCurrentDevice() {
        activeDeviceId =
            if (activeDeviceId == 1) 2
            else 1
        presenter.switchCurrentDevice()
    }


    private fun changeDevice(tvTab: TextView, which: Int) {
        removeRangeTestFragment()
        activeDeviceId = which
        presenter.setCurrentDevice(which)
        runOnUiThread { selectTab(tvTab) }

        if (presenter.getDeviceInfo() == null) {
            showRangeTestSelectDeviceDialog(which)
        } else {
            showRangeTestFragment(presenter.getMode())
        }
    }

    private fun showRangeTestSelectDeviceDialog(which: Int) {
        val tvTab = if (which == 1) binding.tvDevice1Tab else binding.tvDevice2Tab
        SelectDeviceDialog.newDialog(BluetoothService.GattConnectType.RANGE_TEST, service)
            .apply {
                setCallback(object : SelectDeviceDialog.RangeTestCallback {
                    override fun getBluetoothDeviceInfo(info: BluetoothDeviceInfo?) {
                        runOnUiThread {
                            showModalDialog(ConnectionStatus.CONNECTING) {
                                presenter.getDeviceInfo()?.address?.let {
                                    service?.clearConnectedGatt()
                                    presenter.resetDeviceAt(which)
                                }
                                tvTab.text = getString(R.string.range_test_no_device)
                            }
                            tvTab.text = info?.device?.name
                        }

                        presenter.setDeviceInfo(info)
                        service?.let {
                            it.isNotificationEnabled = false
                            it.connectGatt(
                                presenter.getDeviceInfo()?.device!!,
                                false,
                                timeoutGattCallback
                            )
                        }
                    }

                    override fun onCancel() {
                        switchCurrentDevice()
                        updateRangeTestEmptyState()
                    }
                })
            }.show(supportFragmentManager, "select_device_dialog")
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            service?.registerGattCallback(false, null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        service?.disconnectAllGatts()
        service?.isNotificationEnabled = true
        advertisementHandler?.stopListening()
        advertisementHandler = null
    }

    override fun setView(view: RangeTestView?) {
        presenter.setView(view)
    }

    override fun initTestMode(mode: RangeTestMode?) {
        initTestView(mode, true)
    }

    override fun cancelTestMode() {
        dismissModalDialog()
        finish()
    }

    override fun updateTxPower(power: Int) {
        withGatt(SetValueGattAction(GattCharacteristic.RangeTestTxPower, power))
    }

    override fun updatePayloadLength(length: Int) {
        withGatt(SetValueGattAction(GattCharacteristic.RangeTestPayload, length))
    }

    override fun updateMaWindowSize(size: Int) {
        withGatt(SetValueGattAction(GattCharacteristic.RangeTestMaSize, size))
    }

    override fun updateChannel(channel: Int) {
        withGatt(SetValueGattAction(GattCharacteristic.RangeTestChannel, channel))
    }

    override fun updatePacketCount(count: Int) {
        withGatt(SetValueGattAction(GattCharacteristic.RangeTestPacketsRequired, count))
    }

    override fun updateRemoteId(id: Int) {
        withGatt(SetValueGattAction(GattCharacteristic.RangeTestDestinationId, id))
    }

    override fun updateSelfId(id: Int) {
        withGatt(SetValueGattAction(GattCharacteristic.RangeTestSourceId, id))
    }

    override fun updateUartLogEnabled(enabled: Boolean) {
        withGatt(SetValueGattAction(GattCharacteristic.RangeTestLog, enabled))
    }

    override fun toggleRunningState() {
        withGatt(
            SetValueGattAction(
                GattCharacteristic.RangeTestIsRunning,
                !presenter.getTestRunning()
            )
        )
    }

    override fun updatePhyConfig(id: Int) {
        withGatt(SetValueGattAction(GattCharacteristic.RangePhyConfig, id))
    }

    private fun initTestView(mode: RangeTestMode?, writeCharacteristic: Boolean) {
        presenter.setMode(mode)

        if (writeCharacteristic) {
            withGatt(SetValueGattAction(GattCharacteristic.RangeTestRadioMode, mode?.code!!))
        }

        showRangeTestFragment(presenter.getMode())
    }

    private fun initTest(service: BluetoothService?) {
        val deviceAddress = presenter.getDeviceInfo()?.address
        val gatt: BluetoothGatt? = service?.getConnectedGatt(deviceAddress)

        if (gatt == null || !service.isGattConnected(deviceAddress)) {
            handleConnectionError()
        }

        setupData = true
        this.service = service
        service?.registerGattCallback(false, processor)
        service?.refreshGattServices(service.connectedGatt)
    }

    private fun withGatt(action: GattAction) {
        if (presenter.getMode() == RangeTestMode.Rx && presenter.getTestRunning()) {
            return /* Rx board is already disconnected and just transmits data through advertisement. */
        }
        service?.let {
            val gatt = it.getConnectedGatt(presenter.getDeviceInfo()?.address)

            if (!it.isGattConnected()) {
                handleConnectionError()
                return
            }

            if (gatt == null) {
                handleConnectionError()
                return
            }

            action.run(gatt)
        } ?: handleConnectionError()
    }

    private fun initAdvertisementHandler(address: String) {
        advertisementHandler = object : RangeTestAdvertisementHandler(this, address) {
            override fun handleAdvertisementRecord(
                manufacturerData: Int, companyId: Int, structureType: Int,
                rssi: Int, packetCount: Int, packetReceived: Int
            ) {
                if (structureType == 0) {
                    presenter.onTestDataReceived(address, rssi, packetCount, packetReceived)
                }
            }
        }
    }

    private fun handleConnectionError() {
        runOnUiThread {
            CustomToastManager.show(
                this@RangeTestActivity,getString(R.string.demo_range_toast_error),5000
            )
            finish()
        }
    }

    private fun showModeSelectionDialog() {
        val modeDialog = RangeTestModeDialog()
        modeDialog.show(supportFragmentManager, "RANGE_TEST_MODE")
    }

    private fun removeRangeTestFragment() {
        val fragment = supportFragmentManager.findFragmentByTag("tag_show_range_test_fragment")
        fragment?.let {
            supportFragmentManager
                .beginTransaction()
                .remove(fragment)
                .commit()
        }
        supportFragmentManager.executePendingTransactions()
        updateRangeTestEmptyState()
    }

    private fun showRangeTestFragment(mode: RangeTestMode?) {
        val arguments = Bundle()
        arguments.putInt(RangeTestFragment.ARG_MODE, mode?.code!!)

        val fragment = RangeTestFragment()
        fragment.arguments = arguments

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment, fragment, "tag_show_range_test_fragment")
            .commit()
        supportFragmentManager.executePendingTransactions()
        updateRangeTestEmptyState()
    }

    private fun updateRangeTestEmptyState() {
        val hasFragment =
            supportFragmentManager.findFragmentByTag("tag_show_range_test_fragment") != null
        val hasDevice = presenter.getDeviceInfo() != null
        binding.rangeTestEmptyState.visibility =
            if (!hasFragment && !hasDevice) View.VISIBLE else View.GONE
    }

    private fun getGenericAccessCharacteristic(characteristic: GattCharacteristic): BluetoothGattCharacteristic? {
        val gattService = service?.connectedGatt?.getService(GattService.GenericAccess.number)
        return gattService?.getCharacteristic(characteristic.uuid)
    }

    private fun getDeviceInformationCharacteristic(characteristic: GattCharacteristic): BluetoothGattCharacteristic? {
        val gattService = service?.connectedGatt?.getService(GattService.DeviceInformation.number)
        return gattService?.getCharacteristic(characteristic.uuid)
    }

    private fun getRangeTestCharacteristic(characteristic: GattCharacteristic): BluetoothGattCharacteristic? {
        val gattService = service?.connectedGatt?.getService(GattService.RangeTestService.number)
        return gattService?.getCharacteristic(characteristic.uuid)
    }

    private fun handleTxTimer(deviceAddress: String?, running: Boolean) {
        if (txUpdateTimer == null && deviceAddress != null) {
            runOnUiThread {
                txUpdateTimer = TxUpdateTimer(deviceAddress)
            }
        }

        if (running) {
            timerStarted = true
            txUpdateTimer?.cancel()
            txUpdateTimer?.start()
        } else {
            timerStarted = false
            txUpdateTimer?.cancel()
        }
    }

    private class GattCommand {

        enum class Type {
            Read, Write, Subscribe, ReadDescriptor
        }

        val type: Type
        val gatt: BluetoothGatt?
        val characteristic: BluetoothGattCharacteristic?
        var descriptor: BluetoothGattDescriptor? = null

        constructor(
            type: Type,
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            this.type = type
            this.gatt = gatt
            this.characteristic = characteristic
        }

        constructor(
            type: Type,
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            descriptor: BluetoothGattDescriptor
        ) {
            this.type = type
            this.gatt = gatt
            this.characteristic = characteristic
            this.descriptor = descriptor
        }
    }

    private inner class GattProcessor : TimeoutGattCallback() {
        private val commands: Queue<GattCommand> = LinkedList()
        private val lock: Lock = ReentrantLock()
        private var processing = false

        private fun queueRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic?) {
            queue(GattCommand(GattCommand.Type.Read, gatt, characteristic))
        }

        private fun queueReadDescriptor(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            descriptor: BluetoothGattDescriptor
        ) {
            queue(GattCommand(GattCommand.Type.ReadDescriptor, gatt, characteristic, descriptor))
        }

        fun queueWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic) {
            queue(GattCommand(GattCommand.Type.Write, gatt, characteristic))
        }

        private fun queueSubscribe(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic?
        ) {
            queue(GattCommand(GattCommand.Type.Subscribe, gatt, characteristic))
        }

        private fun addToQueue(gattCommand: GattCommand) {
            commands.add(gattCommand)
        }

        private fun queue(command: GattCommand) {
            lock.lock()
            try {
                commands.add(command)
                if (!processing) {
                    processNextCommand()
                }
            } finally {
                lock.unlock()
            }
        }

        private fun processNextCommand() {
            var success = false

            val command = commands.poll()
            if (command?.gatt != null && command.characteristic != null) {
                val gatt = command.gatt
                val characteristic = command.characteristic

                success = when (command.type) {
                    GattCommand.Type.Read -> gatt.readCharacteristic(characteristic)
                    GattCommand.Type.Write -> gatt.writeCharacteristic(characteristic)
                    GattCommand.Type.Subscribe -> {
                        val gattCharacteristic = GattCharacteristic.fromUuid(characteristic.uuid)
                        val gattService = GattService.fromUuid(characteristic.service.uuid)
                        setNotificationForCharacteristic(
                            gatt,
                            gattService,
                            gattCharacteristic,
                            Notifications.INDICATE
                        )
                    }

                    GattCommand.Type.ReadDescriptor -> gatt.readDescriptor(command.descriptor)
                }
            }

            processing = success

            dismissModalDialogWhenSetupCompleted()
        }

        private fun dismissModalDialogWhenSetupCompleted() {
            if (setupData && commands.isEmpty()) {
                setupData = false
                runOnUiThread { dismissModalDialog() }
            }
        }

        private fun handleCommandProcessed() {
            lock.lock()
            try {
                if (commands.isEmpty()) {
                    processing = false
                } else {
                    processNextCommand()
                }
            } finally {
                lock.unlock()
            }
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            presenter.getDeviceByAddress(gatt.device.address)?.let {

                if (it.mode == RangeTestMode.Rx && status == 19 && newState == BluetoothProfile.STATE_DISCONNECTED) {
                    /* "Start RX" results in disconnecting the board. Data comes through advertisement data. */
                    if (isFinishing) return

                    synchronized(this@RangeTestActivity) {
                        initAdvertisementHandler(gatt.device.address)
                        advertisementHandler?.startListening()
                        presenter.onRunningStateUpdated(gatt.device.address, true)
                        presenter.setTestRunning(gatt.device.address, true)
                    }
                }

                if (status != 19 && newState == BluetoothProfile.STATE_DISCONNECTED) {
                    runOnUiThread {
                        dismissModalDialog()

                        if (it.mode == RangeTestMode.Tx && it.testRunning) {
                            handleConnectionError()
                            return@runOnUiThread
                        }

                        val resetDeviceId =
                            if (it === presenter.deviceState1) 1
                            else 2
                        showMessage(
                            ErrorCodes.getDeviceDisconnectedMessage(
                                presenter.getDeviceInfoAt(
                                    resetDeviceId
                                )?.name, status
                            )
                        )
                        presenter.resetDeviceAt(resetDeviceId)

                        if (resetDeviceId == 1) {
                            binding.tvDevice1Tab.text = getString(R.string.range_test_no_device)
                            changeDevice(binding.tvDevice1Tab, 1)
                        } else {
                            binding.tvDevice2Tab.text = getString(R.string.range_test_no_device)
                            changeDevice(binding.tvDevice2Tab, 2)
                        }
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)

            if (status != BluetoothGatt.GATT_SUCCESS) {
                handleConnectionError()
                return
            }

            addToQueue(
                GattCommand(
                    GattCommand.Type.Read,
                    gatt,
                    getRangeTestCharacteristic(GattCharacteristic.RangeTestIsRunning)
                )
            )
            addToQueue(
                GattCommand(
                    GattCommand.Type.Read,
                    gatt,
                    getRangeTestCharacteristic(GattCharacteristic.RangeTestRadioMode)
                )
            )

            addToQueue(
                GattCommand(
                    GattCommand.Type.Read,
                    gatt,
                    getGenericAccessCharacteristic(GattCharacteristic.DeviceName)
                )
            )
            addToQueue(
                GattCommand(
                    GattCommand.Type.Read,
                    gatt,
                    getDeviceInformationCharacteristic(GattCharacteristic.ModelNumberString)
                )
            )

            addToQueue(
                GattCommand(
                    GattCommand.Type.Read,
                    gatt,
                    getRangeTestCharacteristic(GattCharacteristic.RangePhyList)
                )
            )
            addToQueue(
                GattCommand(
                    GattCommand.Type.Read,
                    gatt,
                    getRangeTestCharacteristic(GattCharacteristic.RangePhyConfig)
                )
            )
            addToQueue(
                GattCommand(
                    GattCommand.Type.Read,
                    gatt,
                    getRangeTestCharacteristic(GattCharacteristic.RangeTestTxPower)
                )
            )
            addToQueue(
                GattCommand(
                    GattCommand.Type.Read,
                    gatt,
                    getRangeTestCharacteristic(GattCharacteristic.RangeTestPacketsSend)
                )
            )
            addToQueue(
                GattCommand(
                    GattCommand.Type.Read,
                    gatt,
                    getRangeTestCharacteristic(GattCharacteristic.RangeTestDestinationId)
                )
            )
            addToQueue(
                GattCommand(
                    GattCommand.Type.Read,
                    gatt,
                    getRangeTestCharacteristic(GattCharacteristic.RangeTestSourceId)
                )
            )
            addToQueue(
                GattCommand(
                    GattCommand.Type.Read,
                    gatt,
                    getRangeTestCharacteristic(GattCharacteristic.RangeTestPacketsRequired)
                )
            )
            addToQueue(
                GattCommand(
                    GattCommand.Type.Read,
                    gatt,
                    getRangeTestCharacteristic(GattCharacteristic.RangeTestChannel)
                )
            )
            addToQueue(
                GattCommand(
                    GattCommand.Type.Read,
                    gatt,
                    getRangeTestCharacteristic(GattCharacteristic.RangeTestPayload)
                )
            )
            addToQueue(
                GattCommand(
                    GattCommand.Type.Read,
                    gatt,
                    getRangeTestCharacteristic(GattCharacteristic.RangeTestMaSize)
                )
            )
            addToQueue(
                GattCommand(
                    GattCommand.Type.Read,
                    gatt,
                    getRangeTestCharacteristic(GattCharacteristic.RangeTestLog)
                )
            )

            addToQueue(
                GattCommand(
                    GattCommand.Type.Subscribe,
                    gatt,
                    getRangeTestCharacteristic(GattCharacteristic.RangeTestIsRunning)
                )
            )
            addToQueue(
                GattCommand(
                    GattCommand.Type.Subscribe,
                    gatt,
                    getRangeTestCharacteristic(GattCharacteristic.RangePhyConfig)
                )
            )
            addToQueue(
                GattCommand(
                    GattCommand.Type.Subscribe,
                    gatt,
                    getRangeTestCharacteristic(GattCharacteristic.RangeTestTxPower)
                )
            )
            addToQueue(
                GattCommand(
                    GattCommand.Type.Subscribe,
                    gatt,
                    getRangeTestCharacteristic(GattCharacteristic.RangeTestPacketsSend)
                )
            )
            addToQueue(
                GattCommand(
                    GattCommand.Type.Subscribe,
                    gatt,
                    getRangeTestCharacteristic(GattCharacteristic.RangeTestDestinationId)
                )
            )
            addToQueue(
                GattCommand(
                    GattCommand.Type.Subscribe,
                    gatt,
                    getRangeTestCharacteristic(GattCharacteristic.RangeTestSourceId)
                )
            )
            addToQueue(
                GattCommand(
                    GattCommand.Type.Subscribe,
                    gatt,
                    getRangeTestCharacteristic(GattCharacteristic.RangeTestPacketsRequired)
                )
            )
            addToQueue(
                GattCommand(
                    GattCommand.Type.Subscribe,
                    gatt,
                    getRangeTestCharacteristic(GattCharacteristic.RangeTestChannel)
                )
            )
            addToQueue(
                GattCommand(
                    GattCommand.Type.Subscribe,
                    gatt,
                    getRangeTestCharacteristic(GattCharacteristic.RangeTestRadioMode)
                )
            )
            addToQueue(
                GattCommand(
                    GattCommand.Type.Subscribe,
                    gatt,
                    getRangeTestCharacteristic(GattCharacteristic.RangeTestPayload)
                )
            )
            addToQueue(
                GattCommand(
                    GattCommand.Type.Subscribe,
                    gatt,
                    getRangeTestCharacteristic(GattCharacteristic.RangeTestMaSize)
                )
            )

            // Add to queue and start executing
            queueSubscribe(gatt, getRangeTestCharacteristic(GattCharacteristic.RangeTestLog))

            if (setupData) runOnUiThread { showModalDialog(ConnectionStatus.READING_DEVICE_STATE) }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)

            val descriptorUuid = UUID.fromString("00002906-0000-1000-8000-00805f9b34fb")
            if (characteristic != null) {
                descriptor2906 = characteristic.getDescriptor(descriptorUuid)
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handleConnectionError()
                return
            }

            handleCommandProcessed()

            val gattCharacteristic = GattCharacteristic.fromUuid(characteristic.uuid)

            if (gattCharacteristic != null) {
                updatePresenter(gatt, characteristic, gattCharacteristic)

                if (gattCharacteristic === GattCharacteristic.RangeTestTxPower || gattCharacteristic === GattCharacteristic.RangeTestPayload || gattCharacteristic === GattCharacteristic.RangeTestMaSize) {
                    val descriptors = characteristic.descriptors
                    if (descriptors.size > 1) {
                        queueReadDescriptor(gatt, characteristic, descriptors[descriptors.size - 1])
                    }
                } else {
                    if (gattCharacteristic === GattCharacteristic.RangeTestChannel) {
                        val descriptors = characteristic.descriptors
                        if (descriptors.size > 1) {
                            descriptor2906?.let { queueReadDescriptor(gatt, characteristic, it) }
                        }

                    }
                }
            }
        }

        override fun onDescriptorRead(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            super.onDescriptorRead(gatt, descriptor, status)
            if (descriptor?.uuid == UUID.fromString("00002906-0000-1000-8000-00805f9b34fb")) {
                try {
                    val validRangeValue = descriptor.value
                    lowerLimit =
                        (validRangeValue[1].toInt() and 0xFF) shl 8 or (validRangeValue[0].toInt() and 0xFF)
                    upperLimit =
                        (validRangeValue[3].toInt() and 0xFF) shl 8 or (validRangeValue[2].toInt() and 0xFF)
                    RangeTestValues.setChannelsMinMax(lowerLimit, upperLimit)
                    val gattCharacteristic1 =
                        GattCharacteristic.fromUuid(descriptor.characteristic.uuid)
                    gattCharacteristic1?.let {
                        updatePresenter(gatt, descriptor, it)
                    }

                } catch (e: Exception) {
                    e.stackTrace
                }
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handleConnectionError()
                return
            }

            handleCommandProcessed()

            val gattCharacteristic = GattCharacteristic.fromUuid(descriptor.characteristic.uuid)

            gattCharacteristic?.let {
                updatePresenter(gatt, descriptor, it)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)

            if (status != BluetoothGatt.GATT_SUCCESS) {
                handleConnectionError()
                return
            }

            handleCommandProcessed()

            val gattCharacteristic = GattCharacteristic.fromUuid(characteristic.uuid)

            if (presenter.getMode() == RangeTestMode.Tx) {
                when (gattCharacteristic) {
                    GattCharacteristic.RangeTestIsRunning -> {
                        presenter.setTestRunning(
                            gatt.device.address,
                            characteristic.getIntValue(
                                BluetoothGattCharacteristic.FORMAT_UINT8,
                                0
                            ) != 0
                        )
                        setKeepScreenOn(presenter.getTestRunning())
                        handleTxTimer(gatt.device.address, presenter.getTestRunning())
                        presenter.onRunningStateUpdated(
                            gatt.device.address,
                            presenter.getTestRunning()
                        )
                    }

                    GattCharacteristic.RangeTestPacketsRequired -> {
                        val packetsRequired =
                            characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0)
                        val repeat = packetsRequired == RangeTestValues.PACKET_COUNT_REPEAT
                        presenter.onPacketRequiredUpdated(gatt.device.address, packetsRequired)
                        presenter.onPacketCountRepeatUpdated(gatt.device.address, repeat)
                    }

                    else -> {}
                }
            }

            if (gattCharacteristic === GattCharacteristic.RangePhyConfig) {
                queueRead(gatt, getRangeTestCharacteristic(GattCharacteristic.RangeTestPayload))
            }

            when (gattCharacteristic) {
                GattCharacteristic.RangeTestDestinationId,
                GattCharacteristic.RangeTestSourceId,
                GattCharacteristic.RangeTestPacketsRequired,
                GattCharacteristic.RangeTestChannel,
                GattCharacteristic.RangeTestTxPower,
                GattCharacteristic.RangeTestPayload,
                GattCharacteristic.RangeTestMaSize,
                GattCharacteristic.RangeTestLog,
                GattCharacteristic.RangePhyConfig,
                GattCharacteristic.RangePhyList -> updatePresenter(
                    gatt,
                    characteristic,
                    gattCharacteristic
                )

                else -> {}
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)

            if (status != BluetoothGatt.GATT_SUCCESS) {
                handleConnectionError()
                return
            }

            handleCommandProcessed()
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)

            val gattCharacteristic = GattCharacteristic.fromUuid(characteristic.uuid)

            gattCharacteristic?.let {
                updatePresenter(gatt, characteristic, it)
            }
        }

        private fun updatePresenter(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            gattCharacteristic: GattCharacteristic
        ) {

            when (gattCharacteristic) {
                GattCharacteristic.DeviceName -> {
                    val deviceName = characteristic.getStringValue(0)
                    presenter.onDeviceNameUpdated(gatt.device.address, deviceName)
                }

                GattCharacteristic.ModelNumberString -> {
                    var modelNumber = characteristic.getStringValue(0)

                    val patternDuplicateOpn = "opn\\[.*]"
                    modelNumber = modelNumber.replaceFirst(patternDuplicateOpn.toRegex(), "")

                    presenter.onModelNumberUpdated(gatt.device.address, modelNumber)
                }

                GattCharacteristic.RangeTestDestinationId -> {
                    val remoteId =
                        characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                    presenter.onRemoteIdUpdated(gatt.device.address, remoteId)
                }

                GattCharacteristic.RangeTestSourceId -> {
                    val selfId =
                        characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                    presenter.onSelfIdUpdated(gatt.device.address, selfId)
                }

                GattCharacteristic.RangeTestPacketsSend -> {
                    val deviceResponding = presenter.getDeviceByAddress(gatt.device.address)
                    val packetsSent =
                        characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0)
                    if (deviceResponding?.testRunning == true) {
                        if (!timerStarted && packetsSent > 0) {
                            handleTxTimer(gatt.device.address, true)
                        }
                        presenter.onPacketSentUpdated(gatt.device.address, packetsSent)
                    }
                }

                GattCharacteristic.RangeTestPacketsRequired -> {
                    val packetsRequired =
                        characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0)
                    val repeat = packetsRequired == RangeTestValues.PACKET_COUNT_REPEAT

                    presenter.onPacketRequiredUpdated(gatt.device.address, packetsRequired)
                    presenter.onPacketCountRepeatUpdated(gatt.device.address, repeat)
                }

                GattCharacteristic.RangeTestChannel -> {
                    val channel =
                        characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0)
                    presenter.onChannelNumberUpdated(gatt.device.address, channel)
                }

                GattCharacteristic.RangeTestRadioMode -> {
                    val radioMode =
                        characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                    val rangeTestMode = RangeTestMode.fromCode(radioMode)

                    if (service?.connectedGatt == gatt) {
                        if (presenter.getMode() == null && presenter.getTestRunning()) {
                            initTestView(rangeTestMode, false)
                            presenter.onRunningStateUpdated(
                                gatt.device.address,
                                presenter.getTestRunning()
                            )
                        } else if (presenter.getMode() != null) {
                            initTestView(rangeTestMode, false)
                        }
                    } else {
                        presenter.setModeAt(if (activeDeviceId == 1) 2 else 1, rangeTestMode)
                    }
                }

                GattCharacteristic.RangeTestTxPower -> {
                    val txPower =
                        characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT16, 0)
                    presenter.onTxPowerUpdated(gatt.device.address, txPower)
                }

                GattCharacteristic.RangeTestPayload -> {
                    val payloadLength =
                        characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                    presenter.onPayloadLengthUpdated(gatt.device.address, payloadLength)
                }

                GattCharacteristic.RangeTestMaSize -> {
                    val maWindowSize =
                        characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                    presenter.onMaWindowSizeUpdated(gatt.device.address, maWindowSize)
                }

                GattCharacteristic.RangeTestLog -> {
                    val log =
                        characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                    presenter.onUartLogEnabledUpdated(gatt.device.address, log != 0)
                }

                GattCharacteristic.RangeTestIsRunning -> {
                    presenter.getDeviceByAddress(gatt.device.address)?.let {
                        when (it.mode) {
                            null -> showModeSelectionDialog()
                            RangeTestMode.Tx -> {
                                presenter.setTestRunning(
                                    gatt.device.address,
                                    characteristic.getIntValue(
                                        BluetoothGattCharacteristic.FORMAT_UINT8,
                                        0
                                    ) != 0
                                )
                                handleTxTimer(gatt.device.address, it.testRunning)
                                presenter.onRunningStateUpdated(gatt.device.address, it.testRunning)
                            }

                            else -> {}
                        }
                    }
                }

                GattCharacteristic.RangePhyConfig -> {
                    val phyConfig =
                        characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                    presenter.onPhyConfigUpdated(gatt.device.address, phyConfig)

                    val connectedGatt = service?.connectedGatt
                    if (connectedGatt != null) {
                        queueRead(
                            connectedGatt,
                            getRangeTestCharacteristic(GattCharacteristic.RangeTestPayload)
                        )
                    }
                }

                GattCharacteristic.RangePhyList -> {
                    val phyConfigs = LinkedHashMap<Int, String>()

                    val phyConfigsList = characteristic.getStringValue(0)
                    val phyConfigsListSplit = phyConfigsList.split(",".toRegex()).toTypedArray()

                    for (phyConfigPair in phyConfigsListSplit) {
                        val phyConfigPairSplit = phyConfigPair.split(":".toRegex()).toTypedArray()
                        if (phyConfigPairSplit.size == 2) {
                            val id = Integer.valueOf(phyConfigPairSplit[0])
                            val name = phyConfigPairSplit[1]
                            phyConfigs[id] = name
                        }
                    }

                    presenter.onPhyMapUpdated(gatt.device.address, phyConfigs)
                }

                else -> {}
            }
        }

        private fun updatePresenter(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            gattCharacteristic: GattCharacteristic
        ) {

            val descriptorValues = descriptor.value

            if (descriptorValues.size % 2 != 0) {
                return
            }

            val firstValueArray = Arrays.copyOfRange(descriptorValues, 0, descriptorValues.size / 2)
            val secondValueArray = Arrays.copyOfRange(
                descriptorValues,
                descriptorValues.size / 2,
                descriptorValues.size
            )
            val wrappedFirstValue = ByteBuffer.wrap(firstValueArray).order(ByteOrder.LITTLE_ENDIAN)
            val wrappedSecondValue =
                ByteBuffer.wrap(secondValueArray).order(ByteOrder.LITTLE_ENDIAN)
            val rangeFrom: Int
            val rangeTo: Int

            when (gattCharacteristic) {
                GattCharacteristic.RangeTestTxPower -> {
                    rangeFrom = wrappedFirstValue.short.toInt()
                    rangeTo = wrappedSecondValue.short.toInt()
                    presenter.onTxPowerRangeUpdated(gatt.device.address, rangeFrom, rangeTo)
                }

                GattCharacteristic.RangeTestPayload -> {
                    rangeFrom = firstValueArray[0].toInt()
                    rangeTo = secondValueArray[0].toInt()
                    presenter.onPayloadLengthRangeUpdated(gatt.device.address, rangeFrom, rangeTo)
                }

                GattCharacteristic.RangeTestMaSize -> {
                    rangeFrom = abs(firstValueArray[0].toInt())
                    rangeTo = abs(secondValueArray[0].toInt())
                    presenter.onMaWindowSizeRangeUpdated(gatt.device.address, rangeFrom, rangeTo)
                }

                else -> {
                }
            }
        }
    }

    private fun setKeepScreenOn(enabled: Boolean) {
        runOnUiThread {
            if (enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    private interface GattAction {
        fun run(gatt: BluetoothGatt?)
    }

    private inner class SetValueGattAction : GattAction {

        private val characteristic: GattCharacteristic
        private val value: Int

        constructor(characteristic: GattCharacteristic, value: Boolean) {
            this.characteristic = characteristic
            this.value = if (value) 1 else 0
        }

        constructor(characteristic: GattCharacteristic, value: Int) {
            this.characteristic = characteristic
            this.value = value
        }

        override fun run(gatt: BluetoothGatt?) {
            val characteristic = getRangeTestCharacteristic(characteristic) ?: return

            val currentValue = getCurrentValueOf(characteristic)
            if (currentValue == null || currentValue == value) {
                return
            }

            writeValueFor(gatt, characteristic)
        }

        private fun getCurrentValueOf(characteristic: BluetoothGattCharacteristic): Int? {
            val value = characteristic.value

            return if (value == null || value.isEmpty()) {
                null
            } else characteristic.getIntValue(this.characteristic.format, 0)
        }

        private fun writeValueFor(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic
        ) {
            characteristic.setValue(value, this.characteristic.format, 0)
            processor.queueWrite(gatt, characteristic)
        }
    }

    private fun selectTab(tvTab: TextView) {
        setTabUnselected(binding.tvDevice1Tab)
        setTabUnselected(binding.tvDevice2Tab)
        setTabSelected(tvTab)
    }

    private fun setTabSelected(textView: TextView?) {
        textView?.setBackgroundResource(R.drawable.range_test_tab_selected)
        textView?.setTextColor(ContextCompat.getColor(this, R.color.silabs_primary_text))
        textView?.typeface = ResourcesCompat.getFont(this, R.font.stolzl_regular)
            ?: Typeface.DEFAULT
    }

    private fun setTabUnselected(textView: TextView?) {
        textView?.background = null
        textView?.setTextColor(ContextCompat.getColor(this, R.color.silabs_primary_text))
        textView?.typeface = ResourcesCompat.getFont(this, R.font.stolzl_regular)
            ?: Typeface.DEFAULT
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                service?.disconnectAllGatts()
                gatt?.disconnect()
                Handler(Looper.getMainLooper()).postDelayed({ onBackPressed() }, 500)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val RECONNECTION_RETRIES = 3
        private const val TRANSMISSION_INTERVAL: Long = 1000 / 13
    }

}
