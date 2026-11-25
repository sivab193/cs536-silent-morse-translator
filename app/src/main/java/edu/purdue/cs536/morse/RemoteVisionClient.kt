package edu.purdue.cs536.morse

import android.graphics.Bitmap
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class RemoteVisionClient(@Volatile var endpoint: String) {
    data class OcrResponse(val text: String, val serverLatencyMs: Long)
    private val executor = Executors.newSingleThreadExecutor()

    fun ocr(bitmap: Bitmap, callback: (Result<OcrResponse>) -> Unit) {
        executor.execute {
            callback(runCatching {
                val scaled = if (bitmap.width > 640) {
                    Bitmap.createScaledBitmap(bitmap, 640, bitmap.height * 640 / bitmap.width, true)
                } else bitmap
                val bytes = ByteArrayOutputStream().use { stream ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 72, stream)
                    stream.toByteArray()
                }
                if (scaled !== bitmap) scaled.recycle()
                val connection = (URL("${endpoint.trimEnd('/')}/ocr").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 2_500
                    readTimeout = 10_000
                    doOutput = true
                    setRequestProperty("Content-Type", "image/jpeg")
                    setFixedLengthStreamingMode(bytes.size)
                }
                try {
                    connection.outputStream.use { it.write(bytes) }
                    val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                        .bufferedReader().use { it.readText() }
                    check(connection.responseCode in 200..299) { "Phone ${connection.responseCode}: ${body.take(100)}" }
                    val json = JSONObject(body)
                    OcrResponse(json.optString("text"), json.optLong("latency_ms"))
                } finally {
                    connection.disconnect()
                }
            })
        }
    }
}
