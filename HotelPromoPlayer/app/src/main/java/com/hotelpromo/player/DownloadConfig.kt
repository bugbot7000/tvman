package com.hotelpromo.player

/**
 * Set this to a direct-download URL for a small TEXT FILE before
 * building. This is now the ONLY source of video for the app — there's
 * no manual file picker anymore, so this MUST be set for the app to
 * ever have anything to play.
 *
 * The text file's entire content should be nothing but the actual
 * video's direct-download URL (e.g. a Dropbox "?dl=1" link). To push a
 * new video to all boxes, just edit that text file's content — no
 * rebuilding, no reinstalling. The app re-checks it every time it's
 * opened, and on demand via the "Check for Update" button (reachable
 * by typing 2580 on the remote while a video is playing).
 *
 * IMPORTANT: both this pointer URL and the video URL it contains must
 * be true direct-download links, not "view" pages:
 *  - Dropbox: share the file, then change the URL's trailing "?dl=0" to
 *    "?dl=1". Reliable for any file size, and for a small text file.
 *  - GitHub Releases: attach the file as a release asset and use that
 *    asset's download URL. Also reliable for any size.
 *  - Google Drive is NOT recommended for the video: for files over
 *    ~100MB, Drive serves an HTML "can't scan for viruses" confirmation
 *    page instead of the file itself. (It's fine for the small text
 *    pointer file, which is nowhere near that size, but keep the actual
 *    video off Drive.)
 *
 * If a device is low on storage when a download is attempted, the app
 * shows that on screen and automatically retries the next time it's
 * opened (e.g. next reboot) — it does not loop retrying immediately.
 */
object DownloadConfig {
    const val VIDEO_DOWNLOAD_URL = "https://www.dropbox.com/scl/fi/gfstb6vvqkirevukn5f9r/video.txt?rlkey=e652shm1xgac0pcct0aor8574&st=9eqkes03&dl=1"
}
