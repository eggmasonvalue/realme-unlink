package app.realme.unlink

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadsetCapabilitiesTest {
    @Test
    fun parsesLiveBitmapLeastSignificantBitFirst() {
        val capabilities = HeadsetCapabilities.parse(
            capabilityFrame(payload = byteArrayOf(0, 0xBF.toByte(), 0x1F, 0xDA.toByte(), 0x20, 0x45, 0x04, 0x40, 0)),
        )

        requireNotNull(capabilities)
        assertArrayEquals(
            byteArrayOf(0xBF.toByte(), 0x1F, 0xDA.toByte(), 0x20, 0x45, 0x04, 0x40, 0),
            capabilities.rawBitmap,
        )
        assertEquals(
            listOf(
                0, 1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 12,
                17, 19, 20, 22, 23, 29, 32, 34, 38, 42, 54,
            ),
            capabilities.setBits.toList(),
        )
        assertEquals(setOf(12, 17, 20, 42), capabilities.unknownBits)
        assertEquals(
            setOf(0, 1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 19, 22, 23, 29, 32, 34, 38, 54),
            capabilities.advertisedGroups.map { it.bit }.toSet(),
        )
        assertTrue(capabilities.supportsCommand(HeadsetProtocol.BATTERY_COMMAND))
        assertTrue(capabilities.supportsCommand(HeadsetProtocol.NOISE_LEVEL_COMMAND))
        assertTrue(capabilities.supportsCommand(HeadsetProtocol.SET_NOISE_REDUCTION_COMMAND))
        assertFalse(capabilities.supportsCommand(0x041E))
    }

    @Test
    fun rejectsCapabilityResponseWithoutBitmap() {
        assertNull(HeadsetCapabilities.parse(capabilityFrame(payload = byteArrayOf(0))))
        assertNull(HeadsetCapabilities.parse(capabilityFrame(payload = byteArrayOf())))
    }

    @Test
    fun rejectsCapabilityResponseWithFailureStatus() {
        assertNull(
            HeadsetCapabilities.parse(
                capabilityFrame(payload = byteArrayOf(1, 0x01)),
            ),
        )
    }

    @Test
    fun rejectsWrongCommandAndNonResponseFrames() {
        assertNull(
            HeadsetCapabilities.parse(
                HeadsetProtocol.Frame(
                    command = 0x8106,
                    transferId = 0,
                    payload = byteArrayOf(0, 0x02),
                ),
            ),
        )
        assertNull(
            HeadsetCapabilities.parse(
                HeadsetProtocol.Frame(
                    command = HeadsetProtocol.CAPABILITY_COMMAND,
                    transferId = 0,
                    payload = byteArrayOf(0, 0x02),
                ),
            ),
        )
    }

    private fun capabilityFrame(payload: ByteArray): HeadsetProtocol.Frame {
        return HeadsetProtocol.Frame(
            command = 0x8100,
            transferId = 0,
            payload = payload,
        )
    }
}
