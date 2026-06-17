package com.siliconlabs.bledemo.features.configure.gatt_configurator.viewholders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.common.views.DetailsRow
import com.siliconlabs.bledemo.databinding.AdapterGattServerBinding
import com.siliconlabs.bledemo.features.configure.gatt_configurator.adapters.GattServerAdapter.OnClickListener
import com.siliconlabs.bledemo.features.configure.gatt_configurator.models.GattServer

class GattServerViewHolder(
        private val viewBinding: AdapterGattServerBinding,
        private val list: List<GattServer>,
        private val listener: OnClickListener
) : RecyclerView.ViewHolder(viewBinding.root) {

    fun bind(gattServer: GattServer, isExportMode: Boolean) {
        expandOrCollapseDetailsView(gattServer.isViewExpanded)

        viewBinding.apply {
            tvGattServerName.text = gattServer.name
            tvTotalServices.text = itemView.context.getString(
                    if (gattServer.services.size == 1) R.string.gatt_configurator_one_service
                    else R.string.gatt_configurator_n_services, gattServer.services.size
            )
            swGattServer.isChecked = gattServer.isSwitchedOn
        }

        prepareView(isExportMode, gattServer)
        prepareDetailsView(gattServer)
        handleClickActions(gattServer)
        handleSwitchActions()
    }

    private fun prepareView(isExportMode: Boolean, gattServer: GattServer) {
        viewBinding.apply {
            cbExport.visibility = if (isExportMode) View.VISIBLE else View.GONE
            swGattServer.isEnabled = !isExportMode

            toggleImageButton(ibCopy, !isExportMode)
            toggleImageButton(ibEdit, !isExportMode)
            toggleImageButton(ibRemove, !isExportMode)

            cbExport.isChecked = isExportMode && gattServer.isCheckedForExport
        }
    }

    private fun handleClickActions(gattServer: GattServer) {
        viewBinding.apply {
            ibCopy.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    listener.onCopyClick(gattServer)
                }
            }

            ibEdit.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    listener.onEditClick(adapterPosition, gattServer)
                }
            }

            ibRemove.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    listener.onRemoveClick(adapterPosition)
                }
            }

            cbExport.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    list[adapterPosition].let {
                        it.isCheckedForExport = !it.isCheckedForExport
                        listener.onExportBoxClick()
                    }
                }
            }
            expandArrow.setOnClickListener {
                gattServer.apply {
                    isViewExpanded = !isViewExpanded
                    expandOrCollapseDetailsView(isViewExpanded)
                }
            }
        }
    }

    private fun handleSwitchActions() {
        viewBinding.swGattServer.setOnCheckedChangeListener { _, isChecked ->
            if (adapterPosition != RecyclerView.NO_POSITION) {
                if (isChecked) {
                    listener.switchItemOn(adapterPosition)
                } else {
                    listener.switchItemOff(adapterPosition)
                }
            }
        }
    }

    private fun prepareDetailsView(gattServer: GattServer) {
        viewBinding.llGattServerDetails.removeAllViews()
        for (service in gattServer.services) {
            viewBinding.llGattServerDetails.addView(DetailsRow(
                    itemView.context,
                    service.getUuidWithName(),
                    itemView.context.getString(
                            if (service.characteristics.size == 1) R.string.gatt_configurator_one_characteristic
                            else R.string.gatt_configurator_n_characteristics, service.characteristics.size
                    )
            ).binding.root, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun expandOrCollapseDetailsView(isExpanded: Boolean) {
        viewBinding.llGattServerDetails.visibility =
            if (isExpanded) View.VISIBLE
            else View.GONE
        viewBinding.expandArrow.setState(shouldShowDetails = isExpanded)
    }

    private fun toggleImageButton(ib: MaterialButton, isEnabled: Boolean) {
        ib.let {
            it.isEnabled = isEnabled
            it.icon.alpha = if (isEnabled) 0xFF else 0x3F
        }
    }

    companion object {
        fun create(parent: ViewGroup, list: List<GattServer>, listener: OnClickListener): GattServerViewHolder {
            val viewBinding = AdapterGattServerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return GattServerViewHolder(viewBinding, list, listener)
        }
    }
}