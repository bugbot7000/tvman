package com.hotelpromo.player

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Only shown:
 *  - the very first time the app is opened (no video downloaded yet), or
 *  - if the previously downloaded video can no longer be read, or
 *  - when the user types 2580 on the remote while the video is playing.
 *
 * Whenever a working video already exists and 2580 wasn't used to get
 * here, this jumps straight to PlayerActivity with NO check at all —
 * the update check happens silently in the background from inside
 * PlayerActivity instead, so guests never see a "checking" screen on
 * an ordinary app open.
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FORCE_SHOW = "force_show"
    }

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var buttonAction: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val forceShow = intent?.getBooleanExtra(EXTRA_FORCE_SHOW, false) ?: false

        // Only case that needs zero UI at all: we already have a video
        // and nobody explicitly asked to see this screen. Straight to
        // the player, no check.
        if (!forceShow && VideoUpdateManager.hasPlayableVideo(this)) {
            goToPlayer()
            return
        }

        setContentView(R.layout.activity_settings)

        progressBar = findViewById(R.id.progressDownload)
        statusText = findViewById(R.id.statusText)
        buttonAction = findViewById(R.id.buttonAction)

        buttonAction.setOnClickListener { runCheck() }

        if (!forceShow) {
            // No video yet — check and download automatically.
            runCheck()
        } else {
            // 2580 was typed — just show the idle screen, let the user
            // press the button themselves.
            showIdleUi()
        }
    }

    private fun runCheck() {
        showCheckingUi()
        VideoUpdateManager.checkForUpdate(
            context = this,
            onProgress = { progress -> applyProgress(progress) },
            onResult = { result -> applyResult(result) }
        )
    }

    private fun applyProgress(progress: VideoUpdateManager.Progress) {
        progressBar.isIndeterminate = true
        statusText.text = when (progress) {
            is VideoUpdateManager.Progress.CheckingPointer -> "Checking for the latest video..."
            is VideoUpdateManager.Progress.CheckingStorage -> "Checking available storage..."
            is VideoUpdateManager.Progress.Downloading -> {
                val percent = progress.percent
                if (percent != null) {
                    progressBar.isIndeterminate = false
                    progressBar.progress = percent
                    "Downloading video... $percent%"
                } else {
                    "Downloading video..."
                }
            }
        }
    }

    private fun applyResult(result: VideoUpdateManager.Result) {
        when (result) {
            is VideoUpdateManager.Result.Updated -> goToPlayer()
            is VideoUpdateManager.Result.NoUpdateNeeded -> goToPlayer()
            is VideoUpdateManager.Result.Failed -> {
                if (VideoUpdateManager.hasPlayableVideo(this)) {
                    // We still have a working video — just play that.
                    goToPlayer()
                } else {
                    statusText.text = "${result.message}\n\nIt will retry automatically, or use the button below."
                    showIdleUi()
                }
            }
        }
    }

    private fun showCheckingUi() {
        progressBar.visibility = android.view.View.VISIBLE
        progressBar.isIndeterminate = true
        statusText.visibility = android.view.View.VISIBLE
        buttonAction.visibility = android.view.View.GONE
    }

    private fun showIdleUi() {
        progressBar.visibility = android.view.View.GONE
        val pointerConfigured = DownloadConfig.VIDEO_DOWNLOAD_URL.isNotBlank()
        buttonAction.visibility = if (pointerConfigured) android.view.View.VISIBLE else android.view.View.GONE
        buttonAction.setText(
            if (VideoUpdateManager.hasPlayableVideo(this)) R.string.check_update else R.string.retry_download
        )
        statusText.visibility = if (statusText.text.isNullOrBlank()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun goToPlayer() {
        startActivity(Intent(this, PlayerActivity::class.java))
        finish()
    }

    // Back button is disabled here too, for the same reason as
    // PlayerActivity — no path off this screen except the app's own
    // flow (the action button, or an automatic check completing).
    @Suppress("MissingSuperCall")
    override fun onBackPressed() {
        // Intentionally does nothing.
    }
}
