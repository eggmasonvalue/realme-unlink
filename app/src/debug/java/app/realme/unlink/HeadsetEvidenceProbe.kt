package app.realme.unlink

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import java.io.IOException
import java.util.concurrent.CancellationException

/**
 * Live validation matrix for features that still need evidence before product/UI commitment.
 * Every write path restores the original device state before finishing.
 */
class HeadsetEvidenceProbe(private val context: Context) {
    companion object {
        const val TAG = "UnlinkEvidence"
    }

    @Volatile
    var lastTrace: List<String> = emptyList()
        private set

    @Volatile
    private var cancelled = false

    @Volatile
    private var activeSocket: BluetoothSocket? = null

    @Volatile
    private var activeSession: HeadsetSession? = null

    private val traceEntries = mutableListOf<String>()

    val isCancelled: Boolean
        get() = cancelled

    data class Result(
        val deviceName: String,
        val service: String,
        val findings: List<String>,
        val log: List<String>,
    )

    fun cancel() {
        cancelled = true
        try {
            activeSocket?.close()
        } catch (_: IOException) {
        }
        activeSession?.close()
    }

    @SuppressLint("MissingPermission")
    fun run(): Result {
        clearTrace()
        checkNotCancelled()

        val adapter = bluetoothAdapter()
        check(adapter.isEnabled) { "Bluetooth is turned off" }

        val bondedDevices = adapter.bondedDevices.toList()
        val device = bondedDevices.firstOrNull { it.isTarget() }
            ?: error(
                "Headset is not bonded. Bond realme Buds Wireless 5 ANC first. " +
                    "Bonded: ${bondedDevices.joinToString { it.name ?: "unknown" }}",
            )

        trace("bonded device: ${device.name ?: "realme headset"}")
        val findings = mutableListOf<String>()
        val session = openSession(adapter, device)
        try {
            trace("RFCOMM connected via ${session.service}")
            val capabilities = session.queryCapabilities().let { frame ->
                HeadsetCapabilities.parse(frame)
                    ?: error("Capability response was invalid or reported failure status")
            }
            trace("capability bitmap: ${HeadsetProtocol.hex(capabilities.rawBitmap)}")
            findings += "capability bits=${capabilities.setBits.joinToString()}"

            findings += runFamily("noise") { validateNoiseModes(session, capabilities) }
            findings += runFamily("switch features") { validateSwitchFeatures(session, capabilities) }
            findings += runFamily("button remap") { validateButtonRemap(session, capabilities) }
            findings += runFamily("custom EQ") { validateCustomEq(session, capabilities) }
            findings += runFamily("bass") { validateBassBands(session, capabilities) }
            findings += runFamily("volume enhancer") { validateVolumeEnhancer(session, capabilities) }
            findings += runFamily("wear") { validateWearSnapshot(session, capabilities) }
            findings += runFamily("multipoint") { validateMultipointSnapshot(session, capabilities) }
            findings += runFamily("find-mode") { validateFindModeAction(session, capabilities) }

            checkNotCancelled()
            findings.forEach { Log.i(TAG, "FINDING: $it") }
            Log.i(TAG, "MATRIX_COMPLETE findings=${findings.size}")
            return Result(
                deviceName = session.deviceName,
                service = session.service,
                findings = findings,
                log = snapshotTrace(),
            )
        } finally {
            closeActiveSession()
        }
    }

