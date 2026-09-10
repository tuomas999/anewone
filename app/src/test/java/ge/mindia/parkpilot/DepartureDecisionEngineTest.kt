package ge.mindia.parkpilot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DepartureDecisionEngineTest {
    @Test fun eightKphConfirmsMovement() {
        assertTrue(DepartureDecisionEngine.evaluate(8f, 5f, 15f, 10f).moving)
    }

    @Test fun normalGpsDriftDoesNotConfirmMovement() {
        val r = DepartureDecisionEngine.evaluate(0.5f, 35f, 20f, 25f)
        assertFalse(r.moving)
        assertTrue(r.clearlyStationary)
    }

    @Test fun poorGpsExpandsDistanceThreshold() {
        val r = DepartureDecisionEngine.evaluate(null, 100f, 70f, 65f)
        assertFalse(r.moving)
    }

    @Test fun largeDisplacementCanConfirmWithoutSpeed() {
        val r = DepartureDecisionEngine.evaluate(null, 140f, 15f, 15f)
        assertTrue(r.moving)
    }
}
