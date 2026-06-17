package com.siliconlabs.bledemo.features.demo.matter_demo.fragments

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MatterOvenViewModel : ViewModel() {

    private val _isOvenOn = MutableLiveData<Boolean>(false)
    val isOvenOn: LiveData<Boolean> = _isOvenOn

    private val _isRangeHoodLightOn = MutableLiveData<Boolean>(false)
    val isRangeHoodLightOn: LiveData<Boolean> = _isRangeHoodLightOn

    private val _isRangeHoodFanOn = MutableLiveData<Boolean>(false)
    val isRangeHoodFanOn: LiveData<Boolean> = _isRangeHoodFanOn

    fun setOvenOnStatus(isOn: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                _isOvenOn.value = isOn
            }
        }
    }

    fun getOvenOnStatus(): Boolean {
        return _isOvenOn.value ?: false
    }

    fun setRangeHoodLightStatus(isOn: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                _isRangeHoodLightOn.value = isOn
            }
        }
    }

    fun setRangeHoodFanStatus(isOn: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                _isRangeHoodFanOn.value = isOn
            }
        }
    }
}
