package com.stupidsavacan.clocky.widget.digital

import android.os.Bundle
import android.appwidget.AppWidgetManager
import android.util.SizeF
import android.widget.FrameLayout
import com.stupidsavacan.clocky.design.model.*
import com.stupidsavacan.clocky.design.resolve.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28, 30, 31, 34, 35])
class ResponsiveRenderTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private fun options() = DigitalWidgetUpdater.optionsOf(SizeContext(360, 281, 676, 483)).apply {
        putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(360.38f,483.81f),SizeF(676.57f,281.14f)))
    }
    @Test
    @Config(sdk = [28,31,34,35])
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun nativeMeasurementFitsDifferentEntryGeometryAndSharedSamplesMatchIndependentFits() {
        val d = DigitalDesign(time = TimeElement(TextStyle(sizeSp = 200f)),layout = DesignLayout(template = Template.MINIMAL))
        val batch = DigitalWidgetUpdater.generate(app,WidgetInstance(1,d),options())
        assertNotEquals(batch.entries[0].fit.timePx,batch.entries[1].fit.timePx)
        batch.entries.forEach { e ->
            assertEquals(DigitalWidgetFit.fit(app,e.spec,(e.size.width*app.resources.displayMetrics.density).toInt(),
                (e.size.height*app.resources.displayMetrics.density).toInt()),e.fit)
        }
    }
    @Test fun reportedKeysOnlyAndOneWidgetClassWithEntrySpecificFit() {
        val d = DigitalDesign(layout = DesignLayout(overrides = mapOf(
            SizeClass.SQUARE to LayoutPatch(timeSizeSp = 200f, template = Template.MINIMAL),
        )))
        val batch = DigitalWidgetUpdater.generate(app, WidgetInstance(1,d), options())
        assertEquals(SizeClass.SQUARE, batch.plan.sizeClass)
        assertEquals(android.os.Build.VERSION.SDK_INT >= 31, batch.plan.useMap)
        if (batch.plan.useMap) assertEquals(listOf(SizeF(360.38f,483.81f), SizeF(676.57f,281.14f)),batch.plan.entries)
        batch.entries.forEach { entry ->
            assertEquals(SizeClass.SQUARE, entry.spec.sizeClass)
            assertEquals(200f, entry.spec.time.sizeSp)
            assertEquals(DigitalWidgetFit.fit(app,entry.spec,(entry.size.width*app.resources.displayMetrics.density).toInt(),
                (entry.size.height*app.resources.displayMetrics.density).toInt()), entry.fit)
            assertNotNull(entry.views.apply(app,FrameLayout(app)))
        }
        assertNotNull(batch.views.apply(app,FrameLayout(app)))
    }
    @Test fun malformedOrAbsentSizesHaveSafePairFallbackAndCardForMissingRuleInputs() {
        val options = Bundle().apply { putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,
            arrayListOf(SizeF(-1f,20f),SizeF(0f,20f))) }
        val plan = ResponsiveRenderPlan.from(options,31)
        assertFalse(plan.useMap)
        assertEquals(SizeClass.CARD,plan.sizeClass)
        assertEquals(2,plan.entries.size)
        assertNotNull(DigitalWidgetUpdater.generate(app,WidgetInstance(1,DigitalDesign()),options).views.apply(app,FrameLayout(app)))
    }
    @Test fun duplicateKeysAreRemovedAndLimitIsSixteenHostKeys() {
        val sizes = (1..20).map { SizeF(it.toFloat(),40f) }
        val options = options().apply { putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,ArrayList(sizes+sizes)) }
        assertEquals(sizes.take(16),ResponsiveRenderPlan.from(options,31).entries)
        assertFalse(ResponsiveRenderPlan.from(options,30).useMap)
    }
    @Test fun previewAndWidgetResolveIdenticallyForEveryClass() {
        SizeClass.entries.forEach { cls ->
            val host = PreviewHost(FrameLayout(app),1)
            val size = host.previewSize(cls)
            val d = DigitalDesign(info = InfoElement(source = InfoSource.SECOND_TIMEZONE,timeZoneId = "UTC"))
            val entry = DigitalWidgetUpdater.generate(app,WidgetInstance(1,d),DigitalWidgetUpdater.optionsOf(size)).entries[0]
            val spec = host.render(d,cls)
            assertEquals(entry.spec,spec)
            assertEquals(SizeClassRule.resolve(size.maxWidthDp.toFloat(),size.minHeightDp.toFloat()),cls)
        }
    }
    @Test fun splitCardGeometryIsInheritedBySquareAndLarge() {
        val d = DigitalDesign(layout = DesignLayout(template = Template.SPLIT,
            overrides = mapOf(SizeClass.CARD to LayoutPatch(dateGapDp = 9f,timeSizeSp = 80f))))
        SizeClass.entries.filter { it != SizeClass.STRIP }.forEach { cls ->
            val size = SizeF(363f,260f)
            val entry = DigitalWidgetUpdater.renderEntry(app,d,size,cls,DigitalWidgetUpdater.environment(app))
            val root = entry.views.apply(app,FrameLayout(app))
            assertEquals((9f*app.resources.displayMetrics.density).toInt(),root.findViewById<android.view.View>(com.android.deskclock.R.id.clocky_date_slot).paddingBottom)
        }
    }
}
