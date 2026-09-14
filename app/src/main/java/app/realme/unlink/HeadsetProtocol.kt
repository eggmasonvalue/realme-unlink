package app.realme.unlink

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/** The small, framing-only portion of the realme TL headset protocol. */
object HeadsetProtocol {
    const val CAPABILITY_COMMAND = 0x0100
    const val VERSION_COMMAND = 0x0105
    const val BATTERY_COMMAND = 0x0106
    const val KEY_FUNCTION_COMMAND = 0x0108
    const val WEAR_STATUS_COMMAND = 0x0109
    const val NOISE_LEVEL_COMMAND = 0x010C
    const val SWITCH_FEATURE_COMMAND = 0x010D
    const val EQ_STATUS_COMMAND = 0x010F
    const val MULTI_CONNECT_COMMAND = 0x0112
    const val HEARING_ENHANCEMENT_COMMAND = 0x0115
    const val ALL_EQ_COMMAND = 0x0122
    const val BASS_ENGINE_COMMAND = 0x0124
    const val MULTI_BASS_ENGINE_COMMAND = 0x012C
    const val SET_FIND_MODE_COMMAND = 0x0400
    const val SET_BUTTON_MAPPING_COMMAND = 0x0401
    const val SET_SWITCH_FEATURE_COMMAND = 0x0403
    const val SET_NOISE_REDUCTION_COMMAND = 0x0404
    const val SET_EQ_STATUS_COMMAND = 0x0406
    const val SET_CUSTOM_EQ_COMMAND = 0x0418
    const val SET_BASS_ENGINE_COMMAND = 0x041B
    private const val RESPONSE_MASK = 0x8000
    private const val HEADER_LENGTH = 5
    private const val MAX_PAYLOAD_LENGTH = 8 * 1024
    private const val DEFAULT_MAX_WIRE_FRAME_SIZE = 2000
    private const val MIN_FRAGMENT_FRAME_SIZE = 6

    val controlUuid: UUID = UUID.fromString("0000079A-D102-11E1-9B23-00025B00A5A5")
    val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    data class Frame(
        val command: Int,
        val transferId: Int,
        val payload: ByteArray,
    ) {
        val isResponse: Boolean
            get() = command and RESPONSE_MASK != 0

        val baseCommand: Int
            get() = command and RESPONSE_MASK.inv()

        fun raw(): ByteArray = encode(command, transferId, payload)
    }

    data class BatteryReading(
        val deviceType: Int,
        val percentage: Int,
        val charging: Boolean,
    )

    data class NoiseControlReading(
        val action: Int,
        val type: Int,
        val value: Int,
    )

