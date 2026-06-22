package com.siliconlabs.bledemo.features.demo.channel_sounding.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.siliconlabs.bledemo.features.demo.channel_sounding.services.DigitalKeyConnectionPhase
import com.siliconlabs.bledemo.features.demo.channel_sounding.utils.ReflectorBondLifecycleSentinel
import com.siliconlabs.bledemo.features.demo.channel_sounding.utils.ReflectorSessionPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Foreground session-loss probing and preference clearing for the reflector dashboard.
 * Does not alter [com.siliconlabs.bledemo.features.demo.channel_sounding.services.ReflectorProximityForegroundService]
 * reconnect policy; observes connection snapshots pushed from the activity.
 */
class ReflectorDashboardViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val bondLifecycleSentinel = ReflectorBondLifecycleSentinel()
    private var foregroundProbeJob: Job? = null
    private var sessionLossEmitted: Boolean = false

    private var deviceAddress: String? = null
    private var hadReachedLive: Boolean = false
    private var linkUp: Boolean = false
    private var connectionPhase: DigitalKeyConnectionPhase = DigitalKeyConnectionPhase.DISCONNECTED
    private var foregroundServiceSessionLost: Boolean = false

    private var lastLiveTimestampMs: Long = 0L
    private var lastDisconnectAfterLiveMs: Long = 0L

    private val _uiEvents = Channel<ReflectorDashboardUiEvent>(Channel.BUFFERED)
    val uiEvents = _uiEvents.receiveAsFlow()

    fun setDeviceAddress(address: String?) {
        deviceAddress = address?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Clears latched session-loss / probe state when the dashboard is entered for a new pairing
     * (including [singleTop] [onNewIntent] reuse). Prevents stale observers after OS "Can't connect"
     * and Back → re-pair.
     */
    fun beginDashboardSession(address: String?) {
        stopForegroundSessionProbe()
        bondLifecycleSentinel.clear()
        sessionLossEmitted = false
        hadReachedLive = false
        linkUp = false
        connectionPhase = DigitalKeyConnectionPhase.DISCONNECTED
        foregroundServiceSessionLost = false
        lastLiveTimestampMs = 0L
        lastDisconnectAfterLiveMs = 0L
        setDeviceAddress(address)
        while (_uiEvents.tryReceive().isSuccess) {
            // Drop stale one-shot UI events from the previous dashboard visit.
        }
        Timber.tag(TAG).d("Dashboard session reset for address=%s", deviceAddress)
    }

    /**
     * Re-attaches the ViewModel when the user returns to the dashboard without tearing down
     * the foreground BLE session (toolbar / system back, then demo → reflector again).
     */
    fun attachToExistingDashboardSession(address: String?) {
        stopForegroundSessionProbe()
        sessionLossEmitted = false
        bondLifecycleSentinel.clear()
        foregroundServiceSessionLost = false
        setDeviceAddress(address)
        Timber.tag(TAG).d("Attached to existing dashboard session for address=%s", deviceAddress)
    }

    fun updateConnectionSnapshot(
        hadReachedLive: Boolean,
        linkUp: Boolean,
        connectionPhase: DigitalKeyConnectionPhase,
        foregroundServiceSessionLost: Boolean,
    ) {
        val wasLive = this.hadReachedLive
        this.hadReachedLive = hadReachedLive || this.hadReachedLive
        this.linkUp = linkUp
        this.connectionPhase = connectionPhase
        this.foregroundServiceSessionLost = foregroundServiceSessionLost

        if (connectionPhase == DigitalKeyConnectionPhase.LIVE) {
            lastLiveTimestampMs = System.currentTimeMillis()
            lastDisconnectAfterLiveMs = 0L
        }

        if (wasLive || this.hadReachedLive) {
            if (!linkUp && lastDisconnectAfterLiveMs == 0L) {
                lastDisconnectAfterLiveMs = System.currentTimeMillis()
            } else if (linkUp) {
                lastDisconnectAfterLiveMs = 0L
            }
        }
    }

    fun onLinkDisconnectedAfterLive() {
        if (!hadReachedLive) return
        if (lastDisconnectAfterLiveMs == 0L) {
            lastDisconnectAfterLiveMs = System.currentTimeMillis()
        }
    }

    fun recordPauseCheckpoint() {
        bondLifecycleSentinel.recordPauseCheckpoint(
            deviceAddress = deviceAddress,
            hadLiveSession = hadReachedLive,
            foregroundServiceSessionLost = foregroundServiceSessionLost,
        )
    }

    fun clearPauseCheckpoint() {
        bondLifecycleSentinel.clear()
    }

    /**
     * Lifecycle pause/resume probe (existing sentinel rules).
     * @return true when a session-lost event was emitted.
     */
    fun evaluateAtLifecycle(trigger: ReflectorBondLifecycleSentinel.LifecycleTrigger): Boolean {
        return evaluateSessionLoss(trigger)
    }

    fun startForegroundSessionProbe() {
        foregroundProbeJob?.cancel()
        foregroundProbeJob = viewModelScope.launch {
            delay(RESUME_PROBE_INITIAL_DELAY_MS)
            while (isActive) {
                if (evaluateSessionLoss(ReflectorBondLifecycleSentinel.LifecycleTrigger.FOREGROUND_POLL)) {
                    break
                }
                delay(FOREGROUND_PROBE_INTERVAL_MS)
            }
        }
    }

    fun stopForegroundSessionProbe() {
        foregroundProbeJob?.cancel()
        foregroundProbeJob = null
    }

    fun clearAllStoredPreferences() {
        ReflectorSessionPreferences.clearPairedDevice(getApplication())
    }

    fun markSessionLossHandled() {
        sessionLossEmitted = true
        stopForegroundSessionProbe()
    }

    private fun evaluateSessionLoss(
        trigger: ReflectorBondLifecycleSentinel.LifecycleTrigger,
    ): Boolean {
        // Latch blocks duplicate dialogs only; do not short-circuit resume / foreground probe.
        if (sessionLossEmitted) return false

        if (detectOsCantConnectReturn()) {
            emitSessionLost("osCantConnectReturn")
            return true
        }

        val verdict = bondLifecycleSentinel.evaluate(
            deviceAddress = deviceAddress,
            hadLiveSession = hadReachedLive,
            foregroundServiceSessionLost = foregroundServiceSessionLost,
            trigger = trigger,
            connectionPhase = connectionPhase,
            linkUp = linkUp,
            lastLiveTimestampMs = lastLiveTimestampMs,
            lastDisconnectAfterLiveMs = lastDisconnectAfterLiveMs,
        )

        return when (verdict) {
            ReflectorBondLifecycleSentinel.SessionLossVerdict.None -> false
            ReflectorBondLifecycleSentinel.SessionLossVerdict.OsBondRemoved -> {
                emitSessionLost("osBondRemovedAt$trigger")
                true
            }
            ReflectorBondLifecycleSentinel.SessionLossVerdict.BackgroundSessionLost -> {
                emitSessionLost("backgroundSessionLostAt$trigger")
                true
            }
            ReflectorBondLifecycleSentinel.SessionLossVerdict.BoardResetLikely -> {
                emitSessionLost("boardResetLikelyAt$trigger")
                true
            }
        }
    }

    /**
     * After the board RESET button, Android often shows a system "Can't connect" dialog.
     * Dismissing it with Close returns to the app while the bond may still exist and the
     * foreground service is still auto-reconnecting — no [notifySessionLost] yet.
     */
    private fun detectOsCantConnectReturn(): Boolean {
        if (!hadReachedLive || sessionLossEmitted) return false
        if (foregroundServiceSessionLost && hadReachedLive && !linkUp) return true

        if (!linkUp && connectionPhase == DigitalKeyConnectionPhase.SETUP_FAILED) {
            return true
        }

        val disconnectMs = lastDisconnectAfterLiveMs
        if (disconnectMs <= 0L) return false
        val elapsed = System.currentTimeMillis() - disconnectMs

        if (!linkUp && elapsed >= OS_CANT_CONNECT_STALL_MS) {
            when (connectionPhase) {
                DigitalKeyConnectionPhase.DISCOVERING,
                DigitalKeyConnectionPhase.BONDING,
                DigitalKeyConnectionPhase.ENABLING_NOTIFICATIONS,
                DigitalKeyConnectionPhase.STARTING_RANGING -> return true
                DigitalKeyConnectionPhase.RECONNECTING -> {
                    // Distinguish brief range-loss reconnect from stale bond after board reset.
                    if (elapsed >= OS_CANT_CONNECT_RECONNECTING_MS) return true
                }
                else -> Unit
            }
        }
        return false
    }

    private fun emitSessionLost(reason: String) {
        if (sessionLossEmitted) return
        sessionLossEmitted = true
        stopForegroundSessionProbe()
        viewModelScope.launch {
            _uiEvents.send(ReflectorDashboardUiEvent.ShowSessionLostDialog(reason))
        }
    }

    class Factory(
        private val application: Application,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ReflectorDashboardViewModel::class.java)) {
                return ReflectorDashboardViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    companion object {
        private const val TAG = "ReflectorDashboardVM"
        private const val RESUME_PROBE_INITIAL_DELAY_MS = 400L
        private const val FOREGROUND_PROBE_INTERVAL_MS = 2_000L
        /** GATT encrypted setup stalls quickly after board reset + OS "Can't connect". */
        private const val OS_CANT_CONNECT_STALL_MS = 6_000L
        private const val OS_CANT_CONNECT_RECONNECTING_MS = 15_000L
    }
}
