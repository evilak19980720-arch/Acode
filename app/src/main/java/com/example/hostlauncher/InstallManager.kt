package com.example.hostlauncher

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Drives the "install the embedded app B, then launch it" flow for a self-distributed
 * host app (route 1: B is a real, independently installed app; the host just installs
 * and launches it so it feels seamless).
 *
 * The single source of truth for "is B installed" is [isBInstalled] (a real query of the
 * package manager), NOT a stored flag — the user can uninstall B at any time.
 */
object InstallManager {

    private const val TAG = "InstallManager"

    /** Package id of the embedded app B. MUST match B.apk and the <queries> entry in the manifest. */
    const val PKG_B = "com.example.b"

    /** File name of the embedded APK under src/main/assets/. */
    private const val ASSET_APK = "B.apk"

    /** Action string for the install-result broadcast. */
    const val ACTION_INSTALL_RESULT = "com.example.hostlauncher.INSTALL_RESULT"

    /** @return true if app B is currently installed on the device. */
    fun isBInstalled(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(PKG_B, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** @return true if the embedded B.apk is present in assets. */
    fun hasEmbeddedApk(ctx: Context): Boolean = try {
        ctx.assets.open(ASSET_APK).use { true }
    } catch (e: Exception) {
        false
    }

    /**
     * Whether the host may request installs. Below API 26 "unknown sources" is a global
     * setting (no per-app grant), so we optimistically return true there.
     */
    fun canInstall(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) true
        else ctx.packageManager.canRequestPackageInstalls()

    /** Intent that opens the per-app "install unknown apps" settings screen (API 26+). */
    fun unknownSourcesSettingsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${ctx.packageName}"))

    /**
     * Streams the embedded B.apk into a PackageInstaller session and commits it.
     * The system then shows its own install-confirmation dialog (unavoidable without
     * root / system signature). The result arrives at [InstallResultReceiver].
     *
     * The UI shows an indeterminate "installing" animation rather than a real
     * percentage, so no progress is reported back.
     *
     * @return the session id of the committed install.
     * @throws Exception if the asset is missing or writing the session fails.
     */
    fun installEmbeddedApk(ctx: Context): Int {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )

        // Size of the embedded APK (works because we set noCompress "apk").
        val total: Long = ctx.assets.openFd(ASSET_APK).use { it.length }
        params.setSize(total)

        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            ctx.assets.open(ASSET_APK).use { input ->
                session.openWrite("b_apk", 0, total).use { out ->
                    input.copyTo(out, bufferSize = 64 * 1024)
                    session.fsync(out)
                }
            }

            val intent = Intent(ctx, InstallResultReceiver::class.java)
                .setAction(ACTION_INSTALL_RESULT)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pending = PendingIntent.getBroadcast(ctx, sessionId, intent, flags)
            session.commit(pending.intentSender)
            Log.d(TAG, "Committed install session $sessionId for $PKG_B")
        }
        return sessionId
    }

    /**
     * Launches app B. Call this after a successful install, or on startup when B is
     * already installed.
     * @return true if a launch intent was found and started.
     */
    fun launchB(ctx: Context): Boolean {
        val launch = ctx.packageManager.getLaunchIntentForPackage(PKG_B) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(launch)
        return true
    }
}
