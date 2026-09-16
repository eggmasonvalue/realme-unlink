package app.realme.unlink

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import java.io.IOException

class ButtonMappingPersistenceProbe(private val context: Context) {
    companion object {
        const val TAG = "UnlinkButtonTest"

        val CONFIRMED_BASELINE: List<HeadsetCapabilityDiscovery.KeyMapping> = listOf(
            HeadsetCapabilityDiscovery.KeyMapping(1, 1, 1, 1),
            HeadsetCapabilityDiscovery.KeyMapping(1, 1, 2, 6),
            HeadsetCapabilityDiscovery.KeyMapping(1, 1, 3, 5),
            HeadsetCapabilityDiscovery.KeyMapping(1, 1, 4, 3),
            HeadsetCapabilityDiscovery.KeyMapping(1, 4, 1, 8),
        )
    }

    @Volatile
    private var activeSocket: BluetoothSocket? = null

    @Volatile
    private var activeSession: HeadsetSession? = null

    data class MappingTestResult(
        val btnLabel: String,
        val actionLabel: String,
        val originalFunction: Int,
        val targetFunction: Int,
        val writeStatus: Int?,
        val writeError: String?,
        val immediateReadFunction: Int?,
        val read1sFunction: Int?,
        val read3sFunction: Int?,
        val read5sFunction: Int?,
        val rapidReadSuccess: Boolean,
        val persistedAcrossReads: Boolean,
        val notes: String,
    )

    data class ComprehensiveResult(
        val deviceName: String,
        val baselineMappings: List<HeadsetCapabilityDiscovery.KeyMapping>,
        val testResults: List<MappingTestResult>,
        val bulkWriteResult: String,
        val reconnectTestResult: String,
        val finalRestoreVerified: Boolean,
        val summaryFindings: List<String>,
    )

    /**
     * Recovers the exact, empirically confirmed factory mapping after an interrupted probe.
     * The write is allowed only when the headset reports the same five physical mapping slots,
     * then verified both immediately and across a fresh RFCOMM connection.
     */
    @SuppressLint("MissingPermission")
    fun restoreConfirmedBaseline(): String {
        val adapter = bluetoothAdapter()
        check(adapter.isEnabled) { "Bluetooth is disabled" }
        val device = adapter.bondedDevices.firstOrNull { it.isTarget() }
            ?: error("Target neckband not bonded")
        var writeAttempted = false

        try {
            var session = getOrOpenSession(adapter, device)
            val current = readButtonMappings(session)
            val identity: (HeadsetCapabilityDiscovery.KeyMapping) -> Triple<Int, Int, Int> = {
                Triple(it.deviceType, it.button, it.action)
            }
            check(current.map(identity).toSet() == CONFIRMED_BASELINE.map(identity).toSet() &&
                current.size == CONFIRMED_BASELINE.size
            ) {
                "Refusing baseline recovery: mapping slots differ from confirmed hardware: $current"
            }

            val expectedByIdentity = CONFIRMED_BASELINE.associateBy(identity)
            val target = current.map { expectedByIdentity.getValue(identity(it)) }
            if (current != target) {
                writeAttempted = true
                val status = HeadsetProtocol.parseSetStatus(session.setButtonMappings(target))
                check(status == 0) { "Baseline recovery rejected with status=$status" }
                check(readButtonMappings(session) == target) {
                    "Baseline recovery failed immediate read-back"
                }
            }

            session = forceReconnect(adapter, device)
            check(readButtonMappings(session) == target) {
                "Baseline recovery did not persist across RFCOMM reconnect"
            }
            return "Confirmed baseline restored and verified across reconnect: $target"
        } catch (failure: Exception) {
            if (writeAttempted) {
                try {
                    val recovery = forceReconnect(adapter, device)
                    val status = HeadsetProtocol.parseSetStatus(
                        recovery.setButtonMappings(CONFIRMED_BASELINE),
                    )
                    check(status == 0 && readButtonMappings(recovery) == CONFIRMED_BASELINE) {
                        "Emergency baseline restoration could not be verified"
                    }
                } catch (restoreFailure: Exception) {
                    failure.addSuppressed(restoreFailure)
                    Log.e(TAG, "EMERGENCY BASELINE RESTORATION FAILED", restoreFailure)
                }
            }
            throw failure
        } finally {
            closeActiveSession()
        }
    }

