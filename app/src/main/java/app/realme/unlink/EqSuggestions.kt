package app.realme.unlink

/**
 * App-side EQ curves for the device's custom slot. Frequencies and dB range must come from the
 * headset profile; suggestions only supply relative shapes that are scaled into that range.
 */
object EqSuggestions {
    data class Suggestion(
        val id: String,
        val name: String,
        /** Relative weights per band index; scaled into the profile's min/max. */
        val relative: List<Float>,
        /** Exact dB values per band index if specified for a fixed-range profile. */
        val exactDb: List<Int>? = null,
    )

    val suggestions: List<Suggestion> = listOf(
        Suggestion("pump_power", "Pump & Power", listOf(0.667f, 0.5f, 0f, 0.333f, 0.167f, 0f), listOf(4, 3, 0, 2, 1, 0)),
        Suggestion("harman_balanced", "Harman Balanced", listOf(0.333f, -0.167f, 0.167f, 0.167f, 0.333f, 0.167f), listOf(2, -1, 1, 1, 2, 1)),
        Suggestion("voice_dialogue", "Voice & Dialogue", listOf(-0.5f, -0.333f, 0.333f, 0.667f, 0.167f, 0f), listOf(-3, -2, 2, 4, 1, 0)),
        Suggestion("flat", "Flat (0 dB)", listOf(0f, 0f, 0f, 0f, 0f, 0f), listOf(0, 0, 0, 0, 0, 0)),
    )

    fun matchingSuggestion(
        profile: HeadsetCapabilityDiscovery.EqProfile,
    ): Suggestion? = suggestions.firstOrNull { suggestion ->
        apply(profile, suggestion).dbValues == profile.dbValues
    }

    fun apply(
        profile: HeadsetCapabilityDiscovery.EqProfile,
        suggestion: Suggestion,
    ): HeadsetCapabilityDiscovery.EqProfile {
        val bandCount = profile.frequencies.size
        require(bandCount > 0) { "Profile has no bands" }
        val scaled = if (suggestion.exactDb != null) {
            suggestion.exactDb.map { it.coerceIn(profile.minValue, profile.maxValue) }
        } else {
            val weights = suggestion.relative
            List(bandCount) { index ->
                val weight = weights.getOrElse(index) { 0f }.coerceIn(-1f, 1f)
                val span = if (weight >= 0f) {
                    profile.maxValue.toFloat()
                } else {
                    -profile.minValue.toFloat()
                }
                val raw = weight * span
                raw.toInt().coerceIn(profile.minValue, profile.maxValue)
            }
        }
        return profile.copy(dbValues = scaled, selected = true)
    }
}