    private fun validateNoiseModes(
        session: HeadsetSession,
        capabilities: HeadsetCapabilities,
    ): List<String> {
        if (!capabilities.supportsCommand(HeadsetProtocol.NOISE_LEVEL_COMMAND) ||
            !capabilities.supportsCommand(HeadsetProtocol.SET_NOISE_REDUCTION_COMMAND)
        ) {
            return listOf("noise: skipped (commands not advertised)")
        }

        val baseline = readNoise(session)
        val findings = mutableListOf(
            "noise baseline: ${HeadsetSemantics.noiseSummary(baseline.action, baseline.type, baseline.value)} " +
                "(action=${baseline.action}, type=${baseline.type}, value=${baseline.value})",
        )
        check(baseline.action == 1 && baseline.type == 1) {
            "Expected action/type 1/1 noise baseline for mode mapping; got ${baseline.action}/${baseline.type}"
        }

        val candidates = listOf(1, 2, 4, 8, 16, 32).filter { it != baseline.value }
        var changedAccepted = 0
        try {
            for (value in candidates) {
                checkNotCancelled()
                val target = baseline.copy(value = value)
                val status = writeStatus("noise value $value", session.setNoiseControl(target))
                if (status != 0) {
                    findings += "noise value $value: rejected status=$status"
                    continue
                }
                val after = readNoise(session)
                if (after == target) {
                    changedAccepted++
                    findings += "noise value $value: accepted and read back as " +
                        HeadsetSemantics.noiseDisplayLabel(after.action, after.value)
                } else {
                    findings += "noise value $value: accepted but read back action=${after.action}, " +
                        "type=${after.type}, value=${after.value}"
                }
            }
        } finally {
            restoreNoise(session, baseline, findings)
        }
        findings += "noise mode mapping: $changedAccepted/${candidates.size} alternate values verified"

        try {
            val cycleReading = HeadsetProtocol.parseNoiseControl(session.queryNoiseCycle())
            if (cycleReading != null) {
                findings += "ANC button cycle baseline: value=${cycleReading.value}"
                val altCycle = if (cycleReading.value == 11) 10 else 11
                checkWriteAccepted("noise cycle write $altCycle", session.setNoiseCycle(altCycle))
                val afterCycle = HeadsetProtocol.parseNoiseControl(session.queryNoiseCycle())
                check(afterCycle?.value == altCycle) {
                    "ANC button cycle read-back was ${afterCycle?.value}, expected $altCycle"
                }
                findings += "ANC button cycle: changed ${cycleReading.value} → $altCycle and verified read-back"
                checkWriteAccepted("noise cycle restore ${cycleReading.value}", session.setNoiseCycle(cycleReading.value))
                val restoredCycle = HeadsetProtocol.parseNoiseControl(session.queryNoiseCycle())
                check(restoredCycle?.value == cycleReading.value) {
                    "ANC button cycle restore read-back was ${restoredCycle?.value}, expected ${cycleReading.value}"
                }
                findings += "ANC button cycle: restored baseline ${cycleReading.value} and verified read-back"
            }
        } catch (e: Exception) {
            findings += "ANC button cycle: failed (${e.message})"
        }

        return findings
    }

    private fun validateSwitchFeatures(
        session: HeadsetSession,
        capabilities: HeadsetCapabilities,
    ): List<String> {
        if (!capabilities.supportsCommand(HeadsetProtocol.SWITCH_FEATURE_COMMAND) ||
            !capabilities.supportsCommand(HeadsetProtocol.SET_SWITCH_FEATURE_COMMAND)
        ) {
            return listOf("switch features: skipped (commands not advertised)")
        }

        val baseline = readSwitchFeatures(session)
        val findings = mutableListOf(
            "switch baseline: " + baseline.joinToString { feature ->
                val label = HeadsetSemantics.switchFeatureLabel(feature.featureId)
                    ?: "feature ${feature.featureId}"
                "$label=${HeadsetSemantics.switchValueLabel(feature.value)}(id=${feature.featureId})"
            },
        )

        // Boolean-looking IDs already returned by this headset.
        val candidates = baseline
            .filter { it.value == 0 || it.value == 1 }
            .map { it.featureId }
            .distinct()
            .sorted()

        val accepted = mutableListOf<Int>()
        val rejected = mutableListOf<String>()
        try {
            for (featureId in candidates) {
                checkNotCancelled()
                val current = baseline.first { it.featureId == featureId }
                val targetEnabled = current.value == 0
                val status = writeStatus(
                    "switch feature $featureId",
                    session.setSwitchFeature(featureId, targetEnabled),
                )
                if (status != 0) {
                    rejected += "$featureId(status=$status)"
                    continue
                }
                val after = readSwitchFeatures(session)
                val afterValue = after.firstOrNull { it.featureId == featureId }?.value
                if (afterValue == if (targetEnabled) 1 else 0) {
                    accepted += featureId
                    // Restore immediately per feature so a later failure leaves less drift.
                    checkWriteAccepted(
                        "switch feature $featureId restore",
                        session.setSwitchFeature(featureId, current.value == 1),
                    )
                    val restored = readSwitchFeatures(session)
                        .firstOrNull { it.featureId == featureId }
                        ?.value
                    check(restored == current.value) {
                        "switch feature $featureId restore read back as $restored"
                    }
                } else {
                    rejected += "$featureId(readback=$afterValue)"
                    checkWriteAccepted(
                        "switch feature $featureId emergency restore",
                        session.setSwitchFeature(featureId, current.value == 1),
                    )
                }
            }
        } finally {
            restoreSwitchFeatures(session, baseline, findings)
        }

        findings += "switch reversible accepted: ${accepted.joinToString().ifEmpty { "none" }}"
        findings += "switch rejected/unverified: ${rejected.joinToString().ifEmpty { "none" }}"
        return findings
    }

