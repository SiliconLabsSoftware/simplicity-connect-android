package com.siliconlabs.bledemo.features.demo.channel_sounding.services

/**
 * High-level Digital Key session phase for dashboard / notification UX.
 */
enum class DigitalKeyConnectionPhase {
    /** BLE link down; foreground service not monitoring a device. */
    DISCONNECTED,

    /** BLE link down; waiting for stack autoConnect or user retry. */
    RECONNECTING,

    /** GATT connected; discovering services. */
    DISCOVERING,

    /** Waiting for bonding before encrypted GATT access. */
    BONDING,

    /** Reading BBCC / writing CCCD. */
    ENABLING_NOTIFICATIONS,

    /** BBCC notify active; starting CS reflector if needed. */
    STARTING_RANGING,

    /** Full pipeline ready — lock byte + CS drive firmware updates. */
    LIVE,

    /** Recoverable setup failure (discovery / BBCC / CCCD). */
    SETUP_FAILED
}
