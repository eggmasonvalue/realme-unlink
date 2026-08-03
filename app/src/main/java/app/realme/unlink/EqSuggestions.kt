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
    )

    val suggestions: List<Suggestion> = listOf(
        Suggestion("flat", "Flat", listOf(0f, 0f, 0f, 0f, 0f, 0f)),
        Suggestion("bass", "Bass boost", listOf(1f, 0.55f, 0.1f, 0f, 0f, 0f)),
        Suggestion("warm", "Warm", listOf(0.45f, 0.35f, 0.1f, -0.1f, -0.15f, -0.2f)),
        Suggestion("vocal", "Vocal", listOf(-0.25f, -0.1f, 0.55f, 0.7f, 0.25f, 0f)),
        Suggestion("bright", "Bright", listOf(-0.15f, -0.05f, 0.1f, 0.35f, 0.65f, 0.75f)),
        Suggestion("treble", "Treble lift", listOf(0f, 0f, 0.1f, 0.35f, 0.7f, 1f)),
    )

    fun apply(
        profile: HeadsetCapabilityDiscovery.EqProfile,
        suggestion: Suggestion,
    ): HeadsetCapabilityDiscovery.EqProfile {
        val bandCount = profile.frequencies.size
        require(bandCount > 0) { "Profile has no bands" }
        val weights = suggestion.relative
        val scaled = List(bandCount) { index ->
            val weight = weights.getOrElse(index) { 0f }.coerceIn(-1f, 1f)
            val span = if (weight >= 0f) {
                profile.maxValue.toFloat()
            } else {
                -profile.minValue.toFloat()
            }
            val raw = weight * span
            raw.toInt().coerceIn(profile.minValue, profile.maxValue)
        }
        return profile.copy(dbValues = scaled, selected = true)
    }
}
