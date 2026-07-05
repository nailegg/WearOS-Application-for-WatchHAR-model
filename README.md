# WatchHAR Wear OS Runtime

WatchHAR Wear OS Runtime is a portfolio snapshot of a Wear OS application built
to run the TFLite version of the multimodal activity-recognition model described
by the WatchHAR paper. The app focuses on the on-device runtime: live IMU
collection, event-gated microphone capture, TFLite inference orchestration, and
compact result display on a smartwatch.

This repository does not include the trained model binaries. The source expects
WatchHAR-compatible TFLite assets to be supplied under `app/src/main/assets/`
before running the app on a device. The expected asset layout is documented in
`app/src/main/assets/README.md` and `docs/model-assets.md`.

## What This App Implements

- Wear OS app shell in Kotlin and Jetpack Compose.
- Runtime permission flow for microphone access.
- Continuous accelerometer/gyroscope sampling with fixed-rate IMU buffering.
- Stage-1 IMU event detection to avoid continuous microphone inference.
- Event-gated audio capture and PCM downsampling for the audio branch.
- TFLite execution pipeline for IMU encoder, mel preprocessing, audio encoder,
  and multimodal feature fusion.
- EMA smoothing of activity probabilities before updating the UI.
- Small unit tests for pure Kotlin utility behavior used by the streaming path.

## Runtime Pipeline

1. `SensorRepositoryImpl` samples watch IMU streams and maintains rolling
   windows at the model's expected rate.
2. `IMUEventDetectorImpl` runs the event-detector TFLite model on IMU windows.
3. `AudioRepositoryImpl` records microphone audio only while an event is active,
   downsamples PCM audio, and publishes ready audio windows.
4. `MultimodalClassifierImpl` feeds the IMU and audio windows through the
   WatchHAR-style TFLite stages:
   `imu_encoder`, `mel_preprocess`, `audio_encoder`, and `feature_fusion`.
5. `MainViewModel` smooths the classifier probabilities and exposes the current
   activity result to `MainScreen`.

The app currently keeps the Android package id as `com.example.watchhar` because
this repository is a runtime/portfolio extraction rather than a published app
package.

## Required Model Assets

The repository intentionally excludes actual `.tflite`, `.pth`, checkpoint, and
raw dataset files. To run the app on a watch, provide compatible TFLite files
with this structure:

```text
app/src/main/assets/
  imu_event_detector.tflite
  float32/
    imu_encoder.tflite
    mel_preprocess.tflite
    audio_encoder.tflite
    feature_fusion.tflite
  float16/
    imu_encoder.tflite
    mel_preprocess.tflite
    audio_encoder.tflite
    feature_fusion.tflite
```

`ModelConfig.USE_FLOAT16` controls whether the runtime loads `float32/` or
`float16/`. The default is `float32`.

## Build And Test

The project can be built with Android Studio or the Gradle wrapper once the
Android SDK is available:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

These commands validate the Android project structure and unit tests. Device
runtime verification additionally requires the TFLite assets above and a Wear OS
device with microphone and high-sampling-rate sensor permissions.

## Repository Contents

- `app/`: Wear OS runtime application.
- `docs/architecture.md`: component map and runtime notes.
- `docs/model-assets.md`: expected model files and shape-level contract.
- `docs/validation.md`: build checks and compact dataset-audit summary.
- `docs/reports/samosa-dataset-distribution/20260603/`: small local dataset
  distribution audit used as supporting context.

The larger internship working directory contained training scripts, raw data,
experiment checkpoints, and evaluation runs. Those artifacts are not part of
this portfolio snapshot; this repository is scoped to the smartwatch runtime
implementation.
