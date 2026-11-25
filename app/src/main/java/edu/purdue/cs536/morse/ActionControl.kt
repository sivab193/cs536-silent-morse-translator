package edu.purdue.cs536.morse

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class ActionControl @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    private val icon = ImageView(context)
    private val label = TextView(context)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        minimumWidth = dp(48)
        minimumHeight = dp(48)
        setPadding(dp(4), dp(7), dp(4), dp(5))
        background = controlBackground()

        addView(icon, LayoutParams(dp(21), dp(21)))
        addView(label, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(2)
        })
        label.apply {
            gravity = Gravity.CENTER
            setTextColor(textColors())
            textSize = 10f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
            maxLines = 1
        }
        icon.imageTintList = textColors()
        stateListAnimator = null
    }

    fun configure(text: String, iconResource: Int, description: String = text) {
        label.text = text
        icon.setImageResource(iconResource)
        contentDescription = description
    }

    private fun controlBackground() = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_selected), rounded(DesignTokens.ACCENT, 12))
        addState(intArrayOf(android.R.attr.state_focused), rounded(DesignTokens.FOCUS, 12, DesignTokens.ACCENT, 2))
        addState(intArrayOf(android.R.attr.state_pressed), rounded(DesignTokens.PRESSED, 12))
        addState(intArrayOf(), rounded(Color.TRANSPARENT, 12))
    }

    private fun textColors() = ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_selected),
            intArrayOf(android.R.attr.state_focused),
            intArrayOf()
        ),
        intArrayOf(Color.WHITE, Color.WHITE, DesignTokens.SECONDARY_TEXT)
    )

    private fun rounded(fill: Int, radius: Int, stroke: Int? = null, strokeWidth: Int = 0) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            setColor(fill)
            if (stroke != null) setStroke(dp(strokeWidth), stroke)
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

object DesignTokens {
    val ACCENT = Color.rgb(10, 132, 255)
    val SIGNAL = Color.rgb(48, 209, 88)
    val WARNING = Color.rgb(255, 214, 10)
    val ERROR = Color.rgb(255, 69, 58)
    val SURFACE = Color.rgb(28, 28, 30)
    val SURFACE_RAISED = Color.rgb(44, 44, 46)
    val FOCUS = Color.rgb(56, 56, 60)
    val PRESSED = Color.rgb(58, 58, 62)
    val SECONDARY_TEXT = Color.rgb(209, 209, 214)
    val TERTIARY_TEXT = Color.rgb(174, 174, 178)
}