    private fun validateButtonRemap(
        session: HeadsetSession,
        capabilities: HeadsetCapabilities,
    ): List<String> {
        if (!capabilities.supportsCommand(HeadsetProtocol.KEY_FUNCTION_COMMAND) ||
            !capabilities.supportsCommand(HeadsetProtocol.SET_BUTTON_MAPPING_COMMAND)
        ) {
            return listOf("button remap: skipped (commands not advertised)")
        }

        val baseline = readButtonMappings(session)
        val findings = mutableListOf(
            "button baseline: " + baseline.joinToString {
                "d${it.deviceType}/b${it.button}/" +
                    "${HeadsetSemantics.buttonActionLabel(it.action)}→" +
                    HeadsetSemantics.buttonFunctionLabel(it.function)
            },
        )

        // Prefer media functions that are commonly remappable; volume functions on the
        // secondary button group were accepted but not applied on this model.
        val candidateIndexes = buildList {
            baseline.forEachIndexed { index, mapping ->
                if (mapping.function in setOf(1, 2, 3, 5, 6)) add(index)
            }
            baseline.forEachIndexed { index, mapping ->
                if (mapping.function in setOf(8, 9) && index !in this) add(index)
            }
        }

        if (candidateIndexes.isEmpty()) {
            findings += "button remap: no safe candidate mapping found"
            return findings
        }

        var verified = false
        try {
            for (targetIndex in candidateIndexes) {
                checkNotCancelled()
                val original = baseline[targetIndex]
                val alternateFunction = alternateButtonFunction(original.function)
                if (alternateFunction == original.function) continue
                val modified = baseline.toMutableList()
                modified[targetIndex] = original.copy(function = alternateFunction)

                val status = writeStatus(
                    "button remap idx=$targetIndex",
                    session.setButtonMappings(modified),
                )
                if (status != 0) {
                    findings += "button remap candidate " +
                        "${HeadsetSemantics.buttonActionLabel(original.action)}/" +
                        "${HeadsetSemantics.buttonFunctionLabel(original.function)}: " +
                        "rejected status=$status"
                    continue
                }

                val after = readButtonMappings(session)
                if (after == modified) {
                    verified = true
                    findings += "button remap: changed " +
                        "${HeadsetSemantics.buttonActionLabel(original.action)} " +
                        "${HeadsetSemantics.buttonFunctionLabel(original.function)} → " +
                        HeadsetSemantics.buttonFunctionLabel(alternateFunction) +
                        " and read back"
                    break
                }

                findings += "button remap candidate " +
                    "${HeadsetSemantics.buttonActionLabel(original.action)}/" +
                    "${HeadsetSemantics.buttonFunctionLabel(original.function)}→" +
                    "${HeadsetSemantics.buttonFunctionLabel(alternateFunction)}: " +
                    "accepted but not applied"
                // Put the baseline back before the next candidate.
                checkWriteAccepted("button interim restore", session.setButtonMappings(baseline))
            }
            if (!verified) {
                findings += "button remap: no candidate produced a verified state change"
            }
        } finally {
            checkWriteAccepted("button remap restore", session.setButtonMappings(baseline))
            val restored = readButtonMappings(session)
            check(restored == baseline) {
                "button remap restore mismatch: expected $baseline, got $restored"
            }
            findings += "button remap: restored original mappings and verified read-back"
        }
        return findings
    }

