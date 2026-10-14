package com.siliconlabs.bledemo.features.demo.thunderboard_demos.base.activities

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.base.activities.BaseDemoActivity
import com.siliconlabs.bledemo.bluetooth.ble.GattCharacteristic
import com.siliconlabs.bledemo.bluetooth.ble.GattService
import com.siliconlabs.bledemo.bluetooth.ble.TimeoutGattCallback
import com.siliconlabs.bledemo.databinding.ActivityThunderboardBaseBinding
import com.siliconlabs.bledemo.features.demo.thunderboard_demos.base.fragments.StatusFragment
import com.siliconlabs.bledemo.features.demo.thunderboard_demos.base.models.ThunderBoardDevice
import com.siliconlabs.bledemo.features.demo.thunderboard_demos.base.utils.SensorChecker
import com.siliconlabs.bledemo.home_screen.dialogs.SelectDeviceDialog
import com.siliconlabs.bledemo.utils.GattQueue

//import kotlinx.android.synthetic.main.activity_thunderboard_base.*

abstract class ThunderboardActivity : BaseDemoActivity() {

    protected lateinit var gattQueue: GattQueue
    protected val sensorChecker = SensorChecker()
    protected var setup = true

    protected abstract val gattCallback: TimeoutGattCallback

    protected var mainSection: FrameLayout? = null
    protected lateinit var statusFragment: StatusFragment
    private lateinit var binding: ActivityThunderboardBaseBinding

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityThunderboardBaseBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        mainSection = binding.mainSection as FrameLayout
        statusFragment =
            supportFragmentManager.findFragmentById(R.id.bluegecko_status_fragment) as StatusFragment
    }

    override fun onBluetoothServiceBound() {
        service?.also {
            val preferredName = intent.getStringExtra(SelectDeviceDialog.DEVICE_NAME_EXTRA)
            statusFragment.viewModel.thunderboardDevice.value =
                ThunderBoardDevice(gatt?.device!!, preferredName)
            statusFragment.viewModel.state.postValue(BluetoothProfile.STATE_CONNECTED)
            it.registerGattCallback(gattCallback)
        }
        gattQueue = GattQueue(gatt)
        gatt?.discoverServices()
        showModalDialog(ConnectionStatus.READING_DEVICE_STATE)
    }

    protected fun queueReadingDeviceCharacteristics() {
        gattQueue.let {
            if (statusFragment.viewModel.thunderboardDevice.value?.name == null) {
                it.queueRead(
                    getDeviceCharacteristic(
                        GattService.GenericAccess,
                        GattCharacteristic.DeviceName
                    )
                )
            }
            it.queueRead(
                getDeviceCharacteristic(
                    GattService.DeviceInformation,
                    GattCharacteristic.ModelNumberString
                )
            )
            it.queueRead(
                getDeviceCharacteristic(
                    GattService.BatteryService,
                    GattCharacteristic.BatteryLevel
                )
            )
            it.queueRead(
                getDeviceCharacteristic(
                    GattService.PowerSource,
                    GattCharacteristic.PowerSource
                )
            )
            it.queueRead(
                getDeviceCharacteristic(
                    GattService.DeviceInformation,
                    GattCharacteristic.FirmwareRevision
                )
            )

            it.queueNotify(
                getDeviceCharacteristic(
                    GattService.BatteryService,
                    GattCharacteristic.BatteryLevel
                )
            )
        }
    }

    private fun getDeviceCharacteristic(
        gattService: GattService,
        characteristic: GattCharacteristic
    ): BluetoothGattCharacteristic? {
        return gatt?.getService(gattService.number)?.getCharacteristic(characteristic.uuid)
    }

}