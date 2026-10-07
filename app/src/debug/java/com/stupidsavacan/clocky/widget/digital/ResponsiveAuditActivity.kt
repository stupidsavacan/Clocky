package com.stupidsavacan.clocky.widget.digital

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.os.Parcel
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.android.deskclock.R
import com.stupidsavacan.clocky.design.model.*
import org.json.JSONArray
import org.json.JSONObject

/** Debug only. Real options, common queued generation/send path, no design/settings writes. */
class ResponsiveAuditActivity : Activity() {
    private val samples = JSONArray()
    private lateinit var status: TextView
    private var id = AppWidgetManager.INVALID_APPWIDGET_ID
    private var index = 0
    private var count = 30
    private var active = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        status = TextView(this).apply { text = "Measuring responsive production updates…" }
        setContentView(status)
        id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID)
        count = intent.getIntExtra("samples",30).coerceIn(1,100)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        active = true
        next()
    }

    private fun next() {
        if (!active) return
        val app = applicationContext
        val wm = AppWidgetManager.getInstance(app)
        val options = wm.getAppWidgetOptions(id)
        val d = DigitalDesign(
            time = TimeElement(TextStyle(fontId = "clocky-poppins",sizeSp = 64f)),
            layout = DesignLayout(template = Template.CENTER_STACK),
            info = InfoElement(source = InfoSource.SECOND_TIMEZONE,timeZoneId = "UTC",label = "UTC"),
            behavior = Behavior(hourMode = HourMode.FORCE_12_HOUR,showSeconds = true,amPm = AmPmStyle(AmPmMode.SUFFIX)),
            effects = Effects(shadow = ShadowLevel.STRONG),
            background = BackgroundElement(type = BackgroundType.GRADIENT,paddingDp = 10f),
        )
        DigitalWidgetUpdater.update(app,wm,id,options,onFinished = {
            runOnUiThread {
                index++
                if (index < count && active) next() else complete()
            }
        }, measurementDesign = d, onMeasured = { batch, sendMs, endMs ->
            // All inspection is AFTER the production send/end timestamp, excluded from timing.
            var bytes = 0L
            val entries = JSONArray()
            batch.entries.forEach { e ->
                val root = e.views.apply(app,FrameLayout(app))
                val bitmap = (root.findViewById<ImageView>(R.id.clocky_widget_background).drawable as? BitmapDrawable)?.bitmap
                val allocation = bitmap?.allocationByteCount ?: 0
                bytes += allocation
                entries.put(JSONObject().put("key",e.size.toString()).put("class",e.spec.sizeClass.name)
                    .put("resolveMs",e.resolveNanos/1e6).put("fitMs",e.fitNanos/1e6).put("composeMs",e.composeNanos/1e6)
                    .put("bitmapBytes",allocation).put("effectiveFont",e.spec.time.face.fontId))
            }
            val parcel = Parcel.obtain()
            val parcelBytes = try { batch.views.writeToParcel(parcel,0); parcel.dataSize() } finally { parcel.recycle() }
            val row = JSONObject().put("firstUpdate",index == 0).put("generationMs",batch.generationNanos/1e6)
                .put("remoteViewsMs",batch.remoteViewsNanos/1e6).put("sendMs",sendMs).put("endToEndMs",endMs)
                .put("bitmapBytes",bytes).put("parcelBytes",parcelBytes).put("entries",entries)
            samples.put(row)
            Log.d("ClockyRenderAudit",row.toString())
        })
    }

    private fun complete() {
        val app = applicationContext
        val file = java.io.File(app.filesDir,"phase3a2-performance.json")
        file.writeText(JSONObject().put("sdk",android.os.Build.VERSION.SDK_INT).put("samples",samples).toString(2))
        DigitalWidgetUpdater.update(app,AppWidgetManager.getInstance(app),id)
        status.text = "Complete: ${samples.length()} updates. Saved phase3a2-performance.json; original design restored."
        active = false
    }

    override fun onDestroy() {
        active = false
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) DigitalWidgetUpdater.update(applicationContext,AppWidgetManager.getInstance(this),id)
        super.onDestroy()
    }
}
