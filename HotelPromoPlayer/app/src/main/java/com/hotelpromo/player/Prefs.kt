package com.hotelpromo.player

import android.content.Context
import android.net.Uri

object Prefs {
    private const val PREFS_NAME = "hotel_promo_prefs"
    private const val KEY_VIDEO_URI = "video_uri"
    private const val KEY_LAUNCH_ON_BOOT = "launch_on_boot"

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

    fun isLaunchOnBootEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LAUNCH_ON_BOOT, false)
    }

    fun setLaunchOnBootEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LAUNCH_ON_BOOT, enabled)
            .apply()
    }
}
