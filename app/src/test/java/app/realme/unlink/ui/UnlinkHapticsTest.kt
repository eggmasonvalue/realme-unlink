package app.realme.unlink.ui

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import org.junit.Assert.assertEquals
import org.junit.Test

class UnlinkHapticsTest {
    private class RecordingHapticFeedback : HapticFeedback {
        val events = mutableListOf<HapticFeedbackType>()

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            events += hapticFeedbackType
        }
    }

    @Test
    fun toggleMapsOnAndOffDistinctly() {
        val recorder = RecordingHapticFeedback()
        val haptics = UnlinkHaptics(recorder)

        haptics.toggle(true)
        haptics.toggle(false)

        assertEquals(
            listOf(HapticFeedbackType.ToggleOn, HapticFeedbackType.ToggleOff),
            recorder.events,
        )
    }

    @Test
    fun semanticActionsEmitExpectedPrimitives() {
        val recorder = RecordingHapticFeedback()
        val haptics = UnlinkHaptics(recorder)

        haptics.select()
        haptics.click()
        haptics.confirm()
        haptics.reject()
        haptics.thresholdActivate()
        haptics.gestureEnd()

        assertEquals(
            listOf(
                HapticFeedbackType.SegmentTick,
                HapticFeedbackType.ContextClick,
                HapticFeedbackType.Confirm,
                HapticFeedbackType.Reject,
                HapticFeedbackType.GestureThresholdActivate,
                HapticFeedbackType.GestureEnd,
            ),
            recorder.events,
        )
    }

    @Test
    fun sliderStepEmitsStrongerDetentAtZeroAndBounds() {
        val recorder = RecordingHapticFeedback()
        val haptics = UnlinkHaptics(recorder)

        haptics.sliderStep(value = -6, minValue = -6, maxValue = 6)
        haptics.sliderStep(value = -3, minValue = -6, maxValue = 6)
        haptics.sliderStep(value = 0, minValue = -6, maxValue = 6)
        haptics.sliderStep(value = 4, minValue = -6, maxValue = 6)
        haptics.sliderStep(value = 6, minValue = -6, maxValue = 6)

        assertEquals(
            listOf(
                HapticFeedbackType.SegmentTick,
                HapticFeedbackType.SegmentFrequentTick,
                HapticFeedbackType.SegmentTick,
                HapticFeedbackType.SegmentFrequentTick,
                HapticFeedbackType.SegmentTick,
            ),
            recorder.events,
        )
    }
}
