# Copilot Instructions for BitChat Scanner

## Project Overview
This is a specialized Android 8.0+ app that scans for BitChat network nodes using Bluetooth Low Energy (BLE). It uses an active callback while its Activity is visible and a system-managed filtered `PendingIntent` when an explicitly started session continues while hidden. The app creates a coverage map from visible-session coordinates and keeps background detections without fresh coordinates.

## Architecture Overview

### Core Components
- **MainActivity**: Jetpack Compose UI with permission handling and BLE scan controls
- **BleScanCoordinator**: Switches between the visible active callback and Android 8.0+ filtered `PendingIntent` delivery
- **Scan result receiver**: Explicit, non-exported receiver for system-managed hidden delivery
- **DetectionStore**: SQLite database abstraction for storing scan results (max 1000 records)
- **Constants**: BitChat-specific UUIDs and service identifiers

### Data Flow
1. The user explicitly starts a scan while the visible Activity has the required permissions; visible results use the active callback for service UUID `f47b5e2d-4a9e-4c5a-9b3f-8e1d2c3a4b5c`
2. When the started session is hidden on Android 8.0+, the coordinator uses a filtered, system-managed mutable `PendingIntent` delivered only to the non-exported app receiver
3. Matches are found via service UUIDs, service data keys, or service solicitation UUIDs
4. Each detection is stored with timestamp, device info, RSSI, and service data; only visible callback detections may receive a best-effort current coordinate
5. Hidden delivery is best-effort/throttled and may be delayed, batched, or omitted; it is not continuous background monitoring
6. Scanner state and detection events are delivered through the in-process UI state/event path; optional cooldown-limited notifications show device name/address and RSSI and require `POST_NOTIFICATIONS` on Android 13+
7. Database automatically pruned to maintain 1000 most recent detections
8. The scanner stops when the user taps Stop; persisted history remains available afterward

## Development Patterns

### Permission Handling
Uses version-aware permission requests; `PermissionUtils.requiredPermissionsFor(sdkInt)` is the single, unit-tested source of the matrix:
- **Every version**: `ACCESS_FINE_LOCATION` is required (requested together with `ACCESS_COARSE_LOCATION` so Android 12+ offers the precise option). The manifest declares `BLUETOOTH_SCAN` without `neverForLocation`, so Android 12+ only delivers scan results while precise location is granted and Location Services are on; on Android 8.0–11 the location grant is the classic BLE scan requirement.
- **Android 12+**: `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` are requested in addition
- **Android 13+**: `POST_NOTIFICATIONS` is optional and only needed for detection notifications

```kotlin
// Precise location is never optional; Android 12+ adds the Bluetooth runtime pair.
val required = PermissionUtils.requiredPermissionsFor(Build.VERSION.SDK_INT)
// API 26-30: [ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION]
// API 31+:   [BLUETOOTH_SCAN, BLUETOOTH_CONNECT, ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION]
```

Do not re-introduce an "optional location" path or `neverForLocation` without changing the CI manifest audit, `ManifestContractTest`, and the docs together.

### Scanner and Activity Lifecycle
- Start scanning only from a visible Activity after the required permissions are granted.
- Use the active callback while the Activity is visible.
- On Android 8.0+, a started hidden session uses a filtered system-managed `PendingIntent` delivered to an explicit non-exported receiver.
- Make the scan `PendingIntent` mutable with `FLAG_MUTABLE | FLAG_UPDATE_CURRENT` because Bluetooth adds result extras; make it explicit to this package/component and reject deliveries after Stop Scanner.
- Keep notification content `PendingIntent`s targeting `MainActivity` immutable (`FLAG_IMMUTABLE`).
- Treat hidden delivery as best-effort/throttled and non-continuous; never promise guaranteed background detection.
- Both registrations use the shared filter list from `buildScanFilters()`. If a vendor rejects the zero-length service-data presence filter, retain service-UUID/solicitation filters and disclose that service-data-only advertisements may be missed. Only the visible session may continue unfiltered when even the fallback is rejected; the `PendingIntent` path reports an error instead.
- Treat Location Services off as the distinct `LOCATION_DISABLED` state; never show an "active" scan that cannot receive results.
- Do not add a foreground service or `ACCESS_BACKGROUND_LOCATION`; background detections must not request fresh coordinates.
- Keep persisted detection history separate from the scanner session so stopping a scan never clears prior records.

### Compose UI State Management
- `rememberSaveable` for configuration persistence
- `mutableStateListOf` for real-time log updates
- `LaunchedEffect` for database initialization
- `MainActivity` lifecycle callbacks to switch callback/PendingIntent modes when visibility changes
- Explicit receiver events feed the same in-process state/event path as visible callback results

### Database Pattern
SQLite with automatic pruning using transactions:
```kotlin
fun insertAndPrune(row: DetectionRow) {
    writableDatabase.beginTransaction()
    try {
        // Insert new record
        // Delete excess records beyond MAX_ROWS
        setTransactionSuccessful()
    } finally { endTransaction() }
}
```

## Build & Development

### Key Build Configuration
- **Compile SDK**: 36 (latest)
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 36
- **Kotlin**: 2.0.21 with Compose plugin
- **Compose BOM**: 2024.09.00

### Version Catalog Usage
Dependencies managed via `gradle/libs.versions.toml` with type-safe accessors:
```gradle
implementation libs.androidx.compose.material3
implementation platform(libs.androidx.compose.bom)
```

