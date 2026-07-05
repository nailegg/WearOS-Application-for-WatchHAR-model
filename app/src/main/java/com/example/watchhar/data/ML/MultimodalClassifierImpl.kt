package com.example.watchhar.data.ML

import android.content.Context
import android.util.Log
import com.example.watchhar.domain.ml.MultimodalClassifierRepository
import com.example.watchhar.util.ModelConfig
import com.example.watchhar.util.loadModelFile
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MultimodalClassifierImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : MultimodalClassifierRepository {

    companion object {
        private const val TAG = "MULTIMODAL_CLASSIFIER"

        private val IMU_ENCODER_FILE    = "${ModelConfig.PRECISION_DIR}/imu_encoder.tflite"
        private val MEL_PREPROCESS_FILE = "${ModelConfig.PRECISION_DIR}/mel_preprocess.tflite"
        private val AUDIO_ENCODER_FILE  = "${ModelConfig.PRECISION_DIR}/audio_encoder.tflite"
        private val FEATURE_FUSION_FILE = "${ModelConfig.PRECISION_DIR}/feature_fusion.tflite"

        // IMU encoder: input (1, 50, 1, 6) floats, output (1, 128) floats
        private const val IMU_INPUT_BYTES  = 1 * 50 * 1 * 6 * 4   // 1 200 bytes
        private const val IMU_OUTPUT_BYTES = 1 * 128 * 4           // 512 bytes

        // mel_preprocess: input (1, 950, 1) floats, output (1, 96, 64) floats
        private const val MEL_INPUT_BYTES  = 1 * 950 * 1 * 4       // 3 800 bytes
        private const val MEL_OUTPUT_BYTES = 1 * 96 * 64 * 4       // 24 576 bytes
        // Note: audio_encoder input (1, 96, 64, 1) == MEL_OUTPUT_BYTES in raw bytes;
        //       outputMelBuffer is passed directly as audio_encoder input (no copy needed).

        // audio_encoder: output (1, 640) floats
        private const val AUDIO_OUTPUT_BYTES = 1 * 640 * 4         // 2 560 bytes

        // feature_fusion: output (1, N) floats. N is read from the loaded TFLite model
        // so 27-class, 19-class, and 20-class fusion heads can share this runtime.
        private const val FLOAT_BYTES = 4
        private const val EXPECTED_IMU_FLOATS = 50 * 6
        private const val EXPECTED_AUDIO_SAMPLES = 950
    }

    private var imuTotalTime   = 0f
    private var audioTotalTime = 0f
    private var fusionTotalTime = 0f
    private var inferenceCount = 0L

    private var imuInterpreter:   Interpreter? = null
    private var melInterpreter:   Interpreter? = null
    private var audioInterpreter: Interpreter? = null
    private var fusionInterpreter: Interpreter? = null

    // ── IMU encoder buffers ──────────────────────────────────────────────────
    private val inputIMUBuffer      = ByteBuffer.allocateDirect(IMU_INPUT_BYTES).order(ByteOrder.nativeOrder())
    private val inputIMUFloatBuffer = inputIMUBuffer.asFloatBuffer()
    private val outputIMUBuffer     = ByteBuffer.allocateDirect(IMU_OUTPUT_BYTES).order(ByteOrder.nativeOrder())

    // ── mel_preprocess buffers ───────────────────────────────────────────────
    private val inputMelBuffer  = ByteBuffer.allocateDirect(MEL_INPUT_BYTES).order(ByteOrder.nativeOrder())
    // outputMelBuffer doubles as audio_encoder input: (1,96,64) and (1,96,64,1) share the same
    // 24 576-byte row-major layout; TFLite ByteBuffer API validates byte count, not rank.
    private val outputMelBuffer = ByteBuffer.allocateDirect(MEL_OUTPUT_BYTES).order(ByteOrder.nativeOrder())

    // ── audio encoder / fusion buffers ───────────────────────────────────────
    private val outputAudioBuffer  = ByteBuffer.allocateDirect(AUDIO_OUTPUT_BYTES).order(ByteOrder.nativeOrder())
    private val outputFusionBuffer: ByteBuffer

    private val numClasses: Int
    private val resultFloatArray: FloatArray

    init {
        val options = Interpreter.Options()
        options.setNumThreads(2)
        options.setUseXNNPACK(true)

        imuInterpreter   = Interpreter(loadModelFile(context, IMU_ENCODER_FILE),    options)
        melInterpreter   = Interpreter(loadModelFile(context, MEL_PREPROCESS_FILE), options)
        audioInterpreter = Interpreter(loadModelFile(context, AUDIO_ENCODER_FILE),  options)
        fusionInterpreter = Interpreter(loadModelFile(context, FEATURE_FUSION_FILE), options)

        val fusionOutputShape = fusionInterpreter?.getOutputTensor(0)?.shape()
            ?: error("Fusion interpreter output tensor is unavailable")
        require(fusionOutputShape.isNotEmpty()) {
            "Invalid fusion output shape: ${fusionOutputShape.contentToString()}"
        }
        numClasses = fusionOutputShape.last()
        require(numClasses > 0) {
            "Invalid fusion class count from shape: ${fusionOutputShape.contentToString()}"
        }
        outputFusionBuffer = ByteBuffer
            .allocateDirect(numClasses * FLOAT_BYTES)
            .order(ByteOrder.nativeOrder())
        resultFloatArray = FloatArray(numClasses)
        Log.i(TAG, "Loaded feature_fusion output shape=${fusionOutputShape.contentToString()}, numClasses=$numClasses")
    }

    @Synchronized
    override fun run(imuData: FloatArray, audioData: ShortArray): FloatArray {
        require(imuData.size == EXPECTED_IMU_FLOATS) {
            "Expected $EXPECTED_IMU_FLOATS IMU floats, got ${imuData.size}"
        }
        require(audioData.size == EXPECTED_AUDIO_SAMPLES) {
            "Expected $EXPECTED_AUDIO_SAMPLES audio samples, got ${audioData.size}"
        }

        if (imuInterpreter == null) {
            Log.e(TAG, "Interpreter not initialized")
        }

        // ── Phase 1: IMU encoding  (1, 50, 1, 6) → (1, 128) ────────────────
        inputIMUBuffer.rewind()
        outputIMUBuffer.rewind()
        inputIMUFloatBuffer.rewind()
        inputIMUFloatBuffer.put(imuData)

        var startTime = System.nanoTime()
        imuInterpreter?.run(inputIMUBuffer, outputIMUBuffer)
        val imuInferenceTime = (System.nanoTime() - startTime) / 1_000_000
        Log.d(TAG, "IMU Encoder Inference Time: $imuInferenceTime ms")

        // ── Phase 2a: PCM normalization  ShortArray → float in [-1, 1] ──────
        // AudioRecord PCM-16 range: [-32768, 32767].
        // mel_preprocess expects float32 in [-1.0, 1.0].
        inputMelBuffer.rewind()
        for (sample in audioData) {
            inputMelBuffer.putFloat(sample.toFloat() / 32768f)
        }

        // ── Phase 2b: mel_preprocess  (1, 950, 1) → (1, 96, 64) ─────────────
        inputMelBuffer.rewind()
        outputMelBuffer.rewind()

        startTime = System.nanoTime()
        melInterpreter?.run(inputMelBuffer, outputMelBuffer)
        val melInferenceTime = (System.nanoTime() - startTime) / 1_000_000
        Log.d(TAG, "Mel Preprocess Inference Time: $melInferenceTime ms")

        // ── Phase 2c: audio_encoder  (1, 96, 64, 1) → (1, 640) ──────────────
        // outputMelBuffer (1,96,64) and audio_encoder input (1,96,64,1) are identical in
        // raw bytes (same 24 576 bytes, same row-major layout). Reuse without extra copy.
        outputMelBuffer.rewind()
        outputAudioBuffer.rewind()

        startTime = System.nanoTime()
        audioInterpreter?.run(outputMelBuffer, outputAudioBuffer)
        val audioInferenceTime = (System.nanoTime() - startTime) / 1_000_000
        Log.d(TAG, "Audio Encoder Inference Time: $audioInferenceTime ms")

        // ── Phase 3: Fusion  [(1,128), (1,640)] → (1, numClasses) ─────────────
        // T#0 imu_features  = outputIMUBuffer  (index 0)
        // T#1 audio_features = outputAudioBuffer (index 1)
        outputIMUBuffer.rewind()
        outputAudioBuffer.rewind()
        outputFusionBuffer.rewind()

        val inputs  = arrayOf(outputIMUBuffer, outputAudioBuffer)
        val outputs = HashMap<Int, Any>()
        outputs[0]  = outputFusionBuffer

        startTime = System.nanoTime()
        fusionInterpreter?.runForMultipleInputsOutputs(inputs, outputs)
        val fusionInferenceTime = (System.nanoTime() - startTime) / 1_000_000
        Log.d(TAG, "Fusion Inference Time: $fusionInferenceTime ms")

        // ── Result extraction ─────────────────────────────────────────────────
        outputFusionBuffer.rewind()
        outputFusionBuffer.asFloatBuffer().get(resultFloatArray)
        softmax(resultFloatArray)   // convert raw logits → probabilities in [0,1]

        val maxIndex = resultFloatArray.indices.maxByOrNull { resultFloatArray[it] } ?: -1
        Log.d(TAG, "Prediction: Class $maxIndex, Score (prob): ${resultFloatArray[maxIndex]}")

        // ── Running averages ──────────────────────────────────────────────────
        inferenceCount  += 1
        imuTotalTime    += imuInferenceTime.toFloat()
        audioTotalTime  += (melInferenceTime + audioInferenceTime).toFloat()
        fusionTotalTime += fusionInferenceTime.toFloat()

        Log.d(TAG, "AVG Inference Time — $inferenceCount runs: " +
                   "IMU=${imuTotalTime / inferenceCount} ms, " +
                   "Audio(mel+enc)=${audioTotalTime / inferenceCount} ms, " +
                   "Fusion=${fusionTotalTime / inferenceCount} ms")

        return resultFloatArray.copyOf()
    }

    private fun softmax(logits: FloatArray) {
        val maxVal = logits.max()
        var expSum = 0f
        for (i in logits.indices) {
            logits[i] = Math.exp((logits[i] - maxVal).toDouble()).toFloat()
            expSum += logits[i]
        }
        for (i in logits.indices) {
            logits[i] /= expSum
        }
    }

    fun close() {
        imuInterpreter?.close()
        melInterpreter?.close()
        audioInterpreter?.close()
        fusionInterpreter?.close()
    }
}
