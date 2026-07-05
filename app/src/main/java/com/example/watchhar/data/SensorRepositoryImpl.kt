package com.example.watchhar.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.example.watchhar.domain.ml.IMUEventDetectorRepository
import com.example.watchhar.domain.repository.SensorRepository
import com.example.watchhar.util.FloatRingBuffer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SensorRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val eventDetector: IMUEventDetectorRepository
) : SensorRepository, SensorEventListener {

    companion object {
        private const val TAG = "SensorRepository"
        private const val CHANNELS = 6
        private const val SAMPLE_RATE = 50                       // Hz — resampled output rate
        private const val TICK_PERIOD_MS = 1000L / SAMPLE_RATE   // 20 ms
        // Two-threshold hysteresis (R4): once the event fires (avg ≥ THRESHOLD_HIGH),
        // it stays active until the moving average drops below THRESHOLD_LOW.
        // Prevents rapid on/off toggling when the average sits near a single threshold
        // (which restarts the audio buffer on each toggle, wasting the 950 ms warm-up).
        private const val THRESHOLD_HIGH = 0.8f
        private const val THRESHOLD_LOW  = 0.6f
        // Stage 2 IMU encoder normalization contract.
        // Matches the offline training pipeline's IMU normalization parameters.
        // Do not apply this transform to imu_event_detector.tflite.
        private val IMU_NORM_MAX  = floatArrayOf( 5.679449f, -2.147220f,  7.097475f,  0.524282f,  0.322827f,  0.323170f)
        private val IMU_NORM_MIN  = floatArrayOf(-5.338194f, -8.850697f, -1.813361f, -0.526405f, -0.307250f, -0.342115f)
        private val IMU_NORM_MEAN = floatArrayOf( 0.425053f, -5.287797f,  2.476576f, -0.017834f, -0.004427f, -0.020158f)
        private val IMU_NORM_STD  = floatArrayOf( 5.883963f,  4.776786f,  5.469956f,  1.504210f,  0.813269f,  0.856229f)
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val _isEventDetected = MutableStateFlow(false)
    override val isEventDetected = _isEventDetected.asStateFlow()

    // ── Latest raw sensor readings ────────────────────────────────────────────
    // Written by the sensor-callback thread; read by the ticker thread.
    // AtomicReference gives cross-thread visibility of the FloatArray reference
    // without a lock — each set() publishes a freshly cloned array, so the
    // ticker always sees a complete, immutable snapshot.
    private val latestAcc  = AtomicReference<FloatArray?>(null)
    private val latestGyro = AtomicReference<FloatArray?>(null)

    // ── Buffers owned by the ticker thread ───────────────────────────────────
    // 150 frames × 6 channels = 900 floats.  At 50 Hz → 3 s of rolling history.
    // getLast1sSensorData() extracts the final 50 frames = 1 000 ms.
    //
    // NOTE: if imu_event_detector.tflite was trained on a 1.5-second window at
    // 100 Hz (150 frames × 10 ms), its effective receptive field is now 3 seconds
    // (150 frames × 20 ms) after this fix.  Verify the model's training window
    // and resize sensorBuffer / detectionScratch accordingly if needed.
    private val sensorBuffer    = FloatRingBuffer(3 * CHANNELS * SAMPLE_RATE)
    private val movingAvgWindow = FloatRingBuffer(2 * SAMPLE_RATE)
    private val detectionScratch = FloatArray(3 * CHANNELS * SAMPLE_RATE)
    private val sensorLock = Any()

    // ── Ticker infrastructure ─────────────────────────────────────────────────
    // Created fresh on each startMonitoring() call so the object can be
    // started/stopped cleanly across the ViewModel lifecycle.
    @Volatile private var tickerHandler: Handler? = null
    private var tickerThread: HandlerThread? = null

    // Absolute time target for the next tick, tracked on the ticker thread.
    // Using postAtTime() (absolute deadline) rather than postDelayed() (relative
    // delay) prevents cumulative drift: a late tick reschedules to
    // nextTickMs + TICK_PERIOD_MS, not to "now + TICK_PERIOD_MS".
    private var nextTickMs = 0L

    // Self-rescheduling Runnable.  Using an object expression lets 'this' inside
    // run() refer to the Runnable itself, avoiding a captured-variable cycle.
    private val tickRunnable: Runnable = object : Runnable {
        override fun run() {
            onTick()
            nextTickMs += TICK_PERIOD_MS
            // tickerHandler is @Volatile; if stopMonitoring() set it to null
            // between onTick() returning and this line, the ?. makes it a no-op.
            // The looper's MessageQueue also rejects posts after quitSafely().
            tickerHandler?.postAtTime(this, nextTickMs)
        }
    }

    // ── Inference infrastructure (B5 fix) ────────────────────────────────────
    // A dedicated thread receives inference work from the ticker.
    // THREAD_PRIORITY_BACKGROUND keeps it below the ticker so buffer writes
    // are never delayed waiting for the inference thread to be scheduled.
    //
    // Conflation pattern: the ticker calls removeCallbacksAndMessages(null)
    // before each post, so at most one snapshot is ever pending in the queue.
    // A currently-running inference is not interrupted; only the NEXT queued job
    // is replaced by the freshest snapshot.  This decouples sensorBuffer write
    // rate (strict 50 Hz, bounded by ticker) from inference rate (bounded by
    // model speed), and guarantees inference always operates on the latest data.
    @Volatile private var inferenceHandler: Handler? = null
    private var inferenceThread: HandlerThread? = null
    @Volatile private var isMonitoring = false

    // ── Public API ────────────────────────────────────────────────────────────

    override fun startMonitoring() {
        if (isMonitoring) return

        synchronized(sensorLock) {
            resetStateLocked()
        }
        latestAcc.set(null)
        latestGyro.set(null)
        _isEventDetected.value = false

        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyro  = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        // SENSOR_DELAY_GAME is a hint; actual rate is hardware-dependent (~100 Hz
        // on Galaxy Watch).  The ticker resamples to an exact 50 Hz regardless.
        sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(this, gyro,  SensorManager.SENSOR_DELAY_GAME)

        // Start inference thread first so it is ready before the first tick fires.
        val infThread = HandlerThread("IMU-Inference", Process.THREAD_PRIORITY_BACKGROUND)
        infThread.start()
        inferenceThread = infThread
        inferenceHandler = Handler(infThread.looper)

        val tickThread = HandlerThread("IMU-Ticker", Process.THREAD_PRIORITY_AUDIO)
        tickThread.start()
        tickerThread = tickThread

        val handler = Handler(tickThread.looper)
        tickerHandler = handler

        nextTickMs = SystemClock.uptimeMillis() + TICK_PERIOD_MS
        handler.postAtTime(tickRunnable, nextTickMs)
        isMonitoring = true
    }

    // Pure write — no inference, no pairing queues, no blocking.
    // The ticker owns all buffer-management and inference logic.
    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> latestAcc.set(event.values.clone())
            Sensor.TYPE_GYROSCOPE           -> latestGyro.set(event.values.clone())
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // Returns the last 50 frames (1 000 ms at 50 Hz), normalized for the
    // Stage 2 IMU encoder. imu_encoder.tflite expects shape (1, 50, 6) — 300
    // floats, row-major.
    override fun getLast1sSensorData(): FloatArray {
        synchronized(sensorLock) {
            val snapshot = sensorBuffer.getLastData(50 * CHANNELS)
            normalizeForImuEncoder(snapshot)
            return snapshot
        }
    }

    override fun stopMonitoring() {
        if (!isMonitoring) return

        // Stop the ticker first so no new inference jobs are posted.
        tickerHandler?.removeCallbacks(tickRunnable)
        tickerHandler = null           // @Volatile — visible to ticker thread immediately
        tickerThread?.quitSafely()
        tickerThread = null

        // Stop the inference thread. removeCallbacksAndMessages(null) drops any
        // pending job that hasn't started yet; a currently-running inference will
        // complete normally before the looper exits.
        inferenceHandler?.removeCallbacksAndMessages(null)
        inferenceHandler = null        // @Volatile — seen by any stale ticker reference
        inferenceThread?.quitSafely()
        inferenceThread = null

        synchronized(sensorLock) {
            sensorManager.unregisterListener(this)
            resetStateLocked()
        }

        latestAcc.set(null)
        latestGyro.set(null)
        _isEventDetected.value = false
        isMonitoring = false
        Log.d(TAG, "Monitoring stopped and flags reset.")
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /**
     * Called every 20 ms on the IMU-Ticker HandlerThread.
     *
     * Responsibilities are now split into two phases:
     *
     * Phase 1 — buffer write (this thread, ~5 µs):
     *   Snapshot the latest acc/gyro pair into sensorBuffer under sensorLock.
     *   This always completes in < 10 µs regardless of model speed, so the
     *   ticker fires at a true 50 Hz.
     *
     * Phase 2 — inference (IMU-Inference thread, 10–30 ms):
     *   The raw snapshot is posted to the inference handler for Stage 1 event
     *   detection. Stage 2 IMU encoder normalization happens later when the
     *   classifier requests the last 1-second sensor window.
     *   removeCallbacksAndMessages(null) drops any pending (not yet started)
     *   job before posting the fresh one — last-value conflation.  If the
     *   inference thread is busy, intermediate snapshots are dropped and it
     *   picks up the most recent one when it finishes.
     */
    private fun onTick() {
        val acc  = latestAcc.get()  ?: return   // skip until first event arrives
        val gyro = latestGyro.get() ?: return

        // Phase 1: brief critical section — buffer write + window snapshot only.
        val inferenceSnapshot: FloatArray
        synchronized(sensorLock) {
            sensorBuffer.write(acc)
            sensorBuffer.write(gyro)
            sensorBuffer.copyLastDataTo(detectionScratch)
            inferenceSnapshot = detectionScratch.copyOf()
        }

        val iHandler = inferenceHandler ?: return   // stopMonitoring() was called
        iHandler.removeCallbacksAndMessages(null)   // conflate: drop stale pending job
        iHandler.post {
            val prob = eventDetector.run(inferenceSnapshot)
            synchronized(sensorLock) {
                movingAvgWindow.write(prob)
                val avg = movingAvgWindow.getAvg()
                val current = _isEventDetected.value
                _isEventDetected.value = when {
                    avg >= THRESHOLD_HIGH -> true
                    avg <  THRESHOLD_LOW  -> false
                    else                  -> current  // hysteresis: hold previous state
                }
            }
        }
    }

    private fun resetStateLocked() {
        sensorBuffer.clear()
        movingAvgWindow.clear()
        detectionScratch.fill(0f)
    }

    private fun normalizeForImuEncoder(buffer: FloatArray) {
        for (i in buffer.indices) {
            val ch = i % CHANNELS
            val step1 = 1f + (buffer[i] - IMU_NORM_MAX[ch]) * 2f / (IMU_NORM_MAX[ch] - IMU_NORM_MIN[ch])
            buffer[i] = (step1 - IMU_NORM_MEAN[ch]) / IMU_NORM_STD[ch]
        }
    }
}
