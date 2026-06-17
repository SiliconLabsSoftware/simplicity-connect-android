package com.siliconlabs.bledemo.features.demo.smartlock.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Coalesces rapid state updates and ignores conflicting incoming updates for a short
 * period after an explicit user command.
 */
class DebouncedUpdater<T>(
    private val scope: CoroutineScope,
    private val debounceMs: Long,
    private val commandSuppressMs: Long,
) {
    private var debounceJob: Job? = null
    private var suppressIncomingUntilMs = 0L
    private var pendingValue: T? = null
    private var lastApplied: T? = null

    fun applyUserCommand(value: T, apply: (T) -> Unit) {
        suppressIncomingUntilMs = System.currentTimeMillis() + commandSuppressMs
        lastApplied = value
        debounceJob?.cancel()
        pendingValue = null
        apply(value)
    }

    fun scheduleIncoming(value: T, apply: (T) -> Unit) {
        val now = System.currentTimeMillis()
        if (now < suppressIncomingUntilMs && value != lastApplied) {
            return
        }
        pendingValue = value
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(debounceMs)
            if (System.currentTimeMillis() < suppressIncomingUntilMs &&
                pendingValue != lastApplied
            ) {
                return@launch
            }
            val toApply = pendingValue ?: return@launch
            lastApplied = toApply
            apply(toApply)
        }
    }

    fun reset() {
        debounceJob?.cancel()
        debounceJob = null
        suppressIncomingUntilMs = 0L
        pendingValue = null
        lastApplied = null
    }

    fun cancel() {
        debounceJob?.cancel()
        debounceJob = null
        pendingValue = null
    }
}