    /**
     * Validates one conservative remap per reported slot. Every attempted mutation is followed by
     * immediate and reconnect read-back, then a confirmed-baseline restore with the same checks.
     */
    @SuppressLint("MissingPermission")
    fun validateConservativeRemaps(): List<String> {
        val adapter = bluetoothAdapter()
        check(adapter.isEnabled) { "Bluetooth is disabled" }
        val device = adapter.bondedDevices.firstOrNull { it.isTarget() }
            ?: error("Target neckband not bonded")
        val results = mutableListOf<String>()
        var session = getOrOpenSession(adapter, device)
        val baseline = readButtonMappings(session)
        check(baseline == CONFIRMED_BASELINE) {
            "Refusing remap validation: expected confirmed baseline, got $baseline"
        }
        val alternates = listOf(6, 1, 1, 1, 1)

        try {
            baseline.indices.forEach { index ->
                val target = baseline.toMutableList().also {
                    it[index] = it[index].copy(function = alternates[index])
                }
                var writeAttempted = false
                try {
                    writeAttempted = true
                    val status = HeadsetProtocol.parseSetStatus(session.setButtonMappings(target))
                    if (status != 0) {
                        results += "slot $index rejected conservative remap with status=$status"
                    } else {
                        val immediate = readButtonMappings(session)
                        val applied = immediate == target
                        Thread.sleep(1_000)
                        session = forceReconnect(adapter, device)
                        val afterReconnect = readButtonMappings(session)
                        val persisted = afterReconnect == target
                        results += "slot $index applied=$applied persisted=$persisted " +
                            "f${baseline[index].function}→f${alternates[index]}"
                    }
                } finally {
                    if (writeAttempted) {
                        session = forceReconnect(adapter, device)
                        val restoreStatus = HeadsetProtocol.parseSetStatus(
                            session.setButtonMappings(CONFIRMED_BASELINE),
                        )
                        check(restoreStatus == 0) {
                            "slot $index baseline restore rejected with status=$restoreStatus"
                        }
                        check(readButtonMappings(session) == CONFIRMED_BASELINE) {
                            "slot $index baseline restore failed immediate read-back"
                        }
                        session = forceReconnect(adapter, device)
                        check(readButtonMappings(session) == CONFIRMED_BASELINE) {
                            "slot $index baseline restore failed reconnect read-back"
                        }
                    }
                }
            }
            return results
        } finally {
            closeActiveSession()
        }
    }

