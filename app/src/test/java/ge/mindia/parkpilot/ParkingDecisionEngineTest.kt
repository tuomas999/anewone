package ge.mindia.parkpilot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingDecisionEngineTest {
    private val now = 1_000_000L
    private val location = ResolvedLocation(41.0, 44.0, 15f, 0f, "ყაზბეგის გამზირი", "7", "თბილისი, ყაზბეგის გამზირი 7")
    private val candidates = listOf(ParkingCandidate("A091", "ყაზბეგის 5-9", 100, "test"))

    private fun base(mode: DetectionMode = DetectionMode.BLUETOOTH) = ParkingDecisionInputs(
        now = now, mode = mode, sessionActive = false, suppressedUntil = 0, promptedAt = 0,
        drivingStartedAt = now - 300_000, tripActive = true, lastDrivingAt = now - 10_000,
        lastInVehicleAt = now - 10_000, lastOnFootAt = 0, lastStillAt = 0, vehicleExitedAt = 0,
        carConnected = false, lastCarConnectedAt = now - 300_000, lastCarDisconnectedAt = now - 50_000,
        tripDistanceMeters = 500f
    )

    @Test fun trafficJamNeverPromptsWhileCarConnected() {
        val result = ParkingDecisionEngine.evaluate(base().copy(carConnected = true), location, candidates)
        assertFalse(result.shouldPrompt)
    }

    @Test fun bluetoothDropWhileStillMovingNeverPrompts() {
        val result = ParkingDecisionEngine.evaluate(base(), location.copy(speedKph = 26f), candidates)
        assertFalse(result.shouldPrompt)
    }

    @Test fun stableBluetoothStopAfterTripPrompts() {
        assertTrue(ParkingDecisionEngine.evaluate(base(), location, candidates).shouldPrompt)
    }

    @Test fun bluetoothOnlyWithoutMovementOrActivityDoesNotPrompt() {
        val result = ParkingDecisionEngine.evaluate(base().copy(tripActive = false, lastDrivingAt = 0, lastInVehicleAt = 0, tripDistanceMeters = null), location, candidates)
        assertFalse(result.shouldPrompt)
    }

    @Test fun bluetoothOnlyWithRealTripDistanceCanPrompt() {
        val result = ParkingDecisionEngine.evaluate(base().copy(tripActive = false, lastDrivingAt = 0, lastInVehicleAt = 0, tripDistanceMeters = 700f), location, candidates)
        assertTrue(result.shouldPrompt)
    }

    @Test fun shortBluetoothConnectionWithoutDrivingEvidenceDoesNotPrompt() {
        val result = ParkingDecisionEngine.evaluate(base().copy(tripActive = false, lastDrivingAt = 0, lastInVehicleAt = 0, lastCarConnectedAt = 900_000, lastCarDisconnectedAt = 950_000, tripDistanceMeters = null), location, candidates)
        assertFalse(result.shouldPrompt)
    }

    @Test fun motionOnlyNeedsVehicleExitOrWalkingEvidence() {
        val weak = ParkingDecisionEngine.evaluate(base(DetectionMode.MOTION_ONLY).copy(tripActive = false, lastDrivingAt = 0, lastInVehicleAt = 0, lastCarConnectedAt = 0, lastCarDisconnectedAt = 0, tripDistanceMeters = null), location, candidates)
        assertFalse(weak.shouldPrompt)
        val strong = ParkingDecisionEngine.evaluate(base(DetectionMode.MOTION_ONLY).copy(lastCarConnectedAt = 0, lastCarDisconnectedAt = 0, vehicleExitedAt = now - 10_000, lastOnFootAt = now - 5_000), location, candidates)
        assertTrue(strong.shouldPrompt)
    }
}
