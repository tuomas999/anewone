package ge.mindia.parkpilot

object ParkingDecisionEngine {
    private const val RECENT_ACTIVITY_MS = 12 * 60_000L
    private const val RECENT_DRIVING_MS = 35 * 60_000L
    private const val MIN_BT_ONLY_TRIP_MS = 3 * 60_000L
    private const val HARD_MOVING_SPEED_KPH = 8f
    private const val MIN_TRIP_DISTANCE_METERS = 150f

    fun evaluate(inputs: ParkingDecisionInputs, location: ResolvedLocation, candidates: List<ParkingCandidate>): ParkingEvidence {
        val now = inputs.now
        if (inputs.sessionActive) return blocked("active parking session")
        if (now < inputs.suppressedUntil) return blocked("user suppression")
        if (inputs.promptedAt > 0L && inputs.promptedAt >= inputs.drivingStartedAt) return blocked("already prompted this trip")
        if (inputs.mode == DetectionMode.BLUETOOTH && inputs.carConnected) return blocked("car still connected")
        if ((location.speedKph ?: 0f) >= HARD_MOVING_SPEED_KPH) return blocked("vehicle still moving")

        val drivingSessionRecent = inputs.tripActive && age(now, inputs.lastDrivingAt) <= RECENT_DRIVING_MS
        val recentInVehicle = age(now, inputs.lastInVehicleAt) <= RECENT_DRIVING_MS
        val weakBtTrip = inputs.mode == DetectionMode.BLUETOOTH && inputs.lastCarConnectedAt > 0L &&
            inputs.lastCarDisconnectedAt > inputs.lastCarConnectedAt &&
            inputs.lastCarDisconnectedAt - inputs.lastCarConnectedAt >= MIN_BT_ONLY_TRIP_MS
        val tripMovementConfirmed = (inputs.tripDistanceMeters ?: 0f) >= MIN_TRIP_DISTANCE_METERS
        val trustedTrip = drivingSessionRecent || recentInVehicle || tripMovementConfirmed
        val tripActive = trustedTrip || weakBtTrip

        val stableDisconnect = inputs.mode == DetectionMode.BLUETOOTH &&
            inputs.lastCarDisconnectedAt > 0L && age(now, inputs.lastCarDisconnectedAt) >= 45_000L
        val exitedVehicleRecently = age(now, inputs.vehicleExitedAt) <= RECENT_ACTIVITY_MS
        val onFootRecently = minOf(age(now, inputs.lastOnFootAt), age(now, inputs.lastStillAt)) <= RECENT_ACTIVITY_MS
        val stationary = location.speedKph != null && location.speedKph <= 4.0f
        val addressMatched = candidates.isNotEmpty()
        val accurateLocation = location.accuracyMeters <= 60f

        var score = 0
        if (trustedTrip) score += 40 else if (weakBtTrip) score += 15
        if (tripMovementConfirmed) score += 20
        if (stableDisconnect) score += 25
        if (exitedVehicleRecently) score += 25
        if (onFootRecently) score += 15
        if (stationary) score += 10
        if (addressMatched) score += 10
        if (accurateLocation) score += 5
        if (inputs.mode == DetectionMode.MOTION_ONLY && !exitedVehicleRecently && !onFootRecently) score -= 35

        return ParkingEvidence(
            score = score.coerceIn(0, 100),
            tripActive = tripActive,
            stableDisconnect = stableDisconnect,
            exitedVehicleRecently = exitedVehicleRecently,
            onFootRecently = onFootRecently,
            stationary = stationary,
            addressMatched = addressMatched,
            accurateLocation = accurateLocation,
            tripMovementConfirmed = tripMovementConfirmed
        )
    }

    private fun blocked(reason: String) = ParkingEvidence(
        score = 0, tripActive = false, stableDisconnect = false,
        exitedVehicleRecently = false, onFootRecently = false,
        stationary = false, addressMatched = false, accurateLocation = false,
        tripMovementConfirmed = false, blockedReason = reason
    )

    private fun age(now: Long, timestamp: Long): Long = if (timestamp <= 0L) Long.MAX_VALUE else (now - timestamp).coerceAtLeast(0L)
}
