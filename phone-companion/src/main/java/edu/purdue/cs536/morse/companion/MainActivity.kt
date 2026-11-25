package edu.purdue.cs536.morse.companion

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var serviceStatus: TextView
    private lateinit var baseUrl: EditText
    private lateinit var apiKey: EditText
    private lateinit var model: EditText
    private lateinit var ocr: PhoneOcrEngine
    private var server: CompanionServer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Palette.BACKGROUND
        window.navigationBarColor = Palette.BACKGROUND
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
            setBackgroundColor(Palette.BACKGROUND)
        }
        root.addView(buildHeader())
        root.addView(buildEndpointCard(), verticalMargins(top = 20))

        root.addView(sectionTitle("Model routing", "Optional cloud or local model connection"), verticalMargins(top = 24, bottom = 10))
        val settingsCard = card().apply { setPadding(dp(16), dp(8), dp(16), dp(12)) }
        baseUrl = labeledField(settingsCard, "BASE URL", "http://host:port", prefs.getString("base", "http://192.168.1.2:1234").orEmpty())
        model = labeledField(settingsCard, "MODEL", "Model name", prefs.getString("model", "local-model").orEmpty())
        apiKey = labeledField(settingsCard, "API KEY", "Optional", prefs.getString("key", "").orEmpty()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(settingsCard)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        actions.addView(actionButton("Start", R.drawable.ic_start, true) { startServer() }, weightedButton())
        actions.addView(actionButton("Stop", R.drawable.ic_stop, false) { stopServer() }, weightedButton(8))
        actions.addView(actionButton("Test", R.drawable.ic_test, false) { testUpstream() }, weightedButton(8))
        root.addView(actions, verticalMargins(top = 14))

        root.addView(sectionTitle("Activity", "Live requests and connection events"), verticalMargins(top = 24, bottom = 10))
        status = TextView(this).apply {
            text = "Starting companion…\n"
            textSize = 13f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setTextColor(Palette.SECONDARY_TEXT)
            setTextIsSelectable(true)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(Palette.SURFACE, 16)
            minHeight = dp(132)
        }
        root.addView(status)

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })

        ocr = PhoneOcrEngine(this)
        startServer()
    }

    private fun buildHeader() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.ic_app)
            contentDescription = null
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(TextView(this@MainActivity).apply {
                text = "Morse Companion"
                textSize = 23f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(Color.WHITE)
                includeFontPadding = false
            })
            addView(TextView(this@MainActivity).apply {
                text = "Phone compute bridge"
                textSize = 13f
                setTextColor(Palette.SECONDARY_TEXT)
                includeFontPadding = false
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        serviceStatus = TextView(this@MainActivity).apply {
            text = "STARTING"
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Palette.WARNING)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = rounded(Palette.SURFACE_RAISED, 50)
        }
        addView(serviceStatus)
    }

    private fun buildEndpointCard() = card().apply {
        setPadding(dp(16), dp(14), dp(16), dp(14))
        addView(TextView(this@MainActivity).apply {
            text = "GLASSES ENDPOINT"
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Palette.SECONDARY_TEXT)
        })
        addView(TextView(this@MainActivity).apply {
            val addresses = localAddresses()
            text = if (addresses.isEmpty()) "Connect this phone to Wi-Fi" else addresses.joinToString("\n") { "http://$it:8765" }
            textSize = 17f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.WHITE)
            setPadding(0, dp(5), 0, dp(3))
            setTextIsSelectable(true)
        })
        addView(TextView(this@MainActivity).apply {
            text = "Use this address in Phone mode on the glasses."
            textSize = 13f
            setTextColor(Palette.TERTIARY_TEXT)
        })
    }

    private fun sectionTitle(title: String, subtitle: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(this@MainActivity).apply {
            text = title
            textSize = 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.WHITE)
        })
        addView(TextView(this@MainActivity).apply {
            text = subtitle
            textSize = 13f
            setTextColor(Palette.SECONDARY_TEXT)
        })
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Palette.SURFACE, 16)
    }

    private fun labeledField(parent: LinearLayout, label: String, hint: String, value: String): EditText {
        parent.addView(TextView(this).apply {
            text = label
            textSize = 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Palette.SECONDARY_TEXT)
            setPadding(0, dp(10), 0, dp(5))
        })
        return EditText(this).apply {
            this.hint = hint
            setText(value)
            textSize = 15f
            setTextColor(Color.WHITE)
            setHintTextColor(Palette.TERTIARY_TEXT)
            setSingleLine(true)
            setPadding(dp(12), 0, dp(12), 0)
            minHeight = dp(48)
            backgroundTintList = ColorStateList.valueOf(Palette.OUTLINE)
            parent.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        }
    }

    private fun actionButton(text: String, icon: Int, primary: Boolean, action: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 14f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (primary) Color.WHITE else Palette.SECONDARY_TEXT)
        setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0)
        compoundDrawablePadding = dp(7)
        compoundDrawableTintList = ColorStateList.valueOf(if (primary) Color.WHITE else Palette.SECONDARY_TEXT)
        background = rounded(if (primary) Palette.ACCENT else Palette.SURFACE_RAISED, 14)
        minHeight = dp(50)
        setOnClickListener { action() }
    }

    private fun weightedButton(startMargin: Int = 0) = LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginStart = dp(startMargin) }

    private fun verticalMargins(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = dp(top)
        bottomMargin = dp(bottom)
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radius).toFloat()
        setColor(color)
    }

    private fun settings(): UpstreamSettings {
        val result = UpstreamSettings(baseUrl.text.toString().trim(), apiKey.text.toString(), model.text.toString().trim())
        getSharedPreferences("settings", MODE_PRIVATE).edit()
            .putString("base", result.baseUrl).putString("key", result.apiKey).putString("model", result.model).apply()
        return result
    }

    private fun startServer() {
        if (server != null) {
            appendStatus("Companion is already running")
            return
        }
        setServiceState("STARTING", Palette.WARNING)
        server = CompanionServer(8765, ocr, ::settings, ::appendStatus).also { it.start() }
    }

    private fun stopServer() {
        server?.close()
        server = null
        setServiceState("OFFLINE", Palette.SECONDARY_TEXT)
        appendStatus("Server stopped")
    }

    private fun testUpstream() {
        appendStatus("Testing model connection…")
        Thread {
            runCatching { UpstreamClient.chat(settings(), "Reply with exactly: MODEL CONNECTED") }
                .onSuccess { appendStatus("Model response: ${it.toString().take(500)}") }
                .onFailure { appendStatus("Model test failed: ${it.message}") }
        }.start()
    }

    private fun appendStatus(message: String) = runOnUiThread {
        if (message.startsWith("Listening")) setServiceState("ONLINE", Palette.SIGNAL)
        if (message.startsWith("Server error")) {
            server?.close()
            server = null
            setServiceState("ERROR", Palette.ERROR)
        }
        status.append("${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())}  $message\n")
    }

    private fun setServiceState(label: String, color: Int) {
        if (!::serviceStatus.isInitialized) return
        serviceStatus.text = label
        serviceStatus.setTextColor(color)
    }

    private fun localAddresses(): List<String> = NetworkInterface.getNetworkInterfaces().toList()
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .filter { !it.isLoopbackAddress }
        .map { it.hostAddress ?: "" }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        stopServer()
        ocr.close()
        super.onDestroy()
    }
}

private object Palette {
    val BACKGROUND = Color.rgb(0, 0, 0)
    val SURFACE = Color.rgb(28, 28, 30)
    val SURFACE_RAISED = Color.rgb(44, 44, 46)
    val OUTLINE = Color.rgb(72, 72, 74)
    val ACCENT = Color.rgb(10, 132, 255)
    val SIGNAL = Color.rgb(48, 209, 88)
    val WARNING = Color.rgb(255, 214, 10)
    val ERROR = Color.rgb(255, 69, 58)
    val SECONDARY_TEXT = Color.rgb(209, 209, 214)
    val TERTIARY_TEXT = Color.rgb(142, 142, 147)
}
