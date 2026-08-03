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
Uses version-aware permission requests:
- **Android 12+**: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` are core scanner permissions; location is optional, and `ACCESS_FINE_LOCATION` enables visible-session coordinate tagging (requested together with coarse location for a precise grant)
- **Android 8.0–11**: `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION` are required for BLE scan results
- **Android 13+**: `POST_NOTIFICATIONS` is optional and only needed for detection notifications

```kotlin
// Check BLE and map permissions based on Android version
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    // Request BLUETOOTH_SCAN and BLUETOOTH_CONNECT
    // Optionally request ACCESS_COARSE_LOCATION + ACCESS_FINE_LOCATION
    // so visible callback detections can receive coordinates
} else {
    // Request ACCESS_COARSE_LOCATION + ACCESS_FINE_LOCATION for BLE scanning
}
```

### Scanner and Activity Lifecycle
- Start scanning only from a visible Activity after the required permissions are granted.
- Use the active callback while the Activity is visible.
- On Android 8.0+, a started hidden session uses a filtered system-managed `PendingIntent` delivered to an explicit non-exported receiver.
- Make the scan `PendingIntent` mutable with `FLAG_MUTABLE | FLAG_UPDATE_CURRENT` because Bluetooth adds result extras; make it explicit to this package/component and reject deliveries after Stop Scanner.
- Keep notification content `PendingIntent`s targeting `MainActivity` immutable (`FLAG_IMMUTABLE`).
- Treat hidden delivery as best-effort/throttled and non-continuous; never promise guaranteed background detection.
- If a vendor rejects the zero-length service-data presence filter, retain service-UUID/solicitation filters and disclose that service-data-only advertisements may be missed in hidden mode; keep the visible callback matcher broad.
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

## Location and Notification Integration

On Android 8.0–11, coarse and fine location are required for BLE scan results. On Android 12+, location is optional for scanning; best-effort tagging uses available providers and a last-known location only from the visible active-callback path when `ACCESS_FINE_LOCATION` is granted. Hidden `PendingIntent` detections never request a fresh coordinate and remain in local history without map coordinates. The app does not request `ACCESS_BACKGROUND_LOCATION`.

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
