package app.realme.unlink

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context

/** Debug-only diagnostic that issues exactly one GET button-mapping transaction. */
class ReadOnlyButtonMappingProbe(private val context: Context) {
    data class Result(
        val deviceName: String,
        val rawPayload: ByteArray,
        val mappings: List<HeadsetCapabilityDiscovery.KeyMapping>,
        val trace: List<String>,
    )

    @SuppressLint("MissingPermission")
    fun run(): Result {
        val adapter = bluetoothAdapter()
        check(adapter.isEnabled) { "Bluetooth is turned off" }
        val device = adapter.bondedDevices.firstOrNull(DeviceSupport::isTarget)
            ?: error("${DeviceSupport.DISPLAY_NAME} is not bonded")
        val trace = mutableListOf<String>()
        val session = HeadsetSession.connect(
            adapter = adapter,
            device = device,
            trace = trace::add,
        )

        return session.use {
            val frame = session.queryButtonMappings()
            val mappings = HeadsetCapabilityDiscovery.parseKeyMappings(frame)
                ?: error("Button-mapping response was invalid or unsuccessful")
            Result(
                deviceName = DeviceSupport.displayName(device),
                rawPayload = frame.payload.copyOf(),
                mappings = mappings,
                trace = trace.toList(),
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun bluetoothAdapter(): BluetoothAdapter =
        context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: error("This phone does not expose Bluetooth")
}
