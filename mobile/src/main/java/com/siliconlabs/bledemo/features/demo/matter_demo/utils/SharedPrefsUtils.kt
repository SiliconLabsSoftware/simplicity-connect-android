package com.siliconlabs.bledemo.features.demo.matter_demo.utils

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.siliconlabs.bledemo.features.demo.matter_demo.dishwasher_demo.utils.DishWasherEnumConstants
import com.siliconlabs.bledemo.features.demo.matter_demo.model.MatterScannedResultModel

object SharedPrefsUtils {
    fun retrieveSavedDevices(mPrefs: SharedPreferences): ArrayList<MatterScannedResultModel> {
        val json = mPrefs.getString("addedDeviceInfoList", null)
        val gson = Gson()

        val myListType = object : TypeToken<ArrayList<MatterScannedResultModel>>() {}.type
        val myList = gson.fromJson<ArrayList<MatterScannedResultModel>>(json, myListType)

        return myList ?: ArrayList()
    }

    fun saveDevicesToPref(
        mPrefs: SharedPreferences,
        scannedDeviceList: ArrayList<MatterScannedResultModel>
    ) {
        val prefsEditor: SharedPreferences.Editor = mPrefs.edit()
        val cleanedList = scannedDeviceList.map {
            it.copy(isAclWriteInProgress = false, isBindingInProgress = false)
        }
        val gson = Gson()
        val json = gson.toJson(cleanedList)
        prefsEditor.putString(ARG_ADDED_DEVICE_INFO_LIST, json)
        prefsEditor.apply()
    }

    fun updateDeviceAtIndex(
        mPrefs: SharedPreferences,
        index: Int,
        updatedDevice: MatterScannedResultModel
    ) {
        val existingList = retrieveSavedDevices(mPrefs)
        if (index >= 0 && index < existingList.size) {
            existingList[index] = updatedDevice
            saveDevicesToPref(mPrefs, existingList)
        }
    }

    fun updateDeviceByDeviceId(mPrefs: SharedPreferences, deviceId: Long, newValue: Boolean) {
        val deviceList = retrieveSavedDevices(mPrefs)
        var updated = false

        for (device in deviceList) {
            if (device.deviceId == deviceId) { // Convert Long to String
                device.isDeviceOnline = newValue
                updated = true
                break
            }
        }

        if (updated) {
            saveDevicesToPref(mPrefs, deviceList)
        }
    }

    //Total energy
    fun saveDishwasherTotalEnergyConsumption(
        mPrefs: SharedPreferences,
        totalEnergyConsumed: Float
    ) {
        val prefsEditor: SharedPreferences.Editor = mPrefs.edit()
        prefsEditor.apply {
            putFloat(TOTAL_ENERGY_DISHWASHER, totalEnergyConsumed)
            apply()
        }

    }

    fun getDishwasherTotalEnergyConsumption(mPrefs: SharedPreferences): Float {

        return mPrefs.getFloat(TOTAL_ENERGY_DISHWASHER, 0f)
    }

    //average ener
    fun saveDishwasherAverageEnergyPerCycle(
        mPrefs: SharedPreferences,
        averageEnergyPerCycleConsumed: Float
    ) {
        val prefsEditor: SharedPreferences.Editor = mPrefs.edit()
        prefsEditor.apply {
            putFloat(AVERAGE_ENERGY_PER_CYCLE_DISHWASHER, averageEnergyPerCycleConsumed)
            apply()
        }

    }

    fun getDishwasherAverageEnergyPerCycle(mPrefs: SharedPreferences): Float{

        return mPrefs.getFloat(AVERAGE_ENERGY_PER_CYCLE_DISHWASHER, 0f)
    }

    //inCurrent cycle
    fun saveDishwasherInCurrentCycleEnergyConsumed(
        mPrefs: SharedPreferences,
        inCurrentCycleEnergyConsumed: Float
    ) {
        val prefsEditor: SharedPreferences.Editor = mPrefs.edit()
        prefsEditor.apply {
            putFloat(DISHWASHER_IN_CURRENT_CYCLE_CONSUMED, inCurrentCycleEnergyConsumed)
            apply()
        }
    }

    fun getDishwasherInCurrentCycleEnergyConsumed(mPrefs: SharedPreferences): Float {

        return mPrefs.getFloat(DISHWASHER_IN_CURRENT_CYCLE_CONSUMED, 0f)
    }

    //completedCycleCount
    fun saveDishwasherCompletedCycleCount(
        mPrefs: SharedPreferences,
        completedCycleCount: Int
    ) {
        val prefsEditor: SharedPreferences.Editor = mPrefs.edit()
        prefsEditor.apply {
            putInt(DISHWASHER_COMPLETED_CYCLE_COUNT, completedCycleCount)
            apply()
        }
    }

    fun getDishwasherCompletedCycleCount(mPrefs: SharedPreferences): Int {

        return mPrefs.getInt(DISHWASHER_COMPLETED_CYCLE_COUNT, 0)
    }


