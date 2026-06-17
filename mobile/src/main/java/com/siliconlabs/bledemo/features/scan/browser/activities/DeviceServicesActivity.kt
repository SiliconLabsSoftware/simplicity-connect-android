/*
 * Bluegiga’s Bluetooth Smart Android SW for Bluegiga BLE modules
 * Contact: support@bluegiga.com.
 *
 * This is free software distributed under the terms of the MIT license reproduced below.
 *
 * Copyright (c) 2013, Bluegiga Technologies
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files ("Software")
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * THIS CODE AND INFORMATION ARE PROVIDED "AS IS" WITHOUT WARRANTY OF
 * ANY KIND, EITHER EXPRESSED OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE IMPLIED WARRANTIES OF MERCHANTABILITY AND/OR FITNESS FOR A  PARTICULAR PURPOSE.
 */
package com.siliconlabs.bledemo.features.scan.browser.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.*
import android.view.View.OnScrollChangeListener
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.children
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import by.kirich1409.viewbindingdelegate.viewBinding
import com.siliconlabs.bledemo.base.activities.BaseActivity
import com.siliconlabs.bledemo.bluetooth.ble.ErrorCodes
import com.siliconlabs.bledemo.bluetooth.ble.TimeoutGattCallback
import com.siliconlabs.bledemo.bluetooth.services.BluetoothService
import com.siliconlabs.bledemo.features.scan.browser.dialogs.*
import com.siliconlabs.bledemo.features.scan.browser.dialogs.ErrorDialog.OtaErrorCallback
import com.siliconlabs.bledemo.features.scan.browser.fragments.*
import com.siliconlabs.bledemo.features.scan.browser.models.OtaFileType
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.bluetooth.ble.BluetoothDeviceInfo
import com.siliconlabs.bledemo.common.views.FlyInBar
import com.siliconlabs.bledemo.databinding.ActivityDeviceServicesBinding
import com.siliconlabs.bledemo.features.scan.browser.fragments.BrowserFragment.Companion.ORIGIN
import com.siliconlabs.bledemo.features.scan.browser.fragments.BrowserFragment.Companion.RECYCLERVIEW_POSITION
import com.siliconlabs.bledemo.home_screen.viewmodels.ScanFragmentViewModel
import com.siliconlabs.bledemo.utils.*
import timber.log.Timber
import java.io.*
import java.util.*

@SuppressWarnings("LogNotTimber")
@SuppressLint("MissingPermission")
class DeviceServicesActivity : BaseActivity() {

    private lateinit var handler: Handler

    private var viewState = ViewState.IDLE
    private var mtuReadType = MtuReadType.VIEW_INITIALIZATION
    private var isLogFragmentOn = false

    private var menu: Menu? = null

    private var reliable = true
    private var doubleStepUpload = false

    private var boolFullOTA = false
    var isUiCreated = false
    private var otaDataCharPresent = false

    private var mtuRequestDialog: MtuRequestDialog? = null
    private var otaConfigDialog: OtaConfigDialog? = null
    private var otaProgressDialog: OtaProgressDialog? = null
    private var otaLoadingDialog: OtaLoadingDialog? = null
    private var errorDialog: ErrorDialog? = null

    private var MTU = 247
    private var connectionPriority = BluetoothGatt.CONNECTION_PRIORITY_BALANCED
    private var mtuDivisible = 0
    private var otatime: Long = 0
    private var pack = 0
    private var otafile: ByteArray? = null


    private var delayNoResponse = 1
    private var currentOtaFileType: OtaFileType = OtaFileType.APPLICATION

    // OTA file paths
    private var appPath = ""
    private var stackPath = ""

    private var bluetoothBinding: BluetoothService.Binding? = null
    var bluetoothService: BluetoothService? = null
        private set

    private var bluetoothDevice: BluetoothDevice? = null
    private var devicesAdapterRecyclerViewItemPos :Int = -1
    var bluetoothGatt: BluetoothGatt? = null

    private var retryAttempts = 0

    private lateinit var binding:ActivityDeviceServicesBinding

    lateinit var scanFragmentViewModel: ScanFragmentViewModel

