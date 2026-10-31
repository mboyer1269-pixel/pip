package ca.michael.pip.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import ca.michael.pip.R

/**
 * Small helpers for Pip's plain platform-View screens (no UI library), styled
 * after BRAND.md 5.6: cream ground, generous pocket-like rounded corners,
 * clementine for the main action.
 */
object Ui {
    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    /** A rounded shape filled with [fill], with an optional outline. Radii in dp. */
    fun shape(context: Context, fill: Int, radiusDp: Int, strokeColor: Int? = null, strokeDp: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(context.getColor(fill))
            cornerRadius = dp(context, radiusDp).toFloat()
            if (strokeColor != null) setStroke(dp(context, strokeDp), context.getColor(strokeColor))
        }

    /** Touch feedback on top of [base]. */
    fun touchable(context: Context, base: Drawable): Drawable =
        RippleDrawable(ColorStateList.valueOf(context.getColor(R.color.pip_ink) and 0x22FFFFFF), base, null)

    /** A scrolling column as the activity's content, titled [titleRes]. */
    fun screen(activity: Activity, titleRes: Int): LinearLayout {
        activity.title = activity.getString(titleRes)
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(activity, 20)
            setPadding(p, dp(activity, 12), p, p)
        }
        activity.setContentView(ScrollView(activity).apply {
            setBackgroundColor(activity.getColor(R.color.pip_cream))
            addView(column)
        })
        return column
    }

    fun text(context: Context, value: CharSequence, sizeSp: Float = 16f, bold: Boolean = false): TextView =
        TextView(context).apply {
            text = value
            textSize = sizeSp
            setTextColor(context.getColor(R.color.pip_ink))
            setLineSpacing(0f, 1.15f)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(context, 4), 0, dp(context, 4))
        }

    fun button(context: Context, label: CharSequence, style: Style = Style.PLAIN, onClick: (View) -> Unit): Button =
        Button(context).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            stateListAnimator = null
            // Refuse taps that pass through an overlay drawn by another app.
            filterTouchesWhenObscured = true
            minHeight = dp(context, 48)
            setPadding(dp(context, 16), 0, dp(context, 16), 0)
            val (fill, ink, line) = when (style) {
                Style.PRIMARY -> Triple(R.color.pip_clementine, R.color.pip_ink, null)
                Style.DANGER -> Triple(R.color.pip_coral_alert, R.color.pip_cream, null)
                Style.INDIGO -> Triple(R.color.pip_indigo, R.color.pip_cream, null)
                Style.PLAIN -> Triple(R.color.pip_card, R.color.pip_indigo, R.color.pip_chip_line)
            }
            background = touchable(context, shape(context, fill, 18, line, if (line != null) 2 else 0))
            setTextColor(context.getColor(ink))
            setTypeface(typeface, Typeface.BOLD)
            setOnClickListener(onClick)
        }

    /** A square icon button with a spoken label (content description). */
    fun iconButton(context: Context, icon: Int, label: CharSequence, fill: Int, tint: Int, onClick: (View) -> Unit): ImageButton =
        ImageButton(context).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(context.getColor(tint))
            contentDescription = label
            filterTouchesWhenObscured = true
            background = touchable(context, shape(context, fill, 16))
            setOnClickListener(onClick)
        }

    /** A small rounded label (status pill). */
    fun pill(context: Context, value: CharSequence, fill: Int, ink: Int): TextView = TextView(context).apply {
        text = value
        textSize = 12f
        setTextColor(context.getColor(ink))
        background = shape(context, fill, 999)
        setPadding(dp(context, 10), dp(context, 4), dp(context, 10), dp(context, 4))
    }

    /** A rounded card that holds a vertical column. */
    fun card(context: Context, fill: Int = R.color.pip_card, radiusDp: Int = 20, padDp: Int = 16): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(context, fill, radiusDp)
            elevation = if (fill == R.color.pip_card) dp(context, 1).toFloat() else 0f
            val p = dp(context, padDp)
            setPadding(p, p, p, p)
        }

    fun row(context: Context, vararg views: View): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        views.forEachIndexed { i, v ->
            addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (i > 0) marginStart = dp(context, 8)
            })
        }
    }

    fun spacer(context: Context, heightDp: Int = 12): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(context, heightDp))
    }

    /** Layout params: full width, wrap height, with a top margin in dp. */
    fun full(context: Context, topDp: Int = 0, heightDp: Int? = null) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        heightDp?.let { dp(context, it) } ?: ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(context, topDp) }

    enum class Style { PLAIN, PRIMARY, DANGER, INDIGO }
}
