package app.realme.unlink

/**
 * Issues monotonically increasing ownership tokens for foreground Bluetooth work.
 * A delayed callback may mutate the session only while its token still owns the lifecycle state.
 */
internal class SessionLifecycleGate {
    private var foreground = false
    private var generation = 0

    @Synchronized
    fun enterForeground(): Int? {
        if (foreground) return null
        foreground = true
        return ++generation
    }

    @Synchronized
    fun leaveForeground(): Int? {
        if (!foreground) return null
        foreground = false
        return ++generation
    }

    @Synchronized
    fun advanceForeground(): Int? {
        if (!foreground) return null
        return ++generation
    }

    @Synchronized
    fun currentForeground(): Int? = generation.takeIf { foreground }

    @Synchronized
    fun isCurrentForeground(token: Int): Boolean = foreground && generation == token

    @Synchronized
    fun isCurrentBackground(token: Int): Boolean = !foreground && generation == token

    @Synchronized
    fun close() {
        foreground = false
        generation++
    }
}
