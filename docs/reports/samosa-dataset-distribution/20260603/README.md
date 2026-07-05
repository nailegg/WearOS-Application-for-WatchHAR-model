# SAMoSA Dataset Distribution Audit

Generated: 2026-06-03

This report summarizes a local audit of the SAMoSA raw files and preprocessed
windows used during WatchHAR app validation. Raw data is not included in this
repository.

## Summary

| Measure | Value |
|---|---:|
| Raw files | 1,633 |
| Preprocessed files | 1,633 |
| Same filename set | True |
| Participants | 20 |
| Labels | 27 |
| Raw non-Other files | 1,516 |
| Raw Other files | 117 |
| Preprocessed non-Other windows | 202,439 |
| Preprocessed Other windows | 23,234 |
| Preprocessed total windows | 225,673 |

## Durations

| Duration measure | Hours |
|---|---:|
| Audio total | 6.4720 |
| Audio non-Other | 5.8163 |
| Audio Other | 0.6556 |
| IMU total at 50 Hz | 6.6952 |

## Completeness Check

A complete non-Other activity protocol would imply:

```text
20 participants * 26 activities * 3 trials = 1,560 trials
```

The local raw dataset contains 1,516 non-Other files, leaving 44 missing trials
versus the complete protocol expectation. The missing trial list is stored in
`missing_activity_trials.csv`.

## Data Quality Notes

- 106 non-Other raw files are shorter than 5 seconds.
- Extreme short examples are listed in
  `short_non_other_files_under_5s.csv`.
- Local preprocessed log-mel windows have shape `(96, 64)`.
- Local preprocessed IMU windows have shape `(50, 9)`.

## Included Files

- `summary.json`
- `raw_file_counts_by_label.csv`
- `preprocessed_window_counts_by_label.csv`
- `raw_file_counts_by_participant_label.csv`
- `preprocessed_window_counts_by_participant_label.csv`
- `missing_activity_trials.csv`
- `raw_file_inventory.csv`
- `short_non_other_files_under_5s.csv`
