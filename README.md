# Next Player Clone (Android Studio project)

A functional local-video player app built with **Media3 / ExoPlayer**, similar in spirit to
"Next Player" — browse videos on the device, then play them with gesture controls.

## Features
- Scans the device (MediaStore) for all videos and lists them with thumbnail, name, size, resolution, duration.
- Runtime permission handling (`READ_MEDIA_VIDEO` on Android 13+, `READ_EXTERNAL_STORAGE` below).
- Full-screen ExoPlayer-based player:
  - Tap to show/hide controls, double-tap left/right to rewind/forward 10s.
  - **Swipe gestures**: left-half vertical swipe = brightness, right-half vertical swipe = volume, horizontal swipe = seek preview.
  - Lock button to lock the screen during playback.
  - Next / previous video within the folder's playlist.
  - Playback speed selector (0.5x–2x).
  - Subtitle/text track selector (picks up embedded or sidecar subtitle tracks Media3 exposes).
  - Auto-advances to the next video when one finishes.
- Handles being launched as the default video viewer for `video/*` intents from other apps (e.g. a file manager).

## How to open in Android Studio
1. Unzip this project.
2. Open Android Studio → **File > Open** → select the unzipped `NextPlayerClone` folder.
3. Let Gradle sync (it will download the Media3/ExoPlayer 1.4.1 dependencies — needs internet).
4. Run on a device/emulator with API 24+ (Android 7.0+).
5. On first launch, grant the video permission when prompted.

## Project structure
```
app/src/main/java/com/example/nextplayerclone/
  MainActivity.kt      – video library screen (MediaStore query, RecyclerView)
  PlayerActivity.kt     – full-screen player with gestures, controls, subtitles, speed
  VideoAdapter.kt        – RecyclerView adapter for the list
  VideoItem.kt            – data model
  ThumbnailLoader.kt       – lightweight background thumbnail extraction/cache
app/src/main/res/layout/
  activity_main.xml     – library screen
  activity_player.xml   – player screen (controls + gesture overlay)
  item_video.xml          – library row
```

## Extending it further (ideas)
- Add a folder/tab view (Next Player groups by folder).
- Add playback resume (store last position per video in SharedPreferences/Room).
- Add a `MediaSessionService` (Media3 `media3-session`, already included) for notification controls & Bluetooth/headset buttons.
- Add external subtitle file (.srt/.vtt) picker via `Intent.ACTION_OPEN_DOCUMENT`.
- Add PiP (picture-in-picture) support via `enterPictureInPictureMode()`.
- Add a settings screen (default speed, default subtitle language, theme).

## Notes
- Thumbnails are generated on-device via `MediaMetadataRetriever` and memory-cached; for large libraries you may want to swap in Glide + a disk cache.
- The manifest registers `PlayerActivity` to handle `ACTION_VIEW` on `video/*`, so this app will show up as an option to open video files from other apps too.