    private val hideFabOnScrollChangeListener =
        OnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
            if (scrollY > oldScrollY) {
                binding.btnDisconnect.hide()
            } else {
                binding.btnDisconnect.show()
            }
        }

    private val remoteServicesFragment = RemoteServicesFragment(hideFabOnScrollChangeListener)
    private val localServicesFragment = LocalServicesFragment()
    private var activeFragment: Fragment = remoteServicesFragment

    private val bondStateChangeListener = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val newState =
                    intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                displayBondState(newState)
                if (newState == BluetoothDevice.BOND_BONDED) {
                    showMessage(getString(R.string.device_bonded_successfully))
                }
            }
        }
    }

    private val bluetoothReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_OFF -> finish()
                }
            }
        }
    }

    private val gattCallback: TimeoutGattCallback = object : TimeoutGattCallback() {
        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (viewState == ViewState.IDLE) {
                super.onReadRemoteRssi(gatt, rssi, status)
                runOnUiThread { binding.tvRssi.text = resources.getString(R.string.n_dBm, rssi) }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)

            when (mtuReadType) {
                MtuReadType.VIEW_INITIALIZATION -> {
                    MTU = if (status == BluetoothGatt.GATT_SUCCESS) mtu
                    else DEFAULT_MTU_VALUE
                    //gatt.requestConnectionPriority(connectionPriority)
                }

                MtuReadType.UPLOAD_INITIALIZATION -> {
                    MTU = if (status == BluetoothGatt.GATT_SUCCESS) mtu
                    else DEFAULT_MTU_VALUE
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH) // optimize upload speed
                    writeOtaControl(OTA_CONTROL_START_COMMAND)
                }

                MtuReadType.USER_REQUESTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        MTU = mtu
                        showMessage(getString(R.string.MTU_colon_n, MTU))
                    } else {
                        showMessage(getString(R.string.error_requesting_mtu, status))
                    }
                    mtuRequestDialog?.dismiss()
                    mtuRequestDialog = null
                }
            }
        }

        //CALLBACK ON CONNECTION STATUS CHANGES
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            if (bluetoothGatt?.device?.address != gatt.device.address) {
                /* Some other device changed its state - do not react */
                return
            }

            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    if (viewState == ViewState.REBOOTING ||
                        viewState == ViewState.REBOOTING_NEW_FIRMWARE
                    ) {
                        handler.postDelayed({
                            bluetoothGatt = null
                            gatt.discoverServices()
                        }, 250)
                    }
                }

                BluetoothGatt.STATE_DISCONNECTED -> {
                    when (viewState) {
                        ViewState.IDLE -> when (status) {
                            0 -> finish()
                            else -> {
                                showLongMessage(
                                    ErrorCodes.getDeviceDisconnectedMessage(
                                        getDeviceName(),
                                        status
                                    )
                                )
                                finish()
                            }
                        }

                        ViewState.REBOOTING -> when (status) {
                            19 -> {
                                /* Device is reconnecting into ota mode */
                                showInitializationInfo()
                            }

                            0 -> {} /* Device reconnecting for another file upload, let it be */
                            else -> showErrorDialog(status)
                        }

                        ViewState.UPLOADING -> showErrorDialog(status)
                        ViewState.REBOOTING_NEW_FIRMWARE -> { /* Do nothing */
                            Log.d("BLE_DEBUG","reboot completed")
                            BLEUtils.IS_FIRMWARE_REBOOTED = true
                            BLEUtils.REBOOTED_FIRMWARE_GATT_ADDRESS = gatt.device.address
                        }

                        else -> {
                            finish()
                        }
                    }
                }
            }
        }

        //CALLBACK ON CHARACTERISTIC READ
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            remoteServicesFragment.updateCurrentCharacteristicView(characteristic.uuid)

            if (viewState == ViewState.REBOOTING_NEW_FIRMWARE) {
                runOnUiThread { supportActionBar?.title = characteristic.getStringValue(0) }
                viewState = ViewState.IDLE
                bluetoothService?.isNotificationEnabled = true

                handler.postDelayed({
                    initServicesFragments(bluetoothGatt?.services.orEmpty())
                    mtuReadType = MtuReadType.VIEW_INITIALIZATION
                    gatt.requestMtu(INITIALIZATION_MTU_VALUE)
                }, GATT_FETCH_ON_SERVICE_DISCOVERED_DELAY)
            }
        }

        //CALLBACK ON CHARACTERISTIC WRITE (PROPERTY: WHITE)
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            remoteServicesFragment.updateCurrentCharacteristicView(characteristic.uuid, status)

            if (status != BluetoothGatt.GATT_SUCCESS) {
                showErrorDialog(status)
                if (viewState == ViewState.UPLOADING) viewState = ViewState.IDLE
            } else {
                when (characteristic.uuid) {
                    UuidConsts.OTA_CONTROL -> {
                        when (characteristic.value[0]) {
                            0x00.toByte() -> {
                                if (viewState == ViewState.REBOOTING) {
                                    reloadDeviceIntoOtaMode()
                                } else if (viewState == ViewState.INITIALIZING_UPLOAD) {
                                    viewState = ViewState.UPLOADING
                                    startOtaUpload()
                                }
                            }

                            0x03.toByte() -> {
                                if (viewState == ViewState.UPLOADING) {
                                    viewState = ViewState.IDLE
                                    if (boolFullOTA) {
                                        prepareForNextUpload()
                                    } else {
                                        runOnUiThread { otaProgressDialog?.toggleEndButton(isEnabled = true) }
                                    }
                                }
                            }

                            else -> {}
                        }
                    }

                    UuidConsts.OTA_DATA -> handleReliableUploadResponse()
                }
            }
            bluetoothGatt?.readCharacteristic(characteristic)
        }

        //CALLBACK ON DESCRIPTOR WRITE
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            remoteServicesFragment.updateDescriptorView(descriptor)
        }

        //CALLBACK ON DESCRIPTOR READ
        override fun onDescriptorRead(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            super.onDescriptorRead(gatt, descriptor, status)
            remoteServicesFragment.updateDescriptorView(descriptor)
        }

        //CALLBACK ON CHARACTERISTIC CHANGED VALUE (READ - CHARACTERISTIC NOTIFICATION)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            remoteServicesFragment.updateCharacteristicView(characteristic)
        }

        //CALLBACK ON SERVICES DISCOVERED
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            bluetoothGatt = gatt

            if (status != BluetoothGatt.GATT_SUCCESS) showErrorDialog(status)
            else {
                printServicesInfo(gatt)
                otaDataCharPresent = (gatt.getService(UuidConsts.OTA_SERVICE)
                    ?.getCharacteristic(UuidConsts.OTA_DATA) != null)

                when (viewState) {
                    ViewState.REFRESHING_SERVICES -> {
                        handler.postDelayed({
                            runOnUiThread { initServicesFragments(bluetoothGatt?.services.orEmpty()) }
                        }, GATT_FETCH_ON_SERVICE_DISCOVERED_DELAY)
                        viewState = ViewState.IDLE
                    }

                    ViewState.IDLE -> {
                        handler.postDelayed({
                            initServicesFragments(bluetoothGatt?.services.orEmpty())
                            mtuReadType = MtuReadType.VIEW_INITIALIZATION
                            gatt.requestMtu(INITIALIZATION_MTU_VALUE)
                        }, GATT_FETCH_ON_SERVICE_DISCOVERED_DELAY)
                    }

                    ViewState.REBOOTING -> {
                        viewState = ViewState.INITIALIZING_UPLOAD
                        mtuReadType = MtuReadType.UPLOAD_INITIALIZATION
                        bluetoothGatt?.requestMtu(INITIALIZATION_MTU_VALUE)
                    }

                    ViewState.REBOOTING_NEW_FIRMWARE -> {
                        bluetoothGatt?.readCharacteristic(getDeviceNameCharacteristic())
                    }

                    else -> {}
                }
            }
        }
    }

    private fun showInitializationInfo() {
        runOnUiThread { otaLoadingDialog?.updateMessage(getString(R.string.ota_rebooting_text)) }
        handler.postDelayed({
            runOnUiThread { otaLoadingDialog?.updateMessage(getString(R.string.ota_loading_text)) }
        }, 1500)
    }

    private fun startOtaUpload() {
        hideOtaLoadingDialog()
        otafile = readChosenFile()
        pack = 0
        if (reliable) {
            setupMtuDivisible()
        }

        hideOtaProgressDialog()
        showOtaProgressDialog(
            OtaProgressDialog.OtaInfo(
                prepareFilename(),
                otafile?.size,
                if (reliable) mtuDivisible else MTU,
                doubleStepUpload,
                if (doubleStepUpload) stackPath != "" else true
            )
        )

        Thread {
            Thread.sleep(DIALOG_DELAY)
            if (reliable) otaWriteDataReliable()
            else writeOtaData(otafile)
        }.start()
    }

    private fun reloadDeviceIntoOtaMode() {
        showOtaLoadingDialog(getString(R.string.ota_resetting_text))
        reconnect()
    }

    private fun setupMtuDivisible() {
        var minus = 0
        do {
            mtuDivisible = MTU - 3 - minus
            minus++
        } while (mtuDivisible % 4 != 0)
    }

    private fun handleReliableUploadResponse() {
        if (reliable) {
            pack += mtuDivisible
            if (pack <= otafile?.size!! - 1) {
                otaWriteDataReliable()
            } else if (pack > otafile?.size!! - 1) {
                handler.post {
                    runOnUiThread {
                        otaProgressDialog?.stopUploading()
                    }
                }
                retryAttempts = 0
                writeOtaControl(OTA_CONTROL_END_COMMAND)
            }
        }
    }

    private fun prepareForNextUpload() {
        viewState = ViewState.REBOOTING
        stackPath = ""

        hideOtaProgressDialog()
        showOtaLoadingDialog(getString(R.string.ota_loading_text))
        bluetoothGatt?.disconnect()
        handler.postDelayed({ reconnect() }, 500)
    }

    private fun showErrorDialog(status: Int) {
        runOnUiThread {
            errorDialog = ErrorDialog(status, object : OtaErrorCallback {
                override fun onDismiss() {
                    bluetoothGatt?.disconnect() ?: finish()
                    errorDialog = null
                }
            })
            errorDialog?.show(supportFragmentManager, "ota_error_dialog")
        }
    }

    /**
     * ACTIVITY STATES MACHINE
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceServicesBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        bluetoothDevice = intent.getParcelableExtra(CONNECTED_DEVICE)
        devicesAdapterRecyclerViewItemPos = intent.getIntExtra(RECYCLERVIEW_POSITION,-1)
        setupBottomNavigation()
        setupActionBar()
        setupUiListeners()
        registerReceivers()
        handler = Handler(Looper.getMainLooper())

        showCharacteristicLoadingAnimation(getString(R.string.debug_mode_device_loading_gatt_info))
        bindBluetoothService()
        scanFragmentViewModel = ScanFragmentViewModel(this@DeviceServicesActivity)
    }

    private fun setupBottomNavigation() {
        supportFragmentManager.beginTransaction().apply {
            add(R.id.services_fragment_container, localServicesFragment)
            hide(localServicesFragment)
            add(R.id.services_fragment_container, remoteServicesFragment)
        }.commit()


        binding.servicesBottomNav.setOnNavigationItemSelectedListener { item ->
            (when (item.itemId) {
                R.id.services_nav_remote -> {
                    toggleRemoteActions(isRemoteFragmentOn = true)
                    supportActionBar?.title = getDeviceName()
                    remoteServicesFragment
                }

                R.id.services_nav_local -> {
                    toggleRemoteActions(isRemoteFragmentOn = false)
                    supportActionBar?.title = bluetoothService?.bluetoothAdapter?.name
                    localServicesFragment
                }

                else -> null
            })?.let { newFragment ->
                supportFragmentManager.beginTransaction().apply {
                    hide(activeFragment)
                    show(newFragment)
                }.commit()
                activeFragment = newFragment
                binding.servicesBottomNav.post { applyDeviceServicesBottomNavItemStyles() }
                true
            } ?: false
        }
        binding.servicesBottomNav.post { applyDeviceServicesBottomNavItemStyles() }
    }

    /**
     * BottomNavigationView shares one text appearance for all items. Remote (Client) and Local (Server)
     * each use Stolzl Medium and [R.color.device_services_remote_nav_text_color] (primary when selected).
     * Tab icon tints use the same color state list.
     */
    private fun applyDeviceServicesBottomNavItemStyles() {
        val font = ResourcesCompat.getFont(this, R.font.stolzl_medium) ?: return
        val labelColors = ContextCompat.getColorStateList(this, R.color.device_services_remote_nav_text_color)
            ?: return
        val menuView = binding.servicesBottomNav.getChildAt(0) as? ViewGroup ?: return

        fun applyNavItemStyle(itemView: View) {
            itemView.findViewById<ImageView>(R.id.navigation_bar_item_icon_view)?.let { icon ->
                ImageViewCompat.setImageTintList(icon, labelColors)
            }
            fun applyToTextViews(view: View) {
                when (view) {
                    is TextView -> {
                        view.typeface = font
                        view.setTextColor(labelColors)
                    }
                    is ViewGroup -> {
                        for (i in 0 until view.childCount) {
                            applyToTextViews(view.getChildAt(i))
                        }
                    }
                }
            }
            applyToTextViews(itemView)
        }

        listOf(R.id.services_nav_remote, R.id.services_nav_local).forEach { itemId ->
            val itemView = (0 until menuView.childCount)
                .map { menuView.getChildAt(it) }
                .firstOrNull { it.id == itemId }
            itemView?.let { applyNavItemStyle(it) }
        }
    }

    private fun toggleRemoteActions(isRemoteFragmentOn: Boolean) {
        val createBondMenu = menu?.findItem(R.id.menu_create_bond)

        createBondMenu?.isVisible = isRemoteFragmentOn

        binding.connectionInfo.visibility =
            if (isRemoteFragmentOn) View.VISIBLE
            else View.GONE
        toggleMenuItemsVisibility(isRemoteFragmentOn)
    }

    private fun toggleMenuItemsVisibility(areVisible: Boolean) {
        menu?.children?.forEach {
            it.isVisible = areVisible
        }
    }

    private fun setupActionBar() {
        AppUtil.setEdgeToEdge(window, this)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.popupTheme = androidx.appcompat.R.style.ThemeOverlay_AppCompat_Light
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = getDeviceName()
        }
    }

    private fun setupUiListeners() {
        binding.tvOtaFirmware.setOnClickListener {
            if (isUiCreated) checkForOtaCharacteristic()
        }
        binding.btnDisconnect.setOnClickListener {

            if(null != intent && intent.getStringExtra(ORIGIN).equals("BrowserFragment",true)){
                if(devicesAdapterRecyclerViewItemPos >= 0){
                    if(null != scanFragmentViewModel){
                        scanFragmentViewModel.setDeviceConnectionState(devicesAdapterRecyclerViewItemPos, connectionState = BluetoothDeviceInfo.ConnectionState.DISCONNECTED)
                    }
                    if(null != bluetoothDevice){
                        bluetoothService?.disconnectGatt(bluetoothDevice!!.address)
                    }
                }else{
                    println("Error: Invalid devicesAdapterRecyclerViewItemPos in setupUiListeners")
                    Log.e("Error: ", "Invalid devicesAdapterRecyclerViewItemPos in setupUiListeners")

                }
            }else{
                bluetoothService?.disconnectGatt(bluetoothDevice!!.address)
            }

        }
    }

    private fun checkForOtaCharacteristic() {
        if (getOtaControlCharacteristic() != null) showOtaConfigDialog()
        else OtaCharacteristicMissingDialog().show(
            supportFragmentManager,
            "ota_characteristic_missing_dialog"
        )
    }

    private fun registerReceivers() {

        registerReceiver(bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        registerReceiver(
            bondStateChangeListener,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        )

    }

    private fun displayBondState(newState: Int? = null) {

        val state = newState ?: bluetoothGatt?.device?.bondState ?: BluetoothDevice.BOND_NONE
        val createBondMenu = menu?.findItem(R.id.menu_create_bond)
        when (state) {
            BluetoothDevice.BOND_BONDED -> {

                binding.tvBondState.text = getString(R.string.bonded)

                createBondMenu?.isEnabled = true
                createBondMenu?.title = getString(R.string.delete_bond)

            }

            BluetoothDevice.BOND_BONDING -> {
                binding.tvBondState.text = getString(R.string.bonding)

                createBondMenu?.isEnabled = false
                createBondMenu?.title = getString(R.string.bonding)
            }

            BluetoothDevice.BOND_NONE -> {
                binding.tvBondState.text = getString(R.string.not_bonded)

                createBondMenu?.isEnabled = true
                createBondMenu?.title = getString(R.string.create_bond)

            }

            else -> {}
        }
    }

    private fun getOtaControlCharacteristic(): BluetoothGattCharacteristic? {
        return bluetoothGatt?.getService(UuidConsts.OTA_SERVICE)
            ?.getCharacteristic(UuidConsts.OTA_CONTROL)
    }

    private fun getDeviceNameCharacteristic(): BluetoothGattCharacteristic? {
        return bluetoothGatt?.getService(UuidConsts.GENERIC_ACCESS)
            ?.getCharacteristic(UuidConsts.DEVICE_NAME)
    }

    private fun setActivityResult() {
        setResult(REFRESH_INFO_RESULT_CODE, Intent().apply {
            putExtra(CONNECTED_DEVICE, bluetoothDevice)
            putExtra(
                CONNECTION_STATE,
                if (bluetoothService?.isGattConnected(bluetoothDevice?.address) == true) BluetoothGatt.STATE_CONNECTED
                else BluetoothGatt.STATE_DISCONNECTED
            )
        })
    }

    override fun onResume() {
        super.onResume()
        bluetoothService?.apply {
            registerGattCallback(gattCallback)
            if (!isGattConnected()) {
                showMessage(R.string.toast_debug_connection_failed)
                finish()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        bluetoothService?.unregisterGattCallback()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (!isFinishing && !isDestroyed) {
                runOnUiThread {
                    if (mtuRequestDialog?.isVisible == true && mtuRequestDialog?.isShowing() == true) {
                        mtuRequestDialog?.dismiss()
                    }
                    if(otaConfigDialog?.isVisible == true && otaConfigDialog?.isShowing() == true){
                        otaConfigDialog?.dismiss()
                    }
                }
                hideOtaProgressDialog()
                hideOtaLoadingDialog()
                runOnUiThread {
                    if(errorDialog?.isVisible == true && errorDialog?.isShowing() == true){
                        errorDialog?.dismiss()
                    }

                }
            }
            unregisterReceivers()
            bluetoothService?.isNotificationEnabled = true
            bluetoothBinding?.unbind()
        } catch (e: Exception) {
            if(e is IllegalStateException){
                Log.e("Devices Services Activity","Illegal State Exception")
            }else{
                Log.e("Devices Services Activity","Exception"+e.message)
            }
        }


    }

    override fun finish() {
        hideOtaLoadingDialog()
        hideOtaProgressDialog()
        setActivityResult()
        super.finish()
    }

    private fun unregisterReceivers() {
        unregisterReceiver(bluetoothReceiver)
        unregisterReceiver(bondStateChangeListener)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_device_services, menu)
        this.menu = menu
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        // Now it's safe to call displayBondState because the menu is ready
        displayBondState()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.show_logs -> showLogFragment()
            R.id.request_priority -> {
                ConnectionRequestDialog(connectionPriority, connectionRequestCallback)
                    .show(supportFragmentManager, CONNECTION_REQUEST_DIALOG_FRAGMENT)
            }

            R.id.request_mtu -> {
                mtuRequestDialog = MtuRequestDialog(MTU, mtuRequestCallback).also {
                    it.show(supportFragmentManager, MTU_REQUEST_DIALOG_FRAGMENT)
                }
            }

            R.id.menu_create_bond -> {
                bluetoothGatt?.device?.let {
                    when (it.bondState) {
                        BluetoothDevice.BOND_BONDED -> askUnbondDevice(it)
                        BluetoothDevice.BOND_NONE -> it.createBond()
                        else -> {}
                    }
                }
            }

            android.R.id.home -> {
                onBackPressed()
                return true
            }

            else -> {}
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        super.onBackPressed()

        if (isLogFragmentOn) {

            binding.fragmentContainer.visibility = View.GONE
            binding.servicesContainer.visibility = View.VISIBLE
            isLogFragmentOn = false
            supportActionBar?.title = bluetoothDevice?.name
            toggleMenuItemsVisibility(areVisible = true)
        }
    }

    private val connectionRequestCallback = object : ConnectionRequestDialog.Callback {
        override fun onConnectionPriorityRequested(priority: Int) {
            bluetoothGatt?.requestConnectionPriority(priority)
            connectionPriority = priority
            showMessage(
                getString(
                    when (priority) {
                        BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER -> R.string.connection_priority_low
                        BluetoothGatt.CONNECTION_PRIORITY_BALANCED -> R.string.connection_priority_balanced
                        BluetoothGatt.CONNECTION_PRIORITY_HIGH -> R.string.connection_priority_high
                        else -> R.string.connection_priority_low
                    }
                )
            )
        }
    }

    private val mtuRequestCallback = object : MtuRequestDialog.Callback {
        override fun onMtuRequested(requestedMtu: Int) {
            mtuReadType = MtuReadType.USER_REQUESTED
            bluetoothGatt?.requestMtu(requestedMtu)
        }
    }

    private fun showLogFragment() {
        supportFragmentManager.beginTransaction().apply {
            add(R.id.fragment_container, LogFragment())
            addToBackStack(null)
        }.commit()

        binding.fragmentContainer.visibility = View.VISIBLE
        binding.servicesContainer
        binding.servicesContainer.visibility = View.GONE
        toggleMenuItemsVisibility(areVisible = false)
        isLogFragmentOn = true
    }

    private fun askUnbondDevice(device: BluetoothDevice) {
        if (SharedPrefUtils(this@DeviceServicesActivity).shouldDisplayUnbondDeviceDialog()) {
            val dialog = UnbondDeviceDialog(object : UnbondDeviceDialog.Callback {
                override fun onOkClicked() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                        unbondDevice(device)
                    } else {
                        removeBond(device)
                    }
                }
            })
            dialog.show(supportFragmentManager, "dialog_unbond_device")
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                unbondDevice(device)
            } else {
                removeBond(device)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun unbondDevice(device: BluetoothDevice) {
        // Try to use CompanionDeviceManager-based bond removal if available and associated
        if (bluetoothService != null) {
            val associationId = bluetoothService?.getAssociationIdForDevice(device.address)
            if (associationId != null) {
                bluetoothService?.removeBluetoothBond(associationId)
                return
            }
        }
        // Fallback to reflection-based removeBond
        if (!removeBond(device)) {
            if (SharedPrefUtils(this@DeviceServicesActivity).shouldDisplayManualUnbondDeviceDialog()) {
                val dialog = ManualUnbondDeviceDialog(object : ManualUnbondDeviceDialog.Callback {
                    override fun onOkClicked() {
                        try {
                            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        } catch (e: ActivityNotFoundException) {
                        }
                    }
                })
                dialog.show(supportFragmentManager, "dialog_unbond_device")
            } else {
                try {
                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                } catch (e: ActivityNotFoundException) {
                }
            }
        }
    }

    private fun removeBond(device: BluetoothDevice): Boolean {
        return try {
            return device::class.java.getMethod("removeBond").invoke(device) as Boolean
        } catch (e: Exception) {
            false
        }
    }

    fun refreshServices() {
        bluetoothGatt?.let {
            remoteServicesFragment.clear()
            localServicesFragment.clear()
            showCharacteristicLoadingAnimation(getString(R.string.debug_mode_device_refreshing_services))

            if (refreshDeviceCache()) {
                handler.postDelayed({
                    viewState = ViewState.REFRESHING_SERVICES
                    it.discoverServices()
                }, CACHE_REFRESH_DELAY)
            } else {
                hideCharacteristicLoadingAnimation()
                showMessage(getString(R.string.refreshing_not_possible))
            }
        }
    }

    private val otaConfigCallback = object : OtaConfigDialog.Callback {
        override fun onOtaPartialFullChanged(doubleStepUpload: Boolean) {
            this@DeviceServicesActivity.doubleStepUpload = doubleStepUpload
        }

        override fun onFileChooserClicked(type: OtaFileType) {
            currentOtaFileType = type
            sendFileChooserIntent()
        }

        override fun onOtaClicked(isReliableMode: Boolean) {
            /* OTA process after clicking button:
            * 0. Request MTU before writing to OTA_CONTROL characteristic (done during view initialization)
            * 1. Write 0x00 to OTA_CONTROL characteristic
            * 2. Device will disconnect on its own (with status 19)
            * 3. Reconnect with device (it will have OTA_DATA characteristic this time)
            * 4. REQUEST MTU AGAIN! (apparently it's super important)
            * 5. Write 0x00 to OTA_CONTROL again
            * 6. Start writing to OTA_DATA characteristic
            * 7. Write 0x03 to OTA_CONTROL to end upload    */

            if (viewState == ViewState.IDLE) {
                otaConfigDialog = null
                reliable = isReliableMode

                if (otaDataCharPresent) {
                    viewState = ViewState.INITIALIZING_UPLOAD
                    mtuReadType = MtuReadType.UPLOAD_INITIALIZATION
                    bluetoothGatt?.requestMtu(INITIALIZATION_MTU_VALUE)
                } else {
                    viewState = ViewState.REBOOTING
                    writeOtaControl(OTA_CONTROL_START_COMMAND)
                }
            }
        }

        override fun onDialogCancelled() {
            otaConfigDialog = null
        }
    }

    private fun sendFileChooserIntent() {
        Intent(Intent.ACTION_GET_CONTENT)
            .apply { type = "*/*" }
            .also {
                startActivityForResult(
                    Intent.createChooser(
                        it,
                        getString(R.string.ota_choose_file)
                    ), FILE_CHOOSER_REQUEST_CODE
                )
            }
    }

    private fun hideOtaProgressDialog() {
        runOnUiThread {
            if(otaProgressDialog?.isVisible == true && otaProgressDialog?.isShowing() == true){
                otaProgressDialog?.dismiss()
                otaProgressDialog = null
            }
        }
    }

    private fun showOtaProgressDialog(info: OtaProgressDialog.OtaInfo) {
        runOnUiThread {
            otaProgressDialog = OtaProgressDialog(otaProgressCallback, info).also {
                it.show(supportFragmentManager, OTA_PROGRESS_DIALOG_FRAGMENT)
            }
        }
    }

    private val otaProgressCallback = object : OtaProgressDialog.Callback {
        override fun onEndButtonClicked() {
            hideOtaProgressDialog()
            showMessage(getString(R.string.ota_uploading_successful))

            remoteServicesFragment.clear()
            localServicesFragment.clear()
            showCharacteristicLoadingAnimation(getString(R.string.debug_mode_device_rebooting_firmware))

            viewState = ViewState.REBOOTING_NEW_FIRMWARE
            bluetoothGatt?.disconnect()
            reconnect(requestRssiUpdates = true)
        }
    }

    private fun showOtaConfigDialog() {
        otaConfigDialog = OtaConfigDialog(otaConfigCallback, doubleStepUpload).also {
            it.show(supportFragmentManager, OTA_CONFIG_DIALOG_FRAGMENT)
        }
    }

    private fun hideOtaLoadingDialog() {
        runOnUiThread {
            if (otaLoadingDialog?.isShowing() == true && otaLoadingDialog?.isVisible == true){
                otaLoadingDialog?.dismiss()
                otaLoadingDialog = null
            }
        }
    }

    private fun showOtaLoadingDialog(message: String, header: String? = null) {
        if (otaLoadingDialog == null) {
            runOnUiThread {
                otaLoadingDialog = OtaLoadingDialog(message, header).also {
                    it.show(supportFragmentManager, OTA_LOADING_DIALOG_FRAGMENT)
                }
            }
        }
    }

    private fun initServicesFragments(services: List<BluetoothGattService>) {
        remoteServicesFragment.init(services)
        localServicesFragment.init(services)
        hideCharacteristicLoadingAnimation()
    }

    private fun printServicesInfo(gatt: BluetoothGatt) {
        Timber.i("onServicesDiscovered(): services count = ${gatt.services.size}")
        gatt.services.forEach { service ->
            Timber.i("onServicesDiscovered(): service UUID = ${service.uuid}, char count = ${service.characteristics.size}")
            service.characteristics.forEach {
                Timber.i("onServicesDiscovered(): characteristic UUID = ${it.uuid}, properties = ${it.properties}")
            }
        }
    }

    private fun writeOtaControl(ctrl: Byte) {
        if (ctrl == OTA_CONTROL_START_COMMAND) {
            bluetoothService?.isNotificationEnabled = false
        }

        val controlDelay = when (ctrl) {
            OTA_CONTROL_START_COMMAND -> OTA_CONTROL_START_DELAY
            OTA_CONTROL_END_COMMAND -> OTA_CONTROL_END_DELAY
            else -> 0
        }.toLong()

        getOtaControlCharacteristic()?.apply {
            writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            value = byteArrayOf(ctrl)
        }.also {
            handler.postDelayed({
                Timber.d("writeOtaControl(): ctrl = $ctrl")
                bluetoothGatt?.writeCharacteristic(it)
            }, controlDelay)
        }
    }

    @Synchronized
    fun writeOtaData(datathread: ByteArray?) {
        try {
            val value = ByteArray(MTU - 3)
            val start = System.nanoTime()
            var j = 0
            for (i in datathread?.indices!!) {
                value[j] = datathread[i]
                j++
                if (j >= MTU - 3 || i >= (datathread.size - 1)) {
                    var wait = System.nanoTime()
                    val charac =
                        bluetoothGatt?.getService(UuidConsts.OTA_SERVICE)?.getCharacteristic(
                            UuidConsts.OTA_DATA
                        )
                    charac?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    val progress = ((i + 1).toFloat() / datathread.size) * 100
                    val bitrate =
                        (((i + 1) * (8.0)).toFloat() / (((wait - start) / 1000000.0).toFloat()))
                    if (j < MTU - 3) {
                        val end = ByteArray(j)
                        System.arraycopy(value, 0, end, 0, j)
                        Log.d(
                            "Progress",
                            "sent " + (i + 1) + " / " + datathread.size + " - " + String.format(
                                "%.1f",
                                progress
                            ) + " % - " + String.format(
                                "%.2fkbit/s",
                                bitrate
                            ) + " - " + Converters.bytesToHexWhitespaceDelimited(end)
                        )
                        runOnUiThread {
                            otaProgressDialog?.updateDataProgress(progress.toInt())
                        }
                        charac?.value = end
                    } else {
                        j = 0
                        Log.d(
                            "Progress",
                            "sent " + (i + 1) + " / " + datathread.size + " - " + String.format(
                                "%.1f",
                                progress
                            ) + " % - " + String.format(
                                "%.2fkbit/s",
                                bitrate
                            ) + " - " + Converters.bytesToHexWhitespaceDelimited(value)
                        )
                        runOnUiThread {
                            otaProgressDialog?.updateDataProgress(progress.toInt())
                        }
                        charac?.value = value
                    }
                    if (bluetoothGatt?.writeCharacteristic(charac)!!) {
                        runOnUiThread { otaProgressDialog?.updateDataRate(bitrate) }
                        while ((System.nanoTime() - wait) / 1000000.0 < delayNoResponse);
                    } else {
                        do {
                            while ((System.nanoTime() - wait) / 1000000.0 < delayNoResponse);
                            wait = System.nanoTime()
                            runOnUiThread {
                                runOnUiThread { otaProgressDialog?.updateDataRate(bitrate) }
                            }
                        } while (!bluetoothGatt?.writeCharacteristic(charac)!!)
                    }
                }
            }
            val end = System.nanoTime()
            val time = (end - start) / 1_000_000.toFloat()
            Log.d("OTA Time - ", "" + time + "s")
            runOnUiThread {
                otaProgressDialog?.stopUploading()
            }
            writeOtaControl(OTA_CONTROL_END_COMMAND)
        } catch (e: NullPointerException) {
            e.printStackTrace()
        }
    }

    /**
     * WRITES EBL/GBL FILES TO OTA_DATA CHARACTERISTIC
     */
    @Synchronized
    fun otaWriteDataReliable() {
        val writearray: ByteArray
        val pgss: Float
        if (pack + mtuDivisible > otafile?.size!! - 1) {
            /**SET last by 4 */
            var plus = 0
            var last = otafile?.size!! - pack
            do {
                last += plus
                plus++
            } while (last % 4 != 0)
            writearray = ByteArray(last)
            for ((j, i) in (pack until pack + last).withIndex()) {
                if (otafile?.size!! - 1 < i) {
                    writearray[j] = 0xFF.toByte()
                } else writearray[j] = otafile!![i]
            }
            pgss = ((pack + last).toFloat() / (otafile?.size!! - 1)) * 100
            Log.d(
                "characte",
                "last: " + pack + " / " + (pack + last) + " : " + Converters.bytesToHexWhitespaceDelimited(
                    writearray
                )
            )
        } else {
            var j = 0
            writearray = ByteArray(mtuDivisible)
            for (i in pack until pack + mtuDivisible) {
                writearray[j] = otafile!![i]
                j++
            }
            pgss = ((pack + mtuDivisible).toFloat() / (otafile?.size!! - 1)) * 100
            Log.d(
                "characte",
                "pack: " + pack + " / " + (pack + mtuDivisible) + " : " + Converters.bytesToHexWhitespaceDelimited(
                    writearray
                )
            )
        }
        val charac = bluetoothGatt?.getService(UuidConsts.OTA_SERVICE)
            ?.getCharacteristic(UuidConsts.OTA_DATA)
        charac?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        charac?.value = writearray
        bluetoothGatt?.writeCharacteristic(charac)
        val waiting_time = (System.currentTimeMillis() - otatime)
        val bitrate = 8 * pack.toFloat() / waiting_time
        if (pack > 0) {
            handler.post {
                runOnUiThread {
                    otaProgressDialog?.let {
                        it.updateDataRate(bitrate)
                        it.updateDataProgress(pgss.toInt())
                    }
                }
            }
        } else {
            otatime = System.currentTimeMillis()
        }
    }

    private fun readChosenFile(): ByteArray? {
        return try {
            val file: File
            if (stackPath != "" && doubleStepUpload) {
                file = File(stackPath)
                boolFullOTA = true
            } else {
                file = File(appPath)
                boolFullOTA = false
            }
            val fileInputStream = FileInputStream(file)
            val size = fileInputStream.available()
            val temp = ByteArray(size)
            fileInputStream.read(temp)
            fileInputStream.close()
            temp
        } catch (e: Exception) {
            Log.e("InputStream", "Couldn't open file$e")
            null
        }
    }

    private fun prepareFilename(): String {
        return if (stackPath != "" && doubleStepUpload) {
            val last = stackPath.lastIndexOf(File.separator)
            getString(R.string.ota_filename_s, stackPath.substring(last).removePrefix("/"))
        } else {
            val last = appPath.lastIndexOf(File.separator)
            getString(R.string.ota_filename_s, appPath.substring(last).removePrefix("/"))
        }
    }

    private fun refreshDeviceCache(): Boolean {
        return try {
            bluetoothGatt?.javaClass?.getMethod("refresh")?.let {
                val success: Boolean = (it.invoke(bluetoothGatt, *arrayOfNulls(0)) as Boolean)
                Timber.d("refreshDeviceCache(): success: $success")
                success
            } ?: false
        } catch (localException: Exception) {
            Timber.e("refreshDeviceCache(): an exception occurred while refreshing device")
            false
        }
    }

    private fun reconnect(requestRssiUpdates: Boolean = false) {
        bluetoothDevice = bluetoothGatt?.device

        handler.postDelayed({
            runOnUiThread { otaLoadingDialog?.updateMessage(getString(R.string.attempting_connection)) }
            bluetoothDevice?.let { device ->
                bluetoothService?.connectGatt(device, requestRssiUpdates, gattCallback)
            }
        }, RECONNECTION_DELAY)
    }

    private fun showCharacteristicLoadingAnimation(barLabel: String) {
        runOnUiThread {
            binding.btnDisconnect.visibility = View.GONE
            binding.tvBondStateWithRssi.visibility = View.GONE
            binding.flyInBar.apply {
                setOnClickListener { /* this onclicklistener prevents services and characteristics from user interaction before ui is loaded*/ }
                visibility = View.VISIBLE
                startFlyInAnimation(barLabel)
            }
        }
    }

    private fun hideCharacteristicLoadingAnimation() {
        runOnUiThread {

            binding.flyInBar.startFlyOutAnimation(object : FlyInBar.Callback {
                override fun onFlyOutAnimationEnded() {
                    binding.flyInBar.visibility = View.GONE
                    binding.btnDisconnect.visibility = View.VISIBLE
                    binding.tvBondStateWithRssi.visibility = View.VISIBLE
                }
            })
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == WRITE_EXTERNAL_STORAGE_REQUEST_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showMessage(resources.getString(R.string.permissions_granted_successfully))
                checkForOtaCharacteristic()
            } else {
                showMessage(R.string.permissions_not_granted)
            }
        }
    }

    private fun bindBluetoothService() {
        bluetoothBinding = object : BluetoothService.Binding(this) {
            override fun onBound(service: BluetoothService?) {
                this@DeviceServicesActivity.bluetoothService = service

                bluetoothService?.apply {
                    bluetoothDevice?.address?.let {
                        bluetoothGatt = getActiveConnection(it)?.connection?.gatt
                    }
                    bluetoothGatt?.let {
                        registerGattCallback(true, gattCallback)
                        displayBondState()
                        it.discoverServices()
                    } ?: run {
                        showMessage(R.string.toast_debug_connection_failed)
                        finish()
                    }
                }
            }
        }
        bluetoothBinding?.bind()
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                FILE_CHOOSER_REQUEST_CODE -> {
                    val uri = data?.data

                    val filename = try {
                        getFileName(uri)
                    } catch (e: Exception) {
                        ""
                    }

                    filename?.let {
                        if (!hasOtaFileCorrectExtension(filename)) showMessage(getString(R.string.incorrect_file))
                        else prepareOtaFile(uri, currentOtaFileType, it)
                    } ?: showMessage(getString(R.string.incorrect_file))
                }
            }
        }
    }

    @SuppressLint("Range")
    private fun getFileName(uri: Uri?): String? {
        var result: String? = null
        if ((uri?.scheme == "content")) {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor.use { c ->
                if (c != null && c.moveToFirst()) {
                    result = c.getString(c.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        if (result == null) {
            result = uri?.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result
    }

    private fun hasOtaFileCorrectExtension(filename: String?): Boolean {
        return filename?.uppercase(Locale.getDefault())?.contains(".GBL")!!
    }

    private fun getDeviceName(): String {
        return bluetoothDevice?.let {
            if (TextUtils.isEmpty(it.name)) getString(R.string.not_advertising_shortcut)
            else it.name
        } ?: getString(R.string.not_advertising_shortcut)
    }

    private fun prepareOtaFile(uri: Uri?, type: OtaFileType, filename: String) {
        try {
            val inStream = contentResolver.openInputStream(uri!!)
            if (inStream == null) {
                showMessage(resources.getString(R.string.problem_while_preparing_the_file))
                return
            }
            val file = File(cacheDir, filename)
            val output: OutputStream = FileOutputStream(file)
            val buffer = ByteArray(4 * 1024)
            var read: Int
            while ((inStream.read(buffer).also { read = it }) != -1) {
                output.write(buffer, 0, read)
            }
            if ((type == OtaFileType.APPLICATION)) appPath = file.absolutePath
            else stackPath = file.absolutePath

            otaConfigDialog?.changeFilename(type, filename)

            output.flush()
        } catch (e: IOException) {
            e.printStackTrace()
            showMessage(resources.getString(R.string.incorrect_file))
        }
    }

    enum class ViewState {
        IDLE,
        REFRESHING_SERVICES,
        REBOOTING, //rebooting to bootloader or rebooting for second stage upload in full OTA scenario
        INITIALIZING_UPLOAD, //setting MTU and connection priority
        UPLOADING,
        REBOOTING_NEW_FIRMWARE
    }

    enum class MtuReadType {
        VIEW_INITIALIZATION,
        UPLOAD_INITIALIZATION,
        USER_REQUESTED
    }

    companion object {
        private const val GATT_FETCH_ON_SERVICE_DISCOVERED_DELAY = 875L
        private const val WRITE_EXTERNAL_STORAGE_REQUEST_PERMISSION = 300
        private const val FILE_CHOOSER_REQUEST_CODE = 9999

        private const val DEFAULT_MTU_VALUE = 200
        private const val INITIALIZATION_MTU_VALUE = 247

        private const val OTA_CONTROL_START_COMMAND: Byte = 0x00
        private const val OTA_CONTROL_END_COMMAND: Byte = 0x03
        private const val RECONNECTION_DELAY = 4000L // device needs to reboot
        private const val OTA_CONTROL_START_DELAY = 200L // needed to avoid error status 135
        private const val OTA_CONTROL_END_DELAY = 500L
        private const val CACHE_REFRESH_DELAY =
            500L // no callback available, so give some time to refresh
        private const val DIALOG_DELAY = 500L // give time for progress dialog to bind layout

        private const val CONNECTION_REQUEST_DIALOG_FRAGMENT = "connection_request_dialog_fragment"
        private const val MTU_REQUEST_DIALOG_FRAGMENT = "mtu_request_dialog_fragment"
        private const val OTA_CONFIG_DIALOG_FRAGMENT = "ota_config_dialog_fragment"
        private const val OTA_PROGRESS_DIALOG_FRAGMENT = "ota_progress_dialog_fragment"
        private const val OTA_LOADING_DIALOG_FRAGMENT = "ota_loading_dialog_fragment"

        const val CONNECTED_DEVICE = "connected_device"
        const val CONNECTION_STATE = "connection_state"
        const val REFRESH_INFO_RESULT_CODE = 279

        fun startActivity(context: Context, device: BluetoothDevice) {
            Intent(context, DeviceServicesActivity::class.java).apply {
                putExtra(CONNECTED_DEVICE, device)
                putExtra(ORIGIN,"ConnectionsFragment")
            }.also {
                startActivity(context, it, null)
            }
        }
    }
}
