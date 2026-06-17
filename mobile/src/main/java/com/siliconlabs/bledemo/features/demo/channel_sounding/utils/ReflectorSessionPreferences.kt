package com.siliconlabs.bledemo.features.demo.channel_sounding.utils

import android.content.Context

/**
 * Persists the paired Digital Key (reflector) device so the app can reopen
 * [ReflectorDashboardActivity] after process death without scan/pairing again.
 */
object ReflectorSessionPreferences {

    private const val PREFS_NAME = "reflector_digital_key_session"
    private const val KEY_DEVICE_ADDRESS = "device_address"
    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_PAIRING_COMPLETE = "pairing_complete"

    fun savePairedDevice(context: Context, deviceAddress: String, deviceName: String?) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEVICE_ADDRESS, deviceAddress)
            .putString(KEY_DEVICE_NAME, deviceName)
            .putBoolean(KEY_PAIRING_COMPLETE, true)
            .apply()
    }

    /**
     * @return address and optional display name when a prior reflector pairing completed; null otherwise.
     */
    fun getPairedDevice(context: Context): Pair<String, String?>? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_PAIRING_COMPLETE, false)) return null
        val address = prefs.getString(KEY_DEVICE_ADDRESS, null)?.trim().orEmpty()
        if (address.isEmpty()) return null
        return address to prefs.getString(KEY_DEVICE_NAME, null)
    }

    fun hasPairedDevice(context: Context): Boolean = getPairedDevice(context) != null

    /**
     * Saved session is valid only when pairing was completed and the lock is still bonded on the phone.
     */
    fun hasActivePairedSession(context: Context): Boolean {
        val paired = getPairedDevice(context) ?: return false
        return ReflectorBondUtils.isDeviceBonded(paired.first)
    }

    fun clearPairedDevice(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
