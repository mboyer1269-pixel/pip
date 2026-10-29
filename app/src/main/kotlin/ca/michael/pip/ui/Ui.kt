package ca.michael.pip.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import ca.michael.pip.R

/** Small helpers for Pip's plain platform-View screens (no UI library). */
object Ui {
    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    /** A scrolling column as the activity's content, titled [titleRes]. */
    fun screen(activity: Activity, titleRes: Int): LinearLayout {
        activity.title = activity.getString(titleRes)
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(activity, 16)
            setPadding(p, p, p, p)
        }
        activity.setContentView(ScrollView(activity).apply { addView(column) })
        return column
    }

    fun text(context: Context, value: CharSequence, sizeSp: Float = 16f, bold: Boolean = false): TextView =
        TextView(context).apply {
            text = value
            textSize = sizeSp
            setTextColor(context.getColor(R.color.pip_ink))
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(context, 4), 0, dp(context, 4))
        }

    fun button(context: Context, label: CharSequence, style: Style = Style.PLAIN, onClick: (View) -> Unit): Button =
        Button(context).apply {
            text = label
            isAllCaps = false
            // Refuse taps that pass through an overlay drawn by another app.
            filterTouchesWhenObscured = true
            when (style) {
                Style.PRIMARY -> { backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.pip_clementine)); setTextColor(context.getColor(R.color.pip_ink)) }
                Style.DANGER -> { backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.pip_coral_alert)); setTextColor(context.getColor(R.color.pip_cream)) }
                Style.PLAIN -> {}
            }
            setOnClickListener(onClick)
        }

    fun row(context: Context, vararg views: View): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEach { addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
    }

    fun spacer(context: Context, heightDp: Int = 12): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(context, heightDp))
    }

    enum class Style { PLAIN, PRIMARY, DANGER }
}