    fun encode(command: Int, transferId: Int, payload: ByteArray = byteArrayOf()): ByteArray {
        require(command in 0..0xFFFF) { "Command must fit in 16 bits" }
        require(transferId in 0..0xFF) { "Transfer ID must fit in 8 bits" }
        require(payload.size <= 0xFFFF) { "Payload is too large" }

        val buffer = ByteBuffer.allocate(HEADER_LENGTH + payload.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.putShort(command.toShort())
        buffer.put(transferId.toByte())
        buffer.putShort(payload.size.toShort())
        buffer.put(payload)
        return buffer.array()
    }

    /**
     * Wraps a small TL packet in one OPPOv1 transport envelope. Call [wrapFrames] for packets
     * that need first/middle/final fragmentation.
     */
    fun wrap(packet: ByteArray): ByteArray {
        val frames = wrapFrames(packet)
        require(frames.size == 1) {
            "TL packet requires ${frames.size} OPPOv1 frames; use wrapFrames()"
        }
        return frames.single()
    }

    /**
     * Wraps a TL packet in OPPOv1 envelope frames.
     *
     * The vendor wrapper uses a two-byte control/reserved header for a single frame and adds a
     * one-byte frame sequence number to fragmented frames. [maxWireFrameSize] follows the vendor
     * implementation's maximum complete wire-frame size (2000 bytes by default), including the
     * start byte and length varint.
     */
    fun wrapFrames(
        packet: ByteArray,
        maxWireFrameSize: Int = DEFAULT_MAX_WIRE_FRAME_SIZE,
    ): List<ByteArray> {
        require(maxWireFrameSize >= MIN_FRAGMENT_FRAME_SIZE) {
            "OPPOv1 frame size must be at least $MIN_FRAGMENT_FRAME_SIZE bytes"
        }

        val singlePayloadLimit = maxPayloadForFrame(maxWireFrameSize, headerSize = 2)
        if (packet.size <= singlePayloadLimit) {
            return listOf(encodeTransportFrame(control = 0, sequence = null, payload = packet))
        }

        val fragmentPayloadLimit = maxPayloadForFrame(maxWireFrameSize, headerSize = 3)
        require(fragmentPayloadLimit > 0) {
            "OPPOv1 frame size cannot carry a fragmented payload"
        }

        val frames = mutableListOf<ByteArray>()
        var offset = 0
        var sequence = 0
        while (offset < packet.size) {
            val remaining = packet.size - offset
            val chunkSize = minOf(remaining, fragmentPayloadLimit)
            val control = when {
                offset == 0 -> 1 // first
                remaining == chunkSize -> 3 // final
                else -> 2 // middle
            }
            val chunk = packet.copyOfRange(offset, offset + chunkSize)
            frames += encodeTransportFrame(control, sequence, chunk)
            offset += chunkSize
            sequence = (sequence + 1) and 0xFF
        }
        return frames
    }

    fun readFrame(input: InputStream): Frame {
        return parsePacket(readTransportPacket(input))
    }

    private fun parsePacket(packet: ByteArray): Frame {
        require(packet.size >= HEADER_LENGTH) { "TL packet is shorter than its header" }
        val command = (packet[0].toInt() and 0xFF) or
            ((packet[1].toInt() and 0xFF) shl 8)
        val transferId = packet[2].toInt() and 0xFF
        val length = (packet[3].toInt() and 0xFF) or
            ((packet[4].toInt() and 0xFF) shl 8)
        require(length <= MAX_PAYLOAD_LENGTH) {
            "Refusing oversized headset payload: $length bytes"
        }
        require(packet.size == HEADER_LENGTH + length) {
            "TL payload length does not match its packet"
        }
        return Frame(command, transferId, packet.copyOfRange(HEADER_LENGTH, packet.size))
    }

    /**
     * Reads and reassembles one OPPOv1 envelope. RFCOMM may split this into arbitrary chunks;
     * InputStream reads are intentionally not treated as packet boundaries.
     */
    private fun readTransportPacket(input: InputStream): ByteArray {
        val stream = DataInputStream(input)
        val assembled = ByteArrayOutputStream()
        var firstFrame = true

        while (true) {
            require(stream.readUnsignedByte() == 0xAA) { "Invalid OPPOv1 start byte" }
            val linkDataLength = readVarint(stream)
            require(linkDataLength in 2..(MAX_PAYLOAD_LENGTH + 64)) {
                "Invalid OPPOv1 frame length: $linkDataLength"
            }

            val body = ByteArray(linkDataLength)
            stream.readFully(body)
            val frameType = body[0].toInt() and 0x03
            val payloadOffset = if (frameType == 0) 2 else 3
            require(body.size >= payloadOffset) { "OPPOv1 frame has no payload" }

            when (frameType) {
                0 -> return body.copyOfRange(payloadOffset, body.size)
                1 -> {
                    assembled.reset()
                    assembled.write(body, payloadOffset, body.size - payloadOffset)
                    firstFrame = false
                }
                2 -> {
                    require(!firstFrame) { "OPPOv1 continuation without a first frame" }
                    assembled.write(body, payloadOffset, body.size - payloadOffset)
                }
                3 -> {
                    require(!firstFrame) { "OPPOv1 final frame without a first frame" }
                    assembled.write(body, payloadOffset, body.size - payloadOffset)
                    return assembled.toByteArray()
                }
            }
        }
    }

    private fun encodeTransportFrame(
        control: Int,
        sequence: Int?,
        payload: ByteArray,
    ): ByteArray {
        require(control in 0..3) { "OPPOv1 control must fit in two bits" }
        require(sequence == null || sequence in 0..0xFF) {
            "OPPOv1 sequence must fit in one byte"
        }
        val headerSize = if (sequence == null) 2 else 3
        val linkDataLength = payload.size + headerSize
        val length = encodeVarint(linkDataLength)
        return ByteArray(1 + length.size + linkDataLength).also { frame ->
            frame[0] = 0xAA.toByte()
            length.copyInto(frame, destinationOffset = 1)
            val bodyOffset = 1 + length.size
            frame[bodyOffset] = control.toByte()
            frame[bodyOffset + 1] = 0 // reserved byte used by the vendor wrapper
            var payloadOffset = bodyOffset + 2
            if (sequence != null) {
                frame[payloadOffset] = sequence.toByte()
                payloadOffset++
            }
            payload.copyInto(frame, destinationOffset = payloadOffset)
        }
    }

    /** Returns the largest payload that fits in a complete wire frame. */
    private fun maxPayloadForFrame(maxWireFrameSize: Int, headerSize: Int): Int {
        var low = 0
        var high = maxWireFrameSize
        while (low < high) {
            val candidate = (low + high + 1) / 2
            val wireSize = 1 + encodeVarint(headerSize + candidate).size + headerSize + candidate
            if (wireSize <= maxWireFrameSize) {
                low = candidate
            } else {
                high = candidate - 1
            }
        }
        return low
    }

    private fun readVarint(stream: DataInputStream): Int {
        var value = 0
        var shift = 0
        repeat(4) {
            val next = stream.readUnsignedByte()
            value = value or ((next and 0x7F) shl shift)
            if (next and 0x80 == 0) return value
            shift += 7
        }
        error("OPPOv1 length varint is too long")
    }

    private fun encodeVarint(value: Int): ByteArray {
        var remaining = value
        val encoded = ByteArrayOutputStream()
        do {
            var next = remaining and 0x7F
            remaining = remaining ushr 7
            if (remaining != 0) next = next or 0x80
            encoded.write(next)
        } while (remaining != 0)
        return encoded.toByteArray()
    }

    fun parseNoiseControl(frame: Frame): NoiseControlReading? {
        if (frame.payload.size < 4) return null
        if (frame.payload[0].toInt() and 0xFF != 0) return null
        return NoiseControlReading(
            action = frame.payload[1].toInt() and 0xFF,
            type = frame.payload[2].toInt() and 0xFF,
            value = frame.payload[3].toInt() and 0xFF,
        )
    }

    fun parseSetStatus(frame: Frame): Int? {
        return frame.payload.firstOrNull()?.toInt()?.and(0xFF)
    }

    /** Builds a custom-EQ write payload matching the vendor modify/add/delete envelope. */
    fun encodeCustomEqWrite(
        action: Int,
        profile: HeadsetCapabilityDiscovery.EqProfile,
    ): ByteArray {
        require(action in 1..3) { "Custom EQ action must be add(1), modify(2), or delete(3)" }
        require(profile.id in 0..0xFF) { "EQ ID must fit in 8 bits" }
        require(profile.frequencies.size == profile.dbValues.size) {
            "Frequency and dB arrays must match"
        }
        require(profile.frequencies.isNotEmpty()) { "At least one EQ band is required" }
        require(profile.frequencies.size <= 0xFF) { "Too many EQ bands" }
        val nameBytes = profile.name.toByteArray(Charsets.UTF_8)
        require(nameBytes.size <= 0xFF) { "EQ name is too long" }
        val payload = ByteArray(6 + nameBytes.size + profile.frequencies.size * 3)
        var offset = 0
        payload[offset++] = action.toByte()
        payload[offset++] = profile.minValue.toByte()
        payload[offset++] = profile.maxValue.toByte()
        payload[offset++] = profile.id.toByte()
        payload[offset++] = nameBytes.size.toByte()
        nameBytes.copyInto(payload, destinationOffset = offset)
        offset += nameBytes.size
        payload[offset++] = profile.frequencies.size.toByte()
        profile.frequencies.zip(profile.dbValues).forEach { (frequency, db) ->
            require(frequency in 0..0xFFFF) { "Frequency must fit in 16 bits" }
            require(db in -128..127) { "dB value must fit in a signed byte" }
            payload[offset++] = (frequency and 0xFF).toByte()
            payload[offset++] = ((frequency ushr 8) and 0xFF).toByte()
            payload[offset++] = db.toByte()
        }
        return payload
    }

    val DEFAULT_CUSTOM_EQ_FREQUENCIES = listOf(50, 250, 1000, 4000, 8000, 16000)

    /** Builds a custom 6-band EQ payload for bands 50Hz, 250Hz, 1000Hz, 4000Hz, 8000Hz, 16000Hz. */
    fun encodeCustom6BandEqPayload(
        action: Int = 2,
        id: Int = 4,
        name: String = "Custom",
        minValue: Int = -6,
        maxValue: Int = 6,
        dbValues: List<Int>,
    ): ByteArray {
        require(dbValues.size == DEFAULT_CUSTOM_EQ_FREQUENCIES.size) {
            "6 dB values are required for 6-band EQ"
        }
        val profile = HeadsetCapabilityDiscovery.EqProfile(
            selected = true,
            minValue = minValue,
            maxValue = maxValue,
            id = id,
            name = name,
            frequencies = DEFAULT_CUSTOM_EQ_FREQUENCIES,
            dbValues = dbValues,
        )
        return encodeCustomEqWrite(action, profile)
    }

    /** Builds a bass-engine write payload. Typed multi-band values include the trailing type byte. */
    fun encodeBassEngineWrite(value: HeadsetCapabilityDiscovery.BassEngineValue): ByteArray {
        require(value.minValue in -128..127) { "Bass min must fit in a signed byte" }
        require(value.maxValue in -128..127) { "Bass max must fit in a signed byte" }
        require(value.currentValue in -128..127) { "Bass current must fit in a signed byte" }
        val type = value.type
        return if (type == null || type <= 0) {
            byteArrayOf(
                value.minValue.toByte(),
                value.maxValue.toByte(),
                value.currentValue.toByte(),
            )
        } else {
            require(type in 1..0xFF) { "Bass type must fit in 8 bits" }
            byteArrayOf(
                value.minValue.toByte(),
                value.maxValue.toByte(),
                value.currentValue.toByte(),
                type.toByte(),
            )
        }
    }

    fun parseBattery(frame: Frame): BatteryReading? = parseBatteries(frame)?.firstOrNull()

    /** All (device type, level) pairs from a battery response. */
    fun parseBatteries(frame: Frame): List<BatteryReading>? {
        // Response data is status, count, then (device type, battery) pairs.
        if (frame.payload.size < 4) return null
        if (frame.payload[0].toInt() and 0xFF != 0) return null
        val count = frame.payload[1].toInt() and 0xFF
        if (count < 1 || frame.payload.size < 2 + count * 2) return null

        return (0 until count).map { index ->
            val offset = 2 + index * 2
            val deviceType = frame.payload[offset].toInt() and 0xFF
            val encodedLevel = frame.payload[offset + 1].toInt() and 0xFF
            BatteryReading(
                deviceType = deviceType,
                percentage = encodedLevel and 0x7F,
                charging = encodedLevel and 0x80 != 0,
            )
        }
    }

    fun hex(bytes: ByteArray, limit: Int = 160): String {
        val shown = bytes.take(limit)
            .joinToString(separator = "") { "%02X".format(it.toInt() and 0xFF) }
        return if (bytes.size > limit) "$shown…" else shown
    }
}
