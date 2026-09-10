package ge.mindia.parkpilot

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ParkingStore(context: Context) {
    private val prefs = context.getSharedPreferences("parkpilot", Context.MODE_PRIVATE)

    fun session() = ParkingSession(
        active = prefs.getBoolean("active", false),
        lotCode = prefs.getString("lot_code", null),
        startedAt = prefs.getLong("started_at", 0L),
        parkedLatitude = nullableDouble("parked_lat"),
        parkedLongitude = nullableDouble("parked_lon"),
        parkedAccuracyMeters = nullableFloat("parked_accuracy")
    )

    fun start(code: String) {
        val editor = prefs.edit()
            .putBoolean("active", true)
            .putString("lot_code", code)
            .putLong("started_at", System.currentTimeMillis())
        nullableDouble("pending_lat")?.let { editor.putLong("parked_lat", java.lang.Double.doubleToRawLongBits(it)) }
        nullableDouble("pending_lon")?.let { editor.putLong("parked_lon", java.lang.Double.doubleToRawLongBits(it)) }
        nullableFloat("pending_accuracy")?.let { editor.putFloat("parked_accuracy", it) }
        editor.remove("pending_lot")
            .remove("pending_lat")
            .remove("pending_lon")
            .remove("pending_accuracy")
            .remove("candidates_json")
            .remove("stop_reminder_session_started_at")
            .apply()
    }

    fun stop() = prefs.edit()
        .putBoolean("active", false)
        .remove("lot_code")
        .remove("started_at")
        .remove("parked_lat")
        .remove("parked_lon")
        .remove("parked_accuracy")
        .remove("pending_lot")
        .remove("stop_reminder_session_started_at")
        .apply()

    fun stopReminderSentFor(session: ParkingSession): Boolean =
        session.startedAt > 0L && prefs.getLong("stop_reminder_session_started_at", 0L) == session.startedAt

    fun markStopReminderSentFor(session: ParkingSession) {
        if (session.startedAt > 0L) prefs.edit().putLong("stop_reminder_session_started_at", session.startedAt).apply()
    }

    fun selectedCar() = prefs.getString("car_address", null)
    fun selectedCarName() = prefs.getString("car_name", null)

    fun selectCar(address: String, name: String) = prefs.edit()
        .putString("car_address", address)
        .putString("car_name", name)
        .putString("detection_mode", DetectionMode.BLUETOOTH.name)
        .apply()

    fun clearCar() = prefs.edit().remove("car_address").remove("car_name").apply()

    fun detectionMode(): DetectionMode = runCatching {
        DetectionMode.valueOf(prefs.getString("detection_mode", DetectionMode.BLUETOOTH.name) ?: DetectionMode.BLUETOOTH.name)
    }.getOrDefault(DetectionMode.BLUETOOTH)

    fun setDetectionMode(mode: DetectionMode) = prefs.edit().putString("detection_mode", mode.name).apply()

    fun monitoringEnabled() = prefs.getBoolean("monitoring_enabled", false)
    fun setMonitoringEnabled(enabled: Boolean) = prefs.edit().putBoolean("monitoring_enabled", enabled).apply()

    fun markCarConnected(now: Long = System.currentTimeMillis()) {
        val startedAt = if (prefs.getBoolean("car_connected", false) && prefs.getLong("last_car_connected_at", 0L) > 0L)
            prefs.getLong("last_car_connected_at", now) else now
        prefs.edit()
            .putBoolean("car_connected", true)
            .putLong("last_car_connected_at", startedAt)
            .remove("last_car_disconnected_at")
            .apply()
    }

    fun markCarDisconnected(now: Long = System.currentTimeMillis()) = prefs.edit()
        .putBoolean("car_connected", false)
        .putLong("last_car_disconnected_at", now)
        .apply()

    fun carConnected() = prefs.getBoolean("car_connected", false)
    fun lastCarConnectedAt() = prefs.getLong("last_car_connected_at", 0L)
    fun lastCarDisconnectedAt() = prefs.getLong("last_car_disconnected_at", 0L)

    fun prepareNewTripLeg() = prefs.edit()
        .putBoolean("trip_active", false)
        .remove("driving_started_at")
        .remove("last_driving_at")
        .remove("driving_source")
        .remove("last_in_vehicle_at")
        .remove("last_on_foot_at")
        .remove("last_still_at")
        .remove("vehicle_exited_at")
        .remove("trip_anchor_lat")
        .remove("trip_anchor_lon")
        .remove("trip_anchor_accuracy")
        .remove("prompted_trip_at")
        .remove("suppress_until")
        .remove("candidates_json")
        .remove("pending_lot")
        .remove("pending_lat")
        .remove("pending_lon")
        .remove("pending_accuracy")
        .apply()

    fun markDriving(now: Long = System.currentTimeMillis(), source: String = "activity") {
        prefs.edit()
            .putBoolean("trip_active", true)
            .putLong("driving_started_at", if (prefs.getLong("driving_started_at", 0L) == 0L) now else prefs.getLong("driving_started_at", now))
            .putLong("last_driving_at", now)
            .putString("driving_source", source)
            .remove("prompted_trip_at")
            .remove("suppress_until")
            .apply()
    }

    fun endTrip() = prefs.edit()
        .putBoolean("trip_active", false)
        .remove("driving_started_at")
        .remove("last_driving_at")
        .remove("driving_source")
        .remove("last_in_vehicle_at")
        .remove("last_on_foot_at")
        .remove("last_still_at")
        .remove("vehicle_exited_at")
        .remove("trip_anchor_lat")
        .remove("trip_anchor_lon")
        .remove("trip_anchor_accuracy")
        .apply()

    fun setTripAnchor(latitude: Double, longitude: Double, accuracyMeters: Float) = prefs.edit()
        .putLong("trip_anchor_lat", java.lang.Double.doubleToRawLongBits(latitude))
        .putLong("trip_anchor_lon", java.lang.Double.doubleToRawLongBits(longitude))
        .putFloat("trip_anchor_accuracy", accuracyMeters)
        .apply()

    fun hasTripAnchor(): Boolean = prefs.contains("trip_anchor_lat") && prefs.contains("trip_anchor_lon")

    fun clearTripAnchor() = prefs.edit()
        .remove("trip_anchor_lat")
        .remove("trip_anchor_lon")
        .remove("trip_anchor_accuracy")
        .apply()

    fun tripDistanceMeters(latitude: Double, longitude: Double): Float? {
        val startLat = nullableDouble("trip_anchor_lat") ?: return null
        val startLon = nullableDouble("trip_anchor_lon") ?: return null
        val out = FloatArray(1)
        android.location.Location.distanceBetween(startLat, startLon, latitude, longitude, out)
        return out[0]
    }

    fun tripActive() = prefs.getBoolean("trip_active", false)
    fun drivingStartedAt() = prefs.getLong("driving_started_at", 0L)
    fun lastDrivingAt() = prefs.getLong("last_driving_at", 0L)

    fun recordMotion(activity: MotionActivity, now: Long = System.currentTimeMillis()) {
        val e = prefs.edit().putString("motion_activity", activity.name).putLong("motion_activity_at", now)
        when (activity) {
            MotionActivity.IN_VEHICLE -> e.putLong("last_in_vehicle_at", now)
            MotionActivity.WALKING, MotionActivity.ON_FOOT, MotionActivity.RUNNING -> e.putLong("last_on_foot_at", now)
            MotionActivity.STILL -> e.putLong("last_still_at", now)
            else -> Unit
        }
        e.apply()
    }

    fun motionActivity(): MotionActivity = runCatching {
        MotionActivity.valueOf(prefs.getString("motion_activity", MotionActivity.UNKNOWN.name) ?: MotionActivity.UNKNOWN.name)
    }.getOrDefault(MotionActivity.UNKNOWN)
    fun lastInVehicleAt() = prefs.getLong("last_in_vehicle_at", 0L)
    fun lastOnFootAt() = prefs.getLong("last_on_foot_at", 0L)
    fun lastStillAt() = prefs.getLong("last_still_at", 0L)
    fun vehicleExitedAt() = prefs.getLong("vehicle_exited_at", 0L)
    fun markVehicleExited(now: Long = System.currentTimeMillis()) = prefs.edit().putLong("vehicle_exited_at", now).apply()

    fun suppressParkingPrompt(minutes: Int = 20) {
        prefs.edit().putLong("suppress_until", System.currentTimeMillis() + minutes * 60_000L).apply()
        clearCandidates()
        clearPending()
    }
    fun suppressUntil() = prefs.getLong("suppress_until", 0L)

    fun markPrompted(now: Long = System.currentTimeMillis()) = prefs.edit().putLong("prompted_trip_at", now).apply()
    fun promptedAt() = prefs.getLong("prompted_trip_at", 0L)

    fun setCandidates(candidates: List<ParkingCandidate>) {
        val array = JSONArray()
        candidates.forEach { c ->
            array.put(JSONObject().apply {
                put("code", c.code); put("address", c.address); put("score", c.score); put("reason", c.reason)
            })
        }
        prefs.edit().putString("candidates_json", array.toString()).remove("pending_lot").apply()
    }

    fun candidates(): List<ParkingCandidate> {
        val raw = prefs.getString("candidates_json", null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                ParkingCandidate(o.getString("code"), o.optString("address"), o.optInt("score"), o.optString("reason"))
            }
        }.getOrElse { emptyList() }
    }

    fun pendingLot() = prefs.getString("pending_lot", null)
    fun selectPendingLot(code: String) = prefs.edit().putString("pending_lot", code).apply()
    fun clearPending() = prefs.edit().remove("pending_lot").remove("pending_lat").remove("pending_lon").remove("pending_accuracy").apply()
    fun clearCandidates() = prefs.edit().remove("candidates_json").apply()

    fun setPendingLocation(location: ResolvedLocation) = prefs.edit()
        .putLong("pending_lat", java.lang.Double.doubleToRawLongBits(location.latitude))
        .putLong("pending_lon", java.lang.Double.doubleToRawLongBits(location.longitude))
        .putFloat("pending_accuracy", location.accuracyMeters)
        .apply()

    fun recordEvent(text: String) {
        val now = System.currentTimeMillis()
        Log.i("ParkPilot", text)
        val history = runCatching { JSONArray(prefs.getString("diag_event_log", "[]")) }.getOrDefault(JSONArray())
        history.put(JSONObject().put("at", now).put("text", text))
        val trimmed = JSONArray()
        val start = maxOf(0, history.length() - MAX_EVENT_LOG)
        for (i in start until history.length()) trimmed.put(history.getJSONObject(i))
        prefs.edit()
            .putString("diag_last_event", text)
            .putLong("diag_last_event_at", now)
            .putString("diag_event_log", trimmed.toString())
            .apply()
    }

    fun eventLog(): List<Pair<Long, String>> {
        val history = runCatching { JSONArray(prefs.getString("diag_event_log", "[]")) }.getOrDefault(JSONArray())
        return (0 until history.length()).mapNotNull { i ->
            val o = history.optJSONObject(i) ?: return@mapNotNull null
            val text = o.optString("text").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            o.optLong("at", 0L) to text
        }
    }

    fun diagnosticReport(): String {
        val d = diagnostics()
        val s = session()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return buildString {
            appendLine("ParkPilot diagnostics")
            appendLine("mode=${detectionMode().name}")
            appendLine("monitoring=${monitoringEnabled()}")
            appendLine("car=${selectedCarName() ?: "none"}")
            appendLine("carConnected=${carConnected()}")
            appendLine("activity=${d.motionActivity ?: "unknown"}")
            appendLine("sessionActive=${s.active} lot=${s.lotCode ?: "none"}")
            appendLine("confidence=${d.confidenceSummary ?: "none"}")
            appendLine("address=${d.lastAddress ?: "none"}")
            appendLine("gpsAccuracy=${d.lastAccuracyMeters}")
            appendLine("candidates=${d.candidateSummary ?: "none"}")
            appendLine("--- recent events ---")
            eventLog().takeLast(40).forEach { (at, text) ->
                appendLine("${if (at > 0) time.format(Date(at)) else "?"} | $text")
            }
        }
    }

    fun recordEvidence(evidence: ParkingEvidence) = prefs.edit().putString("diag_confidence", evidence.summary()).apply()

    fun recordLocation(resolved: ResolvedLocation, candidates: List<ParkingCandidate>) = prefs.edit()
        .putString("diag_last_address", resolved.addressLine ?: resolved.street ?: "მისამართი ვერ განისაზღვრა")
        .putLong("diag_last_location_at", System.currentTimeMillis())
        .putFloat("diag_last_accuracy", resolved.accuracyMeters)
        .putString("diag_candidates", candidates.joinToString { "${it.code}:${it.score}" })
        .apply()

    fun diagnostics() = DiagnosticSnapshot(
        lastEvent = prefs.getString("diag_last_event", null),
        lastEventAt = prefs.getLong("diag_last_event_at", 0L),
        lastAddress = prefs.getString("diag_last_address", null),
        lastLocationAt = prefs.getLong("diag_last_location_at", 0L),
        lastAccuracyMeters = prefs.getFloat("diag_last_accuracy", -1f),
        candidateSummary = prefs.getString("diag_candidates", null),
        motionActivity = prefs.getString("motion_activity", null),
        confidenceSummary = prefs.getString("diag_confidence", null)
    )

    private fun nullableDouble(key: String): Double? {
        if (!prefs.contains(key)) return null
        return java.lang.Double.longBitsToDouble(prefs.getLong(key, 0L))
    }

    private fun nullableFloat(key: String): Float? = if (prefs.contains(key)) prefs.getFloat(key, 0f) else null

    companion object { private const val MAX_EVENT_LOG = 80 }
}
