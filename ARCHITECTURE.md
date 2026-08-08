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

## CI/CD and Release Artifacts

- Public repository builds do not depend on a checked-in Firebase config.
- `ci.yml` runs on pull requests, pushes to `main`, and manual CI dispatches. It uploads a debug-key-signed development APK named `bitchat-scanner-debug-pr<PR_NUMBER>-<SHORT_SHA>` for pull requests or `bitchat-scanner-debug-main-<SHORT_SHA>` for `main` (7-day PR retention, 30-day `main` retention), plus run-scoped JUnit and lint artifacts.
- Debug artifacts are installable for development/QA but are not release-signed and must not be used for production distribution or update testing. A local release build without signing variables produces an unsigned APK.
- `release.yml` is tag-only (`vX.Y.Z`) or a manual dispatch with the required existing `tag` input. The read-only `validate` job verifies the tag format, exact `versionName`, and ancestry from `main` before the protected `release` environment is used.
- The read-only `build` job checks out with `persist-credentials: false`, uses the protected `release` environment, builds and verifies both a signed APK and a signed AAB, and uploads the Actions artifact `bitchat-scanner-vX.Y.Z-release-signed` for 90 days. The APK is for installation/sideloading; the AAB is for Google Play upload. Release files use owned names: `bitchat-scanner-vX.Y.Z-release-signed.sha256`, `.build-info.json`, `.manifest-audit.txt`, and `.signer-metadata.txt` alongside the unchanged APK/AAB names. The manifest audit is generated from the release APK and therefore audits the same release variant built alongside the AAB. GitHub Release assets include the APK/AAB, checksum, build info, and signer metadata; the manifest audit remains in the Actions artifact.
- The separate `publish` job has `contents: write` but no signing secrets. It downloads the exact build artifact and publishes only the allowlisted APK/AAB and prefixed metadata files to the GitHub Release.
- R8 mapping is kept separately as the Actions-only artifact `bitchat-scanner-vX.Y.Z-r8-mapping` for 90 days. Its visibility follows repository GitHub Actions artifact permissions; it is not a GitHub Release asset.
- Release signing uses the protected `release` environment secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`; missing secrets fail closed. Do not place signing material in artifacts.
- Artifact paths are explicitly allowlisted. Build archives must not include the workspace, `RUNNER_TEMP`, keystores, generated local databases, map caches, or broad `app/build/**` output. APK/AAB packages contain no app-local detection history or location records.
