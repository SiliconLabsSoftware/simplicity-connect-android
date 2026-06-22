package com.siliconlabs.bledemo.features.demo.matter_demo.fragments

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
import com.siliconlabs.bledemo.databinding.DialogNetworkSelectionMatterBinding
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterOTBRInputDialogFragment.Companion.WINDOW_SIZE
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannerFragment.Companion.CANCEL_REQ_CODE
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannerFragment.Companion.THREAD_REQ_CODE
import com.siliconlabs.bledemo.features.demo.matter_demo.fragments.MatterScannerFragment.Companion.WIFI_REQ_CODE
import com.siliconlabs.bledemo.features.demo.matter_demo.model.ProvisionNetworkType
import com.siliconlabs.bledemo.utils.DisplayUtils


class MatterNetworkSelectionInputDialogFragment : DialogFragment() {

    private lateinit var binding: DialogNetworkSelectionMatterBinding

    companion object {
        fun newInstance():
                MatterNetworkSelectionInputDialogFragment =
            MatterNetworkSelectionInputDialogFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogNetworkSelectionMatterBinding.inflate(inflater, container, false)

        if (dialog != null && dialog!!.window != null) {
            dialog!!.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT));
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
                    (DisplayUtils.getScreenWidth(requireActivity()) * WINDOW_SIZE).toInt(),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners(view)
    }

    private fun setupClickListeners(view: View) {
        binding.llCancel.setOnClickListener {
            val intent = Intent()
            intent.putExtra(
                MatterScannerFragment.ARG_PROVISION_NETWORK_TYPE,
                ProvisionNetworkType.NONE
            )
            targetFragment?.onActivityResult(
                targetRequestCode, CANCEL_REQ_CODE,
                intent
            )
            dismiss()
        }

        binding.llThread.setOnClickListener {
            val intent = Intent()
            intent.putExtra(
                MatterScannerFragment.ARG_PROVISION_NETWORK_TYPE,
                ProvisionNetworkType.THREAD
            )
            targetFragment?.onActivityResult(
                targetRequestCode, THREAD_REQ_CODE,
                intent
            )
            dismiss()
        }
        binding.llWifi.setOnClickListener {
            val intent = Intent()
            intent.putExtra(
                MatterScannerFragment.ARG_PROVISION_NETWORK_TYPE,
                ProvisionNetworkType.WIFI
            )
            targetFragment?.onActivityResult(
                targetRequestCode, WIFI_REQ_CODE,
                intent
            )
            dismiss()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

}