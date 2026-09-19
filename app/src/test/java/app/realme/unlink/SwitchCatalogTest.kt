package app.realme.unlink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitchCatalogTest {
    @Test
    fun keepsGameModeAndAutoAnswerAsIndependentSwitches() {
        val logical = SwitchCatalog.logicalSwitches(
            listOf(
                HeadsetCapabilityDiscovery.SwitchFeatureStatus(6, 0),
                HeadsetCapabilityDiscovery.SwitchFeatureStatus(8, 1),
                HeadsetCapabilityDiscovery.SwitchFeatureStatus(9, 1), // unresolved — hide
            ),
        )

        assertEquals(listOf(6, 8), logical.map { it.id })
        assertEquals("Game mode", logical[0].label)
        assertFalse(logical[0].enabled)
        assertEquals("Auto answer", logical[1].label)
        assertTrue(logical[1].enabled)
    }

    @Test
    fun includesValidatedSwitchesAndOmitsUnvalidatedSwitchIds() {
        val logical = SwitchCatalog.logicalSwitches(
            listOf(
                HeadsetCapabilityDiscovery.SwitchFeatureStatus(5, 1),
                HeadsetCapabilityDiscovery.SwitchFeatureStatus(9, 1),
                HeadsetCapabilityDiscovery.SwitchFeatureStatus(31, 1),
                HeadsetCapabilityDiscovery.SwitchFeatureStatus(27, 1),
                HeadsetCapabilityDiscovery.SwitchFeatureStatus(26, 1),
            ),
        )
        assertEquals(listOf(26, 27), logical.map { it.id })
        assertEquals("Smart de-wind", logical[0].label)
        assertEquals("Spatial audio", logical[1].label)
        assertTrue(logical[1].enabled)
    }
}
