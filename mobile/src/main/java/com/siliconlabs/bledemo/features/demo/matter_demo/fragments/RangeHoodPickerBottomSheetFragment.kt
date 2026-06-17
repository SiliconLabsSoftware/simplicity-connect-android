package com.siliconlabs.bledemo.features.demo.matter_demo.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ListView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.R as MaterialR
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.features.demo.matter_demo.model.MatterScannedResultModel

/**
 * Bottom sheet that lists Range Hood devices for the user to pick one (Oven–RangeHood binding).
 * Replaces the previous AlertDialog; same binding flow is handled by the caller.
 * Uses floating iOS-style theme (margins, transparent container, custom rounded background).
 */
class RangeHoodPickerBottomSheetFragment : BottomSheetDialogFragment() {

    var listener: Listener? = null

    interface Listener {
        fun onRangeHoodSelected(device: MatterScannedResultModel)
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
        return inflater.inflate(R.layout.dialog_oven_rangehood_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val devices = requireArguments().getParcelableArrayList<MatterScannedResultModel>(ARG_DEVICES)
            ?: return
        val list = view.findViewById<ListView>(R.id.list_range_hoods)
        val names = devices.map { it.matterName }
        list.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, names)
        list.setOnItemClickListener { _, _, position, _ ->
            listener?.onRangeHoodSelected(devices[position])
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
        private const val ARG_DEVICES = "devices"

        fun newInstance(devices: ArrayList<MatterScannedResultModel>): RangeHoodPickerBottomSheetFragment {
            return RangeHoodPickerBottomSheetFragment().apply {
                arguments = Bundle().apply { putParcelableArrayList(ARG_DEVICES, devices) }
            }
        }
    }
}
