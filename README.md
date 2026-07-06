# WearOS Application for WatchHAR Model

WearOS Application for WatchHAR Model is a portfolio snapshot of a Wear OS application built
to run the TFLite model stages that follow the multimodal activity-recognition
pipeline described in the WatchHAR paper. The app focuses on the smartwatch
runtime layer: live IMU collection, event-gated microphone capture, staged
TFLite inference, probability smoothing, and compact on-watch result display.

This repository includes one runnable float32 Drop-8 test model bundle so the
app can be built and launched without adding private checkpoints first. The
bundle is suitable for runtime demonstration and integration testing, not for
claiming production-grade recognition quality. PyTorch checkpoints, raw
datasets, training scripts, and full experiment directories are not included.

## What This App Implements

- Wear OS app shell in Kotlin and Jetpack Compose.
- Runtime permission flow for microphone access.
- Continuous accelerometer/gyroscope sampling with fixed-rate IMU buffering.
- Stage-1 IMU event detection to avoid continuous microphone inference.
- Event-gated audio capture and PCM downsampling for the audio branch.
- TFLite execution pipeline for IMU encoder, mel preprocessing, audio encoder,
  and multimodal feature fusion.

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

The Android package id remains `com.example.watchhar` because this repository is
a runtime/portfolio extraction rather than a published app package.

## Included Test Model Bundle

The included runtime bundle is:

```text
app/src/main/assets/
  imu_event_detector.tflite
  float32/
    imu_encoder.tflite
    mel_preprocess.tflite
    audio_encoder.tflite
    feature_fusion.tflite
```

`feature_fusion.tflite` is the Drop-8 closed-set classifier converted from the
`vessl_drop8_frozen_103_b32` experiment. It predicts 19 labels: 18 target
activities plus `Other`.

Labels: `Brushing hair`, `Chopping`, `Clapping`, `Drill in use`, `Drinking`,
`Grating`, `Hammering`, `Knocking`, `Pouring pitcher`, `Sanding`, `Scratching`,
`Screwing`, `Toothbrushing`, `Twisting jar`, `Vacuum in use`,
`Washing utensils`, `Washing hands`, `Wiping with rag`, and `Other`.

Evaluation snapshot for this test classifier:

- Best validation target macro recall: `0.8407` at epoch 2.
- Primary evaluation, file-majority: accuracy `0.8167`, macro F1 `0.7733`,
  target macro recall `0.7963`.
- Primary evaluation, window-level: accuracy `0.7184`, macro F1 `0.6682`,
  target macro recall `0.6673`.
- Excluded-class challenge: `99.24%` of windows from the eight dropped classes
  were still predicted as target activities, so open-set rejection is weak.

In short, the bundled model is useful for proving that the Wear OS runtime can
execute the WatchHAR-style TFLite pipeline end to end. Its recognition quality is
modest, and the excluded-class behavior is not strong enough for deployment.

## Build And Test

The project can be built with Android Studio or the Gradle wrapper once the
Android SDK is available:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

Device runtime verification additionally requires a Wear OS device with
microphone and high-sampling-rate sensor permissions.

## Repository Contents

- `app/`: Wear OS runtime application and included TFLite test assets.
- `docs/architecture.md`: component map and runtime notes.
- `docs/model-assets.md`: bundled model files, label contract, and evaluation
  context.
- `docs/validation.md`: build checks and compact dataset-audit summary.
- `docs/reports/samosa-dataset-distribution/20260603/`: small local dataset
  distribution audit used as supporting context.
