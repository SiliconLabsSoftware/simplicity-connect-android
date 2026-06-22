package com.siliconlabs.bledemo.features.demo.matter_demo.fragments

import android.annotation.SuppressLint
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import chip.devicecontroller.ChipDeviceController
import chip.devicecontroller.UnpairDeviceCallback
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.databinding.FragmentMatterScannedResultsBinding
import com.siliconlabs.bledemo.features.demo.matter_demo.activities.MatterDemoActivity
import com.siliconlabs.bledemo.features.demo.matter_demo.adapters.MatterScannedResultAdapter
import com.siliconlabs.bledemo.features.demo.matter_demo.dishwasher_demo.view.MatterDishwasherFragment
import com.siliconlabs.bledemo.features.demo.matter_demo.dishwasher_demo.view.MatterDishwasherFragment.Companion.DISHWASHER_PREF
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterOvenFragment
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterRangeHoodFragment
import com.siliconlabs.bledemo.features.demo.matter_demo.evse.ui.EVFragment
import com.siliconlabs.bledemo.features.demo.matter_demo.model.MatterScannedResultModel
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.ChipClient
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.FragmentUtils
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.MatterTextAlertDialogFragment
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.RecyclerViewMargin
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.SharedPrefsUtils
import com.siliconlabs.bledemo.utils.CustomToastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber


class MatterScannedResultFragment : Fragment() {
    private val deviceController: ChipDeviceController
        get() = ChipClient.getDeviceController(requireContext())
    private lateinit var scope: CoroutineScope
    private lateinit var deviceList: ArrayList<MatterScannedResultModel>
    private lateinit var binding: FragmentMatterScannedResultsBinding
    private var deviceId: Long = 0
    private var deviceType: Int = 0
    private var pos: Int = 0
    private lateinit var matterAdapter: MatterScannedResultAdapter
    private lateinit var mPrefs: SharedPreferences
    private lateinit var dishwasherPref: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mPrefs = requireContext().getSharedPreferences("your_preference_name", MODE_PRIVATE)
        dishwasherPref = requireContext().getSharedPreferences(
            DISHWASHER_PREF,
            AppCompatActivity.MODE_PRIVATE
        )
        deviceList = requireArguments().getParcelableArrayList(ARG_DEVICE_LIST)!!
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        scope = viewLifecycleOwner.lifecycleScope
        binding = FragmentMatterScannedResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    inner class ChipUnpairDeviceCallback : UnpairDeviceCallback {
        override fun onError(status: Int, remoteDeviceId: Long) {
            Timber.tag(TAG).e("onError : $remoteDeviceId, $status")
        }

        override fun onSuccess(remoteDeviceId: Long) {
            Timber.tag(TAG).d("onSuccess : $remoteDeviceId")
            showMessages(R.string.matter_device_unpair)
        }
    }

