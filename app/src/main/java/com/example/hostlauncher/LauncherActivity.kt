package com.example.hostlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.hostlauncher.databinding.ActivityLauncherBinding

/**
 * Entry point. On every launch it first checks whether B is installed:
 *   installed    -> launch B immediately (feels like B runs inside A)
 *   not installed -> prompt -> ensure install permission -> install B -> launch B
 *
 * During installation it shows an indeterminate water-ripple animation (no real
 * percentage) with a "please don't close the app" message, localized to the device
 * language.
 */
class LauncherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLauncherBinding

    // True while our install session is in flight, so returning to the foreground
    // (e.g. from the system install dialog) keeps the ripple instead of the prompt.
    private var installing = false

    // Returned from the "install unknown apps" settings screen; re-check and continue.
    private val unknownSourcesLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (InstallManager.canInstall(this)) {
                startInstall()
            } else {
                showError(getString(R.string.status_need_permission))
            }
        }

    // Listens for the install outcome relayed by InstallResultReceiver.
    private val installResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getStringExtra(InstallResultReceiver.EXTRA_RESULT)) {
                InstallResultReceiver.Result.SUCCESS.name -> launchBOrError()
                InstallResultReceiver.Result.CANCELED.name -> {
                    installing = false
                    showError(getString(R.string.status_install_canceled))
                }
                else -> {
                    installing = false
                    showError(getString(R.string.status_install_failed))
                }
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
        evaluate()
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(installResultReceiver)
        super.onDestroy()
    }

    /** Step 6: prefer detecting B; if present, launch straight away. */
    private fun evaluate() {
        when {
            InstallManager.isBInstalled(this) -> launchBOrError()
            installing -> showInstalling()          // keep ripple while install is running
            !InstallManager.hasEmbeddedApk(this) ->
                showError(getString(R.string.status_missing_asset), showButton = false)
            else -> showNeedComponent()
        }
    }

    /** Step 2/3: user tapped "install". Ensure permission, then install. */
    private fun onInstallClicked() {
        if (!InstallManager.canInstall(this)) {
            unknownSourcesLauncher.launch(InstallManager.unknownSourcesSettingsIntent(this))
            return
        }
        startInstall()
    }

    /**
     * Step 4: write B.apk into a session and commit (off the main thread). The UI shows
     * the water-ripple animation; the system install dialog then takes over.
     */
    private fun startInstall() {
        installing = true
        showInstalling()
        Thread {
            try {
                InstallManager.installEmbeddedApk(this)
            } catch (e: Exception) {
                runOnUiThread {
                    installing = false
                    showError(getString(R.string.status_install_failed))
                }
            }
        }.start()
    }

    /** Step 5: install finished -> launch B. */
    private fun launchBOrError() {
        installing = false
        showLaunching()
        if (InstallManager.launchB(this)) {
            finish()
        } else {
            showError(getString(R.string.status_launch_failed))
        }
    }

    // ---- UI states (all rendered inside the centered Material card) ----------

    private fun showChecking() = setState(
        message = getString(R.string.status_checking)
    )

    /** Installing: water-ripple animation + localized "do not close" message. */
    private fun showInstalling() = setState(
        message = getString(R.string.status_installing),
        icon = false, wave = true
    )

    private fun showLaunching() = setState(
        message = getString(R.string.status_launching)
    )

    /** Initial prompt with icon, title, description and the install button. */
    private fun showNeedComponent() = setState(
        title = getString(R.string.need_component_title),
        message = getString(R.string.need_component_desc),
        button = true
    )

    /** Prompt plus an error/status message. */
    private fun showError(msg: String, showButton: Boolean = true) = setState(
        title = getString(R.string.need_component_title),
        message = msg,
        button = showButton
    )

    private fun setState(
        title: String? = null,
        message: String = "",
        icon: Boolean = true,
        wave: Boolean = false,
        button: Boolean = false,
    ) {
        binding.iconWrap.visibility = if (icon) View.VISIBLE else View.GONE
        binding.wave.visibility = if (wave) View.VISIBLE else View.GONE
        binding.actionButton.visibility = if (button) View.VISIBLE else View.GONE
        binding.titleText.visibility = if (title.isNullOrEmpty()) View.GONE else View.VISIBLE
        binding.titleText.text = title.orEmpty()
        binding.messageText.visibility = if (message.isEmpty()) View.GONE else View.VISIBLE
        binding.messageText.text = message
    }
}
