package ge.mindia.parkpilot

/** Pure policy for separating a flaky reconnect from a genuine new driving leg. */
object TripLegPolicy {
    const val SAME_TRIP_RECONNECT_WINDOW_MS = 2 * 60_000L

    fun isNewLeg(
        wasConnected: Boolean,
        activeParkingSession: Boolean,
        previousDisconnectAt: Long,
        now: Long,
        promptedAt: Long
    ): Boolean {
        if (wasConnected || activeParkingSession) return false
        if (previousDisconnectAt <= 0L) return true
        val gap = (now - previousDisconnectAt).coerceAtLeast(0L)
        return gap > SAME_TRIP_RECONNECT_WINDOW_MS || promptedAt > 0L
    }
}
