# Hotel Promo Player

Minimal Android TV app.

- Opens to a settings screen with a "Select Video File" button and a
  "Launch on boot" checkbox.
- Once a video is picked, it jumps straight into a fullscreen, muted,
  looping player and never shows the settings screen again.
- The settings screen only reappears if the video file becomes
  unreadable, or if you type **2580** on the remote's number pad
  while the video is playing.

## First-install auto-download (optional)

Open `app/src/main/java/com/hotelpromo/player/DownloadConfig.kt` and set
`VIDEO_DOWNLOAD_URL` to a direct-download link **before building**. Every
fresh install will then download that video automatically on first
launch, with a progress bar, and go straight into looping it — no need
to manually pick a file on all 45 boxes. Leave it blank to disable this
and keep the manual "Select File" flow only.

**This must be a true direct-download link, not a "view/share" page:**
- **Dropbox** (recommended, works for any file size): share the file,
  then change the URL's trailing `?dl=0` to `?dl=1`.
- **GitHub Releases**: attach the mp4 as a release asset and use that
  asset's download URL. Also reliable for any size, up to 2GB.
- **Google Drive is not recommended**: for files over ~100MB (which a
  real promo video almost certainly is), Drive serves an HTML "can't
  scan for viruses" confirmation page instead of the file, so the app
  would download that page, not your video.

If the download ever fails (bad URL, no WiFi on first boot, etc.), the
app shows the error and falls back to the normal settings screen with
a "Retry Download" button, plus the usual manual file picker.

## Permissions used (and why)

- `RECEIVE_BOOT_COMPLETED` — needed for the boot checkbox to work.
- `READ_MEDIA_VIDEO` (Android 13+) / `READ_EXTERNAL_STORAGE` (below 13,
  capped with `maxSdkVersion="32"`) — needed to read a manually picked
  video file.
- `INTERNET` / `ACCESS_NETWORK_STATE` — needed only for the optional
  first-install auto-download described above.
- That's it. "Display over other apps" (`SYSTEM_ALERT_WINDOW`) is
  not included — this app doesn't draw over other apps or block the
  Home button, so it wouldn't do anything here. If you later want
  guests unable to leave the app via Home (true kiosk lock), that
  needs a different mechanism (setting the box as Device Owner via
  `adb`), not this permission.

## 1. Build the APK

**Option A — let GitHub build it for you (no Android Studio, no Gradle, nothing to install):**

1. Create a free GitHub account if you don't have one, and create a new repository (Settings can be "Public" — that keeps GitHub Actions free and simple).
2. On the repo page, click **Add file → Upload files**, then drag in the whole unzipped `HotelPromoPlayer` folder contents (everything inside it — `app/`, `.github/`, `build.gradle`, etc.). Commit the upload.
3. Click the **Actions** tab. A workflow called "Build APK" should start running automatically (if it doesn't, click it and hit "Run workflow"). It takes about 3–5 minutes.
4. Once it finishes (green checkmark), click into that run, scroll to **Artifacts**, and download **app-debug-apk** — it's a zip containing `app-debug.apk`.
5. That's your APK. Go to step 2 below.

This works because GitHub's own servers have full internet access to Google's Android build tools, which this sandbox doesn't — so it can do the compiling that I can't do directly for you.

**Option B — build it yourself in Android Studio**, if you'd rather not use GitHub:

1. Install [Android Studio](https://developer.android.com/studio) if
   you don't have it.
2. File → Open → select the unzipped `HotelPromoPlayer` folder.
3. Let it sync (downloads Gradle + dependencies automatically — needs
   normal internet access).
4. Build → Build Bundle(s) / APK(s) → Build APK(s).
5. The APK will be at:
   `app/build/outputs/apk/debug/app-debug.apk`

(A debug APK installs and runs fine for this — no need for a signed
release build unless you plan to publish it somewhere.)

## 2. Copy to USB and install manually

1. Copy `app-debug.apk` onto a USB flash drive.
2. On each TV box: Settings → enable "Install from unknown sources"
   (or "Install unknown apps") for whichever file manager app you'll
   use to open the APK — this setting exists on basically every
   Android TV box but the exact menu wording varies by brand.
3. Plug the USB drive into the box, open it with a file manager app
   (most cheap boxes have one preinstalled; if not, install one from
   the Play Store/App Store on the box first), and tap the APK to
   install.
4. Repeat per box. Since it's the same APK for all 45, you can leave
   it on the same USB drive and just walk it round.

## 3. On each TV, after installing

Open the app once, tap "Select Video File", pick the promo video, and
tick "Launch on boot" if you want it to survive power cycles. After
that it just loops, muted, forever — the settings screen won't show
again unless the file goes missing or you type 2580 on the remote.
