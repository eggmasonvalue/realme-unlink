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
    val readFailures: Set<Int> = emptySet(),
    val highVolumeEnabled: Boolean? = null,
    val noiseCycle: HeadsetProtocol.NoiseControlReading? = null,
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

    fun readFailed(command: Int): Boolean = command in readFailures

    val canEditCustomEq: Boolean
        get() = customEqProfile != null && currentEqId != null &&
            capabilities.supportsCommand(HeadsetProtocol.SET_CUSTOM_EQ_COMMAND) &&
            capabilities.supportsCommand(HeadsetProtocol.SET_EQ_STATUS_COMMAND) &&
            !readFailed(HeadsetProtocol.ALL_EQ_COMMAND) &&
            !readFailed(HeadsetProtocol.EQ_STATUS_COMMAND)

    internal fun requireCompatibleCustomEq(
        requested: HeadsetCapabilityDiscovery.EqProfile,
    ): HeadsetCapabilityDiscovery.EqProfile {
        check(canEditCustomEq) { "Custom EQ is not available" }
        val baseline = checkNotNull(customEqProfile)
        require(requested.id == baseline.id) { "Custom EQ profile ID changed" }
        require(requested.frequencies == baseline.frequencies) { "Custom EQ frequencies changed" }
        require(requested.dbValues.size == baseline.dbValues.size) { "Custom EQ band count changed" }
        require(requested.dbValues.all { it in baseline.minValue..baseline.maxValue }) {
            "Custom EQ value is outside the headset-provided range"
        }
        return baseline.copy(dbValues = requested.dbValues, selected = true)
    }

    val currentEqLabel: String
        get() {
            val id = currentEqId ?: return "Unknown"
            eqProfiles.firstOrNull { it.id == id }?.name?.takeIf { it.isNotBlank() }?.let { return it }
            return when (id) {
                0 -> "Nature Balance"
                1 -> "Bass Boost"
                2 -> "Clear Vocals"
                3 -> "Clear Bass"
                4 -> "Custom"
                else -> "Profile $id"
            }
        }

    val spatialAudioEnabled: Boolean?
        get() = switches.firstOrNull { it.id == 27 }?.enabled

    val gameModeEnabled: Boolean?
        get() = switches.firstOrNull { it.id == 6 }?.enabled

    val autoAnswerEnabled: Boolean?
        get() = switches.firstOrNull { it.id == 8 }?.enabled

    val dualDeviceEnabled: Boolean?
        get() = switches.firstOrNull { it.id == 17 }?.enabled

    val smartWindNoiseEnabled: Boolean?
        get() = switches.firstOrNull { it.id == 26 }?.enabled

    val dynamicBassEnabled: Boolean?
        get() = switches.firstOrNull { it.id == 29 }?.enabled
}
