package com.siliconlabs.bledemo.features.scan.browser.adapters

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.ParcelUuid
import android.text.Html
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.bluetooth.beacon_utils.BleFormat
import com.siliconlabs.bledemo.bluetooth.beacon_utils.altbeacon.AltBeacon
import com.siliconlabs.bledemo.bluetooth.beacon_utils.eddystone.Beacon
import com.siliconlabs.bledemo.bluetooth.beacon_utils.eddystone.Constants
import com.siliconlabs.bledemo.bluetooth.beacon_utils.eddystone.TlmValidator
import com.siliconlabs.bledemo.bluetooth.beacon_utils.eddystone.UidValidator
import com.siliconlabs.bledemo.bluetooth.beacon_utils.eddystone.UrlValidator
import com.siliconlabs.bledemo.bluetooth.beacon_utils.ibeacon.IBeaconInfo
import com.siliconlabs.bledemo.bluetooth.ble.BluetoothDeviceInfo
import com.siliconlabs.bledemo.bluetooth.ble.ScanResultCompat
import com.siliconlabs.bledemo.bluetooth.parsing.ScanRecordParser
import com.siliconlabs.bledemo.common.views.DetailsRow
import com.siliconlabs.bledemo.databinding.AdapterBrowserDeviceBinding
import com.siliconlabs.bledemo.home_screen.viewmodels.ScanFragmentViewModel
import com.siliconlabs.bledemo.utils.BLEUtils
import com.siliconlabs.bledemo.utils.RecyclerViewUtils
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

