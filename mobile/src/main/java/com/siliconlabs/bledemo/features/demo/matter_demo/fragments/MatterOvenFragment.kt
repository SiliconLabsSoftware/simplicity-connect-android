package com.siliconlabs.bledemo.features.demo.matter_demo.fragments

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import chip.devicecontroller.ChipClusters
import chip.devicecontroller.ChipDeviceController
import chip.devicecontroller.ChipStructs
import chip.devicecontroller.ChipStructs.AccessControlClusterAccessControlEntryStruct
import chip.devicecontroller.GetConnectedDeviceCallbackJni
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.bluetooth.beacon_utils.eddystone.Constants.SCAN_TIMER
import com.siliconlabs.bledemo.databinding.FragmentMatterOvenBinding
import com.siliconlabs.bledemo.features.demo.matter_demo.activities.MatterDemoActivity
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannedResultFragment.Companion.RANGE_HOOD
import com.siliconlabs.bledemo.features.demo.matter_demo.model.MatterScannedResultModel
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.ChipClient
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.CustomProgressDialog
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.FragmentUtils
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.MessageDialogFragment
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.MatterTitleMessageOkDialogFragment
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.SharedPrefsUtils
import com.siliconlabs.bledemo.utils.CustomToastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Optional

class MatterOvenFragment : Fragment() {
    private lateinit var dialog: MessageDialogFragment
    private val dialogTag = "MessageDialog"
    private val deviceController: ChipDeviceController
        get() = ChipClient.getDeviceController(requireContext())
    private lateinit var mPrefs: SharedPreferences

    private lateinit var scope: CoroutineScope
    private lateinit var binding: FragmentMatterOvenBinding
    private lateinit var viewModel: MatterOvenViewModel
    private var ovenDeviceId: Long = INIT
    private var rangeHoodDeviceId: Long? = null
    private var onOffEndpointId: Int = OVEN_ON_OFF_ENDPOINT
    private var ovenModeEndpointId: Int = OVEN_MODE_CLUSTER_ENDPOINT
    private lateinit var model: MatterScannedResultModel
    private var customProgressDialog: CustomProgressDialog? = null
    private var selectedMode: OvenMode = OvenMode.GRILL
    private var supportedModes: Set<Int> = emptySet()
    private var currentModeId: Int? = null
    private var statusPollingJob: Job? = null
    private var lastKnownOvenOn: Boolean? = null


    enum class OvenMode(val displayName: String) {
        BAKE("Bake"),
        CONVECTION("Convection"),
        GRILL("Grill"),
        ROAST("Roast"),
        CLEAN("Clean"),
        CONVECTION_BAKE("Convection Bake"),
        CONVECTION_ROAST("Convection Roast"),
        WARMING("Warming"),
        PROOFING("Proofing")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mPrefs = requireContext().getSharedPreferences(
            MatterDemoActivity.MATTER_PREF, AppCompatActivity.MODE_PRIVATE
        )
        if (requireArguments() != null) {
            model =
                requireArguments().getParcelable(MatterLightFragment.Companion.ARG_DEVICE_MODEL)!!
            ovenDeviceId = model.deviceId
            val binding = SharedPrefsUtils.getOvenRangeHoodBinding(mPrefs, ovenDeviceId)
            rangeHoodDeviceId = binding.takeIf { it != SharedPrefsUtils.OVEN_NO_RANGEHOOD_BINDING }
            Timber.tag(TAG)
                .d("MatBT Oven DeviceID: $ovenDeviceId, RangeHood DeviceID: $rangeHoodDeviceId")
        }
        showMatterProgressDialog(getString(R.string.matter_device_status))
        CoroutineScope(Dispatchers.IO).launch {
            val resultInfo = checkForDeviceStatus()
            withContext(Dispatchers.Main) {
                if (resultInfo) {
                    println("Operation was successful")
                    removeProgress()
                }
            }
        }
    }

    private suspend fun checkForDeviceStatus(): Boolean {
        return withContext(Dispatchers.Default) {
            deviceController.getConnectedDevicePointer(
                ovenDeviceId,
                object : GetConnectedDeviceCallbackJni.GetConnectedDeviceCallback {
                    override fun onDeviceConnected(devicePointer: Long) {
                        println("----DevicePointer:$devicePointer")
                        model.isDeviceOnline = true
                        SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, ovenDeviceId, true)
                    }

                    override fun onConnectionFailure(nodeId: Long, error: java.lang.Exception?) {
                        model.isDeviceOnline = false
                        removeProgress()
                        println("----nodeId:$nodeId")
                        println("----error:${error!!.message}")
                        SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, ovenDeviceId, false)
                        showMessageDialog()
                    }
                })
            delay(SCAN_TIMER * COUNTDOWN_INTERVAL)
            true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = FragmentMatterOvenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scope = viewLifecycleOwner.lifecycleScope
        (activity as MatterDemoActivity).hideQRScanner()

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[MatterOvenViewModel::class.java]

        // Observe oven on/off status from ViewModel
        viewModel.isOvenOn.observe(viewLifecycleOwner) { isOn ->
            updatePowerUI()
        }

        // Observe Range Hood light and fan status
        viewModel.isRangeHoodLightOn.observe(viewLifecycleOwner) { isOn ->
            updateRangeHoodLightUI(isOn)
        }
        viewModel.isRangeHoodFanOn.observe(viewLifecycleOwner) { isOn ->
            updateRangeHoodFanUI(isOn)
        }

        // Disabled until initial oven state is read from device
        binding.btnOvenOff.isEnabled = false

        // Initially hide all mode buttons until we know which modes are supported
        val allModeButtons = mapOf(
            OvenMode.BAKE to binding.btnModeBake,
            OvenMode.CONVECTION to binding.btnModeConvection,
            OvenMode.GRILL to binding.btnModeGrill,
            OvenMode.ROAST to binding.btnModeRoast,
            OvenMode.CLEAN to binding.btnModeClean,
            OvenMode.CONVECTION_BAKE to binding.btnModeConvectionBake,
            OvenMode.CONVECTION_ROAST to binding.btnModeConvectionRoast,
            OvenMode.WARMING to binding.btnModeWarming,
            OvenMode.PROOFING to binding.btnModeProofing
        )
        allModeButtons.values.forEach { it.visibility = View.GONE }

        setupPowerControls()
        updateRangeHoodButtonState()
        setupRangeHoodBindingButton()
        updateUI()

        // Read initial state from device
        scope.launch {
            readOvenState()
            readRangeHoodStatus()
            readSupportedModes()
            readOvenMode()
        }

        // Start background polling task for On/Off status
        startStatusPolling()

