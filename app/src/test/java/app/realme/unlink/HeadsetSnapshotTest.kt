package app.realme.unlink

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadsetSnapshotTest {
    private val custom = HeadsetCapabilityDiscovery.EqProfile(
        selected = false,
        minValue = -6,
        maxValue = 6,
        id = 4,
        name = "Custom",
        frequencies = listOf(50, 250, 1000, 4000, 8000, 16000),
        dbValues = List(6) { 0 },
    )

    @Test
    fun customEqRequiresAdvertisedCommandsAndHeadsetProfile() {
        assertTrue(snapshot(eqProfiles = listOf(custom)).canEditCustomEq)
        assertFalse(snapshot(eqProfiles = emptyList()).canEditCustomEq)
        assertFalse(
            snapshot(
                eqProfiles = listOf(custom),
                readFailures = setOf(HeadsetProtocol.ALL_EQ_COMMAND),
            ).canEditCustomEq,
        )
        assertFalse(
            snapshot(
                eqProfiles = listOf(custom),
                readFailures = setOf(HeadsetProtocol.EQ_STATUS_COMMAND),
            ).canEditCustomEq,
        )
    }

    @Test
    fun customEqValidationPreservesHeadsetSchemaAndRange() {
        val snapshot = snapshot(eqProfiles = listOf(custom))
        val validated = snapshot.requireCompatibleCustomEq(
            custom.copy(name = "Injected", minValue = -100, maxValue = 100, dbValues = List(6) { 2 }),
        )
        assertEquals(custom.name, validated.name)
        assertEquals(custom.minValue, validated.minValue)
        assertEquals(custom.maxValue, validated.maxValue)
        assertEquals(List(6) { 2 }, validated.dbValues)

        assertThrows(IllegalArgumentException::class.java) {
            snapshot.requireCompatibleCustomEq(custom.copy(frequencies = listOf(100, 200)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            snapshot.requireCompatibleCustomEq(custom.copy(dbValues = listOf(7, 0, 0, 0, 0, 0)))
        }
    }

    @Test
    fun resolvesCurrentEqLabelForStandardPresetsAndCustom() {
        assertEquals("Nature Balance", snapshot(eqProfiles = emptyList(), currentEqId = 0).currentEqLabel)
        assertEquals("Bass Boost", snapshot(eqProfiles = emptyList(), currentEqId = 1).currentEqLabel)
        assertEquals("Clear Vocals", snapshot(eqProfiles = emptyList(), currentEqId = 2).currentEqLabel)
        assertEquals("Clear Bass", snapshot(eqProfiles = emptyList(), currentEqId = 3).currentEqLabel)
        assertEquals("Custom", snapshot(eqProfiles = emptyList(), currentEqId = 4).currentEqLabel)
        assertEquals("Profile 99", snapshot(eqProfiles = emptyList(), currentEqId = 99).currentEqLabel)
        assertEquals("Unknown", snapshot(eqProfiles = emptyList(), currentEqId = null).currentEqLabel)

        val customProfile = custom.copy(id = 4, name = "My EQ")
        assertEquals("My EQ", snapshot(eqProfiles = listOf(customProfile), currentEqId = 4).currentEqLabel)
    }

    @Test
    fun resolvesSpatialAudioAndHighVolumeAndNoiseCycle() {
        val spatialSwitch = SwitchCatalog.LogicalSwitch(
            id = 27,
            label = "Spatial audio",
            enabled = true,
        )
        val cycle = HeadsetProtocol.NoiseControlReading(action = 2, type = 1, value = 9)

        val snap = snapshot(
            eqProfiles = emptyList(),
            switches = listOf(spatialSwitch),
            highVolumeEnabled = true,
            noiseCycle = cycle,
        )

        assertEquals(true, snap.spatialAudioEnabled)
        assertEquals(true, snap.highVolumeEnabled)
        assertEquals(cycle, snap.noiseCycle)
    }

    private fun snapshot(
        eqProfiles: List<HeadsetCapabilityDiscovery.EqProfile>,
        readFailures: Set<Int> = emptySet(),
        currentEqId: Int? = 0,
        switches: List<SwitchCatalog.LogicalSwitch> = emptyList(),
        highVolumeEnabled: Boolean? = null,
        noiseCycle: HeadsetProtocol.NoiseControlReading? = null,
    ) = HeadsetSnapshot(
        deviceName = DeviceSupport.DISPLAY_NAME,
        serviceLabel = "test",
        firmwareVersion = null,
        batteries = emptyList(),
        noise = null,
        currentEqId = currentEqId,
        eqProfiles = eqProfiles,
        bassBands = emptyList(),
        switches = switches,
        buttonMappings = emptyList(),
        wear = emptyList(),
        multipoint = emptyList(),
        capabilities = HeadsetCapabilities(
            rawBitmap = byteArrayOf(),
            setBits = setOf(10, 34),
            advertisedGroups = HeadsetCapabilities.knownGroups.filter { it.bit == 10 || it.bit == 34 },
            unknownBits = emptySet(),
        ),
        readFailures = readFailures,
        highVolumeEnabled = highVolumeEnabled,
        noiseCycle = noiseCycle,
    )
}
