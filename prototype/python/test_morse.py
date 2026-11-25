import unittest

from morse import MORSE_TO_TEXT, TEXT_TO_MORSE, MorseDecoder, encode_segments


class DecoderTests(unittest.TestCase):
    def decode(self, text, unit=200):
        return MorseDecoder().decode_segments(encode_segments(text, unit))

    def test_sos(self): self.assertEqual(self.decode("SOS"), "SOS")
    def test_hello(self): self.assertEqual(self.decode("HELLO"), "HELLO")
    def test_e(self): self.assertEqual(self.decode("E"), "E")
    def test_t(self): self.assertEqual(self.decode("T"), "T")
    def test_word_gap(self): self.assertEqual(self.decode("HI THERE"), "HI THERE")

    def test_unknown(self):
        decoder = MorseDecoder()
        for _ in range(8):
            decoder.process_segment(True, 200)
            decoder.process_segment(False, 100)
        decoder.process_segment(False, 600)
        self.assertEqual(decoder.text, "?")

    def test_recalibration(self):
        decoder = MorseDecoder()
        for _ in range(5): decoder.observe_dot(120)
        self.assertAlmostEqual(decoder.unit_ms, 120, delta=1)
        for _ in range(10): decoder.observe_dot(300)
        self.assertGreaterEqual(decoder.unit_ms, 295)

    def test_complete_table_round_trip(self):
        expected = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.,?'!/()&:;=+-_\"$@"
        self.assertEqual(set(expected), set(TEXT_TO_MORSE))
        for character in expected:
            self.assertEqual(MORSE_TO_TEXT[TEXT_TO_MORSE[character]], character)


if __name__ == "__main__":
    unittest.main(verbosity=2)
