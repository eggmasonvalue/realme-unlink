package app.realme.unlink

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Foreground-only headset session owner.
 *
 * Connects while the UI is active, keeps the RFCOMM socket open for seamless writes, and
 * disconnects when the UI backgrounds. Writes update local snapshot optimistically after a
 * successful status-0 response — they do **not** re-run the full discovery pass (that hung EQ).
 */
class HeadsetController(context: Context) {
    private val appContext = context.applicationContext
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val closed = AtomicBoolean(false)
    private val foreground = AtomicBoolean(false)
    private val generation = AtomicInteger(0)
    private val connecting = AtomicBoolean(false)

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

    /** Call from Activity.onResume. Safe to call repeatedly while already foreground. */
    fun onForeground() {
        if (closed.get()) return
        foreground.set(true)
        // Bump generation so a stale onPause disconnect cannot clobber a fresh connect.
        val gen = generation.incrementAndGet()
        Log.i(TAG, "onForeground gen=$gen session=${session != null}")
        emitState(ConnectionState.Connecting)
        executor.execute {
            if (!foreground.get() || gen != generation.get()) return@execute
            if (session != null && lastSnapshot != null) {
                Log.i(TAG, "Reusing live session")
                emitState(ConnectionState.Connected(lastSnapshot!!))
                return@execute
            }
            connectAndRefreshLocked(gen)
        }
    }

    fun onBackground() {
        Log.i(TAG, "onBackground")
        foreground.set(false)
        generation.incrementAndGet()
        executor.execute {
            disconnectLocked()
        }
    }