    private fun showMessages(msgId: Int) {
        if (isAdded) { // Check if the Fragment is attached
            requireActivity().runOnUiThread {
                if (isAdded) {
                    val resString = requireContext().getString(msgId)
                    if (resString.isNotEmpty()) { // Check for an empty string
                        CustomToastManager.show(
                            requireActivity(), resString, 5000
                        )
                    }
                }
            }
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as MatterDemoActivity).showQRScanner()


        val quickStartGuide = getString(R.string.matter_quick_starter_guide_link)
        val spannableString = SpannableString(quickStartGuide)
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                // Define the action to be taken when the link is clicked
                val uri = Uri.parse(MATTER_URL)
                val intent = Intent(Intent.ACTION_VIEW, uri)
                startActivity(intent)
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = ContextCompat.getColor(requireContext(), R.color.silabs_rebranding_2373_hyperlink_text_color)
            }
        }
        spannableString.setSpan(
            clickableSpan,
            HYPERLINK_START,
            HYPERLINK_END,
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // Set the SpannableString as the text of the TextView
        binding.tvQuickStartHyperlink.text = spannableString

        // Make the TextView clickable
        binding.tvQuickStartHyperlink.movementMethod = LinkMovementMethod.getInstance()

        setListDisplay()
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            onBackPressedCallback
        )

    }


    private fun setListDisplay() {
        deviceList = SharedPrefsUtils.retrieveSavedDevices(mPrefs)
        if (deviceList.size !== 0) {
            Timber.tag(TAG).e("+ deviceList $deviceList")
            val uniqueDevices = deviceList.distinctBy { it.matterName } // WORKAROUND

            deviceList.clear()
            deviceList.addAll(uniqueDevices)
            Timber.tag(TAG).e("+ deviceList $deviceList")
            matterAdapter = MatterScannedResultAdapter(deviceList)
            binding.recyclerViewScannedDevices.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            binding.recyclerViewScannedDevices.adapter = matterAdapter
            binding.recyclerViewScannedDevices.addItemDecoration(
                RecyclerViewMargin(resources.getDimensionPixelSize(R.dimen.matter_0dp))
            )
            matterAdapter.notifyDataSetChanged()
            matterAdapter.setOnClickListener(object : MatterScannedResultAdapter.OnClickListener {
                override suspend fun onClick(position: Int, model: MatterScannedResultModel) {
                    deviceId = model.deviceId
                    navigateToDemos(model)
                }

                override suspend fun onDelete(position: Int, model: MatterScannedResultModel) {
                    Timber.tag(TAG).d("Unpair")
                    deviceId = model.deviceId
                    pos = position
                    val dialog = MatterTextAlertDialogFragment.newInstance(
                        requireContext().getString(R.string.matter_delete_alert_title),
                        requireContext().getString(R.string.matter_delete_message)
                    )
                    dialog.onPositiveClick = { performDeleteConfirmed() }
                    dialog.onNegativeClick = { }
                    dialog.show(parentFragmentManager, TAG_DELETE_DEVICE_DIALOG)
                }
            })
        } else {
            binding.placeholder.visibility = View.VISIBLE
            binding.tvQuickStartHyperlink.visibility = View.VISIBLE
        }
    }


    private fun performDeleteConfirmed() {
        Timber.tag(TAG).d("deviceList   $deviceList")
        deviceList.removeAt(pos)
        binding.recyclerViewScannedDevices.adapter?.notifyItemRemoved(pos)
        Timber.tag(TAG).d("deviceList removed elem $deviceList")
        scope.launch {
            deviceController.unpairDeviceCallback(deviceId, ChipUnpairDeviceCallback())
        }

        matterAdapter.notifyDataSetChanged()
        SharedPrefsUtils.saveDevicesToPref(mPrefs, deviceList)
        if (null != dishwasherPref) {
            SharedPrefsUtils.clearDishwasherSharedPreferences(dishwasherPref)
        }
        if (deviceList.isEmpty()) {
            requireActivity().finish()
        }
        setListDisplay()
    }


    private fun navigateToDemos(model: MatterScannedResultModel) {
        deviceType = model.deviceType
        Log.e("MATTER_SCANNED_RESULT", "MODEL DEVICE TYPE::--${model.deviceType}")
        when (deviceType) {

            THERMOSTAT_TYPE -> {
                val matterThermostatFragment = MatterThermostatFragment.newInstance()
                FragmentUtils.getHost(
                    this@MatterScannedResultFragment,
                    Callback::class.java
                ).navigateToDemo(matterThermostatFragment, model)
            }

            DIMMABLE_Light_TYPE, ENHANCED_COLOR_LIGHT_TYPE, ON_OFF_LIGHT_TYPE, COLOR_TEMPERATURE_LIGHT_TYPE -> {
                val matterLightFragment = MatterLightFragment.newInstance()
                FragmentUtils.getHost(
                    this@MatterScannedResultFragment,
                    Callback::class.java
                ).navigateToDemo(matterLightFragment, model)
            }

            WINDOW_COVERING_TYPE -> {
                val matterWindowCoverFragment = MatterWindowCoverFragment.newInstance()
                FragmentUtils.getHost(
                    this@MatterScannedResultFragment,
                    Callback::class.java
                ).navigateToDemo(matterWindowCoverFragment, model)
            }

            DOOR_LOCK_TYPE -> {
                val matterDoorFragment = MatterDoorFragment.newInstance()
                FragmentUtils.getHost(
                    this@MatterScannedResultFragment,
                    Callback::class.java
                ).navigateToDemo(matterDoorFragment, model)
            }

            OCCUPANCY_SENSOR_TYPE -> {
                val matterOccupancySensorFragment = MatterOccupancySensorFragment.newInstance()
                FragmentUtils.getHost(
                    this@MatterScannedResultFragment,
                    Callback::class.java
                ).navigateToDemo(matterOccupancySensorFragment, model)
            }

            CONTACT_SENSOR_TYPE -> {
                val matterContactSensorFragment = MatterContactSensorFragment.newInstance()
                FragmentUtils.getHost(
                    this@MatterScannedResultFragment,
                    Callback::class.java
                ).navigateToDemo(matterContactSensorFragment, model)
            }

            TEMPERATURE_SENSOR_TYPE -> {
                val matterTemperatureSensorFragment = MatterTemperatureSensorFragment.newInstance()
                FragmentUtils.getHost(
                    this@MatterScannedResultFragment,
                    Callback::class.java
                ).navigateToDemo(matterTemperatureSensorFragment, model)
            }

            DIMMABLE_PLUG_IN_UNIT_TYPE -> {
                val matterPlugFragment = MatterPlugFragment.newInstance()
                FragmentUtils.getHost(
                    this@MatterScannedResultFragment,
                    Callback::class.java
                ).navigateToDemo(matterPlugFragment, model)
            }

            DISHWASHER_TYPE -> {
                val matterDishwasherFragment = MatterDishwasherFragment.newInstance()
                FragmentUtils.getHost(
                    this@MatterScannedResultFragment,
                    Callback::class.java
                ).navigateToDemo(matterDishwasherFragment, model)
            }

            OVEN -> {
                val matterOvenFragment = MatterOvenFragment.newInstance()
                FragmentUtils.getHost(
                    this@MatterScannedResultFragment,
                    Callback::class.java
                ).navigateToDemo(matterOvenFragment, model)
            }

            RANGE_HOOD -> {
                val matterRangeHoodFragment = MatterRangeHoodFragment.newInstance()
                FragmentUtils.getHost(
                    this@MatterScannedResultFragment,
                    Callback::class.java
                ).navigateToDemo(matterRangeHoodFragment, model)
            }

            AIR_QUALITY_SENSOR_TYPE -> {
                val matterAirQualitySensorFragment = MatterAirQualitySensorFragment.newInstance()
                FragmentUtils.getHost(
                    this@MatterScannedResultFragment,
                    Callback::class.java
                ).navigateToDemo(matterAirQualitySensorFragment, model)
            }

            GENERIC_SWITCH, ON_OFF_LIGHT_SWITCH, DIMMER_SWITCH,
            COLOR_DIMMER_SWITCH -> {
                val matterLightControlSwitch = MatterLightControlSwitchFragment.newInstance()
                FragmentUtils.getHost(
                    this@MatterScannedResultFragment,
                    Callback::class.java
                ).navigateToDemo(matterLightControlSwitch, model)
            }

            ENERGY_EVSE_TYPE -> {
                val evFragment = EVFragment()
                FragmentUtils.getHost(
                    this@MatterScannedResultFragment,
                    Callback::class.java
                ).navigateToDemo(evFragment, model)
            }


            else -> {
                println("Unhandled Operation....")
                Timber.tag(TAG).e("Unhandled Operation....")
            }
        }

    }


    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            requireActivity().finish()
        }
    }


    interface Callback {
        fun navigateToDemo(
            matterDemoFragment: Fragment,
            model: MatterScannedResultModel
        )

    }

    /**
     * Fast path: update a single device's online status in place (no full list rebuild).
     */
    fun applyDeviceOnlineStatus(deviceId: Long, isOnline: Boolean) {
        if (!this::matterAdapter.isInitialized) return
        val idx = deviceList.indexOfFirst { it.deviceId == deviceId }
        if (idx >= 0) {
            val model = deviceList[idx]
            if (model.isDeviceOnline != isOnline) {
                model.isDeviceOnline = isOnline
                if (isAdded) {
                    requireActivity().runOnUiThread {
                        matterAdapter.notifyItemChanged(idx)
                    }
                } else {
                    matterAdapter.notifyItemChanged(idx)
                }
            }
        }
    }

    /**
     * Slow path fallback: re-sync all device models from SharedPrefs without fragment recreation.
     */
    fun refreshAllFromPrefs() {
        val updated = SharedPrefsUtils.retrieveSavedDevices(mPrefs)
        val map = updated.associateBy { it.deviceId }
        var anyChanged = false
        deviceList.forEachIndexed { i, existing ->
            val fresh = map[existing.deviceId] ?: return@forEachIndexed
            if (existing.isDeviceOnline != fresh.isDeviceOnline) {
                existing.isDeviceOnline = fresh.isDeviceOnline
                anyChanged = true
                matterAdapter.notifyItemChanged(i)
            }
        }
        if (anyChanged) Timber.tag(TAG).d("Device statuses refreshed in-place")
    }

    companion object {
        private const val ARG_DEVICE_LIST = "device_list"
        private const val TAG = "MatterScannedResultFragment"
        private const val TAG_DELETE_DEVICE_DIALOG = "MatterDeleteDeviceDialog"
        private const val MATTER_URL = "https://docs.silabs.com/matter/2.1.0/matter-overview"


        //Lighting Type
        const val ON_OFF_LIGHT_TYPE = 256
        const val DIMMABLE_Light_TYPE = 257
        const val COLOR_TEMPERATURE_LIGHT_TYPE = 268
        const val ENHANCED_COLOR_LIGHT_TYPE = 269

        //smart plugs/outlets and other actuators
        const val ON_OFF_PLUG_IN_UNIT_TYPE = 266
        const val DIMMABLE_PLUG_IN_UNIT_TYPE = 267
        const val PUMP = 771

        //switches and controls
        const val ON_OFF_LIGHT_SWITCH = 259
        const val DIMMER_SWITCH = 260
        const val COLOR_DIMMER_SWITCH = 261
        const val CONTROL_BRIDGE = 2112
        const val PUMP_CONTROLLER = 772
        const val GENERIC_SWITCH = 15

        //sensors
        const val CONTACT_SENSOR_TYPE = 21
        const val LIGHT_SENSOR_TYPE = 262
        const val OCCUPANCY_SENSOR_TYPE = 263
        const val TEMPERATURE_SENSOR_TYPE = 770
        const val PRESSURE_SENSOR_TYPE = 773
        const val FLOW_SENSOR_TYPE = 774
        const val HUMIDITY_SENSOR_TYPE = 775
        const val ON_OFF_SENSOR_TYPE = 2128
        const val SMOKE_CO_ALARM_TYPE = 118

        //closures
        const val DOOR_LOCK_TYPE = 10
        const val DOOR_LOCK_CONTROLLER_TYPE = 11
        const val WINDOW_COVERING_TYPE = 514
        const val WINDOW_COVERING_CONTROLLER_TYPE = 515

        //HVAC
        const val HEATING_COOLING_UNIT_TYPE = 768
        const val THERMOSTAT_TYPE = 769
        const val FAN_TYPE = 43
        const val AIR_PURIFIER_TYPE = 45
        const val AIR_QUALITY_SENSOR_TYPE = 44

        //media
        const val BASIC_VIDEO_PLAYER_TYPE = 40
        const val CASTING_VIDEO_PLAYER_TYPE = 35
        const val SPEAKER_TYPE = 34
        const val CONTENT_APP_TYPE = 36
        const val CASTING_VIDEO_CLIENT_TYPE = 41
        const val VIDEO_REMOTE_CONTROL_TYPE = 42

        //Generic
        const val MODE_SELECT_TYPE = 39

        //robotic devices
        const val ROBOTIC_VACUUM_CLEANER_TYPE = 116

        //appliances
        const val REFRIGERATOR_TYPE = 112
        const val TEMPERATURE_CONTROLLED_CABINET_TYPE = 113
        const val ROOM_AIR_CONDITIONER_TYPE = 114
        const val LAUNDRY_WASHER_TYPE = 115
        const val DISHWASHER_TYPE = 117
        const val MICROWAVE_OVEN = 121
        const val OVEN = 123
        const val RANGE_HOOD = 122

        //Energy EVSE
        const
        val ENERGY_EVSE_TYPE = 1293//1292

        //Hyperlink const
        private const val HYPERLINK_START = 89
        private const val HYPERLINK_END = 107

        fun newInstance(): MatterScannedResultFragment {
            return MatterScannedResultFragment()
        }
    }
}