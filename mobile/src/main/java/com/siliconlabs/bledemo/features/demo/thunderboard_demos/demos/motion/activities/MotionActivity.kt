package com.siliconlabs.bledemo.features.demo.thunderboard_demos.demos.motion.activities

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.appbar.MaterialToolbar
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.bluetooth.ble.GattCharacteristic
import com.siliconlabs.bledemo.bluetooth.ble.GattService
import com.siliconlabs.bledemo.bluetooth.ble.TimeoutGattCallback
import com.siliconlabs.bledemo.databinding.ActivityMotionBinding
import com.siliconlabs.bledemo.features.demo.thunderboard_demos.base.activities.ThunderboardActivity
import com.siliconlabs.bledemo.features.demo.thunderboard_demos.base.utils.SensorChecker
import com.siliconlabs.bledemo.features.demo.thunderboard_demos.base.utils.SensorChecker.ThunderboardSensor
import com.siliconlabs.bledemo.features.demo.thunderboard_demos.demos.motion.viewmodels.MotionViewModel
import com.siliconlabs.bledemo.home_screen.dialogs.SelectDeviceDialog
import com.siliconlabs.bledemo.utils.AppUtil
import timber.log.Timber

class MotionActivity : ThunderboardActivity(), AndroidFragmentApplication.Callbacks {
    override fun exit() { finish() }

    private var calibratingDialog: AlertDialog? = null
    private var gdxFragment: MotionGdxFragment? = null

    private lateinit var viewModel: MotionViewModel
    private lateinit var binding: ActivityMotionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMotionBinding
            .inflate(LayoutInflater.from(this), null, false)
        mainSection?.addView(binding.root)
        viewModel = ViewModelProvider(this).get(MotionViewModel::class.java)
        prepareToolBar()
        setupClickListeners()
        setupDataListeners()
        setupDataInitialValues()

