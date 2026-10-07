package com.stupidsavacan.clocky.widget.digital

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextClock
import com.android.deskclock.R

/**
 * RemoteViews inflates foreign-package resources differently from a same-package preview.
 * Probe the resolved HOME launcher resource Context, matching the real widget host on the home
 * screen. The platform android package has a special Context and must not stand in for a launcher.
 * Use this same capability for provider and Preview. Unknown/failed probes never ship raw HH.
 * This only measures an unattached view; no ticking state or capability is persisted.
 */
internal object AmPmHostCapability {
    fun supportsLatin(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 26) return false
        return runCatching {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val hostPackage = context.packageManager.resolveActivity(home, 0)?.activityInfo?.packageName
                ?: return false
            if (hostPackage == "android" || hostPackage == context.packageName) return false
            supportsLatinIn(context, context.createPackageContext(hostPackage, 0))
        }.getOrDefault(false)
    }

    internal fun supportsLatinIn(app: Context, host: Context): Boolean {
        if (Build.VERSION.SDK_INT < 26) return false
        val marker = RemoteViews(app.packageName, R.layout.clocky_face_ampm_marker_off)
            .apply(host, FrameLayout(host)) as TextClock
        if (marker.typeface != app.resources.getFont(R.font.clocky_ampm_marker)) return false
        // Verify both groups actually substitute: all hours in each half share its marker width.
        // Typeface identity excludes a system font where every two-digit number has equal width.
        val am = marker.paint.measureText("00")
        val pm = marker.paint.measureText("12")
        return am != pm && (0..23).all { hour ->
            val digits = hour.toString().padStart(2, '0')
            marker.paint.measureText(digits) == if (hour < 12) am else pm
        }
    }
}