    fun saveDishwasherTimeLeftFormatted(mPrefs: SharedPreferences, timeLeftInMillSeconds: Long) {
        val prefsEditor: SharedPreferences.Editor = mPrefs.edit()
        prefsEditor.apply {
            putLong(DISHWASHER_TOTAL_TIME_LEFT, timeLeftInMillSeconds)
            apply()
        }
    }


    fun getDishwasherTimeLeftFormatted(mPrefs: SharedPreferences): Long {
        return mPrefs.getLong(DISHWASHER_TOTAL_TIME_LEFT, 600000)
    }

    fun saveDishwasherAppliedCycleStates(mPrefs: SharedPreferences, cycleStates: DishWasherEnumConstants) {
        val prefsEditor: SharedPreferences.Editor = mPrefs.edit()
        prefsEditor.apply {
            putString(
                DISHWASHER_CYCLE_STATES,
                cycleStates.toString()
            )
            apply()
        }
    }

    fun getDishwasherAppliedCycleStates(mPrefs: SharedPreferences): DishWasherEnumConstants? {
        val cycleStateString = mPrefs.getString(DISHWASHER_CYCLE_STATES, null)
        return if(null != cycleStateString){
            DishWasherEnumConstants.valueOf(cycleStateString)
        }else{
            null
        }
    }


    //SAVE progressBar
    fun saveDishwasherCompletedCycleProgressBar(
        mPrefs: SharedPreferences,
        completedProgressBarCount: Int
    ) {
        val prefsEditor: SharedPreferences.Editor = mPrefs.edit()
        prefsEditor.apply {
            putInt(DISHWASHER_COMPLETED_PROGRESS_BAR_COUNT, completedProgressBarCount)
            apply()
        }
    }

    fun getDishwasherCompletedCycleProgressBar(mPrefs: SharedPreferences): Int {
        return mPrefs.getInt(DISHWASHER_COMPLETED_PROGRESS_BAR_COUNT, 0)
    }

    fun clearDishwasherSharedPreferences(preferences: SharedPreferences) {
        preferences.edit().apply {
            clear()  // Clears all data in the SharedPreferences
            apply()  // Commit the changes asynchronously
        }
    }




    const val ARG_ADDED_DEVICE_INFO_LIST = "addedDeviceInfoList"
    const val TOTAL_ENERGY_DISHWASHER = "totalEnergyDishwasher"
    const val AVERAGE_ENERGY_PER_CYCLE_DISHWASHER = "averageEnergyPerCycle"
    const val DISHWASHER_IN_CURRENT_CYCLE_CONSUMED = "inCurrentCycle"
    const val DISHWASHER_COMPLETED_CYCLE_COUNT = "cycleCount"
    const val DISHWASHER_TOTAL_TIME_LEFT = "timeLeft"
    const val DISHWASHER_CYCLE_STATES = "dishwasherCycleStates"
    const val DISHWASHER_COMPLETED_PROGRESS_BAR_COUNT = "dishwasherCompletedProgressBarCount"
    const val DISHWASHER_ON_CYCLE_PAUSE_RESUME_STATE = "dishwasherOnPauseOrStopState"

    /** Prefix for Oven–RangeHood binding keys. Key = OVEN_RANGEHOOD_BINDING_PREFIX + ovenNodeId */
    const val OVEN_RANGEHOOD_BINDING_PREFIX = "OvenRangeHoodBinding_"

    /** Sentinel for "no binding" — getOvenRangeHoodBinding returns this when not bound. */
    const val OVEN_NO_RANGEHOOD_BINDING: Long = -1L

    /**
     * Save Oven–RangeHood binding. Persists across navigation.
     */
    fun saveOvenRangeHoodBinding(
        mPrefs: SharedPreferences,
        ovenNodeId: Long,
        rangeHoodNodeId: Long
    ) {
        mPrefs.edit().putLong(OVEN_RANGEHOOD_BINDING_PREFIX + ovenNodeId, rangeHoodNodeId).apply()
    }

    /**
     * Get bound RangeHood node ID for this Oven, or [OVEN_NO_RANGEHOOD_BINDING] if not bound.
     */
    fun getOvenRangeHoodBinding(mPrefs: SharedPreferences, ovenNodeId: Long): Long {
        val key = OVEN_RANGEHOOD_BINDING_PREFIX + ovenNodeId
        return if (mPrefs.contains(key)) mPrefs.getLong(key, OVEN_NO_RANGEHOOD_BINDING) else OVEN_NO_RANGEHOOD_BINDING
    }

    /**
     * Remove Oven–RangeHood binding.
     */
    fun removeOvenRangeHoodBinding(mPrefs: SharedPreferences, ovenNodeId: Long) {
        mPrefs.edit().remove(OVEN_RANGEHOOD_BINDING_PREFIX + ovenNodeId).apply()
    }

    /**
     * Whether this Oven is currently bound to a RangeHood (persisted).
     */
    fun isOvenBoundToRangeHood(mPrefs: SharedPreferences, ovenNodeId: Long): Boolean =
        getOvenRangeHoodBinding(mPrefs, ovenNodeId) != OVEN_NO_RANGEHOOD_BINDING

}