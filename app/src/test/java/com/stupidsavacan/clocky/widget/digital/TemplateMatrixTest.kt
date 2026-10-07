package com.stupidsavacan.clocky.widget.digital

import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import com.android.deskclock.R
import com.stupidsavacan.clocky.design.model.*
import com.stupidsavacan.clocky.design.resolve.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Replays the complete 3A-0 300-case matrix through the activated production generation path. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 31, 34, 35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TemplateMatrixTest {
    private fun design(template: Template) = DigitalDesign(layout = DesignLayout(template = template),
        info = InfoElement(source = InfoSource.SECOND_TIMEZONE,timeZoneId = "UTC",label = "UTC"),
        behavior = Behavior(hourMode = HourMode.FORCE_12_HOUR,showSeconds = true,amPm = AmPmStyle(AmPmMode.SUFFIX)),
        effects = Effects(shadow = ShadowLevel.STRONG))

    @Test @Config(sdk = [31,34,35])
    fun actualReportedFractionalKeysPassTheSameGeometryAudit() {
        val app = RuntimeEnvironment.getApplication()
        val rows = org.json.JSONArray(javaClass.getResourceAsStream("/phase3a2/reported-sizes.json")!!.bufferedReader().readText())
        var cases = 0
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val size = SizeContext(row.getInt("minW"),row.getInt("minH"),row.getInt("maxW"),row.getInt("maxH"))
            val sizes = row.getJSONArray("sizes")
            val options = DigitalWidgetUpdater.optionsOf(size)
            val reported = arrayListOf<android.util.SizeF>()
            for (j in 0 until sizes.length()) reported.add(android.util.SizeF(sizes.getJSONArray(j).getString(0).toFloat(),sizes.getJSONArray(j).getString(1).toFloat()))
            if (reported.isNotEmpty()) options.putParcelableArrayList(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_SIZES,reported)
            Template.entries.forEach { template ->
                val batch = DigitalWidgetUpdater.generate(app,WidgetInstance(1,design(template)),options)
                assertEquals(reported.isNotEmpty(),batch.plan.useMap)
                batch.entries.forEach { e -> verify(e,"${row.getInt("api")}/${row.getString("cell")}/$template"); cases++ }
            }
        }
        assertEquals(345,cases)
    }

    @Test fun everyMeasuredTemplateClassAndOrientationFitsWithoutClippingOrOverlap() {
        val app = RuntimeEnvironment.getApplication()
        val rows = javaClass.getResourceAsStream("/phase3a2/measured-bounds.csv")!!.bufferedReader().readLines().drop(1)
        assertEquals(30,rows.size)
        var cases = 0
        rows.forEach { row ->
            val v = row.split(',').map { it.toInt() }
            val size = SizeContext(v[1],v[2],v[3],v[4])
            Template.entries.forEach { template ->
                val d = design(template)
                val batch = DigitalWidgetUpdater.generate(app,WidgetInstance(1,d),DigitalWidgetUpdater.optionsOf(size))
                batch.entries.forEach { e ->
                    val label = "$row/$template/${e.spec.sizeClass}/${e.size}"
                    verify(e,label)
                    cases++
                }
            }
        }
        assertEquals(300,cases)
    }

    private fun verify(e: RenderEntry, label: String) {
        val app = RuntimeEnvironment.getApplication()
        val density = app.resources.displayMetrics.density
        val root = e.views.apply(app,FrameLayout(app))
        val time = DigitalWidgetFit.visibleTextsIn(root,R.id.clocky_time_slot)
        time[0].text = DigitalWidgetFit.widestTime(time[0],e.spec.timeFormats.format12Hour)
        if (time.size > 1) time[1].text = DigitalWidgetFit.widestAmPm(time[1],e.spec.amPm!!.format12Hour)
        val date = DigitalWidgetFit.visibleTextIn(root,R.id.clocky_date_slot)
        date?.text = DigitalWidgetFit.widestDate(date,e.spec.datePattern)
        val info = DigitalWidgetFit.visibleTextIn(root,R.id.clocky_info_slot)
        info?.text = DigitalWidgetFit.widestTime(info,e.spec.info!!.format12Hour)
        val w = (e.size.width*density).toInt()
        val h = (e.size.height*density).toInt()
        val unspecified = View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED)
        root.measure(unspecified,unspecified)
        assertTrue("natural overflow $label ${root.measuredWidth}x${root.measuredHeight} > ${w}x$h",
            root.measuredWidth <= w && root.measuredHeight <= h)
        root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY))
        root.layout(0,0,w,h)
        val rects = (time + listOfNotNull(date,info)).filter { it.text.isNotEmpty() }.map { t ->
            Rect(0,0,t.width,t.height).also { (root as android.view.ViewGroup).offsetDescendantRectToMyCoords(t,it) }
        }
        rects.forEach { r -> assertTrue("clipping $label $r",Rect(0,0,w,h).contains(r)) }
        rects.forEachIndexed { i, r -> rects.drop(i+1).forEach { other -> assertFalse("overlap $label $r $other",Rect.intersects(r,other)) } }
    }
}
