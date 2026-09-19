package com.hotelpromo.player

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

/**
 * Fullscreen, muted, looping video playback. This is what's on screen
 * almost all the time. The settings screen is only reachable from here
 * by typing 2580 on the remote's number pad, or automatically if the
 * video can't be read after several retries.
 */
class PlayerActivity : AppCompatActivity() {

    private lateinit var videoView: VideoView

    // Rolling buffer of the last few digits typed on the remote.
    private val codeBuffer = StringBuilder()
    private val unlockCode = "2580"

    // A playback error is sometimes a one-off glitch (e.g. the file was
    // being written to, or a momentary hiccup reading storage) rather
    // than the file actually being gone. Retry a few times before
    // giving up and sending someone to the settings screen.
    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0
    private val maxRetries = 3
    private val retryDelayMs = 2000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep the screen on while playing unattended.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_player)
        videoView = findViewById(R.id.videoView)
        videoView.setMediaController(null)

        playVideo()
    }

    private fun playVideo() {
        val uri = Prefs.getVideoUri(this)
        if (uri == null) {
            returnToSettings()
            return
        }

        try {
            videoView.setVideoURI(uri)
        } catch (e: Exception) {
            handleFailure()
            return
        }

        videoView.setOnPreparedListener { mp ->
            retryCount = 0 // played successfully, reset the counter
            mp.isLooping = true
            mp.setVolume(0f, 0f) // muted
            videoView.start()
        }

        videoView.setOnErrorListener { _, _, _ ->
            handleFailure()
            true
        }
    }

    private fun handleFailure() {
        if (retryCount < maxRetries) {
            retryCount++
            handler.postDelayed({ playVideo() }, retryDelayMs)
        } else {
            retryCount = 0
            // File missing, corrupted, or permission revoked after
            // repeated attempts — fall back to settings so a new file
            // can be picked (or re-downloaded).
            returnToSettings()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private fun returnToSettings() {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            putExtra(SettingsActivity.EXTRA_FORCE_SHOW, true)
        }
        startActivity(intent)
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val digit = keyCodeToDigit(keyCode)
        if (digit != null) {
            codeBuffer.append(digit)
            if (codeBuffer.length > unlockCode.length) {
                codeBuffer.delete(0, codeBuffer.length - unlockCode.length)
            }
            if (codeBuffer.toString() == unlockCode) {
                codeBuffer.clear()
                returnToSettings()
                return true
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun keyCodeToDigit(keyCode: Int): Char? {
        return when (keyCode) {
            KeyEvent.KEYCODE_0 -> '0'
            KeyEvent.KEYCODE_1 -> '1'
            KeyEvent.KEYCODE_2 -> '2'
            KeyEvent.KEYCODE_3 -> '3'
            KeyEvent.KEYCODE_4 -> '4'
            KeyEvent.KEYCODE_5 -> '5'
            KeyEvent.KEYCODE_6 -> '6'
            KeyEvent.KEYCODE_7 -> '7'
            KeyEvent.KEYCODE_8 -> '8'
            KeyEvent.KEYCODE_9 -> '9'
            else -> null
        }
    }
}
