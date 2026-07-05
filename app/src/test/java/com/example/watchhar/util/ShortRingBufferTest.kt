package com.example.watchhar.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ShortRingBufferTest {
    @Test
    fun getLastDataReturnsNewestValuesAfterWraparound() {
        val buffer = ShortRingBuffer(4)

        buffer.write(shortArrayOf(10, 20, 30, 40, 50))

        assertArrayEquals(shortArrayOf(20, 30, 40, 50), buffer.getLastData(4))
        assertArrayEquals(shortArrayOf(40, 50), buffer.getLastData(2))
    }

    @Test
    fun getLastDataRejectsRequestsBeforeBufferHasEnoughSamples() {
        val buffer = ShortRingBuffer(4)

        buffer.write(shortArrayOf(1, 2))

        assertThrows(IllegalArgumentException::class.java) {
            buffer.getLastData(3)
        }
    }
}
