package ge.mindia.parkpilot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

object Notifications {
    const val CHANNEL = "parking_reminders"
    const val MONITOR_CHANNEL = "car_monitor"
    const val MONITOR_ID = 1
    const val CANDIDATE_ID = 100
    const val START_ID = 101
    const val STOP_ID = 102
    const val RESUME_ID = 103

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "პარკირების შეხსენებები", NotificationManager.IMPORTANCE_HIGH))
        manager.createNotificationChannel(NotificationChannel(MONITOR_CHANNEL, "მანქანის მონიტორინგი", NotificationManager.IMPORTANCE_LOW))
    }

    fun monitor(context: Context, carName: String?, mode: DetectionMode = DetectionMode.BLUETOOTH): Notification = NotificationCompat.Builder(context, MONITOR_CHANNEL)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setContentTitle("ParkPilot მუშაობს")
        .setContentText(
            if (mode == DetectionMode.MOTION_ONLY) "მოძრაობის ტიპს და GPS-ს აკვირდება"
            else if (carName.isNullOrBlank()) "მანქანის Bluetooth-ს და მოძრაობას აკვირდება"
            else "$carName — კავშირს და მოძრაობას აკვირდება"
        )
        .setContentIntent(mainActivity(context, 300))
        .setOngoing(true)
        .build()

    fun offerCandidates(context: Context, candidates: List<ParkingCandidate>, address: String?, evidence: ParkingEvidence) {
        val top = candidates.firstOrNull()
        val preview = candidates.take(3).joinToString(" • ") { it.code }
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(if (top != null) "სავარაუდო პარკინგი: ${top.code}" else "სავარაუდოდ დააპარკინგე")
            .setContentText(address ?: "პარკირების ადგილი გადაამოწმე")
            .setStyle(NotificationCompat.BigTextStyle().bigText(buildString {
                if (!address.isNullOrBlank()) append(address).append("\n")
                if (candidates.isNotEmpty()) append("სავარაუდო ლოტები: $preview\n")
                append("ParkPilot confidence: ${evidence.score}/100")
            }))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(mainActivity(context, 301))
            .setAutoCancel(false)

        val clearTop = top != null && (candidates.size == 1 || top.score - candidates[1].score >= 15)
        if (clearTop && top != null) b.addAction(0, "დაწყება ${top.code}", handoff(context, top.code, ParkingHandoffActivity.Mode.START, 210))
        b.addAction(0, if (clearTop) "სხვა ლოტი" else "ლოტის არჩევა", mainActivity(context, 211))
        b.addAction(0, "არ ვპარკინგობ", action(context, ParkingActionReceiver.ACTION_NOT_PARKING, null, 212))
        context.getSystemService(NotificationManager::class.java).notify(CANDIDATE_ID, b.build())
    }

    fun offerManualLotSelection(context: Context, detail: String, evidence: ParkingEvidence? = null) {
        val suffix = evidence?.let { "\nParkPilot confidence: ${it.score}/100" }.orEmpty()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle("სავარაუდოდ დააპარკინგე — ლოტი აირჩიე")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$detail\nParkPilot-ში მოძებნე ლოტის კოდი ან მისამართი.$suffix"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(mainActivity(context, 302))
            .addAction(0, "ლოტის არჩევა", mainActivity(context, 213))
            .addAction(0, "არ ვპარკინგობ", action(context, ParkingActionReceiver.ACTION_NOT_PARKING, null, 214))
            .setAutoCancel(false)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(CANDIDATE_ID, notification)
    }

    fun offerStartConfirmation(context: Context, code: String) {
        val confirm = action(context, ParkingActionReceiver.ACTION_STARTED, code, 201)
        val open = handoff(context, code, ParkingHandoffActivity.Mode.START, 202)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("ლოტი $code არჩეულია")
            .setContentText("$code დაკოპირებულია. Parking Tbilisi-ში ჩართე პარკირება და შემდეგ დააჭირე „დავიწყე“")
            .setStyle(NotificationCompat.BigTextStyle().bigText("ParkPilot ანგარიშს/პაროლს არ ინახავს. Parking Tbilisi-ში აირჩიე ან ჩასვი $code, ჩართე პარკირება და მხოლოდ შემდეგ დაადასტურე „დავიწყე“."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOngoing(true)
            .addAction(0, "გახსენი Parking", open)
            .addAction(0, "დავიწყე", confirm)
            .build()
        context.getSystemService(NotificationManager::class.java).apply {
            cancel(CANDIDATE_ID)
            notify(START_ID, notification)
        }
    }

    fun offerStop(context: Context, session: ParkingSession, detail: String? = null) {
        val age = System.currentTimeMillis() - session.startedAt
        val stale = age > 18 * 60 * 60 * 1000L
        val confirm = action(context, ParkingActionReceiver.ACTION_STOPPED, session.lotCode, 203)
        val open = handoff(context, null, ParkingHandoffActivity.Mode.STOP, 204)
        val title = if (stale) "გადაამოწმე ძველი ParkPilot სესია" else "მანქანა დაიძრა — ${session.lotCode ?: ""} ჯერ აქტიურია"
        val text = if (stale) "ჩანაწერი 18 საათზე ძველია — Parking Tbilisi-ში გადაამოწმე" else detail ?: "Parking Tbilisi-ში დაასრულე პარკირება"
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\nParkPilot ავტომატურად არ წყვეტს ფასიან პარკირებას — საბოლოო მოქმედება შენ უნდა დაადასტურო ოფიციალურ აპში."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOngoing(true)
            .addAction(0, "გახსენი Parking", open)
            .addAction(0, "დავასრულე", confirm)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(STOP_ID, notification)
    }

    fun offerResumeMonitoring(context: Context) {
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("ParkPilot მონიტორინგის განახლება")
            .setContentText("ტელეფონი გადაიტვირთა — გახსენი ParkPilot და ხელახლა ჩართე მონიტორინგი")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(mainActivity(context, 305))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(RESUME_ID, notification)
    }

    fun cancel(context: Context, id: Int) = context.getSystemService(NotificationManager::class.java).cancel(id)

    private fun mainActivity(context: Context, request: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun action(context: Context, action: String, code: String?, request: Int): PendingIntent {
        val intent = Intent(context, ParkingActionReceiver::class.java).setAction(action).putExtra("lot_code", code)
        return PendingIntent.getBroadcast(context, request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun handoff(context: Context, code: String?, mode: ParkingHandoffActivity.Mode, request: Int): PendingIntent {
        val intent = Intent(context, ParkingHandoffActivity::class.java)
            .putExtra(ParkingHandoffActivity.EXTRA_MODE, mode.name)
            .putExtra(ParkingHandoffActivity.EXTRA_LOT_CODE, code)
        return PendingIntent.getActivity(context, request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
