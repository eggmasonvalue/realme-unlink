package app.realme.unlink

/** High-level UI connection / readiness state for the product surface. */
sealed class ConnectionState {
    data object Checking : ConnectionState()

    data object BluetoothOff : ConnectionState()

    data object PermissionNeeded : ConnectionState()

    data object NotBonded : ConnectionState()

    data object Connecting : ConnectionState()

    data class Connected(val snapshot: HeadsetSnapshot) : ConnectionState()

    data class Unavailable(
        val title: String,
        val message: String,
        val canRetry: Boolean = true,
    ) : ConnectionState()
}
