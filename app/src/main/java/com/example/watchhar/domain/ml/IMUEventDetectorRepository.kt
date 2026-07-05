package com.example.watchhar.domain.ml

interface IMUEventDetectorRepository {
    fun run(imuData: FloatArray): Float
}