    private fun alternateButtonFunction(function: Int): Int = when (function) {
        1 -> 6 // play/pause → next
        2 -> 1 // device switch → play/pause
        3 -> 1 // noise mode → play/pause
        5 -> 6 // previous → next
        6 -> 1 // next → play/pause
        8 -> 9 // volume up → down
        9 -> 8 // volume down → up
        else -> function
    }

    private fun runFamily(name: String, block: () -> List<String>): List<String> {
        return try {
            checkNotCancelled()
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val message = "$name family FAILED: ${error.message ?: error.javaClass.simpleName}"
            Log.e(TAG, message, error)
            listOf(message)
        }
    }

    private fun validateCustomEq(
        session: HeadsetSession,
        capabilities: HeadsetCapabilities,
    ): List<String> {
        if (!capabilities.supportsCommand(HeadsetProtocol.ALL_EQ_COMMAND) ||
            !capabilities.supportsCommand(HeadsetProtocol.SET_CUSTOM_EQ_COMMAND) ||
            !capabilities.supportsCommand(HeadsetProtocol.EQ_STATUS_COMMAND) ||
            !capabilities.supportsCommand(HeadsetProtocol.SET_EQ_STATUS_COMMAND)
        ) {
            return listOf("custom EQ: skipped (commands not advertised)")
        }

        val eqBefore = readEqStatus(session)
        val profilesBefore = readEqProfiles(session)
        val findings = mutableListOf(
            "custom EQ baseline: currentEqId=$eqBefore, profiles=" +
                profilesBefore.joinToString {
                    "${it.id}:${it.name} selected=${it.selected} bands=${it.dbValues}"
                },
        )

        val originalProfile = profilesBefore.firstOrNull { it.id == 4 }
            ?: profilesBefore.firstOrNull { it.name.contains("Custom", ignoreCase = true) }
            ?: run {
                findings += "custom EQ: no custom profile available"
                return findings
            }

        check(originalProfile.dbValues.isNotEmpty()) { "custom EQ profile has no bands" }
        val bandIndex = 0
        val originalDb = originalProfile.dbValues[bandIndex]
        val nextDb = when {
            originalDb < originalProfile.maxValue -> originalDb + 1
            originalDb > originalProfile.minValue -> originalDb - 1
            else -> error("custom EQ band has no room to move within range")
        }
        val modifiedDb = originalProfile.dbValues.toMutableList().also { it[bandIndex] = nextDb }
        val modifiedProfile = originalProfile.copy(dbValues = modifiedDb)

        var modifiedApplied = false
        try {
            checkNotCancelled()
            checkWriteAccepted(
                "custom EQ modify",
                session.setCustomEq(action = 2, profile = modifiedProfile),
            )
            modifiedApplied = true
            val profilesAfter = readEqProfiles(session)
            val afterProfile = profilesAfter.firstOrNull { it.id == originalProfile.id }
                ?: error("custom EQ profile ${originalProfile.id} missing after modify")
            check(afterProfile.dbValues == modifiedDb) {
                "custom EQ modify read-back bands=${afterProfile.dbValues}, expected $modifiedDb"
            }
            check(afterProfile.frequencies == originalProfile.frequencies) {
                "custom EQ frequencies changed unexpectedly"
            }
            findings += "custom EQ: modified profile ${originalProfile.id} band0 " +
                "$originalDb → $nextDb and verified read-back"
        } finally {
            if (modifiedApplied) {
                checkWriteAccepted(
                    "custom EQ restore profile",
                    session.setCustomEq(action = 2, profile = originalProfile),
                )
            }
            checkWriteAccepted("custom EQ restore selection", session.setEqStatus(eqBefore))
            val profilesRestored = readEqProfiles(session)
            val restoredProfile = profilesRestored.firstOrNull { it.id == originalProfile.id }
            check(restoredProfile?.dbValues == originalProfile.dbValues) {
                "custom EQ restore bands=${restoredProfile?.dbValues}, expected ${originalProfile.dbValues}"
            }
            val eqRestored = readEqStatus(session)
            check(eqRestored == eqBefore) {
                "custom EQ selection restore read back as $eqRestored, expected $eqBefore"
            }
            findings += "custom EQ: restored profile ${originalProfile.id} bands and EQ ID $eqBefore"
        }
        return findings
    }

