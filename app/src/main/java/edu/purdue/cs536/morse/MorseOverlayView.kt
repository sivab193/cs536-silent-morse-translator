package edu.purdue.cs536.morse

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.View

class MorseOverlayView(context: Context) : View(context) {
    data class UiState(
        val mode: String = "TAP",
        val signalOn: Boolean = false,
        val unitMs: Int = 200,
        val fps: Int = 0,
        val detail: String = "Starting camera…",
        val decoded: String = "",
        val symbols: String = "",
        val prompt: String = "",
        val handPoints: List<HandTracker.Point>? = null,
        val cameraError: String? = null
    )

    @Volatile var state = UiState()
        private set
    var controlsHeightPx: Int = 58

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val handConnections = listOf(
        0 to 1, 1 to 2, 2 to 3, 3 to 4, 0 to 5, 5 to 6, 6 to 7, 7 to 8,
        5 to 9, 9 to 10, 10 to 11, 11 to 12, 9 to 13, 13 to 14, 14 to 15,
        15 to 16, 13 to 17, 17 to 18, 18 to 19, 19 to 20, 0 to 17
    )

    fun update(transform: (UiState) -> UiState) {
        state = transform(state)
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = state
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        drawStatusBar(canvas, s, w)

        if (s.mode == "LIGHT") {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(2).toFloat()
            paint.color = DesignTokens.WARNING
            canvas.drawRoundRect(RectF(w * 0.3f, h * 0.27f, w * 0.7f, h * 0.67f), dp(12).toFloat(), dp(12).toFloat(), paint)
        } else drawHand(canvas, s.handPoints, w, h)

        if (s.prompt.isNotBlank()) {
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(238, 28, 28, 30)
            val promptRect = RectF(dp(54).toFloat(), dp(76).toFloat(), w - dp(54), dp(120).toFloat())
            canvas.drawRoundRect(promptRect, dp(14).toFloat(), dp(14).toFloat(), paint)
            paint.textAlign = Paint.Align.CENTER
            paint.color = DesignTokens.WARNING
            paint.textSize = sp(14)
            paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            canvas.drawText(ellipsize(s.prompt, w - dp(132), paint), w / 2, promptRect.centerY() - (paint.ascent() + paint.descent()) / 2, paint)
            paint.textAlign = Paint.Align.LEFT
        }

        val controlsTop = h - controlsHeightPx
        val captionHeight = dp(92).toFloat()
        val captionTop = (controlsTop - captionHeight).coerceAtLeast(h * 0.52f)
        val captionRect = RectF(dp(8).toFloat(), captionTop, w - dp(8), controlsTop - dp(2))
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(238, 18, 18, 20)
        canvas.drawRoundRect(captionRect, dp(16).toFloat(), dp(16).toFloat(), paint)
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        paint.color = Color.WHITE
        paint.textSize = sp(28)
        val visible = s.decoded.trim().split(Regex("\\s+")).takeLast(3).joinToString(" ")
        val decodedLabel = visible.ifBlank { "Waiting for signal" }
        canvas.drawText(ellipsize(decodedLabel, w - dp(48), paint), dp(22).toFloat(), captionTop + dp(44), paint)
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.color = DesignTokens.SECONDARY_TEXT
        paint.textSize = sp(13)
        val morse = if (s.symbols.isBlank()) "No symbols yet" else s.symbols
        canvas.drawText(ellipsize(morse, w - dp(48), paint), dp(22).toFloat(), captionTop + dp(70), paint)

        s.cameraError?.let { message ->
            paint.color = Color.argb(248, 10, 10, 12)
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.color = DesignTokens.ERROR
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            paint.textSize = sp(18)
            canvas.drawText(ellipsize(message, w - dp(80), paint), w / 2, h * 0.38f, paint)
            paint.color = DesignTokens.SECONDARY_TEXT
            paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            paint.textSize = sp(13)
            canvas.drawText("Check camera access, then retry", w / 2, h * 0.46f, paint)
            paint.textAlign = Paint.Align.LEFT
        }
    }

    private fun drawStatusBar(canvas: Canvas, s: UiState, w: Float) {
        val panel = RectF(dp(8).toFloat(), dp(8).toFloat(), w - dp(8), dp(64).toFloat())
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(238, 18, 18, 20)
        canvas.drawRoundRect(panel, dp(16).toFloat(), dp(16).toFloat(), paint)

        val modeLabel = when (s.mode) { "TAP" -> "Hand"; "REMOTE" -> "Phone"; else -> "Light" }
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        paint.textSize = sp(18)
        paint.color = Color.WHITE
        canvas.drawText(modeLabel, dp(22).toFloat(), dp(34).toFloat(), paint)

        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = sp(11)
        paint.color = DesignTokens.TERTIARY_TEXT
        canvas.drawText(ellipsize(s.detail, w * 0.45f, paint), dp(22).toFloat(), dp(53).toFloat(), paint)

        val chipTop = dp(18).toFloat()
        val chipHeight = dp(30).toFloat()
        val signalWidth = dp(72).toFloat()
        val statsWidth = dp(118).toFloat()
        val statsLeft = w - dp(20) - statsWidth
        val signalLeft = statsLeft - dp(8) - signalWidth
        drawChip(canvas, RectF(signalLeft, chipTop, signalLeft + signalWidth, chipTop + chipHeight), if (s.signalOn) DesignTokens.SIGNAL else DesignTokens.SURFACE_RAISED)
        paint.color = if (s.signalOn) Color.BLACK else DesignTokens.SECONDARY_TEXT
        paint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        paint.textSize = sp(11)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(if (s.signalOn) "SIGNAL" else "IDLE", signalLeft + signalWidth / 2, chipTop + dp(20), paint)

        drawChip(canvas, RectF(statsLeft, chipTop, statsLeft + statsWidth, chipTop + chipHeight), DesignTokens.SURFACE_RAISED)
        paint.color = DesignTokens.SECONDARY_TEXT
        canvas.drawText("${s.unitMs} ms  •  ${s.fps} fps", statsLeft + statsWidth / 2, chipTop + dp(20), paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawChip(canvas: Canvas, rect: RectF, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, paint)
    }

    private fun ellipsize(text: String, maxWidth: Float, source: Paint): String {
        textPaint.set(source)
        return TextUtils.ellipsize(text, textPaint, maxWidth, TextUtils.TruncateAt.END).toString()
    }

    private fun dp(value: Int) = value * resources.displayMetrics.density
    private fun sp(value: Int) = value * resources.displayMetrics.scaledDensity

    private fun drawHand(canvas: Canvas, points: List<HandTracker.Point>?, w: Float, h: Float) {
        if (points == null || points.size < 21) return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2)
        paint.color = DesignTokens.ACCENT
        handConnections.forEach { (a, b) ->
            canvas.drawLine(points[a].x * w, points[a].y * h, points[b].x * w, points[b].y * h, paint)
        }
        paint.style = Paint.Style.FILL
        points.forEachIndexed { index, point ->
            paint.color = if (index == 4 || index == 8) DesignTokens.SIGNAL else DesignTokens.ACCENT
            canvas.drawCircle(point.x * w, point.y * h, if (index == 4 || index == 8) dp(4) else dp(2), paint)
        }
    }
}
