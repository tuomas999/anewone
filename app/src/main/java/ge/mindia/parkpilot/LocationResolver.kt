package ge.mindia.parkpilot

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale
import java.util.concurrent.Executors

object LocationResolver {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun current(context: Context, callback: (ResolvedLocation?, String?) -> Unit) {
        currentRaw(context) { location, error ->
            if (location == null) callback(null, error)
            else reverseGeocode(context, location, callback)
        }
    }

    fun currentRaw(context: Context, callback: (Location?, String?) -> Unit) {
        val fine = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            callback(null, "მდებარეობის ნებართვა არ არის")
            return
        }
        val source = CancellationTokenSource()
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, source.token)
            .addOnSuccessListener { location ->
                if (location == null) callback(null, "მდებარეობა ვერ განისაზღვრა") else callback(location, null)
            }
            .addOnFailureListener { callback(null, it.message ?: "მდებარეობის შეცდომა") }
    }

    private fun reverseGeocode(context: Context, location: Location, callback: (ResolvedLocation?, String?) -> Unit) {
        executor.execute {
            val result = runCatching {
                val geocoder = Geocoder(context, Locale("ka", "GE"))
                @Suppress("DEPRECATION")
                val addr = geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
                ResolvedLocation(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracy,
                    speedKph = if (location.hasSpeed()) location.speed * 3.6f else null,
                    street = addr?.thoroughfare ?: addr?.subLocality,
                    houseNumber = addr?.subThoroughfare,
                    addressLine = addr?.getAddressLine(0)
                )
            }
            main.post {
                val resolved = result.getOrNull()
                if (resolved == null) callback(location.toResolved(), result.exceptionOrNull()?.message ?: "მისამართი ვერ განისაზღვრა")
                else callback(resolved, null)
            }
        }
    }

    private fun Location.toResolved() = ResolvedLocation(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy,
        speedKph = if (hasSpeed()) speed * 3.6f else null,
        street = null,
        houseNumber = null,
        addressLine = null
    )
}
