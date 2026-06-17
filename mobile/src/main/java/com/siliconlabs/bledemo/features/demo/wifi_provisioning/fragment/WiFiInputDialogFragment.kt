package com.siliconlabs.bledemo.features.demo.wifi_provisioning.fragment

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.databinding.CustomWifiProvisioningDialogBinding
import com.siliconlabs.bledemo.features.demo.matter_demo.utils.FragmentUtils

class WiFiInputDialogFragment : DialogFragment() {
    private lateinit var binding: CustomWifiProvisioningDialogBinding
    private lateinit var ssid: String
    private lateinit var security: String
    private lateinit var address: String
    private lateinit var rssi: String


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        ssid = arguments?.getString(ARG_INPUT_SSID_INFO).toString()
        security = arguments?.getString(ARG_SECURITY_TYPE_INPUT_INFO).toString()
        address = arguments?.getString(ARG_ADDR_INPUT_INFO).toString()
        rssi = arguments?.getString(ARG_RSSI_INPUT_INFO).toString()
        binding = CustomWifiProvisioningDialogBinding.inflate(inflater, container, false)
        if (dialog != null && dialog!!.window != null) {
            dialog!!.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog!!.window!!.requestFeature(Window.FEATURE_NO_TITLE)
            dialog!!.setCanceledOnTouchOutside(false)
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.ssid.text = ssid
        binding.securityTypeInfo.text = security
        binding.bssidInfo.text = address.uppercase()
        binding.rssidInfo.text = rssi

        binding.editWiFiProvPassword.apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }

        val passwordRequired = !security.isOpenSecurityType()
        if (!passwordRequired) {
            binding.tvPasswordLabel.visibility = View.GONE
            binding.passwordTextInputLayout.visibility = View.GONE
        } else {
            binding.editWiFiProvPassword.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(s: Editable?) {
                    updatePositiveButtonState(passwordRequired = true)
                }
            })
        }
        updatePositiveButtonState(passwordRequired)

        binding.negativeBtn.setOnClickListener {
            listener?.onDialogCancel()
            dismiss()
        }
        binding.positiveBtn.setOnClickListener {
            if (!passwordRequired) {
                listener?.onDialogResult(ssid, "", security)
                return@setOnClickListener
            }
            val wifiPassword = binding.editWiFiProvPassword.text.toString().trim()
            if (wifiPassword.isBlank()) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.wifi_provisioning_password_required), Toast.LENGTH_SHORT
                )
                    .show()
                return@setOnClickListener
            }
            if (!FragmentUtils.isPasswordValid(wifiPassword)) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.password_must_be_min_8_characters), Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            listener?.onDialogResult(ssid, wifiPassword, security)
            //dismiss()
        }
    }

    private fun updatePositiveButtonState(passwordRequired: Boolean) {
        binding.positiveBtn.isEnabled = if (!passwordRequired) {
            true
        } else {
            val password = binding.editWiFiProvPassword.text?.toString().orEmpty().trim()
            password.isNotEmpty() && password.length > MIN_PASSWORD_LENGTH_TO_ENABLE
        }
    }

    override fun onStart() {
        super.onStart()
        val window = dialog?.window
        window?.setLayout(
            (resources.displayMetrics.widthPixels * DIALOG_WIDTH_FRACTION).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? DialogResultListener
            ?: throw ClassCastException("$context must implement DialogResultListener")
    }


    interface DialogResultListener {
        fun onDialogResult(ssid: String, passphrase: String, security: String)
        fun onDialogCancel()
    }

    private var listener: DialogResultListener? = null

    companion object {
        private const val DIALOG_WIDTH_FRACTION = 0.9f
        private const val MIN_PASSWORD_LENGTH_TO_ENABLE = 2

        private fun String.isOpenSecurityType(): Boolean {
            val trimmed = trim()
            if (trimmed.equals("OPEN", ignoreCase = true)) {
                return true
            }
            return trimmed.toIntOrNull() == 0
        }

        private const val ARG_INPUT_SSID_INFO = "arg_input_ssid_info"
        private const val ARG_ADDR_INPUT_INFO = "arg_addr_input_info"
        private const val ARG_RSSI_INPUT_INFO = "arg_rssi_input_info"
        private const val ARG_SECURITY_TYPE_INPUT_INFO = "arg_security_type_input_info"

        fun newInstance(
            ssid: String, address: String, rssi: String,
            security: String
        ): WiFiInputDialogFragment {
            val fragDialog = WiFiInputDialogFragment()
            val argument = Bundle().apply {
                putString(ARG_INPUT_SSID_INFO, ssid)
                putString(ARG_ADDR_INPUT_INFO, address)
                putString(ARG_RSSI_INPUT_INFO, rssi)
                putString(ARG_SECURITY_TYPE_INPUT_INFO, security)
            }
            fragDialog.arguments = argument
            return fragDialog
        }
    }
}