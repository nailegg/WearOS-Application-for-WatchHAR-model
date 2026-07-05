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
- The bundled model assets need an explicit redistribution decision before a
  public release.
- Live wearable performance depends on device microphone source behavior,
  sensor sampling consistency, and environmental audio conditions.
