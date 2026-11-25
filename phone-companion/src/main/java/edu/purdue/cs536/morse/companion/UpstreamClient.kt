package edu.purdue.cs536.morse.companion

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpstreamSettings(val baseUrl: String, val apiKey: String, val model: String)

object UpstreamClient {
    fun chat(settings: UpstreamSettings, prompt: String): JSONObject {
        val request = JSONObject()
            .put("model", settings.model)
            .put("temperature", 0.1)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        val connection = (URL("${settings.baseUrl.trimEnd('/')}/v1/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (settings.apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
        }
        return try {
            connection.outputStream.bufferedWriter().use { it.write(request.toString()) }
            val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                .bufferedReader().use { it.readText() }
            check(connection.responseCode in 200..299) { "Upstream ${connection.responseCode}: ${body.take(250)}" }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }
}
