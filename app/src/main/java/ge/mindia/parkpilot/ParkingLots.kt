package ge.mindia.parkpilot

import android.content.Context
import org.json.JSONObject
import java.util.zip.GZIPInputStream

object ParkingLots {
    @Volatile private var cache: List<ParkingLot>? = null
    @Volatile private var metadataCache: String? = null

    fun all(context: Context): List<ParkingLot> = cache ?: synchronized(this) {
        cache ?: load(context).also { cache = it }
    }

    fun datasetInfo(context: Context): String {
        if (cache == null) all(context)
        return metadataCache ?: "უცნობი წყარო"
    }

    private fun load(context: Context): List<ParkingLot> {
        val raw = GZIPInputStream(context.assets.open("parking_lots.json.gz")).bufferedReader().use { it.readText() }
        val root = JSONObject(raw)
        val meta = root.optJSONObject("metadata")
        metadataCache = if (meta != null) {
            val total = meta.optInt("totalSpaces", 0)
            "${meta.optString("sourceDate", "?")} • ${meta.optInt("recordCount", 0)} ლოტი" + if (total > 0) " • $total ადგილი" else ""
        } else "მონაცემთა წყარო უცნობია"
        val array = root.getJSONArray("lots")
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val nums = o.optJSONArray("houseNumbers")
            ParkingLot(
                code = o.getString("code"),
                address = o.optString("address"),
                street = o.optString("street"),
                houseNumbers = if (nums == null) emptyList() else (0 until nums.length()).map { nums.optString(it) },
                houseStart = o.optIntOrNull("houseStart"),
                houseEnd = o.optIntOrNull("houseEnd"),
                spaces = o.optIntOrNull("spaces"),
                disabledSpaces = o.optIntOrNull("disabledSpaces"),
                evSpaces = o.optIntOrNull("evSpaces"),
                parkingMethod = o.optNullableString("parkingMethod"),
                additionalSign = o.optNullableString("additionalSign")
            )
        }
    }

    fun candidates(context: Context, resolved: ResolvedLocation, limit: Int = 5): List<ParkingCandidate> =
        ParkingMatcher.candidates(all(context), resolved, limit)

    fun search(context: Context, query: String, limit: Int = 20): List<ParkingCandidate> =
        ParkingMatcher.search(all(context), query, limit)

    private fun JSONObject.optIntOrNull(name: String): Int? = if (!has(name) || isNull(name)) null else optInt(name)
    private fun JSONObject.optNullableString(name: String): String? = if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
}
