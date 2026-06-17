package com.siliconlabs.bledemo.features.configure.gatt_configurator.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.siliconlabs.bledemo.features.configure.gatt_configurator.models.GattServer
import com.siliconlabs.bledemo.features.configure.gatt_configurator.utils.GattConfiguratorStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class GattConfiguratorViewModel @Inject constructor(val gattConfiguratorStorage: GattConfiguratorStorage) : ViewModel() {
    private val _gattServers: MutableLiveData<ArrayList<GattServer>> = MutableLiveData(gattConfiguratorStorage.loadGattServerList())
    val gattServers: LiveData<ArrayList<GattServer>> = _gattServers
    private val _removedPosition: MutableLiveData<Int> = MutableLiveData()
    val removedPosition: LiveData<Int> = _removedPosition
    private val _insertedPosition: MutableLiveData<Int> = MutableLiveData()
    val insertedPosition: LiveData<Int> = _insertedPosition
    private val _changedPosition: MutableLiveData<Int> = MutableLiveData()
    val changedPosition = _changedPosition
    private val _areAnyGattServers: MutableLiveData<Boolean> = MutableLiveData()
    val areAnyGattServers = _areAnyGattServers
    private val _switchedOffPosition: MutableLiveData<Int> = MutableLiveData()
    val switchedOffPosition: LiveData<Int> = _switchedOffPosition

    init {
        areAnyGattServers()
    }

    private fun areAnyGattServers() {
        _areAnyGattServers.value = _gattServers.value?.size!! > 0
    }

    fun createGattServer(gattServer: GattServer? = null) {
        _gattServers.value?.apply {
            gattServer?.let {
                 add(gattServer)
            } ?: add(GattServer("New GATT Server"))
            _insertedPosition.value = size - 1
        }
        areAnyGattServers()
        saveGattDatabase()
    }

    fun copyGattServer(gattServer: GattServer) {
        _gattServers.value?.apply {
            add(gattServer.deepCopy())
            _insertedPosition.value = size - 1
        }
        saveGattDatabase()
    }

    fun removeGattServerAt(position: Int) {
        _gattServers.value?.apply {
            removeAt(position)
            _removedPosition.value = position
        }
        areAnyGattServers()
        saveGattDatabase()
    }

    fun replaceGattServerAt(position: Int, gattServer: GattServer) {
        _gattServers.value?.apply {
            set(position, gattServer)
            _changedPosition.value = position
        }
        saveGattDatabase()
    }

    fun switchGattServerOnAt(position: Int) {
        _gattServers.value?.let { servers ->
            servers.forEachIndexed { index, server ->
                if (server.isSwitchedOn) {
                    server.isSwitchedOn = false
                    _switchedOffPosition.value = index
                }
            }
            servers[position].isSwitchedOn = true
            gattConfiguratorStorage.saveActiveGattServer(servers[position])
        }
        saveGattDatabase()
    }

    fun switchGattServerOffAt(position: Int) {
        _gattServers.value?.let { servers ->
            servers[position].isSwitchedOn = false
            _switchedOffPosition.value = position
            if (!isAnyGattServerSwitchedOn()) {
                gattConfiguratorStorage.saveActiveGattServer(null)
            }
        }
        saveGattDatabase()
    }

    fun isAnyGattServerSwitchedOn(): Boolean {
        _gattServers.value?.let { servers ->
            return servers.any { it.isSwitchedOn }
        }
        return false
    }

    fun isAnyGattServerCheckedForExport() : Boolean {
        _gattServers.value?.let { servers ->
            return servers.any { it.isCheckedForExport }
        }
        return false
    }

    private fun saveGattDatabase() {
        _gattServers.value?.apply {
            gattConfiguratorStorage.saveGattServerList(this)
        }
    }
}