    @Deprecated(
        message = "Unsafe exhaustive mutation matrix; use validateConservativeRemaps",
        level = DeprecationLevel.ERROR,
    )
    @Suppress("UNREACHABLE_CODE")
    @SuppressLint("MissingPermission")
    fun run(): ComprehensiveResult {
        error("Unsafe exhaustive button mutation matrix is disabled")
        Log.i(TAG, "==========================================================")
        Log.i(TAG, "=== STARTING HARDWARE BUTTON MAPPING PERSISTENCE PROBE ===")
        Log.i(TAG, "==========================================================")

        val adapter = bluetoothAdapter()
        check(adapter.isEnabled) { "Bluetooth is disabled" }

        val bondedDevices = adapter.bondedDevices.toList()
        val device = bondedDevices.firstOrNull { it.isTarget() }
            ?: error("Target neckband not bonded. Bonded: ${bondedDevices.map { it.name }}")

        Log.i(TAG, "Connected target device: ${device.name} (${device.address})")

        val findings = mutableListOf<String>()
        val testResults = mutableListOf<MappingTestResult>()

        var session = getOrOpenSession(adapter, device)

        try {
            // 1. Initial Capability Query & Warm-up (Ensures RFCOMM session is ready)
            val capsFrame = session.queryCapabilities()
            val caps = HeadsetCapabilities.parse(capsFrame)
            Log.i(TAG, "Headset capabilities verified: setBits=${caps?.setBits?.joinToString()}")

            // 2. Read baseline button mappings
            val baseline = readButtonMappings(session)
            Log.i(TAG, "BASELINE BUTTON MAPPINGS COUNT: ${baseline.size}")
            baseline.forEachIndexed { i, m ->
                val btnName = HeadsetSemantics.buttonControlLabel(m.deviceType, m.button)
                val actionName = HeadsetSemantics.buttonActionLabel(m.action)
                val funcName = HeadsetSemantics.buttonFunctionLabel(m.function)
                Log.i(TAG, "  Baseline [$i]: $btnName | $actionName (${m.action}) -> $funcName (${m.function}) [dev=${m.deviceType}, btn=${m.button}]")
            }
            findings.add("Baseline mappings (${baseline.size}): " + baseline.joinToString {
                "d${it.deviceType}/b${it.button}/a${it.action}→f${it.function}"
            })

            // Candidate functions to test per mapping
            val candidateFunctions = listOf(
                1,  // Play / Pause
                2,  // Switch Device
                3,  // Voice Assistant (verified)
                4,  // Unknown
                5,  // Previous Track
                6,  // Next Track
                7,  // Game Mode
                8,  // Volume Up
                9,  // Volume Down
                0,  // Off
            )

            // 3. Test EACH mapping entry individually
            for (index in baseline.indices) {
                val orig = baseline[index]
                val btnName = HeadsetSemantics.buttonControlLabel(orig.deviceType, orig.button)
                val actionName = HeadsetSemantics.buttonActionLabel(orig.action)

                Log.i(TAG, "----------------------------------------------------------")
                Log.i(TAG, "Testing Mapping [$index]: $btnName - $actionName (Current Function: ${orig.function} = ${HeadsetSemantics.buttonFunctionLabel(orig.function)})")
                Log.i(TAG, "----------------------------------------------------------")

                // Pick alternate functions different from current function
                val alternates = candidateFunctions.filter { it != orig.function }.take(2)

                for (altFunc in alternates) {
                    val altFuncName = HeadsetSemantics.buttonFunctionLabel(altFunc)
                    Log.i(TAG, ">> Target: Change $btnName $actionName -> $altFuncName (func ID $altFunc)")

                    // Re-read baseline list to ensure session is live and we start from known state
                    session = getOrOpenSession(adapter, device)
                    val currentList = try {
                        readButtonMappings(session)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed reading button mappings before write: ${e.message}, reconnecting...")
                        session = forceReconnect(adapter, device)
                        readButtonMappings(session)
                    }

                    val modifiedList = currentList.toMutableList()
                    if (index < modifiedList.size) {
                        modifiedList[index] = orig.copy(function = altFunc)
                    } else {
                        Log.w(TAG, "Index $index out of bounds in current list size ${modifiedList.size}")
                        continue
                    }

                    var writeStatus: Int? = null
                    var writeError: String? = null

                    try {
                        val responseFrame = session.setButtonMappings(modifiedList)
                        writeStatus = HeadsetProtocol.parseSetStatus(responseFrame)
                        Log.i(TAG, "Write response status=$writeStatus")
                    } catch (e: Exception) {
                        writeError = e.message ?: e.javaClass.simpleName
                        Log.e(TAG, "Write transaction EXCEPTION: $writeError", e)
                        closeActiveSession()
                    }

                    if (writeStatus == null || writeStatus != 0) {
                        val note = if (writeError != null) "Exception during write: $writeError" else "Hardware rejected write status=$writeStatus"
                        Log.w(TAG, "Write failed for $btnName $actionName -> $altFuncName: $note")
                        testResults.add(
                            MappingTestResult(
                                btnLabel = btnName,
                                actionLabel = actionName,
                                originalFunction = orig.function,
                                targetFunction = altFunc,
                                writeStatus = writeStatus,
                                writeError = writeError,
                                immediateReadFunction = null,
                                read1sFunction = null,
                                read3sFunction = null,
                                read5sFunction = null,
                                rapidReadSuccess = false,
                                persistedAcrossReads = false,
                                notes = note
                            )
                        )
                        // Restore baseline before continuing
                        session = getOrOpenSession(adapter, device)
                        runCatching { session.setButtonMappings(baseline) }
                        continue
                    }

                    // --- Step 4. Readbacks ---
                    // Read #1: Immediate read (0ms)
                    session = getOrOpenSession(adapter, device)
                    val read0 = runCatching { readButtonMappings(session).getOrNull(index)?.function }.getOrNull()
                    Log.i(TAG, "Immediate Read (0ms): func=$read0 (${read0?.let { HeadsetSemantics.buttonFunctionLabel(it) }})")

                    // Read #2: 1 second delay
                    Thread.sleep(1000)
                    session = getOrOpenSession(adapter, device)
                    val read1 = runCatching { readButtonMappings(session).getOrNull(index)?.function }.getOrNull()
                    Log.i(TAG, "Read after 1s: func=$read1")

                    // Read #3: 3 seconds delay
                    Thread.sleep(2000)
                    session = getOrOpenSession(adapter, device)
                    val read3 = runCatching { readButtonMappings(session).getOrNull(index)?.function }.getOrNull()
                    Log.i(TAG, "Read after 3s: func=$read3")

                    // Read #4: 5 seconds delay
                    Thread.sleep(2000)
                    session = getOrOpenSession(adapter, device)
                    val read5 = runCatching { readButtonMappings(session).getOrNull(index)?.function }.getOrNull()
                    Log.i(TAG, "Read after 5s: func=$read5")

                    // Read #5: 5 rapid consecutive reads
                    Log.i(TAG, "Executing 5 rapid consecutive reads...")
                    var rapidOk = true
                    repeat(5) { rIdx ->
                        val rFunc = runCatching { readButtonMappings(session).getOrNull(index)?.function }.getOrNull()
                        if (rFunc != altFunc) {
                            rapidOk = false
                            Log.w(TAG, "Rapid read #$rIdx mismatch: expected $altFunc, got $rFunc")
                        }
                    }

                    val appliedImmediate = (read0 == altFunc)
                    val retainedDelays = (read1 == altFunc && read3 == altFunc && read5 == altFunc)
                    val persistedAll = appliedImmediate && retainedDelays && rapidOk

                    val note = when {
                        !appliedImmediate -> "ACCEPTED (status=0) BUT NOT APPLIED (Immediate read=$read0)"
                        !retainedDelays -> "APPLIED initially, but RESET to func=$read5 after delay!"
                        !rapidOk -> "APPLIED initially, but RESET during rapid re-reads!"
                        else -> "PERSISTED cleanly across all immediate, delay, and rapid readbacks"
                    }

                    Log.i(TAG, "VERDICT for $btnName $actionName -> $altFuncName: $note")
                    findings.add("$btnName $actionName -> $altFuncName (func $altFunc): $note")

                    testResults.add(
                        MappingTestResult(
                            btnLabel = btnName,
                            actionLabel = actionName,
                            originalFunction = orig.function,
                            targetFunction = altFunc,
                            writeStatus = writeStatus,
                            writeError = null,
                            immediateReadFunction = read0,
                            read1sFunction = read1,
                            read3sFunction = read3,
                            read5sFunction = read5,
                            rapidReadSuccess = rapidOk,
                            persistedAcrossReads = persistedAll,
                            notes = note
                        )
                    )

                    // Restore baseline mapping
                    session = getOrOpenSession(adapter, device)
                    runCatching { session.setButtonMappings(baseline) }
                    Thread.sleep(300)
                }
            }

            // 5. Test Bulk Modified Mappings & Disconnect / Reconnect Persistence
            Log.i(TAG, "----------------------------------------------------------")
            Log.i(TAG, "=== Testing Bulk Write & RFCOMM Disconnect/Reconnect ===")
            Log.i(TAG, "----------------------------------------------------------")

            session = getOrOpenSession(adapter, device)
            val bulkModified = baseline.map { orig ->
                val alt = if (orig.function == 1) 2 else 1
                orig.copy(function = alt)
            }

            var bulkWriteMsg = ""
            val bulkStatus = runCatching {
                val frame = session.setButtonMappings(bulkModified)
                HeadsetProtocol.parseSetStatus(frame)
            }.getOrNull()

            val bulkReadImmediate = runCatching { readButtonMappings(session) }.getOrNull()
            if (bulkReadImmediate == bulkModified) {
                bulkWriteMsg = "Bulk write status=$bulkStatus: Accepted & Applied immediately across all keys!"
            } else {
                bulkWriteMsg = "Bulk write status=$bulkStatus: Accepted but immediate readback mismatch (got $bulkReadImmediate)"
            }
            Log.i(TAG, bulkWriteMsg)
            findings.add(bulkWriteMsg)

            // Disconnect RFCOMM, wait 5s, reconnect and readback
            Log.i(TAG, "Closing RFCOMM session to test persistent hardware storage across disconnects...")
            closeActiveSession()

            Thread.sleep(5000)

            Log.i(TAG, "Reconnecting RFCOMM session...")
            session = getOrOpenSession(adapter, device)
            val readAfterReconnect = runCatching { readButtonMappings(session) }.getOrNull()

            var reconnectMsg = ""
            if (readAfterReconnect == bulkModified) {
                reconnectMsg = "HARDWARE PERSISTENCE CONFIRMED: Modified button mappings retained after RFCOMM disconnect/reconnect!"
                Log.i(TAG, reconnectMsg)
            } else if (readAfterReconnect == baseline) {
                reconnectMsg = "HARDWARE RESET ON DISCONNECT: Button mappings RESET back to factory defaults upon RFCOMM disconnect!"
                Log.w(TAG, reconnectMsg)
            } else {
                reconnectMsg = "PARTIAL MAPPING ON RECONNECT: Readback after reconnect = $readAfterReconnect"
                Log.w(TAG, reconnectMsg)
            }
            findings.add(reconnectMsg)

            // 6. Restore Original Baseline Mappings
            Log.i(TAG, "=== Restoring Original Baseline Mappings ===")
            session = getOrOpenSession(adapter, device)
            session.setButtonMappings(baseline)
            Thread.sleep(500)
            val finalRead = readButtonMappings(session)
            val restoredOk = (finalRead == baseline)
            Log.i(TAG, "Baseline restoration verified=$restoredOk")
            findings.add("Baseline restoration verified=$restoredOk")

            // Summary Statistics
            val total = testResults.size
            val persisted = testResults.count { it.persistedAcrossReads }
            val notApplied = testResults.count { it.writeStatus == 0 && it.immediateReadFunction != it.targetFunction }
            val resetAfterDelay = testResults.count { it.immediateReadFunction == it.targetFunction && !it.persistedAcrossReads }
            val rejected = testResults.count { it.writeStatus != 0 || it.writeError != null }

            findings.add("SUMMARY: total_tests=$total, persisted=$persisted, not_applied=$notApplied, reset_after_delay=$resetAfterDelay, rejected=$rejected")

            Log.i(TAG, "==========================================================")
            Log.i(TAG, "=== PERSISTENCE PROBE COMPLETE ===")
            Log.i(TAG, "  Total Test Runs: $total")
            Log.i(TAG, "  Persisted Cleanly: $persisted")
            Log.i(TAG, "  Accepted but Not Applied: $notApplied")
            Log.i(TAG, "  Reset After Delay/Reads: $resetAfterDelay")
            Log.i(TAG, "  Rejected / Write Exceptions: $rejected")
            Log.i(TAG, "==========================================================")

            return ComprehensiveResult(
                deviceName = session.deviceName,
                baselineMappings = baseline,
                testResults = testResults,
                bulkWriteResult = bulkWriteMsg,
                reconnectTestResult = reconnectMsg,
                finalRestoreVerified = restoredOk,
                summaryFindings = findings
            )
        } finally {
            closeActiveSession()
        }
    }

