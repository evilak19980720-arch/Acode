package com.example.hostlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.widget.Toast
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

    /** Step 4: kick off the PackageInstaller session. */
    private fun startInstall() {
        showInstalling()
        try {
            InstallManager.installEmbeddedApk(this)
        } catch (e: Exception) {
            showPrompt(getString(R.string.status_install_failed))
        }
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
        progress = true, button = false, prompt = false
    )

    private fun showInstalling() = setState(
        status = getString(R.string.status_installing),
        progress = true, button = false, prompt = false
    )

    private fun showLaunching() = setState(
        status = getString(R.string.status_launching),
        progress = true, button = false, prompt = false
    )

    private fun showPrompt(status: String, showButton: Boolean = true) = setState(
        status = status, progress = false, button = showButton, prompt = true
    )

    private fun setState(
        status: String,
        progress: Boolean,
        button: Boolean,
        prompt: Boolean,
    ) {
        binding.progress.visibility = if (progress) View.VISIBLE else View.GONE
        binding.actionButton.visibility = if (button) View.VISIBLE else View.GONE
        binding.titleText.visibility = if (prompt) View.VISIBLE else View.GONE
        binding.descText.visibility = if (prompt) View.VISIBLE else View.GONE
        binding.statusText.text = status
    }
}
