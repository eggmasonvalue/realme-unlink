package app.realme.unlink

import java.nio.charset.StandardCharsets

/**
 * Read-only command inventory used during capability discovery. This is deliberately separate from
 * the user-facing controls: an advertised command is not considered usable until its response is
 * parsed successfully.
 */
object HeadsetCapabilityDiscovery {
    data class Query(
        val title: String,
        val command: Int,
        val payload: ByteArray = byteArrayOf(),
    )

    data class Readout(
        val query: Query,
        val frame: HeadsetProtocol.Frame,
        val summary: String,
    )

    data class VersionEntry(
        val first: String,
        val second: String,
        val third: String,
    )

    data class KeyMapping(
        val deviceType: Int,
        val button: Int,
        val action: Int,
        val function: Int,
    )

    data class WearStatus(
        val deviceType: Int,
        val flags: Int,
        val inBox: Boolean,
        val inEar: Boolean,
        val coverClosed: Boolean,
    )

    data class SwitchFeatureStatus(
        val featureId: Int,
        val value: Int,
    )

    data class MultipointEntry(
        val state: Int,
        val flags: Int,
        val name: String,
    )

    data class EqProfile(
        val selected: Boolean,
        val minValue: Int,
        val maxValue: Int,
        val id: Int,
        val name: String,
        val frequencies: List<Int>,
        val dbValues: List<Int>,
    )

    data class BassEngineValue(
        val minValue: Int,
        val maxValue: Int,
        val currentValue: Int,
        val type: Int?,
    )

    data class HearingSummary(val profileCount: Int)

    data class ExcludedQuery(
        val title: String,
        val command: Int,
        val reason: String,
    )

    val buttonMappingQuery = Query(
        "Button mappings",
        HeadsetProtocol.KEY_FUNCTION_COMMAND,
        payload(14, 1, 3, 4, 5, 6, 11, 12, 8, 13, 17, 31, 26, 27, 0),
    )

    /**
     * These requests are read-only in the reference implementation. Commands that initiate an
     * action, register events, enable debug mode, or write a setting are intentionally absent.
     */
    val readOnlyQueries: List<Query> = listOf(
        Query("Firmware/version", HeadsetProtocol.VERSION_COMMAND),
        buttonMappingQuery,
        Query("In-ear/wear status", HeadsetProtocol.WEAR_STATUS_COMMAND),
        Query(
            "Switch-feature status",
            HeadsetProtocol.SWITCH_FEATURE_COMMAND,
            payload(16, 7, 6, 4, 5, 11, 8, 17, 19, 24, 9, 26, 12, 29, 27, 54, 38),
        ),
        Query("EQ status", HeadsetProtocol.EQ_STATUS_COMMAND),
        Query("Dual-device status", HeadsetProtocol.MULTI_CONNECT_COMMAND),
        Query("Hearing enhancement data", HeadsetProtocol.HEARING_ENHANCEMENT_COMMAND),
        Query("EQ profiles", HeadsetProtocol.ALL_EQ_COMMAND),
        Query("Multi-band bass-engine data", HeadsetProtocol.MULTI_BASS_ENGINE_COMMAND),
    )

    /**
     * Kept as a recorded matrix result, not as a query. Fresh reconnects timed out on 0x0124
     * twice while the other advertised reads completed, so this path is excluded from v1 and is
     * not retried by the app's discovery action.
     */
    val excludedFromReadOnlyDiscovery: List<ExcludedQuery> = listOf(
        ExcludedQuery(
            title = "Bass-engine data",
            command = HeadsetProtocol.BASS_ENGINE_COMMAND,
            reason = "0x0124 timed out after two fresh reconnects; usability is unresolved",
        ),
    )

