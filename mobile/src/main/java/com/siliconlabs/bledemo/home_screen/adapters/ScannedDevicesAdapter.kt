package com.siliconlabs.bledemo.home_screen.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.os.ParcelUuid
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat.getString
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.siliconlabs.bledemo.bluetooth.ble.BluetoothDeviceInfo
import com.siliconlabs.bledemo.bluetooth.ble.GattService
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.databinding.AdapterScannedDeviceBinding
import com.siliconlabs.bledemo.utils.RecyclerViewUtils
import kotlin.math.max

class ScannedDevicesAdapter(
        private var scannedDemoDevices: MutableList<BluetoothDeviceInfo>,
        private val demoDeviceCallback: DemoDeviceCallback,
        /** When true (Connected Device demo), show protocol icon ([android.R.id.icon2]) beside RSSI ([android.R.id.icon]). */
        private val showProtocolIcon: Boolean = false,
) : RecyclerView.Adapter<ScannedDevicesAdapter.ViewHolder>()
{

    var localParentContext: Context? = null

    override fun getItemCount(): Int {
        return scannedDemoDevices.size
    }

    override fun getItemId(position: Int): Long {
        val info: BluetoothDeviceInfo = scannedDemoDevices[position]
        return info.device.address.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val viewBinding = AdapterScannedDeviceBinding.inflate(LayoutInflater.from(parent.context))
        localParentContext = parent.context
        return ViewHolder(viewBinding, localParentContext!!, showProtocolIcon).apply {
            viewBinding.root.setOnClickListener {
                RecyclerViewUtils.withProperAdapterPosition(this) { pos ->
                    demoDeviceCallback.onDemoDeviceClicked(scannedDemoDevices[pos])
                }
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            holder.showChanges(payloads)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(scannedDemoDevices[position])
    }

    fun addNewDevice(newDevice: BluetoothDeviceInfo) {
        scannedDemoDevices.add(newDevice.clone())
        notifyItemInserted(itemCount - 1)
    }

    fun updateList(newList: MutableList<BluetoothDeviceInfo>) {
        val listDiff = DiffUtil.calculateDiff(DiffCallback(
                scannedDemoDevices.toList(),
                newList.toList()
        ), false)

        scannedDemoDevices = getDeepCopyList(newList).toMutableList()
        listDiff.dispatchUpdatesTo(this)
    }

    private fun getDeepCopyList(list: List<BluetoothDeviceInfo>) : List<BluetoothDeviceInfo> {
        return mutableListOf<BluetoothDeviceInfo>().apply {
            list.forEach { add(it.clone()) }
        }
    }

    private class DiffCallback(
            private val oldList: List<BluetoothDeviceInfo>,
            private val newList: List<BluetoothDeviceInfo>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int {
            return oldList.size
        }

        override fun getNewListSize(): Int {
            return newList.size
        }

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].address == newList[newItemPosition].address
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]

            return oldItem.rssi == newItem.rssi
                    && oldItem.scanInfo?.timestampNanos == newItem.scanInfo?.timestampNanos
        }

        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any {
            return PayloadChange(oldList[oldItemPosition], newList[newItemPosition])
        }
    }

    private class PayloadChange(val oldItem: BluetoothDeviceInfo, val newItem: BluetoothDeviceInfo)

    fun clear() {
        scannedDemoDevices.clear()
        notifyDataSetChanged()
    }

    interface DemoDeviceCallback {
        fun onDemoDeviceClicked(deviceInfo: BluetoothDeviceInfo)
    }

    class ViewHolder(
        private val viewBinding: AdapterScannedDeviceBinding,
        val context: Context,
        private val showProtocolIcon: Boolean,
    ) : RecyclerView.ViewHolder(viewBinding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(info: BluetoothDeviceInfo) {
            viewBinding.apply {
                itemBottomDivider.visibility = View.VISIBLE
                title.text = info.scanInfo?.getDisplayName()
                address.text = info.scanInfo?.device?.address
                tvRssiLabel.text = String.format(getString(context,R.string.unit_value_dbm), info.scanInfo?.rssi)


                icon.setImageLevel(getRssiIconValue(info.rssi))

                if (!showProtocolIcon) {
                    icon2.visibility = View.GONE
                } else {
                    info.scanInfo?.scanRecord?.serviceUuids?.let {
                        icon2.apply {
                            when {
                                it.contains(ParcelUuid(GattService.ZigbeeLightService.number)) -> {
                                    setImageResource(R.drawable.icon_zigbee)
                                    visibility = View.VISIBLE
                                }
                                it.contains(ParcelUuid(GattService.ProprietaryLightService.number)) -> {
                                    setImageResource(R.drawable.icon_proprietary)
                                    visibility = View.VISIBLE
                                }
                                it.contains(ParcelUuid(GattService.ConnectLightService.number)) -> {
                                    setImageResource(R.drawable.icon_connect)
                                    visibility = View.VISIBLE
                                }
                                it.contains(ParcelUuid(GattService.ThreadLightService.number)) -> {
                                    setImageResource(R.drawable.icon_thread)
                                    visibility = View.VISIBLE
                                }
                                it.contains(ParcelUuid(GattService.TheDMP.number)) -> {
                                    setImageResource(R.drawable.ic_aws_iot_icon)
                                    visibility = View.VISIBLE
                                    clipToOutline = true
                                }
                                it.contains(ParcelUuid(GattService.TheAmazonSideWalk.number)) -> {
                                    setImageResource(R.drawable.ic_side_walk_icon)
                                    visibility = View.VISIBLE
                                    clipToOutline = true
                                }
                                else -> {
                                    visibility = View.GONE
                                }
                            }
                        }
                    } ?: run {
                        icon2.visibility = View.GONE
                    }
                }
            }
        }

        @SuppressLint("SetTextI18n")
        fun showChanges(payloads: List<Any>) {
            val oldState = (payloads.first() as PayloadChange).oldItem
            val newState = (payloads.last() as PayloadChange).newItem

            if (!showProtocolIcon) {
                viewBinding.icon2.visibility = View.GONE
            }

            if (!isRssiIconLevelSame(getRssiIconValue(oldState.rssi), getRssiIconValue(newState.rssi))) {
                viewBinding.tvRssiLabel.text = String.format(getString(context,R.string.unit_value_dbm), newState.rssi)
                viewBinding.icon.setImageLevel(getRssiIconValue(newState.rssi))
            }
        }

        private fun isRssiIconLevelSame(oldIconRssi: Int, newIconRssi: Int) : Boolean {
            return oldIconRssi == 0 && newIconRssi == 0 ||
                    oldIconRssi in (1.. 29) && newIconRssi in (1..29) ||
                    oldIconRssi in (30.. 79) && newIconRssi in (30.. 79)
        }

        private fun getRssiIconValue(actualRssiValue: Int) : Int {
            return max(0, actualRssiValue + 80) // see bt_level.xml for explanation
        }
    }

}
