package com.hotelpromo.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Launches the player straight after the TV box boots, but only if the
 * user ticked "launch on boot" in settings AND a video is already set.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        if (!Prefs.isLaunchOnBootEnabled(context)) return
        if (Prefs.getVideoUri(context) == null) return

        val launchIntent = Intent(context, PlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(launchIntent)
    }
}
