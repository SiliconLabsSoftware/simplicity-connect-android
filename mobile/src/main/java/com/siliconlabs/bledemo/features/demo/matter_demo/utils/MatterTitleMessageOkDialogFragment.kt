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
import com.siliconlabs.bledemo.databinding.DialogMatterTitleMessageOkBinding
import com.siliconlabs.bledemo.utils.DisplayUtils

/**
 * Title + message + single **OK** text action (e.g. Oven–RangeHood unbind success).
 * Window width is 90% of the screen.
 */
class MatterTitleMessageOkDialogFragment : DialogFragment() {

    private var _binding: DialogMatterTitleMessageOkBinding? = null
    private val binding get() = _binding!!

    var onOkClick: (() -> Unit)? = null

    companion object {
        const val TAG = "MatterTitleMessageOkDialog"
        private const val ARG_TITLE = "matter_ok_alert_title"
        private const val ARG_MESSAGE = "matter_ok_alert_message"
        private const val DIALOG_WIDTH_SCREEN_FRACTION = 0.9f

        fun newInstance(title: String, message: String): MatterTitleMessageOkDialogFragment {
            return MatterTitleMessageOkDialogFragment().apply {
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

        _binding = DialogMatterTitleMessageOkBinding.inflate(LayoutInflater.from(requireContext()))
        binding.tvOkAlertTitle.text = title
        binding.tvOkAlertMessage.text = message

        binding.btnOkAlertOk.setOnClickListener {
            onOkClick?.invoke()
            dismiss()
        }

        return AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()
            .also { dialog ->
                dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                dialog.setCancelable(false)
                dialog.setCanceledOnTouchOutside(false)
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
