# BitChat Scanner

Android app for BLE scanning of BitChat devices with local detection history and map visualization.

## Public Repository Notes

The public repository intentionally excludes local IDE files and private service configuration:

- `.idea/`
- `local.properties`
- `app/google-services.json`

If you want to experiment with your own Firebase project locally, keep `app/google-services.json` only on your machine. The public repository does not require Firebase to build.

## GitHub Releases

The release workflow publishes a signed APK on every push to `main` and `master`, plus manual runs.

Required GitHub Actions secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
