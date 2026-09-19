package com.hotelpromo.player

/**
 * Set this to a direct-download URL for your promo video before building,
 * and every fresh install will download it automatically on first launch.
 *
 * Leave it blank to disable auto-download entirely — the app will just
 * show the normal "Select Video File" screen, same as before.
 *
 * IMPORTANT: this must be a true direct-download link, not a "view" page.
 *  - Dropbox: share the file, then change the URL's trailing "?dl=0" to
 *    "?dl=1". Reliable for any file size.
 *  - GitHub Releases: attach the mp4 as a release asset and use that
 *    asset's download URL. Also reliable for any file size (up to 2GB).
 *  - Google Drive is NOT recommended here: for files over ~100MB
 *    (basically any real video) Drive serves an HTML "can't scan for
 *    viruses" confirmation page instead of the file itself, so the app
 *    would download that page, not your video.
 */
object DownloadConfig {
    const val VIDEO_DOWNLOAD_URL = "https://www.dropbox.com/scl/fi/saijo7srrgop5lx8g11jx/10-Second-Timer-HD.mp4?rlkey=u15f98g5kh0r9qjudcw66ftxs&st=evgi7zhq&dl=1"
}
