package com.stupidsavacan.clocky.design.storage

import com.stupidsavacan.clocky.design.library.BuiltinDesigns
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/** Phase 3C-2: documents imported into the library keep their unknown fields and never touch other entries. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DesignRepositoryImportTest {
    @get:Rule val tmp = TemporaryFolder()

    private var now = 1_000L
    private val dir get() = File(tmp.root, "designs")
    private fun repo() = FileDesignRepository(dir, clock = { now++ })

    private fun document() = DigitalDesignCodec.encode(BuiltinDesigns.byId("bold-poster")!!.instantiate())
        .put("futureTop", JSONObject().put("n", 1))

    @Test
    fun createFromDocumentStoresTheDocumentVerbatim() {
        val doc = document()
        val saved = repo().createFromDocument("Imported", doc)
        assertEquals("Imported", saved.name)
        assertEquals(doc.toString(), repo().rawDesign(saved.id).toString())
        assertEquals(1, repo().rawDesign(saved.id)!!.getJSONObject("futureTop").getInt("n"))
        assertEquals(BuiltinDesigns.byId("bold-poster")!!.instantiate(), repo().get(saved.id)!!.design)
    }

    @Test
    fun theCallerKeepsOwnershipOfTheDocument() {
        val doc = document()
        val saved = repo().createFromDocument("Imported", doc)
        doc.put("later", true)
        assertFalse(repo().rawDesign(saved.id)!!.has("later"))
    }

    @Test
    fun metadataEditsAndDuplicatesKeepUnknownFields() {
        val repo = repo()
        val saved = repo.createFromDocument("Imported", document())
        repo.rename(saved.id, "Renamed")
        repo.setFavorite(saved.id, true)
        val copy = repo.duplicate(saved.id, "Copy")!!
        listOf(saved.id, copy.id).forEach { assertEquals(1, repo.rawDesign(it)!!.getJSONObject("futureTop").getInt("n")) }
    }

    @Test
    fun undecodableOrNamelessDocumentsAreRejectedAndLeaveNoFile() {
        val repo = repo()
        try {
            repo.createFromDocument("Bad", JSONObject().put("schema", 3))
            fail()
        } catch (_: IllegalArgumentException) {
        }
        try {
            repo.createFromDocument("   ", document())
            fail()
        } catch (_: IllegalArgumentException) {
        }
        assertTrue(dir.list().isNullOrEmpty())
    }

    @Test
    fun existingEntriesAreNeverOverwrittenByImports() {
        val repo = repo()
        val first = repo.createFromDocument("A", document())
        val before = File(dir, first.id + ".json").readText()
        repeat(5) { repo.createFromDocument("A", document()) }
        assertEquals(before, File(dir, first.id + ".json").readText())
        assertEquals(6, repo.list().size)
    }

    @Test
    fun rawDesignOfUnknownOrInvalidIdIsNull() {
        val repo = repo()
        assertNull(repo.rawDesign("../../etc/passwd"))
        assertNull(repo.rawDesign("00000000-0000-0000-0000-000000000000"))
    }
}
