# Google Play submission checklist

Use this list before uploading a new AAB. It records the decisions behind the current scanning design so the Play review has no open questions.

## What changed since the rejected build

The earlier build ran a foreground service (`BleScannerService`, type `connectedDevice|location`) and was rejected for a background service without a clear justification. The current design has no service at all:

- While the Activity is visible the app scans with a regular `ScanCallback`.
- When the Activity is hidden, a user-started session continues through Android's system-managed `BluetoothLeScanner.startScan(filters, settings, PendingIntent)` delivered to a non-exported `BroadcastReceiver`. Delivery is filtered to the BitChat service and best-effort.
- No `FOREGROUND_SERVICE*`, `ACCESS_BACKGROUND_LOCATION` or `BLUETOOTH_ADVERTISE` permissions. `scripts/ci/verify-android-manifest.sh` and `ManifestContractTest` fail the build if any of them return.

## Build

- [ ] `versionCode` and `versionName` in `app/build.gradle` are higher than the last uploaded build (the rejected upload was `4` / `1.0.3`; the current values are `5` / `1.1.0`).
- [ ] `targetSdk` is 36. Google Play requires API 36 for new apps and updates from 31 August 2026.
- [ ] The AAB comes from the tag-driven **Android Release** workflow, which also runs the manifest audit against the same release variant.
- [ ] No native libraries are bundled, so the 16 KB page-size requirement does not apply.

## Play Console: App content

- [ ] **Foreground service permissions**: the manifest no longer declares any `FOREGROUND_SERVICE*` permission. Remove or update the previous declaration so it no longer claims a foreground service.
- [ ] **Location permissions**: only `ACCESS_BACKGROUND_LOCATION` needs the location declaration form. The app does not request it.
- [ ] **Privacy policy**: use the raw or blob URL of `docs/PRIVACY_POLICY.md` on the `main` branch, for example `https://github.com/Vadim170/bitchat_scanner/blob/main/docs/PRIVACY_POLICY.md`.
- [ ] **Data safety**: suggested answers below.

### Data safety answers

- Detection records (Bluetooth addresses of other devices, RSSI, timestamps) and the phone position are processed and stored only on the device and never transmitted. Per the Play definition this is not "collection" and does not need to be declared.
- Map tiles are fetched from OpenStreetMap tile servers by the osmdroid library. A tile request reveals the approximate map area being viewed to a third party. Conservative answer: **Location > Approximate location**, shared with a third party, not collected, purpose *App functionality*, optional (only when the user opens a map). Adjust if your reviewer guidance differs.
- No user IDs, accounts, analytics, crash reporting or advertising SDKs are present.
- Data is encrypted in transit (HTTPS tile requests). Users can request deletion by clearing the log or uninstalling; there is no server-side data.

## Reviewer notes (paste into the release notes for reviewers)

> BitChat Scanner detects nearby BitChat mesh devices over Bluetooth Low Energy and draws their approximate coverage on a map. There is no background service. When the app is hidden, a scan the user explicitly started continues through Android's system-managed BLE PendingIntent subscription (`BluetoothLeScanner.startScan` with a `PendingIntent`), filtered to the BitChat service UUID and delivered to a non-exported receiver. Precise location is required on every Android version because Bluetooth scan results are attributed to location (`BLUETOOTH_SCAN` is declared without `neverForLocation`) and because detections are tagged with the phone position for the coverage map. No detection data leaves the device; the only network traffic is OpenStreetMap map tiles.
>
> To test: open the app, tap **Grant Permissions**, allow Nearby devices and choose **Precise** location, make sure Location Services are on, tap **Start Scanner**. A BitChat device (the BitChat app advertising nearby) appears as a card with RSSI. Press Home: the status line switches to the passive background subscription; returning to the app resumes the active scan.

## Store listing wording

- Do not promise continuous or guaranteed background detection; describe hidden delivery as best-effort and system-managed.
- Mention that precise location and Bluetooth permissions are required and why.

## Pre-launch report expectations

- Devices without a BitChat advertiser nearby show "No devices found"; that is expected.
- If a test device has Location Services off, the app shows "Location Services are off" with a button to the system settings instead of an empty active scan.
