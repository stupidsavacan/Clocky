package com.stupidsavacan.clocky.design.storage

import android.content.Context
import org.robolectric.RuntimeEnvironment
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.TextStyle
import com.stupidsavacan.clocky.design.model.TimeElement
import com.stupidsavacan.clocky.design.model.WidgetInstance
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPreferencesDesignStoreTest {
    private val context = RuntimeEnvironment.getApplication()
    private val prefs = context.getSharedPreferences(SharedPreferencesDesignStore.PREFS_NAME, Context.MODE_PRIVATE)
    private val store = SharedPreferencesDesignStore(context)

    @Test
    fun missingWidgetLoadsDefaults() {
        assertEquals(WidgetInstance(5), store.load(5))
        assertFalse(prefs.contains(SharedPreferencesDesignStore.key(5)))
    }

    @Test
    fun saveLoadAndDeleteRoundTrip() {
        val instance = WidgetInstance(7, DigitalDesign(time = TimeElement(TextStyle(sizeSp = 80f, weight = 300))))
        store.save(instance)
        assertEquals(instance, store.load(7))
        store.delete(7)
        assertNull(prefs.getString(SharedPreferencesDesignStore.key(7), null))
    }

    @Test
    fun v1DocumentMigratesInPlaceAndIsWrittenBackAsV2() {
        prefs.edit().putString(
            SharedPreferencesDesignStore.key(9),
            """{"schema":1,"time":{"weight":250},"fourByOne":{"dateEnabled":false}}""",
        ).commit()

        val loaded = store.load(9)
        assertEquals(250, loaded.design.time.style.weight)
        assertEquals(false, loaded.design.layout.patchFor(SizeClass.STRIP).dateVisible)

        val stored = JSONObject(prefs.getString(SharedPreferencesDesignStore.key(9), null)!!)
        assertEquals(2, stored.getInt("schema"))
        assertEquals(loaded, store.load(9))
    }

    @Test
    fun undecodableDocumentRendersDefaultsWithoutBeingOverwritten() {
        val future = """{"schema":99,"something":"new"}"""
        prefs.edit().putString(SharedPreferencesDesignStore.key(11), future).commit()
        assertEquals(DigitalDesign(), store.load(11).design)
        assertEquals(future, prefs.getString(SharedPreferencesDesignStore.key(11), null))

        prefs.edit().putString(SharedPreferencesDesignStore.key(12), "not json").commit()
        assertEquals(DigitalDesign(), store.load(12).design)
        assertEquals("not json", prefs.getString(SharedPreferencesDesignStore.key(12), null))
    }
}
