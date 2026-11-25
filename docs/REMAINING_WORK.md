# Remaining work plan

This plan is ordered around the November 30 source deadline, the December 4 report/slide deadline, and the December 7/9 in-class demonstration.

## P0 - Submission reliability (finish first)

- Re-clone the public repository into an empty directory and run `scripts/verify-build.ps1` on Windows and `./gradlew testDebugUnitTest assembleDebug` on macOS/Linux.
- Rehearse installation and USB routing from two freshly connected devices; the complete setup must take under three minutes.
- Record the exact Air2 and phone serial-selection workflow and keep a known-good pair of APKs as a demo fallback.
- Freeze the graded source revision on November 30 and tag it (for example, `submission-2026-11-30`).

## P1 - Stable Part B demonstration

- Add a temporal OCR accumulator that ignores empty frames, deduplicates repeated text, and only changes the HUD after the same normalized result is observed in consecutive samples.
- Add unit tests for OCR normalization, deduplication, and stabilization before wiring it into the glasses UI.
- Run controlled `SOS`, `HELLO`, and `BYE` trials for hand and light input. Record expected text, decoded text, success/failure, input speed, lighting, and end-to-end latency.
- Tune only from recorded failures. Preserve a known-good configuration for the classroom demo.
- Validate both USB routing and direct same-network Wi-Fi. Use USB as the primary classroom path because it is deterministic.

## P2 - VLM fallback (only after P1 is stable)

- Add an image-aware companion route that sends a cropped difficult frame to the configured OpenAI-compatible vision model.
- Trigger the fallback manually or after repeated low-quality/empty OCR results; do not place the multi-second VLM in the per-frame path.
- Display whether a result came from ML Kit or the VLM and log its latency.
- Keep the entire demo functional when no VLM server is running.

## P3 - Presentation and final report

- Build a five-minute presentation: 30-60 seconds on the goal and setup, about two minutes of live Part B demo, then results and limitations.
- Include the architecture, adaptive decoder, 640x400 HUD, measured 29-30 FPS, phone OCR latency, controlled trial table, and privacy design in the 4-6 page two-column report.
- Prepare a prerecorded fallback clip, screenshots, and the measured-results table, while still planning to perform the mandatory live Part B demo.
- Test the laptop/projector connection and rehearse the full setup/presentation by December 4.

## Definition of done

- A fresh clone builds both APKs and passes every automated test using only documented steps.
- A new device pair can be installed and routed in under three minutes.
- The live demo decodes at least one light message and one hand message, and the phone OCR path is demonstrably stable.
- The repository, report, and slides describe the same implemented system and measured results.
