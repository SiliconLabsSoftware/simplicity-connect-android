package com.siliconlabs.bledemo.features.demo.wifi_commissioning.models

enum class SecurityMode(val value: Int) {
    OPEN(0),
    WPA(1),
    WPA2(2),
    WEP(3),
    EAP_WPA(4),
    EAP_WPA2(5),
    WPA_WPA2(6),
    WPA3(7),
    WPA2_WPA3(8),
    OWE(9),
    OWE_TRANSITION(10),
    WPA3_EAP(11),
    WPA3_EAP_192(12),
    WPA2_WPA3_EAP(13),
    WPA3_SAE_EXT(14),

    UNKNOWN(255);

    /** Open networks do not use a pre-shared key in the provisioning flow. */
    fun requiresPassword(): Boolean = this != OPEN

    companion object {
        fun fromInt(code: Int) : SecurityMode {
            for (mode in values()) {
                if (mode.value == code) return mode
            }
            return UNKNOWN
        }
    }
}