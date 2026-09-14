package app.realme.unlink

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadsetProtocolTest {
    @Test
    fun readsAnOpPoV1WrappedTlPacket() {
        val packet = HeadsetProtocol.encode(
            command = 0x8106,
            transferId = 0x2A,
            payload = byteArrayOf(0, 1, 1, 0xC6.toByte()),
        )

        val frame = HeadsetProtocol.readFrame(
            ByteArrayInputStream(HeadsetProtocol.wrap(packet)),
        )

        assertEquals(0x8106, frame.command)
        assertEquals(0x2A, frame.transferId)
        assertArrayEquals(byteArrayOf(0, 1, 1, 0xC6.toByte()), frame.payload)
    }

    @Test
    fun fragmentsAndReassemblesAnOpPoV1Packet() {
        val packet = HeadsetProtocol.encode(
            command = 0x8123,
            transferId = 0x2A,
            payload = ByteArray(30) { it.toByte() },
        )

        val frames = HeadsetProtocol.wrapFrames(packet, maxWireFrameSize = 12)

        assertTrue(frames.size > 1)
        assertTrue(frames.all { it.size <= 12 })
        assertEquals(1, frames.first()[2].toInt() and 0x03)
        assertTrue(frames.drop(1).dropLast(1).all { it[2].toInt() and 0x03 == 2 })
        assertEquals(3, frames.last()[2].toInt() and 0x03)

        val wire = frames.fold(ByteArray(0)) { all, frame -> all + frame }
        val parsed = HeadsetProtocol.readFrame(ChunkedInputStream(wire, maxChunk = 2))

        assertArrayEquals(packet, parsed.raw())
    }

    @Test
    fun keepsSmallPacketsInOneFrame() {
        val packet = HeadsetProtocol.encode(0x0100, 0, byteArrayOf(0x01, 0x02))
        val frames = HeadsetProtocol.wrapFrames(packet, maxWireFrameSize = 12)

        assertEquals(1, frames.size)
        assertArrayEquals(HeadsetProtocol.wrap(packet), frames.single())
        assertEquals(0, frames.single()[2].toInt() and 0x03)
    }

    @Test
    fun parsesBatteryChargingBitAndLevel() {
        val frame = HeadsetProtocol.Frame(
            command = 0x8106,
            transferId = 1,
            payload = byteArrayOf(0, 1, 1, 0xC6.toByte()),
        )

        assertEquals(
            HeadsetProtocol.BatteryReading(deviceType = 1, percentage = 70, charging = true),
            HeadsetProtocol.parseBattery(frame),
        )
    }

    @Test
    fun parsesNoiseControlState() {
        val frame = HeadsetProtocol.Frame(
            command = 0x810C,
            transferId = 2,
            payload = byteArrayOf(0, 1, 1, 8),
        )

        assertEquals(
            HeadsetProtocol.NoiseControlReading(action = 1, type = 1, value = 8),
            HeadsetProtocol.parseNoiseControl(frame),
        )
    }

    @Test
    fun encodesCustomEqModifyPayload() {
        val profile = HeadsetCapabilityDiscovery.EqProfile(
            selected = false,
            minValue = -6,
            maxValue = 6,
            id = 4,
            name = "Custom1",
            frequencies = listOf(62, 250, 1000),
            dbValues = listOf(0, 1, -1),
        )

        assertArrayEquals(
            byteArrayOf(
                2, // modify
                (-6).toByte(),
                6,
                4,
                7, // name length
                'C'.code.toByte(),
                'u'.code.toByte(),
                's'.code.toByte(),
                't'.code.toByte(),
                'o'.code.toByte(),
                'm'.code.toByte(),
                '1'.code.toByte(),
                3, // band count
                62, 0, 0,
                250.toByte(), 0, 1,
                0xE8.toByte(), 0x03, (-1).toByte(),
            ),
            HeadsetProtocol.encodeCustomEqWrite(action = 2, profile = profile),
        )
    }

    @Test
    fun encodesTypedBassEngineWritePayload() {
        val value = HeadsetCapabilityDiscovery.BassEngineValue(
            minValue = -5,
            maxValue = 5,
            currentValue = 1,
            type = 2,
        )

        assertArrayEquals(
            byteArrayOf((-5).toByte(), 5, 1, 2),
            HeadsetProtocol.encodeBassEngineWrite(value),
        )
        assertArrayEquals(
            byteArrayOf((-5).toByte(), 5, 1),
            HeadsetProtocol.encodeBassEngineWrite(value.copy(type = null)),
        )
    }

    @Test
    fun encodesCustom6BandEqPayload() {
        val payload = HeadsetProtocol.encodeCustom6BandEqPayload(
            action = 2,
            id = 4,
            name = "Custom",
            minValue = -6,
            maxValue = 6,
            dbValues = listOf(-2, 0, 2, 4, 1, -1),
        )

        assertEquals(2, payload[0].toInt()) // action=2 modify
        assertEquals(-6, payload[1].toInt())
        assertEquals(6, payload[2].toInt())
        assertEquals(4, payload[3].toInt()) // id=4
        assertEquals(6, payload[4].toInt()) // name length = 6 ("Custom")
        assertEquals('C'.code.toByte(), payload[5])
        assertEquals(6, payload[11].toInt()) // 6 bands
    }

    private class ChunkedInputStream(
        private val bytes: ByteArray,
        private val maxChunk: Int,
    ) : InputStream() {
        private var offset = 0

        override fun read(): Int {
            if (offset == bytes.size) return -1
            return bytes[offset++].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, off: Int, len: Int): Int {
            if (offset == bytes.size) return -1
            val count = minOf(len, maxChunk, bytes.size - offset)
            bytes.copyInto(buffer, destinationOffset = off, startIndex = offset, endIndex = offset + count)
            offset += count
            return count
        }
    }
}
