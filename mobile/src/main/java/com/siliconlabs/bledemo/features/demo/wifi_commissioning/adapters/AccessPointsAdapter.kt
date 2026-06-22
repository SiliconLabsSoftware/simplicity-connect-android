package com.siliconlabs.bledemo.features.demo.wifi_commissioning.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.features.demo.wifi_commissioning.adapters.AccessPointsAdapter.AccessPointViewHolder
import com.siliconlabs.bledemo.features.demo.wifi_commissioning.models.AccessPoint
import com.siliconlabs.bledemo.features.demo.wifi_commissioning.models.SecurityMode
import java.util.*

/**
 * Created by harika on 18-04-2016.
 */
class AccessPointsAdapter(
    private val accessPoints: ArrayList<AccessPoint>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<AccessPointViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccessPointViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.adapter_access_point, parent, false)
        return AccessPointViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: AccessPointViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun getItemCount(): Int {
        return accessPoints.size
    }

    fun getItem(position: Int): AccessPoint {
        return accessPoints[position]
    }

    private fun convertSecurityMode(mode: SecurityMode): Int {
        return when (mode) {
            SecurityMode.OPEN -> R.string.security_mode_open
            SecurityMode.WPA -> R.string.security_mode_wpa
            SecurityMode.WPA2 -> R.string.security_mode_wpa2
            SecurityMode.WPA3 -> R.string.security_mode_wpa3
            SecurityMode.WEP -> R.string.security_mode_wep
            SecurityMode.EAP_WPA -> R.string.security_mode_eap_wpa
            SecurityMode.EAP_WPA2 -> R.string.security_mode_eap_wpa2
            SecurityMode.WPA_WPA2 -> R.string.status_wpa_wpa2
            SecurityMode.WPA2_WPA3 -> R.string.security_mode_wpa2_wpa3
            SecurityMode.OWE -> R.string.security_mode_enhanced_open
            SecurityMode.OWE_TRANSITION -> R.string.security_mode_enhanced_open_transition
            SecurityMode.WPA3_EAP -> R.string.security_mode_enhanced_wpa3_enterprise
            SecurityMode.WPA3_EAP_192 -> R.string.security_mode_enhanced_wpa3_enterprise_192
            SecurityMode.WPA2_WPA3_EAP -> R.string.security_mode_enhanced_wpa3_enterprise_transition
            SecurityMode.WPA3_SAE_EXT -> R.string.security_mode_enhanced_wpa3_personal_swe

            else -> R.string.security_mode_unknown
        }
    }

    interface OnItemClickListener {
        fun onItemClick(itemView: View?, position: Int)
    }

    inner class AccessPointViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private var name: TextView = itemView.findViewById(R.id.access_pt_name)
        private var securityMode: TextView = itemView.findViewById(R.id.access_pt_type)
        private var macAddress: TextView = itemView.findViewById(R.id.access_mac_address)
        private var ipAddress: TextView = itemView.findViewById(R.id.access_pt_ip_address)
        private var statusImage: ImageView = itemView.findViewById(R.id.access_pt_status)


        fun bind(position: Int) {
            val deviceName = accessPoints[position].name
            name.text = deviceName

            @StringRes val securityMode = convertSecurityMode(accessPoints[position].securityMode)
            this.securityMode.setText(securityMode)

            if (accessPoints[position].status) {
                statusImage.setImageResource(R.drawable.icon_connected)
                if (accessPoints[position].macAddress != null) {
                    macAddress.text = accessPoints[position].macAddress
                    macAddress.visibility = View.VISIBLE
                }
                if (accessPoints[position].ipAddress != null) {
                    ipAddress.text = accessPoints[position].ipAddress
                    ipAddress.visibility = View.VISIBLE
                }
            } else {
                statusImage.setImageResource(R.drawable.icon_disconnect)
                macAddress.visibility = View.GONE
                ipAddress.visibility = View.GONE
            }
            itemView.setOnClickListener { view: View? -> listener.onItemClick(view, position) }
        }

    }
}