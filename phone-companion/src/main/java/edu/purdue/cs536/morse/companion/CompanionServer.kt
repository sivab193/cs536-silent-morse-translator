package edu.purdue.cs536.morse.companion

import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class CompanionServer(
    private val port: Int,
    private val ocr: PhoneOcrEngine,
    private val upstream: () -> UpstreamSettings,
    private val log: (String) -> Unit
) : AutoCloseable {
    private val workers = Executors.newFixedThreadPool(2)
    @Volatile private var socket: ServerSocket? = null

    fun start() {
        if (socket != null) return
        workers.execute {
            try {
                val server = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }.also { socket = it }
                log("Listening on port $port")
                while (!server.isClosed) {
                    val client = try { server.accept() } catch (_: SocketException) { break }
                    workers.execute { handle(client) }
                }
            } catch (error: Throwable) {
                log("Server error: ${error.message}")
                socket = null
            }
        }
    }

    private fun handle(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 130_000
            try {
                val input = BufferedInputStream(socket.getInputStream())
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(' ')
                if (parts.size < 2) return
                val method = parts[0]
                val path = parts[1]
                var contentLength = 0
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val split = line.indexOf(':')
                    if (split > 0 && line.substring(0, split).equals("Content-Length", true)) {
                        contentLength = line.substring(split + 1).trim().toIntOrNull() ?: 0
                    }
                }
                require(contentLength <= 5_000_000) { "Request too large" }
                val body = ByteArray(contentLength)
                var read = 0
                while (read < body.size) {
                    val count = input.read(body, read, body.size - read)
                    if (count < 0) break
                    read += count
                }
                val response = when {
                    method == "GET" && path == "/health" -> JSONObject().put("ok", true).put("service", "morse-companion")
                    method == "POST" && path == "/ocr" -> {
                        val result = ocr.recognize(body)
                        log("OCR ${result.latencyMs}ms: ${result.text.replace('\n', ' ').take(80)}")
                        JSONObject().put("text", result.text).put("latency_ms", result.latencyMs)
                    }
                    method == "POST" && path == "/llm" -> {
                        val prompt = JSONObject(String(body, StandardCharsets.UTF_8)).getString("prompt")
                        val result = UpstreamClient.chat(upstream(), prompt)
                        JSONObject().put("response", result)
                    }
                    else -> throw IllegalArgumentException("Unknown route $method $path")
                }
                respond(socket, 200, response.toString())
            } catch (error: Throwable) {
                log("Request failed: ${error.message}")
                respond(socket, 500, JSONObject().put("error", error.message ?: error.toString()).toString())
            }
        }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val bytes = ArrayList<Byte>()
        while (true) {
            val value = input.read()
            if (value < 0) return if (bytes.isEmpty()) null else bytes.toByteArray().toString(StandardCharsets.UTF_8)
            if (value == '\n'.code) break
            if (value != '\r'.code) bytes.add(value.toByte())
            if (bytes.size > 8_192) throw IllegalArgumentException("Header line too long")
        }
        return bytes.toByteArray().toString(StandardCharsets.UTF_8)
    }

    private fun respond(socket: Socket, code: Int, body: String) {
        runCatching {
            val bytes = body.toByteArray(StandardCharsets.UTF_8)
            BufferedOutputStream(socket.getOutputStream()).use { output ->
                output.write("HTTP/1.1 $code ${if (code == 200) "OK" else "Error"}\r\n".toByteArray())
                output.write("Content-Type: application/json\r\n".toByteArray())
                output.write("Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                output.write(bytes)
            }
        }
    }

    override fun close() {
        socket?.close()
        socket = null
        workers.shutdownNow()
    }
}
