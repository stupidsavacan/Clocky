package com.stupidsavacan.clocky.widget.digital

import android.content.ContextWrapper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [26, 28, 31, 34, 35])
class AmPmHostCapabilityTest {
    private val app = RuntimeEnvironment.getApplication()

    @Test fun loadedFontAndSubstitutionAreRequiredForTheLatinCapability() {
        assertTrue(AmPmHostCapability.supportsLatinIn(app, app))
    }

    @Test @Config(sdk = [34, 35]) fun restrictedHostCannotBecomeLatinJustBecauseThePreviewCan() {
        val restricted = object : ContextWrapper(app) {
            override fun isRestricted() = true
        }
        assertFalse(AmPmHostCapability.supportsLatinIn(app, restricted))
    }

    @Test @Config(sdk = [23, 25]) fun legacyHostsCannotLoadTheResourceFace() {
        assertFalse(AmPmHostCapability.supportsLatin(app))
        assertFalse(AmPmHostCapability.supportsLatinIn(app, app))
    }
}
