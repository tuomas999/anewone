package ge.mindia.parkpilot

enum class DetectionMode { BLUETOOTH, MOTION_ONLY }

enum class MotionActivity {
    UNKNOWN, IN_VEHICLE, WALKING, ON_FOOT, STILL, RUNNING, ON_BICYCLE
}

data class ParkingLot(
    val code: String,
    val address: String,
    val street: String,
    val houseNumbers: List<String>,
    val houseStart: Int?,
    val houseEnd: Int?,
    val spaces: Int?,
    val disabledSpaces: Int? = null,
    val evSpaces: Int? = null,
    val parkingMethod: String? = null,
    val additionalSign: String? = null
)

data class ParkingCandidate(
    val code: String,
    val address: String,
    val score: Int,
    val reason: String
)

data class ResolvedLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedKph: Float?,
    val street: String?,
    val houseNumber: String?,
    val addressLine: String?
)

data class ParkingSession(
    val active: Boolean,
    val lotCode: String?,
    val startedAt: Long,
    val parkedLatitude: Double?,
    val parkedLongitude: Double?,
    val parkedAccuracyMeters: Float?
)

data class ParkingDecisionInputs(
    val now: Long,
    val mode: DetectionMode,
    val sessionActive: Boolean,
    val suppressedUntil: Long,
    val promptedAt: Long,
    val drivingStartedAt: Long,
    val tripActive: Boolean,
    val lastDrivingAt: Long,
    val lastInVehicleAt: Long,
    val lastOnFootAt: Long,
    val lastStillAt: Long,
    val vehicleExitedAt: Long,
    val carConnected: Boolean,
    val lastCarConnectedAt: Long,
    val lastCarDisconnectedAt: Long,
    val tripDistanceMeters: Float?
)

data class ParkingEvidence(
    val score: Int,
    val tripActive: Boolean,
    val stableDisconnect: Boolean,
    val exitedVehicleRecently: Boolean,
    val onFootRecently: Boolean,
    val stationary: Boolean,
    val addressMatched: Boolean,
    val accurateLocation: Boolean,
    val tripMovementConfirmed: Boolean,
    val blockedReason: String? = null
) {
    val shouldPrompt: Boolean get() = blockedReason == null && score >= 70

    fun summary(): String = buildString {
        append("confidence=$score")
        if (tripActive) append(" • trip")
        if (stableDisconnect) append(" • BT-off")
        if (exitedVehicleRecently) append(" • vehicle-exit")
        if (onFootRecently) append(" • on-foot")
        if (stationary) append(" • stopped")
        if (addressMatched) append(" • lot-match")
        if (accurateLocation) append(" • GPS-ok")
        if (tripMovementConfirmed) append(" • trip-moved")
        blockedReason?.let { append(" • blocked: ").append(it) }
    }
}

data class DiagnosticSnapshot(
    val lastEvent: String?,
    val lastEventAt: Long,
    val lastAddress: String?,
    val lastLocationAt: Long,
    val lastAccuracyMeters: Float,
    val candidateSummary: String?,
    val motionActivity: String?,
    val confidenceSummary: String?
)
