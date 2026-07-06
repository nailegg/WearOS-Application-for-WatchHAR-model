# Runtime Model Assets

This directory includes the float32 TFLite test bundle loaded by the Wear OS
runtime:

```text
app/src/main/assets/
  imu_event_detector.tflite
  float32/
    imu_encoder.tflite
    mel_preprocess.tflite
    audio_encoder.tflite
    feature_fusion.tflite
```

The bundled `feature_fusion.tflite` is a Drop-8 19-class closed-set test
classifier. It is included so the app can run immediately, but its recognition
quality is modest and its excluded-class rejection is weak. See
`docs/model-assets.md` for the label set and evaluation snapshot.

`ModelConfig.USE_FLOAT16` is `false` by default. This snapshot does not include
float16 model files or PyTorch checkpoints.
