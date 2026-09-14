package app.realme.unlink

/** Immutable product-facing snapshot of headset state. */
data class HeadsetSnapshot(
    val deviceName: String,
    val serviceLabel: String,
    val firmwareVersion: String?,
    val batteries: List<BatteryLevel>,
    val noise: HeadsetProtocol.NoiseControlReading?,
    val currentEqId: Int?,
    val eqProfiles: List<HeadsetCapabilityDiscovery.EqProfile>,
    val bassBands: List<HeadsetCapabilityDiscovery.BassEngineValue>,
    val switches: List<SwitchCatalog.LogicalSwitch>,
    val buttonMappings: List<HeadsetCapabilityDiscovery.KeyMapping>,
    val wear: List<HeadsetCapabilityDiscovery.WearStatus>,
    val multipoint: List<HeadsetCapabilityDiscovery.MultipointEntry>,
    val findModeActive: Boolean = false,
    val capabilities: HeadsetCapabilities,
) {
    data class BatteryLevel(
        val deviceType: Int,
        val percentage: Int,
        val charging: Boolean,
    ) {
        val role: String
            get() = when (deviceType) {
                1 -> "Left"
                2 -> "Right"
                3 -> "Case"
                else -> "Bud"
            }
    }

    val primaryBatteryPercent: Int?
        get() {
            val buds = batteries.filter { it.deviceType == 1 || it.deviceType == 2 }
            if (buds.isNotEmpty()) return buds.minOf { it.percentage }
            return batteries.firstOrNull()?.percentage
        }

    val anyCharging: Boolean
        get() = batteries.any { it.charging }

    val customEqProfile: HeadsetCapabilityDiscovery.EqProfile?
        get() = eqProfiles.firstOrNull { it.id == 4 }
            ?: eqProfiles.firstOrNull { it.name.contains("Custom", ignoreCase = true) }

    val currentEqLabel: String
        get() {
            val id = currentEqId ?: return "Unknown"
            eqProfiles.firstOrNull { it.id == id }?.name?.let { return it }
            return when (id) {
                0 -> "Original"
                else -> "Profile $id"
            }
        }

    val gameModeEnabled: Boolean?
        get() = switches.firstOrNull { it.id == 8 || 8 in it.aliases }?.enabled

    val volumeEnhancerEnabled: Boolean?
        get() = switches.firstOrNull { it.id == 9 }?.enabled

    val dualDeviceEnabled: Boolean?
        get() = switches.firstOrNull { it.id == 17 }?.enabled

    val smartWindNoiseEnabled: Boolean?
        get() = switches.firstOrNull { it.id == 26 }?.enabled

    val dynamicBassEnabled: Boolean?
        get() = switches.firstOrNull { it.id == 29 }?.enabled

    val hdAudioEnabled: Boolean?
        get() = switches.firstOrNull { it.id == 38 }?.enabled
}
