package com.siliconlabs.bledemo.features.demo.wifi_ota_update

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import com.siliconlabs.bledemo.base.fragments.BaseDialogFragment
import com.siliconlabs.bledemo.databinding.DialogWifiOtaFileUpdateBinding


class WiFiOtaFileSelectionDialog(
    private val cancelCallback: CancelCallback,
    private val listener: FileSelectionListener, private val ipAddress: String?
) : BaseDialogFragment(
    hasCustomWidth = true,
    isCanceledOnTouchOutside = false
) {

    private lateinit var _binding: DialogWifiOtaFileUpdateBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = DialogWifiOtaFileUpdateBinding.inflate(inflater)
        _binding.wifiIpAddress.text = ipAddress
        return _binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9f).toInt(),
            LayoutParams.WRAP_CONTENT
        )
        setupUiListeners()
    }

    fun setupUiListeners() {
        _binding.apply {
            selectAppFileBtn.setOnClickListener { listener.onSelectFileButtonClicked() }
            otaCancel.setOnClickListener {
                listener.onCancelButtonClicked()
                dismiss()
                cancelCallback.onDismiss()
            }
            otaProceed.setOnClickListener { listener.onOtaButtonClicked() }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        cancelCallback.onDismiss()
    }

    fun checkPortNumberValid(): Boolean {
        return _binding.portId.text.length >= 4
    }

    fun changeFileName(newName: String?) {
        _binding.selectAppFileBtn.text = newName
    }

    fun enableUploadButton() {
        _binding.otaProceed.isEnabled = true
    }

    fun disableUploadButton() {
        _binding.otaProceed.isEnabled = false
    }

    fun getPortId(): String {
        return _binding.portId.text.toString()
    }

    interface FileSelectionListener {
        fun onSelectFileButtonClicked()
        fun onOtaButtonClicked()
        fun onCancelButtonClicked()
    }

    interface CancelCallback {
        fun onDismiss()
    }
}