package com.example.watchhar.domain.ml

interface MultimodalClassifierRepository {
    // Returns softmax probabilities for the loaded fusion model's activity classes.
    fun run(imuData: FloatArray, audioData: ShortArray): FloatArray
}
