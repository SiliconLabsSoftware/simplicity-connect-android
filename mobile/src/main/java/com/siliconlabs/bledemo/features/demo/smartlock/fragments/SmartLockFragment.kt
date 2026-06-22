package com.siliconlabs.bledemo.features.demo.smartlock.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.unit.Velocity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.pranavpandey.android.dynamic.toasts.DynamicToast
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.databinding.SmartLockFragmentBinding
import com.siliconlabs.bledemo.features.demo.smartlock.activities.SmartLockActivity
import com.siliconlabs.bledemo.features.demo.smartlock.activities.SmartLockActivity.Companion.AWS_CONNECTION
import com.siliconlabs.bledemo.features.demo.smartlock.activities.SmartLockActivity.Companion.BLE_CONNECTION
import com.siliconlabs.bledemo.features.demo.smartlock.dialogs.SmartLockConfigurationDialog
import com.siliconlabs.bledemo.features.demo.smartlock.models.LockUIState
import com.siliconlabs.bledemo.features.demo.smartlock.viewmodels.SmartLockBleViewModel
import timber.log.Timber

class SmartLockFragment : Fragment(), SmartLockActivity.SmartLockImageRefreshClickListener {
    private lateinit var binding: SmartLockFragmentBinding
    private lateinit var viewModel: SmartLockBleViewModel


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = SmartLockFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    fun onBackPressed() {
        Timber.tag(TAG).d("Back pressed, returning to previous screen")
        Toast.makeText(
            requireActivity(),
            "Back pressed, returning to previous screen",
            Toast.LENGTH_SHORT
        ).show()
        parentFragmentManager.popBackStack()
        requireActivity().finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(requireActivity(), callback)
        viewModel = ViewModelProvider(requireActivity()).get(SmartLockBleViewModel::class.java)
        if (activity is SmartLockActivity) {
            (activity as SmartLockActivity).setFragmentRefreshListener(this)

        } else {
            throw IllegalStateException("Activity must implement SmartLockImageRefreshClickListener")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateUIConnectionType()
        (activity as SmartLockActivity).onSmartLockOptionSelected(BLE_CONNECTION)
        binding.apply {
            lockBtn.setOnClickListener {
                if ((activity as SmartLockActivity).getConnectionType() == BLE_CONNECTION) {
                    viewModel.lockState()
                } else {
                    (activity as SmartLockActivity).onAWSSmartLockButtonClicked()
                }
            }
            unlockBtn.setOnClickListener {
                if ((activity as SmartLockActivity).getConnectionType() == BLE_CONNECTION) {
                    viewModel.unLockState()
                } else {
                    (activity as SmartLockActivity).onAWSSmartUnlockButtonClicked()
                }
            }


            subscribeSendBtn.setOnClickListener {
                if ((activity as SmartLockActivity).getConnectionType() == AWS_CONNECTION) {
                    val inputText = awsTextInput.text.toString().trim()
                    if (inputText.isNotEmpty()) {
                        (activity as SmartLockActivity).onAWSSmartLockSubscribeButtonClicked(
                            inputText
                        )
                        hideKeyboard(binding.awsTextInput)
                    } else {
                        Timber.tag(TAG).e("Input text is empty, cannot subscribe")
                        requireActivity().runOnUiThread {
                            DynamicToast.makeError(
                                requireContext(),
                                getString(R.string.subscribe_topic_should_not_be_empty),
                                3000
                            ).show()
                        }
                    }
                }
            }

            connectTypeToggleBtn.addOnButtonCheckedListener { group, checkedId, isChecked ->
                if (isChecked) {
                    when (checkedId) {
                        R.id.btnBleOn -> {
                            (activity as SmartLockActivity).onSmartLockOptionSelected(
                                BLE_CONNECTION
                            )
                        }

                        R.id.btnAwsOn -> {
                            (activity as SmartLockActivity).onSmartLockOptionSelected(
                                AWS_CONNECTION
                            )
                        }
                    }
                }
            }

        }
        smartLockObserversSetup()
    }

    private fun hideKeyboard(view: View) {
        Timber.tag(TAG).d("Hiding keyboard")
        val inputMethodManager =
            requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.awsTextInput.windowToken, 0)
    }

