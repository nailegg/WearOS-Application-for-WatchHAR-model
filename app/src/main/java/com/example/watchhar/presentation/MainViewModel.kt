package com.example.watchhar.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.watchhar.domain.ml.MultimodalClassifierRepository
import com.example.watchhar.domain.repository.AudioRepository
import com.example.watchhar.domain.repository.SensorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "MainViewModel"

// score is the EMA-smoothed class probability displayed in the UI.
data class ClassificationResult(val classIndex: Int, val score: Float, val classCount: Int)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val sensorRepository: SensorRepository,
    private val audioRepository: AudioRepository,
    private val classifier: MultimodalClassifierRepository
) : ViewModel() {



    private val _eventDetectorEnabled = MutableStateFlow(true)
    val eventDetectorEnabled: StateFlow<Boolean> = _eventDetectorEnabled.asStateFlow()

    val isEventDetected: StateFlow<Boolean> = combine(
        sensorRepository.isEventDetected,
        eventDetectorEnabled
    ) { rawDetected, detectorEnabled ->
        rawDetected || !detectorEnabled
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = sensorRepository.isEventDetected.value || !_eventDetectorEnabled.value
    )

    private val _classificationResult = MutableStateFlow<ClassificationResult?>(null)
    val classificationResult: StateFlow<ClassificationResult?> = _classificationResult.asStateFlow()

    fun setEventDetectorEnabled(enabled: Boolean) {
        _eventDetectorEnabled.value = enabled
    }

    init {
        Log.d(TAG, "Start Monitoring Sensors")
        sensorRepository.startMonitoring()

        // Reactive microphone activation: start recording only when the effective
        // event state is active, and stop it when the event subsides.  The effective
        // state can bypass the detector for live classifier debugging.
        // R8: reset stale result when event ends so the old label is never briefly
        // shown at the start of the next event.
        viewModelScope.launch {
            isEventDetected.collect { isDetected ->
                if (isDetected) {
                    Log.d(TAG, "Event detected — start recording")
                    audioRepository.startRecording()
                } else {
                    Log.d(TAG, "Event ended — stop recording")
                    audioRepository.stopRecording()
                    _classificationResult.value = null   // R8: clear stale label
                }
            }
        }

        viewModelScope.launch(Dispatchers.Default) {
            // R2: EMA (Exponential Moving Average) over class probabilities.
            // Blends the latest inference output with a running average so that
            // one unusual inference cannot immediately flip the displayed label.
            // alpha=0.35: each new result contributes 35 %; history 65 %.
            // probabilityEma is reset to null at the start of each event (no
            // state from the previous event bleeds into the new one).
            var probabilityEma: FloatArray? = null
            val emaAlpha = 0.35f

            audioRepository.audioWindowReady.collect {
                // Guard: skip classification if event has already subsided.
                if (!isEventDetected.value) {
                    probabilityEma = null
                    return@collect
                }

                val sensorInput = sensorRepository.getLast1sSensorData()
                val audioInput = audioRepository.getLast1sAudioData()

                val probabilities = classifier.run(sensorInput, audioInput)

                // Update EMA: initialise on first inference, blend thereafter.
                val ema = probabilityEma?.also { e ->
                    for (i in probabilities.indices) {
                        e[i] = emaAlpha * probabilities[i] + (1f - emaAlpha) * e[i]
                    }
                } ?: probabilities.copyOf().also { probabilityEma = it }

                val maxIndex = ema.indices.maxByOrNull { ema[it] } ?: return@collect
                _classificationResult.value = ClassificationResult(maxIndex, ema[maxIndex], ema.size)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            sensorRepository.stopMonitoring()
            audioRepository.stopRecording()
            Log.d(TAG, "Successfully released all sensor and audio resources.")
        } catch (e: Exception) {
            Log.e(TAG, "Error during resource release: ${e.message}")
        }
    }

}
