package app.realme.unlink

/**
 * User-facing names for values whose meanings are confirmed by the headset SDK / product copy.
 * Unknown values deliberately remain available as raw numbers.
 *
 * Buds Wireless 5 ANC is a **neckband** with a multifunction button and volume keys — not TWS
 * left/right touch buds.
 */
object HeadsetSemantics {
    val isNeckbandDevice: Boolean = true

    fun noiseActionLabel(action: Int): String = when (action) {
        1 -> "Noise control"
        2 -> "Noise-control cycle"
        4 -> "Wind noise reduction"
        else -> "Unknown noise action"
    }

    fun noiseTypeLabel(type: Int): String = when (type) {
        1 -> "standard protocol variant"
        2 -> "alternate protocol variant"
        else -> "unknown protocol variant"
    }

    /**
     * Product-facing noise labels (aligned with the user guide + realme Link).
     * Wire values for action 1 are bit-like protocol constants.
     */
    fun noiseValueLabel(action: Int, value: Int): String? = when (action) {
        1 -> when (value) {
            1 -> "Off"
            2 -> "Transparency"
            4 -> "Mild noise cancellation"
            8 -> "Strong noise cancellation"
            16 -> "Moderate noise cancellation"
            32 -> "Smart noise cancellation"
            else -> null
        }

        2 -> when (value) {
            3 -> "Off ↔ transparency"
            9 -> "Ordinary noise cancellation"
            10 -> "Transparency"
            11 -> "Cycle through all modes"
            else -> null
        }

        else -> null
    }

    fun noiseDisplayLabel(action: Int, value: Int): String {
        return noiseValueLabel(action, value)
            ?: "${noiseActionLabel(action)} · raw value $value"
    }

    fun noiseSummary(action: Int, type: Int, value: Int): String {
        return "${noiseDisplayLabel(action, value)} · ${noiseTypeLabel(type)}"
    }

    /** Wear/battery role. On this neckband, 1/2 are left/right buds; 3 would be a case. */
    fun wearRole(deviceType: Int): String = when (deviceType) {
        1 -> "Left"
        2 -> "Right"
        3 -> "Case"
        else -> "Bud $deviceType"
    }

    /**
     * Physical control on the neckband. Evidence baseline uses deviceType=1 for all keys with
     * button 1 = multifunction and button 4 = volume +.
     */
    fun buttonControlLabel(deviceType: Int, button: Int): String = when (button) {
        1 -> "Multifunction (MFB)"
        2 -> "Multifunction (alt)"
        3 -> "Noise key"
        4 -> "Volume +"
        5 -> "Volume −"
        6 -> "Extra key"
        else -> when (deviceType) {
            1 -> "Left control"
            2 -> "Right control"
            else -> "Button $button"
        }
    }

    fun switchFeatureLabel(featureId: Int): String? = when (featureId) {
        5 -> "Power saving"
        6, 8 -> "45ms Ultra-Low Latency Game Mode"
        7 -> "Party mode"
        9 -> "Volume Enhancer"
        11 -> "HearID"
        12 -> "Voice enhancement"
        17 -> "Dual Connection (Multipoint)"
        19 -> "AI microphone"
        24 -> "High-quality audio"
        26 -> "Wind Noise Reduction"
        27 -> "3D Spatial Audio"
        29 -> "Dynamic Bass Boost"
        38 -> "HD Audio / LDAC"
        54 -> "Clear Bass"
        else -> null
    }

    fun switchValueLabel(value: Int): String = when (value) {
        0 -> "off"
        1 -> "on"
        else -> "raw value $value"
    }

    fun multipointStateLabel(state: Int): String = when (state) {
        0 -> "Disconnected"
        1 -> "Connecting"
        2 -> "Connected"
        else -> "Unknown state $state"
    }

    fun bassBandLabel(type: Int): String? = when (type) {
        1 -> "Low"
        2 -> "Mid"
        3 -> "High"
        else -> null
    }

    fun buttonActionLabel(action: Int): String = when (action) {
        1 -> "Single Press"
        2 -> "Double Press"
        3 -> "Triple Press"
        4 -> "Long Press (1s)"
        5 -> "Long Press (3s)"
        else -> "Action $action"
    }

    fun buttonFunctionLabel(function: Int): String = when (function) {
        1 -> "Play / Pause"
        2 -> "Next Track"
        3 -> "Previous Track"
        4 -> "Voice Assistant"
        5 -> "Noise Mode"
        6 -> "Switch Device"
        7 -> "Game Mode"
        8 -> "Volume Up"
        9 -> "Volume Down"
        10 -> "Ring / Find Phone"
        11 -> "Camera Shutter"
        26 -> "Face-to-Face Translation"
        27 -> "AI Home"
        31 -> "AI Assistant"
        0 -> "Off"
        else -> "Function $function"
    }
}
