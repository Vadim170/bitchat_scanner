#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat >&2 <<'EOF'
Usage: verify-android-manifest.sh --apk PATH [--output PATH]

Audits the checked-in manifest and the packaged APK for the BitChat Scanner
BLE/background-delivery contract. The optional output file receives the
permission and manifest dumps used by the audit.
EOF
}

apk_path=""
output_path=""

while (($# > 0)); do
  case "$1" in
    --apk)
      [[ $# -ge 2 ]] || { usage; exit 2; }
      apk_path="$2"
      shift 2
      ;;
    --output)
      [[ $# -ge 2 ]] || { usage; exit 2; }
      output_path="$2"
      shift 2
      ;;
    -h|--help)
      usage >&2
      exit 0
      ;;
    *)
      usage
      exit 2
      ;;
  esac
done

if [[ -z "${apk_path}" || ! -f "${apk_path}" ]]; then
  echo "APK not found: ${apk_path:-<missing>}" >&2
  exit 1
fi

manifest_path="app/src/main/AndroidManifest.xml"
if [[ ! -f "${manifest_path}" ]]; then
  echo "Source manifest not found: ${manifest_path}" >&2
  exit 1
fi

forbidden_source_pattern='BleScannerService|android\.app\.Service|startForeground(Service)?|ServiceCompat|FOREGROUND_SERVICE|foregroundServiceType|getRunningServices|startForegroundService|ACCESS_BACKGROUND_LOCATION|BLUETOOTH_ADVERTISE|neverForLocation'
if rg -n -i "${forbidden_source_pattern}" app/src/main; then
  echo "Foreground-service, background-service, advertise, or neverForLocation reference found in production sources." >&2
  exit 1
fi

if ! rg -n 'android:name="android.hardware.bluetooth_le"' "${manifest_path}"; then
  echo "The manifest must declare BLE hardware support." >&2
  exit 1
fi
if ! rg -n 'android:required="true"' "${manifest_path}"; then
  echo "BLE hardware support must be required by the manifest." >&2
  exit 1
fi
if ! rg -n 'android:name="\.BleScanReceiver"' "${manifest_path}"; then
  echo "The manifest must register the background scan receiver." >&2
  exit 1
fi
if ! rg -n 'android:exported="false"' "${manifest_path}"; then
  echo "The background scan receiver must be explicitly non-exported." >&2
  exit 1
fi
if ! rg -n 'android:allowBackup="false"' "${manifest_path}"; then
  echo "Detection history backup must be disabled in the manifest." >&2
  exit 1
fi
if ! rg -n 'android:dataExtractionRules="@xml/data_extraction_rules"' "${manifest_path}"; then
  echo "Android 12+ device-to-device transfer rules must exclude the detection database." >&2
  exit 1
fi

aapt2_path="${AAPT2_PATH:-}"
if [[ -z "${aapt2_path}" ]]; then
  android_home="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [[ -z "${android_home}" ]]; then
    echo "ANDROID_HOME or ANDROID_SDK_ROOT must be set when AAPT2_PATH is not provided." >&2
    exit 1
  fi
  aapt2_path="$(find "${android_home}/build-tools" -type f -name aapt2 | sort -V | tail -n 1)"
fi
if [[ -z "${aapt2_path}" || ! -x "${aapt2_path}" ]]; then
  echo "Unable to locate an executable aapt2." >&2
  exit 1
fi

permissions="$("${aapt2_path}" dump permissions "${apk_path}")"
for forbidden_permission in \
  android.permission.FOREGROUND_SERVICE \
  android.permission.FOREGROUND_SERVICE_LOCATION \
  android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE \
  android.permission.ACCESS_BACKGROUND_LOCATION \
  android.permission.BLUETOOTH_ADVERTISE; do
  if grep -Fq "uses-permission: name='${forbidden_permission}'" <<<"${permissions}"; then
    echo "Forbidden permission remains in the APK: ${forbidden_permission}" >&2
    exit 1
  fi
done

for required_permission in \
  android.permission.BLUETOOTH_SCAN \
  android.permission.BLUETOOTH_CONNECT \
  android.permission.ACCESS_COARSE_LOCATION \
  android.permission.ACCESS_FINE_LOCATION \
  android.permission.POST_NOTIFICATIONS; do
  if ! grep -Fq "uses-permission: name='${required_permission}'" <<<"${permissions}"; then
    echo "Required permission is missing from the APK: ${required_permission}" >&2
    exit 1
  fi
done

manifest_dump="$("${aapt2_path}" dump xmltree --file AndroidManifest.xml "${apk_path}")"
if ! grep -Fq 'com.vadim170.bitchatscanner.BleScanReceiver' <<<"${manifest_dump}"; then
  echo "BleScanReceiver is missing from the APK manifest." >&2
  exit 1
fi
if ! awk '
  /com\.vadim170\.bitchatscanner\.BleScanReceiver/ { in_receiver = 1 }
  in_receiver && /android:exported/ && /false/ { found = 1; exit }
  in_receiver && /^E: / && !/receiver/ { in_receiver = 0 }
  END { exit(found ? 0 : 1) }
' <<<"${manifest_dump}"; then
  echo "BleScanReceiver must be exported=false in the APK manifest." >&2
  exit 1
fi
if grep -Fq 'com.vadim170.bitchatscanner.BleScannerService' <<<"${manifest_dump}"; then
  echo "BleScannerService remains registered in the APK manifest." >&2
  exit 1
fi
if grep -Fq 'neverForLocation' <<<"${manifest_dump}"; then
  echo "neverForLocation remains in the APK manifest." >&2
  exit 1
fi
if ! grep -Eq 'android:allowBackup.*false' <<<"${manifest_dump}"; then
  echo "Detection history backup must be disabled in the APK manifest." >&2
  exit 1
fi

min_sdk="$("${aapt2_path}" dump badging "${apk_path}" | sed -n "s/^minSdkVersion:'\\([0-9][0-9]*\\)'.*/\\1/p" | head -n 1)"
if [[ -z "${min_sdk}" || "${min_sdk}" -lt 26 ]]; then
  echo "The APK must declare minSdkVersion 26 or newer (found: ${min_sdk:-unknown})." >&2
  exit 1
fi

if [[ -n "${output_path}" ]]; then
  mkdir -p "$(dirname "${output_path}")"
  {
    echo "APK: ${apk_path}"
    echo "minSdkVersion: ${min_sdk}"
    echo
    echo "=== permissions ==="
    printf '%s\n' "${permissions}"
    echo
    echo "=== manifest xmltree ==="
    printf '%s\n' "${manifest_dump}"
  } >"${output_path}"
fi

echo "Android manifest audit passed for ${apk_path}."
