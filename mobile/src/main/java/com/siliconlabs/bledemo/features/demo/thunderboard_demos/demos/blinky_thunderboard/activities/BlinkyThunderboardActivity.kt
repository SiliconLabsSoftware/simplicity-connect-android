package com.siliconlabs.bledemo.features.demo.thunderboard_demos.demos.blinky_thunderboard.activities

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.WindowCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.bluetooth.ble.GattCharacteristic
import com.siliconlabs.bledemo.bluetooth.ble.GattService
import com.siliconlabs.bledemo.bluetooth.ble.TimeoutGattCallback
import com.siliconlabs.bledemo.databinding.ActivityBlinkyThunderboardBinding
import com.siliconlabs.bledemo.features.demo.thunderboard_demos.base.activities.ThunderboardActivity
import com.siliconlabs.bledemo.features.demo.thunderboard_demos.base.models.ThunderBoardDevice
import com.siliconlabs.bledemo.features.demo.thunderboard_demos.demos.blinky_thunderboard.control.ColorLEDControl
import com.siliconlabs.bledemo.features.demo.thunderboard_demos.demos.blinky_thunderboard.control.ColorLEDControl.ColorLEDControlListener
import com.siliconlabs.bledemo.features.demo.thunderboard_demos.demos.blinky_thunderboard.model.LedRGBState
import com.siliconlabs.bledemo.features.demo.thunderboard_demos.demos.blinky_thunderboard.viewmodels.BlinkyThunderboardViewModel
import com.siliconlabs.bledemo.home_screen.dialogs.SelectDeviceDialog
import com.siliconlabs.bledemo.utils.AppUtil
import java.util.UUID

class BlinkyThunderboardActivity : ThunderboardActivity(), ColorLEDControlListener {

    private lateinit var ledsControl: View
    private lateinit var colorLEDControl: ColorLEDControl

    private lateinit var viewModel: BlinkyThunderboardViewModel
    private lateinit var binding:ActivityBlinkyThunderboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityBlinkyThunderboardBinding. inflate(LayoutInflater.from(this), null, false)

        binding.led0.apply {
            thumbTintList = null
            trackTintList = null
        }
        binding.led1.apply {
            thumbTintList = null
            trackTintList = null
        }

        colorLEDControl = binding.colorLedControl  // make the view gone (if necessary) before it can be showed
        ledsControl = binding.ledsControl
        val powerSourceIntent = intent.getIntExtra(SelectDeviceDialog.POWER_SOURCE_EXTRA, 0)
        val modelNumberIntent = intent.getStringExtra(SelectDeviceDialog.MODEL_TYPE_EXTRA)
        setControlsVisibility(ThunderBoardDevice.PowerSource.fromInt(powerSourceIntent), modelNumberIntent)

        mainSection?.addView(binding.root)

        viewModel = ViewModelProvider(this).get(BlinkyThunderboardViewModel::class.java)

