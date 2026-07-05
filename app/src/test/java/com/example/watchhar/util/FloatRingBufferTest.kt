package com.example.watchhar.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FloatRingBufferTest {
    @Test
    fun getLastDataReturnsNewestValuesAfterWraparound() {
        val buffer = FloatRingBuffer(5)

        buffer.write(floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f))

        assertArrayEquals(floatArrayOf(2f, 3f, 4f, 5f, 6f), buffer.getLastData(5), 0f)
        assertArrayEquals(floatArrayOf(4f, 5f, 6f), buffer.getLastData(3), 0f)
    }

    @Test
    fun getAvgTracksCurrentWindowOnly() {
        val buffer = FloatRingBuffer(3)

        buffer.write(floatArrayOf(1f, 2f, 3f, 10f))

        assertEquals(5f, buffer.getAvg(), 0f)
    }

    @Test
    fun rejectsNonPositiveCapacity() {
        assertThrows(IllegalArgumentException::class.java) {
            FloatRingBuffer(0)
        }
    }
}
