# Copilot Instructions for BitChat Scanner

## Project Overview
This is a specialized Android app that scans for BitChat network nodes using Bluetooth Low Energy (BLE). The app creates a coverage map by detecting devices with specific BLE service UUIDs and storing detections with GPS coordinates.

## Architecture Overview

### Core Components
- **MainActivity**: Jetpack Compose UI with permission handling and BLE scan controls
- **BleScannerService**: Foreground service for continuous BLE scanning with location tagging
- **DetectionStore**: SQLite database abstraction for storing scan results (max 1000 records)
- **Constants**: BitChat-specific UUIDs and service identifiers

### Data Flow
1. Service scans for BLE devices with BitChat service UUID `f47b5e2d-4a9e-4c5a-9b3f-8e1d2c3a4b5c`
2. Matches found via service UUIDs, service data keys, or service solicitation UUIDs
3. Each detection stored with timestamp, device info, RSSI, GPS coordinates, and service data
4. Real-time broadcasts sent to UI for live updates
5. Database automatically pruned to maintain 1000 most recent detections

## Development Patterns

### Permission Handling
Uses version-aware permission requests:
- **Android 12+**: `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`
- **Pre-Android 12**: `ACCESS_FINE_LOCATION` (required for BLE scanning)
- **Android 13+**: `POST_NOTIFICATIONS` (optional)

```kotlin
// Check permissions based on Android version
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    // Use BLUETOOTH_SCAN permission
} else {
    // Fallback to ACCESS_FINE_LOCATION
}
```

### Service Communication
Uses local broadcasts for service-to-UI communication:
```kotlin
const val ACTION_LOG_LINE = "com.vadim170.bitchatscanner.LOG_LINE"
// Service sends detection events, UI receives via BroadcastReceiver
```

### Compose UI State Management
- `rememberSaveable` for configuration persistence
- `mutableStateListOf` for real-time log updates
- `LaunchedEffect` for database initialization
- `DisposableEffect` for broadcast receiver lifecycle

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
- **Min SDK**: 24 (Android 7.0)
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

## Service & Background Processing

### Foreground Service Requirements
Service uses combined foreground service types:
```xml
android:foregroundServiceType="connectedDevice|location"
```

### Location Integration
Best-effort location tagging using all available providers (GPS, Network, Passive) with last-known location fallback.

## Development Commands

### Build & Run
```bash
./gradlew assembleDebug          # Build debug APK
./gradlew installDebug          # Install to connected device
./gradlew test                  # Run unit tests
./gradlew connectedAndroidTest  # Run instrumented tests
```

### Debug BLE Issues
- Check device Bluetooth status in service logs
- Monitor scan results via broadcast receiver
- Verify BitChat service UUID detection logic
- Use Android Studio's Device Monitor for BLE debugging

## Code Conventions

### Resource Naming
- String resources: `app_name` (snake_case)
- Theme: `Theme.BitchatScanner` (PascalCase)
- Colors: Material3 dynamic theming preferred

### Service Patterns
- Foreground services must call `ServiceCompat.startForeground()`
- Always check permissions before BLE operations
- Use `@RequiresPermission` annotations for clarity

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