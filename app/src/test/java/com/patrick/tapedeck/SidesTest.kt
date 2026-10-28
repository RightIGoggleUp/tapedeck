package com.patrick.tapedeck

import org.junit.Assert.assertEquals
import org.junit.Test

class SidesTest {
    @Test fun oddCountPutsExtraOnSideA() {
        val (a, b) = splitSides((1..5).toList())
        assertEquals(3, a.size); assertEquals(2, b.size)
    }
    @Test fun singleTrack() {
        val (a, b) = splitSides(listOf(1))
        assertEquals(1, a.size); assertEquals(0, b.size)
    }
    @Test fun empty() {
        val (a, b) = splitSides(emptyList<Int>())
        assertEquals(0, a.size + b.size)
    }
}
