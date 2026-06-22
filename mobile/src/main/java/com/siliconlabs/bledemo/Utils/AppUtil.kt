package com.siliconlabs.bledemo.utils


import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.Window
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import com.siliconlabs.bledemo.R
import com.siliconlabs.bledemo.home_screen.views.HidableBottomNavigationView

object AppUtil {

    fun setEdgeToEdge(window: Window, activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            // Status bar background is dark (#333333), so icons must be rendered in white.
            // isAppearanceLightStatusBars = false => white (light) icons on dark background.
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }

        activity.findViewById<View>(R.id.fakeStatusBar)?.let { fakeStatusBar ->
            ViewCompat.setOnApplyWindowInsetsListener(fakeStatusBar) { v, insets ->
                val statusBarHeight =
                    insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                v.layoutParams = v.layoutParams.apply { height = statusBarHeight }
                v.requestLayout()
                insets
            }
        }

        activity.findViewById<HidableBottomNavigationView>(R.id.main_navigation)
            ?.let { bottomNav ->
                ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, insets ->
                    val navBarHeight =
                        insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                    v.updatePadding(bottom = navBarHeight)
                    insets
                }
            }
    }

    fun optOutEdgeToEdge(window: Window,context: Context) {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = ContextCompat.getColor(context,R.color.blue_primary)
        window.navigationBarColor = ContextCompat.getColor(context,R.color.blue_primary)
    }
}