    private fun readButtonMappings(session: HeadsetSession): List<HeadsetCapabilityDiscovery.KeyMapping> {
        val query = HeadsetCapabilityDiscovery.readOnlyQueries.first {
            it.command == HeadsetProtocol.KEY_FUNCTION_COMMAND
        }
        val frame = session.transact(query.command, query.payload)
        return HeadsetCapabilityDiscovery.parseKeyMappings(frame)
            ?: error("Failed to parse button mappings response from command 0x0108")
    }

    @SuppressLint("MissingPermission")
    private fun getOrOpenSession(adapter: BluetoothAdapter, device: BluetoothDevice): HeadsetSession {
        val current = activeSession
        if (current != null) return current
        return forceReconnect(adapter, device)
    }

    @SuppressLint("MissingPermission")
    private fun forceReconnect(adapter: BluetoothAdapter, device: BluetoothDevice): HeadsetSession {
        closeActiveSession()
        Log.i(TAG, "Opening fresh HeadsetSession RFCOMM transport...")
        val session = HeadsetSession.connect(
            adapter = adapter,
            device = device,
            trace = { Log.d(TAG, it) },
            onSocketCreated = { activeSocket = it },
            shouldCancel = { false }
        )
        activeSession = session
        return session
    }

    private fun closeActiveSession() {
        val session = activeSession
        activeSession = null
        activeSocket = null
        try {
            session?.close()
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun bluetoothAdapter(): BluetoothAdapter {
        return context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()
            ?: error("No Bluetooth adapter found")
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.isTarget(): Boolean {
        val name = name ?: return false
        return name.equals("realme Buds Wireless 5 ANC", ignoreCase = true) ||
            name.contains("Buds Wireless 5", ignoreCase = true)
    }
}