### Testing
- Unit tests: `app/src/test/` (JUnit)
- Instrumented tests: `app/src/androidTest/` (AndroidJUnit + Espresso)

### CI/CD and Artifact Handling
- `.github/workflows/ci.yml` runs on pull requests, pushes to `main`, and manual CI dispatches. It runs unit tests, Android-test Kotlin compilation, lint, the debug build, and the manifest contract audit.
- Pull-request debug artifacts are named `bitchat-scanner-debug-pr<PR_NUMBER>-<SHORT_SHA>` and retained for 7 days. `main` debug artifacts are named `bitchat-scanner-debug-main-<SHORT_SHA>` and retained for 30 days. They contain a debug-key-signed development APK, `output-metadata.json`, `checksums.txt`, `build-info.json`, and `manifest-audit.txt`; they are not release-signed and must not be used for production distribution or update testing.
- JUnit and lint artifacts are named `bitchat-scanner-junit-<RUN_ID>` and `bitchat-scanner-lint-<RUN_ID>`, with 7-day PR retention and 30-day retention for other CI runs.
- `.github/workflows/release.yml` publishes only for an existing `vX.Y.Z` tag (or a manual dispatch whose required `tag` input names that tag). Its read-only `validate` job checks ancestry from `main` and exact `versionName` before the protected `release` environment is used.
- The read-only `build` job checks out with `persist-credentials: false`, receives the protected signing environment, and produces the Actions artifact `bitchat-scanner-vX.Y.Z-release-signed` for 90 days. It contains unchanged signed APK/AAB names plus owned-name metadata files `bitchat-scanner-vX.Y.Z-release-signed.sha256`, `.build-info.json`, `.manifest-audit.txt`, and `.signer-metadata.txt`. The APK is installable/sideloadable, while the AAB is for Google Play and is not directly installable. The manifest audit is generated from the same release APK variant built alongside the AAB. The separate `publish` job has `contents: write`, no signing secrets, downloads the exact build artifact, and publishes only the signed APK/AAB and prefixed checksum/build-info/signer metadata as GitHub Release assets.
- R8 mapping is uploaded separately as the Actions-only artifact `bitchat-scanner-vX.Y.Z-r8-mapping` for 90 days. Its visibility follows repository GitHub Actions artifact permissions; never attach it to a public release.
- Release signing requires the protected `release` environment secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. Never expose those secrets to PR/main or publish jobs or package them in artifacts. Missing secrets must fail the build rather than produce an unsigned release artifact.
- Download CI outputs from the workflow run's **Artifacts** section, not from the repository workspace. Upload only the workflow's explicit APK/AAB, metadata, checksums, selected reports, and mapping paths; never glob `app/build/**`, `RUNNER_TEMP`, keystores, local databases, map caches, or location data.
- A local `assembleRelease` without the signing environment produces an unsigned APK. `assembleDebug` is debug-key signed and suitable only for development/QA.

## Location and Notification Integration

Precise location is required on every supported version: Android 8.0–11 need it for BLE scan results, and Android 12+ only delivers results to an app whose `BLUETOOTH_SCAN` is not marked `neverForLocation` while `ACCESS_FINE_LOCATION` is granted and Location Services are on. Best-effort coordinate tagging uses a last-known location only from the visible active-callback path. Hidden `PendingIntent` detections never request a fresh coordinate and remain in local history without map coordinates. The app does not request `ACCESS_BACKGROUND_LOCATION`.

Detection notifications are optional. On Android 13+, displaying them requires the user to grant `POST_NOTIFICATIONS`; scanning and local history do not depend on that grant.

## Development Commands

### Build & Run
```bash
./gradlew assembleDebug          # Build debug APK
./gradlew installDebug          # Install to connected device
./gradlew test                  # Run unit tests
./gradlew connectedAndroidTest  # Run instrumented tests
```

### Debug BLE Issues
- Check device Bluetooth status and scanner state in app logs
- Monitor visible callback and hidden receiver results through the in-process UI state/event path
- Verify the hidden scan `PendingIntent` is explicit, mutable with `FLAG_MUTABLE | FLAG_UPDATE_CURRENT`, non-exported, and filtered to the BitChat service UUID
- Expect hidden results to be throttled, delayed, batched, or absent on some devices
- Verify BitChat service UUID detection logic
- Use Android Studio's Device Monitor for BLE debugging

## Code Conventions

### Resource Naming
- String resources: `app_name` (snake_case)
- Theme: `Theme.BitchatScanner` (PascalCase)
- Colors: Material3 dynamic theming preferred

### Scanner Patterns
- Always check permissions before BLE operations.
- Keep active callback and hidden `PendingIntent` registration idempotent across lifecycle changes.
- Validate every hidden receiver intent and reject untrusted or stale deliveries.
- Never request a fresh location from hidden receiver handling.
- Do not perform BLE or location work from a foreground/background service.
- Use `@RequiresPermission` annotations for clarity.

### Database Queries
- Use parameterized queries for security
- Implement cursor resource management with `use`
- Maintain indexes on timestamp columns for performance

## BitChat-Specific Logic

### Service Detection
Devices identified by multiple criteria:
1. Service UUID in advertised service list
2. Service data keys containing BitChat UUID
3. Service solicitation UUIDs containing BitChat UUID

### Data Format
Service data stored as hex strings, truncated to 16 characters for display with "…" suffix.

When working on this codebase, prioritize location permission handling, BLE scanning edge cases, and database transaction safety. The app targets network coverage mapping, so accuracy of GPS coordinates and BLE detection reliability are critical.