    private fun validateBassBands(
        session: HeadsetSession,
        capabilities: HeadsetCapabilities,
    ): List<String> {
        if (!capabilities.supportsCommand(HeadsetProtocol.MULTI_BASS_ENGINE_COMMAND) ||
            !capabilities.supportsCommand(HeadsetProtocol.SET_BASS_ENGINE_COMMAND)
        ) {
            return listOf("bass: skipped (commands not advertised)")
        }

        val baseline = readMultiBass(session)
        val findings = mutableListOf(
            "bass baseline: " + baseline.joinToString {
                "type=${it.type} range=${it.minValue}..${it.maxValue} current=${it.currentValue}"
            },
        )
        check(baseline.isNotEmpty()) { "multi-band bass returned no values" }

        try {
            for (original in baseline) {
                checkNotCancelled()
                val next = when {
                    original.currentValue < original.maxValue -> original.currentValue + 1
                    original.currentValue > original.minValue -> original.currentValue - 1
                    else -> continue
                }
                val modified = original.copy(currentValue = next)
                checkWriteAccepted("bass band write type=${original.type}", session.setBassEngine(modified))
                val after = readMultiBass(session)
                val afterBand = after.firstOrNull { it.type == original.type }
                check(afterBand?.currentValue == next) {
                    "bass read-back current=${afterBand?.currentValue}, expected $next"
                }
                findings += "bass: changed type=${original.type} ${original.currentValue} → $next and verified"
                checkWriteAccepted("bass restore type=${original.type}", session.setBassEngine(original))
                val restoredBand = readMultiBass(session).firstOrNull { it.type == original.type }
                check(restoredBand?.currentValue == original.currentValue) {
                    "bass restore current=${restoredBand?.currentValue}, expected ${original.currentValue}"
                }
            }
        } finally {
            // Restore every baseline band in case the write affected more than one.
            for (band in baseline) {
                checkWriteAccepted("bass restore type=${band.type}", session.setBassEngine(band))
            }
            val restored = readMultiBass(session)
            check(restored == baseline) {
                "bass restore mismatch: expected $baseline, got $restored"
            }
            findings += "bass: restored all bands and verified read-back"
        }
        return findings
    }

    private fun validateVolumeEnhancer(
        session: HeadsetSession,
        capabilities: HeadsetCapabilities,
    ): List<String> {
        if (!capabilities.supportsCommand(HeadsetProtocol.HIGH_VOLUME_COMMAND) ||
            !capabilities.supportsCommand(HeadsetProtocol.SET_HIGH_VOLUME_COMMAND)
        ) {
            return listOf("volume enhancer: skipped (commands not advertised)")
        }
        val before = HeadsetProtocol.parseHighVolume(session.queryHighVolume())
            ?: return listOf("volume enhancer: query response unparseable")
        val findings = mutableListOf("volume enhancer baseline: $before")
        val next = !before
        try {
            checkNotCancelled()
            checkWriteAccepted("volume enhancer set $next", session.setHighVolume(next))
            val after = HeadsetProtocol.parseHighVolume(session.queryHighVolume())
            check(after == next) { "volume enhancer read-back was $after, expected $next" }
            findings += "volume enhancer: changed $before → $next and verified read-back"
        } finally {
            checkWriteAccepted("volume enhancer restore $before", session.setHighVolume(before))
            val restored = HeadsetProtocol.parseHighVolume(session.queryHighVolume())
            check(restored == before) { "volume enhancer restore read-back was $restored, expected $before" }
            findings += "volume enhancer: restored baseline $before and verified read-back"
        }
        return findings
    }

    private fun validateWearSnapshot(
        session: HeadsetSession,
        capabilities: HeadsetCapabilities,
    ): List<String> {
        if (!capabilities.supportsCommand(HeadsetProtocol.WEAR_STATUS_COMMAND)) {
            return listOf("wear: skipped (command not advertised)")
        }
        val wear = readWear(session)
        return listOf(
            "wear snapshot: " + wear.joinToString {
                "${HeadsetSemantics.wearRole(it.deviceType)} flags=${it.flags} " +
                    "inEar=${it.inEar} inBox=${it.inBox} coverClosed=${it.coverClosed}"
            } + " (read-only; physical transitions not automated)",
        )
    }

