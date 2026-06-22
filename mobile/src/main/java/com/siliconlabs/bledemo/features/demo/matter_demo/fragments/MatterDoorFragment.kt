package com.siliconlabs.bledemo.features.demo.matter_demo.fragments

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.res.ColorStateList
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
import androidx.lifecycle.lifecycleScope
import chip.devicecontroller.ChipClusters
import chip.devicecontroller.ChipDeviceController
import chip.devicecontroller.GetConnectedDeviceCallbackJni
import com.google.android.material.button.MaterialButton
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.bluetooth.beacon_utils.eddystone.Constants.SCAN_TIMER
import com.siliconlabs.bledemo.databinding.FragmentMatterDoorLightBinding
import com.siliconlabs.bledemo.features.demo.matter_demo.activities.MatterDemoActivity
import com.siliconlabs.bledemo.features.demo.matter_demo.controller.GenericChipDeviceListener
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterLightFragment.Companion.ARG_DEVICE_MODEL
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterLightFragment.Companion.INIT
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterLightFragment.Companion.ON_OFF_CLUSTER_ENDPOINT
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterLightFragment.Companion.TIME_OUT
import com.siliconlabs.bledemo.features.demo.matter_demo.model.MatterScannedResultModel
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.ChipClient
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.CustomProgressDialog
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.FragmentUtils
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.MessageDialogFragment
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.SharedPrefsUtils
import com.siliconlabs.bledemo.utils.CustomToastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Optional


class MatterDoorFragment : Fragment() {

    private lateinit var dialog: MessageDialogFragment
    private val dialogTag = "MessageDialog"
    private val deviceController: ChipDeviceController
        get() = ChipClient.getDeviceController(requireContext())
    private lateinit var mPrefs: SharedPreferences

