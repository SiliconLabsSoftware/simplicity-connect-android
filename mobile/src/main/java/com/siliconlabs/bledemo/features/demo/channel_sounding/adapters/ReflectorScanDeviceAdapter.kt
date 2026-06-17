package com.siliconlabs.bledemo.features.demo.channel_sounding.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.siliconlabs.bledemo.bluetooth.ble.BluetoothDeviceInfo
import com.siliconlabs.bledemo.databinding.ItemReflectorScanDeviceBinding

/**
 * Adapter for displaying scanned CS Initiator devices (door locks) in Reflector mode.
 * 
 * Phase 2 Feature: Part of the Digital Key for Door Lock use case.
 */
class ReflectorScanDeviceAdapter(
    private val devices: List<BluetoothDeviceInfo>,
    private val onDeviceClick: (BluetoothDeviceInfo) -> Unit
) : RecyclerView.Adapter<ReflectorScanDeviceAdapter.DeviceViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemReflectorScanDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount(): Int = devices.size

    override fun getItemId(position: Int): Long {
        return devices[position].device.address.hashCode().toLong()
    }

    inner class DeviceViewHolder(
        private val binding: ItemReflectorScanDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("MissingPermission", "SetTextI18n")
        fun bind(deviceInfo: BluetoothDeviceInfo) {
            binding.apply {
                deviceName.text = deviceInfo.name ?: "Unknown Device"
                deviceAddress.text = deviceInfo.device.address
                deviceRssi.text = "${deviceInfo.rssi} dBm"

                root.setOnClickListener {
                    onDeviceClick(deviceInfo)
                }
            }
        }
    }
}