    /** Formats a matched response without exposing multipoint addresses or other identifiers. */
    fun summarize(query: Query, frame: HeadsetProtocol.Frame): String {
        val payload = successfulPayload(query, frame)
            ?: return responseFailure(query, frame)

        return when (query.command) {
            HeadsetProtocol.VERSION_COMMAND -> {
                val entries = parseVersion(frame)
                if (entries == null) "status=0, invalid version payload"
                else "status=0, entries=${entries.joinToString { versionSummary(it) }}"
            }

            HeadsetProtocol.KEY_FUNCTION_COMMAND -> {
                val entries = parseKeyMappings(frame)
                if (entries == null) "status=0, invalid button-mapping payload"
                else "status=0, mappings=${entries.joinToString { keyMappingSummary(it) }}"
            }

            HeadsetProtocol.WEAR_STATUS_COMMAND -> {
                val entries = parseWearStatus(frame)
                if (entries == null) "status=0, invalid wear-status payload"
                else "status=0, wear=${entries.joinToString { wearSummary(it) }}"
            }

            HeadsetProtocol.SWITCH_FEATURE_COMMAND -> {
                val entries = parseSwitchFeatureStatus(frame)
                if (entries == null) "status=0, invalid switch-feature payload"
                else "status=0, features=${entries.joinToString { switchFeatureSummary(it) }}"
            }

            HeadsetProtocol.EQ_STATUS_COMMAND -> {
                val eqId = parseEqStatus(frame)
                if (eqId == null) "status=0, invalid EQ-status payload" else "status=0, currentEqId=$eqId"
            }

            HeadsetProtocol.MULTI_CONNECT_COMMAND -> {
                val entries = parseMultipointStatus(frame)
                if (entries == null) "status=0, invalid dual-device payload"
                else "status=0, devices=${entries.joinToString { multipointSummary(it) }}"
            }

            HeadsetProtocol.HEARING_ENHANCEMENT_COMMAND -> {
                val hearing = parseHearingSummary(frame)
                if (hearing == null) "status=0, invalid hearing-enhancement payload"
                else "status=0, profileCount=${hearing.profileCount}"
            }

            HeadsetProtocol.ALL_EQ_COMMAND -> {
                val profiles = parseEqProfiles(frame)
                if (profiles == null) "status=0, invalid EQ-profile payload"
                else "status=0, profiles=${profiles.joinToString { profileSummary(it) }}"
            }

            HeadsetProtocol.BASS_ENGINE_COMMAND -> {
                val value = parseBassEngine(frame)
                if (value == null) "status=0, invalid bass-engine payload"
                else "status=0, ${bassSummary(value)}"
            }

            HeadsetProtocol.MULTI_BASS_ENGINE_COMMAND -> {
                val values = parseMultiBassEngine(frame)
                if (values == null) "status=0, invalid multi-bass payload"
                else "status=0, values=${values.joinToString { bassSummary(it) }}"
            }

            else -> "status=0, payload=${HeadsetProtocol.hex(payload)}"
        }
    }

    fun parseVersion(frame: HeadsetProtocol.Frame): List<VersionEntry>? {
        val payload = successfulPayload(VERSION_QUERY, frame) ?: return null
        if (payload.size < 2) return null
        val count = unsigned(payload[1])
        val text = String(payload, 2, payload.size - 2, StandardCharsets.UTF_8)
        val parts = text.split(',')
        if (parts.size != count * 3) return null
        return parts.chunked(3).map { VersionEntry(it[0], it[1], it[2]) }
    }

    fun parseKeyMappings(frame: HeadsetProtocol.Frame): List<KeyMapping>? {
        val payload = successfulPayload(KEY_QUERY, frame) ?: return null
        if (payload.size < 2) return null
        val count = unsigned(payload[1])
        if (count == 0 || payload.size < 2 + count * 4) return null
        return (0 until count).map { index ->
            val offset = 2 + index * 4
            KeyMapping(
                deviceType = unsigned(payload[offset]),
                button = unsigned(payload[offset + 1]),
                action = unsigned(payload[offset + 2]),
                function = unsigned(payload[offset + 3]),
            )
        }
    }

    fun parseWearStatus(frame: HeadsetProtocol.Frame): List<WearStatus>? {
        val payload = successfulPayload(WEAR_QUERY, frame) ?: return null
        if (payload.size < 2) return null
        val count = unsigned(payload[1])
        if (count == 0 || payload.size < 2 + count * 2) return null
        return (0 until count).map { index ->
            val flags = unsigned(payload[3 + index * 2])
            WearStatus(
                deviceType = unsigned(payload[2 + index * 2]),
                flags = flags,
                inBox = flags and 0x01 == 0,
                inEar = flags and 0x02 != 0,
                coverClosed = flags and 0x04 == 0,
            )
        }
    }

    fun parseSwitchFeatureStatus(frame: HeadsetProtocol.Frame): List<SwitchFeatureStatus>? {
        val payload = successfulPayload(SWITCH_QUERY, frame) ?: return null
        if (payload.size < 2) return null
        val count = unsigned(payload[1])
        if (count == 0 || payload.size < 2 + count * 2) return null
        return (0 until count).map { index ->
            val offset = 2 + index * 2
            SwitchFeatureStatus(
                featureId = unsigned(payload[offset]),
                value = unsigned(payload[offset + 1]),
            )
        }
    }