class DebugModeDeviceAdapter(
    private var chosenDevices: MutableList<ScanFragmentViewModel.BluetoothInfoViewState>,
    private val debugModeCallback: DebugModeCallback
) : RecyclerView.Adapter<DebugModeDeviceAdapter.ViewHolder>() {

    private var itemPosition  = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val viewBinding = AdapterBrowserDeviceBinding.inflate(LayoutInflater.from(parent.context))
        return ViewHolder(parent.context, viewBinding).apply {
            setupUiListeners(this, viewBinding)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        itemPosition = position
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            holder.showChanges(payloads)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        itemPosition = position
        holder.bind(chosenDevices[position])
    }

    override fun getItemCount() = chosenDevices.size



    private fun setupUiListeners(holder: ViewHolder, viewBinding: AdapterBrowserDeviceBinding) {
        viewBinding.apply {
            expandArrow.setOnClickListener {
                println("SICONNECT")
                RecyclerViewUtils.withProperAdapterPosition(holder) { pos ->
                    debugModeCallback.toggleViewExpansion(pos)
                }
            }
            connectionBtn.setOnClickListener {
                BLEUtils.IS_FIRMWARE_REBOOTED = false
                RecyclerViewUtils.withProperAdapterPosition(holder) { pos ->
                    with(chosenDevices[pos].deviceInfo) {
                        when (connectionState) {
                            BluetoothDeviceInfo.ConnectionState.CONNECTED -> debugModeCallback.disconnectDevice(
                                pos,
                                this.device
                            )

                            BluetoothDeviceInfo.ConnectionState.DISCONNECTED -> debugModeCallback.connectToDevice(
                                pos,
                                this
                            )

                            else -> {}
                        }
                    }
                }
            }
            favoriteBtn.setOnClickListener {
                RecyclerViewUtils.withProperAdapterPosition(holder) { pos ->
                    val clickedDevice = chosenDevices[pos].deviceInfo
                    toggleFavoriteDevice(clickedDevice)
                }
            }
        }
    }

    fun addNewDevice(newDevice: ScanFragmentViewModel.BluetoothInfoViewState) {
        chosenDevices.add(newDevice)
        notifyItemInserted(itemCount - 1)
    }

    fun getItemPosition() = itemPosition

    fun updateDevices(
        newList: List<ScanFragmentViewModel.BluetoothInfoViewState>,
        withMoves: Boolean = false
    ) {
        val listDiff = DiffUtil.calculateDiff(
            DiffCallback(
                chosenDevices.toList(),
                newList
            ), withMoves
        )

        chosenDevices = getDeepCopyList(newList).toMutableList()
        listDiff.dispatchUpdatesTo(this)
    }

    private fun getDeepCopyList(list: List<ScanFragmentViewModel.BluetoothInfoViewState>): List<ScanFragmentViewModel.BluetoothInfoViewState> {
        return mutableListOf<ScanFragmentViewModel.BluetoothInfoViewState>().apply {
            list.forEach { add(it.copy(deviceInfo = it.deviceInfo.clone())) }
        }
    }

    private fun toggleFavoriteDevice(info: BluetoothDeviceInfo) {
        if (info.isFavorite) {
            debugModeCallback.removeFromFavorites(info.device.address)
        } else {
            debugModeCallback.addToFavorites(info.device.address)
        }
    }

    private class DiffCallback(
        private val oldList: List<ScanFragmentViewModel.BluetoothInfoViewState>,
        private val newList: List<ScanFragmentViewModel.BluetoothInfoViewState>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int {
            return oldList.size
        }

        override fun getNewListSize(): Int {
            return newList.size
        }

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].deviceInfo.address == newList[newItemPosition].deviceInfo.address
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            /* At least signal strength (RSSI) and its interval are very likely to change, so
            let's not waste time on checking it. */
            return false
        }

        override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any {
            return PayloadChange(oldList[oldItemPosition], newList[newItemPosition])
        }
    }

    private class PayloadChange(
        val oldItem: ScanFragmentViewModel.BluetoothInfoViewState,
        val newItem: ScanFragmentViewModel.BluetoothInfoViewState
    )

    private class AdvertisementData(val rows: List<Pair<String, String>>)

    @SuppressLint("MissingPermission")
    class ViewHolder(
        private val context: Context,
        private val viewBinding: AdapterBrowserDeviceBinding
    ) : RecyclerView.ViewHolder(viewBinding.root) {

        fun bind(viewInfoState: ScanFragmentViewModel.BluetoothInfoViewState) {
            val info = viewInfoState.deviceInfo
            refreshConnectionButtonState(info)

            viewBinding.apply {
                manufacturerIcon.setImageDrawable(getManufacturerIcon(info.manufacturer))
                deviceName.text =
                    if (info.name.isEmpty()) itemView.context.getString(R.string.not_advertising_shortcut)
                    else info.name
                tvDeviceAddress.text = context.getString(R.string.string_placeholder, info.address)

                rssi.text = itemView.context.getString(R.string.unit_value_dbm, info.rssi)
                tvInterval.text =
                    itemView.context.getString(R.string.unit_value_ms, info.intervalNanos / 1000000)
                deviceType.text =
                    itemView.context.getString(info.bleFormat?.nameResId ?: R.string.unspecified)
                tvIsConnectable.text = context.getString(
                    if (isEhSensor(info) || !info.isConnectable) R.string.non_connectible else R.string.connectible
                )
                favoriteBtn.isChecked = info.isFavorite
                tvIsBonded.text = getBondedStateText(info.device.bondState)
                toggleDetails(viewInfoState.isInfoExpanded)
            }
            addAdvertsDataToContainer(generateAdvertData(info))
        }

        fun showChanges(payloads: List<Any>) {
            val oldState = (payloads.first() as PayloadChange).oldItem
            val newState = (payloads.last() as PayloadChange).newItem
            val oldInfo = oldState.deviceInfo
            val newInfo = newState.deviceInfo

            viewBinding.apply {
                if (newState.isInfoExpanded != oldState.isInfoExpanded)
                    toggleDetails(shouldShowDetails = newState.isInfoExpanded)
                if (newInfo.name != oldInfo.name) {
                    deviceName.text = newInfo.name
                    tvIsConnectable.text = context.getString(
                        if (isEhSensor(newInfo) || !newInfo.isConnectable) R.string.non_connectible else R.string.connectible
                    )
                }
                if (newInfo.rssi != oldInfo.rssi)
                    rssi.text = context.getString(R.string.unit_value_dbm, newInfo.rssi)
                if (newInfo.intervalNanos != oldInfo.intervalNanos)
                    tvInterval.text =
                        context.getString(R.string.unit_value_ms, newInfo.intervalNanos / 1000000)
                if (newInfo.device.bondState != oldInfo.device.bondState)
                    tvIsBonded.text = getBondedStateText(newInfo.device.bondState)
                if (newInfo.connectionState != oldInfo.connectionState) {
                    refreshConnectionButtonState(newInfo)
                }
                if (newInfo.isConnectable != oldInfo.isConnectable) {
                    tvIsConnectable.text = context.getString(
                        if (isEhSensor(newInfo) || !newInfo.isConnectable) R.string.non_connectible else R.string.connectible
                    )
                }
                if (newInfo.isFavorite != oldInfo.isFavorite) {
                    favoriteBtn.isChecked = newInfo.isFavorite
                }
                if (oldInfo.rawData != newInfo.rawData) {
                    deviceType.text = itemView.context.getString(
                        newInfo.bleFormat?.nameResId ?: R.string.unspecified
                    )
                    manufacturerIcon.setImageDrawable(getManufacturerIcon(newInfo.manufacturer))
                    println("SiCONNECT --newState.isInfoExpanded:${newState.isInfoExpanded}")
                    if (newState.isInfoExpanded) addAdvertsDataToContainer(
                        generateAdvertData(
                            newInfo
                        )
                    )
                    // Raw data change might influence connectible display for EH Sensor devices.
                    tvIsConnectable.text = context.getString(
                        if (isEhSensor(newInfo) || !newInfo.isConnectable) R.string.non_connectible else R.string.connectible
                    )
                }
            }
        }

        private fun refreshConnectionButtonState(deviceInfo: BluetoothDeviceInfo) {
            // Hide connection button for EH Sensor devices regardless of reported connectibility.
            if (isEhSensor(deviceInfo)) {
                viewBinding.connectionBtn.visibility = View.GONE
                return
            }
            viewBinding.connectionBtn.apply {
                if (deviceInfo.isConnectable) {
                    visibility = View.VISIBLE
                    setActionButtonState(deviceInfo.connectionState)
                    text = context.getString(
                        when (deviceInfo.connectionState) {
                            BluetoothDeviceInfo.ConnectionState.CONNECTED -> R.string.button_disconnect
                            BluetoothDeviceInfo.ConnectionState.CONNECTING -> R.string.button_connecting
                            BluetoothDeviceInfo.ConnectionState.DISCONNECTED -> R.string.button_connect
                        }
                    )
                } else {
                    visibility = View.GONE
                }
            }
        }

        private fun toggleDetails(shouldShowDetails: Boolean) {
            viewBinding.advertisementContainer.visibility =
                if (shouldShowDetails) View.VISIBLE
                else View.GONE
            viewBinding.expandArrow.setState(shouldShowDetails)
        }

        private fun generateAdvertData(deviceInfo: BluetoothDeviceInfo): AdvertisementData {
            val rows: MutableList<Pair<String, String>> = ArrayList()

            if (!deviceInfo.scanInfo?.isLegacy!!) {
                rows.add(
                    Pair(
                        context.resources.getString(R.string.Bluetooth_5_Advertising_Extension),
                        prepareBluetooth5AdvertExtensionData(deviceInfo.scanInfo!!)
                    )
                )
            }

            deviceInfo.advertData.forEach {
                val data = it?.split(ScanRecordParser.SPLIT.toRegex())?.toTypedArray()!!
                val dataLabel = data[0]
                val dataValue =
                    if (data.size > 1) data[1]
                    else ""
                rows.add(Pair(dataLabel, dataValue))
            }

            when (deviceInfo.bleFormat) {
                BleFormat.I_BEACON -> iBeaconAdv(deviceInfo, rows)
                BleFormat.EDDYSTONE -> eddystoneAdv(deviceInfo, rows)
                BleFormat.ALT_BEACON -> altBeaconAdv(deviceInfo, rows)
                else -> {}
            }

            return AdvertisementData(rows)
        }

        private fun getManufacturerIcon(manufacturer: BluetoothDeviceInfo.DeviceManufacturer): Drawable? {
            return ContextCompat.getDrawable(
                context, when (manufacturer) {
                    BluetoothDeviceInfo.DeviceManufacturer.WINDOWS -> R.drawable.redesign_ic_scanned_device_windows
                    BluetoothDeviceInfo.DeviceManufacturer.ANDROID -> R.drawable.redesign_ic_scanned_device_android
                    BluetoothDeviceInfo.DeviceManufacturer.UNKNOWN -> R.drawable.redesign_ic_bluetooth_with_background
                }
            )
        }

        private fun getBondedStateText(bondState: Int): String {
            return context.getString(
                if (bondState == BluetoothDevice.BOND_BONDED) R.string.scanned_device_bonded
                else R.string.scanned_device_not_bonded
            )
        }

        private fun prepareBluetooth5AdvertExtensionData(scanResult: ScanResultCompat): String {
            return StringBuilder().apply {
                append(context.getString(R.string.Data_Status_colon))
                append(
                    context.getString(
                        if (scanResult.dataStatus == 0) R.string.advertising_extension_status_complete
                        else R.string.advertising_extension_status_truncated
                    )
                )
                append("<br/>")

                append(context.getString(R.string.Primary_PHY_colon))
                append(
                    context.getString(
                        if (scanResult.primaryPhy == 1) R.string.advertising_extension_phy_le_1m
                        else R.string.advertising_extension_phy_le_coded
                    )
                )
                append("<br/>")

                append(context.getString(R.string.Secondary_PHY_colon))
                append(
                    context.getString(
                        when (scanResult.secondaryPhy) {
                            1 -> R.string.advertising_extension_phy_le_1m
                            2 -> R.string.advertising_extension_phy_le_2m
                            3 -> R.string.advertising_extension_phy_le_coded
                            else -> R.string.advertising_extension_phy_unused
                        }
                    )
                )
                append("<br/>")

                append(context.getString(R.string.Advertising_Set_ID))
                if (scanResult.advertisingSetID == 255) append(context.getString(R.string.advertising_extension_not_present))
                else append(scanResult.advertisingSetID)
                append("<br/>")

                append(context.getString(R.string.Tx_Power))
                if (scanResult.txPower == 127) append(context.getString(R.string.advertising_extension_not_present))
                else append("${scanResult.txPower} dBm")
                append("<br/>")

                append(context.getString(R.string.Periodic_Advertising_Interval_colon))
                if (scanResult.periodicAdvertisingInterval in 6..65536) {
                    val ms = scanResult.periodicAdvertisingInterval * 1.25
                    append("$ms ms")
                    Log.e("DebugModeDeviceAdapterXXX","$ms ms")
                } else {
                    append(context.getString(R.string.advertising_extension_not_present))
                }
            }.toString()
        }

        private fun validateEddyStoneServiceData(
            beacon: Beacon,
            deviceAddress: String,
            serviceData: ByteArray?
        ) {
            if (serviceData == null) {
                val err = "Null Eddystone service data"
                beacon.frameStatus.nullServiceData = err
                return
            }
            when (serviceData[0]) {
                Constants.UID_FRAME_TYPE -> UidValidator.validate(
                    deviceAddress,
                    serviceData,
                    beacon
                )

                Constants.TLM_FRAME_TYPE -> TlmValidator.validate(
                    deviceAddress,
                    serviceData,
                    beacon
                )

                Constants.URL_FRAME_TYPE -> UrlValidator.validate(
                    deviceAddress,
                    serviceData,
                    beacon
                )

                else -> {
                    val err = String.format("Invalid frame type byte %02X", serviceData[0])
                    beacon.frameStatus.invalidFrameType = err
                }
            }
        }

        private fun addAdvertsDataToContainer(advertisementData: AdvertisementData) {
            viewBinding.advertisementContainer.removeAllViews()
            advertisementData.rows.forEach {
                val title = it.first
                val text = Html.fromHtml(it.second).toString()
                val serviceItemContainer = DetailsRow(context, title, text)
                viewBinding.advertisementContainer.addView(serviceItemContainer.binding.root,
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
        }

        private fun iBeaconAdv(
            deviceInfo: BluetoothDeviceInfo,
            rows: MutableList<Pair<String, String>>
        ) {
            IBeaconInfo.getIBeaconInfo(deviceInfo.scanInfo?.scanRecord?.bytes!!)
                ?.let { beaconInfo ->
                    val info = StringBuilder().apply {
                        append("Minor: ").append(beaconInfo.minor).append("<br>")
                        append("Major: ").append(beaconInfo.major).append("<br>")
                        append("UUID: ").append(beaconInfo.uuid).append("<br>")
                        append("RSSI at 1m: ").append(beaconInfo.power)
                    }.toString()
                    rows.add(Pair("iBeacon data", info))
                }
        }

        private fun eddystoneAdv(
            deviceInfo: BluetoothDeviceInfo,
            rows: MutableList<Pair<String, String>>
        ) {
            var dataValue = ""
            val scanInfo = deviceInfo.scanInfo
            val deviceAddress = scanInfo?.device?.address
            val beacon = Beacon(deviceAddress!!, scanInfo.rssi)
            val serviceData = scanInfo.scanRecord?.serviceData!![EDDYSTONE_SERVICE_UUID]
            validateEddyStoneServiceData(beacon, deviceAddress, serviceData)

            // get url string
            var eddystoneUrl = beacon.urlStatus.toString()
            eddystoneUrl =
                if (TextUtils.isEmpty(eddystoneUrl)) context.getString(R.string.beacon_details_dialog_unknown_value) else eddystoneUrl

            // get uid string
            // Eddystone UID Advertisement Data: 16-byte Beacon ID (10-byte namespace, 6-byte instance)
            val eddystoneUid = beacon.uidStatus.uidValue
            val eddystoneUidValue =
                if (TextUtils.isEmpty(eddystoneUid)) context.getString(R.string.beacon_details_dialog_unknown_value) else eddystoneUid?.substring(
                    0,
                    20
                )
            val eddystoneUidNameSpace =
                if (TextUtils.isEmpty(eddystoneUid)) context.getString(R.string.beacon_details_dialog_unknown_value) else "" + eddystoneUid?.substring(
                    20
                )

            // get tlm data
            val version = beacon.tlmStatus.version
            //beacon.tlmstatus.voltage returns value in millivolts
            var voltage = beacon.tlmStatus.voltage
            var voltageValue = 0.0
            if (voltage != null) {
                voltageValue = voltage.toDouble()
                voltage =
                    (voltageValue / 1000).toString() + " " + context.getString(R.string.beacon_details_dialog_unit_volts)
            }
            val temperature = beacon.tlmStatus.temp + " " +
                    context.getString(R.string.beacon_details_dialog_unit_degrees_celsius)
            val advertisementCount = beacon.tlmStatus.advCnt
            var uptimeCountInSeconds = beacon.tlmStatus.deciSecondsCntVal / 10
            uptimeCountInSeconds = Math.round(uptimeCountInSeconds * 10) / 10.0
            val secondsLabel =
                context.getString(R.string.beacon_details_dialog_unit_seconds_abbreviated)
            val daysLabel = context.getString(R.string.beacon_details_dialog_unit_days)
            val uptimeCount = String.format(
                "%d $secondsLabel (%d $daysLabel)",
                uptimeCountInSeconds.toInt(),
                TimeUnit.SECONDS.toDays(uptimeCountInSeconds.roundToLong())
            )
            var eddystoneTlm = ""
            eddystoneTlm += context.getString(R.string.beacon_details_dialog_tlm_version) + ": " + version + "<br>"
            eddystoneTlm += context.getString(R.string.beacon_details_dialog_tlm_voltage) + ": " + voltage + "<br>"
            eddystoneTlm += context.getString(R.string.beacon_details_dialog_tlm_temperature) + ": " + temperature +
                    "<br>"
            eddystoneTlm += context.getString(R.string.beacon_details_dialog_tlm_advertisement_count) + ": " +
                    advertisementCount + "<br>"
            eddystoneTlm += context.getString(R.string.beacon_details_dialog_tlm_uptime) + ": " + uptimeCount
            eddystoneTlm =
                if (TextUtils.isEmpty(eddystoneTlm)) context.getString(R.string.beacon_details_dialog_unknown_value) else eddystoneTlm
            dataValue += context.getString(R.string.beacon_details_dialog_url) + ": " + eddystoneUrl + "<br>"
            dataValue += context.getString(R.string.beacon_details_dialog_uid) + ": " + eddystoneUidValue +
                    "<br>"
            dataValue += context.getString(R.string.beacon_details_dialog_instance) + ": " +
                    eddystoneUidNameSpace + "<br><br>"
            dataValue += context.getString(R.string.beacon_details_dialog_tlm_data) + ":<br>" + eddystoneTlm +
                    "<br>"
            rows.add(Pair("Eddystone data", dataValue))
        }

        private fun altBeaconAdv(
            deviceInfo: BluetoothDeviceInfo,
            rows: MutableList<Pair<String, String>>
        ) {

            val altBeacon = AltBeacon(deviceInfo)
            val beaconId = altBeacon.altBeaconId
            val mfgId = altBeacon.manufacturerId
            val refRssi = "" + altBeacon.altBeaconReferenceRssi

            val dataValue = StringBuilder().apply {
                append(context.getString(R.string.beacon_details_dialog_beacon_id)).append("<br>")
                append(beaconId).append("<br><br>")

                append(context.getString(R.string.beacon_details_dialog_manufacturer_id)).append(
                    mfgId
                )
                append("<br><br>")

                append(context.getString(R.string.beacon_details_dialog_reference_rssi)).append(
                    refRssi
                )
                append("&nbsp;dBm<br>") //non-breaking space in HTML to separate value with a unit (dBm)
            }.toString()

            rows.add(Pair("AltBeacon data", dataValue))
        }

        // Identify an EH Sensor (Energy Harvesting) device by name. Assumes device name contains "EH Sensor".
        private fun isEhSensor(deviceInfo: BluetoothDeviceInfo): Boolean {
            val name = deviceInfo.name
            return name.contains("EH Sensor", ignoreCase = true)
        }
    }

    companion object {
        val EDDYSTONE_SERVICE_UUID = ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")
    }
}
