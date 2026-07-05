# Runtime Model Assets

Actual model binaries are not included in this repository.

To run the app on a Wear OS device, place WatchHAR-compatible TFLite files here:

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

The runtime loads `float32/` by default. Set `ModelConfig.USE_FLOAT16` to `true`
to load the optional `float16/` assets.
