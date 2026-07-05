package com.example.watchhar.util

object ModelConfig {
    const val USE_FLOAT16: Boolean = false
    val PRECISION_DIR: String = if (USE_FLOAT16) "float16" else "float32"
}
