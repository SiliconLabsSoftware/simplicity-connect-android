package com.siliconlabs.bledemo.features.scan.browser.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.siliconlabs.bledemo.base.fragments.BaseDialogFragment
import com.siliconlabs.bledemo.features.scan.browser.models.OtaFileType
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.databinding.DialogOtaConfigBinding

class OtaConfigDialog(
        private val callback: Callback,
        private var doubleStepUpload: Boolean
) : BaseDialogFragment(
        hasCustomWidth = true,
        isCanceledOnTouchOutside = false
) {

    private lateinit var _binding: DialogOtaConfigBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = DialogOtaConfigBinding.inflate(inflater)
        return _binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9f).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        setupUiListeners()
        initRadioGroupState()
        onModeChanged()
    }

    fun changeFilename(type: OtaFileType, name: String) {
        when (type) {
            OtaFileType.APPLICATION -> _binding.selectAppFileBtn.text = name
            OtaFileType.APPLOADER -> _binding.selectApploaderFileBtn.text = name
        }
        toggleOtaProceedButton()
    }

    private fun setupUiListeners() {
        _binding.apply {
            rbTypePartial.setOnClickListener {
                doubleStepUpload = false
                onModeChanged()
                callback.onOtaPartialFullChanged(doubleStepUpload)
            }
            rbTypeFull.setOnClickListener {
                doubleStepUpload = true
                onModeChanged()
                callback.onOtaPartialFullChanged(doubleStepUpload)
            }
            selectAppFileBtn.setOnClickListener { callback.onFileChooserClicked(OtaFileType.APPLICATION) }
            selectApploaderFileBtn.setOnClickListener { callback.onFileChooserClicked(OtaFileType.APPLOADER) }
            otaProceed.setOnClickListener {
                dismiss()
                callback.onOtaClicked(reliabilityRadioButton.isChecked)
            }
            otaCancel.setOnClickListener {
                dismiss()
                callback.onDialogCancelled()
            }

        }
    }

    private fun initRadioGroupState() {
        _binding.apply {
            if (doubleStepUpload) rbTypeFull.isChecked = true
            else rbTypePartial.isChecked = true
        }
    }

    private fun onModeChanged() {
        toggleOtaProceedButton()
        toggleApploaderLayout()
    }

    private fun toggleOtaProceedButton() {
        _binding.otaProceed.isEnabled =
                if (doubleStepUpload) areFullOtaFilesCorrect()
                else arePartialOtaFilesCorrect()
    }

    private fun toggleApploaderLayout() {
        _binding.appLoaderLayout.visibility =
                if (doubleStepUpload) View.VISIBLE
                else View.GONE
    }

    private fun areFullOtaFilesCorrect(): Boolean {
        val defaultLabel = getString(R.string.ota_config_choose_file)
        return _binding.selectAppFileBtn.text.toString() != defaultLabel &&
                _binding.selectApploaderFileBtn.text.toString() != defaultLabel
    }

    private fun arePartialOtaFilesCorrect(): Boolean {
        return _binding.selectAppFileBtn.text.toString() != getString(R.string.ota_config_choose_file)
    }


    interface Callback {
        fun onOtaPartialFullChanged(doubleStepUpload: Boolean)
        fun onFileChooserClicked(type: OtaFileType)
        fun onOtaClicked(isReliableMode: Boolean)
        fun onDialogCancelled()
    }
}