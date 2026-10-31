package ca.michael.pip.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import ca.michael.pip.R
import kotlin.math.abs
import kotlin.math.sin

/**
 * Pip on S2 (BRAND.md 5.1 and 5.5): a round, chubby seed with big shiny eyes,
 * rosy cheeks, a small mouth and two little arms, peeking out of its pocket.
 * Drawn on a 200-unit grid. Each mood is a pose; the loop adds breathing,
 * blinking, talking, dancing and swaying. With system animations off, poses
 * stay still.
 */
class PipFaceView(context: Context) : View(context) {

    enum class Mood { READY, HELLO, TICKLED, LISTENING, THINKING, SPEAKING, WAITING, SUCCESS, ERROR, SLEEP }

    var mood: Mood = Mood.READY
        set(value) { if (field != value) { field = value; invalidate() } }

    private val seed = fill(R.color.pip_seed)
    private val seedLight = fill(R.color.pip_seed_light)
    private val leaf = fill(R.color.pip_sprout)
    private val ink = fill(R.color.pip_ink)
    private val white = fill(R.color.pip_eye_white)
    private val cream = fill(R.color.pip_cream)
    private val blush = fill(R.color.pip_blush)
    private val accent = fill(R.color.pip_clementine)
    private val inkLine = stroke(R.color.pip_ink, 4f)
    private val stitch = stroke(R.color.pip_clementine, 3f)
    private val stem = stroke(R.color.pip_sprout, 4f)
    private val zz = fill(R.color.pip_cream).apply { isFakeBoldText = true }

