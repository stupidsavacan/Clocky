package com.stupidsavacan.clocky.design.storage

import org.robolectric.RuntimeEnvironment
import com.stupidsavacan.clocky.design.library.BuiltinDesigns
import com.stupidsavacan.clocky.design.model.DigitalDesign
import com.stupidsavacan.clocky.design.model.SizeClass
import com.stupidsavacan.clocky.design.model.TextStyle
import com.stupidsavacan.clocky.design.model.TimeElement
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DesignRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private var now = 1_000L
    private val dir get() = File(tmp.root, "designs")
    private fun repo(dir: File = this.dir) = FileDesignRepository(dir, clock = { now++ })
    private val custom = DigitalDesign(time = TimeElement(TextStyle(sizeSp = 80f, weight = 300)))

    @Test
    fun createPersistsUnderUuidFileWithMetadata() {
        val saved = repo().create("  Evening   clock ", custom)
        assertTrue(FileDesignRepository.isValidId(saved.id))
        assertEquals("Evening clock", saved.name)
        assertFalse(saved.favorite)
        assertEquals(saved.createdAt, saved.updatedAt)
        assertEquals(custom, saved.design)
        val file = File(dir, saved.id + ".json")
        assertTrue(file.isFile)
        val json = JSONObject(file.readText())
        assertEquals(2, json.getJSONObject("design").getInt("schema"))
        assertEquals(saved.id, json.getString("id"))
        assertEquals(listOf(saved.id + ".json"), dir.list()!!.toList())
    }

    @Test
    fun savedDesignSurvivesRestartAndKeepsBuiltinProvenance() {
        val builtin = BuiltinDesigns.byId("bold-poster")!!.instantiate()
        val saved = repo().create("Poster", builtin)
        val reopened = FileDesignRepository(dir).get(saved.id)
        assertEquals(saved, reopened)
        assertEquals("bold-poster", reopened!!.design.source?.builtinId)
        assertEquals(listOf(saved), FileDesignRepository(dir).list())
    }

    @Test
    fun idsAreUniqueAndNeverOverwriteExistingDesigns() {
        val ids = ArrayDeque(listOf("00000000-0000-4000-8000-000000000001", "00000000-0000-4000-8000-000000000001", "00000000-0000-4000-8000-000000000002"))
        val r = FileDesignRepository(dir, clock = { now++ }, newId = { ids.removeFirst() })
        val a = r.create("A", custom)
        val b = r.create("B", DigitalDesign())
        assertEquals("00000000-0000-4000-8000-000000000001", a.id)
        assertEquals("00000000-0000-4000-8000-000000000002", b.id)
        assertEquals(custom, r.get(a.id)!!.design)
        assertEquals(2, r.list().size)
    }

    @Test
    fun randomIdsDoNotCollide() {
        val r = repo()
        val ids = (1..50).map { r.create("d$it", custom).id }.toSet()
        assertEquals(50, ids.size)
    }

    @Test
    fun listIsNewestUpdatedFirst() {
        val r = repo()
        val a = r.create("A", custom)
        val b = r.create("B", custom)
        assertEquals(listOf(b.id, a.id), r.list().map { it.id })
        r.rename(a.id, "A2")
        assertEquals(listOf(a.id, b.id), r.list().map { it.id })
    }

    @Test
    fun renameChangesNameAndUpdatedAtOnly() {
        val r = repo()
        val a = r.create("A", custom)
        val renamed = r.rename(a.id, " New name ")!!
        assertEquals("New name", renamed.name)
        assertTrue(renamed.updatedAt > a.updatedAt)
        assertEquals(a.createdAt, renamed.createdAt)
        assertEquals(a.design, renamed.design)
        assertNull(r.rename("00000000-0000-4000-8000-0000000000ff", "x"))
    }

    @Test
    fun blankAndOversizedNames() {
        val r = repo()
        try {
            r.create("   \n ", custom)
            fail("blank name accepted")
        } catch (_: IllegalArgumentException) {
        }
        assertTrue(r.list().isEmpty())
        val saved = r.create("x".repeat(500), custom)
        assertEquals(FileDesignRepository.MAX_NAME_LENGTH, saved.name.length)
        try {
            r.rename(saved.id, "")
            fail("blank rename accepted")
        } catch (_: IllegalArgumentException) {
        }
        assertEquals(saved.name, r.get(saved.id)!!.name)
    }

    @Test
    fun favoriteTogglesWithoutTouchingTheDesign() {
        val r = repo()
        val a = r.create("A", custom)
        val fav = r.setFavorite(a.id, true)!!
        assertTrue(fav.favorite)
        assertEquals(a.updatedAt, fav.updatedAt)
        assertTrue(FileDesignRepository(dir).get(a.id)!!.favorite)
        assertFalse(r.setFavorite(a.id, false)!!.favorite)
    }

    @Test
    fun duplicateIsIndependentCopyWithNewIdAndNoFavorite() {
        val r = repo()
        val a = r.create("A", custom)
        r.setFavorite(a.id, true)
        val copy = r.duplicate(a.id, "A copy")!!
        assertNotEquals(a.id, copy.id)
        assertEquals("A copy", copy.name)
        assertFalse(copy.favorite)
        assertEquals(a.design, copy.design)
        r.rename(copy.id, "Other")
        r.delete(a.id)
        assertEquals(custom, r.get(copy.id)!!.design)
        assertNull(r.duplicate(a.id, "gone"))
    }

    @Test
    fun deleteRemovesOnlyThatDesign() {
        val r = repo()
        val a = r.create("A", custom)
        val b = r.create("B", custom)
        assertTrue(r.delete(a.id))
        assertFalse(r.delete(a.id))
        assertNull(r.get(a.id))
        assertEquals(listOf(b.id), r.list().map { it.id })
    }

    @Test
    fun invalidIdsCannotEscapeTheDirectory() {
        val r = repo()
        r.create("A", custom)
        val outside = File(tmp.root, "victim.json").apply { writeText("{}") }
        assertNull(r.get("../victim"))
        assertFalse(r.delete("../victim"))
        assertNull(r.rename("../victim", "x"))
        assertTrue(outside.exists())
        assertFalse(FileDesignRepository.isValidId("../victim"))
        assertFalse(FileDesignRepository.isValidId("ABCDEF00-0000-4000-8000-000000000001"))
    }

    @Test
    fun corruptFilesAreSkippedAndLeftOnDisk() {
        val r = repo()
        val good = r.create("Good", custom)
        dir.mkdirs()
        val bad = File(dir, "11111111-1111-4111-8111-111111111111.json").apply { writeText("not json") }
        val noDesign = File(dir, "22222222-2222-4222-8222-222222222222.json").apply {
            writeText("""{"format":1,"id":"22222222-2222-4222-8222-222222222222","name":"x","createdAt":1,"updatedAt":1}""")
        }
        val wrongId = File(dir, "33333333-3333-4333-8333-333333333333.json").apply {
            writeText(File(dir, good.id + ".json").readText())
        }
        File(dir, "junk.txt").writeText("hi")
        assertEquals(listOf(good.id), r.list().map { it.id })
        assertNull(r.get("11111111-1111-4111-8111-111111111111"))
        assertNull(r.setFavorite("11111111-1111-4111-8111-111111111111", true))
        assertEquals("not json", bad.readText())
        assertTrue(noDesign.exists())
        assertTrue(wrongId.exists())
    }

    @Test
    fun unknownFieldsSurviveMetadataEditsAndDuplication() {
        val id = "44444444-4444-4444-8444-444444444444"
        val design = DigitalDesignCodec.encode(custom)
        design.getJSONObject("layout").put("overrides", JSONObject().put("poster", JSONObject().put("size", 999)))
        design.put("futureTop", JSONObject().put("a", 1))
        val envelope = JSONObject()
            .put("format", 1).put("id", id).put("name", "Future").put("createdAt", 5L).put("updatedAt", 5L)
            .put("favorite", false).put("design", design).put("envelopeExtra", "keep")
        dir.mkdirs()
        File(dir, "$id.json").writeText(envelope.toString())

        val r = repo()
        val loaded = r.get(id)!!
        assertEquals(mapOf("poster" to "{\"size\":999}"), loaded.design.layout.preservedOverrides)

        r.rename(id, "Renamed")
        r.setFavorite(id, true)
        val afterEdits = JSONObject(File(dir, "$id.json").readText())
        assertEquals("keep", afterEdits.getString("envelopeExtra"))
        assertEquals(1, afterEdits.getJSONObject("design").getJSONObject("futureTop").getInt("a"))
        assertEquals(999, afterEdits.getJSONObject("design").getJSONObject("layout").getJSONObject("overrides").getJSONObject("poster").getInt("size"))

        val copy = r.duplicate(id, "Copy")!!
        assertEquals(loaded.design.layout.preservedOverrides, copy.design.layout.preservedOverrides)
        val copyJson = JSONObject(File(dir, copy.id + ".json").readText())
        assertEquals(1, copyJson.getJSONObject("design").getJSONObject("futureTop").getInt("a"))
        assertEquals("keep", copyJson.getString("envelopeExtra"))
    }

    @Test
    fun legacyPhase1Documents_loadAndStayReadable() {
        val v1 = JSONObject().put("schema", 1).put("presetId", "google-clock")
        val id = "55555555-5555-4555-8555-555555555555"
        dir.mkdirs()
        File(dir, "$id.json").writeText(
            JSONObject().put("format", 1).put("id", id).put("name", "Old").put("createdAt", 1L).put("updatedAt", 1L).put("design", v1).toString(),
        )
        val r = repo()
        val old = r.get(id)
        assertNotNull(old)
        // Metadata edits keep the stored v1 document untouched.
        r.rename(id, "Older")
        assertEquals(1, JSONObject(File(dir, "$id.json").readText()).getJSONObject("design").getInt("schema"))
    }

    @Test
    fun codecRoundTripKeepsResponsiveOverrides() {
        val builtin = BuiltinDesigns.byId("minimal-quiet-split")!!.instantiate()
        val saved = repo().create("Split", builtin)
        assertEquals(builtin, saved.design)
        assertEquals(builtin.layout.overrides.keys, saved.design.layout.overrides.keys)
        assertTrue(SizeClass.entries.isNotEmpty())
    }

    @Test
    fun builtinFavoritesAreRecordedSeparatelyAndPersist() {
        val ctx = RuntimeEnvironment.getApplication()
        val favs = BuiltinFavorites(ctx)
        val before = BuiltinDesigns.all.map { it.design }
        assertTrue(favs.ids().isEmpty())
        favs.set("bold-poster", true)
        favs.set("editorial-serif", true)
        favs.set("bold-poster", false)
        assertEquals(setOf("editorial-serif"), BuiltinFavorites(ctx).ids())
        assertTrue(BuiltinFavorites(ctx).isFavorite("editorial-serif"))
        assertEquals(before, BuiltinDesigns.all.map { it.design })
    }
}
