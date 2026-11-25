#!/usr/bin/env python3
"""Silent Morse Translator: local OpenCV + MediaPipe webcam prototype."""

from __future__ import annotations

import argparse
import os
import sys
import time
from datetime import datetime
from pathlib import Path

try:
    import cv2
    import numpy as np
except ImportError as error:
    print(f"OpenCV/NumPy failed to load: {error}\nRun: pip install -r requirements.txt")
    raise SystemExit(2)

try:
    import mediapipe as mp
except Exception as error:
    print(f"MediaPipe Hands failed to load: {error}\nRun: pip install -r requirements.txt")
    raise SystemExit(2)

from morse import MorseDecoder


class Debouncer:
    def __init__(self, delay_ms=40):
        self.delay_ms = delay_ms
        self.value = False
        self.candidate = False
        self.candidate_since = 0.0

    def update(self, raw_value, now_ms):
        if raw_value != self.candidate:
            self.candidate = raw_value
            self.candidate_since = now_ms
        if self.candidate != self.value and now_ms - self.candidate_since >= self.delay_ms:
            self.value = self.candidate
        return self.value

    def reset(self):
        self.__init__(self.delay_ms)


class SessionLog:
    def __init__(self, mode):
        self.started = datetime.now().astimezone()
        self.mode = mode
        self.lines = []

    def event(self, kind, data):
        stamp = datetime.now().astimezone().strftime("%H:%M:%S.%f")[:-3]
        if kind == "signal":
            suffix = f"  {data['symbol']}" if data.get("symbol") else ""
            self.lines.append(f"{stamp}  {data['state']:<3} {data['duration_ms']:.0f}ms{suffix}")
        elif kind == "character":
            self.lines.append(f"{stamp}  CHAR {data['character']} ({data['symbols']})")
            self.lines.append(f"{stamp}  TEXT {data['text']}")

    def save(self, unit_ms, root):
        root.mkdir(parents=True, exist_ok=True)
        filename = self.started.strftime("morse-%Y%m%d-%H%M%S.txt")
        target = root / filename
        counter = 1
        while target.exists():
            target = root / f"{target.stem}-{counter}.txt"
            counter += 1
        zone = self.started.tzname() or "local"
        header = ["# Silent Morse Translator — webcam prototype log", f"# mode: {self.mode}",
                  f"# started: {self.started.strftime('%Y-%m-%d %H:%M:%S')} {zone}",
                  f"# unit_ms at end: {unit_ms:.0f}"]
        target.write_text("\n".join(header + self.lines) + "\n", encoding="utf-8")
        print(f"Saved log: {target}")
        return target


def parse_args():
    parser = argparse.ArgumentParser(description="Decode Morse from a webcam pinch or light")
    parser.add_argument("--camera", type=int, default=0)
    parser.add_argument("--mode", choices=("tap", "light"), default="tap")
    parser.add_argument("--tap-mode", choices=("pinch", "dip"), default="pinch")
    parser.add_argument("--no-calibrate", action="store_true")
    return parser.parse_args()


def visible_words(text):
    parts = text.strip().split()
    result = " ".join(parts[-3:])
    return result or "…"


def put(frame, text, position, scale=0.7, color=(255, 255, 255), thickness=2):
    cv2.putText(frame, text, position, cv2.FONT_HERSHEY_SIMPLEX, scale, color, thickness, cv2.LINE_AA)


