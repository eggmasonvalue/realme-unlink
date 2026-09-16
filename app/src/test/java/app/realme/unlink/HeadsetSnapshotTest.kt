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

    private fun snapshot(
        eqProfiles: List<HeadsetCapabilityDiscovery.EqProfile>,
        readFailures: Set<Int> = emptySet(),
    ) = HeadsetSnapshot(
        deviceName = DeviceSupport.DISPLAY_NAME,
        serviceLabel = "test",
        firmwareVersion = null,
        batteries = emptyList(),
        noise = null,
        currentEqId = 0,
        eqProfiles = eqProfiles,
        bassBands = emptyList(),
        switches = emptyList(),
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
    )
}
