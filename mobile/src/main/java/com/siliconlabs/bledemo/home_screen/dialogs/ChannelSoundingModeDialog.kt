package com.siliconlabs.bledemo.home_screen.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import by.kirich1409.viewbindingdelegate.viewBinding
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.base.fragments.BaseDialogFragment
import com.siliconlabs.bledemo.databinding.DialogChannelSoundingModeSelectionBinding

/**
 * Dialog for selecting Channel Sounding mode: Phone as Initiator or Phone as Reflector.
 * 
 * Phase 2 Feature: This dialog is shown when the user taps the Channel Sounding demo tile.
 * - Initiator Mode: Continues with existing initiator flow (phone measures distance)
 * - Reflector Mode: Scans for CS Initiator devices (door locks) for Digital Key use case
 */
class ChannelSoundingModeDialog(
    private val callback: Callback
) : BaseDialogFragment(
    hasCustomWidth = false,
    isCanceledOnTouchOutside = true
) {

    private val binding by viewBinding(DialogChannelSoundingModeSelectionBinding::bind)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.dialog_channel_sounding_mode_selection, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Full window width so layout_constraintWidth_percent on the card is 90% of screen, not of the default 85% dialog width.
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.apply {
            // Initiator Mode - Continue with existing flow
            cardInitiatorMode.setOnClickListener {
                dismiss()
                callback.onInitiatorModeSelected()
            }

            // Reflector Mode - Scan for CS Initiator devices
            cardReflectorMode.setOnClickListener {
                dismiss()
                callback.onReflectorModeSelected()
            }

            // Cancel button
            btnCancel.setOnClickListener {
                dismiss()
                callback.onCancelled()
            }
        }
    }

    /**
     * Callback interface for handling mode selection events.
     */
    interface Callback {
        /**
         * Called when user selects "Phone as Initiator" mode.
         * Should continue with existing initiator connection flow.
         */
        fun onInitiatorModeSelected()

        /**
         * Called when user selects "Phone as Reflector" mode.
         * Should trigger device scan for CS Initiator devices (e.g., "Silabs Example").
         */
        fun onReflectorModeSelected()

        /**
         * Called when user cancels the dialog.
         */
        fun onCancelled()
    }

    companion object {
        const val TAG = "ChannelSoundingModeDialog"

        fun newInstance(callback: Callback): ChannelSoundingModeDialog {
            return ChannelSoundingModeDialog(callback)
        }
    }
}
