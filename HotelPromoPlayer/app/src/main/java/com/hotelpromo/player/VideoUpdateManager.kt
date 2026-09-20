package com.hotelpromo.player

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * All the logic for checking the pointer file and downloading a new
 * video if needed, with no UI attached. Callers supply [onProgress] (for
 * showing status, safe to ignore) and [onResult] (called exactly once,
 * on the main thread, when the check is fully done).
 *
 * Used two ways in this app:
 *  - SettingsActivity drives its progress screen from [onProgress] when
 *    there's no video yet, or the user explicitly asked to check (2580).
 *  - PlayerActivity calls this silently after it's already started
 *    playing the existing video, ignoring [onProgress] entirely, and
 *    only acts on [onResult] if a new video was actually downloaded.
 */
object VideoUpdateManager {

    const val LIVE_FILENAME = "promo.mp4"
    const val TEMP_FILENAME = "promo_download_tmp.mp4"
    const val POINTER_CHECK_TIMEOUT_MS = 8000
    const val STORAGE_SAFETY_MARGIN = 1.1

    sealed class Progress {
        object CheckingPointer : Progress()
        object CheckingStorage : Progress()
        data class Downloading(val percent: Int?) : Progress()
    }

    sealed class Result {
        object NoUpdateNeeded : Result()
        data class Updated(val newFileUri: Uri) : Result()
        data class Failed(val message: String) : Result()
    }

    fun checkForUpdate(
        context: Context,
        onProgress: (Progress) -> Unit,
        onResult: (Result) -> Unit
    ) {
        val appContext = context.applicationContext
        val pointerUrl = DownloadConfig.VIDEO_DOWNLOAD_URL
        if (pointerUrl.isBlank()) {
            onResult(Result.Failed("No video source configured."))
            return
        }

        onProgress(Progress.CheckingPointer)
        PointerResolver.resolve(pointerUrl, POINTER_CHECK_TIMEOUT_MS) { resolvedUrl ->
            when {
                resolvedUrl == null -> onResult(
                    Result.Failed("Couldn't check for an update (no connection, or bad pointer URL).")
                )
                resolvedUrl == Prefs.getLastResolvedVideoUrl(appContext) && hasPlayableVideo(appContext) -> {
                    onResult(Result.NoUpdateNeeded)
                }
                else -> checkStorageThenDownload(appContext, resolvedUrl, onProgress, onResult)
            }
        }
    }

    fun hasPlayableVideo(context: Context): Boolean {
        val uri = Prefs.getVideoUri(context) ?: return false
        return try {
            context.contentResolver.openInputStream(uri)?.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---- Storage check --------------------------------------------------

    private fun checkStorageThenDownload(
        context: Context,
        videoUrl: String,
        onProgress: (Progress) -> Unit,
        onResult: (Result) -> Unit
    ) {
        onProgress(Progress.CheckingStorage)
        Thread {
            val contentLength = fetchContentLength(videoUrl)
            val freeBytes = context.getExternalFilesDir(null)?.usableSpace ?: -1L

            Handler(Looper.getMainLooper()).post {
                val needsMoreSpace = contentLength > 0 && freeBytes >= 0 &&
                        freeBytes < (contentLength * STORAGE_SAFETY_MARGIN)

                if (needsMoreSpace) {
                    onResult(
                        Result.Failed(
                            "Not enough free storage on this device to download the video. " +
                                    "It will automatically try again the next time the app opens."
                        )
                    )
                } else {
                    startDownload(context, videoUrl, onProgress, onResult)
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

    // ---- Download --------------------------------------------------

    /**
     * Downloads to a TEMP file first. The existing live video is never
     * touched until the new one is confirmed fully downloaded and
     * non-empty — so a failed or interrupted download can never corrupt
     * or wipe out a working video, and at most one video file ever sits
     * on disk at a time.
     */
    private fun startDownload(
        context: Context,
        videoUrl: String,
        onProgress: (Progress) -> Unit,
        onResult: (Result) -> Unit
    ) {
        onProgress(Progress.Downloading(null))
        File(context.getExternalFilesDir(null), TEMP_FILENAME).delete()

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(videoUrl))
            .setDestinationInExternalFilesDir(context, null, TEMP_FILENAME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)

        val downloadId = try {
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            onResult(Result.Failed("Couldn't start the download.\n${e.message}"))
            return
        }

        pollDownloadProgress(context, downloadManager, downloadId, videoUrl, onProgress, onResult)
    }

    private fun pollDownloadProgress(
        context: Context,
        downloadManager: DownloadManager,
        downloadId: Long,
        videoUrl: String,
        onProgress: (Progress) -> Unit,
        onResult: (Result) -> Unit
    ) {
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) {
                onResult(Result.Failed("Download disappeared unexpectedly."))
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
                    onDownloadFinished(context, videoUrl, onResult)
                    return
                }
                DownloadManager.STATUS_FAILED -> {
                    val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                    val reason = cursor.getInt(reasonIdx)
                    File(context.getExternalFilesDir(null), TEMP_FILENAME).delete()
                    if (reason == DownloadManager.ERROR_INSUFFICIENT_SPACE) {
                        onResult(
                            Result.Failed(
                                "Not enough free storage on this device to download the video. " +
                                        "It will automatically try again the next time the app opens."
                            )
                        )
                    } else {
                        onResult(Result.Failed("Download failed (error code $reason)."))
                    }
                    return
                }
                else -> {
                    if (totalBytes > 0) {
                        onProgress(Progress.Downloading(((downloadedBytes * 100) / totalBytes).toInt()))
                    } else {
                        onProgress(Progress.Downloading(null))
                    }
                }
            }
        }

        Handler(Looper.getMainLooper()).postDelayed(
            { pollDownloadProgress(context, downloadManager, downloadId, videoUrl, onProgress, onResult) },
            500
        )
    }

    private fun onDownloadFinished(context: Context, videoUrl: String, onResult: (Result) -> Unit) {
        val tempFile = File(context.getExternalFilesDir(null), TEMP_FILENAME)
        if (!tempFile.exists() || tempFile.length() == 0L) {
            tempFile.delete()
            onResult(
                Result.Failed("Downloaded file is empty or missing.\nCheck the video URL in your pointer file.")
            )
            return
        }

        // Swap the verified new file into place, replacing the old one
        // (if any) only now that we know the new one is good.
        val liveFile = File(context.getExternalFilesDir(null), LIVE_FILENAME)
        liveFile.delete()
        if (!tempFile.renameTo(liveFile)) {
            onResult(Result.Failed("Couldn't finish updating the video file."))
            return
        }

        val liveUri = Uri.fromFile(liveFile)
        Prefs.setVideoUri(context, liveUri)
        Prefs.setLastResolvedVideoUrl(context, videoUrl)
        onResult(Result.Updated(liveUri))
    }
}
