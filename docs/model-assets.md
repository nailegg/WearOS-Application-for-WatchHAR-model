# Model Assets

This repository includes one float32 TFLite test bundle for the Wear OS runtime.
It is intended to make the portfolio app immediately runnable and to demonstrate
the WatchHAR-style staged inference pipeline on device.

The repository does not include PyTorch `.pth` checkpoints, `.pt` files, raw
datasets, training scripts, or full experiment output directories.

## Included Asset Layout

```text
app/src/main/assets/
  imu_event_detector.tflite
  float32/
    imu_encoder.tflite
    mel_preprocess.tflite
    audio_encoder.tflite
    feature_fusion.tflite
```

`ModelConfig.USE_FLOAT16` is currently `false`, so the runtime loads the
`float32/` model set. No float16 assets are bundled in this portfolio snapshot.

## Runtime Roles

| Asset | Runtime role |
|---|---|
| `imu_event_detector.tflite` | Stage-1 IMU event gate used before audio capture |
| `float32/imu_encoder.tflite` | Encodes normalized IMU windows into a 128-float feature |
| `float32/mel_preprocess.tflite` | Converts downsampled PCM audio into a mel feature map |
| `float32/audio_encoder.tflite` | Encodes mel features into a 640-float audio feature |
| `float32/feature_fusion.tflite` | Drop-8 19-class multimodal activity classifier |

The fusion model was copied from the Drop-8 experiment run
`vessl_drop8_frozen_103_b32` as `feature_fusion_float32.tflite` and renamed to
`feature_fusion.tflite` because that is the filename the Android runtime loads.

## Shape Contract Used By The App

- Event detector input: `150 * 6` IMU floats.
- Stage-2 IMU input: `50 * 6` normalized IMU floats.
- Audio input: `950` downsampled PCM samples.
- Mel output: `96 * 64` floats.
- IMU encoder output: `128` floats.
- Audio encoder output: `640` floats.
- Fusion output: model-defined class count, `19` for the bundled Drop-8 model.

The app validates key input lengths before invoking the TFLite interpreters and
uses the fusion output shape to select the matching activity-label map.

## Bundled Classifier Context

The included Drop-8 classifier is a closed-set test model. Its 19 labels are:

```text
0  Brushing hair
1  Chopping
2  Clapping
3  Drill in use
4  Drinking
5  Grating
6  Hammering
7  Knocking
8  Pouring pitcher
9  Sanding
10 Scratching
11 Screwing
12 Toothbrushing
13 Twisting jar
14 Vacuum in use
15 Washing utensils
16 Washing hands
17 Wiping with rag
18 Other
```

The eight classes removed from the classifier target set were `Alarm clock`,
`Blender in use`, `Coughing`, `Hair dryer in use`, `Laughing`, `Microwave`,
`Shaver in use`, and `Toilet flushing`.

Performance snapshot from the source experiment:

| Metric group | Value |
|---|---:|
| Best validation target macro recall | `0.8407` |
| File-majority accuracy | `0.8167` |
| File-majority macro F1 | `0.7733` |
| File-majority target macro recall | `0.7963` |
| Window-level accuracy | `0.7184` |
| Window-level macro F1 | `0.6682` |
| Window-level target macro recall | `0.6673` |
| Excluded-class windows predicted as target | `0.9924` |

These numbers are good enough for a runnable portfolio demo but not high enough
to present the model as deployment-ready. The excluded-class result especially
shows weak open-set rejection: sounds from dropped classes are usually forced
into one of the target activity labels instead of `Other`.
