package com.siliconlabs.bledemo.features.demo.matter_demo.fragments

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import com.siliconlabs.bledemo.databinding.FragmentOtbrInputDialogBinding
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannerFragment.Companion.DIALOG_OTBR_INFO
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterWifiInputDialogFragment.Companion.DIALOG_WIDTH_FRACTION
import com.siliconlabs.bledemo.utils.DisplayUtils

class MatterOTBRInputDialogFragment : DialogFragment() {

    private lateinit var binding: FragmentOtbrInputDialogBinding

    companion object {
        const val WINDOW_SIZE = 0.65
        fun newInstance(): MatterOTBRInputDialogFragment = MatterOTBRInputDialogFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = FragmentOtbrInputDialogBinding.inflate(inflater, container, false)
        if (dialog != null && dialog!!.window != null) {
            dialog!!.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog!!.window!!.requestFeature(Window.FEATURE_NO_TITLE);
            dialog!!.setCanceledOnTouchOutside(false)
        }
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        val dialog: Dialog? = dialog
        if (dialog != null) {
            dialog.window!!
                .setLayout(
                    (DisplayUtils.getScreenWidth(requireActivity()) * DIALOG_WIDTH_FRACTION).toInt(),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners(view)
    }

    private fun setupClickListeners(view: View) {
        binding.positiveBtn.setOnClickListener {
            dismiss()
            val dat = binding.editText.text.toString().trim()
            val intent = Intent()
            intent.putExtra(DIALOG_OTBR_INFO, dat)
            targetFragment?.onActivityResult(
                targetRequestCode, Activity.RESULT_OK,
                intent
            )

        }

        binding.negativeBtn.setOnClickListener {
            dismiss()
        }
    }
}