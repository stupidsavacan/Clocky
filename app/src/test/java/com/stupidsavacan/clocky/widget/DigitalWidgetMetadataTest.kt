package com.stupidsavacan.clocky.widget

import android.content.res.XmlResourceParser
import com.android.deskclock.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/**
 * Pins the Digital provider geometry to the 4x1 / 4x2 contract restored from the retained MVP
 * metadata (WIDGET_CUSTOMIZATION_CONTRACT.md section 11). How a launcher turns these values into
 * cells is launcher behavior and still needs device verification.
 */
@RunWith(RobolectricTestRunner::class)
class DigitalWidgetMetadataTest {

    private fun providerAttributes(): Map<String, String?> {
        val parser: XmlResourceParser =
            RuntimeEnvironment.getApplication().resources.getXml(R.xml.digital_appwidget)
        parser.use {
            while (it.next() != XmlPullParser.START_TAG || it.name != "appwidget-provider") Unit
            return listOf(
                "minWidth", "minHeight", "minResizeWidth", "minResizeHeight",
                "targetCellWidth", "targetCellHeight", "widgetFeatures", "configure",
            ).associateWith { name -> it.getAttributeValue(ANDROID_NS, name) }
        }
    }

    private fun assertSharedGeometry(attrs: Map<String, String?>) {
        assertEquals("250.0dip", attrs["minWidth"])
        assertEquals("70.0dip", attrs["minHeight"])
        assertEquals("250.0dip", attrs["minResizeWidth"])
        // 40dp (one row), not the MVP's 70dp: on the moto g13 / Motorola Launcher3 (API 34) 70dp
        // produced minSpan(3,2) and the widget could not be resized to 4x1. See contract section 11.
        assertEquals("40.0dip", attrs["minResizeHeight"])
        assertEquals(DigitalWidgetConfigActivity::class.java.name, attrs["configure"])
    }

    @Test
    @Config(sdk = [23])
    fun preApi28MetadataUsesClockyGeometry() {
        val attrs = providerAttributes()
        assertSharedGeometry(attrs)
        assertNull(attrs["widgetFeatures"])
    }

    @Test
    @Config(sdk = [34])
    fun api28PlusMetadataTargetsFourByTwoAndIsReconfigurable() {
        val attrs = providerAttributes()
        assertSharedGeometry(attrs)
        assertEquals("4", attrs["targetCellWidth"])
        assertEquals("2", attrs["targetCellHeight"])
        // widgetFeatures="reconfigurable" is the flag value 0x1.
        assertEquals("0x1", attrs["widgetFeatures"])
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