def main():
    args = parse_args()
    camera = cv2.VideoCapture(args.camera)
    camera.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
    camera.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)
    if not camera.isOpened():
        print(f"Camera {args.camera} not available. Try --camera 1")
        return 1

    try:
        hands_api = mp.solutions.hands
        drawing = mp.solutions.drawing_utils
        hands = hands_api.Hands(static_image_mode=False, max_num_hands=1,
                                min_detection_confidence=0.55, min_tracking_confidence=0.5)
    except Exception as error:
        camera.release()
        print(f"MediaPipe Hands failed to load: {error}\nRun: pip install -r requirements.txt")
        return 2

    mode = args.mode
    session = SessionLog(mode)
    decoder = MorseDecoder(session.event)
    debouncer = Debouncer()
    last_frame = time.monotonic()
    fps = 0.0
    hand_found = False
    hand_missing_since = time.monotonic()
    threshold = 120.0
    off_level = None
    on_level = None
    calibration_stage = "fixed" if args.no_calibrate else "off"
    calibration_started = time.monotonic()
    calibration_values = []
    log_root = Path(__file__).resolve().parent.parent / "logs"

    def start_light_calibration():
        nonlocal calibration_stage, calibration_started, calibration_values
        calibration_stage = "fixed" if args.no_calibrate else "off"
        calibration_started = time.monotonic()
        calibration_values = []

    if mode == "light":
        start_light_calibration()

    try:
        while True:
            ok, frame = camera.read()
            if not ok:
                print("Camera frame could not be read; closing cleanly.")
                break
            now = time.monotonic()
            now_ms = now * 1000
            dt = max(now - last_frame, 0.001)
            fps = 0.9 * fps + 0.1 * (1 / dt)
            last_frame = now
            frame = cv2.flip(frame, 1)
            height, width = frame.shape[:2]
            raw_signal = False
            detail = ""

            if mode == "tap":
                rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                result = hands.process(rgb)
                hand_found = bool(result.multi_hand_landmarks)
                if hand_found:
                    hand_missing_since = now
                    landmarks = result.multi_hand_landmarks[0]
                    drawing.draw_landmarks(frame, landmarks, hands_api.HAND_CONNECTIONS)
                    points = landmarks.landmark
                    p4, p8, p0, p9 = points[4], points[8], points[0], points[9]
                    pinch = ((p4.x-p8.x)**2 + (p4.y-p8.y)**2) ** 0.5
                    hand_size = max(((p0.x-p9.x)**2 + (p0.y-p9.y)**2) ** 0.5, 0.001)
                    ratio = pinch / hand_size
                    if args.tap_mode == "pinch":
                        raw_signal = ratio < (0.37 if debouncer.value else 0.33)
                        detail = f"Hand: FOUND | pinch {ratio:.2f}"
                    else:
                        raw_signal = points[8].y > points[6].y
                        detail = "Hand: FOUND | dip"
                    for index in (4, 8):
                        point = points[index]
                        cv2.circle(frame, (int(point.x*width), int(point.y*height)), 9,
                                   (0, 255, 0) if debouncer.value else (160, 160, 160), -1)
                else:
                    raw_signal = False
                    detail = "Hand: NO HAND"
                    if now - hand_missing_since > 5:
                        detail = "NO HAND - show your hand to the camera"
            else:
                x1, x2 = int(width*0.3), int(width*0.7)
                y1, y2 = int(height*0.3), int(height*0.7)
                roi = frame[y1:y2, x1:x2]
                brightness = float(np.mean(cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)))
                cv2.rectangle(frame, (x1, y1), (x2, y2), (0, 220, 255), 3)
                if calibration_stage in ("off", "on"):
                    calibration_values.append(brightness)
                    elapsed = now - calibration_started
                    if elapsed >= 2:
                        level = float(np.mean(calibration_values))
                        if calibration_stage == "off":
                            off_level = level
                            calibration_stage = "on"
                            calibration_started = now
                            calibration_values = []
                        else:
                            candidate_on = level
                            if off_level is not None and abs(candidate_on - off_level) >= 20:
                                on_level = candidate_on
                                threshold = (off_level + on_level) / 2
                            else:
                                print("Light calibration too close — move the light closer / darken the room")
                            calibration_stage = "done"
                    raw_signal = False
                else:
                    raw_signal = brightness > threshold
                levels = f"off {off_level:.0f} / on {on_level:.0f}" if off_level is not None and on_level is not None else "fixed" if calibration_stage == "fixed" else calibration_stage
                detail = f"Light: {brightness:.0f} / threshold {threshold:.0f} | {levels}"

            signal = debouncer.update(raw_signal, now_ms)
            decoder.set_state(signal, now_ms)
            decoder.tick(now_ms)

            # Top status strip.
            cv2.rectangle(frame, (0, 0), (width, 54), (20, 20, 20), -1)
            dot_color = (0, 255, 0) if signal else (140, 140, 140)
            cv2.circle(frame, (180, 27), 10, dot_color, -1)
            put(frame, f"Mode: {mode.upper()}", (16, 35), 0.72)
            put(frame, f"Signal: {'ON' if signal else 'OFF'}", (198, 35), 0.72)
            put(frame, f"Unit: {decoder.unit_ms:.0f} ms", (380, 35), 0.72)
            put(frame, f"FPS: {fps:.0f}", (570, 35), 0.72)
            put(frame, detail, (700, 35), 0.62)

            if mode == "light" and calibration_stage == "off":
                put(frame, "Calibrating light... keep light OFF", (width//2-260, 100), 0.85, (0, 220, 255), 2)
            elif mode == "light" and calibration_stage == "on":
                put(frame, "Now turn light ON for 2 seconds", (width//2-250, 100), 0.85, (0, 220, 255), 2)
            elif mode == "light" and calibration_stage == "fixed":
                put(frame, "Fixed light threshold: 120 (--no-calibrate)", (width//2-300, 100), 0.75, (0, 220, 255), 2)

            # Caption bar overlays the bottom third of the live video.
            overlay = frame.copy()
            top = int(height * 0.67)
            cv2.rectangle(overlay, (0, top), (width, height), (0, 0, 0), -1)
            frame = cv2.addWeighted(overlay, 0.68, frame, 0.32, 0)
            put(frame, visible_words(decoder.text), (35, top+72), 1.45, (255, 255, 255), 3)
            put(frame, f"Morse: {decoder.symbols or '…'}", (35, top+124), 0.9, (230, 230, 230), 2)
            put(frame, "C = clear | L = light | T = tap | Q = quit | S = save log", (35, height-24), 0.62, (210, 210, 210), 2)

            cv2.imshow("Silent Morse Translator", frame)
            key = cv2.waitKey(1) & 0xFF
            if key in (ord("q"), 27):
                break
            if key == ord("c"):
                decoder.reset()
                debouncer.reset()
            elif key == ord("t"):
                mode = "tap"
                session.mode = mode
                debouncer.reset()
            elif key == ord("l"):
                mode = "light"
                session.mode = mode
                debouncer.reset()
                start_light_calibration()
            elif key == ord("s"):
                session.save(decoder.unit_ms, log_root)
    finally:
        session.save(decoder.unit_ms, log_root)
        hands.close()
        camera.release()
        cv2.destroyAllWindows()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