    private fun validateMultipointSnapshot(
        session: HeadsetSession,
        capabilities: HeadsetCapabilities,
    ): List<String> {
        if (!capabilities.supportsCommand(HeadsetProtocol.MULTI_CONNECT_COMMAND)) {
            return listOf("multipoint: skipped (command not advertised)")
        }
        val entries = readMultipoint(session)
        return listOf(
            "multipoint snapshot: " + entries.joinToString { entry ->
                buildString {
                    append(HeadsetSemantics.multipointStateLabel(entry.state))
                    if (entry.flags == 1) append(", this phone")
                    append(", name=")
                    append(entry.name)
                    append(", flags=")
                    append(entry.flags)
                }
            } + " (no 0x040B connect/delete action exercised)",
        )
    }

    private fun validateFindModeAction(
        session: HeadsetSession,
        capabilities: HeadsetCapabilities,
    ): List<String> {
        if (!capabilities.supportsCommand(HeadsetProtocol.SET_FIND_MODE_COMMAND)) {
            // Capability bit 5 advertises find-device mode command family 0x0400.
            return listOf("find-mode action: skipped (0x0400 not advertised)")
        }

        val findings = mutableListOf<String>()
        try {
            checkNotCancelled()
            val startStatus = writeStatus("find-mode start", session.setFindMode(true))
            findings += if (startStatus == 0) {
                "find-mode action 0x0400 start: accepted"
            } else {
                "find-mode action 0x0400 start: rejected status=$startStatus"
            }
        } finally {
            val stopStatus = writeStatus("find-mode stop", session.setFindMode(false))
            findings += if (stopStatus == 0) {
                "find-mode action 0x0400 stop: accepted"
            } else {
                "find-mode action 0x0400 stop: rejected status=$stopStatus"
            }
        }
        return findings
    }

    private fun restoreNoise(
        session: HeadsetSession,
        baseline: HeadsetProtocol.NoiseControlReading,
        findings: MutableList<String>,
    ) {
        try {
            checkWriteAccepted("noise restore", session.setNoiseControl(baseline))
            val restored = readNoise(session)
            check(restored == baseline) {
                "noise restore mismatch: expected $baseline, got $restored"
            }
            findings += "noise: restored baseline value ${baseline.value}"
        } catch (error: Exception) {
            findings += "noise restore FAILED: ${error.message}"
            throw error
        }
    }

    private fun restoreSwitchFeatures(
        session: HeadsetSession,
        baseline: List<HeadsetCapabilityDiscovery.SwitchFeatureStatus>,
        findings: MutableList<String>,
    ) {
        val current = try {
            readSwitchFeatures(session)
        } catch (error: Exception) {
            findings += "switch final read failed before restore: ${error.message}"
            throw error
        }
        val mismatched = baseline.filter { expected ->
            current.firstOrNull { it.featureId == expected.featureId }?.value != expected.value
        }
        for (feature in mismatched) {
            checkWriteAccepted(
                "switch final restore ${feature.featureId}",
                session.setSwitchFeature(feature.featureId, feature.value == 1),
            )
        }
        val restored = readSwitchFeatures(session)
        check(restored == baseline) {
            "switch final restore mismatch: expected $baseline, got $restored"
        }
        findings += "switch: restored full baseline feature list"
    }

    private fun readNoise(session: HeadsetSession): HeadsetProtocol.NoiseControlReading {
        return HeadsetProtocol.parseNoiseControl(session.queryNoiseControl())
            ?: error("noise-control response was invalid")
    }

    private fun readSwitchFeatures(
        session: HeadsetSession,
    ): List<HeadsetCapabilityDiscovery.SwitchFeatureStatus> {
        val query = HeadsetCapabilityDiscovery.readOnlyQueries.first {
            it.command == HeadsetProtocol.SWITCH_FEATURE_COMMAND
        }
        return HeadsetCapabilityDiscovery.parseSwitchFeatureStatus(
            session.transact(query.command, query.payload),
        ) ?: error("switch-feature response was invalid")
    }

    private fun readButtonMappings(
        session: HeadsetSession,
    ): List<HeadsetCapabilityDiscovery.KeyMapping> {
        val query = HeadsetCapabilityDiscovery.readOnlyQueries.first {
            it.command == HeadsetProtocol.KEY_FUNCTION_COMMAND
        }
        return HeadsetCapabilityDiscovery.parseKeyMappings(
            session.transact(query.command, query.payload),
        ) ?: error("button-mapping response was invalid")
    }

