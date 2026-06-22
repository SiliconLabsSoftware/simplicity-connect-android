package com.siliconlabs.bledemo.home_screen.fragments

import android.content.*
import android.os.Bundle
import android.view.*
import com.siliconlabs.bledemo.home_screen.dialogs.SelectDeviceDialog
import com.siliconlabs.bledemo.bluetooth.services.BluetoothService
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.databinding.FragmentTestBinding
import com.siliconlabs.bledemo.features.iop_test.dialogs.AboutIopDialog
import com.siliconlabs.bledemo.home_screen.base.BaseServiceDependentMainMenuFragment
import com.siliconlabs.bledemo.home_screen.base.BluetoothDependent
import com.siliconlabs.bledemo.home_screen.base.LocationDependent
import com.siliconlabs.bledemo.home_screen.base.NotificationDependent
import com.siliconlabs.bledemo.utils.BLEUtils

class TestFragment : BaseServiceDependentMainMenuFragment(), DialogInterface.OnDismissListener {

    private lateinit var viewBinding: FragmentTestBinding
    private var selectDeviceDialog: SelectDeviceDialog? = null


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        setHasOptionsMenu(true)
        viewBinding = FragmentTestBinding.inflate(inflater)
        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity?.title = getString(R.string.main_navigation_test_title)

        initMainViewValues()
        setupUiListeners()
    }

    private fun initMainViewValues() {
        viewBinding.fragmentMainView.fullScreenInfo.apply {
            image.setImageResource(R.drawable.redesign_ic_main_view_iop)
            textPrimary.apply {
                text = getString(R.string.iop_test_full_page_info)
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = layoutParams.apply { width = ViewGroup.LayoutParams.MATCH_PARENT }
            }
            textSecondary.visibility = View.GONE
        }
        viewBinding.fragmentMainView.extendedFabMainView.text = getString(R.string.iop_test_select_device_btn)
    }

    private fun setupUiListeners() {
        viewBinding.fragmentMainView.extendedFabMainView.setOnClickListener {
            BLEUtils.GATT_DEVICE_SELECTED = BluetoothService.GattConnectType.IOP_TEST
            selectDeviceDialog = SelectDeviceDialog.newDialog(BluetoothService.GattConnectType.IOP_TEST)
            selectDeviceDialog?.show(childFragmentManager, "select_device_dialog")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_test_fragment, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.info_dialog -> {
                AboutIopDialog().show(childFragmentManager, "about_dialog")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override val bluetoothDependent = object : BluetoothDependent {

        override fun onBluetoothStateChanged(isBluetoothOn: Boolean) {
            toggleBluetoothBar(isBluetoothOn, viewBinding.bluetoothBar)
            viewBinding.fragmentMainView.extendedFabMainView.isEnabled =
                isBluetoothOperationPossible()
            if (!isBluetoothOn) selectDeviceDialog?.dismiss()
        }

        override fun onBluetoothPermissionsStateChanged(arePermissionsGranted: Boolean) {
            toggleBluetoothPermissionsBar(arePermissionsGranted, viewBinding.bluetoothPermissionsBar)
            viewBinding.fragmentMainView.extendedFabMainView.isEnabled =
                isBluetoothOperationPossible()
            if (!arePermissionsGranted) selectDeviceDialog?.dismiss()
        }

        override fun refreshBluetoothDependentUi(isBluetoothOperationPossible: Boolean) {
            viewBinding.fragmentMainView.extendedFabMainView.isEnabled = isBluetoothOperationPossible
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
            toggleLocationPermissionBar(isPermissionGranted, viewBinding.locationPermissionBar)
            viewBinding.fragmentMainView.extendedFabMainView.isEnabled = isPermissionGranted
            if (!isPermissionGranted) selectDeviceDialog?.dismiss()
        }
        override fun setupLocationBarButtons() {
            viewBinding.locationBar.setFragmentManager(childFragmentManager)
        }

        override fun setupLocationPermissionBarButtons() {
            viewBinding.locationPermissionBar.setFragmentManager(childFragmentManager)
        }
    }



    override fun onDismiss(dialogInterface: DialogInterface) {
        selectDeviceDialog = null
    }
}