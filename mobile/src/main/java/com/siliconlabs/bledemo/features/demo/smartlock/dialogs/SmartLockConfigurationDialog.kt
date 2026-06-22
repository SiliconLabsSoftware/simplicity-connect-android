package com.siliconlabs.bledemo.features.demo.smartlock.dialogs

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.pranavpandey.android.dynamic.toasts.DynamicToast
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.base.fragments.BaseDialogFragment
import com.siliconlabs.bledemo.databinding.DialogMqttBinding

class SmartLockConfigurationDialog(
    private val context: Context,
    private val title: String,
    private val listener: FileSelectionListener
) :
    BaseDialogFragment(
        hasCustomWidth = true,
        isCanceledOnTouchOutside = false
    ) {
    private lateinit var binding: DialogMqttBinding
    private var smartLockFilename: String? = null
    private var smartLockUri: Uri? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = DialogMqttBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUiListeners()
    }

    private fun setupUiListeners() {

        binding.apply {
            mqttDialogTitle.text = title
            selectP12FileBtn.setOnClickListener { listener.onSelectFileButtonClicked() }
            submitMqttButton.setOnClickListener {
                val uriPath = binding.selectP12FileBtn.text.toString().trim()
                if (uriPath.isEmpty() || uriPath == getString(R.string.smart_lock_select_p12_file)) {
                    DynamicToast.makeError(
                        context,
                        context.getString(R.string.smart_lock_config_alert_p12_message),
                        3000
                    ).show()
                    return@setOnClickListener
                }
                val password = binding.editPassword.text?.toString()?.trim()
                if (password?.isEmpty() == true) {
                    DynamicToast.makeError(
                        context,
                        context.getString(R.string.smart_lock_config_alert_password_message),
                        3000
                    ).show()
                    return@setOnClickListener
                }
                var endPoint = binding.editEndPoint.text.toString().trim()
                if (endPoint.isEmpty() == true) {
                    DynamicToast.makeError(
                        context,
                        context.getString(R.string.smart_lock_config_alert_end_point_message),
                        3000
                    ).show()
                    return@setOnClickListener
                }
                val cleanEndPoint = removeProtocols(endPoint)

                val subcribeTopic = binding.editSubTopic.text.toString().trim()
                if (subcribeTopic.isEmpty() == true) {
                    DynamicToast.makeError(
                        context,
                        context.getString(R.string.smart_lock_config_alert_subs_message),
                        3000
                    ).show()
                    return@setOnClickListener
                }
                val pubTopic = binding.editPubTopic.text.toString().trim()
                if (pubTopic.isEmpty() == true) {
                    DynamicToast.makeError(
                        context,
                        context.getString(R.string.smart_lock_config_alert_pub_message),
                        3000
                    ).show()
                    return@setOnClickListener
                }
                listener.onConnectButtonClicked(
                    smartLockUri,
                    password,
                    cleanEndPoint,
                    subcribeTopic,
                    pubTopic
                )
            }
            submitMqttCancelButton.setOnClickListener {

                listener.onCancelButtonClicked()
            }
        }
    }

    fun displayDialogTitle(title: String) {
        binding.mqttDialogTitle.text = title
    }

    fun displaySubscribeTopic(subscribeTopic: String) {
        binding.editSubTopic.setText(subscribeTopic)
    }

    fun displayPublishTopic(publishTopic: String) {
        binding.editPubTopic.setText(publishTopic)
    }

    fun changeFileName(newName: String?) {
        binding.selectP12FileBtn.text = newName
    }

    fun uriSelected(uri: Uri) {
        smartLockUri = uri
    }

    fun readFilename(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context?.contentResolver?.query(uri, null, null, null, null)
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    result = c.getString(c.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                }
                c.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        smartLockFilename = result
        return result
    }

    interface FileSelectionListener {
        fun onSelectFileButtonClicked()
        fun onCancelButtonClicked()
        fun onConnectButtonClicked(
            uriPath: Uri?,
            password: String?,
            endPoint: String,
            subcribeTopic: String,
            pubTopic: String
        )
    }

    companion object {
        const val PICK_P12_FILE_REQUEST_CODE = 888
        const val HTTPS_PREFIX = "https://"
        const val HTTP_PREFIX = "http://"
        const val SSL_PREFIX = "ssl://"
        const val MQTT_PREFIX = "mqtt://"

        fun removeProtocols(url: String): String {
            val cleaned = when {
                url.startsWith(MQTT_PREFIX) -> url.removePrefix(MQTT_PREFIX)
                url.startsWith(HTTPS_PREFIX) -> url.removePrefix(HTTPS_PREFIX)
                url.startsWith(HTTP_PREFIX) -> url.removePrefix(HTTP_PREFIX)
                url.startsWith(SSL_PREFIX) -> url.removePrefix(SSL_PREFIX)
                else -> url
            }
            return cleaned
        }
    }
}