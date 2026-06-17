package com.siliconlabs.bledemo.utils

import android.app.Activity
import android.graphics.Point
import android.os.Build

object DisplayUtils {

    fun getScreenWidth(activity: Activity): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.windowManager.currentWindowMetrics.bounds.width()
        } else {
            @Suppress("DEPRECATION")
            Point().also { activity.windowManager.defaultDisplay.getSize(it) }.x
        }
    }
}
