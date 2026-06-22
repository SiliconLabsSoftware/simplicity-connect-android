package com.siliconlabs.bledemo.features.demo.matter_demo.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import com.siliconlabs.bledemo.databinding.CustomInputDialogBinding
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.utils.CustomToastManager
import com.siliconlabs.bledemo.utils.DisplayUtils


class CustomInputDialog : DialogFragment() {
    private lateinit var binding: CustomInputDialogBinding
    private var onButtonClickListener: ((String) -> Unit)? = null
    private var titleText: String = "OK"
    private var subTitleText: String = "OK"

    companion object {
        /** Dialog width as a fraction of screen width (Add Device Name modal). */
        private const val DIALOG_WIDTH_SCREEN_FRACTION = 0.9f

        const val DEVICE_KEY = "device_key"
        const val TITLE_TEXT_KEY = "title_text_key"
        const val SUBTITLE_TEXT_KEY = "subtitle_key"

        fun newInstance(
            context: Context,
            device: String,
            title: String,
            subtitle: String
        ): CustomInputDialog {
            val fragment = CustomInputDialog()
            val args = Bundle()
            args.putString(DEVICE_KEY, device)
            args.putString(TITLE_TEXT_KEY, title)
            args.putString(SUBTITLE_TEXT_KEY, subtitle)
            fragment.arguments = args
            return fragment
        }
    }

    @SuppressLint("UseGetLayoutInflater")
    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val device = arguments?.getString(DEVICE_KEY) ?: ""
        titleText = arguments?.getString(TITLE_TEXT_KEY) ?: "OK"
        subTitleText = arguments?.getString(SUBTITLE_TEXT_KEY) ?: "OK"

        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())

        binding = CustomInputDialogBinding.inflate(LayoutInflater.from(requireContext()))
        builder.setView(binding.root)
        binding.tvTitle.text = titleText
        binding.tvSubTitle.text = subTitleText
        binding.editText.setText(device)
        binding.button.text = requireContext().getString(R.string.matter_custom_alert_add)
        binding.button.setOnClickListener {
            onOkButtonClick()
        }

        return builder.create().also { dialog ->
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onStart() {
        super.onStart()
        val dialog: Dialog? = dialog
        if (dialog != null) {
            dialog.setCanceledOnTouchOutside(false)
            dialog.window!!
                .setLayout(
                    (DisplayUtils.getScreenWidth(requireActivity()) * DIALOG_WIDTH_SCREEN_FRACTION).toInt(),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
        }
    }

    private fun onOkButtonClick() {
        val enteredText = binding.editText.text.toString().trim()

        if (enteredText.isNotEmpty() && !enteredText.startsWith(".")) {
            onButtonClickListener?.invoke(enteredText)
            dismiss()
        } else {
            if (enteredText.isEmpty()) {
                CustomToastManager.showError(
                    requireContext(),getString(R.string.please_enter_device_name),5000
                )
            } else {
                CustomToastManager.showError(
                    requireContext(),getString(R.string.please_enter_valid_device_name),5000
                )
            }
        }
    }

    fun setOnButtonClickListener(listener: (String) -> Unit) {
        this.onButtonClickListener = listener
    }
}


