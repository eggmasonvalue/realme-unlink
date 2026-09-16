package app.realme.unlink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EqSuggestionsTest {
    @Test
    fun scalesSuggestionsIntoDeviceRangeWithoutChangingFrequencies() {
        val profile = HeadsetCapabilityDiscovery.EqProfile(
            selected = false,
            minValue = -6,
            maxValue = 6,
            id = 4,
            name = "Custom1",
            frequencies = listOf(50, 250, 1000, 4000, 8000, 16000),
            dbValues = listOf(0, 0, 0, 0, 0, 0),
        )
        val flat = EqSuggestions.apply(profile, EqSuggestions.suggestions.first { it.id == "flat" })
        assertEquals(listOf(0, 0, 0, 0, 0, 0), flat.dbValues)
        assertEquals(profile.frequencies, flat.frequencies)

        val bass = EqSuggestions.apply(profile, EqSuggestions.suggestions.first { it.id == "bass" })
        assertEquals(6, bass.dbValues[0])
        assertTrueInRange(bass.dbValues, -6, 6)
    }

    @Test
    fun recognizesPersistedPresetValuesUntilTheyAreModified() {
        val profile = HeadsetCapabilityDiscovery.EqProfile(
            selected = true,
            minValue = -6,
            maxValue = 6,
            id = 4,
            name = "Custom",
            frequencies = listOf(50, 250, 1000, 4000, 8000, 16000),
            dbValues = listOf(0, 0, 0, 0, 0, 0),
        )
        val suggestion = EqSuggestions.suggestions.first { it.id == "harman_balanced" }
        val applied = EqSuggestions.apply(profile, suggestion)

        assertEquals(suggestion.id, EqSuggestions.matchingSuggestion(applied)?.id)
        assertNull(
            EqSuggestions.matchingSuggestion(
                applied.copy(dbValues = applied.dbValues.toMutableList().also { it[0] += 1 }),
            ),
        )
    }

    private fun assertTrueInRange(values: List<Int>, min: Int, max: Int) {
        values.forEach { value ->
            assertEquals("out of range: $value", true, value in min..max)
        }
    }
}
