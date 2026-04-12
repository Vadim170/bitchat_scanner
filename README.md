# BitChat Scanner

BitChat Scanner is an Android app for detecting nearby BitChat devices over BLE, storing detections locally, and visualizing signal coverage on a map.

![BitChat Scanner screenshots](docs/images/screenshots-strip.png)

## What the app does

- Scans for nearby BitChat devices over Bluetooth Low Energy.
- Stores detection history locally on the device.
- Shows per-device cards with first seen, last seen, RSSI, and detection count.
- Draws approximate coverage circles on a map based on recorded coordinates and signal strength.
- Opens a full-screen map with all saved detections.
- Can optionally notify about new detections.

## Privacy and data handling

- Detection history is stored locally in the app.
- This public repository does not require Firebase or other private service configuration to build.
- The app uses network access for map tiles via osmdroid / OpenStreetMap.

## Required permissions

- `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` for BLE discovery.
- `ACCESS_FINE_LOCATION` to attach detections to coordinates and render them on the map.
- `POST_NOTIFICATIONS` is optional and only needed if you want detection notifications.

## Tech stack

- Kotlin
- Jetpack Compose
- Android foreground service for BLE scanning
- osmdroid for map rendering

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
