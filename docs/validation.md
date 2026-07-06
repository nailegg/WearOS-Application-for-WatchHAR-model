# Validation

This repository keeps validation lightweight and tied to the app snapshot.

## Local Build Checks

Run these before publishing or submitting the repository:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

The unit tests cover pure Kotlin utility behavior that matters to the streaming
pipeline: ring-buffer wraparound, moving averages, short audio buffer reads, and
activity-label mapping.

## Bundled Test Model Check

The repository includes a float32 Drop-8 TFLite test bundle so the app can be
assembled and launched without adding separate model files. The classifier is
useful for runtime validation, but the source experiment reports only modest
recognition quality:

- File-majority accuracy: `0.8167`.
- Window-level accuracy: `0.7184`.
- Window-level macro F1: `0.6682`.
- Excluded-class windows predicted as target activities: `0.9924`.

The last value means the bundled model should not be treated as a robust
open-set classifier. It verifies integration of the TFLite runtime path, not
deployment-grade activity recognition.

## Dataset Distribution Audit

The included SAMoSA distribution report is stored at
`docs/reports/samosa-dataset-distribution/20260603/`.

Summary:

- Raw files: 1,633.
- Preprocessed files: 1,633.
- Participants: 20.
- Labels: 27, including `Other`.
- Preprocessed windows: 225,673 total.
- Raw non-Other audio duration: 5.8163 hours.
- Raw Other audio duration: 0.6556 hours.
- Complete non-Other protocol expectation: 1,560 trials.
- Local non-Other files: 1,516, with 44 missing trials versus the complete
  protocol expectation.

This audit supports data-understanding claims, not model accuracy claims. Model
quality should be reported separately with a fixed protocol, device conditions,
and held-out evaluation split.

## Known Limitations

- The source snapshot does not include the original training workspace.
- The bundled Drop-8 model is a test asset with weak excluded-class rejection.
- Live wearable performance depends on device microphone source behavior,
  sensor sampling consistency, and environmental audio conditions.
