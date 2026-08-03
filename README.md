# BitChat Scanner

BitChat Scanner is an Android 8.0+ app for detecting nearby BitChat devices over BLE, storing detections locally, and visualizing signal coverage on a map. It uses an active scan callback while the Activity is visible and Android's system-managed filtered delivery when a started session continues while the app is hidden.

![BitChat Scanner screenshots](docs/images/screenshots-strip.png)

## What the app does

- Scans for nearby BitChat devices over Bluetooth Low Energy.
- Starts and stops scanning explicitly from the main screen.
- Uses an active callback while the Activity is visible; on Android 8.0+ a hidden started session uses a filtered `PendingIntent` delivered to a non-exported receiver.
- Stores detection history locally on the device.
- Shows per-device cards with first seen, last seen, RSSI, and detection count.
- Draws approximate coverage circles on a map based on recorded coordinates and signal strength.
- Opens a full-screen map with all saved detections.
- Can optionally notify about detections; an opt-in notification shows the matching device name or address and RSSI, subject to the Android 13+ notification permission and a cooldown.

## Privacy and data handling

- Detection history is stored in the app-local database; the app does not sync or upload records to an app backend, and app backup is disabled for this data.
- This public repository does not require Firebase or other private service configuration to build.
- Map views download tiles for the displayed area through osmdroid / OpenStreetMap; those tile requests go to the configured map-tile provider. Detection records are not uploaded to an app backend.
- The app does not use a foreground service or background location permission. Hidden scan delivery is system-managed and best-effort.

## Required permissions

- On Android 12+ (API 31+), `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` are the core BLE discovery permissions.
- On Android 8.0–11 (API 26–30), `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION` are required for BLE scan results.
- On Android 12+, location is optional for scanning. Grant `ACCESS_FINE_LOCATION` (requested together with coarse location for the precise grant) to attach coordinates to visible active-callback detections; hidden detections never receive fresh coordinates.
- `POST_NOTIFICATIONS` is optional for detection notifications; Android 13+ requires its runtime grant before notifications can be shown.

## Tech stack

- Kotlin
- Jetpack Compose
- Lifecycle-aware callback scanning plus system-managed filtered `PendingIntent` delivery (no foreground service or background location)
- osmdroid for map rendering

## Scanning lifecycle

- Tap **Start Scanner** while the main Activity is visible to begin a scan session.
- While the Activity is visible, results arrive through the active BLE scan callback.
- On Android 8.0+ (API 26+), a started session switches to a system-managed filtered `PendingIntent` scan when the app is hidden. The scan `PendingIntent` uses `FLAG_MUTABLE | FLAG_UPDATE_CURRENT` because the Bluetooth framework adds result extras; its explicit package/component and non-exported receiver constrain delivery to this app.
- Hidden delivery is best-effort and may be throttled, delayed, batched, or stopped by the system; it is not continuous background monitoring. The app does not claim guaranteed background detection.
- Some vendor BLE stacks reject the zero-length service-data presence filter. The fallback keeps service-UUID and solicitation filters, so service-data-only BitChat advertisements may be missed while hidden even though the visible callback matcher accepts them.
- Background detections do not collect a fresh coordinate. They remain in local history without map coordinates; visible callback detections may include the current best-effort coordinate when precise location is granted.
- Tap **Stop Scanner** to end both active-callback and hidden delivery registration. There is no foreground service and no `ACCESS_BACKGROUND_LOCATION` permission.
- Detection history and device summaries are persisted locally, so stopping a session does not delete previously recorded data.

## Building locally

```bash
./gradlew assembleDebug
```

Notes:

- The public repo intentionally excludes local/private files such as `.idea/`, `local.properties`, `app/google-services.json`, and release signing keystores.
- If you want to experiment with your own private services locally, keep those files only on your machine and do not commit them.
- Release signing is configured through environment variables in CI, not through committed files.

## GitHub releases

The GitHub Actions workflow publishes a signed APK when you push a version tag.

Example:

```bash
git tag v1.0.3
git push origin v1.0.3
```

The workflow validates that the pushed tag matches `versionName` from the Android build. For example, tag `v1.0.3` must match:

```groovy
versionName "1.0.3"
```

If the tag and app version do not match, the release job fails instead of publishing a mismatched APK.

Required GitHub Actions secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

## Project notes

- Architecture overview: [ARCHITECTURE.md](ARCHITECTURE.md)
