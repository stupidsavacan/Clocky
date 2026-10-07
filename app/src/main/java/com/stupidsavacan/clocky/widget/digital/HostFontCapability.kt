package com.stupidsavacan.clocky.widget.digital

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextClock
import com.android.deskclock.R

/**
 * What the real RemoteViews host can render from this APK's `res/font`.
 *
 * RemoteViews inflates foreign-package resources differently from a same-package preview, and a launcher's
 * restricted Context may silently ignore `android:fontFamily="@font/..."` (moto g13 / API 34 / Launcher3:
 * every bundled face rendered as system sans). So neither the API level nor a same-package inflate says anything
 * about the host. Probe the resolved HOME launcher resource Context, exactly the Context a placed widget is
 * applied with. The platform `android` package has a special Context and must not stand in for a launcher.
 *
 * Granularity: one flag for all bundled font resources. They share one mechanism (a font resource referenced
 * from a TextView in the inflated tree), so a host that drops one drops all (confirmed for all six families on
 * the audited host); a per-face probe would only add cost. The result is never persisted: it is recomputed at
 * every resolve, so a different launcher is never judged by a stale answer. Unknown/failed probes are unsupported.
 * Provider and Preview both take their value from here, so they resolve the same effective face.
 */
internal object HostFontCapability {
    data class Support(val bundledFonts: Boolean, val latinAmPmMarker: Boolean) {
        companion object { val NONE = Support(bundledFonts = false, latinAmPmMarker = false) }
    }

    /** Tests only: stands in for the launcher probe (Robolectric has no real RemoteViews host). */
    @androidx.annotation.VisibleForTesting
    internal var probeOverride: ((Context) -> Support)? = null

    fun probe(context: Context): Support {
        probeOverride?.let { return it(context) }
        if (Build.VERSION.SDK_INT < 26) return Support.NONE
        return runCatching {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val hostPackage = context.packageManager.resolveActivity(home, 0)?.activityInfo?.packageName
                ?: return Support.NONE
            if (hostPackage == "android" || hostPackage == context.packageName) return Support.NONE
            probeIn(context, context.createPackageContext(hostPackage, 0))
        }.getOrDefault(Support.NONE)
    }

    internal fun probeIn(app: Context, host: Context): Support {
        val bundled = runCatching { supportsBundledFontsIn(app, host) }.getOrDefault(false)
        // The marker is itself a bundled font resource: no bundled-font support, no marker.
        val marker = bundled && runCatching { supportsLatinMarkerIn(app, host) }.getOrDefault(false)
        return Support(bundled, marker)
    }

    /** Applies a real bundled-face fragment with [host] and compares what the host actually shaped. */
    internal fun supportsBundledFontsIn(app: Context, host: Context): Boolean {
        if (Build.VERSION.SDK_INT < 26) return false
        val root = RemoteViews(app.packageName, R.layout.clocky_face_poppins_off)
            .apply(host, FrameLayout(host))
        val probed = root.findViewById<TextClock>(R.id.clocky_face_w400) ?: return false
        val expected = app.resources.getFont(R.font.clocky_poppins_400)
        // Typeface identity may differ between Resources instances; the shaped width is what matters, and
        // it must also differ from the platform default so a silent system fallback is never accepted.
        if (probed.typeface == expected) return true
        val sample = "0123456789:AMP aGgj"
        return widthOf(probed.typeface, sample) == widthOf(expected, sample) &&
            widthOf(Typeface.DEFAULT, sample) != widthOf(expected, sample)
    }

    internal fun supportsLatinMarkerIn(app: Context, host: Context): Boolean {
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

    private fun widthOf(typeface: Typeface?, text: String): Float =
        Paint().apply { this.typeface = typeface }.measureText(text)
}
