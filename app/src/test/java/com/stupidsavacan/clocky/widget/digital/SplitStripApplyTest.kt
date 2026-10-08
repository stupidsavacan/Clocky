package com.stupidsavacan.clocky.widget.digital

import android.widget.FrameLayout
import com.stupidsavacan.clocky.design.model.*
import com.stupidsavacan.clocky.design.resolve.*
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The moto 2x1 SPLIT/Strip failure from the 3A-0 audit, applied by RemoteViews itself. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28, 31, 34, 35])
class SplitStripApplyTest {
    @Test fun measuredStripInflates() {
        val app = RuntimeEnvironment.getApplication()
        val size = SizeContext(173, 58, 325, 122)
        val design = DigitalDesign(layout = DesignLayout(template = Template.SPLIT))
        val (_, rv) = DigitalWidgetUpdater.buildPortrait(app, design, size)
        assertNotNull(rv.apply(app, FrameLayout(app)))
    }
}
