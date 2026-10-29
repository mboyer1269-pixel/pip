package ca.michael.pip.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import ca.michael.pip.R
import kotlin.math.sin

/**
 * Pip on S2: a seed with eyes and a leaf, peeking out of a pocket (BRAND.md
 * 5.1 and 5.5). Drawn on a 108-unit grid, the same as the launcher icon.
 * Only the eyes, the leaf and the seed's height move.
 */
class PipFaceView(context: Context) : View(context) {

    enum class Mood { READY, LISTENING, THINKING, SPEAKING, WAITING, SUCCESS, ERROR, STOPPING }

    var mood: Mood = Mood.READY
        set(value) { field = value; invalidate() }

    private val seedPaint = paint(R.color.pip_seed)
    private val leafPaint = paint(R.color.pip_sprout)
    private val inkPaint = paint(R.color.pip_ink)
    private val creamPaint = paint(R.color.pip_cream)
    private val pocketPaint = paint(R.color.pip_indigo)
    private val stitchPaint = paint(R.color.pip_clementine)

    private var phase = 0f
    private val animator: ValueAnimator? =
        if (ValueAnimator.areAnimatorsEnabled()) ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4_000
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
        } else null

    private fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = context.getColor(color) }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); animator?.start() }
    override fun onDetachedFromWindow() { animator?.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val scale = minOf(width, height) / 70f
        canvas.save()
        canvas.translate(width / 2f - 54f * scale, height / 2f - 56f * scale)
        canvas.scale(scale, scale)

        val wave = sin(phase * 2 * Math.PI).toFloat()
        val lift = when (mood) {
            Mood.LISTENING -> -3f
            Mood.SPEAKING -> -1.5f + wave * 1.5f
            Mood.STOPPING -> 10f
            else -> 0f
        }
        canvas.save()
        canvas.translate(0f, lift)

        // Seed
        canvas.drawPath(Path().apply {
            moveTo(54f, 34f)
            lineTo(61.69f, 50.39f)
            arcTo(RectF(45.5f, 45.5f, 62.5f, 62.5f), -25.2f, 230.4f)
            close()
        }, seedPaint)

        // Leaf: sways while thinking, droops on error
        val leafAngle = when (mood) {
            Mood.THINKING -> wave * 12f
            Mood.ERROR -> 40f
            Mood.SUCCESS -> -10f
            else -> 0f
        }
        canvas.save()
        canvas.rotate(leafAngle, 54f, 34f)
        canvas.drawPath(Path().apply {
            moveTo(54f, 34f)
            quadTo(53.5f, 27f, 60f, 25.5f)
            quadTo(61f, 32f, 54f, 34f)
            close()
        }, leafPaint)
        canvas.restore()

        // Eyes
        val blink = mood == Mood.READY && phase in 0.96f..1f
        val eyeY = if (mood == Mood.THINKING) 47.3f else 48.5f
        val eyeDx = if (mood == Mood.WAITING) 0.8f else 0f
        val r = if (mood == Mood.LISTENING) 2.3f else 1.9f
        for (cx in floatArrayOf(50.2f, 57.8f)) {
            val x = cx + eyeDx
            when {
                blink || mood == Mood.ERROR -> canvas.drawRect(x - r, eyeY - 0.4f, x + r, eyeY + 0.5f, inkPaint)
                mood == Mood.SUCCESS && cx > 54f -> canvas.drawRect(x - r, eyeY - 0.4f, x + r, eyeY + 0.5f, inkPaint)
                else -> {
                    canvas.drawCircle(x, eyeY, r, inkPaint)
                    canvas.drawCircle(x + 0.6f, eyeY - 0.7f, 0.6f, creamPaint)
                }
            }
        }
        canvas.restore()

        // Pocket in front, with its stitch line
        canvas.drawPath(Path().apply {
            moveTo(35f, 52f); lineTo(73f, 52f); lineTo(73f, 70f)
            arcTo(RectF(41f, 54f, 73f, 86f), 0f, 90f)
            lineTo(51f, 86f)
            arcTo(RectF(35f, 54f, 67f, 86f), 90f, 90f)
            close()
        }, pocketPaint)
        var x = 39f
        while (x < 70f) {
            canvas.drawRect(x, 55.5f, x + 2f, 56.5f, stitchPaint)
            x += 4f
        }
        canvas.restore()
    }
}
