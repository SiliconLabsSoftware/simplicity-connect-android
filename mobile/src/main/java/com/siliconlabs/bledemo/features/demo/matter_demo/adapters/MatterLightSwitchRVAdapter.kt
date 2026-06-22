package com.siliconlabs.bledemo.features.demo.matter_demo.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.COLOR_TEMPERATURE_LIGHT_TYPE
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.DIMMABLE_Light_TYPE
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.DIMMER_SWITCH
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.ENHANCED_COLOR_LIGHT_TYPE
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.ON_OFF_LIGHT_TYPE
import com.siliconlabs.bledemo.features.demo.matter_demo.model.MatterScannedResultModel

class MatterLightSwitchRVAdapter(
    private var deviceList: ArrayList<MatterScannedResultModel>,
    private var selectedMatterName: String?,
    private val onLightSelected: (MatterScannedResultModel) -> Unit
) : RecyclerView.Adapter<MatterLightSwitchRVAdapter.DeviceViewHolder>() {

    inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.iconLight)
        val name: TextView = itemView.findViewById(R.id.deviceName)
        val checkMark: ImageView = itemView.findViewById(R.id.checkmark)
        val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_light_switch_controller, parent, false)
        return DeviceViewHolder(view)
    }

    fun updateList(newList: List<MatterScannedResultModel>) {
        deviceList = newList as ArrayList<MatterScannedResultModel>
        notifyDataSetChanged()
    }

    fun setSelectedMatterName(name: String?) {
        selectedMatterName = name
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = deviceList[position]
        if (device.deviceType == DIMMER_SWITCH) {
            holder.itemView.visibility = View.GONE
            holder.itemView.setOnClickListener(null)
            holder.itemView.isActivated = false
            return
        }
        if (isValidDeviceType(device.deviceType)) {
            holder.itemView.visibility = View.VISIBLE
            holder.name.text = device.matterName
            holder.checkMark.visibility =
                if (device.isBindingSuccessful && !device.isUnbindingInProgress) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            holder.itemView.isActivated = device.matterName == selectedMatterName
            holder.itemView.setOnClickListener {
                if (device.isBindingInProgress || device.isAclWriteInProgress) return@setOnClickListener
                onLightSelected(device)
            }
        } else {
            holder.itemView.visibility = View.GONE
            holder.itemView.setOnClickListener(null)
            holder.itemView.isActivated = false
        }
    }

    override fun getItemCount(): Int = deviceList.size

    private fun isValidDeviceType(deviceType: Int): Boolean {
        return when (deviceType) {
            DIMMABLE_Light_TYPE,
            ENHANCED_COLOR_LIGHT_TYPE,
            ON_OFF_LIGHT_TYPE,
            COLOR_TEMPERATURE_LIGHT_TYPE -> true
            else -> false
        }
    }
}
