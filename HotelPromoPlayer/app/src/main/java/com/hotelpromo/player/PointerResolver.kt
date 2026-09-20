package com.hotelpromo.player

import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches a small plain-text file at [pointerUrl] and returns its trimmed
 * contents (expected to be a single direct-download video URL) via
 * [callback] on the main thread. Returns null on any failure or timeout
 * instead of throwing, since this is always used opportunistically —
 * a failed check should just fall back to whatever video is already
 * on disk, never block playback.
 */
object PointerResolver {
    fun resolve(pointerUrl: String, timeoutMs: Int = 8000, callback: (String?) -> Unit) {
        Thread {
            val result = try {
                val connection = (URL(pointerUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    requestMethod = "GET"
                }
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                text.trim().takeIf { it.isNotBlank() }
            } catch (e: Exception) {
                null
            }
            Handler(Looper.getMainLooper()).post { callback(result) }
        }.start()
    }
}
