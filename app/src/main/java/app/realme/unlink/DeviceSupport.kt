package app.realme.unlink

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice

/** Bonded-device matching and naming for realme Buds Wireless 5 ANC. */
object DeviceSupport {
    const val DISPLAY_NAME = "realme Buds Wireless 5 ANC"

    @SuppressLint("MissingPermission")
    fun isTarget(device: BluetoothDevice): Boolean = isTargetName(device.name)

    internal fun isTargetName(name: String?): Boolean =
        name?.trim()?.equals(DISPLAY_NAME, ignoreCase = true) == true

    @SuppressLint("MissingPermission")
    fun displayName(device: BluetoothDevice): String = device.name ?: DISPLAY_NAME
}
