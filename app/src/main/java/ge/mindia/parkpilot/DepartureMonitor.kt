package ge.mindia.parkpilot

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class DepartureMonitor(
    private val context: Context,
    private val store: ParkingStore,
    private val onDepartureConfirmed: (ParkingSession, Float?, Float?) -> Unit
) {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var consecutiveMovement = 0

    private val timeout = Runnable { stop() }
    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val session = store.session()
            if (!session.active) {
                stop()
                return
            }
            val location = result.lastLocation ?: return
            val speedKph = if (location.hasSpeed()) location.speed * 3.6f else null
            val distance = distanceFromParked(session, location)
            val decision = DepartureDecisionEngine.evaluate(
                speedKph = speedKph,
                distanceFromParkedMeters = distance,
                parkedAccuracyMeters = session.parkedAccuracyMeters,
                currentAccuracyMeters = location.accuracy
            )

            if (decision.moving) consecutiveMovement++ else if (decision.clearlyStationary) consecutiveMovement = 0

            store.recordEvent("დაძვრის შემოწმება: speed=${speedKph?.toInt() ?: -1}km/h distance=${distance?.toInt() ?: -1}m threshold=${decision.distanceThresholdMeters.toInt()}m hit=$consecutiveMovement")
            if (consecutiveMovement >= 2) {
                onDepartureConfirmed(session, speedKph, distance)
                stop()
            }
        }
    }

    fun start() {
        if (running) return
        val fine = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return

        running = true
        consecutiveMovement = 0
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 15_000L)
            .setMinUpdateIntervalMillis(8_000L)
            .setMinUpdateDistanceMeters(15f)
            .setMaxUpdateDelayMillis(30_000L)
            .build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        handler.postDelayed(timeout, 20 * 60_000L)
        store.recordEvent("მანქანაში დაბრუნება — დაძვრის მონიტორინგი ჩაირთო")
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(timeout)
        client.removeLocationUpdates(callback)
        consecutiveMovement = 0
    }

    private fun distanceFromParked(session: ParkingSession, location: Location): Float? {
        val lat = session.parkedLatitude ?: return null
        val lon = session.parkedLongitude ?: return null
        val out = FloatArray(1)
        Location.distanceBetween(lat, lon, location.latitude, location.longitude, out)
        return out[0]
    }
}
