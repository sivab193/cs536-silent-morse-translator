package edu.purdue.cs536.morse

import java.util.ArrayDeque

/** Pure timestamp/segment Morse decoder; intentionally has no Android dependencies. */
class MorseDecoder(private val listener: ((Event) -> Unit)? = null) {
    sealed class Event {
        data class Signal(val on: Boolean, val durationMs: Long, val symbol: String? = null) : Event()
        data class Noise(val durationMs: Long) : Event()
        data class Character(val character: Char, val symbols: String, val text: String) : Event()
        data class Word(val text: String) : Event()
    }

    var text: String = ""
        private set
    var symbols: String = ""
        private set
    var unitMs: Double = INITIAL_UNIT_MS
        private set

    private val dotSamples = ArrayDeque<Double>()
    private var state = false
    private var transitionMs: Long? = null
    private var gapStage = 0

    @Synchronized
    fun reset() {
        text = ""
        symbols = ""
        unitMs = INITIAL_UNIT_MS
        dotSamples.clear()
        state = false
        transitionMs = null
        gapStage = 0
    }

    private fun emit(event: Event) = listener?.invoke(event)

    private fun recordOn(durationMs: Long) {
        if (durationMs < NOISE_MS) {
            emit(Event.Noise(durationMs))
            return
        }
        val symbol = if (durationMs < 1.5 * unitMs) "." else "-"
        symbols += symbol
        if (symbol == ".") observeDot(durationMs.toDouble())
        emit(Event.Signal(true, durationMs, symbol))
    }

    @Synchronized
    fun observeDot(durationMs: Double) {
        if (durationMs < NOISE_MS) return
        if (dotSamples.size == 10) dotSamples.removeFirst()
        dotSamples.addLast(durationMs)
        if (dotSamples.size >= 3) {
            val sorted = dotSamples.sorted()
            val middle = sorted.size / 2
            val median = if (sorted.size % 2 == 1) sorted[middle]
            else (sorted[middle - 1] + sorted[middle]) / 2.0
            unitMs = median.coerceIn(80.0, 500.0)
        }
    }

    private fun finishLetter() {
        if (symbols.isEmpty()) return
        val sequence = symbols
        val character = MORSE_TO_TEXT[sequence] ?: '?'
        text += character
        symbols = ""
        emit(Event.Character(character, sequence, text))
    }

    private fun finishWord() {
        if (text.isNotEmpty() && !text.endsWith(" ")) {
            text += " "
            emit(Event.Word(text))
        }
    }

    @Synchronized
    fun setState(newState: Boolean, nowMs: Long) {
        if (transitionMs == null) {
            state = newState
            transitionMs = nowMs
            return
        }
        tick(nowMs)
        if (newState == state) return
        val duration = nowMs - transitionMs!!
        if (state) recordOn(duration) else emit(Event.Signal(false, duration))
        state = newState
        transitionMs = nowMs
        gapStage = 0
    }

    @Synchronized
    fun tick(nowMs: Long) {
        val transition = transitionMs ?: return
        if (state) return
        val gap = nowMs - transition
        if (gap >= 1.5 * unitMs && gapStage < 1) {
            finishLetter()
            gapStage = 1
        }
        if (gap >= 6.5 * unitMs && gapStage < 2) {
            finishWord()
            gapStage = 2
        }
    }

    @Synchronized
    fun processSegment(on: Boolean, durationMs: Long) {
        if (on) recordOn(durationMs) else {
            if (durationMs >= 1.5 * unitMs) finishLetter()
            if (durationMs >= 6.5 * unitMs) finishWord()
        }
    }

    @Synchronized
    fun decodeSegments(segments: Iterable<Pair<Boolean, Long>>): String {
        segments.forEach { (on, duration) -> processSegment(on, duration) }
        finishLetter()
        return text.trimEnd()
    }

    companion object {
        const val INITIAL_UNIT_MS = 200.0
        const val NOISE_MS = 40L

        val MORSE_TO_TEXT = mapOf(
            ".-" to 'A', "-..." to 'B', "-.-." to 'C', "-.." to 'D', "." to 'E',
            "..-." to 'F', "--." to 'G', "...." to 'H', ".." to 'I', ".---" to 'J',
            "-.-" to 'K', ".-.." to 'L', "--" to 'M', "-." to 'N', "---" to 'O',
            ".--." to 'P', "--.-" to 'Q', ".-." to 'R', "..." to 'S', "-" to 'T',
            "..-" to 'U', "...-" to 'V', ".--" to 'W', "-..-" to 'X', "-.--" to 'Y', "--.." to 'Z',
            "-----" to '0', ".----" to '1', "..---" to '2', "...--" to '3', "....-" to '4',
            "....." to '5', "-...." to '6', "--..." to '7', "---.." to '8', "----." to '9',
            ".-.-.-" to '.', "--..--" to ',', "..--.." to '?', ".----." to '\'', "-.-.--" to '!',
            "-..-." to '/', "-.--." to '(', "-.--.-" to ')', ".-..." to '&', "---..." to ':',
            "-.-.-." to ';', "-...-" to '=', ".-.-." to '+', "-....-" to '-', "..--.-" to '_',
            ".-..-." to '"', "...-..-" to '$', ".--.-." to '@'
        )
        val TEXT_TO_MORSE = MORSE_TO_TEXT.entries.associate { (key, value) -> value to key }

        fun encodeSegments(text: String, unitMs: Long = 200L): List<Pair<Boolean, Long>> {
            val result = mutableListOf<Pair<Boolean, Long>>()
            val words = text.uppercase().split(" ")
            words.forEachIndexed { wordIndex, word ->
                word.forEachIndexed { letterIndex, character ->
                    val code = requireNotNull(TEXT_TO_MORSE[character]) { "No Morse code for $character" }
                    code.forEachIndexed { symbolIndex, symbol ->
                        result += true to if (symbol == '.') unitMs else 3 * unitMs
                        if (symbolIndex < code.lastIndex) result += false to unitMs
                    }
                    if (letterIndex < word.lastIndex) result += false to 3 * unitMs
                }
                if (wordIndex < words.lastIndex) result += false to 7 * unitMs
            }
            return result
        }
    }
}
