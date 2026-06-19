package com.siliconlabs.bledemo.features.demo.wifi_commissioning.activities

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.ProgressDialog
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pranavpandey.android.dynamic.toasts.DynamicToast
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.base.activities.BaseDemoActivity
import com.siliconlabs.bledemo.bluetooth.ble.GattCharacteristic
import com.siliconlabs.bledemo.bluetooth.ble.GattService
import com.siliconlabs.bledemo.bluetooth.ble.TimeoutGattCallback
import com.siliconlabs.bledemo.bluetooth.services.BluetoothService
import com.siliconlabs.bledemo.databinding.ActivityWifiCommissioningBinding
import com.siliconlabs.bledemo.features.demo.awsiot.AWSIOTDemoActivity
import com.siliconlabs.bledemo.features.demo.connected_lighting.activities.ConnectedLightingActivity
import com.siliconlabs.bledemo.features.demo.devkitsensor917.activities.DevKitSensor917Activity
import com.siliconlabs.bledemo.features.demo.devkitsensor917.activities.DevKitSensor917Activity.Companion.IP_ADDRESS
import com.siliconlabs.bledemo.features.demo.smartlock.activities.SmartLockActivity
import com.siliconlabs.bledemo.features.demo.wifi_commissioning.adapters.AccessPointsAdapter
import com.siliconlabs.bledemo.features.demo.wifi_commissioning.models.AccessPoint
import com.siliconlabs.bledemo.features.demo.wifi_commissioning.models.BoardCommand
import com.siliconlabs.bledemo.features.demo.wifi_commissioning.models.SecurityMode
import com.siliconlabs.bledemo.utils.AppUtil
import com.siliconlabs.bledemo.utils.Converters
import com.siliconlabs.bledemo.utils.CustomToastManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Arrays
import java.util.UUID

/**
 * Created by harika on 18-04-2016.
 */
@SuppressLint("MissingPermission")
class WifiCommissioningActivity : BaseDemoActivity() {

    private lateinit var _binding: ActivityWifiCommissioningBinding
    private var progressDialog: ProgressDialog? = null
    private var disconnectProgressDialog: ProgressDialog? = null

    private var accessPointsAdapter: AccessPointsAdapter? = null
    private val accessPoints = ArrayList<AccessPoint>()
    private var isItemClicked = false
    private var clickedAccessPoint: AccessPoint? = null
    private var connectedAccessPoint: AccessPoint? = null

    private var characteristicWrite: BluetoothGattCharacteristic? = null
    private var characteristicRead: BluetoothGattCharacteristic? = null
    private var characteristicNotification: BluetoothGattCharacteristic? = null
    private val clientCharacteristicConfig = "00002902-0000-1000-8000-00805f9b34fb"

    private val unprintableCharsRegex = Regex("[\\x00-\\x1F]")

    private var sendCommand: BoardCommand.Send = BoardCommand.Send.UNKNOWN
    private var readCommand: BoardCommand.Response = BoardCommand.Response.UNKNOWN

    private val sleepForWrite: Long = 500
    private val sleepForRead: Long = 500
    private lateinit var sharedPref: SharedPreferences

    var connectType = BluetoothService.GattConnectType.WIFI_COMMISSIONING;
    private val timeout = 75000L

    private val handler = Handler(Looper.getMainLooper())
    private var isPullRefreshScan = false

    private val pullRefreshTimeoutRunnable = Runnable {
        isPullRefreshScan = false
        _binding.swipeRefreshContainer.isRefreshing = false
    }

