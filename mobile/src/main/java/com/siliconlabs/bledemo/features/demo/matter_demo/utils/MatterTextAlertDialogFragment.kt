package com.siliconlabs.bledemo.features.demo.matter_demo.utils

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.siliconlabs.bledemo.databinding.DialogMatterTextAlertBinding
import com.siliconlabs.bledemo.utils.DisplayUtils

/**
 * Custom text alert (title, message, Cancel + OK). Title uses Material blue; actions are text-only.
 * Window width is 90% of the screen; scrim dim is applied in [onStart].
 */
class MatterTextAlertDialogFragment : DialogFragment() {

    private var _binding: DialogMatterTextAlertBinding? = null
    private val binding get() = _binding!!

    /** Invoked when OK is tapped (before [dismiss]). */
    var onPositiveClick: (() -> Unit)? = null

    /** Invoked when Cancel is tapped (before [dismiss]). */
    var onNegativeClick: (() -> Unit)? = null

    companion object {
        private const val ARG_TITLE = "matter_text_alert_title"
        private const val ARG_MESSAGE = "matter_text_alert_message"
        private const val DIALOG_WIDTH_SCREEN_FRACTION = 0.9f

        fun newInstance(title: String, message: String): MatterTextAlertDialogFragment {
            return MatterTextAlertDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_MESSAGE, message)
                }
            }
        }
    }

    @SuppressLint("UseGetLayoutInflater")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val title = arguments?.getString(ARG_TITLE).orEmpty()
        val message = arguments?.getString(ARG_MESSAGE).orEmpty()

        _binding = DialogMatterTextAlertBinding.inflate(LayoutInflater.from(requireContext()))
        binding.tvAlertTitle.text = title
        binding.tvAlertMessage.text = message

        binding.btnAlertOk.setOnClickListener {
            onPositiveClick?.invoke()
            dismiss()
        }
        binding.btnAlertCancel.setOnClickListener {
            onNegativeClick?.invoke()
            dismiss()
        }

        return AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()
            .also { dialog ->
                dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }
    }

    override fun onStart() {
        super.onStart()
        val dlg = dialog ?: return
        dlg.setCanceledOnTouchOutside(false)
        dlg.window?.setDimAmount(0.55f)
        dlg.window?.setLayout(
            (DisplayUtils.getScreenWidth(requireActivity()) * DIALOG_WIDTH_SCREEN_FRACTION).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