        // Back handling
        requireActivity().onBackPressedDispatcher.addCallback(
            requireActivity(), onBackPressedCallback
        )
    }

    override fun onResume() {
        super.onResume()
        // Restore range hood binding state from persisted prefs when returning to the screen
        if (::binding.isInitialized) {
            val bindingId = SharedPrefsUtils.getOvenRangeHoodBinding(mPrefs, ovenDeviceId)
            rangeHoodDeviceId =
                bindingId.takeIf { it != SharedPrefsUtils.OVEN_NO_RANGEHOOD_BINDING }
            updateRangeHoodButtonState()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Stop background polling when view is destroyed
        stopStatusPolling()
    }

    /**
     * Start background task to periodically read On/Off status and update UI
     */
    private fun startStatusPolling() {
        stopStatusPolling()

        statusPollingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    readOvenState()
                    readRangeHoodStatus()
                    delay(STATUS_POLL_INTERVAL_MS)
                } catch (e: Exception) {
                    Timber.tag(TAG).e("MatBT Error in status polling: ${e.message}")
                    delay(STATUS_POLL_INTERVAL_MS)
                }
            }
        }
    }

    /**
     * Stop the background status polling task
     */
    private fun stopStatusPolling() {
        statusPollingJob?.cancel()
        statusPollingJob = null
    }

    private fun setupPowerControls() {
        binding.btnOvenOff.setOnClickListener {
            scope.launch {
                if (viewModel.getOvenOnStatus()) {
                    sendOffCommandClick()
                } else {
                    sendOnCommandClick()
                }
            }
        }
    }

    private fun updateRangeHoodButtonState() {
        val isBound = SharedPrefsUtils.isOvenBoundToRangeHood(mPrefs, ovenDeviceId)
        val red = ContextCompat.getColor(requireContext(), R.color.silabs_redtheme_primary_color)
        val white = ContextCompat.getColor(requireContext(), R.color.white)
        if (isBound) {
            binding.btnRangeHoodBinding.text = getString(R.string.matter_oven_unbind)
            binding.cardRangeHoodStatus.visibility = View.VISIBLE
        } else {
            binding.btnRangeHoodBinding.text = getString(R.string.matter_oven_bind_to_rangehood)
            binding.cardRangeHoodStatus.visibility = View.GONE
        }
    }

    private fun setupRangeHoodBindingButton() {
        binding.btnRangeHoodBinding.setOnClickListener {
            val isBound = SharedPrefsUtils.isOvenBoundToRangeHood(mPrefs, ovenDeviceId)
            val rangeHoodNodeId = SharedPrefsUtils.getOvenRangeHoodBinding(mPrefs, ovenDeviceId)
            val sheet = OvenRangeHoodBindingBottomSheetFragment.newInstance(
                ovenNodeId = ovenDeviceId,
                isBound = isBound,
                rangeHoodNodeId = rangeHoodNodeId
            )
            sheet.listener = object : OvenRangeHoodBindingBottomSheetFragment.Listener {
                override fun onUnbind() {
                    val boundRangeHoodNodeId =
                        SharedPrefsUtils.getOvenRangeHoodBinding(mPrefs, ovenDeviceId)
                    Timber.tag(TAG)
                        .d("MatBT Unbinding Oven (DeviceID: $ovenDeviceId) from RangeHood (DeviceID: $boundRangeHoodNodeId)")
                    SharedPrefsUtils.removeOvenRangeHoodBinding(mPrefs, ovenDeviceId)
                    rangeHoodDeviceId = null
                    scope.launch { unbindOvenFromRangeHood(boundRangeHoodNodeId) }
                    updateRangeHoodButtonState()
                }

                override fun onBindToDifferent() {
                    showRangeHoodPickerAndBind()
                }

                override fun onBind() {
                    showRangeHoodPickerAndBind()
                }

                override fun onCancel() {}
            }
            sheet.show(
                parentFragmentManager,
                OvenRangeHoodBindingBottomSheetFragment::class.java.simpleName
            )
        }
    }

    /**
     * Runs the bind flow without showing the former AlertDialog: one device = bind directly;
     * multiple devices = show picker bottom sheet. Underlying functionality unchanged.
     */
    private fun showRangeHoodPickerAndBind() {
        val devices =
            SharedPrefsUtils.retrieveSavedDevices(mPrefs).filter { it.deviceType == RANGE_HOOD }
        if (devices.isEmpty()) {
            CustomToastManager.showError(
                requireContext(),
                getString(R.string.matter_range_hood_device) + " " + getString(R.string.matter_device_commissioning_failed),
                CUSTOM_TOAST_TIME_OUT.toLong()
            )
            return
        }
        if (devices.size == 1) {
            performBindingToRangeHood(devices[0])
            return
        }
        val picker = RangeHoodPickerBottomSheetFragment.newInstance(ArrayList(devices))
        picker.listener = object : RangeHoodPickerBottomSheetFragment.Listener {
            override fun onRangeHoodSelected(device: MatterScannedResultModel) {
                performBindingToRangeHood(device)
            }
        }
        picker.show(
            parentFragmentManager,
            RangeHoodPickerBottomSheetFragment::class.java.simpleName
        )
    }

    private fun performBindingToRangeHood(selected: MatterScannedResultModel) {
        rangeHoodDeviceId = selected.deviceId
        SharedPrefsUtils.saveOvenRangeHoodBinding(mPrefs, ovenDeviceId, selected.deviceId)
        Timber.tag(TAG)
            .d("Binding Oven (DeviceID: $ovenDeviceId) to RangeHood (DeviceID: ${selected.deviceId})")
        scope.launch { bindOvenToRangeHood(selected.deviceId) }
        updateRangeHoodButtonState()
    }

    /**
     * Unbind Oven from RangeHood: clear Binding on Oven.
     * Tries endpoints [1, 3, 0] matching iOS DeviceBindingManager.
     * iOS does not remove ACL from RangeHood on unbind — only clears binding.
     */
    private suspend fun unbindOvenFromRangeHood(rangeHoodNodeId: Long?) {
        Timber.tag(TAG)
            .d("MatBT unbindOvenFromRangeHood - Oven DeviceID: $ovenDeviceId, RangeHood DeviceID: $rangeHoodNodeId")
        try {
            val ovenPtr = ChipClient.getConnectedDevicePointer(requireContext(), ovenDeviceId)
            unbindFromEndpoints(ovenPtr, BINDING_TRY_ENDPOINTS)
        } catch (e: Exception) {
            Timber.tag(TAG).e("MatBT unbindOvenFromRangeHood failed: ${e.message}")
        }
    }

    /**
     * Recursively try writing empty binding to Oven on each endpoint in [endpoints].
     * Matches iOS [self unbindFromOven:ovenDevice ovenNodeId:ovenNodeId tryEndpoints:@[@1, @3, @0]].
     */
    private fun unbindFromEndpoints(ovenPtr: Long, endpoints: List<Int>) {
        if (endpoints.isEmpty()) {
            Timber.tag(TAG).e("MatBT Binding cluster not found on any Oven endpoint for unbind")
            requireActivity().runOnUiThread {
                CustomToastManager.showError(requireContext(), "Unbind failed on all endpoints")
            }
            return
        }

        val endpoint = endpoints[0]
        val remaining = endpoints.drop(1)

        Timber.tag(TAG).d("MatBT Unbinding: writing empty binding on Oven endpoint $endpoint")
        try {
            val bindingCluster = ChipClusters.BindingCluster(ovenPtr, endpoint)
            bindingCluster.writeBindingAttribute(
                object : ChipClusters.DefaultClusterCallback {
                    override fun onSuccess() {
                        Timber.tag(TAG).d("MatBT Unbinding successful on Oven endpoint $endpoint!")
                        requireActivity().runOnUiThread {
                            CustomToastManager.show(
                                requireContext(),
                                "RangeHood unbound",
                                CUSTOM_TOAST_TIME_OUT.toLong()
                            )
                            MatterTitleMessageOkDialogFragment.newInstance(
                                getString(R.string.matter_oven_unbind_success_alert_title),
                                getString(R.string.matter_oven_unbind_success_alert_message)
                            ).show(
                                parentFragmentManager,
                                MatterTitleMessageOkDialogFragment.TAG
                            )
                        }
                    }

                    override fun onError(e: Exception?) {
                        Timber.tag(TAG).w("Unbind on endpoint $endpoint failed: ${e?.message}")
                        if (remaining.isNotEmpty()) {
                            Timber.tag(TAG).d("Trying next endpoint: ${remaining[0]}")
                            unbindFromEndpoints(ovenPtr, remaining)
                        } else {
                            requireActivity().runOnUiThread {
                                CustomToastManager.showError(
                                    requireContext(),
                                    "Unbind failed",
                                    CUSTOM_TOAST_TIME_OUT.toLong()
                                )
                                //Toast.makeText(requireContext(), "Unbind failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                ArrayList()
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e("MatBT Exception unbinding endpoint $endpoint: ${e.message}")
            if (remaining.isNotEmpty()) {
                unbindFromEndpoints(ovenPtr, remaining)
            }
        }
    }

    /**
     * Bind Oven to RangeHood following the iOS DeviceBindingManager pattern:
     * Step 1: Write ACL on RangeHood (target) FIRST to grant Oven permission
     * Step 2: Write Binding on Oven (source) SECOND in the ACL success callback
     *
     * This order is critical -- if Binding is written first, the Oven immediately
     * starts sending commands which the RangeHood rejects (status 0x3 UnsupportedAccess).
     */
    private suspend fun printDeviceAcl(deviceName: String, deviceNodeId: Long) {
        try {
            val devicePtr = ChipClient.getConnectedDevicePointer(requireContext(), deviceNodeId)
            val aclCluster = ChipClusters.AccessControlCluster(devicePtr, ACL_CLUSTER_ENDPOINT)

            Timber.tag(TAG)
                .d("MatBT====== Reading ACL from $deviceName (NodeId: $deviceNodeId) ======")
            aclCluster.readAclAttribute(object :
                ChipClusters.AccessControlCluster.AclAttributeCallback {
                override fun onSuccess(value: MutableList<AccessControlClusterAccessControlEntryStruct>?) {
                    Timber.tag(TAG).d("MatBT====== $deviceName ACL ======")
                    Timber.tag(TAG).d("MatBT $deviceName NodeId: $deviceNodeId")
                    Timber.tag(TAG).d("MatBT $deviceName ACL Entries count: ${value?.size ?: 0}")
                    value?.forEachIndexed { index, entry ->
                        Timber.tag(TAG).d(
                            "  $deviceName ACL[$index]: " +
                                    "fabricIndex=${entry.fabricIndex}, " +
                                    "privilege=${entry.privilege}, " +
                                    "authMode=${entry.authMode}, " +
                                    "subjects=${entry.subjects}, " +
                                    "targets=${entry.targets}"
                        )
                    }
                    Timber.tag(TAG).d("MatBT ====== End $deviceName ACL ======")
                }

                override fun onError(error: Exception?) {
                    Timber.tag(TAG)
                        .e("MatBT Failed to read ACL from $deviceName (NodeId: $deviceNodeId): ${error?.message}")
                }
            })
        } catch (e: Exception) {
            Timber.tag(TAG)
                .e("MatBT Exception reading ACL from $deviceName (NodeId: $deviceNodeId): ${e.message}")
        }
    }

    private suspend fun bindOvenToRangeHood(rangeHoodNodeId: Long) {
        Timber.tag(TAG)
            .d("MatBT bindOvenToRangeHood - Oven DeviceID: $ovenDeviceId, RangeHood DeviceID: $rangeHoodNodeId")
        Timber.tag(TAG).d("MatBT ==== FABRIC DEBUG START ====")
        Timber.tag(TAG).d("MatBT Controller Fabric Index = ${deviceController.fabricIndex}")
        Timber.tag(TAG).d("MatBT Controller NodeId = ${deviceController.controllerNodeId}")
        Timber.tag(TAG).d("MatBTOven NodeId = $ovenDeviceId")
        Timber.tag(TAG).d("MatBT RangeHood NodeId = $rangeHoodNodeId")
        Timber.tag(TAG).d("MatBT ==== FABRIC DEBUG END ====")

        printDeviceAcl("Oven", ovenDeviceId)
        printDeviceAcl("RangeHood", rangeHoodNodeId)

        try {
            val rangeHoodPtr =
                ChipClient.getConnectedDevicePointer(requireContext(), rangeHoodNodeId)
            val aclCluster = ChipClusters.AccessControlCluster(rangeHoodPtr, ACL_CLUSTER_ENDPOINT)

            Timber.tag(TAG).d("MatBT Step 1: Reading ACL from RangeHood (nodeId: $rangeHoodNodeId)")
            aclCluster.readAclAttribute(object :
                ChipClusters.AccessControlCluster.AclAttributeCallback {
                override fun onSuccess(value: MutableList<AccessControlClusterAccessControlEntryStruct>?) {
                    Timber.tag(TAG).d("MatBT ====== ACL READ from RangeHood ======")
                    Timber.tag(TAG).d("MatBT Entries count: ${value?.size ?: 0}")
                    value?.forEachIndexed { index, entry ->
                        Timber.tag(TAG).d(
                            "MatBT  ACL[$index] privilege=${entry.privilege}, authMode=${entry.authMode}, " +
                                    "subjects=${entry.subjects}, targets=${entry.targets}, fabricIndex=${entry.fabricIndex}"
                        )
                    }
                    Timber.tag(TAG).d("MatBT ====================================")

                    if (value.isNullOrEmpty()) {
                        Timber.tag(TAG).e("MatBT RangeHood ACL is empty or invalid")
                        requireActivity().runOnUiThread {
                            CustomToastManager.showError(
                                requireContext(),
                                "Failed to read RangeHood ACL",
                                CUSTOM_TOAST_TIME_OUT.toLong()
                            )
                            //Toast.makeText(requireContext(), "Failed to read RangeHood ACL", Toast.LENGTH_SHORT).show()
                        }
                        return
                    }

                    val ovenAlreadyAuthorized = value.any { entry ->
                        entry.subjects?.any { it.toLong() == ovenDeviceId } == true
                    }

                    if (ovenAlreadyAuthorized) {
                        Timber.tag(TAG)
                            .d("MatBT Oven already authorized on RangeHood. Writing binding directly.")
                        scope.launch { writeBindingOnOven(rangeHoodNodeId) }
                        return
                    }

                    // Preserve existing ACL entries as-is (matching Switch-Light pattern).
                    // Reconstructing entries can lose data and — critically — setting
                    // targets=arrayListOf() means "match nothing" while targets=null
                    // means "match all endpoints/clusters".
                    val aclWriteList = ArrayList<AccessControlClusterAccessControlEntryStruct>()
                    for (entry in value) {
                        aclWriteList.add(entry)
                    }

                    // Add Oven entry: targets=null grants unrestricted access
                    // (matching MatterLightControlSwitchFragment pattern)
                    val ovenEntry = AccessControlClusterAccessControlEntryStruct(
                        ACL_OPERATE_PRIVILEGE,
                        ACL_CASE_AUTH_MODE,
                        arrayListOf(ovenDeviceId),
                        null,
                        Optional.of(deviceController.fabricIndex),
                        deviceController.fabricIndex
                    )
                    aclWriteList.add(ovenEntry)

                    Timber.tag(TAG).d("====== ACL WRITE to RangeHood ======")
                    aclWriteList.forEachIndexed { index, entry ->
                        Timber.tag(TAG).d(
                            "MatBT  ACL[$index] privilege=${entry.privilege}, authMode=${entry.authMode}, " +
                                    "subjects=${entry.subjects}, targets=${entry.targets}, fabricIndex=${entry.fabricIndex}"
                        )
                    }
                    Timber.tag(TAG).d("====================================")
                    aclCluster.writeAclAttribute(
                        object : ChipClusters.DefaultClusterCallback {
                            override fun onSuccess() {
                                Timber.tag(TAG)
                                    .d("MatBT Step 1 done: ACL write success on RangeHood")
                                verifyAclAndWriteBinding(aclCluster, rangeHoodNodeId)
                            }

                            override fun onError(e: Exception?) {
                                Timber.tag(TAG)
                                    .e("MatBT ACL write failed on RangeHood: ${e?.message}")
                                requireActivity().runOnUiThread {
                                    CustomToastManager.showError(
                                        requireContext(),
                                        "ACL write failed: ${e?.message}",
                                        CUSTOM_TOAST_TIME_OUT.toLong()
                                    )
                                }
                            }
                        },
                        aclWriteList
                    )
                }

                override fun onError(error: Exception?) {
                    Timber.tag(TAG).e("Failed to read ACL from RangeHood: ${error?.message}")
                    requireActivity().runOnUiThread {
//                        Toast.makeText(
//                            requireContext(),
//                            "Failed to read RangeHood ACL",
//                            Toast.LENGTH_SHORT
//                        ).show()
                        CustomToastManager.showError(
                            requireContext(),
                            "Failed to read RangeHood ACL",
                            CUSTOM_TOAST_TIME_OUT.toLong()
                        )
                    }
                }
            })
        } catch (e: Exception) {
            Timber.tag(TAG).e("bindOvenToRangeHood failed: ${e.message}")
        }
    }

    /**
     * Read back ACL from RangeHood after write to verify it was applied correctly,
     * then proceed to write Binding on Oven.
     */
    private fun verifyAclAndWriteBinding(
        aclCluster: ChipClusters.AccessControlCluster,
        rangeHoodNodeId: Long
    ) {
        aclCluster.readAclAttribute(object :
            ChipClusters.AccessControlCluster.AclAttributeCallback {
            override fun onSuccess(value: MutableList<AccessControlClusterAccessControlEntryStruct>?) {
                Timber.tag(TAG).d("MatBT ====== ACL VERIFY (read-back) from RangeHood ======")
                Timber.tag(TAG).d("MatBT Entries count: ${value?.size ?: 0}")
                value?.forEach {
                    Timber.d("MatBT ACL entry fabric index = ${it.fabricIndex}")
                }
                value?.forEachIndexed { index, entry ->
                    Timber.tag(TAG).d(
                        "MatBT  ACL[$index] privilege=${entry.privilege}, authMode=${entry.authMode}, " +
                                "subjects=${entry.subjects}, targets=${entry.targets}, fabricIndex=${entry.fabricIndex}"
                    )
                }

                val ovenHasAccess = value?.any { entry ->
                    entry.subjects?.any { it.toLong() == ovenDeviceId } == true
                } ?: false
                Timber.tag(TAG)
                    .d("MatBT Oven (DeviceID: $ovenDeviceId) authorized on RangeHood: $ovenHasAccess")
                Timber.tag(TAG).d("====================================================")

                if (ovenHasAccess) {
                    scope.launch { writeBindingOnOven(rangeHoodNodeId) }
                } else {
                    Timber.tag(TAG)
                        .e("MatBT ACL verify failed: Oven not found in RangeHood ACL after write!")
                    requireActivity().runOnUiThread {
//                        Toast.makeText(
//                            requireContext(),
//                            "ACL verification failed",
//                            Toast.LENGTH_SHORT
//                        ).show()
                        CustomToastManager.showError(
                            requireContext(),
                            "ACL verification failed",
                            CUSTOM_TOAST_TIME_OUT.toLong()
                        )
                    }
                }
            }

            override fun onError(error: Exception?) {
                Timber.tag(TAG)
                    .w("MatBT ACL verify read failed: ${error?.message}, proceeding with binding anyway")
                scope.launch { writeBindingOnOven(rangeHoodNodeId) }
            }
        })
    }

    /**
     * Step 2: Write Binding on Oven (source) targeting RangeHood Light.
     * The binding sends OnOff commands to RangeHood endpoint 2 (Light) via board-to-board.
     * Fan control is handled at app level (sendRangeHoodFanCommand) because:
     *   - RangeHood endpoint 1 only has FanControl cluster (no OnOff)
     *   - Oven binding only forwards OnOff commands, not FanControl
     * Tries endpoints [1, 3, 0] for Binding cluster on Oven.
     */
    private suspend fun writeBindingOnOven(rangeHoodNodeId: Long) {
        Timber.tag(TAG)
            .d("Step 2: Writing Binding on Oven (DeviceID: $ovenDeviceId) -> RangeHood (DeviceID: $rangeHoodNodeId)")
        try {
            val ovenPtr = ChipClient.getConnectedDevicePointer(requireContext(), ovenDeviceId)

            Timber.tag(TAG).d("MatBT Reading Oven ACL to determine device-side fabric index …")
            val aclCluster = ChipClusters.AccessControlCluster(ovenPtr, ACL_CLUSTER_ENDPOINT)
            aclCluster.readAclAttribute(object :
                ChipClusters.AccessControlCluster.AclAttributeCallback {
                override fun onSuccess(value: MutableList<AccessControlClusterAccessControlEntryStruct>?) {
                    val ovenFabricIndex = value?.firstOrNull()?.fabricIndex
                        ?: deviceController.fabricIndex
                    Timber.tag(TAG).d(
                        "MatBT Oven device-side fabricIndex = $ovenFabricIndex " +
                                "(controller fabricIndex = ${deviceController.fabricIndex})"
                    )
                    buildAndWriteBinding(ovenPtr, rangeHoodNodeId, ovenFabricIndex)
                }

                override fun onError(error: Exception?) {
                    Timber.tag(TAG).w(
                        "MatBT Failed to read Oven ACL for fabric index: ${error?.message}. " +
                                "Falling back to controller fabricIndex ${deviceController.fabricIndex}"
                    )
                    buildAndWriteBinding(ovenPtr, rangeHoodNodeId, deviceController.fabricIndex)
                }
            })
        } catch (e: Exception) {
            Timber.tag(TAG).e("MatBT writeBindingOnOven failed: ${e.message}")
        }
    }

    private fun buildAndWriteBinding(ovenPtr: Long, rangeHoodNodeId: Long, fabricIndex: Int) {
        val lightTarget = ChipStructs.BindingClusterTargetStruct(
            Optional.of(rangeHoodNodeId),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            null
        ).apply {
            endpoint = Optional.of(RANGEHOOD_ON_OFF_ENDPOINT)
            cluster = Optional.of(ON_OFF_CLUSTER_ID)
            this.fabricIndex = fabricIndex
        }

        val fanTarget = ChipStructs.BindingClusterTargetStruct(
            Optional.of(rangeHoodNodeId),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            null
        ).apply {
            endpoint = Optional.of(RANGEHOOD_FAN_ENDPOINT)
            cluster = Optional.of(ON_OFF_CLUSTER_ID)
            this.fabricIndex = fabricIndex
        }

        val bindingList = ArrayList<ChipStructs.BindingClusterTargetStruct>().apply {
            add(lightTarget)
            add(fanTarget)
        }
        Timber.tag(TAG).d(
            "MatBT Binding target [Light]: node=$rangeHoodNodeId, endpoint=$RANGEHOOD_ON_OFF_ENDPOINT, " +
                    "cluster=$ON_OFF_CLUSTER_ID, fabricIndex=$fabricIndex"
        )
        Timber.tag(TAG).d(
            "MatBT Binding target [Fan]: node=$rangeHoodNodeId, endpoint=$RANGEHOOD_FAN_ENDPOINT, " +
                    "cluster=$ON_OFF_CLUSTER_ID, fabricIndex=$fabricIndex"
        )
        writeBindingOnEndpoints(ovenPtr, bindingList, BINDING_TRY_ENDPOINTS, rangeHoodNodeId)
    }

    /**
     * Recursively try writing binding on Oven at each endpoint in [endpoints].
     * Matches iOS [self writeBindingOnOven:ovenDevice target:target ... tryEndpoints:@[@1, @3, @0]].
     */
    private fun writeBindingOnEndpoints(
        ovenPtr: Long,
        bindingList: ArrayList<ChipStructs.BindingClusterTargetStruct>,
        endpoints: List<Int>,
        rangeHoodNodeId: Long
    ) {
        if (endpoints.isEmpty()) {
            Timber.tag(TAG).e("MatBT Binding cluster not found on any Oven endpoint")
            requireActivity().runOnUiThread {

                CustomToastManager.showError(
                    requireContext(),
                    "Binding failed on all endpoints",
                    CUSTOM_TOAST_TIME_OUT.toLong()
                )
            }
            return
        }

        val endpoint = endpoints[0]
        val remaining = endpoints.drop(1)

        Timber.tag(TAG)
            .d("Writing Binding on Oven endpoint $endpoint: target RangeHood $rangeHoodNodeId endpoint $RANGEHOOD_ON_OFF_ENDPOINT")
        try {
            val bindingCluster = ChipClusters.BindingCluster(ovenPtr, endpoint)
            bindingCluster.writeBindingAttribute(
                object : ChipClusters.DefaultClusterCallback {
                    override fun onSuccess() {
                        Timber.tag(TAG).d("Binding successful on Oven endpoint $endpoint!")
                        requireActivity().runOnUiThread {
                            updateRangeHoodButtonState()
                            MatterTitleMessageOkDialogFragment.newInstance(
                                getString(R.string.matter_oven_unbind_success_alert_title),
                                getString(R.string.matter_oven_binding_success_message)
                            ).show(
                                parentFragmentManager,
                                MatterTitleMessageOkDialogFragment.TAG
                            )
                        }
                        Timber.tag(TAG)
                            .d("MatBT ACL + Binding complete — writing fanMode=$FAN_MODE_ON to RangeHood")
                        scope.launch {
                            sendRangeHoodFanCommand(true)
                            delay(500)
                            readRangeHoodStatus()
                        }
                    }

                    override fun onError(e: Exception?) {
                        Timber.tag(TAG)
                            .w("MatBT Binding failed on endpoint $endpoint: ${e?.message}")
                        if (remaining.isNotEmpty()) {
                            Timber.tag(TAG).d("MatBT Trying next endpoint: ${remaining[0]}")
                            writeBindingOnEndpoints(
                                ovenPtr,
                                bindingList,
                                remaining,
                                rangeHoodNodeId
                            )
                        } else {
                            requireActivity().runOnUiThread {
                                CustomToastManager.showError(
                                    requireContext(),
                                    "Binding failed",
                                    CUSTOM_TOAST_TIME_OUT.toLong()
                                )
                            }
                        }
                    }
                },
                bindingList
            )
        } catch (e: Exception) {
            Timber.tag(TAG).w("MatBT Exception on endpoint $endpoint: ${e.message}")
            if (remaining.isNotEmpty()) {
                writeBindingOnEndpoints(ovenPtr, bindingList, remaining, rangeHoodNodeId)
            }
        }
    }

    private fun setupModeButtons() {
        val modeButtons = mapOf(
            OvenMode.BAKE to binding.btnModeBake,
            OvenMode.CONVECTION to binding.btnModeConvection,
            OvenMode.GRILL to binding.btnModeGrill,
            OvenMode.ROAST to binding.btnModeRoast,
            OvenMode.CLEAN to binding.btnModeClean,
            OvenMode.CONVECTION_BAKE to binding.btnModeConvectionBake,
            OvenMode.CONVECTION_ROAST to binding.btnModeConvectionRoast,
            OvenMode.WARMING to binding.btnModeWarming,
            OvenMode.PROOFING to binding.btnModeProofing
        )

        modeButtons.forEach { (mode, button) ->
            button.setOnClickListener {
                selectedMode = mode
                updateModeUI(modeButtons)
                scope.launch {
                    sendModeCommand(mode)
                }
            }
        }
    }


    private fun updateModeButtonsVisibility(modeButtons: Map<OvenMode, View>) {
        modeButtons.forEach { (mode, button) ->
            val modeValue = getModeValue(mode)
            val isSupported = supportedModes.contains(modeValue)
            if (isSupported) {
                button.visibility = View.VISIBLE
                Timber.tag(TAG)
                    .d("MatBT Mode ${mode.displayName} (value: $modeValue) is supported - showing button")
            } else {
                button.visibility = View.GONE
                Timber.tag(TAG)
                    .d("MatBT Mode ${mode.displayName} (value: $modeValue) is NOT supported - hiding button")
            }
        }
        Timber.tag(TAG)
            .d("MatBT Updated visibility for ${supportedModes.size} supported modes out of ${modeButtons.size} total modes")
    }

    private fun updatePowerUI() {
        val isOvenOn = viewModel.getOvenOnStatus()
        val redPrimary =
            ContextCompat.getColor(requireContext(), R.color.silabs_redtheme_primary_color)
        val green = ContextCompat.getColor(requireContext(), R.color.silabs_green)
        val red = ContextCompat.getColor(requireContext(), R.color.silabs_redtheme_primary_color)
        binding.btnOvenOff.isEnabled = true
        binding.btnOvenOff.alpha = 1.0f
        if (isOvenOn) {
            binding.btnOvenOff.text = getString(R.string.matter_oven_off)
            binding.tvOvenStatus.text = getString(R.string.matter_oven_on)
            binding.tvOvenStatus.setTextColor(green)
            binding.btnOvenOff.isEnabled = true
            binding.ivOvenIcon.setColorFilter(
                redPrimary,
                android.graphics.PorterDuff.Mode.SRC_IN
            )
        } else {
            binding.btnOvenOff.text = getString(R.string.matter_oven_off)
            binding.tvOvenStatus.text = getString(R.string.matter_oven_off)
            binding.btnOvenOff.isEnabled = false
            binding.tvOvenStatus.setTextColor(red)
            binding.ivOvenIcon.setColorFilter(
                ContextCompat.getColor(requireContext(), R.color.silabs_grey),
                android.graphics.PorterDuff.Mode.SRC_IN
            )
        }
    }

    private fun updateModeUI(modeButtons: Map<OvenMode, View>) {
        val checkmarkViews = mapOf(
            OvenMode.BAKE to binding.ivCheckmarkBake,
            OvenMode.CONVECTION to binding.ivCheckmarkConvection,
            OvenMode.GRILL to binding.ivCheckmarkGrill,
            OvenMode.ROAST to binding.ivCheckmarkRoast,
            OvenMode.CLEAN to binding.ivCheckmarkClean,
            OvenMode.CONVECTION_BAKE to binding.ivCheckmarkConvectionBake,
            OvenMode.CONVECTION_ROAST to binding.ivCheckmarkConvectionRoast,
            OvenMode.WARMING to binding.ivCheckmarkWarming,
            OvenMode.PROOFING to binding.ivCheckmarkProofing
        )

        val txtModeLabel = mapOf(
            OvenMode.BAKE to binding.tvModeBake,
            OvenMode.CONVECTION to binding.tvModeConvection,
            OvenMode.GRILL to binding.tvModeGrill,
            OvenMode.ROAST to binding.tvModeRoast,
            OvenMode.CLEAN to binding.tvModeClean,
            OvenMode.CONVECTION_BAKE to binding.tvModeConvectionBake,
            OvenMode.CONVECTION_ROAST to binding.tvModeConvectionRoast,
            OvenMode.WARMING to binding.tvModeWarming,
            OvenMode.PROOFING to binding.tvModeProofing
        )
        modeButtons.forEach { (mode, button) ->
            val btn = button as? androidx.appcompat.widget.AppCompatButton
            val imgBtn = button as? androidx.appcompat.widget.AppCompatImageButton


            val checkmark = checkmarkViews[mode]
            val tvModeLabel = txtModeLabel[mode]

            val whiteColor = ContextCompat.getColor(requireContext(), R.color.white)
            val redColor =
                ContextCompat.getColor(requireContext(), R.color.silabs_redtheme_primary_color)
            val masalaColor = ContextCompat.getColor(requireContext(), R.color.masala)

            if (mode == selectedMode) {
                btn?.let {
                    it.alpha = 1.0f
                    it.setBackgroundColor(redColor)
                    it.setTextColor(whiteColor)
                    val whiteColorStateList = android.content.res.ColorStateList.valueOf(whiteColor)
                    it.compoundDrawableTintList = whiteColorStateList
                }
                imgBtn?.let {
                    it.alpha = 1.0f
                    it.setBackgroundColor(redColor)
                    it.setColorFilter(whiteColor, android.graphics.PorterDuff.Mode.SRC_IN)
                }
                checkmark?.visibility = View.VISIBLE
                checkmark?.let {
                    it.alpha = 1.0f
                    it.setColorFilter(whiteColor, android.graphics.PorterDuff.Mode.SRC_IN)
                }
                tvModeLabel?.let {
                    it.alpha = 1.0f
                    it.setTextColor(whiteColor)
                }
            } else {
                btn?.let {
                    it.alpha = 1.0f
                    it.setBackgroundColor(whiteColor)
                    it.setTextColor(masalaColor)
                    val redTint = android.content.res.ColorStateList.valueOf(redColor)
                    it.compoundDrawableTintList = redTint
                }
                imgBtn?.let {
                    it.alpha = 1.0f
                    it.setBackgroundColor(whiteColor)
                    it.setColorFilter(redColor, android.graphics.PorterDuff.Mode.SRC_IN)
                }
                checkmark?.visibility = View.GONE
                tvModeLabel?.let {
                    it.alpha = 1.0f
                    it.setTextColor(masalaColor)
                }
            }
        }
    }

    private fun updateUI() {
        binding.tvOvenModeLabel.text = getString(R.string.matter_oven_mode_label)
        updatePowerUI()
        val modeButtons = mapOf(
            OvenMode.BAKE to binding.btnModeBake,
            OvenMode.CONVECTION to binding.btnModeConvection,
            OvenMode.GRILL to binding.btnModeGrill,
            OvenMode.ROAST to binding.btnModeRoast,
            OvenMode.CLEAN to binding.btnModeClean,
            OvenMode.CONVECTION_BAKE to binding.btnModeConvectionBake,
            OvenMode.CONVECTION_ROAST to binding.btnModeConvectionRoast,
            OvenMode.WARMING to binding.btnModeWarming,
            OvenMode.PROOFING to binding.btnModeProofing
        )
        updateModeUI(modeButtons)
    }

    private fun showMessageDialog() {
        try {
            if (isAdded && !requireActivity().isFinishing) {
                requireActivity().runOnUiThread {
                    if (!MessageDialogFragment.isDialogShowing()) {
                        dialog = MessageDialogFragment()
                        dialog.setMessage(getString(R.string.matter_device_offline_text))
                        dialog.setOnDismissListener {
                            removeProgress()
                            if (requireActivity().supportFragmentManager.backStackEntryCount > 0) {
                                requireActivity().supportFragmentManager.popBackStack()
                            } else {
                                FragmentUtils.getHost(
                                    this@MatterOvenFragment, CallBackHandler::class.java
                                ).onBackHandler()
                            }
                        }
                        val transaction: FragmentTransaction =
                            requireActivity().supportFragmentManager.beginTransaction()

                        dialog.show(transaction, dialogTag)
                    }
                }
            } else {
                Timber.e("MatBT device offline")
                requireActivity().runOnUiThread {
                    CustomToastManager.showError(
                        requireContext(),
                        "Device Offline", CUSTOM_TOAST_TIME_OUT.toLong()
                    )
                }

            }
        } catch (e: Exception) {
            Timber.tag(TAG).e("MatBT device offline device offline ${e.message}")
        }
    }

    private fun removeProgress() {
        if (customProgressDialog?.isShowing == true) {
            customProgressDialog?.dismiss()
        }
    }

    private fun showMatterProgressDialog(message: String) {
        customProgressDialog = CustomProgressDialog(requireContext())
        customProgressDialog!!.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        customProgressDialog!!.setMessage(message)
        customProgressDialog!!.setCanceledOnTouchOutside(false)
        customProgressDialog!!.show()
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (isAdded) {
                if (requireActivity().supportFragmentManager.backStackEntryCount > 0) {
                    requireActivity().supportFragmentManager.popBackStack()
                } else {
                    FragmentUtils.getHost(
                        this@MatterOvenFragment,
                        MatterLightFragment.CallBackHandler::class.java
                    ).onBackHandler()
                }
            }
        }
    }

    interface CallBackHandler {
        fun onBackHandler()
    }

    private suspend fun getOnOffClusterForDevice(): ChipClusters.OnOffCluster {
        return ChipClusters.OnOffCluster(
            ChipClient.getConnectedDevicePointer(requireContext(), ovenDeviceId),
            onOffEndpointId
        )
    }

    private suspend fun getOvenModeCluster(): ChipClusters.OvenModeCluster {
        return ChipClusters.OvenModeCluster(
            ChipClient.getConnectedDevicePointer(requireContext(), ovenDeviceId),
            OVEN_MODE_CLUSTER_ENDPOINT
        )
    }

    private suspend fun sendOnCommandClick() {
        getOnOffClusterForDevice().on(object : ChipClusters.DefaultClusterCallback {
            override fun onSuccess() {
                viewModel.setOvenOnStatus(true)
                SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, ovenDeviceId, true)
                Timber.tag(TAG)
                    .d("MatBT Oven ON success — sending RangeHood Fan ON + reading RangeHood status")
                viewModel.setRangeHoodLightStatus(true)
                scope.launch {
                    sendRangeHoodFanCommand(true)
                    delay(500)
                    readRangeHoodStatus()
                }
            }

            override fun onError(ex: Exception) {
                SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, ovenDeviceId, false)
                Timber.tag(TAG).e("ON command failure: $ex")
                showMessageDialog()
            }
        })
    }

    private suspend fun sendOffCommandClick() {
        getOnOffClusterForDevice().off(object : ChipClusters.DefaultClusterCallback {
            override fun onSuccess() {
                viewModel.setOvenOnStatus(false)
                SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, ovenDeviceId, true)
                Timber.tag(TAG)
                    .d("MatBT Oven OFF success — sending RangeHood Fan OFF + reading RangeHood status")
                viewModel.setRangeHoodLightStatus(false)
                scope.launch {
                    sendRangeHoodFanCommand(false)
                    delay(500)
                    readRangeHoodStatus()
                }
            }

            override fun onError(ex: Exception) {
                SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, ovenDeviceId, false)
                Timber.tag(TAG).e("OFF command failure: $ex")
                showMessageDialog()
            }
        })
    }

    /**
     * Send FanControl command to the bound RangeHood.
     * The Oven binding handles Light (OnOff on endpoint 2) board-to-board.
     * Fan must be controlled at app level because RangeHood endpoint 1 only has
     * FanControl cluster (no OnOff), and binding only forwards OnOff commands.
     *
     * FanMode values: 0=Off, 4=On (matching MatterRangeHoodFragment)
     */
    private suspend fun sendRangeHoodFanCommand(turnOn: Boolean) {
        val rhNodeId =
            rangeHoodDeviceId ?: SharedPrefsUtils.getOvenRangeHoodBinding(mPrefs, ovenDeviceId)
        if (rhNodeId == null || rhNodeId == SharedPrefsUtils.OVEN_NO_RANGEHOOD_BINDING) {
            Timber.tag(TAG).d("MatBT No RangeHood bound, skipping fan command")
            return
        }

        val isBound = SharedPrefsUtils.isOvenBoundToRangeHood(mPrefs, ovenDeviceId)
        if (!isBound) {
            Timber.tag(TAG).d("MatBT Oven not bound to RangeHood, skipping fan command")
            return
        }

        val fanMode = if (turnOn) FAN_MODE_ON else FAN_MODE_OFF
        Timber.tag(TAG)
            .d("MatBT Sending FanControl command to RangeHood (DeviceID: $rhNodeId) endpoint $RANGEHOOD_FAN_ENDPOINT, fanMode=$fanMode")

        try {
            val rhPtr = ChipClient.getConnectedDevicePointer(requireContext(), rhNodeId)
            val fanCluster = ChipClusters.FanControlCluster(rhPtr, RANGEHOOD_FAN_ENDPOINT)

            fanCluster.writeFanModeAttribute(
                object : ChipClusters.DefaultClusterCallback {
                    override fun onSuccess() {
                        Timber.tag(TAG)
                            .d("MatBT RangeHood Fan ${if (turnOn) "ON" else "OFF"} success")
                        viewModel.setRangeHoodFanStatus(turnOn)
                    }

                    override fun onError(ex: Exception?) {
                        Timber.tag(TAG).e("MatBT RangeHood Fan command failed: ${ex?.message}")
                    }
                },
                fanMode
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e("MatBT sendRangeHoodFanCommand failed: ${e.message}")
        }
    }

    private suspend fun sendModeCommand(mode: OvenMode) {
        val modeValue = getModeValue(mode)
        getOvenModeCluster().changeToMode(
            object : ChipClusters.OvenModeCluster.ChangeToModeResponseCallback {
                override fun onSuccess(status: Int, statusText: java.util.Optional<String?>) {
                    if (status == 0) {
                        selectedMode = mode
                        currentModeId = modeValue
                        requireActivity().runOnUiThread {
                            val modeButtons = mapOf(
                                OvenMode.BAKE to binding.btnModeBake,
                                OvenMode.CONVECTION to binding.btnModeConvection,
                                OvenMode.GRILL to binding.btnModeGrill,
                                OvenMode.ROAST to binding.btnModeRoast,
                                OvenMode.CLEAN to binding.btnModeClean,
                                OvenMode.CONVECTION_BAKE to binding.btnModeConvectionBake,
                                OvenMode.CONVECTION_ROAST to binding.btnModeConvectionRoast,
                                OvenMode.WARMING to binding.btnModeWarming,
                                OvenMode.PROOFING to binding.btnModeProofing
                            )
                            updateModeUI(modeButtons)
                        }
                        SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, ovenDeviceId, true)
                    } else {
                        Timber.tag(TAG).w("ChangeToMode returned non-zero status: $status")
                    }
                }

                override fun onError(ex: Exception) {
                    SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, ovenDeviceId, false)
                    Timber.tag(TAG).e("ChangeToMode command failure: $ex")
                    showMessageDialog()
                }
            },
            modeValue
        )
    }

    private fun getModeValue(mode: OvenMode): Int {
        return when (mode) {
            OvenMode.BAKE -> 0
            OvenMode.CONVECTION -> 1
            OvenMode.GRILL -> 2
            OvenMode.ROAST -> 3
            OvenMode.CLEAN -> 4
            OvenMode.CONVECTION_BAKE -> 5
            OvenMode.CONVECTION_ROAST -> 6
            OvenMode.WARMING -> 7
            OvenMode.PROOFING -> 8
        }
    }

    private fun getModeFromValue(value: Int): OvenMode {
        return when (value) {
            0 -> OvenMode.BAKE
            1 -> OvenMode.CONVECTION
            2 -> OvenMode.GRILL
            3 -> OvenMode.ROAST
            4 -> OvenMode.CLEAN
            5 -> OvenMode.CONVECTION_BAKE
            6 -> OvenMode.CONVECTION_ROAST
            7 -> OvenMode.WARMING
            8 -> OvenMode.PROOFING
            else -> OvenMode.GRILL
        }
    }

    private suspend fun readOvenState() {
        getOnOffClusterForDevice().readOnOffAttribute(object :
            ChipClusters.BooleanAttributeCallback {

            override fun onError(exc: Exception) {
                SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, ovenDeviceId, false)
                Timber.tag(TAG).e("Read OnOff attribute failure: $exc")
                requireActivity().runOnUiThread {
                    CustomToastManager.showError(
                        requireContext(),
                        "Read OnOff attribute failure: $exc",
                        CUSTOM_TOAST_TIME_OUT.toLong()
                    )
                }
            }

            override fun onSuccess(value: Boolean) {
                val ovenOn = value ?: false
                viewModel.setOvenOnStatus(ovenOn)
                SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, ovenDeviceId, true)

                if (lastKnownOvenOn != ovenOn) {
                    Timber.tag(TAG).d(
                        "MatBT Oven state changed: ${lastKnownOvenOn} -> $ovenOn — " +
                                "sending FanControl command to RangeHood"
                    )
                    lastKnownOvenOn = ovenOn
                    val isBound = SharedPrefsUtils.isOvenBoundToRangeHood(mPrefs, ovenDeviceId)
                    if (isBound) {
                        scope.launch {
                            sendRangeHoodFanCommand(ovenOn)
                            delay(500)
                            readRangeHoodStatus()
                        }
                    }
                }
            }
        })
    }

    /**
     * Read the Range Hood's Light (OnOff at ep2) and Fan (FanMode at ep1) status.
     * Only called when the Oven is bound to a Range Hood.
     */
    private suspend fun readRangeHoodStatus() {
        val rhNodeId =
            rangeHoodDeviceId ?: SharedPrefsUtils.getOvenRangeHoodBinding(mPrefs, ovenDeviceId)
        if (rhNodeId == null || rhNodeId == SharedPrefsUtils.OVEN_NO_RANGEHOOD_BINDING) {
            Timber.tag(TAG).d("No RangeHood bound, skipping status read")
            return
        }
        val isBound = SharedPrefsUtils.isOvenBoundToRangeHood(mPrefs, ovenDeviceId)
        if (!isBound) {
            Timber.tag(TAG).d("Oven not bound to RangeHood, skipping status read")
            return
        }

        try {
            val rhPtr = ChipClient.getConnectedDevicePointer(requireContext(), rhNodeId)

            // Read Light status (OnOff cluster on endpoint 2)
            val onOffCluster = ChipClusters.OnOffCluster(rhPtr, RANGEHOOD_ON_OFF_ENDPOINT)
            onOffCluster.readOnOffAttribute(object : ChipClusters.BooleanAttributeCallback {
                override fun onSuccess(value: Boolean) {
                    Timber.tag(TAG).d("RangeHood Light status: ${if (value) "ON" else "OFF"}")
                    viewModel.setRangeHoodLightStatus(value)
                }

                override fun onError(ex: Exception) {
                    Timber.tag(TAG).e("Read RangeHood Light status failure: ${ex.message}")
                }
            })

            // Read Fan status (FanMode attribute on endpoint 1)
            val fanCluster = ChipClusters.FanControlCluster(rhPtr, RANGEHOOD_FAN_ENDPOINT)
            fanCluster.readFanModeAttribute(object : ChipClusters.IntegerAttributeCallback {
                override fun onSuccess(value: Int) {
                    val fanOn = value > 0
                    Timber.tag(TAG).d("RangeHood Fan status: fanMode=$value, isOn=$fanOn")
                    viewModel.setRangeHoodFanStatus(fanOn)
                }

                override fun onError(ex: Exception) {
                    Timber.tag(TAG).e("Read RangeHood Fan status failure: ${ex.message}")
                }
            })
        } catch (e: Exception) {
            Timber.tag(TAG).e("readRangeHoodStatus failed: ${e.message}")
        }
    }

    private fun updateRangeHoodLightUI(isOn: Boolean) {
        if (!isAdded) return
        val yellow = ContextCompat.getColor(requireContext(), R.color.silabs_rangehood_yellow)
        val masala = ContextCompat.getColor(requireContext(), R.color.masala)
        val grey = ContextCompat.getColor(requireContext(), R.color.silabs_grey)
        binding.tvRangeHoodLightStatus.text = getString(
            if (isOn) R.string.matter_range_hood_on else R.string.matter_range_hood_off
        )
        binding.tvRangeHoodLightStatus.setTextColor(if (isOn) masala else grey)
        binding.ivRangeHoodIcon.setColorFilter(
            if (isOn) yellow else grey,
            android.graphics.PorterDuff.Mode.SRC_IN
        )

    }

    private fun updateRangeHoodFanUI(isOn: Boolean) {
        if (!isAdded) return
        val red = ContextCompat.getColor(requireContext(), R.color.silabs_redtheme_primary_color)
        val masala = ContextCompat.getColor(requireContext(), R.color.masala)
        val grey = ContextCompat.getColor(requireContext(), R.color.silabs_grey)
        binding.tvRangeHoodFanStatus.text = getString(
            if (isOn) R.string.matter_range_hood_fan_on else R.string.matter_range_hood_fan_off
        )
        binding.tvRangeHoodFanStatus.setTextColor(if (isOn) masala else grey)
        binding.ivRangeHoodFanIcon.setColorFilter(
            if (isOn) red else grey,
            android.graphics.PorterDuff.Mode.SRC_IN
        )
    }

    private suspend fun readSupportedModes() {
        getOvenModeCluster().readSupportedModesAttribute(object :
            ChipClusters.OvenModeCluster.SupportedModesAttributeCallback {
            override fun onSuccess(value: MutableList<ChipStructs.OvenModeClusterModeOptionStruct>?) {
                val modes = mutableSetOf<Int>()
                Timber.tag(TAG).d("Received ${value?.size ?: 0} supported mode options")

                value?.forEach { option ->
                    try {
                        // Try multiple methods to extract mode ID from the struct
                        val modeId: Int? = try {
                            // Method 1: Try direct field access
                            val field = option.javaClass.getDeclaredField("mode")
                            field.isAccessible = true
                            field.get(option) as? Int
                        } catch (_: Exception) {
                            try {
                                // Method 2: Try getter method
                                val method = option.javaClass.methods.firstOrNull {
                                    it.name.startsWith("getMode") && it.parameterCount == 0
                                }
                                method?.invoke(option) as? Int
                            } catch (_: Exception) {
                                try {
                                    // Method 3: Try getModeId method
                                    val methodId = option.javaClass.getMethod("getModeId")
                                    methodId.invoke(option) as? Int
                                } catch (_: Exception) {
                                    null
                                }
                            }
                        }

                        modeId?.let {
                            modes.add(it)
                            Timber.tag(TAG).d("Found supported mode: $modeId")
                        } ?: Timber.tag(TAG).w("Could not extract mode ID from option struct")
                    } catch (e: Exception) {
                        Timber.tag(TAG)
                            .w("Error extracting mode id from option struct: ${e.message}")
                    }
                }

                supportedModes = modes
                Timber.tag(TAG).d("Total supported modes: ${supportedModes.size} - $supportedModes")

                requireActivity().runOnUiThread {
                    val modeButtons = mapOf(
                        OvenMode.BAKE to binding.btnModeBake,
                        OvenMode.CONVECTION to binding.btnModeConvection,
                        OvenMode.GRILL to binding.btnModeGrill,
                        OvenMode.ROAST to binding.btnModeRoast,
                        OvenMode.CLEAN to binding.btnModeClean,
                        OvenMode.CONVECTION_BAKE to binding.btnModeConvectionBake,
                        OvenMode.CONVECTION_ROAST to binding.btnModeConvectionRoast,
                        OvenMode.WARMING to binding.btnModeWarming,
                        OvenMode.PROOFING to binding.btnModeProofing
                    )
                    updateModeButtonsVisibility(modeButtons)
                    setupModeButtons() // Setup click listeners after visibility is set
                }
                SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, ovenDeviceId, true)
            }

            override fun onError(ex: Exception) {
                SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, ovenDeviceId, false)
                Timber.tag(TAG).e("Read SupportedModes attribute failure: $ex")
                // If reading supported modes fails, show all modes as fallback
                requireActivity().runOnUiThread {
                    val modeButtons = mapOf(
                        OvenMode.BAKE to binding.btnModeBake,
                        OvenMode.CONVECTION to binding.btnModeConvection,
                        OvenMode.GRILL to binding.btnModeGrill,
                        OvenMode.ROAST to binding.btnModeRoast,
                        OvenMode.CLEAN to binding.btnModeClean,
                        OvenMode.CONVECTION_BAKE to binding.btnModeConvectionBake,
                        OvenMode.CONVECTION_ROAST to binding.btnModeConvectionRoast,
                        OvenMode.WARMING to binding.btnModeWarming,
                        OvenMode.PROOFING to binding.btnModeProofing
                    )
                    // Show all modes if we can't read supported modes
                    supportedModes = setOf(0, 1, 2, 3, 4, 5, 6, 7, 8)
                    updateModeButtonsVisibility(modeButtons)
                    setupModeButtons()
                }
            }
        })
    }

    private suspend fun readOvenMode() {
        getOvenModeCluster().readCurrentModeAttribute(object :
            ChipClusters.IntegerAttributeCallback {
            override fun onSuccess(value: Int) {
                value?.let {
                    selectedMode = getModeFromValue(it)
                    currentModeId = it
                    requireActivity().runOnUiThread {
                        val modeButtons = mapOf(
                            OvenMode.BAKE to binding.btnModeBake,
                            OvenMode.CONVECTION to binding.btnModeConvection,
                            OvenMode.GRILL to binding.btnModeGrill,
                            OvenMode.ROAST to binding.btnModeRoast,
                            OvenMode.CLEAN to binding.btnModeClean,
                            OvenMode.CONVECTION_BAKE to binding.btnModeConvectionBake,
                            OvenMode.CONVECTION_ROAST to binding.btnModeConvectionRoast,
                            OvenMode.WARMING to binding.btnModeWarming,
                            OvenMode.PROOFING to binding.btnModeProofing
                        )
                        updateModeUI(modeButtons)
                    }
                }
                SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, ovenDeviceId, true)
            }

            override fun onError(ex: Exception) {
                SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, ovenDeviceId, false)
                Timber.tag(TAG).e("Read CurrentMode attribute failure: $ex")
            }


        })
    }

    companion object {
        private val TAG = Companion::class.java.simpleName.toString()
        const val INIT = -1L
        const val COUNTDOWN_INTERVAL = 500L

        const val CUSTOM_TOAST_TIME_OUT = 5000

        // Oven device endpoints
        const val OVEN_ON_OFF_ENDPOINT = 3
        const val OVEN_MODE_CLUSTER_ENDPOINT = 2

        // Binding configuration
        const val BINDING_CLUSTER_ENDPOINT = 1
        const val RANGEHOOD_ON_OFF_ENDPOINT = 2
        const val RANGEHOOD_FAN_ENDPOINT = 1
        const val ON_OFF_CLUSTER_ID = 6L
        const val FAN_CONTROL_CLUSTER_ID = 0x0202L
        const val FAN_MODE_OFF = 0
        const val FAN_MODE_ON = 4
        val BINDING_TRY_ENDPOINTS = listOf(0, BINDING_CLUSTER_ENDPOINT, OVEN_ON_OFF_ENDPOINT)

        // ACL configuration (matching iOS DeviceBindingManager)
        const val ACL_CLUSTER_ENDPOINT = 0
        const val ACL_ADMINISTER_PRIVILEGE = 5
        const val ACL_OPERATE_PRIVILEGE = 3
        const val ACL_CASE_AUTH_MODE = 2
        //const val FABRIC_INDEX = 1

        const val ARG_DEVICE_MODEL = "ARG_DEVICE_MODEL"
        private const val STATUS_POLL_INTERVAL_MS = 5000L

        fun newInstance(): MatterOvenFragment = MatterOvenFragment()
    }
}
