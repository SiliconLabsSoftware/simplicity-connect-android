package com.siliconlabs.bledemo.features.demo.matter_demo.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import chip.devicecontroller.ChipClusters
import chip.devicecontroller.ChipDeviceController
import chip.devicecontroller.ChipStructs
import chip.devicecontroller.ChipStructs.AccessControlClusterAccessControlEntryStruct
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.databinding.FragmentMatterSmartSwitchLightControllerBinding
import com.siliconlabs.bledemo.features.demo.matter_demo.activities.MatterDemoActivity
import com.siliconlabs.bledemo.features.demo.matter_demo.adapters.MatterLightSwitchRVAdapter
import com.siliconlabs.bledemo.features.demo.matter_demo.controller.GenericChipDeviceListener
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterLightFragment.CallBackHandler
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterLightFragment.Companion.ARG_DEVICE_MODEL
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.COLOR_DIMMER_SWITCH
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.COLOR_TEMPERATURE_LIGHT_TYPE
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.DIMMABLE_Light_TYPE
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.DIMMER_SWITCH
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.ENHANCED_COLOR_LIGHT_TYPE
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.ON_OFF_LIGHT_SWITCH
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.ON_OFF_LIGHT_TYPE
import com.siliconlabs.bledemo.features.demo.matter_demo.model.MatterScannedResultModel
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.ChipClient
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.CustomProgressDialog
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.FragmentUtils
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.SharedPrefsUtils
import com.siliconlabs.bledemo.utils.BLEUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Optional

class MatterLightControlSwitchFragment : Fragment(), (MatterScannedResultModel) -> Unit {
    private lateinit var binding: FragmentMatterSmartSwitchLightControllerBinding
    private var customProgressDialog: CustomProgressDialog? = null

    private lateinit var bindSwitchViewModel: MatterLightSwitchViewModel

    private lateinit var scope: CoroutineScope
    private val deviceController: ChipDeviceController
        get() = ChipClient.getDeviceController(requireContext())

    private lateinit var model: MatterScannedResultModel
    private lateinit var deviceList: ArrayList<MatterScannedResultModel>
    private lateinit var prefs: SharedPreferences
    private var matterLightSwitchAdapter: MatterLightSwitchRVAdapter? = null
    private var nodeIDSwitch: Long = 0
    private var nodeIDLight: Long = 0
    private var matterBindedItemName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = requireContext().getSharedPreferences(
            MatterDemoActivity.MATTER_PREF,
            Context.MODE_PRIVATE
        )
        model = requireArguments().getParcelable(ARG_DEVICE_MODEL)!!
        deviceList = SharedPrefsUtils.retrieveSavedDevices(prefs)
        Log.e("MATTER_LIGHT_BINDING_CONTROL", "" + deviceList.toString())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = FragmentMatterSmartSwitchLightControllerBinding.inflate(
            inflater,
            container, false
        )
        bindSwitchViewModel = ViewModelProvider(this)[MatterLightSwitchViewModel::class.java]
        binding.tvShowBindInfo.text = getString(R.string.binded_to_nothing)
        val wasBindSuccessful = prefs.getBoolean("is_bind_successful", false)
        val bindedDeviceName = prefs.getString("binded_device_name", null)
        val name = prefs.getString("unbind_enabled_for", null)
        if (name != null) {
            bindSwitchViewModel.enableUnbindForDevice(name)
        }
        val restoredList = SharedPrefsUtils.retrieveSavedDevices(prefs).map { device ->
            device.copy(
                isAclWriteInProgress = false,  // Always reset
                isBindingInProgress = false,   // Always reset
                isBindingSuccessful = (wasBindSuccessful && device.matterName == bindedDeviceName)
            )
        }
        bindSwitchViewModel.updateDeviceStates(restoredList)


