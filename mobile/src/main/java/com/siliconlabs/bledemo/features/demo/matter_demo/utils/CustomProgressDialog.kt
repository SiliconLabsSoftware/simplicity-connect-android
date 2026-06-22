package com.siliconlabs.bledemo.features.demo.matter_demo.utils

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.databinding.CustomProgressDialogBinding

class CustomProgressDialog(context: Context) : Dialog(context) {
    private lateinit var binding: CustomProgressDialogBinding
    private var cancelButtonVisible: Boolean = false
    private var cancelButtonClickListener: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCancelable(false)
        setCanceledOnTouchOutside(false)

        if (cancelButtonVisible) {
            binding.customProgressBar.visibility = View.GONE
            binding.customMessage.visibility = View.GONE
            binding.layoutMatterCommissioning.visibility = View.VISIBLE
            binding.cancelBar.setOnClickListener {
                cancelButtonClickListener?.invoke()
            }
        } else {
            binding.customProgressBar.visibility = View.VISIBLE
            binding.customMessage.visibility = View.VISIBLE
            binding.layoutMatterCommissioning.visibility = View.GONE
        }
    }

    fun setMessage(message: String) {
        binding = CustomProgressDialogBinding.inflate(LayoutInflater.from(context))
        setContentView(binding.root)
        // Now you can access views through binding
        binding.customMessage.text = message
    }

    fun setCustomButtonVisible(visible: Boolean, clickListener: (() -> Unit)?) {
        cancelButtonVisible = visible
        cancelButtonClickListener = clickListener
    }
}
