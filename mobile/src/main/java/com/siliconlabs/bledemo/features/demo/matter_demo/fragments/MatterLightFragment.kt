package com.siliconlabs.bledemo.features.demo.matter_demo.fragments

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import chip.devicecontroller.ChipClusters
import chip.devicecontroller.ChipDeviceController
import chip.devicecontroller.GetConnectedDeviceCallbackJni
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.bluetooth.beacon_utils.eddystone.Constants
import com.siliconlabs.bledemo.databinding.FragmentMatterDoorLightBinding
import com.siliconlabs.bledemo.features.demo.matter_demo.activities.MatterDemoActivity
import com.siliconlabs.bledemo.features.demo.matter_demo.controller.GenericChipDeviceListener
import com.siliconlabs.bledemo.features.demo.matter_demo.model.MatterScannedResultModel
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.ChipClient
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.CustomProgressDialog
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.FragmentUtils
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.MessageDialogFragment
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.SharedPrefsUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber


class MatterLightFragment : Fragment() {
    private lateinit var dialog: MessageDialogFragment
    private var customProgressDialog: CustomProgressDialog? = null
    private val dialogTag = "MessageDialog"
    private val deviceController: ChipDeviceController
        get() = ChipClient.getDeviceController(requireContext())
    private lateinit var scope: CoroutineScope
    private lateinit var binding: FragmentMatterDoorLightBinding
    private var deviceId: Long = 0
    private var endpointId: Int = ON_OFF_CLUSTER_ENDPOINT
    private lateinit var model: MatterScannedResultModel
    private lateinit var mPrefs: SharedPreferences
    private var currLightStatus: Boolean = false
    private lateinit var matterLightFragmentJob: Job
    private val matterLightFragmentScope = CoroutineScope(Dispatchers.Main)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mPrefs = requireContext().getSharedPreferences(
            MatterDemoActivity.MATTER_PREF,
            Context.MODE_PRIVATE
        )

        model = requireArguments().getParcelable(ARG_DEVICE_MODEL)!!
        deviceId = model.deviceId
        Timber.tag(TAG).d("deviceID: $model")

        showMatterProgressDialog(getString(R.string.matter_device_status))

