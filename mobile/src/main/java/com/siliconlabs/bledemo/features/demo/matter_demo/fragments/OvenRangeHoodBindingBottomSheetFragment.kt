package com.siliconlabs.bledemo.features.demo.matter_demo.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.IdRes
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.R as MaterialR
import com.siliconlabs.bledemo.R

/**
 * Modal bottom sheet for Oven–RangeHood binding: Unbind, Bind to different RangeHood, or Bind to RangeHood.
 * Binding state is persisted via [com.siliconlabs.bledemo.features.demo.matter_demo.utils.SharedPrefsUtils].
 * Uses floating iOS-style: margins so sheet doesn't touch edges; transparent dialog background with custom rounded drawable.
 */
class OvenRangeHoodBindingBottomSheetFragment : BottomSheetDialogFragment() {

    var listener: Listener? = null

    interface Listener {
        fun onUnbind()
        fun onBindToDifferent()
        fun onBind()
        fun onCancel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.ThemeOverlay_App_BottomSheetDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_oven_rangehood_binding, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val isBound = arguments?.getBoolean(ARG_IS_BOUND, false) ?: false

        val message = view.findViewById<TextView>(R.id.tv_oven_rangehood_binding_message)
        message.text = if (isBound) {
            getString(R.string.matter_oven_rangehood_binding_dialog_message_bound)
        } else {
            getString(R.string.matter_oven_rangehood_binding_dialog_message_not_bound)
        }

        view.findViewById<View>(R.id.btn_oven_rangehood_unbind).apply {
            visibility = if (isBound) View.VISIBLE else View.GONE
            setOnClickListener {
                listener?.onUnbind()
                dismiss()
            }
        }

        view.findViewById<View>(R.id.btn_oven_rangehood_bind_to_different).apply {
            // When bound, sheet shows only Unbind + Cancel (per design). Bind to different is hidden.
            visibility = View.GONE
            setOnClickListener {
                listener?.onBindToDifferent()
                dismiss()
            }
        }

        view.findViewById<View>(R.id.btn_oven_rangehood_bind).apply {
            visibility = if (isBound) View.GONE else View.VISIBLE
            setOnClickListener {
                listener?.onBind()
                dismiss()
            }
        }

        view.findViewById<View>(R.id.btn_oven_rangehood_cancel).setOnClickListener {
            listener?.onCancel()
            dismiss()
        }

        // Floating: apply horizontal and bottom margins so sheet doesn't touch screen edges
        dialog?.setOnShowListener {
            val bottomSheet = dialog?.findViewById<FrameLayout>(MaterialR.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                val params = sheet.layoutParams
                if (params is ViewGroup.MarginLayoutParams) {
                    val margin = resources.getDimensionPixelSize(R.dimen.matter_16dp)
                    params.leftMargin = margin
                    params.rightMargin = margin
                    params.bottomMargin = margin
                    sheet.layoutParams = params
                }
            }
        }
    }

    companion object {
        private const val ARG_OVEN_NODE_ID = "oven_node_id"
        private const val ARG_IS_BOUND = "is_bound"
        private const val ARG_RANGEHOOD_NODE_ID = "rangehood_node_id"

        fun newInstance(
            ovenNodeId: Long,
            isBound: Boolean,
            rangeHoodNodeId: Long? = null
        ): OvenRangeHoodBindingBottomSheetFragment {
            return OvenRangeHoodBindingBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_OVEN_NODE_ID, ovenNodeId)
                    putBoolean(ARG_IS_BOUND, isBound)
                    rangeHoodNodeId?.let { putLong(ARG_RANGEHOOD_NODE_ID, it) }
                }
            }
        }
    }
}
