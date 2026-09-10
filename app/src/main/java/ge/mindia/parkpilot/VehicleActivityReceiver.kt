package ge.mindia.parkpilot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

class VehicleActivityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = ParkingStore(context)
        if (!store.monitoringEnabled()) return

        if (intent.action == ActivityRecognitionManager.ACTION_BOOTSTRAP && ActivityRecognitionResult.hasResult(intent)) {
            val result = ActivityRecognitionResult.extractResult(intent)
            val detected = result?.mostProbableActivity
            if (detected != null) {
                val activity = detected.type.toMotionActivity()
                store.recordMotion(activity)
                if (activity == MotionActivity.IN_VEHICLE) store.markDriving(source = "bootstrap:IN_VEHICLE")
                store.recordEvent("Activity bootstrap: ${activity.name} confidence=${detected.confidence}")
                signalService(context, activity, entering = true)
            }
            ActivityRecognitionManager.removeBootstrap(context)
            return
        }

        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        result.transitionEvents.forEach { event ->
            val activity = event.activityType.toMotionActivity()
            val entering = event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER
            val now = System.currentTimeMillis()

            if (entering) store.recordMotion(activity, now)
            if (activity == MotionActivity.IN_VEHICLE && entering) store.markDriving(now, "activity:IN_VEHICLE")
            if (activity == MotionActivity.IN_VEHICLE && !entering) store.markVehicleExited(now)

            store.recordEvent("Activity: ${activity.name} ${if (entering) "ENTER" else "EXIT"}")
            signalService(context, activity, entering)
        }
    }

    private fun signalService(context: Context, activity: MotionActivity, entering: Boolean) {
        context.sendBroadcast(
            Intent(CarMonitorService.ACTION_ACTIVITY_SIGNAL)
                .setPackage(context.packageName)
                .putExtra(CarMonitorService.EXTRA_ACTIVITY, activity.name)
                .putExtra(CarMonitorService.EXTRA_ENTERING, entering)
        )
    }

    private fun Int.toMotionActivity(): MotionActivity = when (this) {
        DetectedActivity.IN_VEHICLE -> MotionActivity.IN_VEHICLE
        DetectedActivity.WALKING -> MotionActivity.WALKING
        DetectedActivity.ON_FOOT -> MotionActivity.ON_FOOT
        DetectedActivity.STILL -> MotionActivity.STILL
        DetectedActivity.RUNNING -> MotionActivity.RUNNING
        DetectedActivity.ON_BICYCLE -> MotionActivity.ON_BICYCLE
        else -> MotionActivity.UNKNOWN
    }
}
