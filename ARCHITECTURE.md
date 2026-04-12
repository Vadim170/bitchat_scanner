# BitChat Scanner Architecture

## Project Structure

The app is split into a few focused areas:

```text
com.vadim170.bitchatscanner/
├── MainActivity.kt
├── screens/
├── components/
├── utils/
├── constants/
├── viewmodel/
└── repository/
```

## Main Responsibilities

- `MainActivity.kt` handles top-level navigation and permission gatekeeping.
- `screens/` contains route-level Compose screens.
- `components/` contains reusable UI pieces such as permission and device cards.
- `viewmodel/` owns UI state and service coordination.
- `repository/` reads and writes detection data.
- `utils/` groups map, permission, and RSSI helpers.
- `constants/` keeps rendering and map-related constants in one place.

## Detection Flow

1. `BleScannerService` listens for BLE scan results.
2. Matching BitChat devices are converted into `DetectionRow` records.
3. `DetectionDbHelper` stores detections and updates device summaries.
4. `ScannerRepository` exposes logs, devices, and location history to the UI.
5. `MainViewModel` and `DevicesViewModel` refresh Compose state.

## Map Rendering

- `MapUtils.filterLocationsByMinRadius()` removes duplicate coordinate entries.
- `MapUtils.calculateExpandedBoundingBox()` computes a stable viewport.
- `MapUtils.createDetectionCircle()` converts a detection into a map polygon.
- `RssiUtils` maps RSSI values to radius and colors.

## Release Notes

- Public repository builds do not depend on a checked-in Firebase config.
- Release signing is driven by environment variables in CI.
- GitHub Actions can publish signed APK builds from `main`.
