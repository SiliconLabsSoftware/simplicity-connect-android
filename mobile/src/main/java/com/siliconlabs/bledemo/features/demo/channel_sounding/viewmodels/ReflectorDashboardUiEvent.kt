package com.siliconlabs.bledemo.features.demo.channel_sounding.viewmodels

/**
 * One-shot UI events for [ReflectorDashboardActivity] (session-lost dialog, etc.).
 */
sealed class ReflectorDashboardUiEvent {
    data class ShowSessionLostDialog(val reason: String) : ReflectorDashboardUiEvent()
}