        val modelType = intent.getStringExtra(SelectDeviceDialog.MODEL_TYPE_EXTRA)
        initializeAnimation(modelType)
    }

    private fun prepareToolBar() {
        AppUtil.setEdgeToEdge(window, this)

        // Proper ActionBar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setHomeAsUpIndicator(R.drawable.matter_back)
            setDisplayHomeAsUpEnabled(true)
            setTitle(R.string.motion_demo_title)
        }
        (binding.toolbar as? MaterialToolbar)?.isTitleCentered = false
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }
    override fun onResume() {
        super.onResume()
        if (!setup) queueMotionNotificationsSetup()
    }

    override fun onPause() {
        super.onPause()
        clearMotionNotifications()
    }

    private fun clearMotionNotifications() {
        gattQueue.let {
            it.queueCancelNotifications(getMotionCharacteristic(GattCharacteristic.Calibration))
            it.queueCancelNotifications(getMotionCharacteristic(GattCharacteristic.Acceleration))
            it.queueCancelNotifications(getMotionCharacteristic(GattCharacteristic.Orientation))
        }
    }

    private fun setAcceleration(x: Float, y: Float, z: Float) {
        val accelerationString = getString(R.string.motion_acceleration_g)
        binding.motionDemoAccelerationParent.accelerationX.text = String.format(accelerationString, x)
        binding.motionDemoAccelerationParent.accelerationY.text = String.format(accelerationString, y)
        binding.motionDemoAccelerationParent.accelerationZ.text = String.format(accelerationString, z)
    }

    // Angles are measured in degrees (-180 to 180)
    private fun setOrientation(x: Float, y: Float, z: Float) {
        val degreeString = getString(R.string.motion_orientation_degree)
        binding.motionDemoOrientationParent.orientationX.text = String.format(degreeString, x)
        binding.motionDemoOrientationParent.orientationY.text = String.format(degreeString, y)
        binding.motionDemoOrientationParent.orientationZ.text = String.format(degreeString, z)
        gdxFragment?.setOrientation(x, y, z)
    }

    private fun setupClickListeners() {
       binding.calibrate .setOnClickListener {
            popupCalibratingDialog()
            Handler(Looper.getMainLooper()).postDelayed({
                viewModel.calibrate(gatt)
            }, 600)
        }
    }

    private fun setupDataListeners() {
        viewModel.acceleration.observe(this, Observer {
            setAcceleration(it[0], it[1], it[2])
        })
        viewModel.orientation.observe(this, Observer {
            setOrientation(it[0], it[1], it[2])
        })
    }

    private fun setupDataInitialValues() {
        viewModel.acceleration.postValue(floatArrayOf(0f, 0f, 0f))
        viewModel.orientation.postValue(floatArrayOf(0f, 0f, 0f))
    }

    private fun popupCalibratingDialog() {
        calibratingDialog = AlertDialog.Builder(this).apply {
            setTitle(R.string.motion_calibrating)
            setCancelable(true)
            setMessage(R.string.motion_calibrating_message)
        }.create()
        calibratingDialog?.show()
        calibratingDialog?.let { applyMotionAlertTypography(it) }
    }

    private fun closeCalibratingDialog() {
        calibratingDialog?.dismiss()
    }

    private fun setCalibrateVisible(enabled: Boolean) {
        binding.calibrate.visibility =
            if (enabled) View.VISIBLE
            else View.INVISIBLE
    }

    private fun initializeAnimation(modelType: String?) {
        setCalibrateVisible(false)
        val color = getColor(R.color.silabs_white)
        val fragmentTag = "motion_gdx_fragment"
        val existing = supportFragmentManager.findFragmentByTag(fragmentTag) as? MotionGdxFragment
        if (existing == null) {
            val created = MotionGdxFragment.newInstance(color, modelType)
            supportFragmentManager.beginTransaction()
                .replace(binding.carAnimation.id, created, fragmentTag)
                .commit()
            gdxFragment = created
        } else {
            gdxFragment = existing
        }
        gdxFragment?.setOnSceneLoadedListener(object : MotionGdxFragment.OnSceneLoadedListener {
            override fun onSceneLoaded() { runOnUiThread { setCalibrateVisible(true) } }
        })
    }

    @SuppressLint("MissingPermission")
    private fun showBrokenSensorsMessage(brokenSensors: Set<ThunderboardSensor>) {
        runOnUiThread {
            val dialog = AlertDialog.Builder(this).apply {
                setTitle(getString(R.string.sensor_malfunction_dialog_title))
                setMessage(
                    getString(
                        R.string.critical_sensor_malfunction_dialog_message,
                        TextUtils.join(", ", brokenSensors)
                    )
                )
                setPositiveButton(getString(R.string.button_ok)) { _, _ ->
                    gatt?.disconnect() ?: onDeviceDisconnected()
                }
                setCancelable(false)
            }.show()
            applyMotionAlertTypography(dialog)
        }
    }

    private fun applyMotionAlertTypography(dialog: AlertDialog) {
        val titleColor = getColor(R.color.silabs_redtheme_primary_color)
        val messageColor = getColor(R.color.silabs_redtheme_scanner_body_text_color)
        dialog.findViewById<TextView>(android.R.id.title)?.apply {
            ResourcesCompat.getFont(this@MotionActivity, R.font.stolzl_medium)?.let { typeface = it }
            setTextColor(titleColor)
        }
        dialog.findViewById<TextView>(android.R.id.message)?.apply {
            ResourcesCompat.getFont(this@MotionActivity, R.font.helvetica_neue_medium)?.let { typeface = it }
            setTextColor(messageColor)
        }
    }

    private fun queueMotionNotificationsSetup() {
        gattQueue.let {
            it.queueNotify(getMotionCharacteristic(GattCharacteristic.Acceleration))
            it.queueNotify(getMotionCharacteristic(GattCharacteristic.Orientation))
            it.queueIndicate(getMotionCharacteristic(GattCharacteristic.Calibration)) /* Somehow needed for proper writing to this characteristic. */
        }
    }

    private fun getMotionCharacteristic(characteristic: GattCharacteristic): BluetoothGattCharacteristic? {
        return gatt?.getService(GattService.Motion.number)?.getCharacteristic(characteristic.uuid)
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
            queueMotionNotificationsSetup()
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            gattQueue.handleCommandProcessed()
            if (status != BluetoothGatt.GATT_SUCCESS) return

            val gattCharacteristic = GattCharacteristic.fromUuid(characteristic.uuid)

            when (gattCharacteristic) {
                GattCharacteristic.DeviceName,
                GattCharacteristic.ModelNumberString,
                GattCharacteristic.BatteryLevel,
                GattCharacteristic.PowerSource,
                GattCharacteristic.FirmwareRevision -> statusFragment.handleBaseCharacteristic(
                    characteristic
                )

                else -> {}
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (status != BluetoothGatt.GATT_SUCCESS) return

            if (characteristic.uuid == GattCharacteristic.Calibration.uuid) {
                when (characteristic.value[0]) {
                    0x01.toByte() -> viewModel.resetOrientation(gatt, characteristic)
                    0x02.toByte() -> closeCalibratingDialog()
                    else -> {}
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)

            val gattCharacteristic = GattCharacteristic.fromUuid(characteristic.uuid)

            when (gattCharacteristic) {
                GattCharacteristic.BatteryLevel,
                GattCharacteristic.PowerSource -> statusFragment.handleBaseCharacteristic(
                    characteristic
                )

                GattCharacteristic.Acceleration -> {
                    val accelerationX = characteristic.getIntValue(gattCharacteristic.format, 0)
                    val accelerationY = characteristic.getIntValue(gattCharacteristic.format, 2)
                    val accelerationZ = characteristic.getIntValue(gattCharacteristic.format, 4)
                    Timber.d("Acceleration; X = $accelerationX, Y = $accelerationY, Z = $accelerationZ")

                    if (setup) {
                        sensorChecker.checkIfMotionSensorBroken(
                            ThunderboardSensor.Acceleration,
                            accelerationX, accelerationY, accelerationZ
                        )
                    }

                    viewModel.acceleration.postValue(
                        floatArrayOf(
                            accelerationX / 1000f, accelerationY / 1000f, accelerationZ / 1000f
                        )
                    )
                }

                GattCharacteristic.Orientation -> {
                    val orientationX = characteristic.getIntValue(gattCharacteristic.format, 0)
                    val orientationY = characteristic.getIntValue(gattCharacteristic.format, 2)
                    val orientationZ = characteristic.getIntValue(gattCharacteristic.format, 4)
                    Timber.d("Orientation; X = $orientationX, Y = $orientationY, Z = $orientationZ")

                    if (setup) {
                        sensorChecker.checkIfMotionSensorBroken(
                            ThunderboardSensor.Orientation,
                            orientationX, orientationY, orientationZ
                        )
                        val brokenSensors = sensorChecker.motionSensors.filter {
                            it.value == SensorChecker.SensorState.BROKEN
                        }.keys
                        setup = false
                        dismissModalDialog()

                        if (brokenSensors.isNotEmpty()) {
                            clearMotionNotifications()
                            showBrokenSensorsMessage(brokenSensors)
                        }
                    }

                    viewModel.orientation.postValue(
                        floatArrayOf(
                            orientationX / 100f, orientationY / 100f, orientationZ / 100f
                        )
                    )
                }

                GattCharacteristic.Calibration -> {
                    when (characteristic.value[1]) {
                        0x01.toByte() -> viewModel.resetOrientation(
                            gatt,
                            characteristic
                        ) /* Somehow needed for properly resetting char's value */
                        else -> {}
                    }
                }

                else -> {}
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            gattQueue.handleCommandProcessed()
        }
    }
}