        bindSwitchViewModel.setBindingStatus(
            if (wasBindSuccessful && bindedDeviceName != null)
                "Binded to: $bindedDeviceName"
            else
                getString(R.string.binded_to_nothing)
        )
        return binding.root
    }


    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scope = viewLifecycleOwner.lifecycleScope
        deviceController.setCompletionListener(SwitchLightControlChipControllerCallback())
        println("MATTER FabricIndex: ${deviceController.fabricIndex}")
        println("MATTER NodeID: ${deviceController.controllerNodeId}")

        val deviceList = SharedPrefsUtils.retrieveSavedDevices(prefs)
        println("MATTER DeviceList: ${deviceList.size}")
        for (device in deviceList) {
            when (device.deviceType) {
                ON_OFF_LIGHT_TYPE, DIMMABLE_Light_TYPE,
                COLOR_TEMPERATURE_LIGHT_TYPE, ENHANCED_COLOR_LIGHT_TYPE
                    -> nodeIDLight = device.deviceId

                ON_OFF_LIGHT_SWITCH, DIMMER_SWITCH, COLOR_DIMMER_SWITCH
                    -> nodeIDSwitch = device.deviceId
            }
        }


        /*Load the UI Related to Select Device to Bind*/
        if (deviceList.isNotEmpty()) {
            if (deviceList.any { it.deviceType != 260 }) {
                binding.layoutNoDevice.visibility = View.GONE
                binding.layoutViewLightBind.container.visibility = View.VISIBLE
                matterLightSwitchAdapter = MatterLightSwitchRVAdapter(
                    deviceList,
                    selectedMatterName = null,
                    onLightSelected = { device ->
                        if (device.isBindingInProgress || device.isAclWriteInProgress) return@MatterLightSwitchRVAdapter
                        bindSwitchViewModel.selectLightForBinding(device.matterName)
                    }
                )
                binding.layoutViewLightBind.rvMatterList.apply {
                    layoutManager = LinearLayoutManager(requireContext())
                    adapter = matterLightSwitchAdapter
                }
                bindSwitchViewModel.selectedLightNameForBinding.observe(viewLifecycleOwner) { name ->
                    matterLightSwitchAdapter?.setSelectedMatterName(name)
                }
                bindSwitchViewModel.deviceStates.observe(viewLifecycleOwner) { updatedDevices ->
                    matterLightSwitchAdapter?.updateList(updatedDevices)
                    refreshBindStripUi()
                    maybeAutoSelectSingleBindableLight(updatedDevices)
                }
            } else {
                binding.layoutNoDevice.visibility = View.VISIBLE
            }
        }
        (activity as MatterDemoActivity).hideQRScanner()


        bindSwitchViewModel.bindingStatusText.observe(viewLifecycleOwner) { status ->
            val params = binding.tvShowBindInfo.layoutParams
            params.width = 150
            params.height = 100
            binding.tvShowBindInfo.layoutParams = params
            binding.tvShowBindInfo.gravity = Gravity.CENTER
            binding.tvShowBindInfo.background = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.channel_sounding_distance_value_box_bg
            )
            binding.tvShowBindInfo.setTextColor(resources.getColor(R.color.silabs_inactive_light))
            binding.tvShowBindInfo.text = status
            refreshBindStripUi()
        }

        binding.unBindLightBtn.setOnClickListener {
            startUnbindForBoundDevice()
        }

        binding.tvShowBindInfo.setOnClickListener {
            if (binding.tvShowBindInfo.visibility != View.VISIBLE) return@setOnClickListener
            val idleText = getString(R.string.binded_to_nothing)
            if (binding.tvShowBindInfo.text?.toString() != idleText) return@setOnClickListener

            val devices = bindSwitchViewModel.deviceStates.value.orEmpty()
            val selectedName = bindSwitchViewModel.selectedLightNameForBinding.value
            val selected = selectedName?.let { name ->
                devices.firstOrNull { it.matterName == name }
            }?.takeIf { isBindableLightForSwitchUi(it) }

            if (selected == null) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.matter_select_light_to_bind),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            if (selected.isBindingInProgress || selected.isAclWriteInProgress) return@setOnClickListener
            startBindingFlow(selected)
        }

        bindSwitchViewModel.aclWriteError.observe(viewLifecycleOwner) {
            showErrorAndExit(
                "Binding failed due to resource exhaustion.Communication may still work temporarily,but binding is not secure.Hence please reset the " +
                        "boards(Light & Light Switch (Both)) as well as delete the matter commissioned devices(Light & Light Switch(Both) from the MatterList Screen)and " +
                        "then start binding again."
            )

        }

        refreshBindStripUi()
    }

    private fun isBindableLightForSwitchUi(device: MatterScannedResultModel): Boolean {
        if (device.deviceType == DIMMER_SWITCH) return false
        return when (device.deviceType) {
            DIMMABLE_Light_TYPE,
            ENHANCED_COLOR_LIGHT_TYPE,
            ON_OFF_LIGHT_TYPE,
            COLOR_TEMPERATURE_LIGHT_TYPE -> true
            else -> false
        }
    }

    private fun maybeAutoSelectSingleBindableLight(devices: List<MatterScannedResultModel>) {
        val lights = devices.filter { isBindableLightForSwitchUi(it) }
        if (lights.size == 1) {
            bindSwitchViewModel.selectLightForBinding(lights.first().matterName)
        }
    }

    private fun startBindingFlow(selectedDevice: MatterScannedResultModel) {
        bindSwitchViewModel.updateDeviceBindingState(
            selectedDevice.matterName,
            inProgress = true
        )
        bindSwitchViewModel.setBindingStatus("Binding in progress...")

        matterBindedItemName = selectedDevice.matterName
        prefs.edit().apply {
            putString(MATTER_BIND_ITEM_NAME, matterBindedItemName)
            apply()
        }
        scope.launch {
            showMatterProgressDialog("Binding...")
            getACLClusterForDevice()
            performLightAndLightSwitchBinding(selectedDevice.isLightAndLightSwitchItemSelected)
        }
    }

    /**
     * Header row: show Unbind only when a light row is successfully bound and not mid-unbind.
     */
    private fun refreshBindStripUi() {
        if (!this::binding.isInitialized) return
        val devices = bindSwitchViewModel.deviceStates.value.orEmpty()
        val showUnbind =
            devices.any { it.isBindingSuccessful && !it.isUnbindingInProgress }
        binding.tvShowBindInfo.visibility = if (showUnbind) View.GONE else View.VISIBLE
        binding.unBindLightBtn.visibility = if (showUnbind) View.VISIBLE else View.GONE
        binding.unBindLightBtn.isEnabled = showUnbind
    }

    private fun startUnbindForBoundDevice() {
        val deviceName = prefs.getString("binded_device_name", null)
            ?: bindSwitchViewModel.unbindEnableDeviceName.value
            ?: bindSwitchViewModel.deviceStates.value
                ?.firstOrNull { it.isBindingSuccessful }?.matterName
        if (deviceName.isNullOrBlank()) {
            Toast.makeText(
                requireContext(),
                "No bound light to unbind.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        showMatterProgressDialog("Unbinding...")
        bindSwitchViewModel.setUnbindingState(deviceName, inProgress = true)
        scope.launch {
            unbindDeviceBinding(
                requireContext(),
                nodeIDSwitch,
                nodeIDLight,
                deviceName = deviceName
            )
        }
    }

    private fun performLightAndLightSwitchBinding(lightAndLightSwitchItemSelected: Boolean) {
        val groupList = deviceController.availableGroupIds
        val keySetList = deviceController.keySetIds

        println("MATTER GroupList: $groupList")
        //binding.groupListInfo.text = groupList.toString()
        println("MATTER KeySetList: $keySetList")
        //binding.keysListInfo.text = keySetList.toString()
        for (group in groupList) {
            println("MATTER Group: $group")
        }
        for (keys in keySetList) {
            println("MATTER Keys: $keys")

        }

        val bindStruct1 = ChipStructs.BindingClusterTargetStruct(
            Optional.of(nodeIDLight),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            null
        ).apply {
            endpoint = Optional.of(1)
            cluster = Optional.of(6)
            fabricIndex = 1
        }

        // Create the ArrayList
        val bindingList = ArrayList<ChipStructs.BindingClusterTargetStruct>()
        // Add bindStruct to the ArrayList
        bindingList.add(bindStruct1)
        //bindingList.add(bindStruct2)
        println("MATTER binding: $bindingList")

        scope.launch {
            getBindingClusterForDevice().writeBindingAttribute(
                object : ChipClusters.DefaultClusterCallback {
                    override fun onError(error: Exception?) {
                        removeProgress()
                        showBindingMessage("Error : $error")
                    }

                    override fun onSuccess() {
                        removeProgress()
                        //showMatterProgressDialog("Binding in Progress...")
                        //showBindingMessage("Success Binding")
                        BLEUtils.MATTER_IS_LIGHT_SWITCH_BIND_SUCCESSFUL = true
                    }
                },
                bindingList
            )
        }
    }

    private suspend fun getACLClusterForDevice() {
        val devPtr = ChipClient.getConnectedDevicePointer(requireContext(), nodeIDLight)
        println("MatterPoC DevPTr:$devPtr")
        val aclControl = ChipClusters.AccessControlCluster(devPtr, 0)
        println("MatterPoC aclControl:$aclControl")

        aclControl.readAclAttribute(object :
            ChipClusters.AccessControlCluster.AclAttributeCallback {
            override fun onError(error: Exception?) {
                Timber.tag(TAG).d("onError :  $error")
                showMessage("Error : $error")
                removeProgress()
            }

            override fun onSuccess(value: MutableList<AccessControlClusterAccessControlEntryStruct>?) {
                scope.launch {
                    removeProgress()
                    getBindingClusterForDevice()
                }
                requireActivity().runOnUiThread { showAddAccessControlDialog(value) }
            }
        })

    }

    private fun showAddAccessControlDialog(
        value: List<AccessControlClusterAccessControlEntryStruct>?
    ) {
        if (value == null) {
            Timber.tag(TAG).d("MATTER Access Control read fail")
            showMessage("MATTER Access Control read fail")
            return
        }

        println("MATTER Value:$value")
        //showMessage(value.toString())
        println("----------------------------------")
        scope.launch {
            //sendAccessControl(value, GROUPID, 3)
            sendSafeAccessControl(requireContext(), nodeIDLight, nodeIDSwitch, value, GROUPID, 3)
        }
    }

    private suspend fun sendAccessControl(
        value: List<AccessControlClusterAccessControlEntryStruct>,
        groupID: Int,
        accessControl: Int
    ) {
        val devPtr = ChipClient.getConnectedDevicePointer(requireContext(), nodeIDLight)
        println("MATTER sendAccessControl DevPTr:$devPtr")
        val aclControl = ChipClusters.AccessControlCluster(devPtr, 0)
        println("MATTER sendAccessControl aclControl:$aclControl")
        val sendEntry = ArrayList<AccessControlClusterAccessControlEntryStruct>()
        for (entry in value) {
            if (
                entry.authMode == 2 /* Group */ &&
                entry.subjects != null &&
                entry.subjects!!.contains(groupID.toULong().toLong())
            ) {
                continue
            }
            sendEntry.add(entry)
            println("====================================")
            println("Matter :SendEntry$sendEntry")
            println("**************************************")
        }

        val newTarget = ChipStructs.AccessControlClusterAccessControlTargetStruct(
            6L, 1, null
        )

        val newEntry = AccessControlClusterAccessControlEntryStruct(
            accessControl,
            2,
            arrayListOf(nodeIDSwitch),
            null, Optional.of(deviceController.fabricIndex.toInt()),
            deviceController.fabricIndex.toUInt().toInt(),
        )
        println("====================================")
        println("MATTER :newEntry$newEntry")
        sendEntry.add(newEntry)
        println("====================================")
        println("MATTER :Ready to SendEntry$sendEntry")
        bindSwitchViewModel.updateAclWriteProgress(matterBindedItemName, true)
        aclControl.writeAclAttribute(
            object : ChipClusters.DefaultClusterCallback {
                override fun onError(e: Exception?) {
                    removeProgress()
                    Timber.tag(TAG).d("onError : $e")
                    showMessage("ACL Write Error")
                    bindSwitchViewModel.setACLWriteError("ACL Write Error")
                }

                override fun onSuccess() {

                    Timber.tag(TAG).d("onResponse")
                    showMessage("Binding Success")
                    removeProgress()
                    bindSwitchViewModel.enableUnbindForDevice(matterBindedItemName)
                    bindSwitchViewModel.updateAclWriteProgress(matterBindedItemName, false)
                    bindSwitchViewModel.updateDeviceBindingState(
                        matterBindedItemName,
                        inProgress = false,
                        success = true
                    )


                    // Persist final state
                    val cleanList = bindSwitchViewModel.deviceStates.value?.map {
                        it.copy(isBindingInProgress = false, isAclWriteInProgress = false)
                    } ?: emptyList()

                    SharedPrefsUtils.saveDevicesToPref(
                        prefs,
                        cleanList as ArrayList<MatterScannedResultModel>
                    )
                    bindSwitchViewModel.setBindingStatus("Binded to $matterBindedItemName")
                }
            },
            sendEntry
        )
    }

    suspend fun sendSafeAccessControl(
        context: Context, lightNodeId: Long, switchNodeId: Long,
        value: List<AccessControlClusterAccessControlEntryStruct>,
        groupID: Int,
        accessControl: Int
    ) {
        try {
            val devicePtr = ChipClient.getConnectedDevicePointer(context, lightNodeId)
            val aclCluster =
                ChipClusters.AccessControlCluster(devicePtr, 0) // Endpoint 0 for ACL Cluster

            // Step 1: Read existing ACLs
            aclCluster.readAclAttribute(object :
                ChipClusters.AccessControlCluster.AclAttributeCallback {
                override fun onSuccess(list: MutableList<AccessControlClusterAccessControlEntryStruct>?) {
                    val switchAlreadyAuthorized = list?.any { entry ->
                        entry.subjects?.any { it.toLong() == switchNodeId } == true
                    } ?: false

                    if (switchAlreadyAuthorized) {
                        Timber.d("Switch already has ACL permission. Skipping ACL write.")
                        scope.launch {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "Already authorized. Skipping binding!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                Timber.tag(TAG).d("onResponse")
                                showMessage("Binding Success")
                                removeProgress()
                                bindSwitchViewModel.enableUnbindForDevice(matterBindedItemName)
                                bindSwitchViewModel.updateAclWriteProgress(
                                    matterBindedItemName,
                                    false
                                )
                                bindSwitchViewModel.updateDeviceBindingState(
                                    matterBindedItemName,
                                    inProgress = false,
                                    success = true
                                )


                                // Persist final state
                                val cleanList = bindSwitchViewModel.deviceStates.value?.map {
                                    it.copy(
                                        isBindingInProgress = false,
                                        isAclWriteInProgress = false
                                    )
                                } ?: emptyList()

                                SharedPrefsUtils.saveDevicesToPref(
                                    prefs,
                                    cleanList as ArrayList<MatterScannedResultModel>
                                )
                                bindSwitchViewModel.setBindingStatus("Binded to $matterBindedItemName")
                            }
                        }
                    } else {
                        Timber.d("Switch not authorized. Proceeding with ACL write.")
                        //AccessControlClusterAccessControlEntryStruct
                        // Step 2: Send AccessControl write only if needed

                        val sendEntry = ArrayList<AccessControlClusterAccessControlEntryStruct>()
                        for (entry in value) {
                            if (
                                entry.authMode == 2 /* Group */ &&
                                entry.subjects != null &&
                                entry.subjects!!.contains(groupID.toULong().toLong())
                            ) {
                                continue
                            }
                            sendEntry.add(entry)
                            println("====================================")
                            println("Matter :SendEntry$sendEntry")
                            println("**************************************")
                        }


                        val newEntry = AccessControlClusterAccessControlEntryStruct(
                            accessControl,
                            2,
                            arrayListOf(nodeIDSwitch),
                            null,
                            Optional.of(deviceController.fabricIndex.toUInt().toInt()),
                            deviceController.fabricIndex.toUInt().toInt()
                        )
                        println("====================================")
                        println("MATTER :newEntry$newEntry")
                        sendEntry.add(newEntry)
                        println("====================================")
                        println("MATTER :Ready to SendEntry$sendEntry")
                        bindSwitchViewModel.updateAclWriteProgress(matterBindedItemName, true)
                        aclCluster.writeAclAttribute(
                            object : ChipClusters.DefaultClusterCallback {
                                override fun onError(e: Exception?) {
                                    removeProgress()
                                    Timber.tag(TAG).d("onError : $e")
                                    showMessage("ACL Write Error")
                                    bindSwitchViewModel.setACLWriteError("ACL Write Error")
                                }

                                override fun onSuccess() {

                                    Timber.tag(TAG).d("onResponse")
                                    showMessage("Binding Success")
                                    removeProgress()
                                    bindSwitchViewModel.enableUnbindForDevice(matterBindedItemName)
                                    bindSwitchViewModel.updateAclWriteProgress(
                                        matterBindedItemName,
                                        false
                                    )
                                    bindSwitchViewModel.updateDeviceBindingState(
                                        matterBindedItemName,
                                        inProgress = false,
                                        success = true
                                    )


                                    // Persist final state
                                    val cleanList = bindSwitchViewModel.deviceStates.value?.map {
                                        it.copy(
                                            isBindingInProgress = false,
                                            isAclWriteInProgress = false
                                        )
                                    } ?: emptyList()

                                    SharedPrefsUtils.saveDevicesToPref(
                                        prefs,
                                        cleanList as ArrayList<MatterScannedResultModel>
                                    )
                                    bindSwitchViewModel.setBindingStatus("Binded to $matterBindedItemName")
                                }
                            },
                            sendEntry
                        )
                        aclCluster.writeAclAttribute(object : ChipClusters.DefaultClusterCallback {
                            override fun onSuccess() {
                                Timber.tag(TAG).d("onResponse")
                                showMessage("Binding Success")
                                removeProgress()
                                bindSwitchViewModel.enableUnbindForDevice(matterBindedItemName)
                                bindSwitchViewModel.updateAclWriteProgress(
                                    matterBindedItemName,
                                    false
                                )
                                bindSwitchViewModel.updateDeviceBindingState(
                                    matterBindedItemName,
                                    inProgress = false,
                                    success = true
                                )


                                // Persist final state
                                val cleanList = bindSwitchViewModel.deviceStates.value?.map {
                                    it.copy(
                                        isBindingInProgress = false,
                                        isAclWriteInProgress = false
                                    )
                                } ?: emptyList()

                                SharedPrefsUtils.saveDevicesToPref(
                                    prefs,
                                    cleanList as ArrayList<MatterScannedResultModel>
                                )
                                bindSwitchViewModel.setBindingStatus("Binded to $matterBindedItemName")
                            }

                            override fun onError(error: Exception?) {
                                removeProgress()
                                Timber.tag(TAG).d("onError : $error")
                                showMessage("ACL Write Error")
                                bindSwitchViewModel.setACLWriteError("ACL Write Error")
                            }
                        }, sendEntry)
                    }
                }

                override fun onError(error: Exception?) {
                    removeProgress()
                    Timber.e("Failed to read ACL list: ${error?.message}")
                    showMessage("Failed to read ACL: ${error?.message}")
                }
            })

        } catch (e: Exception) {
            removeProgress()
            Timber.e("Exception in sendAccessControl: ${e.message}")
            showMessage("Error during ACL operation: ${e.message}")
        }
    }


    private suspend fun getBindingClusterForDevice(): ChipClusters.BindingCluster {
        return ChipClusters.BindingCluster(
            ChipClient.getConnectedDevicePointer(requireContext(), nodeIDSwitch), SWITCH_END_POINT
        )
    }


    private fun showMessage(message: String) {
        println("MATTER $message")
        //requireActivity().runOnUiThread { binding.statusACLInfo.text = message }
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            if (message.equals("Binding Success", true)) {
                prefs.edit().apply {
                    putBoolean("is_bind_successful", true)
                    putString("binded_device_name", matterBindedItemName)
                    putString("unbind_enabled_for", matterBindedItemName)
                    apply()
                }
            }
        }
    }

    private fun showErrorAndExit(message: String) {
        scope.launch(Dispatchers.Main) {
            AlertDialog.Builder(requireContext())
                .setTitle("Alert")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Ok") { dialog, _ ->
                    dialog.dismiss()
                    binding.tvShowBindInfo.text = "Binding Failed"
                    bindSwitchViewModel.resetDeviceStates()
                    val editor = prefs.edit()
                    editor.remove(MATTER_BIND_ITEM_NAME)
                    editor.remove("is_bind_successful")
                    editor.remove("binded_device_name")
                    editor.remove("unbind_enabled_for")
                    editor.apply()
                }
                .show()
        }

    }

    private fun showBindingMessage(message: String) {
        println("MATTER $message")
        //requireActivity().runOnUiThread { binding.statusBindingInfo.text = message }
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }


    inner class SwitchLightControlChipControllerCallback : GenericChipDeviceListener() {
        override fun onConnectDeviceComplete() {}

        override fun onCommissioningComplete(nodeId: Long, errorCode: Long) {
            Timber.tag(TAG).d("onCommissioningComplete for nodeId $nodeId: $errorCode")
            showMessage("Address update complete for nodeId $nodeId with code $errorCode")
        }

        override fun onNotifyChipConnectionClosed() {
            Timber.tag(TAG).d("onNotifyChipConnectionClosed")
        }

        override fun onCloseBleComplete() {
            Timber.tag(TAG).d("onCloseBleComplete")
        }

        override fun onError(error: Throwable?) {
            super.onError(error)
            Timber.tag(TAG).d("onError: $error")
        }

    }


    companion object {
        const val SWITCH_END_POINT = 1
        const val GROUPID = 0
        const val MATTER_BIND_ITEM_NAME = "matter_binded_name"
        private val TAG = Companion::class.java.simpleName.toString()
        fun newInstance(): MatterLightControlSwitchFragment =
            MatterLightControlSwitchFragment()
    }

    override fun invoke(p1: MatterScannedResultModel) {
        // DO NOT HANDLE ANYTHING HERE
    }


    suspend fun unbindDeviceBinding(
        context: Context,
        sourceNodeId: Long,         // Usually the Switch
        targetNodeId: Long,         // Usually the Light
        targetEndpointId: Int = 1,  // Light's endpoint
        targetClusterId: Int = 6,
        deviceName: String// OnOff or any bound cluster
    ) {
        val bindingCluster = ChipClusters.BindingCluster(
            ChipClient.getConnectedDevicePointer(context, sourceNodeId),
            SWITCH_END_POINT // or dynamically passed if needed
        )

        bindingCluster.readBindingAttribute(object :
            ChipClusters.BindingCluster.BindingAttributeCallback {
            override fun onSuccess(bindings: MutableList<ChipStructs.BindingClusterTargetStruct>?) {
                if (bindings.isNullOrEmpty()) {
                    removeProgress()
                    Timber.d("No bindings to remove")
                    bindSwitchViewModel.cancelUnbindingInProgress(deviceName)
                    return
                }

                val updatedBindings = bindings.filterNot { binding ->
                    val nid = binding.node.orElse(null)
                    val eid = binding.endpoint.orElse(null)
                    val cid = binding.cluster.orElse(null)

                    nid == targetNodeId && eid == targetEndpointId && cid?.toInt() == targetClusterId
                }

                Log.e("unBinding ACL List:", "${ArrayList(updatedBindings).toString()}")

                // Write back the filtered list
                bindingCluster.writeBindingAttribute(
                    object : ChipClusters.DefaultClusterCallback {
                        override fun onSuccess() {
                            removeProgress()
                            Timber.d("Unbinding success")
                            requireActivity().runOnUiThread {
                                Toast.makeText(
                                    requireContext(),
                                    "Unbinding Success",
                                    Toast.LENGTH_LONG
                                ).show()

                                // After unbind succeeds
                                val editor = prefs.edit()
                                editor.remove(MATTER_BIND_ITEM_NAME)
                                editor.remove("is_bind_successful")
                                editor.remove("binded_device_name")
                                editor.remove("unbind_enabled_for")
                                editor.apply()
                                bindSwitchViewModel.setUnbindingState(
                                    deviceName,
                                    inProgress = false
                                )

                                // Reset binding status text and UI
                                bindSwitchViewModel.setBindingStatus(
                                    getString(R.string.binded_to_nothing)
                                )
                                bindSwitchViewModel.enableUnbindForDevice("")
                            }

                        }

                        override fun onError(e: Exception?) {
                            removeProgress()
                            Timber.e("Unbinding write failed: $e")
                            bindSwitchViewModel.cancelUnbindingInProgress(deviceName)
                            requireActivity().runOnUiThread {
                                Toast.makeText(
                                    requireContext(),
                                    "Unbinding write failed",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    ArrayList(updatedBindings)
                )
            }

            override fun onError(error: Exception?) {
                Timber.e("Error reading binding attribute: $error")
                removeProgress()
                bindSwitchViewModel.cancelUnbindingInProgress(deviceName)
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Unbinding write failed", Toast.LENGTH_LONG)
                        .show()
                }
            }
        })
    }

    private fun removeProgress() {
        scope.launch(Dispatchers.Main) {
            if (customProgressDialog?.isShowing == true) {
                customProgressDialog?.dismiss()
            }
        }
    }

    private fun showMatterProgressDialog(message: String) {
        scope.launch(Dispatchers.Main) {
            customProgressDialog = CustomProgressDialog(requireContext())
            customProgressDialog!!.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            customProgressDialog!!.setMessage(message)
            customProgressDialog!!.setCanceledOnTouchOutside(false)
            customProgressDialog!!.show()
        }


    }

}