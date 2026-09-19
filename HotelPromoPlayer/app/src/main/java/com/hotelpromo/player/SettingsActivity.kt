package com.hotelpromo.player

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The only "screen" in this app. There is no manual file picker — the
 * video always comes from DownloadConfig.VIDEO_DOWNLOAD_URL (a pointer
 * text file containing the real video's direct-download URL). This
 * screen is shown:
 *  - the very first time the app is opened, before any video has been
 *    downloaded yet, or
 *  - if the previously downloaded video can no longer be read AND
 *    there's nothing to fall back to, or
 *  - when the user types 2580 on the remote while the video is playing
 *    (PlayerActivity relaunches this activity with EXTRA_FORCE_SHOW).
 *
 * Whenever a working video already exists on disk, any failure while
 * checking or downloading an update (no WiFi, bad pointer URL, broken
 * download, low storage, etc.) falls back to that existing video
 * silently — nothing blank is ever shown, and it just tries again next
 * time the app opens (e.g. next reboot).
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FORCE_SHOW = "force_show"
        const val LIVE_FILENAME = "promo.mp4"
        const val TEMP_FILENAME = "promo_download_tmp.mp4"
        const val POINTER_CHECK_TIMEOUT_MS = 8000
        // Require this much headroom over the video's reported size
        // before starting a download, so a download that's slightly
        // larger than expected doesn't run the device out of storage.
        const val STORAGE_SAFETY_MARGIN = 1.1
    }

    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var buttonAction: Button

    private val downloadHandler = Handler(Looper.getMainLooper())
    private var activeDownloadId: Long = -1
    private var pollingDownload = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val forceShow = intent?.getBooleanExtra(EXTRA_FORCE_SHOW, false) ?: false

        setContentView(R.layout.activity_settings)

        progressBar = findViewById(R.id.progressDownload)
        statusText = findViewById(R.id.statusText)
        buttonAction = findViewById(R.id.buttonAction)

        buttonAction.setOnClickListener {
            startPointerCheckThenDownload(preferOldOnFailure = hasPlayableVideo())
        }

        val pointerConfigured = DownloadConfig.VIDEO_DOWNLOAD_URL.isNotBlank()

        when {
            !hasPlayableVideo() && pointerConfigured -> {
                // First run (or file got wiped): resolve the pointer and
                // download automatically. No old file to fall back to,
                // so a failure here does show the idle screen — there's
                // nothing else to play.
                startPointerCheckThenDownload(preferOldOnFailure = false)
            }
            !hasPlayableVideo() -> {
                // No pointer configured at all — nothing this app can do.
                statusText.text = "No video source configured."
                showIdleUi()
            }
            !forceShow && pointerConfigured -> {
                // Already have a working video. Do a quick, silent check
                // for an update before jumping to the player. Any
                // failure just plays the existing video.
                showCheckingUi()
                startPointerCheckThenDownload(preferOldOnFailure = true)
            }
            !forceShow -> {
                // Already have a video, no pointer configured — nothing
                // to check, go straight to the player.
                goToPlayer()
            }
            else -> {
                // forceShow (2580 was typed) — show the idle screen,
                // don't auto-trigger anything.
                showIdleUi()
            }
        }
    }

    // ---- Pointer file check --------------------------------------------

    /**
     * Resolves DownloadConfig.VIDEO_DOWNLOAD_URL (the pointer text file).
     *
     * [preferOldOnFailure]: if true, any failure (pointer unreachable,
     * same video as last time, download failure, low storage, etc.)
     * falls back to playing whatever's already on disk instead of
     * showing an error screen.
     */
    private fun startPointerCheckThenDownload(preferOldOnFailure: Boolean) {
        val pointerUrl = DownloadConfig.VIDEO_DOWNLOAD_URL
        if (pointerUrl.isBlank()) {
            statusText.text = "No video source configured."
            showIdleUi()
            return
        }

        showCheckingUi()

        PointerResolver.resolve(pointerUrl, POINTER_CHECK_TIMEOUT_MS) { resolvedUrl ->
            when {
                resolvedUrl == null -> {
                    fallbackOrError(
                        preferOldOnFailure,
                        "Couldn't check for an update (no connection, or bad pointer URL)."
                    )
                }
                resolvedUrl == Prefs.getLastResolvedVideoUrl(this) && hasPlayableVideo() -> {
                    // Same video as already on disk — nothing to do.
                    if (preferOldOnFailure) {
                        goToPlayer()
                    } else {
                        statusText.text = "Already up to date."
                        showIdleUi()
                    }
                }
                else -> {
                    checkStorageThenDownload(resolvedUrl, preferOldOnFailure)
                }
            }
        }
    }

    private fun fallbackOrError(preferOldOnFailure: Boolean, errorMessage: String) {
        if (preferOldOnFailure && hasPlayableVideo()) {
            goToPlayer()
        } else {
            showError(errorMessage)
        }
    }

    // ---- Storage check --------------------------------------------------

    /**
     * Checks the video's reported size (via a HEAD request) against
     * free space before committing to a download. If the size can't be
     * determined (HEAD request fails or the server doesn't report a
     * length), skips straight to downloading — DownloadManager's own
     * insufficient-space failure is still caught as a safety net.
     */
    private fun checkStorageThenDownload(videoUrl: String, preferOldOnFailure: Boolean) {
        showCheckingUi()
        statusText.text = "Checking available storage..."

        Thread {
            val contentLength = fetchContentLength(videoUrl)
            val freeBytes = getExternalFilesDir(null)?.usableSpace ?: -1L

            Handler(Looper.getMainLooper()).post {
                val needsMoreSpace = contentLength > 0 && freeBytes >= 0 &&
                        freeBytes < (contentLength * STORAGE_SAFETY_MARGIN)

                if (needsMoreSpace) {
                    handleInsufficientStorage(preferOldOnFailure)
                } else {
                    startDownload(videoUrl, preferOldOnFailure)
                }
            }
        }.start()
    }

    private fun fetchContentLength(url: String): Long {
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = POINTER_CHECK_TIMEOUT_MS
                readTimeout = POINTER_CHECK_TIMEOUT_MS
                instanceFollowRedirects = true
            }
            val length = connection.contentLengthLong
            connection.disconnect()
            length
        } catch (e: Exception) {
            -1L
        }
    }

    private fun handleInsufficientStorage(preferOldOnFailure: Boolean) {
        val message = "Not enough free storage on this device to download the video. " +
                "It will automatically try again the next time the app opens."
        fallbackOrError(preferOldOnFailure, message)
    }

    // ---- Download --------------------------------------------------

    /**
     * Downloads to a TEMP file first. The existing live video is never
     * touched until the new one is confirmed fully downloaded and
     * non-empty — so a failed or interrupted download can never corrupt
     * or wipe out a working video.
     */
    private fun startDownload(videoUrl: String, preferOldOnFailure: Boolean) {
        showDownloadingUi()

        // Clean up any leftover temp file from a previous interrupted attempt.
        File(getExternalFilesDir(null), TEMP_FILENAME).delete()

        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(videoUrl))
            .setDestinationInExternalFilesDir(this, null, TEMP_FILENAME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)

        activeDownloadId = try {
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            fallbackOrError(preferOldOnFailure, "Couldn't start the download.\n${e.message}")
            return
        }

        pollingDownload = true
        pollDownloadProgress(downloadManager, videoUrl, preferOldOnFailure)
    }

    private fun pollDownloadProgress(
        downloadManager: DownloadManager,
        videoUrl: String,
        preferOldOnFailure: Boolean
    ) {
        if (!pollingDownload) return

        val query = DownloadManager.Query().setFilterById(activeDownloadId)
        downloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) {
                fallbackOrError(preferOldOnFailure, "Download disappeared unexpectedly.")
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
                    onDownloadFinished(videoUrl, preferOldOnFailure)
                    return
                }
                DownloadManager.STATUS_FAILED -> {
                    pollingDownload = false
                    val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                    val reason = cursor.getInt(reasonIdx)
                    File(getExternalFilesDir(null), TEMP_FILENAME).delete()
                    if (reason == DownloadManager.ERROR_INSUFFICIENT_SPACE) {
                        handleInsufficientStorage(preferOldOnFailure)
                    } else {
                        fallbackOrError(preferOldOnFailure, "Download failed (error code $reason).")
                    }
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

        downloadHandler.postDelayed(
            { pollDownloadProgress(downloadManager, videoUrl, preferOldOnFailure) },
            500
        )
    }

    private fun onDownloadFinished(videoUrl: String, preferOldOnFailure: Boolean) {
        val tempFile = File(getExternalFilesDir(null), TEMP_FILENAME)
        if (!tempFile.exists() || tempFile.length() == 0L) {
            tempFile.delete()
            fallbackOrError(
                preferOldOnFailure,
                "Downloaded file is empty or missing.\nCheck the video URL in your pointer file."
            )
            return
        }

        // Swap the verified new file into place, replacing the old one
        // (if any) only now that we know the new one is good — so at
        // most one video file ever sits on disk at a time.
        val liveFile = File(getExternalFilesDir(null), LIVE_FILENAME)
        liveFile.delete()
        if (!tempFile.renameTo(liveFile)) {
            fallbackOrError(preferOldOnFailure, "Couldn't finish updating the video file.")
            return
        }

        Prefs.setVideoUri(this, Uri.fromFile(liveFile))
        Prefs.setLastResolvedVideoUrl(this, videoUrl)
        goToPlayer()
    }

    private fun showError(message: String) {
        statusText.text = "$message\n\nIt will retry automatically, or use the button below."
        showIdleUi()
    }

    // ---- UI state helpers ----------------------------------------------

    private fun showCheckingUi() {
        progressBar.visibility = android.view.View.VISIBLE
        progressBar.isIndeterminate = true
        statusText.visibility = android.view.View.VISIBLE
        statusText.text = "Checking for the latest video..."
        buttonAction.visibility = android.view.View.GONE
    }

    private fun showDownloadingUi() {
        progressBar.visibility = android.view.View.VISIBLE
        progressBar.isIndeterminate = true
        statusText.visibility = android.view.View.VISIBLE
        statusText.text = "Downloading video..."
        buttonAction.visibility = android.view.View.GONE
    }

    private fun showIdleUi() {
        progressBar.visibility = android.view.View.GONE

        val pointerConfigured = DownloadConfig.VIDEO_DOWNLOAD_URL.isNotBlank()
        if (pointerConfigured) {
            buttonAction.visibility = android.view.View.VISIBLE
            buttonAction.setText(
                if (hasPlayableVideo()) R.string.check_update else R.string.retry_download
            )
        } else {
            buttonAction.visibility = android.view.View.GONE
        }

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
