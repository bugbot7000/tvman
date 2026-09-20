# Hotel Promo Player

Minimal Android TV app that plays a promo video on fullscreen loop, muted.

## How it works

- There is **no manual file picker**. The video always comes from
  `DownloadConfig.VIDEO_DOWNLOAD_URL` — a small text file you host
  (e.g. on Dropbox) whose entire content is the real video's
  direct-download URL.
- On first launch, the app fetches that pointer file, downloads the
  video it points to, and starts looping it — muted, fullscreen.
- It re-checks the pointer file every time the app is opened (and via
  the on-screen "Check for Update" button, reachable by typing **2580**
  on the remote while the video is playing). If the pointer file now
  points to a different video, it downloads the new one and swaps it
  in. If it's unchanged, it just resumes playing — no re-download.
- **To push a new video to all boxes**, just edit the pointer text
  file's content to a new video URL. No rebuilding, no reinstalling,
  no walking around with a USB stick.
- Playback errors auto-retry a few times (in case of a momentary
  glitch) before falling back to the settings screen.
- **Any failure — no WiFi, bad pointer URL, broken download, not
  enough free storage — falls back to whatever video is already on
  disk**, if there is one. Nothing blank is ever shown; it just tries
  again automatically the next time the app is opened.
- At most one video file is ever kept on disk. A new download only
  replaces the old file after it's confirmed to have downloaded
  successfully, so a failed or interrupted download can never corrupt
  or wipe out a working video.
- There is no "launch on boot" feature built into this app — auto-start
  on power-up is handled by a separate autostart app of your choosing.

## Permissions used (and why)

- `INTERNET` / `ACCESS_NETWORK_STATE` — needed to check the pointer
  file and download the video.
- That's it. No storage-picker permissions (no manual picker anymore),
  no boot-related permissions, no "display over other apps."

## Setting the video source

Open `app/src/main/java/com/hotelpromo/player/DownloadConfig.kt` and
set `VIDEO_DOWNLOAD_URL` to a direct-download link for a small **text
file** (not the video itself) **before building**. That text file's
entire content should be nothing but the actual video's direct-download
URL.

Both links need to be true direct-download links, not "view" pages:
- **Dropbox** (recommended): share the file, then change the URL's
  trailing `?dl=0` to `?dl=1`. Reliable for any file size, and for the
  small text file.
- **GitHub Releases**: attach the file as a release asset and use that
  asset's download URL. Also reliable for any size.
- **Google Drive is not recommended for the video**: for files over
  ~100MB, Drive serves an HTML "can't scan for viruses" confirmation
  page instead of the file itself. (Fine for the small text pointer
  file, not fine for the actual video.)

If left blank, the app has no way to ever get a video — this field is
required for the app to function.

## 1. Build the APK

**Option A — let GitHub build it for you (no Android Studio, no Gradle, nothing to install):**

1. Create a free GitHub account if you don't have one, and create a new repository (Public keeps GitHub Actions free and simple).
2. Add all these files to the repo **at the repo root** — i.e. `app/`, `.github/`, `build.gradle`, `settings.gradle`, etc. should sit directly at the top level of the repo, not inside a subfolder. On GitHub's web upload, drag in the *contents* of this folder, not the folder itself. Note: GitHub's drag-and-drop upload silently skips hidden folders like `.github` — if that happens, use **Add file → Create new file** and type the path `.github/workflows/build.yml` to create it directly (typing the slashes creates the folders).
3. Click the **Actions** tab. The "Build APK" workflow should run automatically (or click it and hit "Run workflow"). Takes about 3–5 minutes.
4. Once it finishes (green checkmark), open that run, scroll to **Artifacts**, and download **app-debug-apk** — a zip containing `app-debug.apk`.

**Option B — build it yourself in Android Studio:**

1. Install [Android Studio](https://developer.android.com/studio).
2. File → Open → select this folder.
3. Let it sync (needs normal internet access).
4. Build → Build Bundle(s) / APK(s) → Build APK(s).
5. APK is at `app/build/outputs/apk/debug/app-debug.apk`.

(A debug APK installs and runs fine — no need for a signed release
build unless you plan to publish it somewhere.)

## 2. Copy to USB and install manually

1. Copy `app-debug.apk` onto a USB flash drive.
2. On each TV box: Settings → enable "Install from unknown sources"
   (or "Install unknown apps") for whichever file manager app you'll
   use to open the APK.
3. Plug the USB drive into the box, open it with a file manager app,
   and tap the APK to install.
4. Repeat per box — same APK for all of them.

## 3. On each TV, after installing

Open the app once. If `VIDEO_DOWNLOAD_URL` is set correctly, it'll
download the video automatically and start looping — nothing else to
configure. If you want it to launch automatically after power cycles,
set that up in whatever autostart app you're using separately; this
app does not handle that itself.
