package com.example.watchhar.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.watchhar.domain.repository.AudioRepository
import com.example.watchhar.util.ShortRingBuffer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AudioRepository"

@Singleton
class AudioRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : AudioRepository {

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val READ_CHUNK_SIZE = 320
        private const val AUDIO_BUFFER_CAPACITY = 950
        private const val DOWNSAMPLE_STEP = 16
        // 20 ms hop: emit once per read chunk (20 downsampled samples = 20 ms at 1 kHz).
        // MutableSharedFlow(DROP_OLDEST) absorbs backpressure when inference is slower
        // than 20 ms — effective rate self-adjusts to device throughput.
        // First emission still gated by isFull() (~960 ms warm-up, unchanged).
        private const val AUDIO_WINDOW_HOP = 20
    }

    private val audioBuffer = ShortRingBuffer(AUDIO_BUFFER_CAPACITY)
    private val audioLock = Any()

    // Counts downsampled samples written since the last audioWindowReady emission.
    // Once the buffer is full, isFull() stays true forever (ring buffer), so this
    // counter keeps the post-warm-up emission cadence at AUDIO_WINDOW_HOP samples.
    // Buffer mutation and emission gating stay inside audioLock so the classifier
    // never snapshots a partially-updated ring buffer.
    private var samplesSinceLastEmit = 0

    // R7: class-level scope replaces orphaned CoroutineScope(Dispatchers.IO) created on
    // every startRecording() call.  SupervisorJob ensures a cancelled child job does not
    // cancel the scope, so the scope can launch new jobs on subsequent startRecording() calls.
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var recordingJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var selectedAudioSourceLabel: String = "UNKNOWN"

    // R6: extraBufferCapacity=1 + DROP_OLDEST prevents emit() from suspending the audio read
    // loop when the downstream collector (inference) is slower than the 20 ms emission cadence.
    private val _audioWindowReady = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val audioWindowReady = _audioWindowReady.asSharedFlow()



    override fun startRecording() {
        if (recordingJob?.isActive == true) return

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "no permission")
            return
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val internalBufferSize = maxOf(minBufferSize * 2, 16000)

        audioRecord = createAudioRecord(internalBufferSize)

        if (audioRecord == null) {
            throw RuntimeException("Audio Record can't initialize!")
        }

        audioRecord?.startRecording()
        synchronized(audioLock) {
            audioBuffer.clear()
            samplesSinceLastEmit = 0
        }
        try {
            recordingJob = repositoryScope.launch {
                val readBuffer = ShortArray(READ_CHUNK_SIZE)
                val downsampledBuffer = ShortArray(READ_CHUNK_SIZE / DOWNSAMPLE_STEP)

                while (currentCoroutineContext().isActive) {
                    val readAudio = audioRecord?.read(readBuffer, 0, READ_CHUNK_SIZE) ?: -1

                    if (readAudio > 0) {
                        val downsampledCount = readAudio / DOWNSAMPLE_STEP
                        if (downsampledCount == 0) {
                            continue
                        }

                        for (i in 0 until downsampledCount) {
                            downsampledBuffer[i] = readBuffer[i * DOWNSAMPLE_STEP]
                        }

                        val shouldEmit = synchronized(audioLock) {
                            for (i in 0 until downsampledCount) {
                                audioBuffer.write(downsampledBuffer[i])
                            }
                            samplesSinceLastEmit += downsampledCount
                            val ready = audioBuffer.isFull() && samplesSinceLastEmit >= AUDIO_WINDOW_HOP
                            if (ready) {
                                samplesSinceLastEmit = 0
                            }
                            ready
                        }

                        if (shouldEmit) {
                            _audioWindowReady.emit(Unit)
                        }
                    } else {
                        Log.e(TAG, "Audio read error: $readAudio")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
        }
    }

    override fun stopRecording() {
        try {
            recordingJob?.cancel()
            recordingJob = null

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            synchronized(audioLock) {
                audioBuffer.clear()
                samplesSinceLastEmit = 0
            }
            Log.d(TAG, "Recording stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Stop failed", e)
        }
    }

    private fun createAudioRecord(internalBufferSize: Int): AudioRecord? {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Cannot create AudioRecord without RECORD_AUDIO permission")
            return null
        }

        for (source in candidateAudioSources()) {
            val record = AudioRecord(
                source,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                internalBufferSize
            )
            if (record.state == AudioRecord.STATE_INITIALIZED) {
                selectedAudioSourceLabel = audioSourceLabel(source)
                Log.i(TAG, "Selected audio source: $selectedAudioSourceLabel")
                return record
            }

            val failedLabel = audioSourceLabel(source)
            Log.w(TAG, "Audio source $failedLabel failed to initialize")
            record.release()
        }
        return null
    }

    private fun candidateAudioSources(): List<Int> {
        val candidates = listOf(
            MediaRecorder.AudioSource.UNPROCESSED,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        )
        Log.i(TAG, "Audio source candidates=${candidates.map { audioSourceLabel(it) }}")
        return candidates
    }

    private fun audioSourceLabel(source: Int): String {
        return when (source) {
            MediaRecorder.AudioSource.UNPROCESSED -> "UNPROCESSED"
            MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
            MediaRecorder.AudioSource.MIC -> "MIC"
            else -> source.toString()
        }
    }

    override fun getLast1sAudioData(): ShortArray {
        synchronized(audioLock) {
            return audioBuffer.getLastData(AUDIO_BUFFER_CAPACITY)
        }
    }
}
