#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <glasses-serial> <phone-serial>" >&2
  exit 2
fi

GLASSES_SERIAL="$1"
PHONE_SERIAL="$2"

./gradlew testDebugUnitTest assembleDebug
adb -s "$GLASSES_SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$PHONE_SERIAL" install -r phone-companion/build/outputs/apk/debug/phone-companion-debug.apk

echo "Installed Silent Morse on the glasses and companion on the phone."
