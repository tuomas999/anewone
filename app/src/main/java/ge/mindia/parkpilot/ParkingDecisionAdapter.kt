package ge.mindia.parkpilot

object ParkingDecisionAdapter {
    fun evaluate(store: ParkingStore, location: ResolvedLocation, candidates: List<ParkingCandidate>): ParkingEvidence {
        val now = System.currentTimeMillis()
        return ParkingDecisionEngine.evaluate(
            ParkingDecisionInputs(
                now = now,
                mode = store.detectionMode(),
                sessionActive = store.session().active,
                suppressedUntil = store.suppressUntil(),
                promptedAt = store.promptedAt(),
                drivingStartedAt = store.drivingStartedAt(),
                tripActive = store.tripActive(),
                lastDrivingAt = store.lastDrivingAt(),
                lastInVehicleAt = store.lastInVehicleAt(),
                lastOnFootAt = store.lastOnFootAt(),
                lastStillAt = store.lastStillAt(),
                vehicleExitedAt = store.vehicleExitedAt(),
                carConnected = store.carConnected(),
                lastCarConnectedAt = store.lastCarConnectedAt(),
                lastCarDisconnectedAt = store.lastCarDisconnectedAt(),
                tripDistanceMeters = store.tripDistanceMeters(location.latitude, location.longitude)
            ),
            location,
            candidates
        )
    }
}
