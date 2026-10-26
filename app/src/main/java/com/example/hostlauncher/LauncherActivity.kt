package com.example.hostlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.hostlauncher.databinding.ActivityLauncherBinding

/**
 * Entry point. On every launch it first checks whether B is installed:
 *   installed    -> launch B immediately (feels like B runs inside A)
 *   not installed -> prompt -> ensure install permission -> install B -> launch B
 */
class LauncherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLauncherBinding

    // Tracks the in-flight install session so progress callbacks can be matched to it.
    private var currentSessionId = -1
    private val mainHandler = Handler(Looper.getMainLooper())

    private val installer by lazy { packageManager.packageInstaller }

    // Overall session progress is 0f..1f: ~0..0.8 is the staging/copy phase (which we
    // already show ourselves), ~0.8..1.0 is the real system install. We only surface the
    // system-install slice here, remapped to 0..100.
    private val sessionCallback = object : PackageInstaller.SessionCallback() {
        override fun onCreated(sessionId: Int) {}
        override fun onBadgingChanged(sessionId: Int) {}
        override fun onActiveChanged(sessionId: Int, active: Boolean) {}
        override fun onFinished(sessionId: Int, success: Boolean) {}
        override fun onProgressChanged(sessionId: Int, progress: Float) {
            if (sessionId != currentSessionId) return
            if (progress >= STAGING_FRACTION) {
                val installPct =
                    (((progress - STAGING_FRACTION) / (1f - STAGING_FRACTION)) * 100)
                        .toInt().coerceIn(0, 100)
                showSystemInstalling(installPct)
            }
        }
    }

    // Returned from the "install unknown apps" settings screen; re-check and continue.
    private val unknownSourcesLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (InstallManager.canInstall(this)) {
                startInstall()
            } else {
                showPrompt(getString(R.string.status_need_permission))
            }
        }

    // Listens for the install outcome relayed by InstallResultReceiver.
    private val installResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getStringExtra(InstallResultReceiver.EXTRA_RESULT)) {
                InstallResultReceiver.Result.SUCCESS.name -> launchBOrError()
                InstallResultReceiver.Result.CANCELED.name ->
                    showPrompt(getString(R.string.status_install_canceled))
                else ->
                    showPrompt(getString(R.string.status_install_failed))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        LocalBroadcastManager.getInstance(this).registerReceiver(
            installResultReceiver,
            IntentFilter(InstallResultReceiver.LOCAL_ACTION)
        )
        installer.registerSessionCallback(sessionCallback, mainHandler)

        binding.actionButton.setOnClickListener { onInstallClicked() }
    }

    override fun onResume() {
        super.onResume()
        // Re-evaluate every time we come to the front (e.g. user uninstalled B, or
        // returned from the system install dialog).
        evaluate()
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(installResultReceiver)
        installer.unregisterSessionCallback(sessionCallback)
        super.onDestroy()
    }

    /** Step 6: prefer detecting B; if present, launch straight away. */
    private fun evaluate() {
        showChecking()
        when {
            InstallManager.isBInstalled(this) -> launchBOrError()
            !InstallManager.hasEmbeddedApk(this) ->
                showPrompt(getString(R.string.status_missing_asset), showButton = false)
            else -> showPrompt(getString(R.string.need_component_desc))
        }
    }

    /** Step 2/3: user tapped "install". Ensure permission, then install. */
    private fun onInstallClicked() {
        if (!InstallManager.canInstall(this)) {
            // Step 3: send the user to grant "install unknown apps", then resume.
            unknownSourcesLauncher.launch(InstallManager.unknownSourcesSettingsIntent(this))
            return
        }
        startInstall()
    }

    /**
     * Step 4: write B.apk into a session (showing real byte progress), then commit.
     * Runs off the main thread so the copy doesn't block the UI and the percentage
     * can animate. After commit the system install UI takes over; its progress is
     * shown by [sessionCallback] when this screen is foreground.
     */
    private fun startInstall() {
        currentSessionId = -1
        showWriting(0)
        Thread {
            try {
                val id = InstallManager.installEmbeddedApk(this) { pct ->
                    runOnUiThread { if (currentSessionId == -1) showWriting(pct) }
                }
                runOnUiThread {
                    currentSessionId = id
                    // Hand-off to the system installer; show an indeterminate state
                    // until onProgressChanged reports the real install fraction.
                    showSystemInstalling(null)
                }
            } catch (e: Exception) {
                runOnUiThread { showPrompt(getString(R.string.status_install_failed)) }
            }
        }.start()
    }

    /** Step 5: install finished -> launch B. */
    private fun launchBOrError() {
        showLaunching()
        if (InstallManager.launchB(this)) {
            // B is now in front; close the host shell so Back doesn't return here.
            finish()
        } else {
            showPrompt(getString(R.string.status_launch_failed))
        }
    }

    // ---- UI states ----------------------------------------------------------

    private fun showChecking() = setState(
        status = getString(R.string.status_checking),
        spinner = true, prompt = false
    )

    /** Writing B.apk into the session — real byte progress. */
    private fun showWriting(percent: Int) = setState(
        status = getString(R.string.status_writing, percent),
        spinner = false, prompt = false, installPercent = percent
    )

    /**
     * System install phase. [percent] null = indeterminate (system UI owns the screen);
     * a value = the real install fraction reported while this screen is foreground.
     */
    private fun showSystemInstalling(percent: Int?) {
        if (percent == null) {
            setState(getString(R.string.status_sys_installing), spinner = true, prompt = false)
        } else {
            setState(
                getString(R.string.status_sys_installing_progress, percent),
                spinner = false, prompt = false, installPercent = percent
            )
        }
    }

    private fun showLaunching() = setState(
        status = getString(R.string.status_launching),
        spinner = true, prompt = false
    )

    private fun showPrompt(status: String, showButton: Boolean = true) = setState(
        status = status, spinner = false, prompt = true, button = showButton
    )

    private fun setState(
        status: String,
        spinner: Boolean,
        prompt: Boolean,
        button: Boolean = false,
        installPercent: Int = -1,
    ) {
        binding.progress.visibility = if (spinner) View.VISIBLE else View.GONE
        binding.actionButton.visibility = if (button) View.VISIBLE else View.GONE
        binding.titleText.visibility = if (prompt) View.VISIBLE else View.GONE
        binding.descText.visibility = if (prompt) View.VISIBLE else View.GONE
        if (installPercent >= 0) {
            binding.installProgress.visibility = View.VISIBLE
            binding.installProgress.progress = installPercent
        } else {
            binding.installProgress.visibility = View.GONE
        }
        binding.statusText.text = status
    }

    companion object {
        /** Fraction of overall session progress the framework assigns to staging/copy. */
        private const val STAGING_FRACTION = 0.8f
    }
}
