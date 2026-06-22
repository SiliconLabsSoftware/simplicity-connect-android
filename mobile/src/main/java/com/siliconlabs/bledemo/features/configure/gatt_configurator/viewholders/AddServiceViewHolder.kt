package com.siliconlabs.bledemo.features.configure.gatt_configurator.viewholders

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.siliconlabs.bledemo.databinding.AdapterAddServiceBinding
import com.siliconlabs.bledemo.features.configure.gatt_configurator.adapters.EditGattServerAdapter.AddServiceListener

//import kotlinx.android.synthetic.main.adapter_add_service.view.*

class AddServiceViewHolder(
    view: AdapterAddServiceBinding,
    private val addServiceListener: AddServiceListener
) : RecyclerView.ViewHolder(view.root) {
    private val btnAddService = view.btnAddService


    fun bind() {
        handleClickEvents()
    }

    private fun handleClickEvents() {
        btnAddService.setOnClickListener {
            addServiceListener.onAddService()
        }
    }

    companion object {
        fun create(
            parent: ViewGroup,
            addServiceListener: AddServiceListener
        ): AddServiceViewHolder {
            val binding = AdapterAddServiceBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )

            return AddServiceViewHolder(binding, addServiceListener)
        }
    }
}