package com.siliconlabs.bledemo.home_screen.activities

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.*
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.marginTop
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.siliconlabs.bledemo.base.activities.BaseActivity
import com.siliconlabs.bledemo.bluetooth.services.BluetoothService
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.databinding.ActivityMainBinding
import com.siliconlabs.bledemo.features.demo.channel_sounding.utils.ReflectorFlowNavigator
import com.siliconlabs.bledemo.home_screen.dialogs.PermissionsDialog
import com.siliconlabs.bledemo.home_screen.fragments.DemoFragment
import com.siliconlabs.bledemo.home_screen.viewmodels.MainActivityViewModel
import com.siliconlabs.bledemo.home_screen.views.HidableBottomNavigationView
import com.siliconlabs.bledemo.utils.AppUtil
import com.siliconlabs.bledemo.utils.CustomToastManager
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
open class MainActivity : BaseActivity(),
        BluetoothService.ServicesStateListener
{
    private lateinit var _binding:ActivityMainBinding
    private lateinit var viewModel: MainActivityViewModel
    private lateinit var binding: BluetoothService.Binding
    var bluetoothService: BluetoothService? = null
        private set

    private val neededPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
        )

    @RequiresApi(Build.VERSION_CODES.S)
    private val android12Permissions = listOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    private val toastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_SHOW_CUSTOM_TOAST) {
                val message = intent.getStringExtra(EXTRA_TOAST_MESSAGE)
                message?.let {
                    CustomToastManager.show(this@MainActivity, it)
                }
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.MainAppTheme_NoActionBar)

        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
       // setContentView(R.layout.activity_main)
        setContentView(_binding.root)
        setSupportActionBar(_binding.includeLayoutEdgeToEdge.toolbar)
        AppUtil.setEdgeToEdge(window,this)
        supportActionBar?.show()

        viewModel = ViewModelProvider(this).get(MainActivityViewModel::class.java)

        handlePermissions()
        setupMainNavigationListener()
        handleReflectorLaunchIntent(intent)

        // Register the receiver
        val filter = IntentFilter(ACTION_SHOW_CUSTOM_TOAST)
        LocalBroadcastManager.getInstance(this).registerReceiver(toastReceiver, filter)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleReflectorLaunchIntent(intent)
    }

    private fun handleReflectorLaunchIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(ReflectorFlowNavigator.EXTRA_LAUNCH_REFLECTOR_PAIRING, false) != true) {
            return
        }
        intent.removeExtra(ReflectorFlowNavigator.EXTRA_LAUNCH_REFLECTOR_PAIRING)
        DemoFragment.requestReflectorPairingOnNextResume()
        val navFragment = supportFragmentManager.findFragmentById(R.id.main_fragment) as? NavHostFragment
        navFragment?.navController?.navigate(R.id.demoFragment)
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.getIsSetupFinished()) {
            viewModel.setIsLocationPermissionGranted(isPermissionGranted(neededPermissions[0]))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                viewModel.setAreBluetoothPermissionsGranted(areBluetoothPermissionsGranted())
            }
        }
    }

    private fun setupMainNavigationListener() {
        val navFragment = supportFragmentManager.findFragmentById(R.id.main_fragment) as NavHostFragment
        val navController = navFragment.navController

        NavigationUI.setupWithNavController(_binding.mainNavigation, navController)
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

    fun toggleMainNavigation(isOn: Boolean) {
        if(isOn) {
            _binding.mainNavigation.show(instant = true)
        } else {
            _binding.mainNavigation.hide(instant = true)
        }
    }

    fun toggleHomeIcon(isOn: Boolean) {
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(isOn)
            if (isOn) setHomeAsUpIndicator(R.drawable.redesign_ic_close)
        }
    }

    private fun bindBluetoothService() {
        binding = object : BluetoothService.Binding(this) {
            override fun onBound(service: BluetoothService?) {
                this@MainActivity.bluetoothService = service
                bluetoothService?.servicesStateListener = this@MainActivity
                setServicesInitialState()
            }
        }
        binding.bind()
    }

    private fun setServicesInitialState() {
        viewModel.setIsLocationPermissionGranted(isPermissionGranted(neededPermissions[0]))
        viewModel.setAreBluetoothPermissionsGranted(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) areBluetoothPermissionsGranted()
            else true /* No runtime permissions needed for bluetooth operation in Android 11- */
        )
        bluetoothService?.let {
            viewModel.setIsBluetoothOn(it.isBluetoothOn())
            viewModel.setIsLocationOn(it.isLocationOn())
            viewModel.setIsNotificationOn(it.areNotificationOn())
        }
        observeChanges()
        viewModel.setIsSetupFinished(isSetupFinished = true)
    }

    private fun observeChanges() {
        viewModel.areBluetoothPermissionGranted.observe(this) {
            bluetoothService?.setAreBluetoothPermissionsGranted(
                viewModel.getAreBluetoothPermissionsGranted())
        }
    }

    fun getMainNavigation(): HidableBottomNavigationView? {
        return _binding.mainNavigation
    }

    override fun onBluetoothStateChanged(isOn: Boolean) {
        viewModel.setIsBluetoothOn(isOn)
    }

    override fun onLocationStateChanged(isOn: Boolean) {
        viewModel.setIsLocationOn(isOn)
    }

    override fun onNotificationStateChanged(isOn: Boolean) {
        viewModel.setIsNotificationOn(isOn)
    }

    private fun handlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            neededPermissions.addAll(android12Permissions)
        }
        // API 33+: notifications are off by default until POST_NOTIFICATIONS is granted (FGS, permission bar, etc.)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (neededPermissions.any { !isPermissionGranted(it) }) askForPermissions()
        else bindBluetoothService()
    }

    private fun askForPermissions() {
        val rationalesToShow = neededPermissions.filter { shouldShowRequestPermissionRationale(it) }
        val permissionsToRequest = neededPermissions.toTypedArray()

        if (rationalesToShow.isNotEmpty()) {
            PermissionsDialog(rationalesToShow, object : PermissionsDialog.Callback {
                override fun onDismiss() {
                    requestPermissions(permissionsToRequest, PERMISSIONS_REQUEST_CODE)
                }
            }).show(supportFragmentManager, "permissions_dialog")
        } else {
            requestPermissions(permissionsToRequest, PERMISSIONS_REQUEST_CODE)
        }
    }

    private fun isPermissionGranted(permission: String) : Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun areBluetoothPermissionsGranted() : Boolean {
        return android12Permissions.all { isPermissionGranted(it) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSIONS_REQUEST_CODE -> bindBluetoothService()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the receiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(toastReceiver)
    }

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 400
        // private const val IMPORT_EXPORT_CODE_VERSION = 20
        const val ACTION_SHOW_CUSTOM_TOAST = "com.example.ACTION_SHOW_CUSTOM_TOAST"
        const val EXTRA_TOAST_MESSAGE = "com.example.EXTRA_TOAST_MESSAGE"
    }
//TODO: handle migration. See BTAPP-1285 for clarification.
/*
    private fun migrateGattDatabaseIfNeeded() {
        if (BuildConfig.VERSION_CODE <= IMPORT_EXPORT_CODE_VERSION - 1) {
            Migrator(this).migrate()
        }
    }
*/

}