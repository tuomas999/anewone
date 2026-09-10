package ge.mindia.parkpilot

import org.junit.Assert.assertEquals
import org.junit.Test

class ParkingMatcherTest {
    private val lots = listOf(
        ParkingLot("A090", "ალექსანდრე ყაზბეგის გამზირი №1-№3", "ალექსანდრე ყაზბეგის გამზირი", listOf("1", "3"), 1, 3, 5),
        ParkingLot("A091", "ალექსანდრე ყაზბეგის გამზირი №5-№7-№9", "ალექსანდრე ყაზბეგის გამზირი", listOf("5", "7", "9"), 5, 9, 11),
        ParkingLot("A092", "ალექსანდრე ყაზბეგის გამზირი №11", "ალექსანდრე ყაზბეგის გამზირი", listOf("11"), 11, null, 4)
    )

    @Test fun kazbegi7RanksA091First() {
        val location = ResolvedLocation(41.0, 44.0, 15f, 0f, "ალექსანდრე ყაზბეგის გამზირი", "7", "თბილისი, ალექსანდრე ყაზბეგის გამზირი 7")
        assertEquals("A091", ParkingMatcher.candidates(lots, location, 3).first().code)
    }

    @Test fun numericManualCodeNormalizes() {
        assertEquals("A091", ParkingMatcher.search(lots, "91").first().code)
    }
}
