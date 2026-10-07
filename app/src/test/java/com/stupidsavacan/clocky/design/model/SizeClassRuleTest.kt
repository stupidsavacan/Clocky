package com.stupidsavacan.clocky.design.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SizeClassRuleTest {
    @Test fun allThirtyMeasuredHostCellsArePinned() {
        val lines = javaClass.getResourceAsStream("/phase3a1/measured-cells.csv")!!.bufferedReader().use { it.readLines() }.drop(1)
        assertEquals(30, lines.size)
        lines.forEach { line ->
            val (host, cell, width, height, expected) = line.split(',')
            assertEquals("$host $cell", SizeClass.valueOf(expected), SizeClassRule.resolve(width.toFloat(), height.toFloat()))
        }
    }

    @Test fun heightAndRatioBoundariesAndSquarePriority() {
        listOf(99f to SizeClass.STRIP, 100f to SizeClass.CARD, 101f to SizeClass.CARD,
            159f to SizeClass.CARD, 160f to SizeClass.LARGE, 161f to SizeClass.LARGE).forEach { (h, expected) ->
            assertEquals(expected, SizeClassRule.resolve(600f, h))
        }
        val boundary = SizeClassRule.SQUARE_RATIO * 160f
        assertEquals(SizeClass.SQUARE, SizeClassRule.resolve(boundary - 0.001f, 160f))
        assertEquals(SizeClass.LARGE, SizeClassRule.resolve(boundary, 160f))
        assertEquals(SizeClass.LARGE, SizeClassRule.resolve(boundary + 0.001f, 160f))
        assertEquals(SizeClass.SQUARE, SizeClassRule.resolve(667f, 281f))
    }

    @Test fun invalidOrMissingInputFallsBackToCard() {
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach {
            assertEquals(SizeClass.CARD, SizeClassRule.resolve(it, 58f))
            assertEquals(SizeClass.CARD, SizeClassRule.resolve(667f, it))
        }
    }

    @Test fun existingEnumOrderIsAdditive() {
        assertEquals(listOf(SizeClass.STRIP, SizeClass.CARD), SizeClass.entries.take(2))
    }
}
