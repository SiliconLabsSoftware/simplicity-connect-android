package com.siliconlabs.bledemo.features.demo.health_thermometer.activities

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MenuItem
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.siliconlabs.bledemo.home_screen.dialogs.SelectDeviceDialog
import com.siliconlabs.bledemo.bluetooth.ble.GattCharacteristic
import com.siliconlabs.bledemo.bluetooth.ble.GattService
import com.siliconlabs.bledemo.bluetooth.ble.TimeoutGattCallback
import com.siliconlabs.bledemo.features.demo.health_thermometer.models.TemperatureReading
import com.siliconlabs.bledemo.features.demo.health_thermometer.models.TemperatureReading.HtmType
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.base.activities.BaseDemoActivity
import com.siliconlabs.bledemo.databinding.ActivityThermometerBinding
import com.siliconlabs.bledemo.utils.AppUtil
import com.siliconlabs.bledemo.utils.BLEUtils.setNotificationForCharacteristic
import com.siliconlabs.bledemo.utils.Notifications

//import kotlinx.android.synthetic.main.activity_thermometer.*

@SuppressLint("MissingPermission")
class HealthThermometerActivity : BaseDemoActivity() {
    private var htmType = HtmType.UNKNOWN
    private var serviceHasBeenSet = false

    private var currentReading: TemperatureReading? = null
    private var currentType: TemperatureReading.Type = TemperatureReading.Type.FAHRENHEIT
    private lateinit var binding: ActivityThermometerBinding

    private val gattCallback: TimeoutGattCallback = object : TimeoutGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                onDeviceDisconnected()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            var startNotificationForCharacteristicFromHere = true
            val services = gatt.services
            if (services != null) {
                for (s in services) {
                    if (s.characteristics != null) {
                        for (ch in s.characteristics) {
                            if (GattCharacteristic.TemperatureType.uuid == ch.uuid) {
                                startNotificationForCharacteristicFromHere = false
                                gatt.readCharacteristic(ch)
                                break
                            }
                        }
                    }
                }
            }
            if (startNotificationForCharacteristicFromHere) {
                setNotificationForCharacteristic(
                    gatt, GattService.HealthThermometer,
                    GattCharacteristic.Temperature,
                    Notifications.INDICATE
                )
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            if (GattCharacteristic.fromUuid(characteristic.uuid) == GattCharacteristic.Temperature) {
                val reading = TemperatureReading.fromCharacteristic(characteristic)
                reading.htmType = htmType
                var deviceName = gatt.device.name
                if (TextUtils.isEmpty(deviceName)) {
                    deviceName = gatt.device.address
                }
                runOnUiThread {
                    setCurrentReading(reading)
                    binding.connectionDeviceName.text = deviceName
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if (GattCharacteristic.fromUuid(characteristic.uuid) == GattCharacteristic.TemperatureType) {
                htmType = HtmType.values()[characteristic.getIntValue(
                    BluetoothGattCharacteristic.FORMAT_UINT8,
                    0
                )]
                setNotificationForCharacteristic(
                    gatt, GattService.HealthThermometer,
                    GattCharacteristic.Temperature,
                    Notifications.INDICATE
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThermometerBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)
        prepareToolBar()
        applyTemperatureUnitUi(isFahrenheit = true)
        binding.thermoLargeTemperature.setCurrentType(currentType)
        binding.thermoUnitFTab.setOnClickListener { setTemperatureUnit(isFahrenheit = true) }
        binding.thermoUnitCTab.setOnClickListener { setTemperatureUnit(isFahrenheit = false) }
    }

    private fun setTemperatureUnit(isFahrenheit: Boolean) {
        currentType = if (isFahrenheit) {
            TemperatureReading.Type.FAHRENHEIT
        } else {
            TemperatureReading.Type.CELSIUS
        }
        applyTemperatureUnitUi(isFahrenheit)
        binding.thermoLargeTemperature.setCurrentType(currentType)
        refreshUi()
    }

    private fun applyTemperatureUnitUi(isFahrenheit: Boolean) {
        val red = ContextCompat.getColor(this, R.color.silabs_redtheme_primary_color)
        val lightLine = ContextCompat.getColor(this, R.color.silabs_divider)
        val selectedH = resources.getDimensionPixelSize(R.dimen.thermo_tab_underline_selected)
        val unselectedH = resources.getDimensionPixelSize(R.dimen.thermo_tab_underline_unselected)
        binding.thermoUnitFUnderline.setBackgroundColor(if (isFahrenheit) red else lightLine)
        binding.thermoUnitCUnderline.setBackgroundColor(if (isFahrenheit) lightLine else red)
        binding.thermoUnitFUnderline.layoutParams = binding.thermoUnitFUnderline.layoutParams.apply {
            height = if (isFahrenheit) selectedH else unselectedH
        }
        binding.thermoUnitCUnderline.layoutParams = binding.thermoUnitCUnderline.layoutParams.apply {
            height = if (isFahrenheit) unselectedH else selectedH
        }
        binding.thermoUnitFUnderline.requestLayout()
        binding.thermoUnitCUnderline.requestLayout()
    }

    private fun prepareToolBar() {
        AppUtil.setEdgeToEdge(window, this)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        binding.fakeStatusBar.setBackgroundColor(
            ContextCompat.getColor(this, R.color.silabs_redtheme_status_bar_color)
        )
        setSupportActionBar(binding.toolbar)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setHomeAsUpIndicator(R.drawable.matter_back)
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.title = getString(R.string.title_Health_Thermometer)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            gatt?.disconnect()
            // Prevent dialog from showing again
            val dialog = supportFragmentManager.findFragmentByTag("select_device_tag") as? SelectDeviceDialog
            if (dialog != null && dialog.isVisible) {
                dialog.dismiss()
            }
            onBackPressed()
            true
        } else super.onOptionsItemSelected(item)
    }


    override fun onResume() {
        super.onResume()
        if (serviceHasBeenSet && service == null || service != null && !service?.isGattConnected(
                connectionAddress
            )!!
        ) {
            onDeviceDisconnected()
        }
    }

    fun setCurrentReading(temperatureReading: TemperatureReading?) {
        currentReading = temperatureReading
        refreshUi()
    }

    private fun refreshUi() {
        if (currentReading != null) {
            binding.thermoLargeTemperature.setTemperature(currentReading)
            binding.thermoTypeValueText.text = getString(currentReading?.htmType?.nameResId!!)
            binding.thermoLargeTimeText.text = currentReading?.getFormattedTime()
        }
    }

    override fun onBluetoothServiceBound() {
        serviceHasBeenSet = true
        service?.registerGattCallback(true, gattCallback)
        gatt?.discoverServices()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        //When you switch thermometers, this activity should get a new intent - at this point, we hide the dialog
        val dialog =
            supportFragmentManager.findFragmentByTag("select_device_tag") as SelectDeviceDialog?
        if (dialog != null && dialog.isVisible) {
            dialog.dismiss()
        }
    }
}
