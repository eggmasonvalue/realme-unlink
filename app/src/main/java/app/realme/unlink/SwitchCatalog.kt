package app.realme.unlink

/**
 * Switch features that passed live write validation on Buds Wireless 5 ANC.
 * Rejected IDs must never appear as working toggles.
 */
object SwitchCatalog {
    /** IDs accepted by reversible live writes. */
    val writableIds: Set<Int> = setOf(6, 8, 9, 17, 26, 29, 38)

    /**
     * Gaming mode is advertised under both 6 and 8. Prefer 8 (baseline was on) and
     * collapse both into one logical control so the UI never shows two identical rows.
     */
    const val GAMING_PRIMARY_ID = 8
    private val gamingIds = setOf(6, 8)

    data class LogicalSwitch(
        val id: Int,
        val label: String,
        val enabled: Boolean,
        /** Extra protocol IDs that share this control (e.g. secondary gaming id). */
        val aliases: Set<Int> = emptySet(),
    )

    fun logicalSwitches(
        statuses: List<HeadsetCapabilityDiscovery.SwitchFeatureStatus>,
    ): List<LogicalSwitch> {
        val byId = statuses.associateBy { it.featureId }
        val result = mutableListOf<LogicalSwitch>()
        val consumed = mutableSetOf<Int>()

        val gamingPresent = gamingIds.any { it in byId && it in writableIds }
        if (gamingPresent) {
            val writeId = when {
                GAMING_PRIMARY_ID in byId -> GAMING_PRIMARY_ID
                else -> gamingIds.first { it in byId }
            }
            result += LogicalSwitch(
                id = writeId,
                label = "Gaming mode",
                enabled = gamingIds.any { (byId[it]?.value ?: 0) != 0 },
                aliases = gamingIds.filter { it in byId && it != writeId }.toSet(),
            )
            consumed += gamingIds
        }

        // Stable product order for the remaining proven toggles.
        val order = listOf(9, 17, 26, 29, 38)
        for (id in order) {
            if (id in consumed || id !in writableIds) continue
            val status = byId[id] ?: continue
            val label = HeadsetSemantics.switchFeatureLabel(id) ?: "Feature $id"
            result += LogicalSwitch(
                id = id,
                label = label,
                enabled = status.value != 0,
            )
            consumed += id
        }

        // Any other writable IDs that appeared (defensive).
        for (status in statuses) {
            if (status.featureId in consumed || status.featureId !in writableIds) continue
            val label = HeadsetSemantics.switchFeatureLabel(status.featureId)
                ?: "Feature ${status.featureId}"
            result += LogicalSwitch(
                id = status.featureId,
                label = label,
                enabled = status.value != 0,
            )
        }
        return result
    }
}
