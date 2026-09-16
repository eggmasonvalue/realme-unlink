package app.realme.unlink

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Foreground-only headset session owner.
 *
 * Connects while the UI is active, keeps the RFCOMM socket open for seamless writes, and
 * disconnects when the UI backgrounds. ALL transactions run off the UI thread on [Dispatchers.IO].
 * Writes update local snapshot optimistically with rollback on error.
 */
class HeadsetController(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val closed = AtomicBoolean(false)
    private val findModeMayBeActive = AtomicBoolean(false)
    private val lifecycleGate = SessionLifecycleGate()
    private val operationMutex = Mutex()

    @Volatile
    private var session: HeadsetSession? = null

    @Volatile
    private var activeSocket: BluetoothSocket? = null

    @Volatile
    private var lastSnapshot: HeadsetSnapshot? = null

    private val listener = AtomicReference<((UiEvent) -> Unit)?>(null)

    sealed class UiEvent {
        data class State(val state: ConnectionState) : UiEvent()
        data class Message(val text: String, val isError: Boolean = false) : UiEvent()
        data class Busy(val busy: Boolean) : UiEvent()
    }

    fun setListener(onEvent: ((UiEvent) -> Unit)?) {
        listener.set(onEvent)
    }

    /** Call from the Activity observer. Duplicate foreground callbacks are ignored. */
    fun onForeground() {
        if (closed.get()) return
        val gen = lifecycleGate.enterForeground() ?: return
        Log.i(TAG, "onForeground gen=$gen")
        emitState(ConnectionState.Connecting)
        scope.launch {
            operationMutex.withLock {
                if (!lifecycleGate.isCurrentForeground(gen)) return@withLock
                // Every foreground lifetime owns a fresh session. A previous onStop may already
                // have closed its socket even when its asynchronous cleanup has not acquired the lock.
                stopFindModeBestEffort("before reconnecting")
                disconnectLocked()
                connectAndRefreshLocked(gen)
            }
        }
    }

    fun onBackground() {
        val gen = lifecycleGate.leaveForeground() ?: return
        Log.i(TAG, "onBackground gen=$gen")
        val shouldStopFindMode = findModeMayBeActive.get() || lastSnapshot?.findModeActive == true
        if (!shouldStopFindMode) {
            // Cancel an in-flight read immediately. The generation guard below prevents this older
            // lifecycle callback from disconnecting a newer foreground session.
            runCatching { activeSocket?.close() }
        }
        scope.launch {
            operationMutex.withLock {
                if (!lifecycleGate.isCurrentBackground(gen)) return@withLock
                if (shouldStopFindMode) stopFindModeBestEffort("during backgrounding")
                disconnectLocked()
            }
        }
    }

    fun refresh() {
        if (closed.get()) return
        val gen = lifecycleGate.advanceForeground() ?: return
        scope.launch {
            operationMutex.withLock {
                if (!lifecycleGate.isCurrentForeground(gen)) return@withLock
                try {
                    emitBusy(true)
                    emitState(ConnectionState.Connecting)
                    disconnectLocked()
                    connectAndRefreshLocked(gen)
                } catch (error: Exception) {
                    handleSessionFailure(error, gen)
                } finally {
                    emitBusy(false)
                }
            }
        }
    }

    fun setNoiseValue(value: Int) {
        write(
            label = "noise control",
            optimisticUpdate = { snap ->
                check(snap.capabilities.supportsCommand(HeadsetProtocol.SET_NOISE_REDUCTION_COMMAND)) {
                    "Noise-control writes were not advertised"
                }
                val baseline = checkNotNull(snap.noise) { "Noise-control state is unavailable" }
                require(value in WRITABLE_NOISE_VALUES) { "Unsupported noise-control value" }
                snap.copy(noise = baseline.copy(value = value))
            },
            performWrite = { session, _ ->
                checkWrite(session.setNoiseControl(HeadsetProtocol.NoiseControlReading(1, 1, value)))
            },
        )
    }

    fun setEqId(eqId: Int) {
        write(
            label = "equalizer",
            optimisticUpdate = { snap ->
                check(snap.capabilities.supportsCommand(HeadsetProtocol.SET_EQ_STATUS_COMMAND)) {
                    "EQ selection writes were not advertised"
                }
                check(!snap.readFailed(HeadsetProtocol.EQ_STATUS_COMMAND) && snap.currentEqId != null) {
                    "Current EQ state is unavailable"
                }
                require(eqId == 0 || snap.eqProfiles.any { it.id == eqId }) {
                    "Unknown EQ profile"
                }
                val profiles = snap.eqProfiles.map {
                    it.copy(selected = (it.id == eqId))
                }
                snap.copy(currentEqId = eqId, eqProfiles = profiles)
            },
            performWrite = { session, _ ->
                checkWrite(session.setEqStatus(eqId))
            },
        )
    }

    fun applyCustomEq(profile: HeadsetCapabilityDiscovery.EqProfile) {
        val snapshot = lastSnapshot ?: return
        runCatching { snapshot.requireCompatibleCustomEq(profile) }
            .onFailure {
                Log.w(TAG, "Rejected incompatible custom EQ request", it)
                emitMessage("Custom EQ is unavailable for this headset state", isError = true)
            }
            .getOrNull() ?: return
        write(
            label = "custom EQ",
            optimisticUpdate = { snap ->
                val validated = snap.requireCompatibleCustomEq(profile)
                val profiles = snap.eqProfiles.map {
                    if (it.id == validated.id) validated else it.copy(selected = false)
                }
                snap.copy(currentEqId = validated.id, eqProfiles = profiles)
            },
            performWrite = { session, before ->
                val baseline = checkNotNull(before.customEqProfile)
                val validated = before.requireCompatibleCustomEq(profile)
                checkWrite(session.setCustomEq(action = 2, profile = validated))
                try {
                    checkWrite(session.setEqStatus(validated.id))
                } catch (selectionError: Exception) {
                    runCatching {
                        checkWrite(session.setCustomEq(action = 2, profile = baseline))
                        before.currentEqId?.let { checkWrite(session.setEqStatus(it)) }
                    }.exceptionOrNull()?.let(selectionError::addSuppressed)
                    throw selectionError
                }
            },
        )
    }

    fun setBassBand(band: HeadsetCapabilityDiscovery.BassEngineValue) {
        write(
            label = "bass",
            optimisticUpdate = { snap ->
                check(snap.capabilities.supportsCommand(HeadsetProtocol.SET_BASS_ENGINE_COMMAND)) {
                    "Bass writes were not advertised"
                }
                val baseline = snap.bassBands.firstOrNull { it.type == band.type }
                    ?: error("Bass band is unavailable")
                require(band.currentValue in baseline.minValue..baseline.maxValue) {
                    "Bass value is outside the headset-provided range"
                }
                val validated = baseline.copy(currentValue = band.currentValue)
                val bands = snap.bassBands.map {
                    if (it.type == validated.type) validated else it
                }
                snap.copy(bassBands = bands)
            },
            performWrite = { session, before ->
                val baseline = before.bassBands.firstOrNull { it.type == band.type }
                    ?: error("Bass band is unavailable")
                require(band.currentValue in baseline.minValue..baseline.maxValue)
                checkWrite(session.setBassEngine(baseline.copy(currentValue = band.currentValue)))
            },
        )
    }

    fun setSwitch(id: Int, enabled: Boolean) {
        write(
            label = "feature",
            optimisticUpdate = { snap ->
                require(id in SwitchCatalog.writableIds) { "Unsupported feature ID" }
                check(snap.switches.any { it.id == id }) { "Feature is unavailable" }
                val switches = snap.switches.map { item ->
                    if (item.id == id) item.copy(enabled = enabled) else item
                }
                snap.copy(switches = switches)
            },
            performWrite = { session, _ ->
                checkWrite(session.setSwitchFeature(id, enabled))
            },
        )
    }

    fun setGameMode(enabled: Boolean) = setSwitch(SwitchCatalog.GAMING_PRIMARY_ID, enabled)
    fun setDualDevice(enabled: Boolean) = setSwitch(17, enabled)
    fun setSmartWindNoise(enabled: Boolean) = setSwitch(26, enabled)
    fun setDynamicBass(enabled: Boolean) = setSwitch(29, enabled)

    /**
     * Update a single button mapping by merging with the latest snapshot state,
     * avoiding stale-closure issues when multiple rapid changes are made in the UI.
     */
    fun setButtonMapping(changed: HeadsetCapabilityDiscovery.KeyMapping) {
        write(
            label = "buttons",
            optimisticUpdate = { snap ->
                require(changed.button == 1 && changed.action in 1..4) {
                    "Only confirmed MFB slots are writable"
                }
                require(changed.function in WRITABLE_BUTTON_FUNCTIONS) {
                    "Unsupported MFB function"
                }
                check(snap.buttonMappings.any {
                    it.deviceType == changed.deviceType &&
                        it.button == changed.button && it.action == changed.action
                }) { "Button slot is unavailable" }
                val updated = snap.buttonMappings.map {
                    if (it.button == changed.button && it.action == changed.action) changed else it
                }
                snap.copy(buttonMappings = updated)
            },
            performWrite = { session, _ ->
                // Read the latest snapshot (which includes the optimistic update)
                // to build the full list for the wire protocol
                val current = lastSnapshot?.buttonMappings ?: return@write
                checkWrite(session.setButtonMappings(current))
            },
        )
    }


    fun setFindMode(enabled: Boolean) {
        write(
            label = FIND_MODE_LABEL,
            optimisticUpdate = { snap ->
                check(snap.capabilities.supportsCommand(HeadsetProtocol.SET_FIND_MODE_COMMAND)) {
                    "Find mode was not advertised"
                }
                snap.copy(findModeActive = enabled)
            },
            performWrite = { session, _ ->
                if (enabled) findModeMayBeActive.set(true)
                checkWrite(session.setFindMode(enabled))
                findModeMayBeActive.set(enabled)
            },
        )
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        lifecycleGate.close()
        val shouldStopFindMode = findModeMayBeActive.get() || lastSnapshot?.findModeActive == true
        if (!shouldStopFindMode) runCatching { activeSocket?.close() }
        scope.launch(kotlinx.coroutines.NonCancellable) {
            operationMutex.withLock {
                if (shouldStopFindMode) stopFindModeBestEffort("while closing")
                disconnectLocked()
            }
            scope.cancel()
        }
        listener.set(null)
    }

    private fun write(
        label: String,
        optimisticUpdate: (HeadsetSnapshot) -> HeadsetSnapshot,
        performWrite: (HeadsetSession, HeadsetSnapshot) -> Unit,
    ) {
        if (closed.get()) return
        val gen = lifecycleGate.currentForeground() ?: return
        scope.launch {
            operationMutex.withLock {
                if (!lifecycleGate.isCurrentForeground(gen)) return@withLock
                val beforeSnap = lastSnapshot ?: return@withLock
                val optimisticSnap = try {
                    optimisticUpdate(beforeSnap)
                } catch (error: IllegalArgumentException) {
                    Log.w(TAG, "Rejected invalid $label request", error)
                    emitMessage("Invalid $label request", isError = true)
                    return@withLock
                } catch (error: IllegalStateException) {
                    Log.w(TAG, "$label is unavailable", error)
                    emitMessage("$label is unavailable", isError = true)
                    return@withLock
                }

                lastSnapshot = optimisticSnap
                emitState(ConnectionState.Connected(optimisticSnap))

                try {
                    var live = session
                    if (live == null) {
                        connectAndRefreshLocked(gen)
                        live = session
                    }
                    val sessionLive = live ?: error("Not connected")
                    performWrite(sessionLive, beforeSnap)
                } catch (error: Exception) {
                    Log.w(TAG, "Failed $label", error)
                    if (lifecycleGate.isCurrentForeground(gen)) {
                        if (label == FIND_MODE_LABEL && findModeMayBeActive.get()) {
                            disconnectLocked()
                            connectAndRefreshLocked(gen)
                            if (!findModeMayBeActive.get() && session != null) {
                                emitMessage("Find mode was reset after a connection error", isError = true)
                                return@withLock
                            }
                        }
                        lastSnapshot = beforeSnap
                        emitState(ConnectionState.Connected(beforeSnap))
                        handleSessionFailure(error, gen, userLabel = label)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectAndRefreshLocked(gen: Int) {
        if (!lifecycleGate.isCurrentForeground(gen)) return
        emitState(ConnectionState.Connecting)
        try {
            val adapter = bluetoothAdapter()
                ?: run {
                    emitState(
                        ConnectionState.Unavailable(
                            title = "Bluetooth unavailable",
                            message = "This phone does not expose a Bluetooth adapter.",
                            canRetry = false,
                        ),
                    )
                    return
                }
            if (!adapter.isEnabled) {
                emitState(ConnectionState.BluetoothOff)
                return
            }

            val bonded = adapter.bondedDevices.orEmpty().toList()
            val device = bonded.firstOrNull { DeviceSupport.isTarget(it) }
            if (device == null) {
                emitState(ConnectionState.NotBonded)
                return
            }

            disconnectLocked()
            Log.i(TAG, "Connecting to ${DeviceSupport.displayName(device)}")
            val connected = HeadsetSession.connect(
                adapter = adapter,
                device = device,
                trace = { Log.d(TAG, it) },
                onSocketCreated = { activeSocket = it },
                shouldCancel = { !lifecycleGate.isCurrentForeground(gen) || closed.get() },
            )
            if (!lifecycleGate.isCurrentForeground(gen)) {
                connected.close()
                return
            }
            session = connected
            if (findModeMayBeActive.get()) {
                checkWrite(connected.setFindMode(false))
                findModeMayBeActive.set(false)
            }
            lastSnapshot = null
            val snapshot = readSnapshot(connected)
            lastSnapshot = snapshot
            Log.i(
                TAG,
                "Connected battery=${snapshot.primaryBatteryPercent} " +
                    "wear=${snapshot.wear.joinToString { "t${it.deviceType}:f${it.flags}/ear=${it.inEar}" }} " +
                    "eq=${snapshot.currentEqId} noise=${snapshot.noise?.value}",
            )
            emitState(ConnectionState.Connected(snapshot))
        } catch (error: Exception) {
            handleSessionFailure(error, gen)
        }
    }

    private fun handleSessionFailure(
        error: Exception,
        gen: Int,
        userLabel: String? = null,
    ) {
        Log.w(TAG, "Session failure", error)
        disconnectLocked()
        if (!lifecycleGate.isCurrentForeground(gen)) return
        val message = error.message.orEmpty()
        if (message.contains("cancel", ignoreCase = true)) {
            emitState(ConnectionState.Connecting)
            return
        }
        val title = when {
            message.contains("timed out", ignoreCase = true) -> "Earbuds not responding"
            else -> "Couldn’t reach the earbuds"
        }
        emitState(
            ConnectionState.Unavailable(
                title = title,
                message = "Separate magnetic earbud clasps to power on neckband",
                canRetry = true,
            ),
        )
        if (userLabel != null) {
            emitMessage("Couldn’t update $userLabel", isError = true)
        }
    }

    private fun stopFindModeBestEffort(context: String) {
        if (!findModeMayBeActive.get() && lastSnapshot?.findModeActive != true) return
        var lastError: Throwable? = null
        repeat(FIND_MODE_STOP_ATTEMPTS) { attempt ->
            val result = runCatching {
                val live = checkNotNull(session) { "No session available" }
                checkWrite(live.setFindMode(false))
            }
            if (result.isSuccess) {
                findModeMayBeActive.set(false)
                return
            }
            lastError = result.exceptionOrNull()
            Log.w(TAG, "Could not stop find mode $context (attempt ${attempt + 1})", lastError)
        }
        Log.e(TAG, "Find mode may still be active $context", lastError)
    }

    private fun disconnectLocked() {
        try {
            session?.close()
        } catch (_: Exception) {
        }
        session = null
        try {
            activeSocket?.close()
        } catch (_: IOException) {
        }
        activeSocket = null
    }

    private fun readSnapshot(session: HeadsetSession): HeadsetSnapshot {
        val capabilityFrame = session.queryCapabilities()
        val capabilities = HeadsetCapabilities.parse(capabilityFrame)
            ?: error("Capability response was invalid")
        val readFailures = linkedSetOf<Int>()

        fun <T> readFeature(command: Int, fallback: T, block: () -> T): T {
            if (!capabilities.supportsCommand(command)) return fallback
            return try {
                block()
            } catch (error: IOException) {
                // HeadsetSession closes itself after transport failures; a partial snapshot would
                // incorrectly present a dead socket as connected.
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Could not decode feature command 0x${command.toString(16)}", error)
                readFailures += command
                fallback
            }
        }

        val batteries = readFeature(HeadsetProtocol.BATTERY_COMMAND, emptyList()) {
            HeadsetProtocol.parseBatteries(session.queryBattery())
                ?: error("Battery response was invalid")
        }
        val noise = readFeature<HeadsetProtocol.NoiseControlReading?>(
            HeadsetProtocol.NOISE_LEVEL_COMMAND,
            null,
        ) {
            HeadsetProtocol.parseNoiseControl(session.queryNoiseControl())
                ?: error("Noise-control response was invalid")
        }
        val firmware = readFeature<String?>(HeadsetProtocol.VERSION_COMMAND, null) {
            val versions = HeadsetCapabilityDiscovery.parseVersion(
                session.transact(HeadsetProtocol.VERSION_COMMAND),
            ) ?: error("Version response was invalid")
            versions.firstOrNull { it.second == "2" || it.second == "3" }?.third
                ?: versions.firstOrNull()?.third
                ?: error("Version response was empty")
        }
        val buttonMappings = readFeature(HeadsetProtocol.KEY_FUNCTION_COMMAND, emptyList()) {
            val query = HeadsetCapabilityDiscovery.readOnlyQueries.first {
                it.command == HeadsetProtocol.KEY_FUNCTION_COMMAND
            }
            HeadsetCapabilityDiscovery.parseKeyMappings(
                session.transact(query.command, query.payload),
            ) ?: error("Button-mapping response was invalid")
        }
        val wear = readFeature(HeadsetProtocol.WEAR_STATUS_COMMAND, emptyList()) {
            HeadsetCapabilityDiscovery.parseWearStatus(
                session.transact(HeadsetProtocol.WEAR_STATUS_COMMAND),
            ) ?: error("Wear-status response was invalid")
        }
        val switchStatuses = readFeature(HeadsetProtocol.SWITCH_FEATURE_COMMAND, emptyList()) {
            val query = HeadsetCapabilityDiscovery.readOnlyQueries.first {
                it.command == HeadsetProtocol.SWITCH_FEATURE_COMMAND
            }
            HeadsetCapabilityDiscovery.parseSwitchFeatureStatus(
                session.transact(query.command, query.payload),
            ) ?: error("Switch-feature response was invalid")
        }
        val currentEqId = readFeature<Int?>(HeadsetProtocol.EQ_STATUS_COMMAND, null) {
            HeadsetCapabilityDiscovery.parseEqStatus(
                session.transact(HeadsetProtocol.EQ_STATUS_COMMAND),
            ) ?: error("EQ-status response was invalid")
        }
        val eqProfiles = readFeature(HeadsetProtocol.ALL_EQ_COMMAND, emptyList()) {
            HeadsetCapabilityDiscovery.parseEqProfiles(
                session.transact(HeadsetProtocol.ALL_EQ_COMMAND),
            ) ?: error("EQ-profile response was invalid")
        }
        val multipoint = readFeature(HeadsetProtocol.MULTI_CONNECT_COMMAND, emptyList()) {
            HeadsetCapabilityDiscovery.parseMultipointStatus(
                session.transact(HeadsetProtocol.MULTI_CONNECT_COMMAND),
            ) ?: error("Multipoint response was invalid")
        }
        val bassBands = readFeature(HeadsetProtocol.MULTI_BASS_ENGINE_COMMAND, emptyList()) {
            HeadsetCapabilityDiscovery.parseMultiBassEngine(
                session.transact(HeadsetProtocol.MULTI_BASS_ENGINE_COMMAND),
            ) ?: error("Bass response was invalid")
        }

        return HeadsetSnapshot(
            deviceName = session.deviceName,
            serviceLabel = session.service,
            firmwareVersion = firmware,
            batteries = batteries.map {
                HeadsetSnapshot.BatteryLevel(it.deviceType, it.percentage, it.charging)
            },
            noise = noise,
            currentEqId = currentEqId,
            eqProfiles = eqProfiles,
            bassBands = bassBands,
            switches = SwitchCatalog.logicalSwitches(switchStatuses),
            buttonMappings = buttonMappings,
            wear = wear,
            multipoint = multipoint,
            findModeActive = false,
            capabilities = capabilities,
            readFailures = readFailures,
        )
    }

    private fun checkWrite(frame: HeadsetProtocol.Frame) {
        val status = HeadsetProtocol.parseSetStatus(frame)
        check(status == 0) { "Headset rejected write (status=$status)" }
    }

    private fun bluetoothAdapter(): BluetoothAdapter? {
        val manager = appContext.getSystemService(BluetoothManager::class.java) ?: return null
        return manager.adapter
    }

    private fun emitState(state: ConnectionState) {
        listener.get()?.invoke(UiEvent.State(state))
    }

    private fun emitMessage(text: String, isError: Boolean) {
        listener.get()?.invoke(UiEvent.Message(text, isError))
    }

    private fun emitBusy(busy: Boolean) {
        listener.get()?.invoke(UiEvent.Busy(busy))
    }

    private companion object {
        const val TAG = "UnlinkController"
        const val FIND_MODE_STOP_ATTEMPTS = 2
        const val FIND_MODE_LABEL = "find device"
        val WRITABLE_NOISE_VALUES = setOf(1, 2, 4, 8, 16, 32)
        val WRITABLE_BUTTON_FUNCTIONS = setOf(0, 1, 4, 5, 6, 17)
    }
}
