# Silent Morse Translator

An Android smart-glasses system that turns hand gestures or light pulses into
Morse text. The repository contains the Air2 glasses app, an Android phone
companion for OCR/offload, optional local vision-model serving, and a small
desktop decoder reference.

## Current status

The complete two-device path has been built and exercised on physical hardware:

- **INMO Air2 glasses:** camera preview, MediaPipe hand tracking, light mode,
  adaptive Morse decoding, logging, and the redesigned 640×400 HUD.
- **Android phone companion:** HTTP bridge, ML Kit OCR, local/cloud model
  settings, status reporting, and request logs.
- **Mac local inference:** Qwen3-VL-4B served directly by `llama.cpp` through an
  OpenAI-compatible API. LM Studio is not required.

See [docs/TESTING.md](docs/TESTING.md) for measured physical-device results and
known limitations.

## Data flow

```text
Hand or light ──> Air2 detector ──> Morse decoder ──> glasses HUD

Air2 camera ──> phone companion ──> ML Kit OCR ──> glasses HUD
                         │
                         └──> local/cloud OpenAI-compatible model
```

Processing stays local by default. The glasses app does not upload or retain
video; it only saves text session logs when requested or when the activity
closes.

## Repository layout

```text
app/                 Air2/Android glasses application
phone-companion/     Android phone OCR and compute bridge
scripts/             Build, USB demo, and local VLM launch helpers
prototype/python/    Legacy desktop decoder and webcam reference
docs/                Architecture and physical test evidence
```

## Build and test

Requirements: JDK 17, Android SDK 36, and an Android device with USB debugging.

```bash
./gradlew testDebugUnitTest assembleDebug
python3 -m unittest discover -s prototype/python -p 'test_*.py'
```

Generated APKs:

```text
app/build/outputs/apk/debug/app-debug.apk
phone-companion/build/outputs/apk/debug/phone-companion-debug.apk
```

On Windows, create the optional desktop prototype environment and verify the full source tree:

```powershell
# Run this with a Python 3.11 executable (the Windows launcher form is shown).
py -3.11 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r prototype\python\requirements.txt
.\scripts\verify-build.ps1
```

Install both builds using device serials shown by `adb devices -l`:

```bash
./scripts/install-debug.sh <glasses-serial> <phone-serial>
```

Windows PowerShell equivalent:

```powershell
.\scripts\install-debug.ps1 -GlassesSerial <glasses-serial> -PhoneSerial <phone-serial>
```

## Run a USB demo

Start the phone companion and glasses app, then create the tested tunnel chain:

```bash
./scripts/setup-usb-demo.sh <glasses-serial> <phone-serial>
```

Windows PowerShell equivalent:

```powershell
.\scripts\setup-usb-demo.ps1 -GlassesSerial <glasses-serial> -PhoneSerial <phone-serial>
```

The routing is:

```text
glasses localhost:8765 -> Mac -> phone companion:8765
phone localhost:1235   -> Mac local VLM:1235
```

In the phone companion, use:

- Base URL: `http://127.0.0.1:1235`
- Model: `qwen3-vl-4b`
- API key: blank for the default local demo

Long-press **Phone** on the glasses to edit its companion endpoint. The default
`http://127.0.0.1:8765` works with the USB tunnel above.

## Run Qwen or LFM directly

Download each model's main GGUF and matching `mmproj` vision projector, then:

```bash
brew install llama.cpp
./scripts/run-local-vlm.sh qwen
```

Use `./scripts/run-local-vlm.sh lfm` for the smaller LFM3-VL-2B option. Override
the default `~/models` location with `MORSE_MODEL_ROOT` and port 1235 with
`MORSE_VLM_PORT`. More details are in [LOCAL_VLM.md](LOCAL_VLM.md).

## Glasses controls

- **Hand:** pinch input; long-press to switch between pinch and index-dip.
- **Light:** center-region brightness calibration and pulse detection.
- **Phone:** send periodic frames to the phone companion for OCR.
- **Clear:** reset decoded text and signal state.
- **Save:** persist the current text session log.
- **Share:** open Android's share sheet for the latest log.

## Privacy and constraints

- Camera frames are processed in memory and are not recorded.
- Phone OCR runs locally with ML Kit.
- The optional VLM server listens on the local network; use a trusted network or
  add an API key before exposing it beyond a controlled demo.
- Local ML Kit OCR was removed from the Air2 build because its native runtime
  executes an unsupported CPU instruction on that hardware. OCR is therefore
  delegated to the phone or a server.

## Remaining work

The deadline-ordered implementation, validation, and presentation plan is in
[`docs/REMAINING_WORK.md`](docs/REMAINING_WORK.md).
