package app.realme.unlink

/** Switch features with validated identities on realme Buds Wireless 5 ANC. */
object SwitchCatalog {
    val writableIds: Set<Int> = setOf(6, 8, 17, 26, 29)

    const val GAMING_PRIMARY_ID = 6

    data class LogicalSwitch(
        val id: Int,
        val label: String,
        val enabled: Boolean,
    )

    fun logicalSwitches(
        statuses: List<HeadsetCapabilityDiscovery.SwitchFeatureStatus>,
    ): List<LogicalSwitch> {
        val byId = statuses.associateBy { it.featureId }
        return listOf(17, 6, 8, 26, 29).mapNotNull { id ->
            if (id !in writableIds) return@mapNotNull null
            val status = byId[id] ?: return@mapNotNull null
            LogicalSwitch(
                id = id,
                label = HeadsetSemantics.switchFeatureLabel(id) ?: return@mapNotNull null,
                enabled = status.value != 0,
            )
        }
    }
}
