package com.example.watchhar.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityLabelsTest {
    @Test
    fun mapsKnownClassCountsToHumanReadableLabels() {
        assertEquals("Alarm clock", ActivityLabels.labelFor(27, 0))
        assertEquals("Other", ActivityLabels.labelFor(27, 26))
        assertEquals("Brushing hair", ActivityLabels.labelFor(19, 0))
        assertEquals("Unknown", ActivityLabels.labelFor(20, 19))
    }

    @Test
    fun fallsBackForUnexpectedClassCountOrIndex() {
        assertEquals("Class 7", ActivityLabels.labelFor(18, 7))
        assertEquals("Class 99", ActivityLabels.labelFor(27, 99))
    }
}