    private val timeoutRunnable = Runnable {
        showToastOnUi("Timeout Expired")
        lifecycleScope.launch {
            delay(5000)
            finish() // Exit the screen
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPref = this.getSharedPreferences("WIFI_Comm_Pref", Context.MODE_PRIVATE)
        _binding = ActivityWifiCommissioningBinding.inflate(layoutInflater)
        setContentView(_binding.root)
        prepareToolBar()
        connectType =
            ((intent?.getSerializableExtra("connectType") as? BluetoothService.GattConnectType)!!)
        setupRecyclerView()
        setupSwipeRefreshLayout()
        setupUiListeners()
    }

    private fun prepareToolBar() {
        AppUtil.setEdgeToEdge(window, this)
        setSupportActionBar(_binding.toolbar)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setHomeAsUpIndicator(R.drawable.matter_back)
            actionBar.setDisplayHomeAsUpEnabled(true)
            actionBar.title = this.getString(R.string.wifi_commissioning_label)
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
        service?.registerGattCallback(mBluetoothGattCallback)
        gatt?.discoverServices()
        showProgressDialogWithTimeout(getString(R.string.ble_detail_device_connection))
    }

    private fun setupRecyclerView() {
        accessPointsAdapter =
            AccessPointsAdapter(accessPoints, object : AccessPointsAdapter.OnItemClickListener {
                override fun onItemClick(itemView: View?, position: Int) {
                    onAccessPointClicked(position)
                }
            })
        _binding.wifiAccessPtsList.apply {
            adapter = accessPointsAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun setupSwipeRefreshLayout() {
        _binding.swipeRefreshContainer.setOnRefreshListener { refreshAccessPointsFromPull() }
        _binding.swipeRefreshContainer.setColorSchemeColors(
            ContextCompat.getColor(this, android.R.color.holo_red_dark),
            ContextCompat.getColor(this, android.R.color.holo_orange_dark),
            ContextCompat.getColor(this, android.R.color.holo_orange_light),
            ContextCompat.getColor(this, android.R.color.holo_red_light)
        )
    }

    private fun refreshAccessPointsFromPull() {
        if (connectedAccessPoint != null) {
            stopPullRefresh()
            return
        }
        if (gatt == null || characteristicWrite == null) {
            stopPullRefresh()
            showToastOnUi(getString(R.string.ble_detail_device_connection))
            return
        }
        isPullRefreshScan = true
        clickedAccessPoint = null
        accessPoints.clear()
        runOnUiThread { accessPointsAdapter?.notifyDataSetChanged() }
        handler.removeCallbacks(pullRefreshTimeoutRunnable)
        writeCommand(BoardCommand.Send.SCAN)
        handler.postDelayed(pullRefreshTimeoutRunnable, PULL_REFRESH_SCAN_TIMEOUT_MS)
    }

    private fun stopPullRefresh() {
        isPullRefreshScan = false
        handler.removeCallbacks(pullRefreshTimeoutRunnable)
        runOnUiThread { _binding.swipeRefreshContainer.isRefreshing = false }
    }

    private fun setupUiListeners() {
        _binding.disconnectBtn.setOnClickListener { showDisconnectionDialog() }
    }

    private fun showProgressDialog(message: String) {
        runOnUiThread {
            progressDialog?.dismiss()
            progressDialog =
                ProgressDialog.show(this, getString(R.string.empty_description), message)
            applyProgressDialogMessageStyle(progressDialog)
        }
    }

    private fun showDisconnectProgressDialog(message: String) {
        runOnUiThread {
            disconnectProgressDialog?.dismiss()
            disconnectProgressDialog =
                ProgressDialog.show(this, getString(R.string.empty_description), message)
            applyProgressDialogMessageStyle(disconnectProgressDialog)
        }
    }

    private fun showProgressDialogWithTimeout(message: String) {
        runOnUiThread {
            progressDialog?.dismiss()
            progressDialog =
                ProgressDialog.show(this, getString(R.string.empty_description), message)
            applyProgressDialogMessageStyle(progressDialog)
            lifecycleScope.launch {
               // delay(60_000) // 60 seconds
                handler.postDelayed({
                    if (progressDialog?.isShowing == true) {
                        progressDialog?.dismiss()
                        progressDialog = null
                        DynamicToast.make(this@WifiCommissioningActivity, "Timeout Expired", 3000)
                            .show()
                        finish()
                    } }, timeout)



            }
        }
    }

    private fun dismissProgressDialog() {
        runOnUiThread { progressDialog?.dismiss() }
    }

    private fun dismissDisconnectProgressDialog() {
        runOnUiThread { disconnectProgressDialog?.dismiss() }
    }

    private fun applyProgressDialogMessageStyle(dialog: ProgressDialog?) {
        val d = dialog ?: return
        val applyStyle: () -> Unit = {
            d.findViewById<TextView>(android.R.id.message)?.apply {
                ResourcesCompat.getFont(this@WifiCommissioningActivity, R.font.stolzl_regular)
                    ?.let { tf ->
                        typeface = tf
                    }
                setTextColor(
                    ContextCompat.getColor(
                        this@WifiCommissioningActivity,
                        R.color.wifi_commissioning_progress_message
                    )
                )
            }
            Unit
        }
        d.window?.decorView?.post(applyStyle) ?: applyStyle()
    }


    private fun onAccessPointClicked(position: Int) {
        isItemClicked = true
        clickedAccessPoint = accessPointsAdapter?.getItem(position)
        showProgressDialog(getString(R.string.check_for_status))
        writeCommand(BoardCommand.Send.CHECK_CONNECTION)
    }

    private fun showToastOnUi(message: String) {
        runOnUiThread {
            CustomToastManager.show(
                this@WifiCommissioningActivity, message, 5000
            )
        }
    }

    fun onAccessPointScanned(accessPoint: AccessPoint) {
        if (!isPullRefreshScan) {
            dismissProgressDialog()
        }
        accessPoints.add(accessPoint)
        runOnUiThread { accessPointsAdapter?.notifyDataSetChanged() }
    }

    fun onAccessPointConnection(isSuccessful: Boolean) {
        dismissProgressDialog()
        if (isSuccessful) {
            println("DMP Connect Type : ${connectType}")
            when (connectType) {
                BluetoothService.GattConnectType.WIFI_COMMISSIONING -> {
                    showToastOnUi(getString(R.string.ap_connect))
                    connectedAccessPoint = clickedAccessPoint
                    connectedAccessPoint?.status = true

                    runOnUiThread { accessPointsAdapter?.notifyDataSetChanged() }
                }

                BluetoothService.GattConnectType.DEV_KIT_SENSOR -> {
                    showToastOnUi(getString(R.string.ap_connect))
                    connectedAccessPoint = clickedAccessPoint
                    connectedAccessPoint?.status = true
                    val devKitIntent = Intent(
                        this,
                        DevKitSensor917Activity::class.java
                    ).apply {
                        putExtra(IP_ADDRESS, clickedAccessPoint?.ipAddress)
                    }
                    storeInfo(clickedAccessPoint!!.ipAddress!!)
                    println("BLE_PROV ipAddress:${clickedAccessPoint!!.ipAddress}")
                    startActivity(devKitIntent)
                    this.finish()

                }

                BluetoothService.GattConnectType.AWS_DEMO -> {

                    showToastOnUi(getString(R.string.ap_connect))
                    connectedAccessPoint = clickedAccessPoint
                    connectedAccessPoint?.status = true
                    runOnUiThread { accessPointsAdapter?.notifyDataSetChanged() }
                    Handler(Looper.getMainLooper()).postDelayed({
                        val devKitIntent = Intent(
                            this,
                            AWSIOTDemoActivity::class.java
                        ).apply {
                            putExtra(IP_ADDRESS, clickedAccessPoint?.ipAddress)
                        }
                        storeInfo(clickedAccessPoint!!.ipAddress!!)
                        println("BLE_PROV ipAddress:${clickedAccessPoint!!.ipAddress}")
                        startActivity(devKitIntent)
                        this.finish()
                    }, 5000)
                }

                BluetoothService.GattConnectType.SMART_LOCK -> {
                    showToastOnUi(getString(R.string.ap_connect))
                    connectedAccessPoint = clickedAccessPoint
                    connectedAccessPoint?.status = true
                    val devKitIntent = Intent(
                        this,
                        SmartLockActivity::class.java
//                        SmartActivity::class.java
                    ).apply {
                        putExtra(IP_ADDRESS, clickedAccessPoint?.ipAddress)
                    }
                    storeInfo(clickedAccessPoint!!.ipAddress!!)
                    println("BLE_PROV ipAddress:${clickedAccessPoint!!.ipAddress}")
                    activityLauncher.launch(devKitIntent)

                }

                BluetoothService.GattConnectType.LIGHT -> {
                    showToastOnUi(getString(R.string.ap_connect))
                    connectedAccessPoint = clickedAccessPoint
                    connectedAccessPoint?.status = true
                    val devKitIntent = Intent(
                        this,
                        ConnectedLightingActivity::class.java
                    ).apply {
                        putExtra(IP_ADDRESS, clickedAccessPoint?.ipAddress)
                    }
                    storeInfo(clickedAccessPoint!!.ipAddress!!)
                    println("DMP BLE_PROV ipAddress:${clickedAccessPoint!!.ipAddress}")
                    activityLauncher.launch(devKitIntent)

                }

                else -> null
            }
        } else {
            showToastOnUi(getString(R.string.ap_connect_fail))
        }
    }

    fun onAccessPointDisconnection(isSuccessful: Boolean) {
        dismissDisconnectProgressDialog()
        if (isSuccessful) {
            showToastOnUi(getString(R.string.ap_disconnect_success))
            when (connectType) {
                BluetoothService.GattConnectType.WIFI_COMMISSIONING -> {
                    handler.removeCallbacks( timeoutRunnable)
                    connectedAccessPoint = null
                    scanForAccessPoints()
                    toggleMainView(isAccessPointConnected = false)
                }

                BluetoothService.GattConnectType.DEV_KIT_SENSOR -> {
                    connectedAccessPoint = null
                    scanForAccessPoints()
                    toggleMainView(isAccessPointConnected = false)
                    showToastOnUi(getString(R.string.ap_disconnect_success))
                }

                BluetoothService.GattConnectType.AWS_DEMO -> {
                    connectedAccessPoint = null
                    scanForAccessPoints()
                    toggleMainView(isAccessPointConnected = false)
                    showToastOnUi(getString(R.string.ap_disconnect_success))
                }

                BluetoothService.GattConnectType.SMART_LOCK -> {
                    connectedAccessPoint = null
                    scanForAccessPoints()
                    toggleMainView(isAccessPointConnected = false)
                    showToastOnUi(getString(R.string.ap_disconnect_success))
                }

                BluetoothService.GattConnectType.LIGHT -> {
                    connectedAccessPoint = null
                    scanForAccessPoints()
                    toggleMainView(isAccessPointConnected = false)
                    showToastOnUi(getString(R.string.ap_disconnect_success))
                }

                else -> null
            }
            handler.removeCallbacks(timeoutRunnable) // Remove timeout callback if success
        } else {
            showToastOnUi(getString(R.string.ap_disconnect_fail))
        }
    }

    fun isAccessPointConnected(isConnected: Boolean) {
        dismissProgressDialog()
        if (isConnected) {
            when (connectType) {
                BluetoothService.GattConnectType.WIFI_COMMISSIONING -> {
                    if (isItemClicked) { /* Board already connected when clicking on item */
                        showDisconnectionDialog()
                    } else { /* Board connected when entering the app */
                        toggleMainView(isAccessPointConnected = true)
                    }
                }

                BluetoothService.GattConnectType.DEV_KIT_SENSOR -> {

                    val devKitIntent = Intent(
                        this,
                        DevKitSensor917Activity::class.java
                    ).apply {
                        putExtra(IP_ADDRESS, getInfo())
                    }
                    println("BLE_PROV ipAddress:${getInfo()}")
                    startActivity(devKitIntent)
                    this.finish()
                }

                BluetoothService.GattConnectType.AWS_DEMO -> {

                    val devKitIntent = Intent(
                        this,
                        AWSIOTDemoActivity::class.java
                    ).apply {
                        putExtra(IP_ADDRESS, getInfo())
                    }
                    println("BLE_PROV ipAddress:${getInfo()}")
                    startActivity(devKitIntent)
                    this.finish()
                }

                BluetoothService.GattConnectType.SMART_LOCK -> {
                    val intent = Intent(
                        this,
                        SmartLockActivity::class.java
                        // SmartActivity::class.java
                    ).apply {
                        putExtra(IP_ADDRESS, getInfo())
                    }
                    println("BLE_PROV ipAddress:${getInfo()}")
                    activityLauncher.launch(intent)
                }

                BluetoothService.GattConnectType.LIGHT -> {
                    val intent = Intent(
                        this,
                        ConnectedLightingActivity::class.java

                    ).apply {
                        putExtra(IP_ADDRESS, getInfo())
                    }
                    println("DMP BLE_PROV ipAddress:${getInfo()}")
                    activityLauncher.launch(intent)
                }

                else -> null
            }
        } else {
            clickedAccessPoint?.let { /* No board connected when clicking on item */
                if (it.securityMode != SecurityMode.OPEN) showPasswordDialog()
                else {
                    it.password = getString(R.string.empty_description)
                    writeCommand(BoardCommand.Send.SSID, it.name)
                    showProgressDialog(getString(R.string.config_AP))
                }
            } ?: scanForAccessPoints() /* No board connected when entering the app */
        }
    }

    fun onFirmwareVersionReceived(firmwareVersion: String) {
        runOnUiThread { _binding.firmwareVersionTv.text = firmwareVersion }
        writeCommand(BoardCommand.Send.CHECK_CONNECTION)
    }

    private fun toggleMainView(isAccessPointConnected: Boolean) {
        runOnUiThread {
            _binding.apConnectedLayout.visibility =
                if (isAccessPointConnected) View.VISIBLE else View.GONE
            _binding.swipeRefreshContainer.visibility =
                if (isAccessPointConnected) View.GONE else View.VISIBLE
            if (isAccessPointConnected) {
                stopPullRefresh()
            }
        }
    }

    private fun showDisconnectionDialog() {
        val dialogMessage =
            if (connectedAccessPoint?.name == clickedAccessPoint?.name) getString(R.string.disconnect_title)
            else getString(R.string.another_ap_connected)

        AlertDialog.Builder(this).apply {
            setCancelable(false)
            setMessage(dialogMessage)
            setPositiveButton(getString(R.string.yes)) { dialog: DialogInterface, _: Int ->
                //showProgressDialog(getString(R.string.disconnect_ap))
                showDisconnectProgressDialog(getString(R.string.disconnect_ap))
                if (connectType == BluetoothService.GattConnectType.AWS_DEMO
                    || connectType == BluetoothService.GattConnectType.SMART_LOCK || connectType == BluetoothService.GattConnectType.LIGHT
                ) {
                    handler.postDelayed(timeoutRunnable, timeout) // Schedule timeout
                    // handler.postDelayed({
                    writeCommand(BoardCommand.Send.DISCONNECTION)
                    dialog.cancel()
                    /*if (!isFinishing) {
                        showTimeOutMessageAndFinish()
                    }*/
                    //}, twentySeconds)
                } else {
                    writeCommand(BoardCommand.Send.DISCONNECTION)
                    dialog.cancel()
                }
            }
            setNegativeButton(getString(R.string.no)) { dialog: DialogInterface, _: Int ->
                isItemClicked = false
                dialog.cancel()
            }
            runOnUiThread {
                create()
                show()
            }
        }
    }


    private fun showPasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.secured_ap_connect_dialog, null)
        val password = dialogView.findViewById<EditText>(R.id.password_et)
        //functionality for Hide and Un-hide the password
        password.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                // Check if the touch is on the drawable end (right side)
                val drawableEnd = password.compoundDrawablesRelative[2]
                if (drawableEnd != null && event.rawX >= (password.right - drawableEnd.bounds.width())) {
                    // Toggle password visibility
                    val isPasswordVisible = password.inputType == InputType.TYPE_CLASS_TEXT
                    if (isPasswordVisible) {
                        password.inputType =
                            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                        password.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            0,
                            0,
                            R.drawable.visibility_off,
                            0
                        )
                    } else {
                        password.inputType = InputType.TYPE_CLASS_TEXT
                        password.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            0,
                            0,
                            R.drawable.baseline_remove_red_eye_24,
                            0
                        )
                    }
                    // Move cursor to end after changing input type
                    password.setSelection(password.text.length)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
        AlertDialog.Builder(this).apply {
            setView(dialogView)
            setTitle(clickedAccessPoint?.name)
            setPositiveButton(getString(R.string.connect_btn_txt)) { dialog: DialogInterface?, _: Int ->
                if (password.text.toString().isNotEmpty()) {
                    dialog?.dismiss()
                    showProgressDialog(getString(R.string.config_AP))
                    clickedAccessPoint?.let {
                        it.password = password.text.toString()
                        writeCommand(BoardCommand.Send.SSID, it.name)
                    }
                } else {
                    showToastOnUi(getString(R.string.invalid_password))
                    dialog?.dismiss()
                }
            }
            setNegativeButton(getString(R.string.button_cancel)) { dialog: DialogInterface?, _: Int ->
                dialog?.dismiss()
            }
            runOnUiThread {
                create()
                show()
            }
        }
    }

