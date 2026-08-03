package app.realme.unlink

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * A serialized command session for the headset's RFCOMM control service.
 *
 * The session owns the socket and the one reader used to enforce response timeouts. Closing the
 * socket is the cancellation mechanism for a blocked Bluetooth read, so callers can stop a probe
 * without leaving a reader thread behind.
 */
class HeadsetSession private constructor(
    val deviceName: String,
    val service: String,
    private val input: DataInputStream,
    private val output: BufferedOutputStream,
    private val closeTransport: () -> Unit,
    private val responseTimeoutMillis: Long,
    private val trace: (String) -> Unit,
) : AutoCloseable {
    companion object {
        private const val DEFAULT_RESPONSE_TIMEOUT_MILLIS = 6_000L
        private const val MAX_IGNORED_FRAMES = 8
        private val services = listOf(
            HeadsetProtocol.controlUuid to "realme control UUID",
            HeadsetProtocol.sppUuid to "SPP fallback",
        )

        @SuppressLint("MissingPermission")
        fun connect(
            adapter: BluetoothAdapter,
            device: BluetoothDevice,
            trace: (String) -> Unit,
            onSocketCreated: (BluetoothSocket) -> Unit = {},
            shouldCancel: () -> Boolean = { false },
        ): HeadsetSession {
            var lastError: Exception? = null
            for ((uuid, label) in services) {
                if (shouldCancel()) throw CancellationException("Headset connection cancelled")

                var candidate: BluetoothSocket? = null
                try {
                    trace("trying $label")
                    adapter.cancelDiscovery()
                    candidate = device.createRfcommSocketToServiceRecord(uuid)
                    onSocketCreated(candidate)
                    if (shouldCancel()) {
                        throw CancellationException("Headset connection cancelled")
                    }
                    candidate.connect()
                    if (shouldCancel()) {
                        throw CancellationException("Headset connection cancelled")
                    }

                    val input = DataInputStream(BufferedInputStream(candidate.inputStream))
                    val output = BufferedOutputStream(candidate.outputStream)
                    val connectedSocket = candidate
                        ?: error("Headset socket was not created")
                    return HeadsetSession(
                        deviceName = device.name ?: "realme headset",
                        service = label,
                        input = input,
                        output = output,
                        closeTransport = { connectedSocket.close() },
                        responseTimeoutMillis = DEFAULT_RESPONSE_TIMEOUT_MILLIS,
                        trace = trace,
                    )
                } catch (error: Exception) {
                    try {
                        candidate?.close()
                    } catch (_: IOException) {
                        // Best effort before trying the fallback service.
                    }
                    if (shouldCancel()) {
                        throw CancellationException("Headset connection cancelled")
                    }
                    lastError = error
                    trace("$label failed: ${error.message ?: error.javaClass.simpleName}")
                }
            }
            throw IOException("Could not open a headset control service", lastError)
        }

        /** Creates a stream-backed session for protocol/session tests without a Bluetooth socket. */
        internal fun forTesting(
            input: java.io.InputStream,
            output: java.io.OutputStream,
            responseTimeoutMillis: Long = DEFAULT_RESPONSE_TIMEOUT_MILLIS,
            trace: (String) -> Unit = {},
        ): HeadsetSession {
            require(responseTimeoutMillis > 0) { "Response timeout must be positive" }
            val bufferedInput = DataInputStream(BufferedInputStream(input))
            val bufferedOutput = BufferedOutputStream(output)
            return HeadsetSession(
                deviceName = "test headset",
                service = "test transport",
                input = bufferedInput,
                output = bufferedOutput,
                closeTransport = {
                    try {
                        bufferedInput.close()
                    } finally {
                        bufferedOutput.close()
                    }
                },
                responseTimeoutMillis = responseTimeoutMillis,
                trace = trace,
            )
        }
    }

    private val transactionLock = Any()
    private val readerExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    @Volatile
    private var closed = false
    private var nextTransferId = 0

    /** Sends one command and waits for its matching response; concurrent calls are serialized. */
    fun transact(command: Int, payload: ByteArray = byteArrayOf()): HeadsetProtocol.Frame {
        synchronized(transactionLock) {
            check(!closed) { "Headset session is closed" }

            val transferId = nextTransferId and 0xFF
            nextTransferId = (nextTransferId + 1) and 0xFF
            val packet = HeadsetProtocol.encode(command, transferId, payload)
            val wireFrames = HeadsetProtocol.wrapFrames(packet)
            trace(
                "tx 0x${command.toString(16).uppercase()} id=$transferId " +
                    "packet=${HeadsetProtocol.hex(packet)} frames=${wireFrames.size}",
            )

            try {
                wireFrames.forEachIndexed { index, wire ->
                    trace("tx frame ${index + 1}/${wireFrames.size}: ${HeadsetProtocol.hex(wire)}")
                    output.write(wire)
                    output.flush()
                }
            } catch (error: IOException) {
                close()
                throw IOException("Failed writing headset command", error)
            }

            repeat(MAX_IGNORED_FRAMES) {
                val frame = readWithTimeout()
                trace(
                    "rx 0x${frame.command.toString(16).uppercase()} id=${frame.transferId} " +
                        tracePayload(frame.command, frame.payload),
                )
                if (frame.isResponse && frame.baseCommand == command && frame.transferId == transferId) {
                    return frame
                }
                trace("ignored unsolicited/non-matching frame")
            }

            close()
            throw IOException("No response for command 0x${command.toString(16)}")
        }
    }

    fun queryCapabilities(): HeadsetProtocol.Frame = transact(HeadsetProtocol.CAPABILITY_COMMAND)

    fun queryBattery(): HeadsetProtocol.Frame = transact(HeadsetProtocol.BATTERY_COMMAND)

    fun queryNoiseControl(): HeadsetProtocol.Frame {
        // This model uses the two-byte left/right selector used by realme Link.
        return transact(HeadsetProtocol.NOISE_LEVEL_COMMAND, byteArrayOf(1, 1))
    }

    fun setButtonMappings(entries: List<HeadsetCapabilityDiscovery.KeyMapping>): HeadsetProtocol.Frame {
        require(entries.isNotEmpty()) { "At least one button mapping is required" }
        require(entries.size <= 0xFF) { "Too many button mappings" }
        val payload = ByteArray(1 + entries.size * 4)
        payload[0] = entries.size.toByte()
        entries.forEachIndexed { index, mapping ->
            val offset = 1 + index * 4
            payload[offset] = mapping.deviceType.toByte()
            payload[offset + 1] = mapping.button.toByte()
            payload[offset + 2] = mapping.action.toByte()
            payload[offset + 3] = mapping.function.toByte()
        }
        return transact(HeadsetProtocol.SET_BUTTON_MAPPING_COMMAND, payload)
    }

    fun setSwitchFeature(featureId: Int, enabled: Boolean): HeadsetProtocol.Frame {
        require(featureId in 0..0xFF) { "Feature ID must fit in 8 bits" }
        return transact(
            HeadsetProtocol.SET_SWITCH_FEATURE_COMMAND,
            byteArrayOf(featureId.toByte(), if (enabled) 1 else 0),
        )
    }

    fun setEqStatus(eqId: Int): HeadsetProtocol.Frame {
        require(eqId in 0..0xFF) { "EQ ID must fit in 8 bits" }
        return transact(HeadsetProtocol.SET_EQ_STATUS_COMMAND, byteArrayOf(eqId.toByte()))
    }

    fun setNoiseControl(state: HeadsetProtocol.NoiseControlReading): HeadsetProtocol.Frame {
        return transact(
            HeadsetProtocol.SET_NOISE_REDUCTION_COMMAND,
            byteArrayOf(state.action.toByte(), state.type.toByte(), state.value.toByte()),
        )
    }

    fun setCustomEq(
        action: Int,
        profile: HeadsetCapabilityDiscovery.EqProfile,
    ): HeadsetProtocol.Frame {
        return transact(
            HeadsetProtocol.SET_CUSTOM_EQ_COMMAND,
            HeadsetProtocol.encodeCustomEqWrite(action, profile),
        )
    }

    fun setBassEngine(value: HeadsetCapabilityDiscovery.BassEngineValue): HeadsetProtocol.Frame {
        return transact(
            HeadsetProtocol.SET_BASS_ENGINE_COMMAND,
            HeadsetProtocol.encodeBassEngineWrite(value),
        )
    }

    fun setFindMode(enabled: Boolean): HeadsetProtocol.Frame {
        return transact(
            HeadsetProtocol.SET_FIND_MODE_COMMAND,
            byteArrayOf(if (enabled) 1 else 0),
        )
    }

    private fun readWithTimeout(): HeadsetProtocol.Frame {
        val read: Future<HeadsetProtocol.Frame> = try {
            readerExecutor.submit<HeadsetProtocol.Frame> {
                HeadsetProtocol.readFrame(input)
            }
        } catch (error: RejectedExecutionException) {
            throw IOException("Headset session is closed", error)
        }

        return try {
            read.get(responseTimeoutMillis, TimeUnit.MILLISECONDS)
        } catch (error: TimeoutException) {
            read.cancel(true)
            close()
            throw IOException("Timed out reading headset response", error)
        } catch (error: InterruptedException) {
            read.cancel(true)
            Thread.currentThread().interrupt()
            close()
            throw IOException("Interrupted while reading headset response", error)
        } catch (error: ExecutionException) {
            close()
            val cause = error.cause ?: error
            throw IOException("Failed reading headset response: ${cause.message}", cause)
        } catch (error: CancellationException) {
            close()
            throw IOException("Headset response read was cancelled", error)
        }
    }

    private fun tracePayload(command: Int, payload: ByteArray): String {
        return if ((command and 0x7FFF) == HeadsetProtocol.MULTI_CONNECT_COMMAND) {
            "[${payload.size} bytes redacted]"
        } else {
            HeadsetProtocol.hex(payload)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            closeTransport()
        } catch (_: IOException) {
            // Closing is best effort; the blocked reader still gets interrupted below.
        }
        readerExecutor.shutdownNow()
    }
}
