package com.example.watchhar.util

// Primitive storage avoids boxing allocations on the hot audio path.
class ShortRingBuffer(private val capacity: Int) {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val buffer = ShortArray(capacity)
    private var head = 0
    private var size = 0


    fun write(value: Short) {
        buffer[head] = value
        head = (head + 1) % capacity
        if (size < capacity) size++
    }

    fun write(value: ShortArray) {
        for (v in value) {
            write(v)
        }
    }

    fun getLastData(count: Int): ShortArray {
        val result = ShortArray(count)
        if (count > size) throw IllegalArgumentException("count is larger than buffer size")
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
    }

    fun isFull(): Boolean {
        return size == capacity
    }

}
