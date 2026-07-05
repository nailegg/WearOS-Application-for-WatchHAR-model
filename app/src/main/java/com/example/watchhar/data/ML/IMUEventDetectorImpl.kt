package com.example.watchhar.data.ML

import android.content.Context
import android.util.Log
import com.example.watchhar.domain.ml.IMUEventDetectorRepository

import com.example.watchhar.util.loadModelFile
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IMUEventDetectorImpl @Inject constructor(
    @ApplicationContext context: Context
) : IMUEventDetectorRepository {

    companion object {
        private const val TAG = "IMU_EVENT_DETECTOR"
        private const val EVENT_DETECTOR_FILE = "imu_event_detector.tflite"
        private const val EXPECTED_IMU_FLOATS = 150 * 6
        private const val INPUT_BYTES = 1 * 150 * 6 * 4
        private const val OUTPUT_BYTES = 1 * 4
    }
    private var imuTotalTime = 0f
    private var inferenceCount = 0L
    private var interpreter: Interpreter? = null
    private val inputBuffer = ByteBuffer.allocateDirect(INPUT_BYTES).order(ByteOrder.nativeOrder())
    // shares backing store with inputBuffer for fast I/O
    private val inputFloatBuffer = inputBuffer.asFloatBuffer()
    private val outputBuffer = ByteBuffer.allocateDirect(OUTPUT_BYTES).order(ByteOrder.nativeOrder())

    init {
        val options = Interpreter.Options()
        options.setNumThreads(2)
        options.setUseXNNPACK(true)
        interpreter = Interpreter(loadModelFile(context, EVENT_DETECTOR_FILE), options)
    }

    @Synchronized
    override fun run(imuData: FloatArray): Float {
        require(imuData.size == EXPECTED_IMU_FLOATS) {
            "Expected $EXPECTED_IMU_FLOATS IMU floats, got ${imuData.size}"
        }

        if (interpreter == null) {
            Log.e(TAG, "Failed to load model")
        }

        inputBuffer.rewind()
        outputBuffer.rewind()
        inputFloatBuffer.rewind()

        inputFloatBuffer.put(imuData)
        val startTime = System.nanoTime()
        interpreter?.run(inputBuffer, outputBuffer)
        @Suppress("UNUSED_VARIABLE")
        val inferenceTime = (System.nanoTime() - startTime) / 1000000

        outputBuffer.rewind()
        val result = outputBuffer.getFloat(0)

        imuTotalTime += inferenceTime
        inferenceCount += 1

        Log.d(TAG + "AVG Inference Time", "For ${inferenceCount} inference \n Event Detector Inference Time: ${imuTotalTime / inferenceCount} ms")
        Log.d(TAG, "Event Probability: $result")

        return result
    }
}
