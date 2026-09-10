package ge.mindia.parkpilot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val store = ParkingStore(context)
        if (store.monitoringEnabled()) {
            store.setMonitoringEnabled(false)
            ActivityRecognitionManager.unregister(context)
            store.recordEvent("ტელეფონი გადაიტვირთა — მონიტორინგი ხელახლა ჩასართავია")
            Notifications.createChannels(context)
            Notifications.offerResumeMonitoring(context)
        }
    }
}