    fun parseEqStatus(frame: HeadsetProtocol.Frame): Int? {
        val payload = successfulPayload(EQ_QUERY, frame) ?: return null
        return payload.getOrNull(1)?.let(::unsigned)
    }

    fun parseMultipointStatus(frame: HeadsetProtocol.Frame): List<MultipointEntry>? {
        val payload = successfulPayload(MULTIPOINT_QUERY, frame) ?: return null
        if (payload.size < 2) return null
        val count = unsigned(payload[1])
        if (count == 0) return null

        val entries = mutableListOf<MultipointEntry>()
        var offset = 2
        repeat(count) {
            if (offset + 10 > payload.size) return null
            val recordLength = unsigned(payload[offset + 6])
            val state = unsigned(payload[offset + 7])
            val flags = unsigned(payload[offset + 8])
            val nameLength = unsigned(payload[offset + 9])
            if (recordLength < 3 || nameLength > recordLength - 3) return null
            val recordEnd = offset + 7 + recordLength
            if (recordEnd > payload.size || offset + 10 + nameLength > recordEnd) return null
            val name = String(payload, offset + 10, nameLength, StandardCharsets.UTF_8)
            entries += MultipointEntry(state = state, flags = flags, name = name)
            offset = recordEnd
        }
        return entries
    }

    fun parseHearingSummary(frame: HeadsetProtocol.Frame): HearingSummary? {
        val payload = successfulPayload(HEARING_QUERY, frame) ?: return null
        if (payload.size < 3) return null
        val count = unsigned(payload[2])
        val afterProfiles = 3 + count * 3
        if (afterProfiles + 4 > payload.size) return null
        return HearingSummary(profileCount = count)
    }

    fun parseEqProfiles(frame: HeadsetProtocol.Frame): List<EqProfile>? {
        val payload = successfulPayload(EQ_PROFILES_QUERY, frame) ?: return null
        if (payload.size < 2) return null
        val count = unsigned(payload[1])
        val profiles = mutableListOf<EqProfile>()
        var offset = 2
        repeat(count) {
            if (offset + 5 > payload.size) return null
            val selected = unsigned(payload[offset]) != 0
            val minValue = payload[offset + 1].toInt()
            val maxValue = payload[offset + 2].toInt()
            val id = unsigned(payload[offset + 3])
            val nameLength = unsigned(payload[offset + 4])
            offset += 5
            if (offset + nameLength > payload.size) return null
            val name = String(payload, offset, nameLength, StandardCharsets.UTF_8)
            offset += nameLength
            if (offset >= payload.size) return null
            val frequencyCount = unsigned(payload[offset])
            offset++
            if (offset + frequencyCount * 3 > payload.size) return null
            val frequencies = mutableListOf<Int>()
            val dbValues = mutableListOf<Int>()
            repeat(frequencyCount) {
                frequencies += unsigned(payload[offset]) or (unsigned(payload[offset + 1]) shl 8)
                dbValues += payload[offset + 2].toInt()
                offset += 3
            }
            profiles += EqProfile(
                selected = selected,
                minValue = minValue,
                maxValue = maxValue,
                id = id,
                name = name,
                frequencies = frequencies,
                dbValues = dbValues,
            )
        }
        return profiles
    }

    fun parseBassEngine(frame: HeadsetProtocol.Frame): BassEngineValue? {
        val payload = successfulPayload(BASS_QUERY, frame) ?: return null
        if (payload.size < 4) return null
        return BassEngineValue(
            minValue = payload[1].toInt(),
            maxValue = payload[2].toInt(),
            currentValue = payload[3].toInt(),
            type = payload.getOrNull(4)?.let(::unsigned),
        )
    }

    fun parseMultiBassEngine(frame: HeadsetProtocol.Frame): List<BassEngineValue>? {
        val payload = successfulPayload(MULTI_BASS_QUERY, frame) ?: return null
        if (payload.size < 2) return null
        val count = unsigned(payload[1])
        if (count == 0 || payload.size < 2 + count * 4) return null
        return (0 until count).map { index ->
            val offset = 2 + index * 4
            BassEngineValue(
                minValue = payload[offset].toInt(),
                maxValue = payload[offset + 1].toInt(),
                currentValue = payload[offset + 2].toInt(),
                type = unsigned(payload[offset + 3]),
            )
        }
    }

