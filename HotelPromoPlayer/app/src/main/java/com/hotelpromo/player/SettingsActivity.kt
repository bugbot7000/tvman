package com.hotelpromo.player

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import java.io.File

/**
 * The only "screen" in this app. It is shown:
 *  - the very first time the app is opened (no video picked/downloaded
 *    yet — if DownloadConfig.VIDEO_DOWNLOAD_URL is set, this triggers an
 *    automatic download instead of asking the user to pick a file), or
 *  - if the previously set video can no longer be read, or
 *  - when the user types 2580 on the remote while the video is playing
 *    (PlayerActivity relaunches this activity with EXTRA_FORCE_SHOW).
 *
 * Any other time a valid video is already saved, this activity jumps
 * straight to PlayerActivity and finishes, so the settings screen is
 * never actually seen.
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FORCE_SHOW = "force_show"
        const val DOWNLOADED_FILENAME = "promo.mp4"
    }

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var buttonRetryDownload: Button
    private lateinit var buttonSelectFile: Button
    private lateinit var checkboxBoot: CheckBox

    private val downloadHandler = Handler(Looper.getMainLooper())
    private var activeDownloadId: Long = -1
    private var pollingDownload = false

    private val pickVideoLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                Prefs.setVideoUri(this, uri)
                goToPlayer()
            }
        }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                launchPicker()
            } else {
                Toast.makeText(
                    this,
                    "Storage permission is needed to pick a video file",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val forceShow = intent?.getBooleanExtra(EXTRA_FORCE_SHOW, false) ?: false

        if (!forceShow && hasPlayableVideo()) {
            goToPlayer()
            return
        }

        setContentView(R.layout.activity_settings)

        progressBar = findViewById(R.id.progressDownload)
        statusText = findViewById(R.id.statusText)
        buttonRetryDownload = findViewById(R.id.buttonRetryDownload)
        buttonSelectFile = findViewById(R.id.buttonSelectFile)
        checkboxBoot = findViewById(R.id.checkboxLaunchOnBoot)

        checkboxBoot.isChecked = Prefs.isLaunchOnBootEnabled(this)
        checkboxBoot.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setLaunchOnBootEnabled(this, isChecked)
        }

        buttonSelectFile.setOnClickListener { onSelectFileClicked() }
        buttonRetryDownload.setOnClickListener { startAutoDownload() }

        // Only auto-download when nobody explicitly asked for this screen
        // (i.e. not the 2580 code path) and there's genuinely no video yet.
        if (!forceShow && !hasPlayableVideo() && DownloadConfig.VIDEO_DOWNLOAD_URL.isNotBlank()) {
            startAutoDownload()
        } else {
            showManualUi()
        }
    }

    // ---- Manual picking ----------------------------------------------

    private fun onSelectFileClicked() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val alreadyGranted = ContextCompat.checkSelfPermission(this, permission) ==
                PackageManager.PERMISSION_GRANTED

        if (alreadyGranted) {
            launchPicker()
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun launchPicker() {
        pickVideoLauncher.launch(arrayOf("video/*"))
    }

    // ---- Auto-download --------------------------------------------------

    private fun startAutoDownload() {
        val url = DownloadConfig.VIDEO_DOWNLOAD_URL
        if (url.isBlank()) {
            showManualUi()
            return
        }

        showDownloadingUi()

        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url))
            .setDestinationInExternalFilesDir(this, null, DOWNLOADED_FILENAME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)

        activeDownloadId = try {
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            showError("Couldn't start the download.\n${e.message}")
            return
        }

        pollingDownload = true
        pollDownloadProgress(downloadManager)
    }

    private fun pollDownloadProgress(downloadManager: DownloadManager) {
        if (!pollingDownload) return

        val query = DownloadManager.Query().setFilterById(activeDownloadId)
        downloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) {
                showError("Download disappeared unexpectedly.")
                return
            }

            val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val downloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

            val status = cursor.getInt(statusIdx)
            val downloadedBytes = cursor.getLong(downloadedIdx)
            val totalBytes = cursor.getLong(totalIdx)

            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    pollingDownload = false
                    onDownloadFinished()
                    return
                }
                DownloadManager.STATUS_FAILED -> {
                    pollingDownload = false
                    val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                    val reason = cursor.getInt(reasonIdx)
                    showError("Download failed (error code $reason).")
                    return
                }
                else -> {
                    if (totalBytes > 0) {
                        val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                        progressBar.isIndeterminate = false
                        progressBar.progress = percent
                        statusText.text = "Downloading video... $percent%"
                    } else {
                        progressBar.isIndeterminate = true
                        statusText.text = "Downloading video..."
                    }
                }
            }
        }

        downloadHandler.postDelayed({ pollDownloadProgress(downloadManager) }, 500)
    }

    private fun onDownloadFinished() {
        val file = File(getExternalFilesDir(null), DOWNLOADED_FILENAME)
        if (!file.exists() || file.length() == 0L) {
            showError("Downloaded file is empty or missing.\nCheck the download URL.")
            return
        }
        Prefs.setVideoUri(this, Uri.fromFile(file))
        goToPlayer()
    }

    private fun showError(message: String) {
        statusText.text = "$message\n\nYou can retry, or pick a file manually below."
        showManualUi()
    }

    // ---- UI state helpers ----------------------------------------------

    private fun showDownloadingUi() {
        progressBar.visibility = android.view.View.VISIBLE
        progressBar.isIndeterminate = true
        statusText.visibility = android.view.View.VISIBLE
        statusText.text = "Downloading video..."
        buttonRetryDownload.visibility = android.view.View.GONE
        buttonSelectFile.visibility = android.view.View.GONE
        checkboxBoot.visibility = android.view.View.GONE
    }

    private fun showManualUi() {
        progressBar.visibility = android.view.View.GONE
        buttonSelectFile.visibility = android.view.View.VISIBLE
        checkboxBoot.visibility = android.view.View.VISIBLE
        buttonRetryDownload.visibility =
            if (DownloadConfig.VIDEO_DOWNLOAD_URL.isNotBlank()) android.view.View.VISIBLE
            else android.view.View.GONE
        if (statusText.text.isNullOrBlank()) {
            statusText.visibility = android.view.View.GONE
        } else {
            statusText.visibility = android.view.View.VISIBLE
        }
    }

    private fun hasPlayableVideo(): Boolean {
        val uri = Prefs.getVideoUri(this) ?: return false
        return try {
            contentResolver.openInputStream(uri)?.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun goToPlayer() {
        startActivity(Intent(this, PlayerActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingDownload = false
        downloadHandler.removeCallbacksAndMessages(null)
    }
}
