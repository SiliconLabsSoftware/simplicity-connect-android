package com.siliconlabs.bledemo.utils


import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import com.pranavpandey.android.dynamic.toasts.DynamicToast
import com.pranavpandey.android.dynamic.toasts.R as DynamicToastLibR
import com.siliconlabs.bledemo.R


object CustomToastManager {

    private fun applyToastTypeface(context: Context) {
        ResourcesCompat.getFont(context.applicationContext, R.font.stolzl_medium)?.let { tf ->
            DynamicToast.Config.getInstance().setTextTypeface(tf).apply()
        }
    }

    private fun centerToastMessage(toast: Toast) {
        toast.view?.findViewById<TextView>(DynamicToastLibR.id.adt_toast_text)?.gravity =
            Gravity.CENTER_HORIZONTAL
    }

    @SuppressLint("StaticFieldLeak")
    fun showError(context: Context, message: String, duration: Long = 5000) {
        applyToastTypeface(context)
        DynamicToast.makeError(context, message, duration.toInt()).also { centerToastMessage(it) }.show()
    }

    @SuppressLint("StaticFieldLeak")
    fun show(context: Context, message: String, duration: Long = 5000) {
        applyToastTypeface(context)
        DynamicToast.make(context, message, duration.toInt()).also { centerToastMessage(it) }.show()
    }

    fun showSuccess(context: Context, message: String, duration: Long = 5000) {
        applyToastTypeface(context)
        DynamicToast.makeSuccess(context, message, duration.toInt()).also { centerToastMessage(it) }.show()
    }

}
