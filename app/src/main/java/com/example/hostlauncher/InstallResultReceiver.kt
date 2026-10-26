package com.example.hostlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/**
 * Receives the install outcome from PackageInstaller and relays it to whatever UI is
 * on screen (LauncherActivity) via a local broadcast, so the Activity can react
 * (launch B / show a retry prompt) without being tightly coupled to the receiver.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // The system needs to show its install-confirmation dialog. Launch it.
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                confirm?.let { context.startActivity(it) }
                Log.d(TAG, "Install needs user confirmation")
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Log.d(TAG, "Install success")
                broadcast(context, Result.SUCCESS)
            }

            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                Log.d(TAG, "Install aborted by user")
                broadcast(context, Result.CANCELED)
            }

            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.w(TAG, "Install failed: status=$status msg=$msg")
                broadcast(context, Result.FAILED)
            }
        }
    }

    private fun broadcast(context: Context, result: Result) {
        val i = Intent(LOCAL_ACTION).putExtra(EXTRA_RESULT, result.name)
        LocalBroadcastManager.getInstance(context).sendBroadcast(i)
    }

    enum class Result { SUCCESS, CANCELED, FAILED }

    companion object {
        private const val TAG = "InstallResultReceiver"
        const val LOCAL_ACTION = "com.example.hostlauncher.LOCAL_INSTALL_RESULT"
        const val EXTRA_RESULT = "result"
    }
}
