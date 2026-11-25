package edu.purdue.cs536.morse.companion

import android.content.Context
import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

class PhoneOcrEngine(@Suppress("UNUSED_PARAMETER") context: Context) : AutoCloseable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun recognize(jpeg: ByteArray): Result {
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)) { "Invalid JPEG" }
        return try {
            val started = System.nanoTime()
            val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), 20, TimeUnit.SECONDS)
            Result(result.text, (System.nanoTime() - started) / 1_000_000L)
        } finally {
            bitmap.recycle()
        }
    }

    override fun close() = recognizer.close()
    data class Result(val text: String, val latencyMs: Long)
}