    private var phase = 0f
    private val animator: ValueAnimator? =
        if (ValueAnimator.areAnimatorsEnabled()) ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4_000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
        } else null

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = context.getColor(color) }
    private fun stroke(color: Int, width: Float) = fill(color).apply {
        style = Paint.Style.STROKE; strokeWidth = width; strokeCap = Paint.Cap.ROUND
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); animator?.start() }
    override fun onDetachedFromWindow() { animator?.cancel(); super.onDetachedFromWindow() }

    private fun wave(cycles: Int) = sin(phase * cycles * 2 * Math.PI).toFloat()

    override fun onDraw(canvas: Canvas) {
        val scale = minOf(width, height) / 200f
        canvas.save()
        canvas.translate(width / 2f - 100f * scale, height / 2f - 100f * scale)
        canvas.scale(scale, scale)

        val lift = when (mood) {
            Mood.LISTENING -> -8f
            Mood.SUCCESS -> -10f * abs(wave(4))
            Mood.SLEEP -> 18f
            Mood.ERROR -> 4f
            else -> 0f
        } + wave(1) * (if (mood == Mood.SLEEP) 2.5f else 1.5f)

        canvas.save()
        canvas.translate(0f, lift)
        if (mood == Mood.TICKLED) canvas.scale(1.08f, 0.92f, 100f, 150f)
        drawLeaf(canvas)
        canvas.drawOval(RectF(44f, 42f, 156f, 150f), seed)
        canvas.drawOval(RectF(64f, 62f, 108f, 86f), seedLight)
        drawEyes(canvas)
        drawCheeks(canvas)
        drawMouth(canvas)
        canvas.restore()

        // The pocket sits in front of the body; the arms rest on or rise over it.
        canvas.drawPath(Path().apply {
            moveTo(22f, 134f); lineTo(178f, 134f); lineTo(178f, 160f)
            quadTo(178f, 196f, 100f, 196f); quadTo(22f, 196f, 22f, 160f); close()
        }, cream)
        var x = 34f
        while (x < 166f) { canvas.drawLine(x, 146f, x + 8f, 146f, stitch); x += 15f }
        drawArms(canvas, lift)
        if (mood == Mood.SLEEP) drawZz(canvas)
        if (mood == Mood.TICKLED || mood == Mood.SUCCESS) drawSparkles(canvas)
        canvas.restore()
    }

    private fun drawLeaf(canvas: Canvas) {
        val angle = when (mood) {
            Mood.THINKING -> wave(2) * 18f
            Mood.ERROR -> 150f
            Mood.SUCCESS, Mood.HELLO -> 16f
            Mood.SLEEP -> 30f
            else -> wave(1) * 4f
        }
        canvas.drawLine(100f, 44f, 102f, 30f, stem)
        canvas.save()
        canvas.rotate(48f + angle, 112f, 24f)
        canvas.drawOval(RectF(103f, 9f, 121f, 39f), leaf)
        canvas.restore()
    }

    private fun drawEyes(canvas: Canvas) {
        val blink = (mood == Mood.READY || mood == Mood.SPEAKING) && phase in 0.95f..1f
        when {
            mood == Mood.SLEEP || blink -> { arc(canvas, 80f, 92f, down = true); arc(canvas, 120f, 92f, down = true) }
            mood == Mood.TICKLED || mood == Mood.SUCCESS -> { arc(canvas, 80f, 92f, down = false); arc(canvas, 120f, 92f, down = false) }
            mood == Mood.HELLO -> { eye(canvas, 80f, 90f, 0f, 0f, 13f); arc(canvas, 120f, 92f, down = false) }
            else -> {
                val big = if (mood == Mood.LISTENING) 16f else 13f
                val (dx, dy) = when (mood) {
                    Mood.THINKING -> 4f to -7f
                    Mood.WAITING -> 4f to 5f
                    Mood.ERROR -> 0f to 6f
                    else -> 0f to 0f
                }
                eye(canvas, 80f, 90f, dx, dy, big)
                eye(canvas, 120f, 90f, dx, dy, big)
                if (mood == Mood.ERROR) {
                    canvas.drawLine(66f, 74f, 90f, 80f, inkLine)
                    canvas.drawLine(134f, 74f, 110f, 80f, inkLine)
                }
            }
        }
    }

    private fun eye(canvas: Canvas, cx: Float, cy: Float, dx: Float, dy: Float, size: Float) {
        canvas.drawOval(RectF(cx - size, cy - size * 1.15f, cx + size, cy + size * 1.15f), white)
        val r = size * 0.62f
        canvas.drawCircle(cx + dx, cy + 3f + dy, r, ink)
        canvas.drawCircle(cx + dx + r * 0.38f, cy + 3f + dy - r * 0.45f, r * 0.36f, white)
    }

    private fun arc(canvas: Canvas, cx: Float, cy: Float, down: Boolean) {
        canvas.drawPath(Path().apply {
            moveTo(cx - 12f, cy); quadTo(cx, cy + if (down) 9f else -12f, cx + 12f, cy)
        }, inkLine)
    }

    private fun drawCheeks(canvas: Canvas) {
        val alpha = if (mood == Mood.TICKLED || mood == Mood.SUCCESS) 255 else 200
        blush.alpha = alpha
        canvas.drawOval(RectF(53f, 106f, 73f, 118f), blush)
        canvas.drawOval(RectF(127f, 106f, 147f, 118f), blush)
    }

    private fun drawMouth(canvas: Canvas) {
        val talkingOpen = mood == Mood.SPEAKING && wave(10) > 0f
        when {
            mood == Mood.SLEEP -> {}
            mood == Mood.TICKLED || mood == Mood.SUCCESS || mood == Mood.HELLO || talkingOpen ->
                canvas.drawPath(Path().apply {
                    moveTo(88f, 112f); quadTo(100f, if (talkingOpen) 126f else 132f, 112f, 112f); close()
                }, ink)
            mood == Mood.LISTENING -> canvas.drawOval(RectF(95f, 110f, 105f, 122f), ink)
            mood == Mood.THINKING -> canvas.drawLine(94f, 117f, 108f, 117f, inkLine)
            mood == Mood.ERROR -> canvas.drawPath(Path().apply {
                moveTo(92f, 122f); quadTo(100f, 114f, 108f, 122f)
            }, inkLine)
            else -> canvas.drawPath(Path().apply {
                moveTo(91f, 113f); quadTo(100f, 122f, 109f, 113f)
            }, inkLine)
        }
    }

    private fun hand(canvas: Canvas, cx: Float, cy: Float, rotate: Float = 0f) {
        canvas.save()
        canvas.rotate(rotate, cx, cy)
        canvas.drawOval(RectF(cx - 11f, cy - 14f, cx + 11f, cy + 14f), seed)
        canvas.restore()
    }

    private fun drawArms(canvas: Canvas, lift: Float) {
        when (mood) {
            Mood.HELLO -> {
                hand(canvas, 54f, 134f, 90f)
                hand(canvas, 164f, 70f + lift, 25f + wave(4) * 20f)
            }
            Mood.SUCCESS -> {
                hand(canvas, 36f, 60f + lift, -25f + wave(4) * 10f)
                hand(canvas, 164f, 60f + lift, 25f - wave(4) * 10f)
            }
            Mood.TICKLED -> {
                hand(canvas, 44f, 116f, -30f)
                hand(canvas, 156f, 116f, 30f)
            }
            Mood.LISTENING -> {
                hand(canvas, 54f, 134f, 90f)
                hand(canvas, 160f, 86f + lift, -20f)
            }
            Mood.THINKING -> {
                hand(canvas, 54f, 134f, 90f)
                hand(canvas, 118f, 128f + lift)
            }
            Mood.ERROR, Mood.WAITING -> {
                hand(canvas, 76f, 136f, 90f)
                hand(canvas, 124f, 136f, 90f)
            }
            Mood.SLEEP -> {}
            else -> {
                hand(canvas, 54f, 134f, 90f)
                hand(canvas, 146f, 134f, 90f)
            }
        }
    }

    private fun drawZz(canvas: Canvas) {
        val drift = phase * 6f
        zz.textSize = 20f; canvas.drawText("z", 146f, 70f - drift, zz)
        zz.textSize = 26f; canvas.drawText("z", 160f, 50f - drift, zz)
        zz.textSize = 32f; canvas.drawText("Z", 176f, 28f - drift, zz)
    }

    private fun drawSparkles(canvas: Canvas) {
        val pulse = 1f + 0.25f * abs(wave(3))
        sparkle(canvas, 30f, 62f, 9f * pulse)
        sparkle(canvas, 172f, 40f, 7f * pulse)
    }

    private fun sparkle(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        canvas.drawPath(Path().apply {
            moveTo(cx, cy - r); lineTo(cx + r * 0.3f, cy - r * 0.3f); lineTo(cx + r, cy)
            lineTo(cx + r * 0.3f, cy + r * 0.3f); lineTo(cx, cy + r); lineTo(cx - r * 0.3f, cy + r * 0.3f)
            lineTo(cx - r, cy); lineTo(cx - r * 0.3f, cy - r * 0.3f); close()
        }, accent)
    }
}
