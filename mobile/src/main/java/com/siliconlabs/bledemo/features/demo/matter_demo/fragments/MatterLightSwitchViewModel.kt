package com.siliconlabs.bledemo.features.demo.matter_demo.fragments

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.siliconlabs.bledemo.features.demo.matter_demo.model.MatterScannedResultModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MatterLightSwitchViewModel : ViewModel() {

    private val _deviceStates = MutableLiveData<List<MatterScannedResultModel>>()
    val deviceStates: LiveData<List<MatterScannedResultModel>> = _deviceStates

    fun updateDeviceStates(updated: List<MatterScannedResultModel>) {
        viewModelScope.launch {
            withContext(Dispatchers.Main){
                _deviceStates.value = updated
            }
        }
    }

    fun updateDeviceBindingState(name: String, inProgress: Boolean, success: Boolean = false) {
        viewModelScope.launch {
            withContext(Dispatchers.Main){
                _deviceStates.value = _deviceStates.value?.map {
                    if (it.matterName == name) {
                        it.copy(isBindingInProgress = inProgress, isBindingSuccessful = success)
                    } else it.copy(isBindingInProgress = false) // Reset others
                }
            }
        }

    }

    private val _bindingStatusText = MutableLiveData<String>()
    val bindingStatusText: LiveData<String> = _bindingStatusText

    fun setBindingStatus(message: String) {
        viewModelScope.launch {
            withContext(Dispatchers.Main){
                _bindingStatusText.value = message
            }
        }
    }

    private val _aclWriteError = MutableLiveData<String>()
    val aclWriteError: LiveData<String> = _aclWriteError

    fun setACLWriteError(message: String){
        viewModelScope.launch {
            withContext(Dispatchers.Main){
                _aclWriteError.value = message
            }
        }
    }

    fun updateAclWriteProgress(name: String, inProgress: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.Main){
                _deviceStates.value = _deviceStates.value?.map {
                    if (it.matterName == name) {
                        it.copy(isAclWriteInProgress = inProgress)
                    } else it
                }
            }
        }

    }

    private val _unbindEnableDeviceName = MutableLiveData<String?>()
    val unbindEnableDeviceName: LiveData<String?> = _unbindEnableDeviceName

    fun enableUnbindForDevice(name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.Main){
                _unbindEnableDeviceName.value = name
            }
        }

    }

    fun setUnbindingState(name: String, inProgress: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.Main){
                _deviceStates.value = _deviceStates.value?.map {
                    if (it.matterName == name) {
                        it.copy(
                            isUnbindingInProgress = inProgress,
                            isBindingInProgress = false,
                            isAclWriteInProgress = false,
                            isBindingSuccessful = if (!inProgress) false else it.isBindingSuccessful
                        )
                    } else it
                }
            }
        }

    }

    /** Clears unbind-in-progress without changing binding success (e.g. unbind read/write failed). */
    fun cancelUnbindingInProgress(name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                _deviceStates.value = _deviceStates.value?.map {
                    if (it.matterName == name) {
                        it.copy(isUnbindingInProgress = false)
                    } else {
                        it
                    }
                }
            }
        }
    }

    private val _selectedLightNameForBinding = MutableLiveData<String?>(null)
    val selectedLightNameForBinding: LiveData<String?> = _selectedLightNameForBinding

    fun selectLightForBinding(matterName: String?) {
        viewModelScope.launch {
            withContext(Dispatchers.Main) {
                _selectedLightNameForBinding.value = matterName
            }
        }
    }

    fun resetDeviceStates() {
        viewModelScope.launch {
            withContext(Dispatchers.Main){
                _deviceStates.value = _deviceStates.value?.map { it.copy(isBindingInProgress = false, isBindingSuccessful = false,
                    isUnbindingInProgress = false, isAclWriteInProgress = false) }
                _selectedLightNameForBinding.value = null
            }
        }

    }

}