package com.stupidsavacan.clocky.widget.digital

import android.appwidget.AppWidgetManager
import android.graphics.drawable.BitmapDrawable
import android.os.Parcel
import android.util.SizeF
import android.widget.FrameLayout
import android.widget.ImageView
import com.android.deskclock.R
import com.stupidsavacan.clocky.design.model.*
import com.stupidsavacan.clocky.design.resolve.SizeContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Measured API35 4x4 keys/density. Allocation/parcel regression guard, not real launcher send evidence. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ResponsiveMemoryTest {
    @Test fun worstCaseReportedMapRetainsFivePercentHeadroomWithoutReducingQuality() {
        val app = RuntimeEnvironment.getApplication()
        assertEquals(2.625f,app.resources.displayMetrics.density)
        val options = DigitalWidgetUpdater.optionsOf(SizeContext(360,281,676,483)).apply {
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,arrayListOf(
                SizeF(360.38095f,483.8095f),SizeF(360.38095f,464f),SizeF(676.5714f,281.14285f),SizeF(676.5714f,281.14285f)))
        }
        val d = DigitalDesign(time = TimeElement(TextStyle(fontId = "clocky-poppins",sizeSp = 64f)),
            layout = DesignLayout(template = Template.CENTER_STACK),
            background = BackgroundElement(type = BackgroundType.GRADIENT,paddingDp = 10f),
            info = InfoElement(source = InfoSource.SECOND_TIMEZONE,timeZoneId = "UTC",label = "UTC"),
            behavior = Behavior(hourMode = HourMode.FORCE_12_HOUR,showSeconds = true,amPm = AmPmStyle(AmPmMode.SUFFIX)),
            effects = Effects(ShadowLevel.STRONG))
        HostFontCapability.probeOverride = { HostFontCapability.Support.NONE }
        try {
            val batch = DigitalWidgetUpdater.generate(app,WidgetInstance(1,d),options)
            assertTrue(batch.plan.useMap)
            assertEquals(3,batch.entries.size)
            val bytes = batch.entries.sumOf { e ->
                assertEquals(FontIds.SYSTEM_SANS,e.spec.time.face.fontId)
                val root = e.views.apply(app,FrameLayout(app))
                (root.findViewById<ImageView>(R.id.clocky_widget_background).drawable as BitmapDrawable).bitmap.allocationByteCount.toLong()
            }
            assertTrue("$bytes leaves <5% headroom",bytes <= 15_552_000L * .95)
            val parcel = Parcel.obtain()
            try {
                batch.views.writeToParcel(parcel,0)
                println("API35 native regression guard: bitmapBytes=$bytes parcelBytes=${parcel.dataSize()}")
                // Native Robolectric parcels inline the pixels; real Binder uses bitmap handles.
                // Bound the remaining map/action overhead, not the emulated bitmap payload.
                assertTrue(parcel.dataSize().toLong() <= bytes + 32_768)
            } finally { parcel.recycle() }
        } finally { HostFontCapability.probeOverride = null }
    }
}
