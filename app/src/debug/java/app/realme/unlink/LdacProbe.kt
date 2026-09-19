package app.realme.unlink

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log

/**
 * Debug probe to test setting and reading back switch features (such as switch ID 38 / LDAC support).
 */
class LdacProbe(private val context: Context) {
    companion object {
        const val TAG = "UnlinkLdacTest"
    }

    data class Result(
        val deviceName: String,
        val switchId: Int,
        val requestedValue: Boolean,
        val isReadOnly: Boolean = false,
        val txHex: String = "",
        val rxHex: String = "",
        val status: Int? = null,
        val baselineValue: Int?,
        val readbackValue: Int?,
        val rawCapabilities: String,
        val allSwitches: List<HeadsetCapabilityDiscovery.SwitchFeatureStatus> = emptyList(),
        val trace: List<String>,
    )

    @SuppressLint("MissingPermission")
    fun run(switchId: Int = 38, enable: Boolean = true, readOnly: Boolean = false): Result {
        val adapter = bluetoothAdapter()
        check(adapter.isEnabled) { "Bluetooth is turned off" }
        val device = adapter.bondedDevices.firstOrNull(DeviceSupport::isTarget)
            ?: error("${DeviceSupport.DISPLAY_NAME} is not bonded")

        val trace = mutableListOf<String>()
        val session = HeadsetSession.connect(
            adapter = adapter,
            device = device,
            trace = { msg ->
                Log.d(TAG, msg)
                trace.add(msg)
            },
        )

        return session.use {
            // 1. Query capabilities
            val capFrame = session.queryCapabilities()
            val capabilities = HeadsetCapabilities.parse(capFrame)
            val capHex = capabilities?.let { HeadsetProtocol.hex(it.rawBitmap) } ?: "none"
            val bit38 = capabilities?.hasBit(38) == true
            val bit24 = capabilities?.hasBit(24) == true
            Log.i(TAG, "Capabilities raw: $capHex, bit38=$bit38, bit24=$bit24, setBits=${capabilities?.setBits}")

            // 2. Query switch baseline
            val queryPayload = byteArrayOf(16, 7, 6, 4, 5, 11, 8, 17, 19, 24, 9, 26, 12, 29, 27, 54, 38)
            val queryFrame = session.transact(HeadsetProtocol.SWITCH_FEATURE_COMMAND, queryPayload)
            val statuses = HeadsetCapabilityDiscovery.parseSwitchFeatureStatus(queryFrame)
            val currentValue = statuses?.firstOrNull { it.featureId == switchId }?.value
            Log.i(TAG, "Switch status for $switchId: $currentValue, all parsed: $statuses")

            if (readOnly) {
                return@use Result(
                    deviceName = DeviceSupport.displayName(device),
                    switchId = switchId,
                    requestedValue = enable,
                    isReadOnly = true,
                    baselineValue = currentValue,
                    readbackValue = currentValue,
                    rawCapabilities = capHex,
                    allSwitches = statuses ?: emptyList(),
                    trace = trace.toList(),
                )
            }

            // 3. Send SET_SWITCH_FEATURE (0x0403)
            val writePayload = byteArrayOf(switchId.toByte(), if (enable) 1 else 0)
            val txBytes = HeadsetProtocol.encode(HeadsetProtocol.SET_SWITCH_FEATURE_COMMAND, 1, writePayload)
            val txHex = HeadsetProtocol.hex(txBytes)
            Log.i(TAG, "TX 0x0403 switch=$switchId enable=$enable: $txHex")

            val responseFrame = session.setSwitchFeature(switchId, enable)
            val rxHex = HeadsetProtocol.hex(responseFrame.raw())
            val status = HeadsetProtocol.parseSetStatus(responseFrame)
            Log.i(TAG, "RX 0x8403 status=$status raw=$rxHex")

            // 4. Readback verification
            val queryFrameAfter = session.transact(HeadsetProtocol.SWITCH_FEATURE_COMMAND, queryPayload)
            val statusesAfter = HeadsetCapabilityDiscovery.parseSwitchFeatureStatus(queryFrameAfter)
            val readback = statusesAfter?.firstOrNull { it.featureId == switchId }?.value
            Log.i(TAG, "Switch readback for $switchId: $readback, all parsed: $statusesAfter")

            Result(
                deviceName = DeviceSupport.displayName(device),
                switchId = switchId,
                requestedValue = enable,
                isReadOnly = false,
                txHex = txHex,
                rxHex = rxHex,
                status = status,
                baselineValue = currentValue,
                readbackValue = readback,
                rawCapabilities = capHex,
                allSwitches = statusesAfter ?: emptyList(),
                trace = trace.toList(),
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun bluetoothAdapter(): BluetoothAdapter =
        context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: error("This phone does not expose Bluetooth")
}
