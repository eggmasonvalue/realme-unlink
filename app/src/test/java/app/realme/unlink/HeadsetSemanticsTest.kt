package app.realme.unlink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeadsetSemanticsTest {
    @Test
    fun mapsConfirmedNoiseValuesWithoutHidingUnknowns() {
        assertEquals(
            "Strong noise cancellation",
            HeadsetSemantics.noiseValueLabel(action = 1, value = 8),
        )
        assertEquals(
            "Ambient",
            HeadsetSemantics.noiseValueLabel(action = 1, value = 2),
        )
        assertEquals(
            "Strong noise cancellation",
            HeadsetSemantics.noiseDisplayLabel(action = 1, value = 8),
        )
        assertNull(HeadsetSemantics.noiseValueLabel(action = 1, value = 99))

        assertEquals("Noise cancellation and Off", HeadsetSemantics.noiseValueLabel(action = 2, value = 3))
        assertEquals("Noise cancellation and Ambient", HeadsetSemantics.noiseValueLabel(action = 2, value = 9))
        assertEquals("Ambient and Off", HeadsetSemantics.noiseValueLabel(action = 2, value = 10))
        assertEquals("Noise cancellation, Ambient, and Off", HeadsetSemantics.noiseValueLabel(action = 2, value = 11))
        assertNull(HeadsetSemantics.noiseValueLabel(action = 2, value = 99))
    }

    @Test
    fun mapsWearMultipointAndSwitchValues() {
        assertEquals("Left", HeadsetSemantics.wearRole(1))
        assertEquals("Connected", HeadsetSemantics.multipointStateLabel(2))
        assertNull(HeadsetSemantics.switchFeatureLabel(7))
        assertEquals("Dual-device connection", HeadsetSemantics.switchFeatureLabel(17))
        assertEquals("Spatial audio", HeadsetSemantics.switchFeatureLabel(27))
        assertEquals("off", HeadsetSemantics.switchValueLabel(0))
    }

    @Test
    fun mapsNeckbandButtonsNotEarbudTouches() {
        assertEquals("Multifunction (MFB)", HeadsetSemantics.buttonControlLabel(1, 1))
        assertEquals("Volume +", HeadsetSemantics.buttonControlLabel(1, 4))
        assertEquals("Double Press", HeadsetSemantics.buttonActionLabel(2))
        assertEquals("Long Press", HeadsetSemantics.buttonActionLabel(4))
        assertEquals("Action 5", HeadsetSemantics.buttonActionLabel(5))
        assertEquals("None", HeadsetSemantics.buttonFunctionLabel(0))
        assertEquals("Play / Pause", HeadsetSemantics.buttonFunctionLabel(1))
        assertEquals("Function 2", HeadsetSemantics.buttonFunctionLabel(2))
        assertEquals("Function 3", HeadsetSemantics.buttonFunctionLabel(3))
        assertEquals("Voice Assistant", HeadsetSemantics.buttonFunctionLabel(4))
        assertEquals("Previous Track", HeadsetSemantics.buttonFunctionLabel(5))
        assertEquals("Next Track", HeadsetSemantics.buttonFunctionLabel(6))
        assertEquals("Game Mode", HeadsetSemantics.buttonFunctionLabel(17))
        assertEquals("Low", HeadsetSemantics.bassBandLabel(1))
        assertNull(HeadsetSemantics.bassBandLabel(99))
    }
}
