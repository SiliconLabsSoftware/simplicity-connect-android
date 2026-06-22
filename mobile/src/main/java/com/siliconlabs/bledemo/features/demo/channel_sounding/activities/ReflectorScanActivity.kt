package com.siliconlabs.bledemo.features.demo.channel_sounding.activities

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanFilter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.base.activities.BaseActivity
import com.siliconlabs.bledemo.bluetooth.ble.BluetoothDeviceInfo
import com.siliconlabs.bledemo.bluetooth.ble.ScanResultCompat
import com.siliconlabs.bledemo.bluetooth.services.BluetoothService
import com.siliconlabs.bledemo.databinding.ActivityReflectorScanBinding
import com.siliconlabs.bledemo.features.demo.channel_sounding.adapters.ReflectorScanDeviceAdapter
import com.siliconlabs.bledemo.utils.CustomToastManager

/**
 * Activity for scanning BLE devices in Reflector mode (Phone as Reflector).
 * Filters and displays only devices named "Silabs Example" which are CS Initiator devices (door locks).
 * 
 * Phase 2 Feature: Part of the Digital Key for Door Lock use case.
 */
@SuppressLint("MissingPermission")
class ReflectorScanActivity : BaseActivity(), BluetoothService.ScanListener {

    private lateinit var binding: ActivityReflectorScanBinding
    private lateinit var bluetoothBinding: BluetoothService.Binding
    private var bluetoothService: BluetoothService? = null

    private lateinit var adapter: ReflectorScanDeviceAdapter
    private val discoveredDevices = mutableListOf<BluetoothDeviceInfo>()
    private val handler = Handler(Looper.getMainLooper())

    private val bluetoothAdapterStateChangeListener: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_OFF) {
                    finish()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReflectorScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        bindBluetoothService()

        registerReceiver(
            bluetoothAdapterStateChangeListener,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        )
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = ReflectorScanDeviceAdapter(discoveredDevices) { deviceInfo ->
            onDeviceSelected(deviceInfo)
        }

        binding.devicesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ReflectorScanActivity)
            adapter = this@ReflectorScanActivity.adapter
        }
    }

    private fun setupClickListeners() {
        binding.btnRefresh.setOnClickListener {
            restartScanning()
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun bindBluetoothService() {
        bluetoothBinding = object : BluetoothService.Binding(this) {
            override fun onBound(service: BluetoothService?) {
                bluetoothService = service
                startScanning()
            }
        }
        bluetoothBinding.bind()
    }

    private fun startScanning() {
        discoveredDevices.clear()
        adapter.notifyDataSetChanged()
        updateEmptyState()

        binding.scanProgress.visibility = View.VISIBLE
        binding.scanTitle.text = getString(R.string.reflector_scan_scanning_title)

        bluetoothService?.let { service ->
            service.removeListener(this)
            service.addListener(this)
            
            // Add a short delay before starting scan
            handler.postDelayed({
                val filters = createScanFilters()
                service.startDiscovery(filters)
            }, 300)
        }
    }

    private fun restartScanning() {
        stopScanning()
        handler.postDelayed({
            startScanning()
        }, 500)
    }

    private fun stopScanning() {
        bluetoothService?.let { service ->
            service.removeListener(this)
            service.stopDiscovery()
        }
        binding.scanProgress.visibility = View.INVISIBLE
    }

    /**
     * Creates scan filters to find devices named "Silabs Example".
     * This filters for CS Initiator devices that are door locks.
     */
    private fun createScanFilters(): List<ScanFilter> {
        val targetDeviceName = getString(R.string.channel_sounding_reflector_device_name)
        return listOf(
            ScanFilter.Builder()
                .setDeviceName(targetDeviceName)
                .build()
        )
    }

    private fun onDeviceSelected(deviceInfo: BluetoothDeviceInfo) {
        stopScanning()
        
        // Launch pairing activity with selected device
        val intent = Intent(this, ReflectorPairingActivity::class.java).apply {
            putExtra(EXTRA_DEVICE_ADDRESS, deviceInfo.device.address)
            putExtra(EXTRA_DEVICE_NAME, deviceInfo.name)
        }
        startActivity(intent)
        // Don't finish - allow user to come back if pairing fails
    }

    private fun updateEmptyState() {
        if (discoveredDevices.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.devicesRecyclerView.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.devicesRecyclerView.visibility = View.VISIBLE
        }
    }

    // BluetoothService.ScanListener implementation
    override fun handleScanResult(scanResult: ScanResultCompat) {
        val deviceName = scanResult.device?.name ?: return
        val targetName = getString(R.string.channel_sounding_reflector_device_name)

        // Filter for target device name (case-insensitive partial match)
        if (!deviceName.contains(targetName, ignoreCase = true)) {
            return
        }

        val deviceAddress = scanResult.device?.address ?: return

        // Check if device already in list
        val existingDevice = discoveredDevices.find { it.device.address == deviceAddress }
        if (existingDevice != null) {
            // Update RSSI - just update the scanInfo
            existingDevice.scanInfo = scanResult
            val index = discoveredDevices.indexOf(existingDevice)
            runOnUiThread {
                adapter.notifyItemChanged(index)
            }
        } else {
            // Add new device - create BluetoothDeviceInfo with device, then set scanInfo
            val deviceInfo = BluetoothDeviceInfo(scanResult.device!!).apply {
                scanInfo = scanResult
                isConnectable = scanResult.isConnectable
            }
            discoveredDevices.add(deviceInfo)
            runOnUiThread {
                adapter.notifyItemInserted(discoveredDevices.size - 1)
                updateEmptyState()
            }
        }
    }

    override fun onDiscoveryFailed() {
        runOnUiThread {
            binding.scanProgress.visibility = View.INVISIBLE
            CustomToastManager.showError(this, getString(R.string.reflector_scan_discovery_failed))
        }
    }

    override fun onDiscoveryTimeout() {
        runOnUiThread {
            binding.scanProgress.visibility = View.INVISIBLE
            binding.scanTitle.text = getString(R.string.reflector_scan_scan_complete)
        }
    }

    override fun onResume() {
        super.onResume()
        if (bluetoothService != null) {
            startScanning()
        }
    }

    override fun onPause() {
        super.onPause()
        stopScanning()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScanning()
        unregisterReceiver(bluetoothAdapterStateChangeListener)
        bluetoothBinding.unbind()
    }

    companion object {
        const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
        const val EXTRA_DEVICE_NAME = "extra_device_name"
    }
}
