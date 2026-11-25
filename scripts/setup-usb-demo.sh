#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <glasses-serial> <phone-serial>" >&2
  exit 2
fi

GLASSES_SERIAL="$1"
PHONE_SERIAL="$2"

adb -s "$PHONE_SERIAL" get-state >/dev/null
adb -s "$GLASSES_SERIAL" get-state >/dev/null

# Phone localhost:1235 -> Mac localhost:1235 (local VLM).
adb -s "$PHONE_SERIAL" reverse tcp:1235 tcp:1235

# Mac localhost:8765 -> phone companion, then glasses localhost:8765 -> Mac.
adb -s "$PHONE_SERIAL" forward tcp:8765 tcp:8765
adb -s "$GLASSES_SERIAL" reverse tcp:8765 tcp:8765

echo "USB demo routing is ready."
echo "Phone model base URL: http://127.0.0.1:1235"
echo "Glasses companion URL: http://127.0.0.1:8765"
