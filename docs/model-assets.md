# Model Assets

This repository does not include trained model binaries. It contains the Wear OS
runtime code that loads TFLite versions of the WatchHAR-style model stages when
they are supplied under `app/src/main/assets/`.

## Expected Asset Layout

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

## Runtime Roles

| Asset | Runtime role |
|---|---|
| `imu_event_detector.tflite` | Stage-1 IMU event gate |
| `float32/imu_encoder.tflite` | Stage-2 IMU feature encoder |
| `float32/mel_preprocess.tflite` | PCM-to-mel preprocessing |
| `float32/audio_encoder.tflite` | Audio feature encoder |
| `float32/feature_fusion.tflite` | Multimodal activity classifier |
| `float16/*` | Optional smaller precision variant of the same stages |

`ModelConfig.USE_FLOAT16` is currently `false`, so the runtime loads the
`float32/` model set by default.

## Shape Contract Used By The App

- Event detector input: `150 * 6` IMU floats.
- Stage-2 IMU input: `50 * 6` normalized IMU floats.
- Audio input: `950` downsampled PCM samples.
- Mel output: `96 * 64` floats.
- IMU encoder output: `128` floats.
- Audio encoder output: `640` floats.
- Fusion output: model-defined class count.

The app validates key input lengths before invoking the TFLite interpreters.
Model redistribution, checkpoint conversion, and training artifacts are outside
the scope of this portfolio repository.