    private fun scanForAccessPoints() {
        stopPullRefresh()
        clickedAccessPoint = null
        accessPoints.clear()
        runOnUiThread { accessPointsAdapter?.notifyDataSetChanged() }
        //showProgressDialog(getString(R.string.scanning_for_access_points))
        showProgressDialogWithTimeout(getString(R.string.scanning_for_access_points))
        //handlePopup(this,getString(R.string.scanning_for_access_points))
        writeCommand(BoardCommand.Send.SCAN)
    }


    private val mBluetoothGattCallback: TimeoutGattCallback = object : TimeoutGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Timber.d("onConnectionStateChange; status = $status, newState = $newState")
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Timber.e("onConnectionStateChange; status = $status, newState = $newState")
                onDeviceDisconnected()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Timber.d("onServicesDiscovered; status = $status")
            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
            gatt.getService(GattService.WifiCommissioningService.number)?.let {
                characteristicWrite =
                    it.getCharacteristic(GattCharacteristic.WifiCommissioningWrite.uuid)
                characteristicRead =
                    it.getCharacteristic(GattCharacteristic.WifiCommissioningRead.uuid)
                characteristicNotification =
                    it.getCharacteristic(GattCharacteristic.WifiCommissioningNotify.uuid)
            }

            if (gatt.setCharacteristicNotification(characteristicNotification, true)) {
                Timber.d("Notifications enabled for ${characteristicNotification?.uuid}")
                characteristicNotification?.getDescriptor(UUID.fromString(clientCharacteristicConfig))
                    ?.let {
                        it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(it)
                    }
            }

