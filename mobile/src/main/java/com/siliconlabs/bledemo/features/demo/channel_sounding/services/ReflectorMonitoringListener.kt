package com.siliconlabs.bledemo.features.demo.channel_sounding.services

import android.bluetooth.BluetoothGattService

/**
 * Lock state from the Digital Key characteristic (0xBBCC under service 0xAABB).
 * Firmware notifies only on state change after CCCD is enabled.
 */
enum class ReflectorDigitalKeyLockState {
    /** Not yet subscribed or no data (e.g. link down, discovery pending). */
    UNKNOWN,

    /** Payload byte 0x01 — LOCK (far). */
    LOCKED,

    /** Payload byte 0x00 — UNLOCK (near). */
    UNLOCKED
}

/**
 * UI callbacks for [ReflectorProximityForegroundService] state.
 * All methods are invoked on the main thread.
 */
interface ReflectorMonitoringListener {
    fun onConnectionUiState(connected: Boolean)
    fun onServicesDiscovered(services: List<BluetoothGattService>)
    fun onServiceDiscoveryFailed()
    fun onLockStateChanged(state: ReflectorDigitalKeyLockState)

    /** Session pipeline phase for status chips and hero copy. */
    fun onDigitalKeyPhaseChanged(phase: DigitalKeyConnectionPhase, detail: String?) {}

    /**
     * Called when service discovery succeeded but service 0xAABB or characteristic 0xBBCC is missing,
     * or CCCD/subscribe failed after retries. [recoverable] true → user can tap Retry.
     */
    fun onDigitalKeyGattUnavailable(recoverable: Boolean = false, message: String? = null) {}

    /** True after CCCD for 0xBBCC notify is written successfully. */
    fun onDigitalKeyNotifySubscribed(subscribed: Boolean) {}

    /** True when Android BLE CS responder ranging session is running (API 36+). */
    fun onCsReflectorActive(active: Boolean) {}

    /**
     * Bond removed, board reset, or another non-recoverable session loss after the Digital Key was live.
     * UI should show a blocking dialog and return the user to the demo screen.
     */
    fun onReflectorSessionLost() {}
}
