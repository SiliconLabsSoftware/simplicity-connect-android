package com.siliconlabs.bledemo.features.demo.channel_sounding.utils

import android.content.Context
import android.content.Intent
import com.siliconlabs.bledemo.home_screen.activities.MainActivity
import com.siliconlabs.bledemo.home_screen.fragments.DemoFragment

/**
 * Navigation helpers for reflector re-pairing and returning to the demo home screen.
 */
object ReflectorFlowNavigator {

    const val EXTRA_LAUNCH_REFLECTOR_PAIRING = "extra_launch_reflector_pairing"

    fun openDemoScreen(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(intent)
    }

    /** Opens demo home and shows the reflector device scan / pairing dialog. */
    fun openPairingFlow(context: Context) {
        DemoFragment.requestReflectorPairingOnNextResume()
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_LAUNCH_REFLECTOR_PAIRING, true)
        }
        context.startActivity(intent)
    }
}
