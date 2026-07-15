# Disc Golf Eye

**"Where did my disc go?" — an instant-replay black box for disc golf, built from two spare phones.**

You throw, the disc disappears into rough, trees, or glare, and by the time you walk over you're not sure exactly where it landed. This app turns a spare phone into a hands-free camera you drop in your backpack at the tee — it records continuously and silently — while your main phone, connected over a local Wi-Fi hotspot, lets you pull up a replay of the last minute or two in seconds. No touching the backpack phone, no cloud, no waiting.

It's a solo/personal project, built to actually carry around a course.

## How it works

The app has one codebase and two roles, picked on launch: **Camera** (the phone in your backpack) and **Viewer** (the phone in your hand).

1. **The camera phone hosts its own network.** It runs Android's Wi-Fi hotspot, so there's no router, no internet, and no signal dependency in the middle of a forest — the two phones just talk to each other directly.
2. **Recording happens in rolling 1-minute chunks, not one long file.** An in-progress MP4 can't be safely read by another process — its metadata isn't finalized until the file is closed. Chunking means there's always a just-closed, fully-readable file available within seconds, instead of one growing file nobody else can touch. See [`RecordingManager.kt`](app/src/main/java/si/nicofi/discgolfeye/server/RecordingManager.kt).
3. **A "force flush" closes the current chunk on demand.** The moment you open the viewer or pull-to-refresh, it tells the camera phone to immediately finalize whatever it's mid-recording and start a new chunk — so a throw from 5 seconds ago is watchable now, instead of waiting up to a minute for the natural chunk boundary.
4. **The camera phone runs a real embedded HTTP server** (Ktor/Netty) with endpoints for status, video listing, streaming, starring, and deleting — see [`VideoServer.kt`](app/src/main/java/si/nicofi/discgolfeye/server/VideoServer.kt). It supports HTTP range requests (partial content), so the viewer can seek and scrub through a clip over Wi-Fi without downloading the whole thing first.
5. **A watchdog on the viewer phone polls the camera's health every 10 seconds** — battery level, battery temperature (used as a proxy for device heat, since Android blocks apps from reading raw CPU temperature), free storage, and recording state — and vibrates/toasts an alert if something's wrong, so you find out on hole 3 instead of discovering a dead camera on hole 18. See [`CameraWatchdog.kt`](app/src/main/java/si/nicofi/discgolfeye/client/CameraWatchdog.kt).
6. **Storage self-manages.** A configurable retention window (5 minutes to unlimited) auto-deletes old clips so the backpack phone never fills up mid-round — starred clips are exempt from the automatic cleanup.
7. **Screen pinning (Android kiosk mode) locks the camera phone into the app** once recording starts, so nothing inside a jostling backpack can accidentally back out of the app or kill the camera.

## Features

- **Role selection on launch**: "I am the Camera" (phone in the backpack) vs. "I am the Viewer" (phone in your hand).
- **Camera setup**: pick which lens to record from (with real field-of-view in degrees, computed from sensor size and focal length), video quality (480p–4K), frame rate (24/30/60), optional audio, and a recording retention limit.
- **Live health card on the viewer**: battery %, battery temperature, free/used storage, and REC/IDLE state, refreshed continuously while connected.
- **Video list with thumbnails**: auto-generated per clip, newest first, with relative timestamps ("12s ago" → "3min ago" → clock time) and pull-to-refresh that force-flushes the current chunk first.
- **Playback, star, delete, download**: ExoPlayer-based scrubbable playback straight off the camera phone; star clips to protect them from auto-cleanup, delete anything (including starred, with confirmation), or download a clip to the viewer's own gallery in the background.
- **Connection-loss and danger alerts**: low/critical battery, overheating, low/critical storage, and lost connection each trigger a distinct vibration pattern and toast, with a cooldown so they don't spam.
- **Kiosk mode + hotspot shortcuts**: one-tap access to Wi-Fi hotspot settings and Android screen pinning directly from the camera screen, with the stop/unpin controls disabled while actively recording and pinned (so you can't accidentally kill an active recording).

## Tech stack

- Kotlin, Jetpack Compose (Material 3), Navigation Compose
- Ktor: embedded Netty HTTP server on the camera phone + Ktor HTTP client on the viewer, both using `kotlinx.serialization` for JSON
- CameraX (`camera-video`, `camera2` interop for lens/FOV detection) for recording
- Media3 ExoPlayer for scrubbable local network video playback
- Coil for thumbnail loading
- Foreground services + partial wake lock so recording and downloads survive the screen turning off

## Privacy

Nothing leaves the two phones. There's no backend, no account, and no internet involved at any point — the camera phone *is* the network (its own Wi-Fi hotspot) and the only server, and footage only leaves it if you explicitly hit download on the viewer.

## Status

Personal project, actively carried around a real course. Not published to the Play Store — build and sideload it from source if you want to try it.

## Building

```
git clone <repo URL>
cd DiscGolfEye
./gradlew assembleDebug
```

Requires Android SDK with `compileSdk 36`, targets `minSdk 26` (Android 8.0+). You'll need two physical devices with cameras to actually test the Camera/Viewer flow — an emulator can't host a real Wi-Fi hotspot.

## License

No license file yet — all rights reserved by default. Ask before reusing.
