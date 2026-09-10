package ge.mindia.parkpilot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ParkingActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = ParkingStore(context)
        when (intent.action) {
            ACTION_STARTED -> {
                val code = intent.getStringExtra("lot_code") ?: store.pendingLot() ?: return
                store.start(code)
                store.endTrip()
                store.recordEvent("პარკირება დადასტურდა: $code")
                Notifications.cancel(context, Notifications.START_ID)
                Notifications.cancel(context, Notifications.CANDIDATE_ID)
                signalSessionChanged(context)
            }
            ACTION_STOPPED -> {
                store.recordEvent("პარკირების დასრულება დადასტურდა")
                store.stop()
                Notifications.cancel(context, Notifications.STOP_ID)
                signalSessionChanged(context)
            }
            ACTION_NOT_PARKING -> {
                store.suppressParkingPrompt(20)
                store.recordEvent("მომხმარებელმა მონიშნა: არ ვპარკინგობ — 20 წუთი არ შევაწუხოთ")
                Notifications.cancel(context, Notifications.CANDIDATE_ID)
                Notifications.cancel(context, Notifications.START_ID)
            }
        }
    }

    private fun signalSessionChanged(context: Context) {
        context.sendBroadcast(Intent(CarMonitorService.ACTION_SESSION_CHANGED).setPackage(context.packageName))
    }

    companion object {
        const val ACTION_STARTED = "ge.mindia.parkpilot.STARTED"
        const val ACTION_STOPPED = "ge.mindia.parkpilot.STOPPED"
        const val ACTION_NOT_PARKING = "ge.mindia.parkpilot.NOT_PARKING"
    }
}
