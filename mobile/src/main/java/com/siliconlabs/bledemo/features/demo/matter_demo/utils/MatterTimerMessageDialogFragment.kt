package com.siliconlabs.bledemo.features.demo.matter_demo.utils

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.siliconlabs.bledemo.databinding.DialogMatterTimerMessageBinding
import com.siliconlabs.bledemo.utils.DisplayUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Message-only popup with a text [CANCEL] action and auto-dismiss after [autoDismissMs].
 * Window width is 90% of the screen (Oven–RangeHood binding success, etc.).
 */
class MatterTimerMessageDialogFragment : DialogFragment() {

    private var _binding: DialogMatterTimerMessageBinding? = null
    private val binding get() = _binding!!

    private var autoDismissJob: Job? = null

    private val autoDismissMs: Long
        get() = arguments?.getLong(ARG_DISMISS_MS, DEFAULT_DISMISS_MS) ?: DEFAULT_DISMISS_MS

    companion object {
        const val TAG = "MatterTimerMessageDialog"
        private const val ARG_MESSAGE = "matter_timer_message"
        private const val ARG_DISMISS_MS = "matter_timer_dismiss_ms"
        private const val DIALOG_WIDTH_SCREEN_FRACTION = 0.9f

        /** Default time to read the binding success copy before auto-close. */
        private const val DEFAULT_DISMISS_MS = 7000L

        fun newInstance(
            message: String,
            autoDismissMs: Long = DEFAULT_DISMISS_MS
        ): MatterTimerMessageDialogFragment {
            return MatterTimerMessageDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MESSAGE, message)
                    putLong(ARG_DISMISS_MS, autoDismissMs)
                }
            }
        }
    }

    @SuppressLint("UseGetLayoutInflater")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val message = arguments?.getString(ARG_MESSAGE).orEmpty()

        _binding = DialogMatterTimerMessageBinding.inflate(LayoutInflater.from(requireContext()))
        binding.tvTimerMessage.text = message

        binding.btnTimerCancel.setOnClickListener {
            autoDismissJob?.cancel()
            dismiss()
        }

        return AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()
            .also { dialog ->
                dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                dialog.setCancelable(true)
                dialog.setCanceledOnTouchOutside(true)
            }
    }

    override fun onStart() {
        super.onStart()
        val dlg = dialog ?: return
        dlg.window?.setLayout(
            (DisplayUtils.getScreenWidth(requireActivity()) * DIALOG_WIDTH_SCREEN_FRACTION).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        scheduleAutoDismiss()
    }

    private fun scheduleAutoDismiss() {
        autoDismissJob?.cancel()
        autoDismissJob = lifecycleScope.launch {
            delay(autoDismissMs)
            if (isAdded) dismissAllowingStateLoss()
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        autoDismissJob?.cancel()
        super.onCancel(dialog)
    }

    override fun onDestroyView() {
        autoDismissJob?.cancel()
        super.onDestroyView()
        _binding = null
    }
}
