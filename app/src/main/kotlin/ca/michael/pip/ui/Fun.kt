package ca.michael.pip.ui

import android.content.Context
import ca.michael.pip.R
import java.time.LocalTime

/** Pip's small personality touches (BRAND.md "Fun pack"). Local only: nothing is read or sent. */
object Fun {
    val CHIPS = listOf(R.string.chip_tomorrow, R.string.chip_notifications, R.string.chip_calls, R.string.chip_steps)

    fun greeting(context: Context, now: LocalTime = LocalTime.now()): String = context.getString(
        when (now.hour) {
            in 5..11 -> R.string.greet_morning
            in 12..17 -> R.string.greet_afternoon
            in 18..22 -> R.string.greet_evening
            else -> R.string.greet_night
        }
    )

    fun quip(context: Context): String = context.resources.getStringArray(R.array.pip_quips).random()
}
