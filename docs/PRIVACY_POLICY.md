# BitChat Scanner Privacy Policy

Effective date: 2 September 2026

BitChat Scanner is an open-source Android app that detects nearby BitChat devices over Bluetooth Low Energy (BLE) and shows where they were seen on a map. This policy describes what the app processes and where that data goes. The source code is public at <https://github.com/Vadim170/bitchat_scanner>.

## Summary

- Everything the app records stays on your device. There is no account, no app backend, no analytics, no crash reporting and no advertising.
- The only network traffic is map tiles downloaded from OpenStreetMap when you open a map view.
- You can delete all recorded data at any time from the app, or by uninstalling it.

## Data processed on the device

When you start a scan, the app records BLE advertisements that match the BitChat service:

- Bluetooth address of the advertising device, its advertised name if present, signal strength (RSSI) and the advertised service data.
- Time of each detection.
- Your device position (latitude, longitude, accuracy, provider) attached to detections made while the app is visible on screen, when precise location permission is granted. Detections received while the app is hidden are stored without a position.

These records are kept in a local SQLite database limited to the most recent 1000 detections. They are used only to show the device list, the detection log and the coverage map inside the app.

The app also stores two local preferences: whether you asked scanning to continue and whether detection notifications are enabled, together with a short cooldown ledger that prevents repeated notifications for the same device.

The database and preferences are excluded from Android cloud backup and from Android 12+ device-to-device transfer.

## Data that leaves the device

Map views are rendered with the osmdroid library, which downloads map tiles for the visible area from OpenStreetMap tile servers. Each tile request reveals to those servers the approximate map area you are viewing, your IP address and the app's package name as the HTTP user agent. No detection records, Bluetooth addresses or precise coordinates are sent. Tile requests are governed by the OpenStreetMap Foundation privacy policy: <https://wiki.osmfoundation.org/wiki/Privacy_Policy>.

The app does not send any other data to any server.

## Permissions

- Bluetooth (Nearby devices on Android 12+): required to scan for BitChat advertisements.
- Precise location: required on every Android version. Android only delivers BLE scan results to this app while precise location is granted and Location Services are on; the position is also attached to detections made while the app is visible. The app does not request background location and does not run a background service.
- Notifications (Android 13+): optional, requested only if you enable detection notifications.
- Internet: used only for map tiles.

## Your choices

- Stop scanning at any time with the Stop button; hidden delivery stops together with it.
- Delete all recorded detections from the Detection Log screen (Clear). Uninstalling the app removes all local data.
- Revoke any permission in Android settings; scanning stops working until the required permissions are granted again.

## Children

The app is not directed at children and does not knowingly process data about them.

## Changes and contact

Changes to this policy are published in the repository together with the app version that introduces them. Questions can be raised at <https://github.com/Vadim170/bitchat_scanner/issues>.