            writeCommand(BoardCommand.Send.GET_FIRMWARE_VERSION)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Timber.d("onCharacteristicWrite; characteristic = ${characteristic.uuid}, sendCommand = $sendCommand, status = $status")
            Timber.d("Raw data = ${Arrays.toString(characteristic.value)}")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (sendCommand) {
                    BoardCommand.Send.SSID -> writeCommand(
                        BoardCommand.Send.SECURITY_TYPE,
                        clickedAccessPoint?.securityMode?.value?.toString()!!
                    )

                    BoardCommand.Send.DISCONNECTION -> readOperation()
                    BoardCommand.Send.SECURITY_TYPE -> {
                        if (clickedAccessPoint?.password!!.isNotEmpty()) {
                            writeCommand(BoardCommand.Send.PASSWORD, clickedAccessPoint?.password!!)
                        } else {
                            sendCommand = BoardCommand.Send.SSID
                            readOperation()
                        }
                    }

                    BoardCommand.Send.PASSWORD -> {
                        sendCommand = BoardCommand.Send.SSID
                        readOperation()
                    }

                    BoardCommand.Send.CHECK_CONNECTION -> {
                        sendCommand = BoardCommand.Send.CHECK_CONNECTION
                        readOperation()
                    }

                    BoardCommand.Send.GET_FIRMWARE_VERSION -> {
                        sendCommand = BoardCommand.Send.GET_FIRMWARE_VERSION
                        readOperation()
                    }

                    else -> {}
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Timber.d("onCharacteristicRead; characteristic = ${characteristic.uuid}, readCommand = $readCommand, status = $status")
            Timber.d("Raw data = ${Arrays.toString(characteristic.value)}")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                readCommand = BoardCommand.Response.fromInt(
                    characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                )
                val statusBit =
                    characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1)

