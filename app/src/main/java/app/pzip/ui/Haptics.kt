package app.pzip.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import app.pzip.core.Prefs

/** Small, consistent vibration cues. Uses the system haptics, so it needs no permission. */
class Haptics(private val view: View) {
    private fun play(constant: Int) {
        if (Prefs.haptics) view.performHapticFeedback(constant)
    }

    /** Buttons, chips, tabs. */
    fun tap() = play(HapticFeedbackConstants.VIRTUAL_KEY)

    /** Very light tick for sliders, gesture steps and keypad keys. */
    fun tick() = play(HapticFeedbackConstants.CLOCK_TICK)

    /** Entering selection mode, locking the player. */
    fun longPress() = play(HapticFeedbackConstants.LONG_PRESS)

    /** Something succeeded (correct PIN, task started). */
    fun confirm() = play(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)

    /** Something was refused (wrong PIN, wrong password). */
    fun reject() = play(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)

    /** Toggling a switch. */
    fun toggle(on: Boolean) = play(
        if (Build.VERSION.SDK_INT >= 34) (if (on) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF)
        else HapticFeedbackConstants.VIRTUAL_KEY,
    )
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

/** True for landscape / tablet-shaped windows (16:9 on its side, foldables, tablets). */
@Composable
fun isWide(): Boolean {
    val c = LocalConfiguration.current
    return c.screenWidthDp > c.screenHeightDp || c.screenWidthDp >= 600
}
