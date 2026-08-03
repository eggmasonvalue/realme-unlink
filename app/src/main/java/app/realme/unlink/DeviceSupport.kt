package app.realme.unlink

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice

/** Bonded-device matching and naming for realme Buds Wireless 5 ANC. */
object DeviceSupport {
    const val DISPLAY_NAME = "realme Buds Wireless 5 ANC"

    @SuppressLint("MissingPermission")
    fun isTarget(device: BluetoothDevice): Boolean {
        val name = device.name ?: return false
        return name.equals(DISPLAY_NAME, ignoreCase = true) ||
            name.contains("Buds Wireless 5", ignoreCase = true)
    }

    @SuppressLint("MissingPermission")
    fun displayName(device: BluetoothDevice): String = device.name ?: DISPLAY_NAME
}
