package edu.purdue.cs536.morse

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionLogger(context: Context, initialMode: String) {
    private val startedAt = Date()
    private val lines = mutableListOf<String>()
    private val clock = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
    private val directory = File(context.filesDir, "logs").apply { mkdirs() }
    val file: File = uniqueFile(directory, SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(startedAt))

    @Volatile var mode: String = initialMode

    @Synchronized
    fun modeChanged(newMode: String) {
        mode = newMode
        lines += "${clock.format(Date())}  MODE ${newMode.uppercase()}"
    }

    @Synchronized
    fun add(event: MorseDecoder.Event) {
        val stamp = clock.format(Date())
        when (event) {
            is MorseDecoder.Event.Signal -> {
                val symbol = event.symbol?.let { "  $it" }.orEmpty()
                lines += "$stamp  ${if (event.on) "ON " else "OFF"} ${event.durationMs}ms$symbol"
            }
            is MorseDecoder.Event.Character -> {
                lines += "$stamp  CHAR ${event.character} (${event.symbols})"
                lines += "$stamp  TEXT ${event.text}"
            }
            is MorseDecoder.Event.Noise -> lines += "$stamp  NOISE ${event.durationMs}ms ignored"
            is MorseDecoder.Event.Word -> lines += "$stamp  TEXT ${event.text}"
        }
    }

    @Synchronized
    fun note(message: String) {
        lines += "${clock.format(Date())}  NOTE $message"
    }

    @Synchronized
    fun save(unitMs: Double): File {
        val content = buildString {
            appendLine("# Silent Morse Translator — Android session log")
            appendLine("# mode at save: $mode")
            appendLine("# started: ${date.format(startedAt)}")
            appendLine("# unit_ms at end: ${unitMs.toInt()}")
            lines.forEach(::appendLine)
        }
        file.writeText(content)
        return file
    }

    private fun uniqueFile(root: File, stamp: String): File {
        var candidate = File(root, "morse-$stamp.txt")
        var suffix = 1
        while (candidate.exists()) candidate = File(root, "morse-$stamp-${suffix++}.txt")
        return candidate
    }
}
