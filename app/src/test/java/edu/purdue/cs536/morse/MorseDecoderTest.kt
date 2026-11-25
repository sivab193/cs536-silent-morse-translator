package edu.purdue.cs536.morse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MorseDecoderTest {
    private fun decode(text: String, unit: Long = 200L) =
        MorseDecoder().decodeSegments(MorseDecoder.encodeSegments(text, unit))

    @Test fun sos() = assertEquals("SOS", decode("SOS"))
    @Test fun hello() = assertEquals("HELLO", decode("HELLO"))
    @Test fun e() = assertEquals("E", decode("E"))
    @Test fun t() = assertEquals("T", decode("T"))
    @Test fun wordGap() = assertEquals("HI THERE", decode("HI THERE"))

    @Test fun unknownPattern() {
        val decoder = MorseDecoder()
        repeat(8) {
            decoder.processSegment(true, 200)
            decoder.processSegment(false, 100)
        }
        decoder.processSegment(false, 600)
        assertEquals("?", decoder.text)
    }

    @Test fun noiseBurstIgnored() {
        val decoder = MorseDecoder()
        decoder.processSegment(true, 20)
        decoder.processSegment(false, 600)
        assertEquals("", decoder.text)
    }

    @Test fun recalibrationRamp() {
        val decoder = MorseDecoder()
        repeat(5) { decoder.observeDot(120.0) }
        assertEquals(120.0, decoder.unitMs, 1.0)
        repeat(10) { decoder.observeDot(300.0) }
        assertTrue(decoder.unitMs >= 295.0)
    }

    @Test fun all54SymbolsRoundTrip() {
        val expected = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.,?'!/()&:;=+-_\"$@"
        assertEquals(expected.toSet(), MorseDecoder.TEXT_TO_MORSE.keys)
        expected.forEach { character ->
            val code = requireNotNull(MorseDecoder.TEXT_TO_MORSE[character])
            assertEquals(character, MorseDecoder.MORSE_TO_TEXT[code])
        }
    }
}
