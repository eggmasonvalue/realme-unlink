package app.realme.unlink

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReadOnlyButtonMappingProbeTest {
    @Test
    fun diagnosticUsesTheCanonicalGetButtonMappingRequest() {
        val query = HeadsetCapabilityDiscovery.buttonMappingQuery

        assertEquals(0x0108, query.command)
        assertNotEquals(HeadsetProtocol.SET_BUTTON_MAPPING_COMMAND, query.command)
        assertArrayEquals(
            byteArrayOf(14, 1, 3, 4, 5, 6, 11, 12, 8, 13, 17, 31, 26, 27, 0),
            query.payload,
        )
    }
}
