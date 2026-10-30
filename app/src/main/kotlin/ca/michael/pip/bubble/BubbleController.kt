package ca.michael.pip.bubble

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import ca.michael.pip.R
import ca.michael.pip.ui.MainActivity
import kotlin.math.abs

/**
 * Block B (SPEC 15): the optional floating bubble. It lives only while the
 * service runs (FR-B.3), is not focusable (FR-B.5), and does one thing:
 * tap opens Pip listening (FR-B.4). Dragging moves it; its place is kept.
 */
class BubbleController(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val prefs = context.getSharedPreferences("pip_bubble", Context.MODE_PRIVATE)
    private var view: View? = null

    fun canShow(): Boolean = Settings.canDrawOverlays(context)

    /** Shows the bubble when [wanted] and allowed, hides it otherwise. */
    fun sync(wanted: Boolean) {
        if (wanted && canShow()) show() else hide()
    }

    @SuppressLint("ClickableViewAccessibility") // The tap is also exposed through performClick.
    private fun show() {
        if (view != null) return
        val density = context.resources.displayMetrics.density
        val size = (56 * density).toInt()
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", 0)
            y = prefs.getInt("y", (240 * density).toInt())
        }
        val bubble = ImageView(context).apply {
            setImageResource(R.mipmap.ic_launcher)
            contentDescription = context.getString(R.string.bubble_desc)
            setOnClickListener { openTalking() }
        }
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var dragging = false
        bubble.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        params.x = startX + dx.toInt(); params.y = startY + dy.toInt()
                        wm.updateViewLayout(v, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) prefs.edit().putInt("x", params.x).putInt("y", params.y).apply() else v.performClick()
                    true
                }
                else -> false
            }
        }
        wm.addView(bubble, params)
        view = bubble
    }

    fun hide() {
        view?.let { wm.removeView(it) }
        view = null
    }

    private fun openTalking() {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra(MainActivity.EXTRA_TALK, true)
        )
    }
}