    private fun onToggleButtonClicked() {

        Timber.tag(TAG).d("Toggle button clicked")
        if ((activity as SmartLockActivity).getConnectionType() == BLE_CONNECTION) {
            //binding.connectTypeToggleBtn.text = getString(R.string.smart_lock_connection_aws)
            binding.txtClusterName.text = getString(R.string.smart_lock_status_ble_lock)
            binding.awsCommunicationLayout.visibility = View.GONE
            viewModel.queryLockState()
            binding.notePlaceholder.visibility = View.GONE
            (activity as SmartLockActivity).hideConfigureButton()
        } else {
            //binding.connectTypeToggleBtn.text = getString(R.string.smart_lock_connection_ble)
            binding.ivLock.setImageResource(R.drawable.door_lock)
            binding.txtClusterName.text = getString(R.string.smart_lock_status_asw_lock)
            binding.awsCommunicationLayout.visibility = View.VISIBLE
            binding.notePlaceholder.visibility = View.VISIBLE
            (activity as SmartLockActivity).onAWSSmartLockWakeUpCmd()
            (activity as SmartLockActivity).showConfigureButton()
            if ((activity as SmartLockActivity).isMqttConfigValid()) {
                binding.notePlaceholder.visibility = View.GONE
            } else {
                binding.notePlaceholder.visibility = View.VISIBLE
            }
        }
    }

    override fun onAwsSmartLockRefresh(
        buttonClickedStatus: Boolean,
        connectionType: String
    ) {
        if (connectionType != AWS_CONNECTION) return
        Timber.tag(TAG).d("AWS UI update: unlocked=$buttonClickedStatus")
        if (buttonClickedStatus) {
            binding.ivLock.setImageResource(R.drawable.door_unlock)
            if (connectionType == BLE_CONNECTION) {
                binding.txtClusterName.text = getString(R.string.smart_lock_status_ble_unlock)
            } else {
                binding.txtClusterName.text = getString(R.string.smart_lock_status_asw_unlock)
            }
        } else {
            binding.ivLock.setImageResource(R.drawable.door_lock)
            if (connectionType == BLE_CONNECTION) {
                binding.txtClusterName.text = getString(R.string.smart_lock_status_ble_lock)
            } else {
                binding.txtClusterName.text = getString(R.string.smart_lock_status_asw_lock)
            }
        }
    }

    override fun onSmartLockConnectionTypeSelected() {
        onToggleButtonClicked()
    }

    override fun onSmartLockAWSConfigured() {
        if ((activity as SmartLockActivity).isMqttConfigValid()) {
            binding.notePlaceholder.visibility = View.GONE
        } else {
            binding.notePlaceholder.visibility = View.VISIBLE
        }
    }


    private fun smartLockObserversSetup() {
        viewModel.lockUIState.observe(viewLifecycleOwner) { state ->
            if ((activity as SmartLockActivity).getConnectionType() != BLE_CONNECTION) return@observe
            when (state) {
                LockUIState.UNLOCKED -> displayUnlockState()
                LockUIState.LOCKED -> displayLockState()
                LockUIState.UNKNOWN -> Unit
            }
        }
        viewModel.isLockStateChanged.observe(viewLifecycleOwner) {
            if ((activity as SmartLockActivity).getConnectionType() != BLE_CONNECTION) return@observe
            Timber.tag(TAG).d("isLockStateChanged: $it")
            if (it) {
                binding.ivLock.setImageResource(R.drawable.door_unlock)
                binding.txtClusterName.text = getString(R.string.smart_lock_status_ble_unlock)
            } else {
                binding.ivLock.setImageResource(R.drawable.door_lock)
                binding.txtClusterName.text = getString(R.string.smart_lock_status_ble_lock)
            }

        }
    }

    private fun updateUIConnectionType() {
        binding.txtClusterName.visibility = View.VISIBLE
        if ((activity as SmartLockActivity).getConnectionType() == BLE_CONNECTION) {
            binding.txtClusterName.text = getString(R.string.smart_lock_status_ble_lock)
            binding.awsCommunicationLayout.visibility = View.GONE
            // binding.connectTypeToggleBtn.text = getString(R.string.smart_lock_connection_aws)
        } else {
            // binding.connectTypeToggleBtn.text = getString(R.string.smart_lock_connection_ble)
            binding.awsCommunicationLayout.visibility = View.GONE
            binding.txtClusterName.text = getString(R.string.smart_lock_status_asw_lock)
        }
    }

    private fun displayLockState() {
        if ((activity as SmartLockActivity).getConnectionType() == BLE_CONNECTION) {
            binding.ivLock.setImageResource(R.drawable.door_lock)
            binding.txtClusterName.text = getString(R.string.smart_lock_status_ble_lock)
        }
    }

    private fun displayUnlockState() {
        if ((activity as SmartLockActivity).getConnectionType() == BLE_CONNECTION) {
            binding.ivLock.setImageResource(R.drawable.door_unlock)
            binding.txtClusterName.text = getString(R.string.smart_lock_status_ble_unlock)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (activity is SmartLockActivity) {
            (activity as SmartLockActivity).setFragmentRefreshListener(null)
        }
    }

    private val callback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            Toast.makeText(
                requireActivity(),
                "Back pressed, returning to previous screen",
                Toast.LENGTH_SHORT
            ).show()
            parentFragmentManager.popBackStack()
            requireActivity().finish()
        }
    }

    companion object {
        private const val TAG = "SmartLockFragment"
    }

}