    private fun readEqStatus(session: HeadsetSession): Int {
        val query = HeadsetCapabilityDiscovery.readOnlyQueries.first {
            it.command == HeadsetProtocol.EQ_STATUS_COMMAND
        }
        return HeadsetCapabilityDiscovery.parseEqStatus(
            session.transact(query.command, query.payload),
        ) ?: error("EQ status response was invalid")
    }

    private fun readEqProfiles(
        session: HeadsetSession,
    ): List<HeadsetCapabilityDiscovery.EqProfile> {
        val query = HeadsetCapabilityDiscovery.readOnlyQueries.first {
            it.command == HeadsetProtocol.ALL_EQ_COMMAND
        }
        return HeadsetCapabilityDiscovery.parseEqProfiles(
            session.transact(query.command, query.payload),
        ) ?: error("EQ profile response was invalid")
    }

    private fun readMultiBass(
        session: HeadsetSession,
    ): List<HeadsetCapabilityDiscovery.BassEngineValue> {
        val query = HeadsetCapabilityDiscovery.readOnlyQueries.first {
            it.command == HeadsetProtocol.MULTI_BASS_ENGINE_COMMAND
        }
        return HeadsetCapabilityDiscovery.parseMultiBassEngine(
            session.transact(query.command, query.payload),
        ) ?: error("multi-band bass response was invalid")
    }

    private fun readWear(
        session: HeadsetSession,
    ): List<HeadsetCapabilityDiscovery.WearStatus> {
        return HeadsetCapabilityDiscovery.parseWearStatus(
            session.transact(HeadsetProtocol.WEAR_STATUS_COMMAND),
        ) ?: error("wear-status response was invalid")
    }

    private fun readMultipoint(
        session: HeadsetSession,
    ): List<HeadsetCapabilityDiscovery.MultipointEntry> {
        return HeadsetCapabilityDiscovery.parseMultipointStatus(
            session.transact(HeadsetProtocol.MULTI_CONNECT_COMMAND),
        ) ?: error("multipoint response was invalid")
    }

    private fun writeStatus(label: String, frame: HeadsetProtocol.Frame): Int {
        check(frame.isResponse) { "$label write returned a non-response frame" }
        val status = HeadsetProtocol.parseSetStatus(frame)
            ?: error("$label write response had no status byte")
        trace("$label write status=$status")
        return status
    }

    private fun checkWriteAccepted(label: String, frame: HeadsetProtocol.Frame) {
        val status = writeStatus(label, frame)
        check(status == 0) { "$label write was rejected: status=$status" }
    }

    @SuppressLint("MissingPermission")
    private fun openSession(
        adapter: BluetoothAdapter,
        device: BluetoothDevice,
    ): HeadsetSession {
        closeActiveSession()
        val session = HeadsetSession.connect(
            adapter = adapter,
            device = device,
            trace = ::trace,
            onSocketCreated = { activeSocket = it },
            shouldCancel = { cancelled },
        )
        activeSession = session
        return session
    }

    private fun closeActiveSession() {
        val session = activeSession
        activeSession = null
        activeSocket = null
        session?.close()
    }

    @SuppressLint("MissingPermission")
    private fun bluetoothAdapter(): BluetoothAdapter {
        return context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: BluetoothAdapter.getDefaultAdapter()
            ?: error("This phone does not expose Bluetooth")
    }

    private fun checkNotCancelled() {
        if (cancelled) throw CancellationException("Headset evidence probe cancelled")
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.isTarget(): Boolean {
        val name = name ?: return false
        return name.equals("realme Buds Wireless 5 ANC", ignoreCase = true) ||
            name.contains("Buds Wireless 5", ignoreCase = true)
    }

    private fun trace(message: String) {
        Log.i(TAG, message)
        synchronized(traceEntries) {
            traceEntries += message
            lastTrace = traceEntries.toList()
        }
    }

    private fun clearTrace() {
        synchronized(traceEntries) {
            traceEntries.clear()
            lastTrace = emptyList()
        }
    }

    private fun snapshotTrace(): List<String> = synchronized(traceEntries) {
        traceEntries.toList()
    }
}
