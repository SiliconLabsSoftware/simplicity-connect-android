package com.siliconlabs.bledemo.features.demo.wifi_provisioning.model

import com.siliconlabs.bledemo.features.demo.wifi_commissioning.models.SecurityMode

data class ScanResult(
    val ssid: String,
    val security_type: String,
    val network_type: String,
    val bssid: String,
    val channel: String,
    val rssi: String
) {
    /** True when the AP does not need a passphrase (open / security type 0). */
    fun requiresPassword(): Boolean {
        val trimmed = security_type.trim()
        if (trimmed.equals("OPEN", ignoreCase = true)) {
            return false
        }
        trimmed.toIntOrNull()?.let { code ->
            if (SecurityMode.fromInt(code) == SecurityMode.OPEN) {
                return false
            }
        }
        return true
    }
}
