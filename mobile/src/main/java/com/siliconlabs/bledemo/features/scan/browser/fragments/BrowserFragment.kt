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
package com.siliconlabs.bledemo.features.scan.browser.fragments

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.siliconlabs.bledemo.bluetooth.services.BluetoothService
import com.siliconlabs.bledemo.bluetooth.ble.BluetoothDeviceInfo
import com.siliconlabs.bledemo.bluetooth.ble.ErrorCodes.getDeviceDisconnectedMessage
import com.siliconlabs.bledemo.bluetooth.ble.ErrorCodes.getFailedConnectingToDeviceMessage
import com.siliconlabs.bledemo.bluetooth.ble.TimeoutGattCallback
import com.siliconlabs.bledemo.features.scan.browser.adapters.DebugModeDeviceAdapter
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.utils.SharedPrefUtils
import com.siliconlabs.bledemo.features.scan.browser.view_states.ScannerFragmentViewState
import com.siliconlabs.bledemo.databinding.FragmentBrowserBinding
import com.siliconlabs.bledemo.home_screen.activities.MainActivity
import com.siliconlabs.bledemo.common.other.CardViewListDecoration
import com.siliconlabs.bledemo.home_screen.base.BaseServiceDependentMainMenuFragment
import com.siliconlabs.bledemo.home_screen.fragments.ScanFragment
import com.siliconlabs.bledemo.home_screen.base.ViewPagerFragment
import com.siliconlabs.bledemo.features.scan.browser.activities.DeviceServicesActivity
import com.siliconlabs.bledemo.features.scan.browser.activities.UuidDictionaryActivity
import com.siliconlabs.bledemo.home_screen.viewmodels.ScanFragmentViewModel
import com.siliconlabs.bledemo.features.scan.browser.adapters.DebugModeCallback
import com.siliconlabs.bledemo.home_screen.base.BluetoothDependent
import com.siliconlabs.bledemo.home_screen.base.LocationDependent
import com.siliconlabs.bledemo.utils.BLEUtils
import com.siliconlabs.bledemo.utils.CustomToastManager
import timber.log.Timber


