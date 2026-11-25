# Architecture, evaluation, and classroom demo

## System architecture

```text
                                        optional network path
Air2 camera -> CameraX -> mode selector ----------------------------+
     |                                                               |
     +-> Hand: MediaPipe landmarks -> pinch/index-dip Boolean         |
     |                                                               |
     +-> Light: center-ROI brightness -> calibrated Boolean           |
     |                                                               |
     +-> Phone: periodic JPEG -> HTTP /ocr -> phone ML Kit OCR <------+ 
                                      |
                                      +-> optional local/cloud VLM API

Hand/Light Boolean -> 40 ms debounce -> timestamped ON/OFF segments
                   -> adaptive dot/dash + letter/word gap decoder
                   -> 640x400 Air2 HUD + session log
```

The Hand and Light paths run entirely on the glasses and require no Internet connection. The Phone path needs a connection from the glasses to the phone companion, but that connection can be local Wi-Fi or the deterministic USB/ADB tunnel. ML Kit OCR runs locally on the phone. Internet is required only if the optional upstream model points to a cloud API. A laptop-hosted local VLM over USB or LAN does not require Internet after its model files are downloaded.

## How Morse is parsed

1. The selected detector emits a raw ON/OFF value for each analyzed camera frame.
2. `SignalDebouncer` accepts a state change only after it remains stable for 40 ms, filtering camera flicker and hand jitter.
3. `MorseDecoder` measures the elapsed time between accepted state transitions.
4. An ON duration below 1.5 timing units is a dot; a longer ON duration is a dash. Pulses below 40 ms are rejected as noise.
5. An OFF duration at least 1.5 units closes a letter. An OFF duration at least 6.5 units closes a word.
6. The dot duration median from the latest samples updates the timing unit, clamped between 80 and 500 ms. This lets the decoder adapt to different senders.
7. The accumulated symbol string is looked up in the International Morse table. Unknown sequences become `?` instead of silently disappearing.

For a 200 ms unit, the target timing is:

- Dot: 200 ms ON
- Dash: 600 ms ON
- Gap inside one character: 200 ms OFF
- Letter gap: 600 ms OFF
- Word gap: 1400 ms OFF

## Test-video generator

Open `tools/morse-sender.html` in Chrome or Edge. It supports:

- preset or custom messages;
- 100-300 ms timing units;
- white-disc, full-frame, and diagnostic displays;
- normal/inverted contrast;
- a visible expected output and exact segment timeline;
- fullscreen transmission to the glasses camera;
- synchronized WebM video and JSON ground-truth downloads.

The generated JSON records every ON/OFF segment, its start time, duration, character, and symbol. Play the WebM fullscreen while the Air2 is in Light mode, save the Air2 session log, and compare its decoded text and timings against the JSON. Test at least `SOS`, `HELLO`, `HELLO WORLD`, and `CS536` at 100, 150, 200, 250, and 300 ms. Use 200 ms as the expected classroom-safe starting point.

## Results to report

### Part A foundation

Part A is not the graded live demo, but it establishes the engineering foundation:

- Air2 developer/ADB setup and APK installation;
- legacy-compatible camera capture and preview;
- bounded latest-frame processing to prevent latency buildup;
- Air2-to-phone connectivity and screen mirroring;
- measured frame rate, network/processing latency, and CSV/session logging.

Show Part A as one architecture slide and one compact results table. Do not spend live demo time recreating the streaming baseline.

### Part B measured results

Report the existing verified evidence, clearly labeled by date/hardware:

- Air2 camera and HUD: 29-30 FPS in the recorded physical test;
- phone ML Kit OCR: 178 ms measured round trip, including 109 ms phone OCR for one recorded frame;
- Python decoder tests: 8/8 passing;
- Android decoder, debounce, and light-detector tests: passing;
- both debug APKs: reproducible clean build.

Add a controlled trial table before the final report:

| Input | Message | Unit/speed | Environment | Trials | Exact matches | Median latency | Notes |
|---|---|---:|---|---:|---:|---:|---|
| Video/light | SOS | 200 ms | classroom-like | 10 | TBD | TBD | baseline |
| Video/light | HELLO | 200 ms | classroom-like | 10 | TBD | TBD | repeated L test |
| Hand/pinch | HELLO | manual | classroom-like | 10 | TBD | TBD | adaptive timing |
| Phone OCR | printed phrase | periodic | USB tunnel | 10 | TBD | TBD | stabilized text |

Do not convert one successful run into an accuracy percentage. Accuracy requires repeated, recorded trials.

## Classroom setup and five-minute demo

### Before the team ahead begins

1. Connect the Air2 and phone by USB and enable USB debugging.
2. Run `adb devices -l` and identify both serials.
3. Run the install script only if the known-good APKs are not already installed.
4. Run `scripts/setup-usb-demo.ps1` (Windows) or `scripts/setup-usb-demo.sh` (macOS/Linux).
5. Start the phone companion, then the Air2 app; verify Camera, Hand, Light, and Phone controls.
6. Open the sender in Chrome/Edge, select `SOS`, 200 ms, white disc, and fullscreen.

### Presentation timing

- 0:00-0:40: problem, Part B goal, and architecture.
- 0:40-1:00: explain local parsing and exactly where networking is optional.
- 1:00-2:00: Light demo using the deterministic sender (`SOS` or `HELLO`).
- 2:00-2:45: Hand/pinch demo with a short message.
- 2:45-3:15: Phone OCR demo if stable; otherwise show its recorded measured result.
- 3:15-4:10: results table, FPS, latency, exact-match trials, and tests.
- 4:10-5:00: Part A foundation, privacy, limitations, and conclusion.

### Failure recovery

- Light calibration fails: switch to fixed threshold, increase screen brightness, use full-white-frame display, and move the glasses closer.
- Hand tracking is unstable: use a shorter message, slower deliberate pinches, or index-dip mode.
- Phone routing fails: restart both companion servers and rerun the USB tunnel script; do not depend on classroom Wi-Fi.
- Hardware setup consumes the slot: show the prerecorded test video plus matching ground-truth JSON and saved Air2 session log.
- VLM is unavailable: omit it. The primary Part B Morse demo and phone ML Kit path remain local.

## Work order through submission

1. Validate generated videos against the decoder with connected Air2 hardware.
2. Implement and unit-test OCR temporal stabilization/deduplication.
3. Run and record the controlled trial matrix; tune thresholds only from observed failures.
4. Rehearse the complete fresh-device setup until it is consistently below three minutes.
5. Freeze and tag the source revision by November 30.
6. Finish the 4-6 page report and five-minute slides by December 4 using the recorded table and consistent architecture.
7. Test the classroom projector and perform two full timed rehearsals before December 7.