    private fun successfulPayload(query: Query, frame: HeadsetProtocol.Frame): ByteArray? {
        return successfulPayload(query.command, frame)
    }

    private fun successfulPayload(command: Int, frame: HeadsetProtocol.Frame): ByteArray? {
        if (!frame.isResponse || frame.baseCommand != command) return null
        if (frame.payload.isEmpty() || unsigned(frame.payload[0]) != 0) return null
        return frame.payload
    }

    private fun responseFailure(query: Query, frame: HeadsetProtocol.Frame): String {
        if (!frame.isResponse || frame.baseCommand != query.command) {
            return "invalid response command=0x${frame.command.toString(16).uppercase()}"
        }
        val status = frame.payload.firstOrNull()?.let(::unsigned)
            ?: return "invalid response with empty payload"
        return "status=$status"
    }

    private fun versionSummary(entry: VersionEntry): String {
        val kind = when (entry.second) {
            "2", "3" -> "firmware"
            else -> "version type ${entry.second}"
        }
        return "device ${entry.first}, $kind=${entry.third}"
    }

    private fun keyMappingSummary(mapping: KeyMapping): String {
        return "device=${mapping.deviceType}, button=${mapping.button}, " +
            "${HeadsetSemantics.buttonActionLabel(mapping.action)} → " +
            HeadsetSemantics.buttonFunctionLabel(mapping.function) +
            " (action=${mapping.action}, function=${mapping.function})"
    }

    private fun wearSummary(status: WearStatus): String {
        return "${HeadsetSemantics.wearRole(status.deviceType)}, " +
            "inEar=${status.inEar}, inBox=${status.inBox}, " +
            "coverClosed=${status.coverClosed}, flags=${status.flags}"
    }

    private fun switchFeatureSummary(status: SwitchFeatureStatus): String {
        val label = HeadsetSemantics.switchFeatureLabel(status.featureId)
            ?: "feature ${status.featureId}"
        return "$label=${HeadsetSemantics.switchValueLabel(status.value)} " +
            "(id=${status.featureId}, raw=${status.value})"
    }

    private fun multipointSummary(entry: MultipointEntry): String {
        val phone = if (entry.flags == 1) ", this phone" else ""
        return "${HeadsetSemantics.multipointStateLabel(entry.state)}$phone, " +
            "name=${entry.name}, flags=${entry.flags}"
    }

    private fun profileSummary(profile: EqProfile): String {
        return "${profile.id}:${profile.name}, selected=${profile.selected}, " +
            "range=${profile.minValue}..${profile.maxValue}, " +
            "bands=${profile.frequencies.zip(profile.dbValues).joinToString { "${it.first}:${it.second}" }}"
    }

    private fun bassSummary(value: BassEngineValue): String {
        val type = value.type?.let {
            val label = HeadsetSemantics.bassBandLabel(it)
            if (label == null) "type=$it" else "type=$it ($label)"
        } ?: ""
        return "min=${value.minValue}, max=${value.maxValue}, current=${value.currentValue}" +
            if (type.isEmpty()) "" else ", $type"
    }

    private fun unsigned(value: Byte): Int = value.toInt() and 0xFF

    private fun payload(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private val VERSION_QUERY = Query("Firmware/version", HeadsetProtocol.VERSION_COMMAND)
    private val KEY_QUERY = Query("Button mappings", HeadsetProtocol.KEY_FUNCTION_COMMAND)
    private val WEAR_QUERY = Query("In-ear/wear status", HeadsetProtocol.WEAR_STATUS_COMMAND)
    private val SWITCH_QUERY = Query("Switch-feature status", HeadsetProtocol.SWITCH_FEATURE_COMMAND)
    private val EQ_QUERY = Query("EQ status", HeadsetProtocol.EQ_STATUS_COMMAND)
    private val MULTIPOINT_QUERY = Query("Dual-device status", HeadsetProtocol.MULTI_CONNECT_COMMAND)
    private val HEARING_QUERY = Query("Hearing enhancement data", HeadsetProtocol.HEARING_ENHANCEMENT_COMMAND)
    private val EQ_PROFILES_QUERY = Query("EQ profiles", HeadsetProtocol.ALL_EQ_COMMAND)
    private val BASS_QUERY = Query("Bass-engine data", HeadsetProtocol.BASS_ENGINE_COMMAND)
    private val MULTI_BASS_QUERY = Query("Multi-band bass-engine data", HeadsetProtocol.MULTI_BASS_ENGINE_COMMAND)
}
