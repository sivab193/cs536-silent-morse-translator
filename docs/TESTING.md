# Verification record

Last updated: 2026-11-16.

## Automated checks

| Check | Result |
|---|---|
| Android JVM tests for decoder, debounce, and light detector | Pass |
| Python decoder reference tests | 8/8 pass |
| Debug APK assembly for both Android modules | Pass |
| Shell syntax for local VLM and USB helper scripts | Pass |

## Physical-device checks

Hardware used:

- INMO Air2, Android 9, 32-bit `armeabi-v7a`, 640×400 physical display.
- Android phone, Android 14, 64-bit ARM.
- Apple M4 Mac mini with 16 GB unified memory.

| Path | Observed result |
|---|---|
| Air2 camera preview and redesigned HUD | Pass; no clipping at 640×400 |
| MediaPipe Hand Landmarker on Air2 | Pass; camera pipeline sustained 29–30 FPS during final check |
| Light detector calibration/ROI UI | Pass |
| Air2 → phone ML Kit OCR over USB tunnel | Pass; 178 ms total round trip, 109 ms OCR on captured frame |
| Phone companion HTTP health endpoint | Pass |
| Phone → local Qwen3-VL-4B API over USB reverse tunnel | Pass; returned `MODEL CONNECTED` |
| Qwen3-VL-4B image input through `llama-server` | Pass; exact transcription of a small error screenshot |
| Save/share session log paths | Pass |

The OCR log showed normal frame-to-frame variation between roughly 41 and 116
ms on the tested scene. Text accuracy depends heavily on focus, contrast, camera
angle, and occlusion.

## Local model configuration tested

```text
Runtime: llama.cpp 1.0.0 with Apple Metal
Model: Qwen3-VL-4B-Instruct Q4_K_M
Projector: Qwen3-VL-4B-Instruct F16 mmproj
Context: 8192 tokens
GPU layers: 99
API: OpenAI-compatible /v1/chat/completions
Port: 1235
```

On a small screenshot, Qwen processed the image/prompt in approximately 0.9 s
and generated around 30 tokens/s. A full 1280×800 screenshot required about 4.7
s of prompt processing, so the VLM is intended as a cropped/periodic
confirmation path rather than a per-frame detector.

## Known limitations

- Phone mode currently uses ML Kit for the real-time `/ocr` path. The upstream
  VLM connection is available and verified, but raw frames are not yet routed to
  Qwen automatically.
- Direct Wi-Fi operation still needs a same-network field test; the final
  two-device check used reproducible ADB forward/reverse tunnels.
- A controlled live `SOS`/`HELLO` accuracy trial with multiple senders, speeds,
  and lighting conditions remains to be recorded.
- Local ML Kit OCR on Air2 is unsupported because its native library raises
  `SIGILL` on the glasses CPU. The phone/server split is the supported design.
- LFM3-VL-2B files are supported by the launch script but were not included in
  the final measured inference pass.
