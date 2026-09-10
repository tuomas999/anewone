package ge.mindia.parkpilot

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity

object ActivityRecognitionManager {
    const val ACTION_TRANSITION = "ge.mindia.parkpilot.ACTIVITY_TRANSITION_CALLBACK"
    const val ACTION_BOOTSTRAP = "ge.mindia.parkpilot.ACTIVITY_BOOTSTRAP_CALLBACK"

    fun register(context: Context, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        if (!hasPermission(context)) {
            onResult(false, "Activity Recognition permission missing")
            return
        }
        val activities = listOf(
            DetectedActivity.IN_VEHICLE,
            DetectedActivity.WALKING,
            DetectedActivity.ON_FOOT,
            DetectedActivity.STILL,
            DetectedActivity.RUNNING
        )
        val transitions = activities.flatMap { activity ->
            listOf(
                ActivityTransition.Builder().setActivityType(activity).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER).build(),
                ActivityTransition.Builder().setActivityType(activity).setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT).build()
            )
        }
        ActivityRecognition.getClient(context)
            .requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), transitionPendingIntent(context))
            .addOnSuccessListener {
                requestBootstrap(context)
                onResult(true, null)
            }
            .addOnFailureListener { onResult(false, it.message ?: it.javaClass.simpleName) }
    }

    /** One short Sampling API request seeds current state if monitoring starts mid-trip. */
    private fun requestBootstrap(context: Context) {
        if (!hasPermission(context)) return
        ActivityRecognition.getClient(context).requestActivityUpdates(0L, bootstrapPendingIntent(context))
    }

    fun removeBootstrap(context: Context) {
        if (!hasPermission(context)) return
        ActivityRecognition.getClient(context).removeActivityUpdates(bootstrapPendingIntent(context))
    }

    fun unregister(context: Context) {
        if (!hasPermission(context)) return
        ActivityRecognition.getClient(context).removeActivityTransitionUpdates(transitionPendingIntent(context))
        ActivityRecognition.getClient(context).removeActivityUpdates(bootstrapPendingIntent(context))
    }

    private fun transitionPendingIntent(context: Context): PendingIntent = pendingIntent(context, 700, ACTION_TRANSITION)
    private fun bootstrapPendingIntent(context: Context): PendingIntent = pendingIntent(context, 701, ACTION_BOOTSTRAP)

    private fun pendingIntent(context: Context, request: Int, action: String): PendingIntent {
        val intent = Intent(context, VehicleActivityReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            request,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    private fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
}