    fun refresh() {
        foreground.set(true)
        val gen = generation.incrementAndGet()
        executor.execute {
            if (!foreground.get()) return@execute
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

    fun setNoiseValue(value: Int) {
        write("noise control") { session, snap ->
            val baseline = snap.noise ?: error("Noise control is not available")
            val next = baseline.copy(value = value)
            checkWrite(session.setNoiseControl(next))
            snap.copy(noise = next)
        }
    }

    fun setEqId(eqId: Int) {
        write("equalizer") { session, snap ->
            checkWrite(session.setEqStatus(eqId))
            snap.copy(currentEqId = eqId)
        }
    }

    fun applyCustomEq(profile: HeadsetCapabilityDiscovery.EqProfile) {
        write("custom EQ") { session, snap ->
            checkWrite(session.setCustomEq(action = 2, profile = profile))
            checkWrite(session.setEqStatus(profile.id))
            val profiles = snap.eqProfiles.map {
                if (it.id == profile.id) profile.copy(selected = true) else it.copy(selected = false)
            }
            snap.copy(currentEqId = profile.id, eqProfiles = profiles)
        }
    }

    fun setBassBand(band: HeadsetCapabilityDiscovery.BassEngineValue) {
        write("bass") { session, snap ->
            checkWrite(session.setBassEngine(band))
            val bands = snap.bassBands.map {
                if (it.type == band.type) band else it
            }
            snap.copy(bassBands = bands)
        }
    }

    fun setSwitch(id: Int, enabled: Boolean) {
        write("feature") { session, snap ->
            val logical = snap.switches.firstOrNull { it.id == id || id in it.aliases }
            checkWrite(session.setSwitchFeature(id, enabled))
            logical?.aliases?.forEach { alias ->
                runCatching { checkWrite(session.setSwitchFeature(alias, enabled)) }
            }
            val switches = snap.switches.map { item ->
                if (item.id == id || id in item.aliases || item.id == logical?.id) {
                    item.copy(enabled = enabled)
                } else {
                    item
                }
            }
            snap.copy(switches = switches)
        }
    }

    fun setButtonMappings(mappings: List<HeadsetCapabilityDiscovery.KeyMapping>) {
        write("buttons") { session, snap ->
            checkWrite(session.setButtonMappings(mappings))
            snap.copy(buttonMappings = mappings)
        }
    }

    fun setFindMode(enabled: Boolean) {
        write("find device") { session, snap ->
            checkWrite(session.setFindMode(enabled))
            snap.copy(findModeActive = enabled)
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        foreground.set(false)
        generation.incrementAndGet()
        executor.execute {
            disconnectLocked()
            executor.shutdown()
        }
        listener.set(null)
    }

    private fun write(
        label: String,
        block: (HeadsetSession, HeadsetSnapshot) -> HeadsetSnapshot,
    ) {
        if (closed.get()) return
        val gen = generation.get()
        executor.execute {
            if (!foreground.get() || gen != generation.get()) return@execute
            emitBusy(true)
            try {
                var live = session
                var snap = lastSnapshot
                if (live == null || snap == null) {
                    connectAndRefreshLocked(gen)
                    live = session
                    snap = lastSnapshot
                }
                val sessionLive = live ?: error("Not connected")
                val before = snap ?: error("No snapshot")
                val after = block(sessionLive, before)
                if (!foreground.get() || gen != generation.get()) return@execute
                lastSnapshot = after
                emitState(ConnectionState.Connected(after))
            } catch (error: Exception) {
                Log.w(TAG, "Failed $label", error)
                handleSessionFailure(error, gen, userLabel = label)
            } finally {
                emitBusy(false)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectAndRefreshLocked(gen: Int) {
        if (!foreground.get() || gen != generation.get()) return
        if (!connecting.compareAndSet(false, true)) {
            Log.i(TAG, "connect already in flight")
            return
        }
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
                shouldCancel = { !foreground.get() || gen != generation.get() || closed.get() },
            )
            if (!foreground.get() || gen != generation.get()) {
                connected.close()
                return
            }
            session = connected
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
        } finally {
            connecting.set(false)
        }
    }

    private fun handleSessionFailure(
        error: Exception,
        gen: Int,
        userLabel: String? = null,
    ) {
        Log.w(TAG, "Session failure", error)
        disconnectLocked()
        // Stale generation / background: a newer onForeground will reconnect; don't flash an error.
        if (!foreground.get() || gen != generation.get()) return
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
                message = "Wake the earbuds and try again.",
                canRetry = true,
            ),
        )
        if (userLabel != null) {
            emitMessage("Couldn’t update $userLabel", isError = true)
        }
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

        val batteries = if (capabilities.supportsCommand(HeadsetProtocol.BATTERY_COMMAND)) {
            runCatching {
                HeadsetProtocol.parseBatteries(session.queryBattery()).orEmpty()
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val noise = if (capabilities.supportsCommand(HeadsetProtocol.NOISE_LEVEL_COMMAND)) {
            runCatching { HeadsetProtocol.parseNoiseControl(session.queryNoiseControl()) }
                .getOrNull()
        } else {
            null
        }

        val firmware = if (capabilities.supportsCommand(HeadsetProtocol.VERSION_COMMAND)) {
            runCatching {
                val frame = session.transact(HeadsetProtocol.VERSION_COMMAND)
                HeadsetCapabilityDiscovery.parseVersion(frame)
                    ?.firstOrNull { it.second == "2" || it.second == "3" }
                    ?.third
                    ?: HeadsetCapabilityDiscovery.parseVersion(frame)?.firstOrNull()?.third
            }.getOrNull()
        } else {
            null
        }

        val buttonMappings = if (capabilities.supportsCommand(HeadsetProtocol.KEY_FUNCTION_COMMAND)) {
            runCatching {
                val query = HeadsetCapabilityDiscovery.readOnlyQueries.first {
                    it.command == HeadsetProtocol.KEY_FUNCTION_COMMAND
                }
                HeadsetCapabilityDiscovery.parseKeyMappings(
                    session.transact(query.command, query.payload),
                ).orEmpty()
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val wear = if (capabilities.supportsCommand(HeadsetProtocol.WEAR_STATUS_COMMAND)) {
            runCatching {
                HeadsetCapabilityDiscovery.parseWearStatus(
                    session.transact(HeadsetProtocol.WEAR_STATUS_COMMAND),
                ).orEmpty()
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val switchStatuses = if (capabilities.supportsCommand(HeadsetProtocol.SWITCH_FEATURE_COMMAND)) {
            runCatching {
                val query = HeadsetCapabilityDiscovery.readOnlyQueries.first {
                    it.command == HeadsetProtocol.SWITCH_FEATURE_COMMAND
                }
                HeadsetCapabilityDiscovery.parseSwitchFeatureStatus(
                    session.transact(query.command, query.payload),
                ).orEmpty()
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val currentEqId = if (capabilities.supportsCommand(HeadsetProtocol.EQ_STATUS_COMMAND)) {
            runCatching {
                HeadsetCapabilityDiscovery.parseEqStatus(
                    session.transact(HeadsetProtocol.EQ_STATUS_COMMAND),
                )
            }.getOrNull()
        } else {
            null
        }

        val eqProfiles = if (capabilities.supportsCommand(HeadsetProtocol.ALL_EQ_COMMAND)) {
            runCatching {
                HeadsetCapabilityDiscovery.parseEqProfiles(
                    session.transact(HeadsetProtocol.ALL_EQ_COMMAND),
                ).orEmpty()
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val multipoint = if (capabilities.supportsCommand(HeadsetProtocol.MULTI_CONNECT_COMMAND)) {
            runCatching {
                HeadsetCapabilityDiscovery.parseMultipointStatus(
                    session.transact(HeadsetProtocol.MULTI_CONNECT_COMMAND),
                ).orEmpty()
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val bassBands = if (capabilities.supportsCommand(HeadsetProtocol.MULTI_BASS_ENGINE_COMMAND)) {
            runCatching {
                HeadsetCapabilityDiscovery.parseMultiBassEngine(
                    session.transact(HeadsetProtocol.MULTI_BASS_ENGINE_COMMAND),
                ).orEmpty()
            }.getOrDefault(emptyList())
        } else {
            emptyList()
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
    }
}
