package com.siliconlabs.bledemo.features.demo.blinky.activities

import android.annotation.SuppressLint
import android.bluetooth.*
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.siliconlabs.bledemo.home_screen.dialogs.SelectDeviceDialog
import com.siliconlabs.bledemo.bluetooth.ble.GattCharacteristic
import com.siliconlabs.bledemo.bluetooth.ble.GattService
import com.siliconlabs.bledemo.bluetooth.ble.TimeoutGattCallback
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.features.demo.blinky.models.LightState
import com.siliconlabs.bledemo.features.demo.blinky.viewmodels.BlinkyViewModel
import com.siliconlabs.bledemo.base.activities.BaseDemoActivity
import com.siliconlabs.bledemo.features.demo.thunderboard_demos.base.fragments.StatusFragment
import com.siliconlabs.bledemo.features.demo.thunderboard_demos.base.models.ThunderBoardDevice
import com.siliconlabs.bledemo.utils.AppUtil
import com.siliconlabs.bledemo.utils.BLEUtils
import com.siliconlabs.bledemo.utils.Notifications
import java.util.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class BlinkyActivity : BaseDemoActivity() {
    private lateinit var viewModel: BlinkyViewModel
    private val processor = GattProcessor()
    private var isDeviceThunderboard = false
    lateinit var mToolbar: Toolbar
    private var statusFragment: StatusFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blinky)
        viewModel = ViewModelProvider(this).get(BlinkyViewModel::class.java)
        mToolbar = findViewById(R.id.toolbar)
        val boardType = intent.extras?.getString(SelectDeviceDialog.MODEL_TYPE_EXTRA, "Unknown") ?: "Unknown"
        if (boardType == "BRD4184A" || boardType == "BRD4184B") isDeviceThunderboard = true
        prepareToolBar()
        observeChanges()
    }

    private fun prepareToolBar() {
        AppUtil.setEdgeToEdge(window, this)
        setSupportActionBar(mToolbar)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setHomeAsUpIndicator(R.drawable.matter_back)
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.title = this.getString(R.string.title_Blinky)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            gatt?.disconnect()
            onBackPressed()
            true
        } else super.onOptionsItemSelected(item)
    }

    override fun onBluetoothServiceBound() {
        if (isDeviceThunderboard) {
            statusFragment = (supportFragmentManager.findFragmentById(R.id
                    .status_fragment) as StatusFragment).apply {
                val preferredName = intent.getStringExtra(SelectDeviceDialog.DEVICE_NAME_EXTRA)
                viewModel.thunderboardDevice.value =
                    ThunderBoardDevice(gatt?.device!!, preferredName)
                viewModel.state.postValue(BluetoothProfile.STATE_CONNECTED)
            }
            findViewById<LinearLayout>(R.id.thunderboard_fragment_container).visibility = View.VISIBLE
        }
        service?.registerGattCallback(true, processor)
        gatt?.discoverServices()
    }

    private fun observeChanges() {
        viewModel.lightState.observe(this, Observer { state ->
            when (state) {
                LightState.TOGGLING_ON -> gatt?.let { processor.switchLightOn(it) }
                LightState.TOGGLING_OFF -> gatt?.let { processor.switchLightOff(it) }
                else -> {
                }
            }
        })
    }

    private fun getBlinkyCharacteristic(characteristic: GattCharacteristic): BluetoothGattCharacteristic? {
        val gattService = gatt?.getService(GattService.BlinkyExample.number)
        return gattService?.getCharacteristic(characteristic.uuid)
    }

    private fun getFirmwareRevisionCharacteristic(): BluetoothGattCharacteristic? {
        val gattService = gatt?.getService(GattService.DeviceInformation.number)
        return gattService?.getCharacteristic(GattCharacteristic.FirmwareRevision.uuid)
    }

    private fun getPowerSourceCharacteristic(): BluetoothGattCharacteristic? {
        val gattService = gatt?.getService(GattService.PowerSource.number)
        return gattService?.getCharacteristic(GattCharacteristic.PowerSource.uuid)
    }

    private fun getBatteryLevelCharacteristic(): BluetoothGattCharacteristic? {
        val gattService = gatt?.getService(GattService.BatteryService.number)
        return gattService?.getCharacteristic(GattCharacteristic.BatteryLevel.uuid)
    }

    private fun getDigitalCharacteristicWithNotify(): BluetoothGattCharacteristic? {
        val gattService = gatt?.getService(GattService.AutomationIo.number)
        gattService?.characteristics?.forEach { // There are 2 "Digital" characteristics on 4184A/B device
            if (it.uuid == GattCharacteristic.Digital.uuid &&
                    it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0x10) return it
        }
        return null
    }

    private fun getDigitalCharacteristicWithWrite(): BluetoothGattCharacteristic? {
        val gattService = gatt?.getService(GattService.AutomationIo.number)
        gattService?.characteristics?.forEach {
            if (it.uuid == GattCharacteristic.Digital.uuid &&
                    it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0x08) return it
        }
        return null
    }

    private class GattCommand(val type: Type, val gatt: BluetoothGatt?, val characteristic: BluetoothGattCharacteristic?) {
        enum class Type {
            Read, Write, Notify
        }
    }

    private inner class GattProcessor : TimeoutGattCallback() {
        private val commands: Queue<GattCommand> = LinkedList()
        private val lock: Lock = ReentrantLock()
        private var processing = false

        private fun queueRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            queue(GattCommand(GattCommand.Type.Read, gatt, characteristic))
        }

        fun queueWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            queue(GattCommand(GattCommand.Type.Write, gatt, characteristic))
        }

        private fun queueNotify(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            queue(GattCommand(GattCommand.Type.Notify, gatt, characteristic))
        }

        fun switchLightOn(gatt: BluetoothGatt) {
            val characteristicToWrite =
                    if (isDeviceThunderboard) getDigitalCharacteristicWithWrite()
                    else getBlinkyCharacteristic(GattCharacteristic.LedControl)
            characteristicToWrite?.apply {
                value = byteArrayOf(1)
                queueWrite(gatt, this)
            }
        }

        fun switchLightOff(gatt: BluetoothGatt) {
            val characteristicToWrite =
                    if (isDeviceThunderboard) getDigitalCharacteristicWithWrite()
                    else getBlinkyCharacteristic(GattCharacteristic.LedControl)
            characteristicToWrite?.apply {
                value = byteArrayOf(0)
                queueWrite(gatt, this)
            }
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

        @SuppressLint("MissingPermission")
        private fun processNextCommand() {
            var success = false
            val command = commands.poll()

            if (command?.gatt != null && command.characteristic != null) {
                val gatt = command.gatt
                val characteristic = command.characteristic

                success = when (command.type) {
                    GattCommand.Type.Read -> gatt.readCharacteristic(characteristic)
                    GattCommand.Type.Write -> gatt.writeCharacteristic(characteristic)
                    GattCommand.Type.Notify -> {
                        val gattCharacteristic = GattCharacteristic.fromUuid(characteristic.uuid)
                        val gattService = GattService.fromUuid(characteristic.service.uuid)
                        BLEUtils.setNotificationForCharacteristic(gatt, gattService, gattCharacteristic, Notifications.NOTIFY)
                    }
                }
            }
            processing = success
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
            if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                onDeviceDisconnected()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)

            if (isDeviceThunderboard) {
                queueRead(gatt, getDigitalCharacteristicWithWrite())
                queueNotify(gatt, getDigitalCharacteristicWithNotify())

                queueRead(gatt, getFirmwareRevisionCharacteristic())
                queueRead(gatt, getPowerSourceCharacteristic())
                queueRead(gatt, getBatteryLevelCharacteristic())
            } else {
                queueRead(gatt, getBlinkyCharacteristic(GattCharacteristic.LedControl))
                queueNotify(gatt, getBlinkyCharacteristic(GattCharacteristic.ReportButton))
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic, status: Int) {
            super.onCharacteristicRead(gatt, characteristic, status)
            handleCommandProcessed()

            when (GattCharacteristic.fromUuid(characteristic.uuid)) {
                GattCharacteristic.LedControl,
                GattCharacteristic.Digital -> viewModel.handleLightStateChanges(characteristic)

                GattCharacteristic.FirmwareRevision,
                GattCharacteristic.PowerSource,
                GattCharacteristic.BatteryLevel -> statusFragment?.handleBaseCharacteristic(characteristic)
                else -> { }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            handleCommandProcessed()

            val gattCharacteristic = GattCharacteristic.fromUuid(characteristic.uuid)
            when (gattCharacteristic) {
                GattCharacteristic.LedControl,
                GattCharacteristic.Digital -> viewModel.handleLightStateChanges(characteristic)
                else -> { }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicChanged(gatt, characteristic)

            val gattCharacteristic = GattCharacteristic.fromUuid(characteristic.uuid)
            when (gattCharacteristic) {
                GattCharacteristic.ReportButton,
                GattCharacteristic.Digital -> viewModel.handleButtonStateChanges(characteristic)
                else -> { }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            super.onDescriptorWrite(gatt, descriptor, status)
            handleCommandProcessed()
        }
    }

}
