package ge.mindia.parkpilot

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast

object ParkingTbilisi {
    const val PACKAGE = "ge.msda.parking"
    private const val WEB_URL = "https://parking.tbilisi.gov.ge/"

    data class LaunchResult(
        val success: Boolean,
        val method: String,
        val detail: String
    )

    fun copyLotCode(context: Context, code: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("ParkPilot lot", code))
        Toast.makeText(context, "$code დაკოპირებულია", Toast.LENGTH_SHORT).show()
    }

    fun isInstalled(context: Context): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(PACKAGE, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(PACKAGE, 0)
        }
        true
    }.getOrDefault(false)

    fun installedVersion(context: Context): String? = runCatching {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(PACKAGE, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(PACKAGE, 0)
        }
        info.versionName
    }.getOrNull()

    /**
     * Opens the official Parking Tbilisi app using the most reliable supported Android route.
     * The user must still confirm the official paid parking action inside Parking Tbilisi.
     */
    fun open(context: Context, lotCode: String? = null): LaunchResult {
        lotCode?.takeIf { it.isNotBlank() }?.let { copyLotCode(context, it) }

        val result = openInstalledApp(context)
        if (result.success) {
            ParkingStore(context).recordEvent("Parking Tbilisi გაიხსნა: ${result.method}")
            return result
        }

        val webResult = openWebsite(context)
        ParkingStore(context).recordEvent(
            if (webResult.success) "Parking Tbilisi app ვერ გაიხსნა; გაიხსნა website"
            else "Parking Tbilisi ვერ გაიხსნა: ${result.detail}; ${webResult.detail}"
        )
        return webResult
    }

    private fun openInstalledApp(context: Context): LaunchResult {
        val pm = context.packageManager

        // Android 13+: IntentSender is not restricted by package visibility and is the
        // preferred way to launch another package's front-door activity.
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                val sender = pm.getLaunchIntentSenderForPackage(PACKAGE)
                sender.sendIntent(context, 0, null, null, null)
                return LaunchResult(true, "launch-intent-sender", "official app")
            } catch (_: IntentSender.SendIntentException) {
                // Continue with explicit fallbacks below.
            } catch (_: Exception) {
                // Continue with explicit fallbacks below.
            }
        }

        try {
            val launch = pm.getLaunchIntentForPackage(PACKAGE)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                if (context !is Activity) launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                return LaunchResult(true, "launch-intent", "official app")
            }
        } catch (_: Exception) {
            // Continue.
        }

        // Last installed-app fallback: resolve MAIN/LAUNCHER explicitly inside the package.
        try {
            val probe = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(PACKAGE)
            @Suppress("DEPRECATION")
            val resolved = pm.queryIntentActivities(probe, 0).firstOrNull()
            if (resolved != null) {
                val explicit = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClassName(resolved.activityInfo.packageName, resolved.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                if (context !is Activity) explicit.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(explicit)
                return LaunchResult(true, "explicit-launcher", resolved.activityInfo.name)
            }
        } catch (_: Exception) {
            // Website fallback will be attempted by caller.
        }

        return LaunchResult(false, "installed-app", "launcher activity not resolved")
    }

    private fun openWebsite(context: Context): LaunchResult = try {
        val web = Intent(Intent.ACTION_VIEW, Uri.parse(WEB_URL))
        if (context !is Activity) web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(web)
        LaunchResult(true, "website-fallback", WEB_URL)
    } catch (e: Exception) {
        LaunchResult(false, "website-fallback", e.javaClass.simpleName)
    }
}
