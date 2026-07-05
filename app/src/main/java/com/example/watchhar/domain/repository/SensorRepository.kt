package com.example.watchhar.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface SensorRepository {

    val isEventDetected: StateFlow<Boolean>
    fun startMonitoring()
    fun getLast1sSensorData(): FloatArray
    fun stopMonitoring()
}
