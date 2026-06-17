package com.siliconlabs.bledemo.features.demo.smartlock.viewmodels

import android.bluetooth.BluetoothGattCharacteristic
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siliconlabs.bledemo.features.demo.smartlock.models.LockState
import com.siliconlabs.bledemo.features.demo.smartlock.models.LockUIState
import com.siliconlabs.bledemo.features.demo.smartlock.utils.DebouncedUpdater
import kotlinx.coroutines.launch

class SmartLockBleViewModel : ViewModel() {
    private val lockUiDebouncer = DebouncedUpdater<LockUIState>(
        viewModelScope,
        LOCK_UI_DEBOUNCE_MS,
        LOCK_COMMAND_SUPPRESS_MS
    )
    private val _lockState: MutableLiveData<LockState> = MutableLiveData(LockState.UNKNOWN)
    val lockState: MutableLiveData<LockState> = _lockState

    private val _lockUIState: MutableLiveData<LockUIState> = MutableLiveData(LockUIState.UNKNOWN)
    val lockUIState: MutableLiveData<LockUIState> = _lockUIState

    private val _isLockStateChanged: MutableLiveData<Boolean> = MutableLiveData(false)
    val isLockStateChanged: MutableLiveData<Boolean> = _isLockStateChanged

    fun handleLockStateChanges(characteristic: BluetoothGattCharacteristic) {
        val valueArray = characteristic.value
        if (valueArray == null || valueArray.isEmpty()) {
            println("SmartLockBleViewModel: Empty value received for lock state changes")
            return
        }
        // New protocol: single-byte value where 0x00 = LOCKED, 0x01 = UNLOCKED
        val value = valueArray[0].toInt()
        when (value) {
            0 -> _lockState.postValue(LockState.LOCKED)
            1 -> _lockState.postValue(LockState.UNLOCKED)
            else -> {
                println("SmartLockBleViewModel: Unknown lock state byte: $value")
                _lockState.postValue(LockState.UNKNOWN)
            }
        }
    }


    fun handleReadLockState(characteristic: BluetoothGattCharacteristic?) {
        val valueArray = characteristic?.value
        if (valueArray == null || valueArray.isEmpty()) {
            println("SmartLockActivity: Characteristic is null or empty, cannot read lock state.")
            postLockUiStateDebounced(LockUIState.UNKNOWN)
            return
        }

        val state = parseLockUiState(valueArray)
        if (state == null) {
            val value0 = valueArray.getOrNull(0)?.toInt()
            val value1 = valueArray.getOrNull(1)?.toInt()
            val value2 = valueArray.getOrNull(2)?.toInt()
            println(
                "SmartLockActivity: Unrecognized lock state bytes: v0=$value0 v1=$value1 v2=$value2 size=${valueArray.size}"
            )
            postLockUiStateDebounced(LockUIState.UNKNOWN)
            return
        }
        postLockUiStateDebounced(state)
    }

    private fun parseLockUiState(valueArray: ByteArray): LockUIState? {
        if (valueArray.size == 1) {
            val single = valueArray[0].toInt()
            println("SmartLockActivity: Single-byte lock state value: $single")
            return when (single) {
                0 -> LockUIState.LOCKED
                1 -> LockUIState.UNLOCKED
                else -> null
            }
        }
        val value0 = valueArray.getOrNull(0)?.toInt()
        val value1 = valueArray.getOrNull(1)?.toInt()
        val value2 = valueArray.getOrNull(2)?.toInt()
        println("SmartLockActivity: Legacy lock state bytes: v0=$value0 v1=$value1 v2=$value2 size=${valueArray.size}")
        return when {
            (value0 == 9 || value0 == 11) && value1 == 1 && value2 == 1 -> LockUIState.LOCKED
            (value0 == 9 || value0 == 11) && value1 == 1 && value2 == 0 -> LockUIState.UNLOCKED
            else -> null
        }
    }

    private fun postLockUiStateDebounced(state: LockUIState) {
        lockUiDebouncer.scheduleIncoming(state) { _lockUIState.postValue(it) }
    }


    fun handleButtonStateChanges(characteristic: BluetoothGattCharacteristic) {
        val result = characteristic.value[0].toInt()
        _isLockStateChanged.postValue(result == 1)
    }


    fun lockState() {
        viewModelScope.launch {
            try {
                lockUiDebouncer.applyUserCommand(LockUIState.LOCKED) { _lockUIState.postValue(it) }
                _lockState.postValue(LockState.LOCKED)
            } catch (e: Exception) {
                _lockState.postValue(LockState.UNKNOWN)
                println("SmartLockActivity: Error locking state: ${e.message}")
            }
        }
    }

    fun unLockState() {
        viewModelScope.launch {
            try {
                lockUiDebouncer.applyUserCommand(LockUIState.UNLOCKED) { _lockUIState.postValue(it) }
                _lockState.postValue(LockState.UNLOCKED)
            } catch (e: Exception) {
                _lockState.postValue(LockState.UNKNOWN)
                println("SmartLockActivity: Error locking state: ${e.message}")
            }
        }
    }

    fun queryLockState() {
        _lockState.postValue(LockState.UNKNOWN)
    }

    fun resetDebouncers() {
        lockUiDebouncer.reset()
    }

    companion object {
        private const val LOCK_UI_DEBOUNCE_MS = 400L
        private const val LOCK_COMMAND_SUPPRESS_MS = 1200L
    }
}