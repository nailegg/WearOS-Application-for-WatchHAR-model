package com.example.watchhar.domain.repository

import kotlinx.coroutines.flow.Flow

interface AudioRepository {
    val audioWindowReady: Flow<Unit>
    fun startRecording()
    fun stopRecording()
    fun getLast1sAudioData(): ShortArray
}