        setupDataListeners(modelNumberIntent)
        setupUiListeners()
        prepareToolBar()
    }

    private fun prepareToolBar() {
        AppUtil.setEdgeToEdge(window, this)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        setSupportActionBar(binding.toolbar)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setHomeAsUpIndicator(R.drawable.matter_back)
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.title = getString(R.string.title_Blinky)
        }
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            gatt?.disconnect()
            onBackPressed()
            true
        } else super.onOptionsItemSelected(item)
    }
    private fun setupUiListeners() {

        binding.led0.setOnCheckedChangeListener { _, isChecked ->
            var action = 0
            if (isChecked) action = BlinkyThunderboardViewModel.LED_0_ON
            if (binding.led1.isChecked) action = action or BlinkyThunderboardViewModel.LED_1_ON

            getDigitalWriteCharacteristic()?.apply {
                value = byteArrayOf(action.toByte())
                gattQueue.queueWrite(this)
            }
        }
        binding.led1.setOnCheckedChangeListener { _, isChecked ->
            var action = 0
            if (isChecked) action = BlinkyThunderboardViewModel.LED_1_ON
            if (binding.led0.isChecked) action = action or BlinkyThunderboardViewModel.LED_0_ON

            getDigitalWriteCharacteristic()?.apply {
                value = byteArrayOf(action.toByte())
                gattQueue.queueWrite(this)
            }
        }
        colorLEDControl.setColorLEDControlListener(this)
    }

    private fun setupDataListeners(modelNumber: String?) {

        viewModel.button0.observe(this, Observer { binding.switch0.setChecked(it) })
        viewModel.button1.observe(this, Observer { binding.switch1.setChecked(it) })
        viewModel.led0.observe(this, Observer {
            if (it != binding.led0.isChecked) binding.led0.isChecked = it })
        viewModel.led1.observe(this, Observer {
            if (it != binding.led1.isChecked) binding.led1.isChecked = it })

        when (modelNumber) {
            ThunderBoardDevice.THUNDERBOARD_MODEL_DEV_KIT_V3,
            ThunderBoardDevice.THUNDERBOARD_MODEL_SENSE,
            ThunderBoardDevice.THUNDERBOARD_MODEL_DEV_KIT_V1,
            ThunderBoardDevice.THUNDERBOARD_MODEL_DEV_KIT_V2 -> {
                viewModel.colorLed.observe(this, Observer {
                    colorLEDControl.setColorLEDsUI(it)
                })
            }
        }
        viewModel.rgbLedMask.observe(this, Observer {
            gattQueue.queueRead(getRgbLedCharacteristic())
            gattQueue.queueNotify(getDigitalNotifyCharacteristic())
            dismissModalDialog()
        })
    }

    private fun sendColorLedCommand(rgbState: LedRGBState) {
        val data = byteArrayOf(
                (if (rgbState.on) viewModel.rgbLedMask.value!! else 0x00).toByte(),
                (rgbState.red and 0xff).toByte(),
                (rgbState.green and 0xff).toByte(),
                (rgbState.blue and 0xff).toByte()

        )
        getRgbLedCharacteristic()?.apply {
            value = data
            gattQueue.queueWrite(this)
        }
    }

    private fun setControlsVisibility(powerSource: ThunderBoardDevice.PowerSource, modelNumber: String?) {
        if (modelNumber == ThunderBoardDevice.THUNDERBOARD_MODEL_DEV_KIT_V1 ||
                modelNumber == ThunderBoardDevice.THUNDERBOARD_MODEL_DEV_KIT_V2) {
            ledsControl.visibility = View.GONE
        }

        if (modelNumber == ThunderBoardDevice.THUNDERBOARD_MODEL_SENSE &&
                powerSource == ThunderBoardDevice.PowerSource.COIN_CELL) {
            colorLEDControl.visibility = View.GONE
        }
    }

    @SuppressLint("MissingPermission")
    private fun initControls() {
        if (statusFragment.viewModel.thunderboardDevice.value?.
                boardType != ThunderBoardDevice.Type.THUNDERBOARD_SENSE &&
            statusFragment.viewModel.thunderboardDevice.value?.
                boardType != ThunderBoardDevice.Type.THUNDERBOARD_DEV_KIT) {

            runOnUiThread { colorLEDControl.visibility = View.GONE }
        }

        getRgbLedMaskDescriptor()?.let {
            gatt?.readDescriptor(it)
        } ?: viewModel.rgbLedMask.postValue(0x0f)
    }

    override fun updateColorLEDs(ledRGBState: LedRGBState) {
        sendColorLedCommand(ledRGBState)
    }

    override fun onLedUpdateStop() {
        gattQueue.clearAllButLast() // prevent from queueing more gatt commands
    }

    private fun getDigitalWriteCharacteristic() : BluetoothGattCharacteristic? {
        return gatt?.getService(
                GattService.AutomationIo.number)?.characteristics
                ?.filter { it.uuid == GattCharacteristic.Digital.uuid } // there are two
                ?.first { it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 }
    }

    private fun getDigitalNotifyCharacteristic() : BluetoothGattCharacteristic? {
        return gatt?.getService(
                GattService.AutomationIo.number)?.characteristics
                ?.filter { it.uuid == GattCharacteristic.Digital.uuid } // there are two
                ?.first { it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 }
    }


    private fun getRgbLedCharacteristic() : BluetoothGattCharacteristic? {
        return gatt?.getService(GattService.UserInterface.number)?.
        getCharacteristic(GattCharacteristic.RgbLeds.uuid)
    }

    private fun getRgbLedMaskDescriptor() : BluetoothGattDescriptor? {
        return gatt?.getService(GattService.UserInterface.number)?.
        getCharacteristic(GattCharacteristic.RgbLeds.uuid)?.
        getDescriptor(LED_MASK_DESCRIPTOR)
    }

    override val gattCallback: TimeoutGattCallback = object : TimeoutGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                onDeviceDisconnected()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status != BluetoothGatt.GATT_SUCCESS) return

            queueReadingDeviceCharacteristics()
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt,
                                          characteristic: BluetoothGattCharacteristic,
                                          status: Int) {
            super.onCharacteristicRead(gatt, characteristic, status)
            gattQueue.handleCommandProcessed()
            if (status != BluetoothGatt.GATT_SUCCESS) return

            val gattCharacteristic = GattCharacteristic.fromUuid(characteristic.uuid)

            when (gattCharacteristic) {
                GattCharacteristic.DeviceName,
                GattCharacteristic.ModelNumberString,
                GattCharacteristic.BatteryLevel,
                GattCharacteristic.PowerSource,
                GattCharacteristic.FirmwareRevision -> statusFragment.handleBaseCharacteristic(characteristic)

                GattCharacteristic.RgbLeds -> {
                    val on = characteristic.getIntValue(gattCharacteristic.format, 0)
                    val red = characteristic.getIntValue(gattCharacteristic.format, 1)
                    val green = characteristic.getIntValue(gattCharacteristic.format, 2)
                    val blue = characteristic.getIntValue(gattCharacteristic.format, 3)
                    val ledState = LedRGBState(
                            on != null && on != 0,
                            red ?: 0,
                            green ?: 0,
                            blue ?: 0

                    )
                    viewModel.colorLed.postValue(ledState)
                }
                else -> { }
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt,
                                           characteristic: BluetoothGattCharacteristic,
                                           status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            gattQueue.handleCommandProcessed()
            if (status != BluetoothGatt.GATT_SUCCESS) return

            val gattCharacteristic = GattCharacteristic.fromUuid(characteristic.uuid)

            when (gattCharacteristic) {
                GattCharacteristic.Digital -> {
                    val led0State =
                            (characteristic.value[0].toInt() and BlinkyThunderboardViewModel.LED_0_ON) != 0
                    val led1State =
                            (characteristic.value[0].toInt() and BlinkyThunderboardViewModel.LED_1_ON) != 0
                    viewModel.led0.postValue(led0State)
                    viewModel.led1.postValue(led1State)
                }
                GattCharacteristic.RgbLeds -> {
                    val on = characteristic.getIntValue(gattCharacteristic.format, 0)
                    val red = characteristic.getIntValue(gattCharacteristic.format, 1)
                    val green = characteristic.getIntValue(gattCharacteristic.format, 2)
                    val blue = characteristic.getIntValue(gattCharacteristic.format, 3)
                    val ledState = LedRGBState(
                            on != null && on != 0,
                            red ?: 0,
                            green ?: 0,
                            blue ?: 0
                    )
                    viewModel.colorLed.postValue(ledState)
                }
                else -> {
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt,
                                             characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicChanged(gatt, characteristic)

            val gattCharacteristic = GattCharacteristic.fromUuid(characteristic.uuid)

            when (gattCharacteristic) {
                GattCharacteristic.BatteryLevel,
                GattCharacteristic.PowerSource -> statusFragment.handleBaseCharacteristic(characteristic)

                GattCharacteristic.Digital -> {
                    val button0State =
                            (characteristic.value[0].toInt() and BlinkyThunderboardViewModel.BUTTON_0_ON) != 0
                    val button1State =
                            (characteristic.value[0].toInt() and BlinkyThunderboardViewModel.BUTTON_1_ON) != 0
                    viewModel.button0.postValue(button0State)
                    viewModel.button1.postValue(button1State)
                }
                else -> { }
            }
        }

        override fun onDescriptorRead(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?,
                                      status: Int) {
            super.onDescriptorRead(gatt, descriptor, status)

            if (descriptor?.uuid == LED_MASK_DESCRIPTOR) {
                viewModel.rgbLedMask.postValue(descriptor?.value?.get(0)?.toInt() ?: 0)
            }

        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor,
                                       status: Int) {
            super.onDescriptorWrite(gatt, descriptor, status)
            gattQueue.handleCommandProcessed()
            if (status != BluetoothGatt.GATT_SUCCESS) return

            when (descriptor.characteristic.uuid) {
                GattCharacteristic.BatteryLevel.uuid -> initControls()
                else -> { }
            }
        }
    }

    companion object {
        private val LED_MASK_DESCRIPTOR: UUID? = UUID.fromString("1c694489-8825-45cc-8720-28b54b1fbf00")
    }
}