    private lateinit var scope: CoroutineScope
    private lateinit var binding: FragmentMatterDoorLightBinding
    private var deviceId: Long = INIT
    private var endpointId: Int = ON_OFF_CLUSTER_ENDPOINT
    private lateinit var model: MatterScannedResultModel
    private var customProgressDialog: CustomProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mPrefs = requireContext().getSharedPreferences(
            MatterDemoActivity.MATTER_PREF,
            AppCompatActivity.MODE_PRIVATE
        )
        if (requireArguments() != null) {
            model = requireArguments().getParcelable(ARG_DEVICE_MODEL)!!
            deviceId = model.deviceId
            Timber.tag(TAG).e("deviceID: $model")
        }
        if (deviceId != null) {

            showMatterProgressDialog(getString(R.string.matter_device_status))
            CoroutineScope(Dispatchers.IO).launch {
                // This code will run asynchronously

                val resulInfo = checkForDeviceStatus()
                if (resulInfo) {
                    println("Operation was successful")
                    removeProgress()
                }
            }
        }

    }


    private suspend fun checkForDeviceStatus(): Boolean {
        return withContext(Dispatchers.Default) {
            // Simulate a time-consuming task

            deviceController.getConnectedDevicePointer(
                deviceId,
                object : GetConnectedDeviceCallbackJni.GetConnectedDeviceCallback {
                    override fun onDeviceConnected(devicePointer: Long) {
                        model.isDeviceOnline = true
                        SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, true)
                    }

                    override fun onConnectionFailure(nodeId: Long, error: java.lang.Exception?) {
                        model.isDeviceOnline = false
                        removeProgress()
                        SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, false)
                        showMessageDialog()

                    }
                })

            delay(SCAN_TIMER * 1000)
            // handleSubscription()
            true
        }
    }

    private fun removeProgress() {
        if (customProgressDialog?.isShowing() == true) {
            customProgressDialog?.dismiss()
        }
    }

    private fun showMatterProgressDialog(message: String) {
        customProgressDialog = CustomProgressDialog(requireContext())
        customProgressDialog!!.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        customProgressDialog!!.setCanceledOnTouchOutside(false)
        customProgressDialog!!.setMessage(message)
        customProgressDialog!!.show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMatterDoorLightBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as MatterDemoActivity).hideQRScanner()
        scope = viewLifecycleOwner.lifecycleScope
        deviceController.setCompletionListener(DoorChipControllerCallback())
        binding.btnMatterDeviceState.setImageResource(R.drawable.door_lock)
        binding.txtClusterName.text = requireContext().getText(R.string.matter_door_title)
        binding.btnOn.icon = ContextCompat.getDrawable(requireActivity(), R.drawable.ic_btn_lock)
        binding.btnOn.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        binding.btnOn.iconPadding = resources.getDimensionPixelSize(R.dimen.matter_8dp)
        binding.btnOn.iconTint = ColorStateList.valueOf(
            ContextCompat.getColor(
                requireContext(),
                R.color.silabs_redtheme_primary_color
            )
        )

        binding.btnOn.isLongClickable = false
        binding.btnOff.isLongClickable = false

        binding.btnOn.text = requireContext().getText(R.string.matter_locked_status)
        binding.btnOff.text = requireContext().getText(R.string.matter_unlock_status)
        binding.btnOff.icon = ContextCompat.getDrawable(requireActivity(), R.drawable.ic_btn_unlock)
        binding.btnOff.iconTint = ColorStateList.valueOf(
            ContextCompat.getColor(
                requireContext(),
                R.color.silabs_redtheme_primary_color
            )
        )
        binding.btnOff.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        binding.btnOff.iconPadding = resources.getDimensionPixelSize(R.dimen.matter_2dp)
        binding.btnToggle.visibility = View.GONE
        binding.btnOn.setOnClickListener {
            showMatterProgressDialog(getString(R.string.matter_door_lock_in_progress))
            scope.launch {
                sendLockCommandClick()
            }
        }

        binding.btnOff.setOnClickListener {
            showMatterProgressDialog(getString(R.string.matter_door_lock_in_progress))
            scope.launch {
                sendUnlockCommandClick()
            }
        }
        //back handling
        requireActivity().onBackPressedDispatcher.addCallback(
            requireActivity(),
            onBackPressedCallback
        )
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
                                    this@MatterDoorFragment,
                                    CallBackHandler::class.java
                                ).onBackHandler()
                            }
                        }
                        val transaction: FragmentTransaction =
                            requireActivity().supportFragmentManager.beginTransaction()

                        dialog.show(transaction, dialogTag)
                    }
                }
            } else {
                Timber.e("device offline")
            }
        } catch (e: Exception) {
            Timber.e("device offline :$e")
        }

    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (requireActivity().supportFragmentManager.backStackEntryCount > 0) {
                requireActivity().supportFragmentManager.popBackStack();
            } else {
                FragmentUtils.getHost(this@MatterDoorFragment, CallBackHandler::class.java)
                    .onBackHandler()
            }
        }
    }

    private suspend fun getLockUnlockClusterForDevice(): ChipClusters.DoorLockCluster {
        return ChipClusters.DoorLockCluster(
            ChipClient.getConnectedDevicePointer(requireContext(), deviceId),
            endpointId
        )
    }

    private suspend fun handleSubscription() {
        getLockUnlockClusterForDevice().subscribeLockStateAttribute(
            object : ChipClusters.DoorLockCluster.LockStateAttributeCallback {
                override fun onSuccess(value: Int?) {
                    if (isAdded && !requireActivity().isFinishing) {
                        when (value) {
                            1 -> {
                                binding.btnMatterDeviceState.setImageResource(R.drawable.door_lock)
                            }

                            2 -> {
                                binding.btnMatterDeviceState.setImageResource(R.drawable.door_unlock)
                            }

                            else -> println("Unhandled Event....")
                        }
                    }
                }

                override fun onError(ex: java.lang.Exception?) {
                    Timber.tag(TAG).e("LockStateAttributeCallback Error $ex")
                }

            },
            MatterTemperatureSensorFragment.MIN_REFRESH_PERIOD_S,
            MatterTemperatureSensorFragment.MAX_REFRESH_PERIOD_S
        )
    }

    private suspend fun sendLockCommandClick() {
        getLockUnlockClusterForDevice().lockDoor(
            object : ChipClusters.DefaultClusterCallback {
                override fun onSuccess() {
                    requireActivity().runOnUiThread {
                        removeProgress()
                        Timber.tag(TAG).e("Lock command Success")
                        binding.btnMatterDeviceState.setImageResource(R.drawable.door_lock)
                    }
                }

                override fun onError(error: Exception?) {
                    requireActivity().runOnUiThread {
                        removeProgress()
                        SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, false)
                        showMessageDialog()
                    }
                }
            }, Optional.empty(),
            TIME_OUT
        )
    }


    private suspend fun sendUnlockCommandClick() {
        getLockUnlockClusterForDevice().unlockDoor(
            object : ChipClusters.DefaultClusterCallback {
                override fun onSuccess() {
                    requireActivity().runOnUiThread {
                        removeProgress()
                        Timber.tag(TAG).e("Unlock command Success")
                        SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, true)
                        binding.btnMatterDeviceState.setImageResource(R.drawable.door_unlock)
                    }
                }

                @SuppressLint("TimberArgCount")
                override fun onError(error: Exception?) {
                    requireActivity().runOnUiThread {
                        removeProgress()
                        SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, false)
                        Timber.tag(TAG).e("Unlock command failure: $error")
                        showMessageDialog()
                    }
                }

            }, Optional.empty(),
            TIME_OUT
        )
    }

    private fun showMessage(msg: String) {
        requireActivity().runOnUiThread {
            // Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            CustomToastManager.show(
                requireContext(), msg, 5000
            )
        }
    }


    inner class DoorChipControllerCallback : GenericChipDeviceListener() {
        override fun onConnectDeviceComplete() {}

        override fun onCommissioningComplete(nodeId: Long, errorCode: Long) {
            Timber.tag(TAG).d("onCommissioningComplete for nodeId $nodeId: $errorCode")
            // showMessage("Address update complete for nodeId $nodeId with code $errorCode")
        }

        override fun onNotifyChipConnectionClosed() {
            Timber.tag(TAG).d("onNotifyChipConnectionClosed")
        }

        override fun onCloseBleComplete() {
            Timber.tag(TAG).d("onCloseBleComplete")
        }

        override fun onError(error: Throwable?) {
            super.onError(error)
            Timber.tag(TAG).d("onError : $error")
        }
    }

    interface CallBackHandler {
        fun onBackHandler()
    }

    companion object {
        private val TAG = Companion::class.java.simpleName.toString()

        fun newInstance(): MatterDoorFragment = MatterDoorFragment()
    }

}