        // retrieveSavedDevices()
        CoroutineScope(Dispatchers.IO).launch {
            // This code will run asynchronously
            val results = checkForDeviceStatus()
            if (results) {
                println("Operation was successful")
                removeProgress()
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

            delay(Constants.SCAN_TIMER * 500)
            true
        }
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
                                    this@MatterLightFragment, CallBackHandler::class.java
                                ).onBackHandler()
                            }
                        }
                        val transaction: FragmentTransaction =
                            requireActivity().supportFragmentManager.beginTransaction()
                        dialog.show(transaction, dialogTag)

                    }
                }
            } else {
                Timber.tag(TAG).e("device offline")
            }
        } catch (e: Exception) {
            Timber.e("Exception Occurred $e")
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentMatterDoorLightBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scope = viewLifecycleOwner.lifecycleScope
        deviceController.setCompletionListener(LightChipControllerCallback())
        startBackgroundTask()
        binding.btnOn.isLongClickable = false
        binding.btnOff.isLongClickable = false
        binding.btnOff.text = requireContext().getText(R.string.matter_light_off_status)
        binding.btnOn.text = requireContext().getText(R.string.matter_light_on_status)
        binding.btnToggle.text = requireContext().getText(R.string.matter_light_toggle_status)

        binding.btnOn.setOnClickListener {
            scope.launch {
                sendOnCommandClick()
            }
        }

        binding.btnOff.setOnClickListener {
            scope.launch { sendOffCommandClick() }
        }

        binding.btnToggle.setOnClickListener {
            scope.launch { sendToggleCommandClicked() }
        }
        (activity as MatterDemoActivity).hideQRScanner()

        //back handling
        requireActivity().onBackPressedDispatcher.addCallback(
            requireActivity(), onBackPressedCallback
        )
    }


    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (isAdded) {
                if (requireActivity().supportFragmentManager.backStackEntryCount > 0) {
                    requireActivity().supportFragmentManager.popBackStack()
                } else {
                    FragmentUtils.getHost(this@MatterLightFragment, CallBackHandler::class.java)
                        .onBackHandler()
                }
            }
        }
    }

    private suspend fun getOnOffClusterForDevice(): ChipClusters.OnOffCluster {
        return ChipClusters.OnOffCluster(
            ChipClient.getConnectedDevicePointer(requireContext(), deviceId), endpointId
        )
    }

    private suspend fun sendOnCommandClick() {
        getOnOffClusterForDevice().on(object : ChipClusters.DefaultClusterCallback {
            override fun onSuccess() {
                imageForLightOn()
                SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, true)
            }

            override fun onError(ex: Exception) {
                SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, false)
                Timber.tag(TAG).e("ON command failure $ex")
                showMessageDialog()
            }

        }, TIME_OUT)
    }

    private suspend fun sendOffCommandClick() {
        getOnOffClusterForDevice().off(object : ChipClusters.DefaultClusterCallback {
            override fun onSuccess() {
                SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, true)
                imageForLightOff()
            }

            override fun onError(ex: Exception) {
                SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, false)
                Timber.tag(TAG).e("OFF command failure $ex")
                showMessageDialog()
            }
        })
    }


    private suspend fun sendToggleCommandClicked() {
        getOnOffClusterForDevice().toggle(object : ChipClusters.DefaultClusterCallback {
            override fun onSuccess() {
                if (currLightStatus) {
                    imageForLightOff()
                } else {
                    imageForLightOn()
                }
            }

            override fun onError(ex: Exception) {
                SharedPrefsUtils.updateDeviceByDeviceId(mPrefs, deviceId, false)
                Timber.tag(TAG).e("OFF command failure:$ex")
            }

        })
    }

    private fun imageForLightOff() {
        if (isAdded) {
            requireActivity().runOnUiThread {
                binding.btnMatterDeviceState.setImageResource(R.drawable.matter_light)
                binding.btnMatterDeviceState.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.silabs_rebranding_2373_matter_image_disable_color),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
            }
            currLightStatus = false
        }
    }

    private fun imageForLightOn() {
        if (isAdded) {
            requireActivity().runOnUiThread {
                binding.btnMatterDeviceState.setImageResource(R.drawable.matter_light)
                binding.btnMatterDeviceState.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.silabs_yellow),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
            }
            currLightStatus = true
        }
    }


    inner class LightChipControllerCallback : GenericChipDeviceListener() {
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
            println("MatterLight OnError: $error")
            Timber.tag(TAG).e("OnError: $error")
        }

    }

    interface CallBackHandler {
        fun onBackHandler()
    }

    private fun startBackgroundTask() {
        matterLightFragmentJob = matterLightFragmentScope.launch(Dispatchers.IO) {
            while (true) {
                delay(1000)
                getOnOffClusterForDevice().readOnOffAttribute(object :
                    ChipClusters.BooleanAttributeCallback {
                    override fun onSuccess(value: Boolean) {
                        if (value) {
                            imageForLightOn()
                        } else {
                            imageForLightOff()
                        }
                    }

                    override fun onError(error: java.lang.Exception?) {
                        println("error $error")
                    }
                })
            }
        }
    }

    private fun stopBackgroundTask() {
        if (::matterLightFragmentJob.isInitialized && matterLightFragmentJob.isActive) {
            matterLightFragmentJob.cancel()
            println("Background task stopped")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopBackgroundTask()
    }

    companion object {
        private const val TAG = "MatterLightFragment"

        const val ON_OFF_CLUSTER_ENDPOINT = 1

        const val ARG_DEVICE_MODEL = "device_model"
        const val ARG_DEVICE_INFO = "device_info"
        const val TIME_OUT = 900
        const val INIT = 0L
        fun newInstance(): MatterLightFragment = MatterLightFragment()
    }
}