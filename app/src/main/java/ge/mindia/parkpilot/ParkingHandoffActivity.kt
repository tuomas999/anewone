package ge.mindia.parkpilot

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * User-driven bridge to the official Parking Tbilisi application.
 * ParkPilot detects the lot and opens the official app; the paid start/stop remains a
 * user-confirmed action inside the official application.
 */
class ParkingHandoffActivity : Activity() {
    private lateinit var mode: Mode
    private var lotCode: String? = null
    private var launchedExternal = false
    private var dialogShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = runCatching {
            Mode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: Mode.START.name)
        }.getOrDefault(Mode.START)
        lotCode = intent.getStringExtra(EXTRA_LOT_CODE) ?: ParkingStore(this).pendingLot()
    }

    override fun onResume() {
        super.onResume()
        if (!launchedExternal) {
            launchedExternal = true
            window.decorView.post { openOfficialApp() }
        } else if (!dialogShown) {
            dialogShown = true
            showReturnConfirmation()
        }
    }

    private fun openOfficialApp() {
        if (mode == Mode.START) {
            val code = lotCode
            if (code.isNullOrBlank()) {
                Toast.makeText(this, "პარკირების ლოტი არჩეული არ არის", Toast.LENGTH_LONG).show()
                finish()
                return
            }
            ParkingStore(this).selectPendingLot(code)
            Notifications.offerStartConfirmation(this, code)
        }

        val result = ParkingTbilisi.open(this, if (mode == Mode.START) lotCode else null)
        if (!result.success) {
            launchedExternal = false
            showLaunchFailure(result.detail)
        }
    }

    private fun showLaunchFailure(detail: String) {
        AlertDialog.Builder(this)
            .setTitle("Parking Tbilisi ვერ გაიხსნა")
            .setMessage("ოფიციალური აპის გახსნა ვერ მოხერხდა ($detail). გადაამოწმე, რომ Parking Tbilisi დაყენებულია და სცადე თავიდან.")
            .setPositiveButton("თავიდან ცდა") { _, _ ->
                launchedExternal = true
                val result = ParkingTbilisi.open(this, if (mode == Mode.START) lotCode else null)
                if (!result.success) finish()
            }
            .setNegativeButton("დახურვა") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun showReturnConfirmation() {
        when (mode) {
            Mode.START -> showStartConfirmation()
            Mode.STOP -> showStopConfirmation()
        }
    }

    private fun showStartConfirmation() {
        val code = lotCode ?: run { finish(); return }
        AlertDialog.Builder(this)
            .setTitle("პარკირება ჩაირთო?")
            .setMessage("Parking Tbilisi-ში $code ლოტზე პარკირება ნამდვილად დაიწყე?")
            .setPositiveButton("დიახ, დავიწყე") { _, _ ->
                sendAction(ParkingActionReceiver.ACTION_STARTED, code)
                finish()
            }
            .setNegativeButton("ჯერ არა") { _, _ -> finish() }
            .setNeutralButton("გახსენი ისევ") { _, _ ->
                dialogShown = false
                ParkingTbilisi.open(this, code)
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun showStopConfirmation() {
        val session = ParkingStore(this).session()
        AlertDialog.Builder(this)
            .setTitle("პარკირება დასრულდა?")
            .setMessage("Parking Tbilisi-ში ${session.lotCode ?: "აქტიური"} პარკირება ნამდვილად დაასრულე?")
            .setPositiveButton("დიახ, დავასრულე") { _, _ ->
                sendAction(ParkingActionReceiver.ACTION_STOPPED, session.lotCode)
                finish()
            }
            .setNegativeButton("ჯერ არა") { _, _ -> finish() }
            .setNeutralButton("გახსენი ისევ") { _, _ ->
                dialogShown = false
                ParkingTbilisi.open(this)
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun sendAction(action: String, code: String?) {
        sendBroadcast(
            Intent(this, ParkingActionReceiver::class.java)
                .setAction(action)
                .putExtra("lot_code", code)
        )
    }

    enum class Mode { START, STOP }

    companion object {
        const val EXTRA_LOT_CODE = "lot_code"
        const val EXTRA_MODE = "handoff_mode"

        fun intent(activity: Activity, mode: Mode, lotCode: String? = null): Intent =
            Intent(activity, ParkingHandoffActivity::class.java)
                .putExtra(EXTRA_MODE, mode.name)
                .putExtra(EXTRA_LOT_CODE, lotCode)
    }
}
