package com.hotelpromo.player

import android.content.Context
import android.net.Uri

object Prefs {
    private const val PREFS_NAME = "hotel_promo_prefs"
    private const val KEY_VIDEO_URI = "video_uri"
    private const val KEY_LAST_RESOLVED_VIDEO_URL = "last_resolved_video_url"

    fun getVideoUri(context: Context): Uri? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_VIDEO_URI, null) ?: return null
        return Uri.parse(raw)
    }

    fun setVideoUri(context: Context, uri: Uri?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_VIDEO_URI, uri?.toString())
            .apply()
    }

    // The actual video URL (resolved from the pointer text file) that was
    // used for the video currently on disk. Compared against a fresh
    // pointer-file fetch to detect when the hotel owner has pointed the
    // text file at a different video.
    fun getLastResolvedVideoUrl(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_RESOLVED_VIDEO_URL, null)
    }

    fun setLastResolvedVideoUrl(context: Context, url: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_RESOLVED_VIDEO_URL, url)
            .apply()
    }
}
