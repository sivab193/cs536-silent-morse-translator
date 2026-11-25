"""Pure International Morse decoder with adaptive dot timing."""

from __future__ import annotations

from collections import deque
from statistics import median
from typing import Callable, Iterable, Optional

MORSE_TO_TEXT = {
    ".-": "A", "-...": "B", "-.-.": "C", "-..": "D", ".": "E",
    "..-.": "F", "--.": "G", "....": "H", "..": "I", ".---": "J",
    "-.-": "K", ".-..": "L", "--": "M", "-.": "N", "---": "O",
    ".--.": "P", "--.-": "Q", ".-.": "R", "...": "S", "-": "T",
    "..-": "U", "...-": "V", ".--": "W", "-..-": "X", "-.--": "Y", "--..": "Z",
    "-----": "0", ".----": "1", "..---": "2", "...--": "3", "....-": "4",
    ".....": "5", "-....": "6", "--...": "7", "---..": "8", "----.": "9",
    ".-.-.-": ".", "--..--": ",", "..--..": "?", ".----.": "'", "-.-.--": "!",
    "-..-.": "/", "-.--.": "(", "-.--.-": ")", ".-...": "&", "---...": ":",
    "-.-.-.": ";", "-...-": "=", ".-.-.": "+", "-....-": "-", "..--.-": "_",
    ".-..-.": '"', "...-..-": "$", ".--.-.": "@",
}
TEXT_TO_MORSE = {value: key for key, value in MORSE_TO_TEXT.items()}


class MorseDecoder:
    """Stateful decoder. Timestamps are monotonic milliseconds."""

    def __init__(self, event_callback: Optional[Callable[[str, dict], None]] = None):
        self.event_callback = event_callback
        self.reset()

    def reset(self) -> None:
        self.text = ""
        self.symbols = ""
        self.unit_ms = 200.0
        self.dot_samples: deque[float] = deque(maxlen=10)
        self.state = False
        self.transition_ms: Optional[float] = None
        self.gap_stage = 0

    def _emit(self, kind: str, **data) -> None:
        if self.event_callback:
            self.event_callback(kind, data)

    def _record_on(self, duration_ms: float) -> None:
        if duration_ms < 40:
            self._emit("noise", duration_ms=duration_ms)
            return
        symbol = "." if duration_ms < 1.5 * self.unit_ms else "-"
        self.symbols += symbol
        if symbol == ".":
            self.dot_samples.append(duration_ms)
            if len(self.dot_samples) >= 3:
                self.unit_ms = max(80.0, min(500.0, float(median(self.dot_samples))))
        self._emit("signal", state="ON", duration_ms=duration_ms, symbol=symbol)

    def observe_dot(self, duration_ms: float) -> None:
        """Add a known dot sample (useful for calibration/tests without classifying it)."""
        if duration_ms >= 40:
            self.dot_samples.append(duration_ms)
            if len(self.dot_samples) >= 3:
                self.unit_ms = max(80.0, min(500.0, float(median(self.dot_samples))))

    def _finish_letter(self) -> None:
        if not self.symbols:
            return
        sequence = self.symbols
        character = MORSE_TO_TEXT.get(sequence, "?")
        self.text += character
        self.symbols = ""
        self._emit("character", character=character, symbols=sequence, text=self.text)

    def _finish_word(self) -> None:
        if self.text and not self.text.endswith(" "):
            self.text += " "
            self._emit("word", text=self.text)

    def set_state(self, state: bool, now_ms: float) -> None:
        """Accept a debounced binary state sample."""
        if self.transition_ms is None:
            self.state = state
            self.transition_ms = now_ms
            return
        self.tick(now_ms)
        if state == self.state:
            return
        duration = now_ms - self.transition_ms
        if self.state:
            self._record_on(duration)
        else:
            self._emit("signal", state="OFF", duration_ms=duration)
        self.state = state
        self.transition_ms = now_ms
        self.gap_stage = 0

    def tick(self, now_ms: float) -> None:
        """Finalize letters/words while OFF, even before the next transition."""
        if self.transition_ms is None or self.state:
            return
        gap = now_ms - self.transition_ms
        if gap >= 1.5 * self.unit_ms and self.gap_stage < 1:
            self._finish_letter()
            self.gap_stage = 1
        if gap >= 6.5 * self.unit_ms and self.gap_stage < 2:
            self._finish_word()
            self.gap_stage = 2

    def process_segment(self, state: bool, duration_ms: float) -> None:
        """Offline/test interface for a (state, duration_ms) segment."""
        if state:
            self._record_on(duration_ms)
        else:
            if duration_ms >= 1.5 * self.unit_ms:
                self._finish_letter()
            if duration_ms >= 6.5 * self.unit_ms:
                self._finish_word()

    def decode_segments(self, segments: Iterable[tuple[bool, float]]) -> str:
        for state, duration_ms in segments:
            self.process_segment(state, duration_ms)
        self._finish_letter()
        return self.text.rstrip()


def encode_segments(text: str, unit_ms: float = 200) -> list[tuple[bool, float]]:
    """Test helper: encode text into exact Morse timing segments."""
    segments: list[tuple[bool, float]] = []
    for word_index, word in enumerate(text.upper().split(" ")):
        for letter_index, character in enumerate(word):
            code = TEXT_TO_MORSE[character]
            for symbol_index, symbol in enumerate(code):
                segments.append((True, unit_ms if symbol == "." else 3 * unit_ms))
                if symbol_index < len(code) - 1:
                    segments.append((False, unit_ms))
            if letter_index < len(word) - 1:
                segments.append((False, 3 * unit_ms))
        if word_index < len(text.split(" ")) - 1:
            segments.append((False, 7 * unit_ms))
    return segments
