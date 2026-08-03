package app.realme.unlink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitchCatalogTest {
    @Test
    fun collapsesGamingModeIdsIntoOneLogicalSwitch() {
        val logical = SwitchCatalog.logicalSwitches(
            listOf(
                HeadsetCapabilityDiscovery.SwitchFeatureStatus(6, 0),
                HeadsetCapabilityDiscovery.SwitchFeatureStatus(8, 1),
                HeadsetCapabilityDiscovery.SwitchFeatureStatus(9, 0),
                HeadsetCapabilityDiscovery.SwitchFeatureStatus(4, 0), // rejected id — hide
            ),
        )
        assertEquals(2, logical.size)
        val gaming = logical.first { it.label == "Gaming mode" }
        assertEquals(SwitchCatalog.GAMING_PRIMARY_ID, gaming.id)
        assertTrue(gaming.enabled)
        assertEquals(setOf(6), gaming.aliases)
        assertEquals("Voice enhancement", logical[1].label)
    }

    @Test
    fun omitsRejectedSwitchIds() {
        val logical = SwitchCatalog.logicalSwitches(
            listOf(
                HeadsetCapabilityDiscovery.SwitchFeatureStatus(5, 1),
                HeadsetCapabilityDiscovery.SwitchFeatureStatus(26, 1),
            ),
        )
        assertEquals(listOf(26), logical.map { it.id })
    }
}
