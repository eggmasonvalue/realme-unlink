package app.realme.unlink

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeadsetCapabilityDiscoveryTest {
    @Test
    fun buildsReferenceReadOnlyRequestPayloads() {
        val keyQuery = HeadsetCapabilityDiscovery.readOnlyQueries.first {
            it.command == HeadsetProtocol.KEY_FUNCTION_COMMAND
        }
        assertArrayEquals(
            byteArrayOf(14, 1, 3, 4, 5, 6, 11, 12, 8, 13, 17, 31, 26, 27, 0),
            keyQuery.payload,
        )

        val switchQuery = HeadsetCapabilityDiscovery.readOnlyQueries.first {
            it.command == HeadsetProtocol.SWITCH_FEATURE_COMMAND
        }
        assertArrayEquals(
            byteArrayOf(16, 7, 6, 4, 5, 11, 8, 17, 19, 24, 9, 26, 12, 29, 27, 54, 38),
            switchQuery.payload,
        )
    }

    @Test
    fun parsesSwitchFeatureStatuses() {
        val frame = response(
            HeadsetProtocol.SWITCH_FEATURE_COMMAND,
            byteArrayOf(0, 3, 7, 1, 26, 0, 54, 2),
        )

        assertEquals(
            listOf(
                HeadsetCapabilityDiscovery.SwitchFeatureStatus(7, 1),
                HeadsetCapabilityDiscovery.SwitchFeatureStatus(26, 0),
                HeadsetCapabilityDiscovery.SwitchFeatureStatus(54, 2),
            ),
            HeadsetCapabilityDiscovery.parseSwitchFeatureStatus(frame),
        )
    }

    @Test
    fun parsesEqProfilesAndSignedBandValues() {
        val payload = byteArrayOf(
            0, 1,
            1, 0xF4.toByte(), 12, 2, 4,
            *"Rock".toByteArray(StandardCharsets.UTF_8),
            2,
            50, 0, 1,
            0xFA.toByte(), 0, 0xFF.toByte(),
        )
        val profiles = HeadsetCapabilityDiscovery.parseEqProfiles(
            response(HeadsetProtocol.ALL_EQ_COMMAND, payload),
        )

        assertEquals(
            listOf(
                HeadsetCapabilityDiscovery.EqProfile(
                    selected = true,
                    minValue = -12,
                    maxValue = 12,
                    id = 2,
                    name = "Rock",
                    frequencies = listOf(50, 250),
                    dbValues = listOf(1, -1),
                ),
            ),
            profiles,
        )
    }

    @Test
    fun parsesWearFlagsWithRoleAndCoverSemantics() {
        val wear = HeadsetCapabilityDiscovery.parseWearStatus(
            response(
                HeadsetProtocol.WEAR_STATUS_COMMAND,
                byteArrayOf(0, 3, 1, 1, 2, 2, 3, 4),
            ),
        )

        assertEquals(
            listOf(
                HeadsetCapabilityDiscovery.WearStatus(1, 1, inBox = false, inEar = false, coverClosed = true),
                HeadsetCapabilityDiscovery.WearStatus(2, 2, inBox = true, inEar = true, coverClosed = true),
                HeadsetCapabilityDiscovery.WearStatus(3, 4, inBox = true, inEar = false, coverClosed = false),
            ),
            wear,
        )
    }

    @Test
    fun excludesTheRepeatedlyUnusableBassQueryFromDiscovery() {
        assertEquals(
            listOf(HeadsetProtocol.BASS_ENGINE_COMMAND),
            HeadsetCapabilityDiscovery.excludedFromReadOnlyDiscovery.map { it.command },
        )
        assertEquals(
            emptyList<Int>(),
            HeadsetCapabilityDiscovery.readOnlyQueries
                .filter { it.command == HeadsetProtocol.BASS_ENGINE_COMMAND },
        )
    }

    @Test
    fun parsesBassAndMultipointWithoutRetainingAddress() {
        assertEquals(
            HeadsetCapabilityDiscovery.BassEngineValue(-5, 5, 2, 1),
            HeadsetCapabilityDiscovery.parseBassEngine(
                response(HeadsetProtocol.BASS_ENGINE_COMMAND, byteArrayOf(0, -5, 5, 2, 1)),
            ),
        )

        val multipointPayload = byteArrayOf(
            0, 1,
            1, 2, 3, 4, 5, 6,
            4, 2, 1, 1, 'A'.code.toByte(),
        )
        assertEquals(
            listOf(HeadsetCapabilityDiscovery.MultipointEntry(state = 2, flags = 1, name = "A")),
            HeadsetCapabilityDiscovery.parseMultipointStatus(
                response(HeadsetProtocol.MULTI_CONNECT_COMMAND, multipointPayload),
            ),
        )
    }

    @Test
    fun rejectsFailedOrMalformedResponses() {
        assertNull(
            HeadsetCapabilityDiscovery.parseEqStatus(
                response(HeadsetProtocol.EQ_STATUS_COMMAND, byteArrayOf(1, 2)),
            ),
        )
        assertNull(
            HeadsetCapabilityDiscovery.parseKeyMappings(
                response(HeadsetProtocol.KEY_FUNCTION_COMMAND, byteArrayOf(0, 1, 1)),
            ),
        )
    }

    private fun response(command: Int, payload: ByteArray): HeadsetProtocol.Frame {
        return HeadsetProtocol.Frame(
            command = command or 0x8000,
            transferId = 1,
            payload = payload,
        )
    }
}