                if (readCommand.value != sendCommand.value) {
                    readOperation()
                } else {
                    when (readCommand) {
                        BoardCommand.Response.CONNECTION -> {
                            if (statusBit == 1) {
                                clickedAccessPoint?.macAddress = convertMacAddressToString(
                                    characteristic.value.copyOfRange(
                                        3,
                                        9
                                    )
                                )
                                clickedAccessPoint?.ipAddress = convertIpAddressToString(
                                    characteristic.value.copyOfRange(
                                        10,
                                        14
                                    )
                                )
                                onAccessPointConnection(true)
                            } else {
                                onAccessPointConnection(false)
                            }
                        }

                        BoardCommand.Response.DISCONNECTION -> onAccessPointDisconnection(statusBit == 1)
                        BoardCommand.Response.CHECK_CONNECTION -> isAccessPointConnected(statusBit == 1)
                        BoardCommand.Response.FIRMWARE_VERSION -> {

                            val bytes: ByteArray = characteristic.value
                            if (statusBit > 0) {
                                val firmareVersionString = buildString {
                                    for ((index, byte) in bytes.sliceArray(FIRMWARE_BYTE_START_INDEX until FIRMWARE_BYTE_END_INDEX)
                                        .withIndex()) {
                                        if (index < 2) {
                                            append(
                                                byte.toUByte().toString(RADIX_HEX)
                                                    .padStart(PADDING_LENGTH, '0')
                                            ) // Convert byte to hexadecimal string
                                        } else if (index != 7) {
                                            append(".${byte.toUByte()}") // Append byte value with a dot separator
                                        }
                                    }
                                }
                                onFirmwareVersionReceived(firmareVersionString)
                            }


                        }

                        BoardCommand.Response.UNKNOWN -> {
                            if (sendCommand != BoardCommand.Send.SSID) readOperation()
                        }
                    }
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            Timber.d("onCharacteristicChanged; characteristic = ${characteristic.uuid}")
            Timber.d("Raw data = ${Arrays.toString(characteristic.value)}")

            if (characteristic.uuid == GattCharacteristic.WifiCommissioningNotify.uuid) {
                /* Scanned access point info. */
                val rawName = characteristic.getStringValue(2)
                val rawSecurityMode =
                    characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                Timber.d("Raw access point name = $rawName")

                onAccessPointScanned(
                    AccessPoint(
                        name = rawName.replace(unprintableCharsRegex, ""),
                        securityMode = SecurityMode.fromInt(rawSecurityMode)
                    )
                )
            }
        }
    }

    private fun writeCommand(command: BoardCommand.Send) {
        sendCommand = command
        writeUntilSuccess(sendCommand.value.toString())
    }

    private fun writeCommand(command: BoardCommand.Send, additionalData: String) {
        sendCommand = command

        val stringToWrite = StringBuilder().apply {
            append(sendCommand.value)
            if (additionalData.length < 10) append('0')
            append(additionalData.length)
            append(additionalData)
        }.toString()

        writeUntilSuccess(stringToWrite)
    }

    private fun writeUntilSuccess(dataToWrite: String) {
        Thread {
            var writeStatus = false
            while (!writeStatus) {
                writeStatus = writeToCharacteristic(gatt, characteristicWrite, dataToWrite)
                Thread.sleep(sleepForWrite)
            }
            Timber.d("Command $sendCommand written successfully")
        }.start()
    }

    private fun writeToCharacteristic(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        data: String
    ): Boolean {
        return if (gatt != null && characteristic != null) {
            Timber.d("Data to write = $data")
            if (data.length > 100) {
                Timber.d("Attempt to write to characteristic with more then 64 bytes")
                return false
            }
            characteristic.setValue(data)
            gatt.writeCharacteristic(characteristic)
        } else {
            Timber.d("Fail to write: null references")
            false
        }
    }

    private fun readOperation() {
        Thread {
            gatt?.readCharacteristic(characteristicRead)
            Thread.sleep(sleepForRead)
        }.start()
    }

    private fun convertMacAddressToString(rawData: ByteArray): String {
        return StringBuilder().apply {
            rawData.forEachIndexed { index, byte ->
                val hexValue = Integer.toHexString(Converters.getIntFromTwosComplement(byte))

                append(hexValue)
                if (hexValue.length == 1) insert(length - 1, '0')
                if (index != rawData.size - 1) append(':')
            }
        }.toString()
    }

    private fun convertIpAddressToString(rawData: ByteArray): String {
        return StringBuilder().apply {
            rawData.forEachIndexed { index, byte ->
                append(Converters.getIntFromTwosComplement(byte))
                if (index != rawData.size - 1) append('.')
            }
        }.toString()
    }

    private fun isNetworkAvailable(context: Context?): Boolean {
        if (context == null) return false
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val capabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            if (capabilities != null) {
                when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                        return true
                    }
                }
            }
        } else {
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            if (activeNetworkInfo != null && activeNetworkInfo.isConnected) {
                return true
            }
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        progressDialog?.dismiss()
        handler.removeCallbacks(pullRefreshTimeoutRunnable)
        handler.removeCallbacks(timeoutRunnable)
        handler.removeCallbacksAndMessages(null)
    }

    private fun storeInfo(info: String) {
        val ipInfo = sharedPref.edit()
        ipInfo.putString("ipaddress", info)
        ipInfo.apply()
    }

    private fun getInfo(): String? {
        return sharedPref.getString("ipaddress", "")
    }


    private val activityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
        { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                if (data != null) {
                    val close = result.data?.getBooleanExtra(CLOSE, false) ?: false
                    if (close) {
                        this.finish()
                    }
                }
            }
        }
    )

    companion object {
        private const val PULL_REFRESH_SCAN_TIMEOUT_MS = 8_000L
        private const val RADIX_HEX = 16
        private const val PADDING_LENGTH = 2
        private const val FIRMWARE_BYTE_START_INDEX = 2
        private const val FIRMWARE_BYTE_END_INDEX = 11
        const val CLOSE = "close_activity"
    }

}