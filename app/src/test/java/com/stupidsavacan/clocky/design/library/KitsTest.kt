package com.stupidsavacan.clocky.design.library

import com.stupidsavacan.clocky.design.model.FontIds
import com.stupidsavacan.clocky.design.resolve.FontCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KitsTest {
    @Test
    fun kitsAreTheFourDeclaredOnesWithUniqueIds() {
        assertEquals(listOf("default", "minimal", "bold", "editorial"), Kits.all.map { it.id })
        Kits.all.forEach { assertEquals(it, Kits.byId(it.id)) }
    }

    @Test
    fun paletteListsHaveUniqueIdsFromTheLibrary() {
        Kits.all.forEach { kit ->
            val ids = kit.palettes.map { it.palette.id }
            assertEquals("${kit.id} duplicate palettes", ids.size, ids.toSet().size)
            kit.palettes.forEach { assertTrue("${kit.id}/${it.palette.id}", it.palette in Palettes.all) }
        }
    }

    @Test
    fun combosReferenceOnlyDeclaredPalettesAndAllowedTemplates() {
        Kits.all.forEach { kit ->
            assertTrue("${kit.id} has combos", kit.combos.isNotEmpty())
            kit.combos.forEach { c ->
                assertNotNull("${kit.id} combo palette ${c.paletteId}", kit.paletteById(c.paletteId))
                assertTrue("${kit.id} card ${c.cardTemplate}", c.cardTemplate in Kits.cardTemplates)
                assertTrue("${kit.id} strip ${c.stripTemplate}", c.stripTemplate in Kits.stripTemplates)
                assertTrue("${kit.id} typeface ${c.typeface}", c.typeface in kit.typePairs)
            }
            assertEquals("${kit.id} duplicate combos", kit.combos.size, kit.combos.toSet().size)
        }
    }

    @Test
    fun everyKitDefinesAllSixTypefaceCategories() {
        Kits.all.forEach { kit ->
            assertEquals(TypefaceCategory.entries.toSet(), kit.typePairs.keys)
        }
    }

    @Test
    fun typePairFontsExistAndLegacyFamiliesUseTheOnlyExactWeight() {
        Kits.all.forEach { kit ->
            kit.typePairs.forEach { (category, pair) ->
                listOf(pair.primary, pair.secondary).forEach { spec ->
                    val where = "${kit.id}/$category/${spec.fontId}"
                    assertTrue("$where unknown font", spec.fontId in FontCatalog.allIds)
                    assertTrue("$where weight", spec.weight in 100..900)
                    if (spec.fontId != FontIds.SYSTEM_SANS) {
                        assertEquals("$where legacy family weight", 400, spec.weight)
                    }
                }
            }
        }
    }

    @Test
    fun templateListsOfferThreeOrFourDistinctChoicesPerClass() {
        assertTrue(Kits.cardTemplates.size in 3..4)
        assertTrue(Kits.stripTemplates.size in 3..4)
        assertEquals(Kits.cardTemplates.size, Kits.cardTemplates.toSet().size)
        assertEquals(Kits.stripTemplates.size, Kits.stripTemplates.toSet().size)
    }
}
