package com.example.watchhar.util

import java.util.Arrays

// Primitive storage avoids boxing allocations on the hot sensor path.
class FloatRingBuffer(private val capacity: Int) {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val buffer = FloatArray(capacity)
    private var head = 0
    private var size = 0
    private var sum = 0f


    fun write(value: Float) {
        if (size == capacity) {
            sum -= buffer[head]
        }
        sum = sum + value
        buffer[head] = value

        head = (head + 1) % capacity
        if (size < capacity) size++
    }

    fun write(value: FloatArray) {
        for (v in value) {
            write(v)
        }
    }

    fun getLastData(count: Int): FloatArray {
        val result = FloatArray(count)
        if (count > capacity) throw IllegalArgumentException("count is larger than buffer size")

        val start = (head - count + capacity) % capacity
        if (start + count <= capacity) {
            System.arraycopy(buffer, start, result, 0, count)
        } else {
            val endChunkSize = capacity - start
            System.arraycopy(buffer, start, result, 0, endChunkSize)
            System.arraycopy(buffer, 0, result, endChunkSize, count - endChunkSize)
        }
        return result
    }

    fun clear() {
        head = 0
        size = 0
        sum = 0f
        Arrays.fill(buffer, 0f)
    }

    fun isFull(): Boolean {
        return size == capacity
    }

    fun getAvg(): Float {
        return if (size == 0) 0f else sum / size
    }

    fun copyLastDataTo(destination: FloatArray) {
        val count = destination.size
        if (count > capacity) throw IllegalArgumentException("Buffer size too small")

        val start = (head - count + capacity) % capacity
        if (start + count <= capacity) {
            System.arraycopy(buffer, start, destination, 0, count)
        } else {
            val endChunkSize = capacity - start
            System.arraycopy(buffer, start, destination, 0, endChunkSize)
            System.arraycopy(buffer, 0, destination, endChunkSize, count - endChunkSize)
        }
    }
}
