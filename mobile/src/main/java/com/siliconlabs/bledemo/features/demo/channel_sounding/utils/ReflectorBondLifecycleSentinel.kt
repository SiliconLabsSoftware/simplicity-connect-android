package com.siliconlabs.bledemo.features.demo.channel_sounding.utils

import com.siliconlabs.bledemo.features.demo.channel_sounding.services.DigitalKeyConnectionPhase

/**
 * Captures OS bond and foreground-service session state on pause, and evaluates whether the
 * reflector session was lost while the dashboard is backgrounded or after a board reset / bond
 * removal while the UI remains visible.
 *
 * Used from [com.siliconlabs.bledemo.features.demo.channel_sounding.activities.ReflectorDashboardActivity]
 * lifecycle hooks without altering foreground-service reconnect policy.
 */
class ReflectorBondLifecycleSentinel {

    enum class LifecycleTrigger {
        PAUSE,
        RESUME,
        /** Periodic probe while the dashboard is in the foreground (e.g. after OS "Can't connect"). */
        FOREGROUND_POLL,
    }

    sealed class SessionLossVerdict {
        data object None : SessionLossVerdict()
        data object OsBondRemoved : SessionLossVerdict()
        data object BackgroundSessionLost : SessionLossVerdict()
        /** GATT pipeline failed to restore after LIVE — typical of a board reset with stale bond. */
        data object BoardResetLikely : SessionLossVerdict()
    }

    private data class PauseCheckpoint(
        val deviceAddress: String,
        val osBondPresent: Boolean,
        val hadLiveSession: Boolean,
        val foregroundServiceSessionLost: Boolean,
    )

    private var pauseCheckpoint: PauseCheckpoint? = null

    fun recordPauseCheckpoint(
        deviceAddress: String?,
        hadLiveSession: Boolean,
        foregroundServiceSessionLost: Boolean,
    ) {
        val address = deviceAddress?.takeIf { it.isNotBlank() } ?: run {
            pauseCheckpoint = null
            return
        }
        pauseCheckpoint = PauseCheckpoint(
            deviceAddress = address,
            osBondPresent = ReflectorBondUtils.isDeviceBonded(address),
            hadLiveSession = hadLiveSession,
            foregroundServiceSessionLost = foregroundServiceSessionLost,
        )
    }

    fun evaluate(
        deviceAddress: String?,
        hadLiveSession: Boolean,
        foregroundServiceSessionLost: Boolean,
        trigger: LifecycleTrigger,
        connectionPhase: DigitalKeyConnectionPhase = DigitalKeyConnectionPhase.DISCONNECTED,
        linkUp: Boolean = false,
        lastLiveTimestampMs: Long = 0L,
        lastDisconnectAfterLiveMs: Long = 0L,
        nowMs: Long = System.currentTimeMillis(),
    ): SessionLossVerdict {
        val address = deviceAddress?.takeIf { it.isNotBlank() } ?: return SessionLossVerdict.None
        val osBondPresent = ReflectorBondUtils.isDeviceBonded(address)

        if (foregroundServiceSessionLost && hadLiveSession) {
            return SessionLossVerdict.BackgroundSessionLost
        }

        if (!osBondPresent) {
            val hadBondAtPause = pauseCheckpoint
                ?.takeIf { it.deviceAddress.equals(address, ignoreCase = true) }
                ?.osBondPresent == true
            if (hadBondAtPause || hadLiveSession || trigger == LifecycleTrigger.PAUSE) {
                return SessionLossVerdict.OsBondRemoved
            }
        }

        if (trigger == LifecycleTrigger.RESUME || trigger == LifecycleTrigger.FOREGROUND_POLL) {
            val prior = pauseCheckpoint?.takeIf {
                it.deviceAddress.equals(address, ignoreCase = true)
            }
            if (prior != null && prior.osBondPresent && !osBondPresent) {
                return SessionLossVerdict.OsBondRemoved
            }
            if (prior != null && prior.hadLiveSession && !prior.foregroundServiceSessionLost &&
                foregroundServiceSessionLost
            ) {
                return SessionLossVerdict.BackgroundSessionLost
            }
        }

        val setupFailedThresholdMs = if (trigger == LifecycleTrigger.FOREGROUND_POLL) {
            SETUP_FAILED_AFTER_LIVE_FOREGROUND_MS
        } else {
            SETUP_FAILED_AFTER_LIVE_MS
        }
        val pipelineStallThresholdMs = if (trigger == LifecycleTrigger.FOREGROUND_POLL) {
            PIPELINE_STALL_AFTER_LIVE_FOREGROUND_MS
        } else {
            PIPELINE_STALL_AFTER_LIVE_MS
        }

        if (hadLiveSession && lastDisconnectAfterLiveMs > 0L &&
            connectionPhase != DigitalKeyConnectionPhase.LIVE
        ) {
            val elapsedSinceDisconnectMs = nowMs - lastDisconnectAfterLiveMs
            if (elapsedSinceDisconnectMs >= POST_LIVE_DISCONNECT_MS) {
                return SessionLossVerdict.BoardResetLikely
            }
        }

        if (hadLiveSession && !linkUp && connectionPhase == DigitalKeyConnectionPhase.LIVE &&
            lastDisconnectAfterLiveMs > 0L &&
            nowMs - lastDisconnectAfterLiveMs >= PHASE_LINK_DESYNC_MS
        ) {
            return SessionLossVerdict.BoardResetLikely
        }

        if (hadLiveSession && lastLiveTimestampMs > 0L) {
            val elapsedSinceLiveMs = nowMs - lastLiveTimestampMs
            if (connectionPhase != DigitalKeyConnectionPhase.LIVE) {
                when (connectionPhase) {
                    DigitalKeyConnectionPhase.SETUP_FAILED -> {
                        if (elapsedSinceLiveMs >= setupFailedThresholdMs) {
                            return SessionLossVerdict.BoardResetLikely
                        }
                    }
                    DigitalKeyConnectionPhase.RECONNECTING,
                    DigitalKeyConnectionPhase.DISCONNECTED -> {
                        if (!linkUp && elapsedSinceLiveMs >= NON_LIVE_RECONNECTING_MS) {
                            return SessionLossVerdict.BoardResetLikely
                        }
                    }
                    DigitalKeyConnectionPhase.DISCOVERING,
                    DigitalKeyConnectionPhase.BONDING,
                    DigitalKeyConnectionPhase.ENABLING_NOTIFICATIONS,
                    DigitalKeyConnectionPhase.STARTING_RANGING -> {
                        if (elapsedSinceLiveMs >= pipelineStallThresholdMs) {
                            return SessionLossVerdict.BoardResetLikely
                        }
                    }
                    else -> Unit
                }
            }
        }

        return SessionLossVerdict.None
    }

    fun clear() {
        pauseCheckpoint = null
    }

    companion object {
        const val POST_LIVE_DISCONNECT_MS = 10_000L
        private const val PHASE_LINK_DESYNC_MS = 2_000L
        private const val SETUP_FAILED_AFTER_LIVE_MS = 5_000L
        private const val SETUP_FAILED_AFTER_LIVE_FOREGROUND_MS = 1_500L
        private const val PIPELINE_STALL_AFTER_LIVE_MS = 12_000L
        private const val PIPELINE_STALL_AFTER_LIVE_FOREGROUND_MS = 6_000L
        private const val NON_LIVE_RECONNECTING_MS = 45_000L
    }
}
