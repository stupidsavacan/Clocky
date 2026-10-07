package com.stupidsavacan.clocky.widget.digital

import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [26, 28, 31, 34, 35])
class HostFontCapabilityTest {
    private val app = RuntimeEnvironment.getApplication()

    @Test fun loadedFontAndSubstitutionAreRequiredForTheLatinCapability() {
        assertTrue(HostFontCapability.supportsLatinMarkerIn(app, app))
    }

    @Test fun sameContextPassesButProbeWithoutAHomeHostFailsSafe() {
        // Robolectric has no HOME launcher: the real entry point must fail safe even though app == app passes.
        assertTrue(HostFontCapability.supportsBundledFontsIn(app, app))
        assertEquals(HostFontCapability.Support.NONE, HostFontCapability.probe(app))
    }

    @Test @Config(sdk = [34, 35]) fun restrictedHostCannotUseBundledFontsJustBecauseThePreviewCan() {
        val restricted = object : ContextWrapper(app) {
            override fun isRestricted() = true
        }
        assertFalse(HostFontCapability.supportsBundledFontsIn(app, restricted))
        assertEquals(HostFontCapability.Support.NONE, HostFontCapability.probeIn(app, restricted))
    }

    @Test @Config(sdk = [23, 25]) fun legacyHostsCannotUseBundledFonts() {
        assertFalse(HostFontCapability.supportsBundledFontsIn(app, app))
        assertEquals(HostFontCapability.Support.NONE, HostFontCapability.probe(app))
    }

    @Test @Config(sdk = [34, 35]) fun restrictedHostCannotBecomeLatinJustBecauseThePreviewCan() {
        val restricted = object : ContextWrapper(app) {
            override fun isRestricted() = true
        }
        assertFalse(HostFontCapability.supportsLatinMarkerIn(app, restricted))
    }

    @Test @Config(sdk = [23, 25]) fun legacyHostsCannotLoadTheResourceFace() {
        assertFalse(HostFontCapability.probe(app).latinAmPmMarker)
        assertFalse(HostFontCapability.supportsLatinMarkerIn(app, app))
    }

    @Test @Config(sdk = [34, 35]) fun unknownHomeAndSystemResolverDoNotBorrowThePreviewFontCapability() {
        // Regression: the android package Context falsely advertised font support on the moto.
        assertFalse(HostFontCapability.probe(app).latinAmPmMarker)
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        for (pkg in listOf("android", app.packageName)) {
            val info = ResolveInfo().apply {
                activityInfo = ActivityInfo().apply { packageName = pkg; name = "ResolverActivity" }
            }
            shadowOf(app.packageManager).addResolveInfoForIntent(home, info)
            assertFalse(HostFontCapability.probe(app).latinAmPmMarker)
        }
    }
}
