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
            "Transparency",
            HeadsetSemantics.noiseValueLabel(action = 1, value = 2),
        )
        assertEquals(
            "Strong noise cancellation",
            HeadsetSemantics.noiseDisplayLabel(action = 1, value = 8),
        )
        assertNull(HeadsetSemantics.noiseValueLabel(action = 1, value = 99))
    }

    @Test
    fun mapsWearMultipointAndSwitchValues() {
        assertEquals("Left", HeadsetSemantics.wearRole(1))
        assertEquals("Connected", HeadsetSemantics.multipointStateLabel(2))
        assertEquals("Party mode", HeadsetSemantics.switchFeatureLabel(7))
        assertEquals("Dual Connection (Multipoint)", HeadsetSemantics.switchFeatureLabel(17))
        assertEquals("off", HeadsetSemantics.switchValueLabel(0))
    }

    @Test
    fun mapsNeckbandButtonsNotEarbudTouches() {
        assertEquals("Multifunction (MFB)", HeadsetSemantics.buttonControlLabel(1, 1))
        assertEquals("Volume +", HeadsetSemantics.buttonControlLabel(1, 4))
        assertEquals("Double Press", HeadsetSemantics.buttonActionLabel(2))
        assertEquals("Play / Pause", HeadsetSemantics.buttonFunctionLabel(1))
        assertEquals("Low", HeadsetSemantics.bassBandLabel(1))
        assertNull(HeadsetSemantics.bassBandLabel(99))
    }
}
