package ge.mindia.parkpilot

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object ParkingMatcher {
    fun candidates(lots: List<ParkingLot>, resolved: ResolvedLocation, limit: Int = 5): List<ParkingCandidate> {
        val observed = listOfNotNull(resolved.street, resolved.addressLine)
            .flatMap { value -> listOf(normalizeStreet(value), normalizeAddress(value)) }
            .filter { it.isNotBlank() }
            .distinct()
        if (observed.isEmpty()) return emptyList()
        val house = numericHouse(resolved.houseNumber ?: extractHouse(resolved.addressLine))

        return lots.mapNotNull { lot ->
            val targetStreet = normalizeStreet(lot.street.ifBlank { lot.address })
            val targetAddress = normalizeAddress(lot.address)
            if (targetStreet.isBlank() && targetAddress.isBlank()) return@mapNotNull null

            val streetScore = observed.maxOfOrNull { obs ->
                maxOf(similarity(obs, targetStreet), similarity(obs, targetAddress))
            } ?: 0.0
            if (streetScore < 0.28) return@mapNotNull null

            val houseBonus = houseBonus(house, lot)
            val exactHouse = house != null && lot.houseNumbers.mapNotNull(::numericHouse).contains(house)
            val total = (streetScore * 100.0).roundToInt() + houseBonus + if (exactHouse) 5 else 0
            val reason = buildString {
                append("ქუჩა ${(streetScore * 100).roundToInt()}%")
                if (houseBonus > 0) append(", ნომერი +$houseBonus")
            }
            ParkingCandidate(lot.code, lot.address, total, reason)
        }.sortedWith(compareByDescending<ParkingCandidate> { it.score }.thenBy { it.code }).take(limit)
    }

    fun search(lots: List<ParkingLot>, query: String, limit: Int = 20): List<ParkingCandidate> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val qUpper = canonicalQueryCode(q)
        val qNorm = normalizeAddress(q)
        return lots.mapNotNull { lot ->
            val lotCode = lot.code.uppercase(Locale.ROOT)
            val codeExact = qUpper != null && lotCode == qUpper
            val codePrefix = qUpper != null && lotCode.startsWith(qUpper)
            val addressNorm = normalizeAddress(lot.address)
            val streetNorm = normalizeStreet(lot.street)
            val textMatch = qNorm.length >= 2 && (addressNorm.contains(qNorm) || streetNorm.contains(qNorm) || qNorm.contains(streetNorm).takeIf { streetNorm.length >= 4 } == true)
            if (!codeExact && !codePrefix && !textMatch) return@mapNotNull null
            val score = when {
                codeExact -> 1000
                codePrefix -> 800
                streetNorm == qNorm -> 700
                textMatch -> 500
                else -> 0
            }
            ParkingCandidate(lot.code, lot.address, score, "ხელით ძიება")
        }.sortedWith(compareByDescending<ParkingCandidate> { it.score }.thenBy { it.code }).take(limit)
    }

    internal fun normalizeStreet(value: String): String = normalize(value)
        .replace(Regex("[0-9]+(?:/[0-9]+)?[ა-ჰ]?"), " ")
        .replace(Regex("(^|\\s)(თბილისი|ქალაქი|ქუჩა|ქ|გამზირი|გამზ|ხეივანი|შესახვევი|ჩიხი|მოედანი)(?=\\s|$)"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    internal fun normalizeAddress(value: String): String = normalize(value)
        .replace(Regex("(^|\\s)(თბილისი|ქალაქი)(?=\\s|$)"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun normalize(value: String): String = value
        .lowercase(Locale.forLanguageTag("ka-GE"))
        .replace('№', ' ')
        .replace(Regex("[.,;:()\"'–—]"), " ")
        .replace('-', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun similarity(a: String, b: String): Double {
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a == b) return 1.0
        if (a.length >= 5 && b.contains(a)) return 0.94
        if (b.length >= 5 && a.contains(b)) return 0.94
        val at = a.split(' ').filter { it.length > 1 }.toSet()
        val bt = b.split(' ').filter { it.length > 1 }.toSet()
        if (at.isEmpty() || bt.isEmpty()) return 0.0
        val inter = at.intersect(bt).size.toDouble()
        val union = at.union(bt).size.toDouble()
        return if (union == 0.0) 0.0 else inter / union
    }

    private fun houseBonus(house: Int?, lot: ParkingLot): Int {
        house ?: return 0
        val nums = lot.houseNumbers.mapNotNull(::numericHouse)
        if (house in nums) return 35
        val start = lot.houseStart
        val end = lot.houseEnd
        if (start != null && end != null && house in minOf(start, end)..maxOf(start, end)) return 28
        val nearest = nums.minOfOrNull { abs(it - house) }
        return when (nearest) {
            1 -> 18
            2 -> 12
            3 -> 6
            else -> 0
        }
    }

    private fun canonicalQueryCode(value: String): String? {
        val m = Regex("^A?\\s*(\\d{1,5})$", RegexOption.IGNORE_CASE).matchEntire(value.trim()) ?: return null
        val n = m.groupValues[1].toIntOrNull() ?: return null
        return if (n < 1000) "A%03d".format(n) else "A$n"
    }

    private fun numericHouse(value: String?): Int? = value?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() }
    private fun extractHouse(value: String?): String? = value?.let { Regex("(?:№|N|#)?\\s*(\\d+(?:/\\d+)?)").find(it)?.groupValues?.getOrNull(1) }
}
