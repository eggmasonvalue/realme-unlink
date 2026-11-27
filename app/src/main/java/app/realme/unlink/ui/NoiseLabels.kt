package app.realme.unlink.ui

import app.realme.unlink.HeadsetSemantics

/** Compact chip labels for noise modes (action/type 1/1), product-facing. */
object NoiseLabels {
    data class Mode(val value: Int, val shortLabel: String, val fullLabel: String)

    val modes: List<Mode> = listOf(
        Mode(1, "Off", "Off"),
        Mode(2, "Ambient", "Ambient"),
        Mode(4, "Mild", "Mild noise cancellation"),
        Mode(16, "Moderate", "Moderate noise cancellation"),
        Mode(8, "Strong", "Strong noise cancellation"),
        Mode(32, "Smart", "Smart noise cancellation"),
    )

    fun shortLabel(value: Int): String =
        modes.firstOrNull { it.value == value }?.shortLabel
            ?: HeadsetSemantics.noiseValueLabel(1, value)
            ?: "Mode $value"
}