class BrowserFragment : BaseServiceDependentMainMenuFragment(),
        OnRefreshListener {

    private lateinit var viewModel: ScanFragmentViewModel
    private lateinit var viewBinding: FragmentBrowserBinding
    private var bluetoothService: BluetoothService? = null

    private lateinit var sharedPrefUtils: SharedPrefUtils
    private var devicesAdapter: DebugModeDeviceAdapter? = null
    private lateinit var handler: Handler

    private var deviceToConnect: BluetoothDeviceInfo? = null
    private var blockConnectionAttempts = false

    override val bluetoothDependent = object : BluetoothDependent {

        override fun onBluetoothStateChanged(isBluetoothOn: Boolean) {
            toggleBluetoothBar(isBluetoothOn, viewBinding.bluetoothBar)
            viewBinding.btnScanning.isEnabled = isBluetoothOperationPossible()

            getScanFragment().setScanFragmentListener(scanFragmentListener)
            viewModel.let {
                it.setIsScanningOn(isBluetoothOperationPossible())
                if (!isBluetoothOn) {
                    it.reset()
                    it.shouldResetChart = true
                }
            }
        }

        override fun onBluetoothPermissionsStateChanged(arePermissionsGranted: Boolean) {
            toggleBluetoothPermissionsBar(arePermissionsGranted, viewBinding.bluetoothPermissionsBar)
            viewBinding.btnScanning.isEnabled = isBluetoothOperationPossible()

            viewModel.let {
                it.setIsScanningOn(isBluetoothOperationPossible())
                if (!arePermissionsGranted) {
                    it.reset()
                    it.shouldResetChart = true
                }
            }
        }

        override fun refreshBluetoothDependentUi(isBluetoothOperationPossible: Boolean) {
            /* Not needed */
        }

        override fun setupBluetoothPermissionsBarButtons() {
            viewBinding.bluetoothPermissionsBar.setFragmentManager(childFragmentManager)
        }
    }

    override val locationDependent = object : LocationDependent {

        override fun onLocationStateChanged(isLocationOn: Boolean) {
            toggleLocationBar(isLocationOn, viewBinding.locationBar)
        }
        override fun onLocationPermissionStateChanged(isPermissionGranted: Boolean) {
            viewBinding.apply {
                toggleLocationPermissionBar(isPermissionGranted, locationPermissionBar)
                btnScanning.isEnabled = isPermissionGranted
            }
        }
        override fun setupLocationBarButtons() {
            viewBinding.locationBar.setFragmentManager(childFragmentManager)
        }

        override fun setupLocationPermissionBarButtons() {
            viewBinding.locationPermissionBar.setFragmentManager(childFragmentManager)
        }
    }


    private val scanFragmentListener = object: ScanFragment.ScanFragmentListener{
        override fun onScanningStateChanged(isOn: Boolean) {
            if (!isAdded) return
            toggleMainView(isOn, viewModel.isAnyDeviceDiscovered.value ?: false)
            toggleScanningButton(isOn)
            toggleRefreshInfoRunnable(isOn)

            if (!isOn) handler.removeCallbacks(refreshScanRunnable)
        }
    }


    private val refreshScanRunnable = Runnable {
        viewModel.let {
            if (!it.getIsScanningOn()) {
                it.setIsScanningOn(true)
            } else {
                getScanFragment().toggleScannerState(true)
            }
        }
    }

    private fun getScanFragment() = (parentFragment as ViewPagerFragment).getScanFragment()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        viewModel = getScanFragment().viewModel
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        setHasOptionsMenu(true)
        viewBinding = FragmentBrowserBinding.inflate(inflater)
        hidableActionButton = viewBinding.btnScanning
        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.title = getString(R.string.fragment_scan_label)
        sharedPrefUtils = SharedPrefUtils(requireContext())
        handler = Handler(Looper.getMainLooper())

        observeChanges()
        setUiListeners()

        initFullPageInfoViews()
        initDevicesRecyclerView()
        initSwipeRefreshLayout()

        getScanFragment().setScanFragmentListener(scanFragmentListener) // for initial app enter

        bluetoothService?.apply {
            registerGattServerCallback(gattServerCallback)
            registerGattCallback(gattCallback)
        }
    }


    private fun initFullPageInfoViews() {
        viewBinding.lookingForDevicesBackground.apply {
            image.apply {
                setImageResource(R.drawable.redesign_ic_main_view_browser_scanning_spinner)
                (drawable as? AnimatedVectorDrawable)?.start()
            }
            textPrimary.text = getString(R.string.device_scanning_background_message)
            textSecondary.visibility = View.GONE
        }
    }

    private fun observeChanges() {
        viewModel.apply {
            filteredDevices.observe(viewLifecycleOwner) {
                devicesAdapter?.updateDevices(getBluetoothInfoViewsState())
            }
            isAnyDeviceDiscovered.observe(viewLifecycleOwner) {
                toggleMainView(getIsScanningOn(), it)
            }
            deviceToInsert.observe(viewLifecycleOwner) {
                devicesAdapter?.addNewDevice(it)
            }
            activeFiltersDescription.observe(viewLifecycleOwner) {
                toggleFilterDescriptionView(it)
            }
            activityViewModel?.isSetupFinished?.observe(viewLifecycleOwner) {
                if (it) {
                    bluetoothService = (activity as? MainActivity)?.bluetoothService
                    (parentFragment as ViewPagerFragment).setBluetoothService(bluetoothService)
                    viewBinding.btnScanning.visibility = View.VISIBLE
                    refreshViewState(viewModel.getScannerFragmentViewState())
                }
            }
        }
    }

    private fun toggleMainView(isScanningOn: Boolean, isAnyDeviceDiscovered: Boolean) {
        try {
            viewBinding.apply {
                if (isAnyDeviceDiscovered) {
                    rvDebugDevices.visibility = View.VISIBLE
                    lookingForDevicesBackground.root.visibility = View.GONE
                } else {
                    rvDebugDevices.visibility = View.GONE
                    lookingForDevicesBackground.apply {
                        root.visibility = View.VISIBLE
                        if (isScanningOn) {
                            image.setImageResource(R.drawable.redesign_ic_main_view_browser_scanning_spinner)
                            (image.drawable as AnimatedVectorDrawable).start()
                            textPrimary.text = getString(R.string.device_scanning_background_message)
                        } else {
                            image.setImageResource(R.drawable.graphic_loading)
                            textPrimary.text = getString(R.string.no_devices_found_title_copy)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e("BrowserFragment${e.message}")
        }
    }

    private fun toggleFilterDescriptionView(description: String?) {
        viewBinding.activeFiltersDescription.apply {
            if (description.isNullOrEmpty()) {
                visibility = View.GONE
            } else {
                visibility = View.VISIBLE
                text = description
            }
        }
    }

    private fun toggleRefreshInfoRunnable(isOn: Boolean) {
        handler.let {
            if (isOn) {
                it.removeCallbacks(updateScanInfoRunnable)
                it.postDelayed(updateScanInfoRunnable, SCAN_UPDATE_PERIOD)
            }
            else it.removeCallbacks(updateScanInfoRunnable)
        }
    }

    private val updateScanInfoRunnable = object : Runnable {
        override fun run() {
            devicesAdapter?.updateDevices(viewModel.getBluetoothInfoViewsState())
            handler.postDelayed(this, SCAN_UPDATE_PERIOD)
        }
    }

    private fun setUiListeners() {
        viewBinding.btnScanning.setOnClickListener { viewModel.toggleScanningState() }
    }

    override fun onResume() {
        super.onResume()
        if(BLEUtils.IS_FIRMWARE_REBOOTED == true){
            Log.d("BLE_DEBUG","refreshing rebooted firmware name")
            bluetoothService?.clearConnectedGatt()
            viewBinding.swipeRefreshContainer.isRefreshing = true
            Handler(Looper.getMainLooper()).postDelayed({
                viewBinding.rvDebugDevices.adapter!!.notifyDataSetChanged()
                viewBinding.swipeRefreshContainer.isRefreshing = false
            },2000)
            Handler(Looper.getMainLooper()).postDelayed({
                triggerSortMenuClick()
            },500)
            Handler(Looper.getMainLooper()).postDelayed({
                triggerSortMenuClick()
            },500)
            Handler(Looper.getMainLooper()).postDelayed({
                triggerSortMenuClick()
            },500)
        }
        bluetoothService?.apply {
            registerGattServerCallback(gattServerCallback)
            registerGattCallback(gattCallback)
        }

        if (activityViewModel?.getIsSetupFinished() == true) {
            viewModel.updateConnectionStates()
            getScanFragment().setScanFragmentListener(scanFragmentListener)
            refreshViewState(viewModel.getScannerFragmentViewState())
        }
    }

    private fun triggerSortMenuClick(){
        onOptionsItemSelected(MenuItemStub(R.id.menu_sort))
    }

    class MenuItemStub(private val id: Int) : MenuItem {

        // For simplicity, we'll use 0 as the default group ID
        private val groupId: Int = 0

        override fun getItemId(): Int = id
        override fun getGroupId(): Int = groupId
        override fun getOrder(): Int = 0 // Default order

        override fun setEnabled(enabled: Boolean): MenuItem = this
        override fun isEnabled(): Boolean = true
        override fun setVisible(visible: Boolean): MenuItem = this
        override fun isVisible(): Boolean = true
        override fun setChecked(checked: Boolean): MenuItem = this
        override fun isChecked(): Boolean = false
        override fun setTitle(title: CharSequence?): MenuItem = this
        override fun setTitle(title: Int): MenuItem = this
        override fun getTitle(): CharSequence = "Stub Title" // More descriptive
        override fun setTitleCondensed(title: CharSequence?): MenuItem = this
        override fun getTitleCondensed(): CharSequence = "Stub Title Condensed" // More descriptive

        override fun setIcon(icon: Drawable?): MenuItem = this
        override fun setIcon(iconRes: Int): MenuItem = this
        override fun getIcon(): Drawable? = null
        override fun setIntent(intent: Intent?): MenuItem = this
        override fun getIntent(): Intent? = null
        override fun setShortcut(numericChar: Char, alphaChar: Char): MenuItem = this
        override fun setNumericShortcut(numericChar: Char): MenuItem = this
        override fun getNumericShortcut(): Char = '\u0000' // Null character
        override fun setAlphabeticShortcut(alphaChar: Char): MenuItem = this
        override fun getAlphabeticShortcut(): Char = '\u0000' // Null character
        override fun setCheckable(checkable: Boolean): MenuItem = this
        override fun isCheckable(): Boolean = false
        override fun setOnMenuItemClickListener(listener: MenuItem.OnMenuItemClickListener?): MenuItem = this
        override fun getSubMenu(): SubMenu? = null
        override fun hasSubMenu(): Boolean = false
        override fun setActionView(view: View?): MenuItem = this
        override fun setActionView(resId: Int): MenuItem = this
        override fun getActionView(): View? = null
        override fun setShowAsAction(actionEnum: Int) {}
        override fun setShowAsActionFlags(actionEnum: Int): MenuItem = this
        override fun expandActionView(): Boolean = false
        override fun collapseActionView(): Boolean = false
        override fun isActionViewExpanded(): Boolean = false
        override fun setOnActionExpandListener(listener: MenuItem.OnActionExpandListener?): MenuItem = this

        override fun setActionProvider(actionProvider: ActionProvider?): MenuItem = this
        override fun getActionProvider(): ActionProvider? = null
        override fun setContentDescription(contentDescription: CharSequence?): MenuItem = this
        override fun getContentDescription(): CharSequence = ""
        override fun setTooltipText(tooltipText: CharSequence?): MenuItem = this
        override fun getTooltipText(): CharSequence = ""
        override fun setIconTintList(tint: android.content.res.ColorStateList?): MenuItem = this // Returns this.
        override fun getIconTintList(): android.content.res.ColorStateList? = null
        override fun setIconTintMode(tintMode: android.graphics.PorterDuff.Mode?): MenuItem = this // Returns this.
        override fun getIconTintMode(): android.graphics.PorterDuff.Mode? = null
        override fun getMenuInfo(): ContextMenu.ContextMenuInfo? = null
    }

    private fun refreshViewState(viewState: ScannerFragmentViewState) {
        toggleMainView(viewState.isScanningOn, viewState.devicesToShow.isNotEmpty())
        toggleScanningButton(viewState.isScanningOn)
        devicesAdapter?.updateDevices(viewState.devicesToShow)
    }

    private fun toggleScanningButton(isScanningOn: Boolean) {
        viewBinding.btnScanning.apply {
            text = getString(
                    if (isScanningOn) R.string.button_stop_scanning
                    else R.string.button_start_scanning
            )
            setIsActionOn(isScanningOn)
        }
    }

    override fun onPause() {
        super.onPause()
        getScanFragment().setScanFragmentListener(null)
        bluetoothService?.apply {
            unregisterGattServerCallback()
            unregisterGattCallback()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        inflater.inflate(R.menu.menu_browser, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_filter -> {
                getScanFragment().toggleFilterFragment(shouldShowFilterFragment = true)
                true
            }
            R.id.menu_sort -> {
                viewModel.let { model ->
                    devicesAdapter?.let { adapter ->
                        model.sortDevices()
                        adapter.updateDevices(model.getBluetoothInfoViewsState(), withMoves = true)
                        viewBinding.rvDebugDevices.scrollToPosition(0)
                        CustomToastManager.show(requireContext(),getString(R.string.devices_sorted_by_descending_rssi),5000)
                    }
                }
                true
            }
            R.id.menu_uuid_dictionary -> {
                startActivity(Intent(requireContext(), UuidDictionaryActivity::class.java))
                true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onRefresh() {
        if (isBluetoothOperationPossible() &&
                activityViewModel?.getIsLocationPermissionGranted() == true) {
            getScanFragment().toggleScannerState(false)

            viewModel.apply {
                reset()
                shouldResetChart = true
                setTimestamps()
            }

            toggleScanningButton(true)
            toggleMainView(isScanningOn = true, isAnyDeviceDiscovered = false)

            handler.removeCallbacks(refreshScanRunnable)
            handler.postDelayed(refreshScanRunnable, RESTART_SCAN_TIMEOUT)
        } else {
            if (activityViewModel?.getIsBluetoothOn() != true) {
                //Toast.makeText(requireContext(), getString(R.string.bluetooth_disabled), Toast.LENGTH_SHORT).show()
                CustomToastManager.show(requireContext(),getString(R.string.bluetooth_disabled),5000)

            } else if (activityViewModel?.getAreBluetoothPermissionsGranted() != true) {
                //Toast.makeText(requireContext(), getString(R.string.bluetooth_permissions_denied), Toast.LENGTH_SHORT).show()
                CustomToastManager.show(requireContext(),getString(R.string.bluetooth_permissions_denied),5000)

            } else if (activityViewModel?.getIsLocationPermissionGranted() != true) {
               // Toast.makeText(requireContext(), getString(R.string.location_permission_denied), Toast.LENGTH_SHORT).show()
                CustomToastManager.show(requireContext(),getString(R.string.location_permission_denied),5000)

            }
        }
        viewBinding.swipeRefreshContainer.post { viewBinding.swipeRefreshContainer.isRefreshing = false }
    }

    private fun initDevicesRecyclerView() {
        devicesAdapter = DebugModeDeviceAdapter(mutableListOf(), debugModeCallback)
        viewBinding.rvDebugDevices.apply {
            layoutManager = getLayoutManagerWithHidingUIElements(requireContext())
            addItemDecoration(CardViewListDecoration())
            adapter = devicesAdapter
        }
    }

    private fun initSwipeRefreshLayout() {
        viewBinding.swipeRefreshContainer.apply {
            setOnRefreshListener(this@BrowserFragment)
            setColorSchemeColors(
                    ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark),
                    ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark),
                    ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light),
                    ContextCompat.getColor(requireContext(), android.R.color.holo_red_light)
            )
        }
    }

    private fun showConnectingAnimation() {
        activity?.runOnUiThread {
            viewBinding.apply {
                btnScanning.visibility = View.GONE
                flyInBar.visibility = View.VISIBLE
                flyInBar.startFlyInAnimation(getString(R.string.debug_mode_device_selection_connecting_bar))
            }
        }
    }

    private fun hideConnectingAnimation() {
        activity?.runOnUiThread {
            devicesAdapter?.notifyDataSetChanged()
            viewBinding.apply {
                flyInBar.clearBarAnimation()
                flyInBar.visibility = View.GONE
                btnScanning.visibility = View.VISIBLE
            }
        }
    }

    private val gattCallback = object : TimeoutGattCallback() {
        override fun onTimeout() {
           /* Toast.makeText(requireContext(), getString(R.string.toast_connection_timed_out), Toast.LENGTH_SHORT).show()*/
            CustomToastManager.show(requireContext(), getString(
                R.string.toast_connection_timed_out),5000)
            hideConnectingAnimation()
            deviceToConnect = null
        }

        override fun onMaxRetriesExceeded(gatt: BluetoothGatt) {
            handleDisconnection(gatt, status = 133)
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            activity?.runOnUiThread {
                viewModel.updateActiveConnections(bluetoothService?.getActiveConnections())
            }

            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) handleSuccessfulConnection(gatt)
                }
                BluetoothGatt.STATE_DISCONNECTED -> {
                    when (status) {
                        133 -> {
                            (activity as MainActivity).showMessage(R.string.connection_failed_reconnecting)
                            showConnectingAnimation()
                        }
                        else -> handleDisconnection(gatt, status)
                    }
                }
            }
        }

    }

    private fun handleSuccessfulConnection(gatt: BluetoothGatt) {
        hideConnectingAnimation()
        if (bluetoothService?.isGattConnected(deviceToConnect?.address) == true) {
            deviceToConnect?.let { bluetoothService?.updateConnectionInfo(it) }
            viewModel.setDeviceConnectionState(gatt.device.address, BluetoothDeviceInfo.ConnectionState.CONNECTED)

            handler.removeCallbacks(startActivityRunnable)
            handler.postDelayed(startActivityRunnable, START_ACTIVITY_DELAY)
            blockConnectionAttempts = true
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleDisconnection(gatt: BluetoothGatt, status: Int) {
        hideConnectingAnimation()
        viewModel.setDeviceConnectionState(gatt.device.address, BluetoothDeviceInfo.ConnectionState.DISCONNECTED)
        activity?.runOnUiThread {
            val deviceName = if (TextUtils.isEmpty(gatt.device.name)) getString(R.string.not_advertising_shortcut) else gatt.device.name
            if (deviceToConnect != null && deviceToConnect?.address!! == gatt.device.address) {
                deviceToConnect = null
             // Toast.makeText(requireContext(), getFailedConnectingToDeviceMessage(deviceName, status), Toast.LENGTH_LONG).show()
                CustomToastManager.show(requireContext(),getFailedConnectingToDeviceMessage(deviceName, status),5000)
            } else {
                (activity as MainActivity).showLongMessage(
                        if (status == 0) getString(R.string.device_disconnected_successfully, getDeviceName(gatt))
                        else getDeviceDisconnectedMessage(deviceName, status)
                )
            }
        }
    }

    private val startActivityRunnable = Runnable {
        deviceToConnect?.let {
            Intent(requireContext(), DeviceServicesActivity::class.java).apply {
                putExtra(DeviceServicesActivity.CONNECTED_DEVICE, it.device)
                putExtra(RECYCLERVIEW_POSITION, devicesAdapter?.getItemPosition())
                putExtra(ORIGIN,"BrowserFragment")
            }.also {
                activityResultCallback.launch(it)
            }
        }
        deviceToConnect = null
        blockConnectionAttempts = false
    }

    private val activityResultCallback =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == DeviceServicesActivity.REFRESH_INFO_RESULT_CODE) {
                result.data?.let { intent ->
                    val device = intent.getParcelableExtra<BluetoothDevice>(
                            DeviceServicesActivity.CONNECTED_DEVICE)
                    val connectionState = intent.getIntExtra(
                            DeviceServicesActivity.CONNECTION_STATE,
                            BluetoothGatt.STATE_DISCONNECTED)
                    device?.let { viewModel.refreshConnectedDeviceInfo(device, connectionState) }
                }
            }
        }

    /* Getting connection from other device when on BrowserFragment */
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                if (bluetoothService?.isAnyConnectionPending() == false && !blockConnectionAttempts) {
                    deviceToConnect = BluetoothDeviceInfo(device)
                    bluetoothService?.connectGatt(device, requestRssiUpdates = true)
                }
            }
        }
    }

     var debugModeCallback = object : DebugModeCallback {
        override fun connectToDevice(position: Int, deviceInfo: BluetoothDeviceInfo) {
            if (viewModel.getIsScanningOn()) {
                viewModel.setIsScanningOn(false)
            }

            viewModel.setDeviceConnectionState(position, connectionState = BluetoothDeviceInfo.ConnectionState.CONNECTING)
            deviceToConnect = deviceInfo
            showConnectingAnimation()

            handler.postDelayed({
                bluetoothService?.let {
                    it.isNotificationEnabled = false
                    bluetoothService?.connectGatt(deviceInfo.device, true, gattCallback)
                }
            }, ANIMATION_DELAY)
        }

        override fun disconnectDevice(position: Int, device: BluetoothDevice) {
            viewModel.setDeviceConnectionState(position, connectionState = BluetoothDeviceInfo.ConnectionState.DISCONNECTED)
            bluetoothService?.disconnectGatt(device.address)
        }

        override fun addToFavorites(deviceAddress: String) {
            viewModel.toggleIsFavorite(deviceAddress)
            sharedPrefUtils.addDeviceToFavorites(deviceAddress)
        }

        override fun removeFromFavorites(deviceAddress: String) {
            viewModel.toggleIsFavorite(deviceAddress)
            sharedPrefUtils.removeDeviceFromFavorites(deviceAddress)
        }


        override fun toggleViewExpansion(position: Int) {
            viewModel.toggleViewExpansion(position)
        }
    }

    @SuppressLint("MissingPermission")
    private fun getDeviceName(gatt: BluetoothGatt) : String {
        return gatt.device.name ?: getString(R.string.not_advertising_shortcut)
    }


    companion object {
        private const val RESTART_SCAN_TIMEOUT = 1000L
        private const val SCAN_UPDATE_PERIOD = 1000L //ms
        private const val START_ACTIVITY_DELAY = 250L
        private const val ANIMATION_DELAY = 1000L // give time to display animation
        const val RECYCLERVIEW_POSITION = "rv_position"
        const val ORIGIN = "origin"

    }
}
