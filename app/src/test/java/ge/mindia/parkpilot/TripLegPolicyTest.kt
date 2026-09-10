package ge.mindia.parkpilot

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripLegPolicyTest {
    @Test fun shortReconnectIsSameTrip() { assertFalse(TripLegPolicy.isNewLeg(false, false, 1_000_000L, 1_060_000L, 0L)) }
    @Test fun longReconnectIsNewTrip() { assertTrue(TripLegPolicy.isNewLeg(false, false, 1_000_000L, 1_180_001L, 0L)) }
    @Test fun reconnectAfterPromptStartsNewLeg() { assertTrue(TripLegPolicy.isNewLeg(false, false, 1_000_000L, 1_030_000L, 1_020_000L)) }
    @Test fun activeParkingReconnectNeverResetsSession() { assertFalse(TripLegPolicy.isNewLeg(false, true, 1_000_000L, 2_000_000L, 1_500_000L)) }
}
