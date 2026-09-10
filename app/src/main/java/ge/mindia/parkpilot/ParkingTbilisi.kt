package ge.mindia.parkpilot

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object ParkingTbilisi {
    const val PACKAGE = "ge.msda.parking"

    fun intent(context: Context): Intent = context.packageManager.getLaunchIntentForPackage(PACKAGE)
        ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://parking.tbilisi.gov.ge/"))

    fun copyLotCode(context: Context, code: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("ParkPilot lot", code))
        Toast.makeText(context, "$code დაკოპირებულია — Parking Tbilisi-ში ჩასვი/აირჩიე ეს ლოტი", Toast.LENGTH_LONG).show()
    }

    fun open(context: Context, lotCode: String? = null) {
        lotCode?.let { copyLotCode(context, it) }
        val launch = intent(context)
        if (context !is Activity) launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
    }
}
