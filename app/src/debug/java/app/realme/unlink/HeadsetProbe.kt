package app.realme.unlink

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.io.IOException
import java.util.concurrent.CancellationException

/** Orchestrates a read-only snapshot, with an explicit opt-in idempotent write check. */
class HeadsetProbe(private val context: Context) {
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
        val capabilityFrame: HeadsetProtocol.Frame,
        val capabilities: HeadsetCapabilities,
        val capabilityReadouts: List<HeadsetCapabilityDiscovery.Readout>,
        val capabilityWriteMessage: String?,
        val actualChangeMessage: String?,
        val batteryFrame: HeadsetProtocol.Frame?,
        val battery: HeadsetProtocol.BatteryReading?,
        val noiseControl: HeadsetProtocol.NoiseControlReading?,
        val safeControlMessage: String?,
        val log: List<String>,
    )

    /** Interrupts an in-flight connect/read and prevents the probe from continuing. */
    fun cancel() {
        cancelled = true
        try {
            activeSocket?.close()
        } catch (_: IOException) {
            // Best effort; the session close below is also idempotent.
        }
        activeSession?.close()
    }

    @SuppressLint("MissingPermission")
    fun run(
        verifyReversibleControl: Boolean = false,
        discoverReadOnlyCapabilities: Boolean = false,
        verifyIdempotentCapabilityWrites: Boolean = false,
        testEqRoundTrip: Boolean = false,
    ): Result {
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
        val session = HeadsetSession.connect(
            adapter = adapter,
            device = device,
            trace = ::trace,
            onSocketCreated = { activeSocket = it },
            shouldCancel = { cancelled },
        )
        activeSession = session
        try {
            trace("RFCOMM connected via ${session.service}")
            checkNotCancelled()

            val capabilityFrame = session.queryCapabilities()
            check(capabilityFrame.isResponse) {
                "Capability query returned a non-response frame"
            }
            val capabilities = HeadsetCapabilities.parse(capabilityFrame)
                ?: error("Capability response was invalid or reported failure status")
            trace("capability bitmap: ${HeadsetProtocol.hex(capabilities.rawBitmap)}")
            trace("advertised capability bits: ${capabilities.setBits.joinToString()}")
            if (capabilities.unknownBits.isNotEmpty()) {
                trace("unknown capability bits: ${capabilities.unknownBits.joinToString()}")
            }

            val batteryFrame = if (capabilities.supportsCommand(HeadsetProtocol.BATTERY_COMMAND)) {
                try {
                    checkNotCancelled()
                    session.queryBattery()
                } catch (error: Exception) {
                    rethrowIfCancelled()
                    trace("battery query failed: ${error.message}")
                    null
                }
            } else {
                trace("battery query skipped: command not advertised")
                null
            }
            val battery = batteryFrame?.let(HeadsetProtocol::parseBattery)
            if (battery != null) {
                trace(
                    "battery parsed: ${battery.percentage}%" +
                        if (battery.charging) " (charging)" else "",
                )
            } else if (batteryFrame != null) {
                trace("battery response was invalid")
            }

            val noiseFrame = if (capabilities.supportsCommand(HeadsetProtocol.NOISE_LEVEL_COMMAND)) {
                try {
                    checkNotCancelled()
                    session.queryNoiseControl()
                } catch (error: Exception) {
                    rethrowIfCancelled()
                    trace("noise-control read failed: ${error.message}")
                    null
                }
            } else {
                trace("noise-control read skipped: command not advertised")
                null
            }
            val noiseControl = noiseFrame?.let(HeadsetProtocol::parseNoiseControl)
            if (noiseControl != null) {
                trace(
                    "noise control: " +
                        HeadsetSemantics.noiseSummary(
                            action = noiseControl.action,
                            type = noiseControl.type,
                            value = noiseControl.value,
                        ) +
                        " (action=${noiseControl.action}, type=${noiseControl.type}, " +
                        "value=${noiseControl.value})",
                )
            } else if (noiseFrame != null) {
                trace("noise-control response was invalid")
            }

            val capabilityReadouts = if (discoverReadOnlyCapabilities) {
                runReadOnlyCapabilityDiscovery(session, capabilities)
            } else {
                emptyList()
            }

            val capabilityWriteMessage = if (verifyIdempotentCapabilityWrites) {
                runIdempotentCapabilityWriteChecks(session, capabilities)
            } else {
                null
            }

            val actualChangeMessage = if (testEqRoundTrip) {
                runEqProfileRoundTrip(session, capabilities)
            } else {
                null
            }

            val safeControlMessage = if (verifyReversibleControl) {
                checkNotCancelled()
                check(capabilities.supportsCommand(HeadsetProtocol.NOISE_LEVEL_COMMAND)) {
                    "Headset did not advertise noise-control reads"
                }
                check(capabilities.supportsCommand(HeadsetProtocol.SET_NOISE_REDUCTION_COMMAND)) {
                    "Headset did not advertise noise-control writes"
                }
                check(noiseControl != null) { "Could not read the current noise-control state" }
                check(noiseControl.action in 1..2 && noiseControl.type in 1..2) {
                    "Headset returned an unsupported noise-control state"
                }
                val setFrame = session.setNoiseControl(noiseControl)
                check(setFrame.isResponse) {
                    "Noise-control write returned a non-response frame"
                }
                val setStatus = HeadsetProtocol.parseSetStatus(setFrame)
                check(setStatus == 0) {
                    "Headset rejected noise-control write: status=$setStatus"
                }
                trace("reversible noise-control write accepted (status=0)")

                val afterFrame = session.queryNoiseControl()
                val after = HeadsetProtocol.parseNoiseControl(afterFrame)
                check(after == noiseControl) {
                    "Noise-control state changed unexpectedly after the idempotent write"
                }
                trace("noise-control state unchanged after verification")
                "Verified an idempotent noise-control write and read-back."
            } else {
                null
            }

            checkNotCancelled()
            return Result(
                deviceName = session.deviceName,
                service = session.service,
                capabilityFrame = capabilityFrame,
                capabilities = capabilities,
                capabilityReadouts = capabilityReadouts,
                capabilityWriteMessage = capabilityWriteMessage,
                actualChangeMessage = actualChangeMessage,
                batteryFrame = batteryFrame,
                battery = battery,
                noiseControl = noiseControl,
                safeControlMessage = safeControlMessage,
                log = snapshotTrace(),
            )
        } finally {
            if (activeSession === session) activeSession = null
            activeSocket = null
            session.close()
        }
    }

    private fun runIdempotentCapabilityWriteChecks(
        session: HeadsetSession,
        capabilities: HeadsetCapabilities,
    ): String {
        check(capabilities.supportsCommand(HeadsetProtocol.EQ_STATUS_COMMAND)) {
            "Headset did not advertise EQ status reads"
        }
        check(capabilities.supportsCommand(HeadsetProtocol.SET_EQ_STATUS_COMMAND)) {
            "Headset did not advertise EQ status writes"
        }
        check(capabilities.supportsCommand(HeadsetProtocol.KEY_FUNCTION_COMMAND)) {
            "Headset did not advertise button-mapping reads"
        }
        check(capabilities.supportsCommand(HeadsetProtocol.SET_BUTTON_MAPPING_COMMAND)) {
            "Headset did not advertise button-mapping writes"
        }
        check(capabilities.supportsCommand(HeadsetProtocol.SWITCH_FEATURE_COMMAND)) {
            "Headset did not advertise switch-feature reads"
        }
        check(capabilities.supportsCommand(HeadsetProtocol.SET_SWITCH_FEATURE_COMMAND)) {
            "Headset did not advertise switch-feature writes"
        }

        val eqQuery = HeadsetCapabilityDiscovery.readOnlyQueries.first {
            it.command == HeadsetProtocol.EQ_STATUS_COMMAND
        }
        val eqBeforeFrame = session.transact(eqQuery.command, eqQuery.payload)
        val eqBefore = HeadsetCapabilityDiscovery.parseEqStatus(eqBeforeFrame)
            ?: error("EQ status response was invalid")
        checkNotCancelled()
        checkWriteAccepted("EQ status", session.setEqStatus(eqBefore))
        val eqAfter = HeadsetCapabilityDiscovery.parseEqStatus(
            session.transact(eqQuery.command, eqQuery.payload),
        )
        check(eqAfter == eqBefore) { "EQ status changed after idempotent write" }
        trace("EQ status write accepted and read back unchanged")

        val switchQuery = HeadsetCapabilityDiscovery.readOnlyQueries.first {
            it.command == HeadsetProtocol.SWITCH_FEATURE_COMMAND
        }
        val switchBeforeFrame = session.transact(switchQuery.command, switchQuery.payload)
        val switchBefore = HeadsetCapabilityDiscovery.parseSwitchFeatureStatus(switchBeforeFrame)
            ?: error("Switch-feature response was invalid")
        val switchFeature = switchBefore.firstOrNull { it.featureId == 8 }
            ?: switchBefore.firstOrNull { it.value == 0 || it.value == 1 }
            ?: error("No boolean switch-feature value was available for an idempotent check")
        checkNotCancelled()
        checkWriteAccepted(
            "switch feature ${switchFeature.featureId}",
            session.setSwitchFeature(switchFeature.featureId, switchFeature.value == 1),
        )
        val switchAfter = HeadsetCapabilityDiscovery.parseSwitchFeatureStatus(
            session.transact(switchQuery.command, switchQuery.payload),
        )
        check(switchAfter == switchBefore) { "Switch-feature state changed after idempotent write" }
        trace("switch-feature write accepted and read back unchanged")

        val keyQuery = HeadsetCapabilityDiscovery.readOnlyQueries.first {
            it.command == HeadsetProtocol.KEY_FUNCTION_COMMAND
        }
        val keyBeforeFrame = session.transact(keyQuery.command, keyQuery.payload)
        val keyBefore = HeadsetCapabilityDiscovery.parseKeyMappings(keyBeforeFrame)
            ?: error("Button-mapping response was invalid")
        checkNotCancelled()
        checkWriteAccepted("button mappings", session.setButtonMappings(keyBefore))
        val keyAfter = HeadsetCapabilityDiscovery.parseKeyMappings(
            session.transact(keyQuery.command, keyQuery.payload),
        )
        check(keyAfter == keyBefore) { "Button mappings changed after idempotent write" }
        trace("button-mapping write accepted and read back unchanged")

        return "Verified idempotent EQ, switch-feature, and button-mapping writes with read-back."
    }

    private fun runEqProfileRoundTrip(
        session: HeadsetSession,
        capabilities: HeadsetCapabilities,
    ): String {
        check(capabilities.supportsCommand(HeadsetProtocol.EQ_STATUS_COMMAND)) {
            "Headset did not advertise EQ status reads"
        }
        check(capabilities.supportsCommand(HeadsetProtocol.SET_EQ_STATUS_COMMAND)) {
            "Headset did not advertise EQ status writes"
        }
        check(capabilities.supportsCommand(HeadsetProtocol.ALL_EQ_COMMAND)) {
            "Headset did not advertise EQ profile reads"
        }

        val eqQuery = HeadsetCapabilityDiscovery.readOnlyQueries.first {
            it.command == HeadsetProtocol.EQ_STATUS_COMMAND
        }
        val before = HeadsetCapabilityDiscovery.parseEqStatus(
            session.transact(eqQuery.command, eqQuery.payload),
        ) ?: error("EQ status response was invalid")
        check(before == 0) { "Expected current EQ ID 0 before the selected round-trip, got $before" }

        val profilesQuery = HeadsetCapabilityDiscovery.readOnlyQueries.first {
            it.command == HeadsetProtocol.ALL_EQ_COMMAND
        }
        val profiles = HeadsetCapabilityDiscovery.parseEqProfiles(
            session.transact(profilesQuery.command, profilesQuery.payload),
        ) ?: error("EQ profile response was invalid")
        check(profiles.any { it.id == 4 }) { "EQ profile ID 4 was not advertised by the read response" }

        var targetWriteAccepted = false
        try {
            checkNotCancelled()
            checkWriteAccepted("EQ profile 4", session.setEqStatus(4))
            targetWriteAccepted = true
            val selected = HeadsetCapabilityDiscovery.parseEqStatus(
                session.transact(eqQuery.command, eqQuery.payload),
            )
            check(selected == 4) { "EQ status did not change to profile 4: $selected" }
            trace("EQ profile changed to 4 and was read back")
        } finally {
            if (targetWriteAccepted) {
                try {
                    checkWriteAccepted("EQ profile restore", session.setEqStatus(before))
                    val restored = HeadsetCapabilityDiscovery.parseEqStatus(
                        session.transact(eqQuery.command, eqQuery.payload),
                    )
                    check(restored == before) { "EQ status restore read back as $restored, expected $before" }
                    trace("EQ profile restored to $before and was read back")
                } catch (error: Exception) {
                    throw IllegalStateException("EQ profile restore failed after a temporary change", error)
                }
            }
        }

        return "Temporarily selected EQ profile 4, verified the change, restored EQ profile 0, and verified the restore."
    }

    private fun checkWriteAccepted(label: String, frame: HeadsetProtocol.Frame) {
        check(frame.isResponse) { "$label write returned a non-response frame" }
        val status = HeadsetProtocol.parseSetStatus(frame)
        check(status == 0) { "$label write was rejected: status=$status" }
    }

    private fun runReadOnlyCapabilityDiscovery(
        session: HeadsetSession,
        capabilities: HeadsetCapabilities,
    ): List<HeadsetCapabilityDiscovery.Readout> {
        val readouts = mutableListOf<HeadsetCapabilityDiscovery.Readout>()
        for (excluded in HeadsetCapabilityDiscovery.excludedFromReadOnlyDiscovery) {
            if (capabilities.supportsCommand(excluded.command)) {
                trace("${excluded.title} excluded: ${excluded.reason}")
            }
        }
        for (query in HeadsetCapabilityDiscovery.readOnlyQueries) {
            checkNotCancelled()
            if (!capabilities.supportsCommand(query.command)) {
                trace("${query.title} skipped: command not advertised")
                continue
            }
            try {
                val frame = session.transact(query.command, query.payload)
                val summary = HeadsetCapabilityDiscovery.summarize(query, frame)
                trace("${query.title}: $summary")
                readouts += HeadsetCapabilityDiscovery.Readout(query, frame, summary)
            } catch (error: Exception) {
                rethrowIfCancelled()
                trace("${query.title} failed: ${error.message}")
                break
            }
        }
        return readouts
    }

    @SuppressLint("MissingPermission")
    private fun bluetoothAdapter(): BluetoothAdapter {
        return context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: BluetoothAdapter.getDefaultAdapter()
            ?: error("This phone does not expose Bluetooth")
    }

    private fun checkNotCancelled() {
        if (cancelled) throw CancellationException("Headset probe cancelled")
    }

    private fun rethrowIfCancelled() {
        if (cancelled) throw CancellationException("Headset probe cancelled")
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.isTarget(): Boolean {
        val name = name ?: return false
        return name.equals("realme Buds Wireless 5 ANC", ignoreCase = true) ||
            name.contains("Buds Wireless 5", ignoreCase = true)
    }

    private fun trace(message: String) {
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
