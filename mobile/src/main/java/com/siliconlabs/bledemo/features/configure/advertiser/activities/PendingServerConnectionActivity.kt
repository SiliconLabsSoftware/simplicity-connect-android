package com.siliconlabs.bledemo.features.configure.advertiser.activities

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.base.activities.BaseActivity
import com.siliconlabs.bledemo.bluetooth.ble.TimeoutGattCallback
import com.siliconlabs.bledemo.bluetooth.services.BluetoothService
import com.siliconlabs.bledemo.features.scan.browser.activities.DeviceServicesActivity

/* This activity can be invoked from almost every place of the app, but a device needs to have an
 advertiser turned on, that's why it's placed in this package. */
class PendingServerConnectionActivity : BaseActivity() {

    private lateinit var bluetoothBinding: BluetoothService.Binding
    private var service: BluetoothService? = null

    private var deviceToConnect: BluetoothDevice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window,window.decorView).apply {
            // Status bar background is dark (#333333), so icons must be rendered in white.
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContentView(R.layout.activity_pending_server_connection)

        deviceToConnect = intent?.getParcelableExtra(BluetoothService.EXTRA_BLUETOOTH_DEVICE)
        bindBluetoothService()
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothBinding.unbind()
    }

    private fun bindBluetoothService() {
        bluetoothBinding = object : BluetoothService.Binding(this) {
            override fun onBound(service: BluetoothService?) {
                this@PendingServerConnectionActivity.service = service
                connectToDevice()
            }
        }
        bluetoothBinding.bind()
    }

    private fun connectToDevice() {
        deviceToConnect?.let {
            service?.run {
                closeGattServerNotification()
                connectGatt(it, true, gattCallback)
            } ?: finish()
        } ?: finish()
    }

    private fun showServicesActivity(gatt: BluetoothGatt) {
        Intent(this, DeviceServicesActivity::class.java).apply {
            putExtra(DeviceServicesActivity.CONNECTED_DEVICE, gatt.device)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }.also {
            Handler(Looper.getMainLooper()).postDelayed({
                startActivity(it)
            }, TRANSITION_DELAY)
        }
    }

    private val gattCallback = object : TimeoutGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    showServicesActivity(gatt)
                }
                else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    showMessage(R.string.connection_failed)
                }
            } else { showMessage(R.string.connection_failed) }
        }

        override fun onMaxRetriesExceeded(gatt: BluetoothGatt) {
            super.onMaxRetriesExceeded(gatt)
            finish()
        }
    }

    companion object {
        private const val TRANSITION_DELAY = 500L /* Let the user see what is going on */
    }
}