package app.realme.unlink

/** A command family advertised by one bit in the headset capability bitmap. */
data class CapabilityGroup(
    val bit: Int,
    val name: String,
    val commands: Set<Int>,
)

/**
 * The validated capability response returned by command 0x0100.
 *
 * Bitmap bits are numbered least-significant-bit first within each byte. Only groups whose bit is
 * set are included in [advertisedGroups]; [unknownBits] keeps set bits that are not mapped yet.
 */
data class HeadsetCapabilities(
    val rawBitmap: ByteArray,
    val setBits: Set<Int>,
    val advertisedGroups: List<CapabilityGroup>,
    val unknownBits: Set<Int>,
) {
    /** True only when the headset advertised a group containing this command. */
    fun supportsCommand(command: Int): Boolean {
        val baseCommand = command and RESPONSE_MASK.inv()
        return advertisedGroups.any { baseCommand in it.commands }
    }

    fun hasBit(bit: Int): Boolean = bit in setBits

    fun groupForBit(bit: Int): CapabilityGroup? = advertisedGroups.firstOrNull { it.bit == bit }

    companion object {
        private const val RESPONSE_MASK = 0x8000

        /**
         * Parses a capability response. A response must be for 0x0100, have status 0, and contain
         * at least one bitmap byte after the status byte.
         */
        fun parse(frame: HeadsetProtocol.Frame): HeadsetCapabilities? {
            if (!frame.isResponse || frame.baseCommand != HeadsetProtocol.CAPABILITY_COMMAND) {
                return null
            }
            if (frame.payload.size < 2 || unsigned(frame.payload[0]) != 0) return null

            val rawBitmap = frame.payload.copyOfRange(1, frame.payload.size)
            val setBits = linkedSetOf<Int>()
            rawBitmap.forEachIndexed { byteIndex, byte ->
                val value = unsigned(byte)
                for (bitInByte in 0 until 8) {
                    if (value and (1 shl bitInByte) != 0) {
                        setBits += byteIndex * 8 + bitInByte
                    }
                }
            }

            val advertisedGroups = knownGroups.filter { it.bit in setBits }
            val knownBits = knownGroups.mapTo(mutableSetOf()) { it.bit }
            val unknownBits = setBits.filterNot { it in knownBits }.toCollection(linkedSetOf())

            return HeadsetCapabilities(
                rawBitmap = rawBitmap,
                setBits = setBits,
                advertisedGroups = advertisedGroups,
                unknownBits = unknownBits,
            )
        }

        /** Known command-group mapping from the reference capability table. */
        val knownGroups: List<CapabilityGroup> = listOf(
            CapabilityGroup(
                bit = 0,
                name = "Firmware/version query",
                commands = setOf(0x0105),
            ),
            CapabilityGroup(
                bit = 1,
                name = "Battery",
                commands = setOf(0x0106),
            ),
            CapabilityGroup(
                bit = 2,
                name = "Firmware/upgrade capability query",
                commands = setOf(0x0107),
            ),
            CapabilityGroup(
                bit = 3,
                name = "Button/key mappings",
                commands = setOf(0x0108, 0x0401),
            ),
            CapabilityGroup(
                bit = 4,
                name = "In-ear/wear status",
                commands = setOf(0x0109),
            ),
            CapabilityGroup(
                bit = 5,
                name = "Find-device mode",
                commands = setOf(0x0400),
            ),
            CapabilityGroup(
                bit = 7,
                name = "Switch-feature status/control",
                commands = setOf(0x010D, 0x0403),
            ),
            CapabilityGroup(
                bit = 8,
                name = "Noise-control state/control",
                commands = setOf(0x010C, 0x0404),
            ),
            CapabilityGroup(
                bit = 9,
                name = "Ear-fit/compactness detection",
                commands = setOf(0x0405),
            ),
            CapabilityGroup(
                bit = 10,
                name = "EQ status/current EQ ID",
                commands = setOf(0x010F, 0x0406),
            ),
            CapabilityGroup(
                bit = 11,
                name = "High-volume enhancement",
                commands = setOf(0x0110, 0x0407),
            ),
            CapabilityGroup(
                bit = 19,
                name = "Hearing enhancement / HearID / ear scan",
                commands = setOf(0x0115, 0x0116, 0x040D, 0x040E),
            ),
            CapabilityGroup(
                bit = 22,
                name = "Multi-notification/event registration",
                commands = setOf(0x0205),
            ),
            CapabilityGroup(
                bit = 23,
                name = "Debug/log mode",
                commands = setOf(0x0F00),
            ),
            CapabilityGroup(
                bit = 29,
                name = "Dual-device/multipoint",
                commands = setOf(0x0112, 0x040B),
            ),
            CapabilityGroup(
                bit = 32,
                name = "Voice-prompt language",
                commands = setOf(0x0120),
            ),
            CapabilityGroup(
                bit = 34,
                name = "EQ profiles/custom EQ data",
                commands = setOf(0x0122, 0x0418),
            ),
            CapabilityGroup(
                bit = 38,
                name = "Bass-engine data",
                commands = setOf(0x0124, 0x041B),
            ),
            CapabilityGroup(
                bit = 54,
                name = "Multi-band bass-engine data",
                commands = setOf(0x012C),
            ),
        )

        private fun unsigned(byte: Byte): Int = byte.toInt() and 0xFF
    }
}
