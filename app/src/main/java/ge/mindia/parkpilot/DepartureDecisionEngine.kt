package ge.mindia.parkpilot

import kotlin.math.max

/** Pure movement classifier used after the user returns to an actively parked car. */
object DepartureDecisionEngine {
    const val SPEED_CONFIRMED_KPH = 8f
    const val CLEARLY_STATIONARY_KPH = 3f
    const val MIN_DISTANCE_THRESHOLD_METERS = 80f

    data class Result(
        val moving: Boolean,
        val clearlyStationary: Boolean,
        val distanceThresholdMeters: Float
    )

    fun evaluate(
        speedKph: Float?,
        distanceFromParkedMeters: Float?,
        parkedAccuracyMeters: Float?,
        currentAccuracyMeters: Float
    ): Result {
        val threshold = max(
            MIN_DISTANCE_THRESHOLD_METERS,
            ((parkedAccuracyMeters ?: 30f) + currentAccuracyMeters) * 1.5f
        )
        val speedMoving = speedKph != null && speedKph >= SPEED_CONFIRMED_KPH
        val distanceMoving = distanceFromParkedMeters != null && distanceFromParkedMeters >= threshold
        val clearlyStationary = (speedKph ?: 0f) < CLEARLY_STATIONARY_KPH &&
            (distanceFromParkedMeters ?: 0f) < threshold * 0.7f
        return Result(speedMoving || distanceMoving, clearlyStationary, threshold)
    }
}
