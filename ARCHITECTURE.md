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

- `MainActivity.kt` handles top-level navigation, permission gatekeeping, and visible/hidden lifecycle signals.
- `screens/` contains route-level Compose screens.
- `components/` contains reusable UI pieces such as permission and device cards.
- `viewmodel/` owns UI state and scanner-session coordination.
- The scanner coordinator owns the active callback and Android 8.0+ system-managed filtered `PendingIntent` registration.
- A non-exported scan-result receiver accepts only the app's explicit `PendingIntent` deliveries while the user-started session is active.
- `repository/` reads and writes detection data.
- `utils/` groups map, permission, and RSSI helpers.
- `constants/` keeps rendering and map-related constants in one place.

## Detection Flow

1. The user taps **Start Scanner** while `MainActivity` is visible.
2. The scanner uses an active BLE callback while the Activity is visible.
3. On Android 8.0+ (API 26+), hiding the Activity transitions the started session to a filtered system-managed `PendingIntent`; the explicit receiver is non-exported and validates the app-scoped mutable intent.
4. Matching BitChat devices are converted into `DetectionRow` records.
5. Visible callback detections may receive a best-effort current coordinate when `ACCESS_FINE_LOCATION` is granted. Hidden `PendingIntent` detections never request a fresh coordinate.
6. `DetectionDbHelper` stores detections and updates device summaries.
7. `ScannerRepository` exposes logs, devices, and location history to the UI.
8. `MainViewModel` and `DevicesViewModel` refresh Compose state.

## Scanning Lifecycle

- Scanning starts only after the user explicitly requests it and the required permissions are granted.
- On Android 8.0–11 (API 26–30), coarse and fine location are required for BLE scan results. On Android 12+ (API 31+), `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` are core scanner permissions; location is optional, and `ACCESS_FINE_LOCATION` enables visible-session coordinate tagging.
- A visible session uses the active callback. When that session is hidden on Android 8.0+, the coordinator registers the filtered system-managed `PendingIntent`; it must not create an Android service or request background location.
- Hidden delivery is best-effort, throttled, and non-continuous. Android 8.0+ may delay, batch, or omit results; the app must not promise guaranteed background detection.
- Some vendor stacks reject the zero-length service-data presence filter. Its fallback retains service-UUID and solicitation filters, so service-data-only advertisements can be missed in hidden mode even though the visible callback matcher accepts them.
- The scan `PendingIntent` uses `FLAG_MUTABLE | FLAG_UPDATE_CURRENT` so Bluetooth can add result extras. Its explicit package/component and non-exported receiver constrain delivery; notification content `PendingIntent`s targeting `MainActivity` remain immutable. The receiver stops accepting scan results after **Stop Scanner**.
- Background detections are stored without fresh coordinates. They remain in history but are omitted from coordinate-based map rendering.
- Optional detection notifications are user-enabled, contain the matching device name/address and RSSI (not a fresh coordinate), and are cooldown-limited; Android 13+ requires `POST_NOTIFICATIONS`.
- The local SQLite history is independent of the scanner session. Stopping the scanner preserves all previously stored detections and device summaries.

## Map Rendering

- `MapUtils.filterLocationsByMinRadius()` removes duplicate coordinate entries.
- `MapUtils.calculateExpandedBoundingBox()` computes a stable viewport.
- `MapUtils.createDetectionCircle()` converts a detection into a map polygon.
- `RssiUtils` maps RSSI values to radius and colors.
- Detections without a coordinate remain in history but are omitted from coordinate-based map rendering.
- Map tile requests go to the configured osmdroid/OpenStreetMap tile provider; detection records stay in the app-local database, are not sent to an app backend, and are excluded from app backup.

## Release Notes

- Public repository builds do not depend on a checked-in Firebase config.
- Release signing is driven by environment variables in CI.
- GitHub Actions can publish signed APK builds from `main`.
