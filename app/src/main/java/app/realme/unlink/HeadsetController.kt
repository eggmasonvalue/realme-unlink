package app.realme.unlink

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import android.media.Ringtone
import android.media.RingtoneManager
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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

    @Volatile
    private var activeRingtone: Ringtone? = null

    fun toggleRingPhoneAlarm() {
        val ring = activeRingtone
        if (ring != null && ring.isPlaying) {
            runCatching { ring.stop() }
            activeRingtone = null
            emitMessage("Ring Phone stopped", isError = false)
        } else {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val newRingtone = RingtoneManager.getRingtone(appContext, uri)
            if (newRingtone != null) {
                newRingtone.play()
                activeRingtone = newRingtone
                emitMessage("🔔 Phone is ringing!", isError = false)
            } else {
                emitMessage("Could not play ringtone", isError = true)
            }
        }
    }

    sealed class UiEvent {
        data class State(val state: ConnectionState) : UiEvent()
        data class Message(val text: String, val isError: Boolean = false) : UiEvent()
        data class Busy(val busy: Boolean) : UiEvent()
    }

    fun setListener(onEvent: ((UiEvent) -> Unit)?) {
        listener.set(onEvent)
    }

    /** Call from Activity observer. Safe to call repeatedly while already foreground. */
    fun onForeground() {
        if (closed.get()) return
        foreground.set(true)
        val gen = generation.incrementAndGet()
        Log.i(TAG, "onForeground gen=$gen session=${session != null}")
        emitState(ConnectionState.Connecting)
        scope.launch(Dispatchers.IO) {
            if (!foreground.get() || gen != generation.get()) return@launch
            if (session != null && lastSnapshot != null) {
                Log.i(TAG, "Reusing live session")
                emitState(ConnectionState.Connected(lastSnapshot!!))
                return@launch
            }
            connectAndRefreshLocked(gen)
        }
    }

    fun onBackground() {
        Log.i(TAG, "onBackground")
        foreground.set(false)
        generation.incrementAndGet()
        scope.launch(Dispatchers.IO) {
            disconnectLocked()
        }
    }

    fun refresh() {
        foreground.set(true)
        val gen = generation.incrementAndGet()
        scope.launch(Dispatchers.IO) {
            if (!foreground.get()) return@launch
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
        write(
            label = "noise control",
            optimisticUpdate = { snap ->
                val baseline = snap.noise ?: HeadsetProtocol.NoiseControlReading(1, 1, value)
                snap.copy(noise = baseline.copy(value = value))
            },
            performWrite = { session ->
                checkWrite(session.setNoiseControl(HeadsetProtocol.NoiseControlReading(1, 1, value)))
            },
        )
    }

    fun setEqId(eqId: Int) {
        write(
            label = "equalizer",
            optimisticUpdate = { snap ->
                val profiles = snap.eqProfiles.map {
                    it.copy(selected = (it.id == eqId))
                }
                snap.copy(currentEqId = eqId, eqProfiles = profiles)
            },
            performWrite = { session ->
                checkWrite(session.setEqStatus(eqId))
            },
        )
    }

    fun applyCustomEq(profile: HeadsetCapabilityDiscovery.EqProfile) {
        write(
            label = "custom EQ",
            optimisticUpdate = { snap ->
                val profiles = snap.eqProfiles.map {
                    if (it.id == profile.id) profile.copy(selected = true) else it.copy(selected = false)
                }
                snap.copy(currentEqId = profile.id, eqProfiles = profiles)
            },
            performWrite = { session ->
                checkWrite(session.setCustomEq(action = 2, profile = profile))
                checkWrite(session.setEqStatus(profile.id))
            },
        )
    }

    fun setBassBand(band: HeadsetCapabilityDiscovery.BassEngineValue) {
        write(
            label = "bass",
            optimisticUpdate = { snap ->
                val bands = snap.bassBands.map {
                    if (it.type == band.type) band else it
                }
                snap.copy(bassBands = bands)
            },
            performWrite = { session ->
                checkWrite(session.setBassEngine(band))
            },
        )
    }

    fun setSwitch(id: Int, enabled: Boolean) {
        write(
            label = "feature",
            optimisticUpdate = { snap ->
                val logical = snap.switches.firstOrNull { it.id == id || id in it.aliases }
                val switches = snap.switches.map { item ->
                    if (item.id == id || id in item.aliases || item.id == logical?.id) {
                        item.copy(enabled = enabled)
                    } else {
                        item
                    }
                }
                snap.copy(switches = switches)
            },
            performWrite = { session ->
                val logical = lastSnapshot?.switches?.firstOrNull { it.id == id || id in it.aliases }
                checkWrite(session.setSwitchFeature(id, enabled))
                logical?.aliases?.forEach { alias ->
                    runCatching { checkWrite(session.setSwitchFeature(alias, enabled)) }
                }
            },
        )
    }

    fun setGameMode(enabled: Boolean) = setSwitch(SwitchCatalog.GAMING_PRIMARY_ID, enabled)
    fun setVolumeEnhancer(enabled: Boolean) = setSwitch(9, enabled)
    fun setDualDevice(enabled: Boolean) = setSwitch(17, enabled)
    fun setSmartWindNoise(enabled: Boolean) = setSwitch(26, enabled)
    fun setDynamicBass(enabled: Boolean) = setSwitch(29, enabled)
    fun setHdAudio(enabled: Boolean) = setSwitch(38, enabled)

    fun setButtonMappings(mappings: List<HeadsetCapabilityDiscovery.KeyMapping>) {
        write(
            label = "buttons",
            optimisticUpdate = { snap ->
                snap.copy(buttonMappings = mappings)
            },
            performWrite = { session ->
                checkWrite(session.setButtonMappings(mappings))
            },
        )
    }

    /**
     * Update a single button mapping by merging with the latest snapshot state,
     * avoiding stale-closure issues when multiple rapid changes are made in the UI.
     */
    fun setButtonMapping(changed: HeadsetCapabilityDiscovery.KeyMapping) {
        write(
            label = "buttons",
            optimisticUpdate = { snap ->
                val updated = snap.buttonMappings.map {
                    if (it.button == changed.button && it.action == changed.action) changed else it
                }
                snap.copy(buttonMappings = updated)
            },
            performWrite = { session ->
                // Read the latest snapshot (which includes the optimistic update)
                // to build the full list for the wire protocol
                val current = lastSnapshot?.buttonMappings ?: return@write
                checkWrite(session.setButtonMappings(current))
            },
        )
    }


    fun setFindMode(enabled: Boolean) {
        write(
            label = "find device",
            optimisticUpdate = { snap ->
                snap.copy(findModeActive = enabled)
            },
            performWrite = { session ->
                checkWrite(session.setFindMode(enabled))
            },
        )
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        foreground.set(false)
        generation.incrementAndGet()
        scope.launch(Dispatchers.IO) {
            disconnectLocked()
        }
        scope.cancel()
        listener.set(null)
    }

    private fun write(
        label: String,
        optimisticUpdate: (HeadsetSnapshot) -> HeadsetSnapshot,
        performWrite: (HeadsetSession) -> Unit,
    ) {
        if (closed.get()) return
        val gen = generation.get()
        scope.launch(Dispatchers.IO) {
            if (!foreground.get() || gen != generation.get()) return@launch
            val currentSnap = lastSnapshot ?: return@launch
            val beforeSnap = currentSnap
            val optimisticSnap = optimisticUpdate(beforeSnap)

            // Optimistically update and emit immediately
            lastSnapshot = optimisticSnap
            emitState(ConnectionState.Connected(optimisticSnap))

            try {
                var live = session
                if (live == null) {
                    connectAndRefreshLocked(gen)
                    live = session
                }
                val sessionLive = live ?: error("Not connected")
                performWrite(sessionLive)
            } catch (error: Exception) {
                Log.w(TAG, "Failed $label", error)
                // Rollback optimistic update on error
                lastSnapshot = beforeSnap
                emitState(ConnectionState.Connected(beforeSnap))
                handleSessionFailure(error, gen, userLabel = label)
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
            connected.onUnsolicitedFrame = { frame ->
                Log.i(TAG, "Unsolicited frame from neckband: command=0x${frame.command.toString(16)} payload=${HeadsetProtocol.hex(frame.payload)}")
                if (frame.command == 0x0501 && frame.payload.isNotEmpty() && (frame.payload[0] == 10.toByte() || frame.payload.contains(10.toByte()))) {
                    toggleRingPhoneAlarm()
                }
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
                message = "Separate magnetic earbud clasps to power on neckband",
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
