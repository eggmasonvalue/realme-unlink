package app.realme.unlink.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Semantic haptic feedback wrapper tuned for Android 17 / Pixel linear actuators.
 *
 * Uses system [HapticFeedbackType] primitives via [LocalHapticFeedback] so feedback
 * respects user touch-feedback preferences and requires no `VIBRATE` permission.
 */
@Immutable
internal class UnlinkHaptics(
    private val feedback: HapticFeedback,
) {
    /** Binary switch or active-state toggle. */
    fun toggle(enabled: Boolean) {
        feedback.performHapticFeedback(
            if (enabled) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
        )
    }

    /** Segmented mode, preset, or discrete option selection. */
    fun select() {
        feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    /** Subtle navigation, sheet-opening, or toolbar tap. */
    fun click() {
        feedback.performHapticFeedback(HapticFeedbackType.ContextClick)
    }

    /** Explicit commitment of a multi-part or high-impact change. */
    fun confirm() {
        feedback.performHapticFeedback(HapticFeedbackType.Confirm)
    }

    /** Blocked interaction or failed/rolled-back write. */
    fun reject() {
        feedback.performHapticFeedback(HapticFeedbackType.Reject)
    }

    /** Crossing a gesture activation threshold (e.g., pull-to-refresh). */
    fun thresholdActivate() {
        feedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }

    /** Completing a drag gesture that commits a value. */
    fun gestureEnd() {
        feedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
    }

    /**
     * Discrete slider step feedback: pronounced detent at neutral 0 dB and endpoints,
     * lighter tick for intermediate steps.
     */
    fun sliderStep(value: Int, minValue: Int, maxValue: Int) {
        val type = if (value == 0 || value == minValue || value == maxValue) {
            HapticFeedbackType.SegmentTick
        } else {
            HapticFeedbackType.SegmentFrequentTick
        }
        feedback.performHapticFeedback(type)
    }
}

@Composable
internal fun rememberUnlinkHaptics(): UnlinkHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { UnlinkHaptics(feedback) }
}
