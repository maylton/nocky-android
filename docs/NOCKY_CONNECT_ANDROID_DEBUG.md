# Nocky Connect Android debug workflow

This debug-only workflow exports and imports Nocky Connect JSON snapshots from the Android app.

It is intentionally manual and ADB-driven so the snapshot format can be tested before adding a user-facing UI or local-network discovery.

## Requirements

- Build variant: `fossDebug`
- App package: `com.metrolist.music.debug`
- A queue loaded in the player before exporting
- The app open before importing, so `MusicService` and the player are ready

## Build and install

```bash
./gradlew --no-configuration-cache :app:assembleFossDebug
./gradlew --no-configuration-cache :app:installFossDebug
```

## Export the current snapshot

Start playback or pause on the desired track, then run:

```bash
adb shell am start \
  -n com.metrolist.music.debug/com.metrolist.music.connect.NockyConnectDebugExportActivity
```

The activity connects to the app's Media3 session, reads the current queue, writes a Nocky Connect JSON snapshot, shows a toast, and closes immediately.

Snapshots are written to app-specific external storage:

```text
/sdcard/Android/data/com.metrolist.music.debug/files/nocky-connect/
```

List exported snapshots:

```bash
adb shell ls -la /sdcard/Android/data/com.metrolist.music.debug/files/nocky-connect
```

Pull a snapshot to the current directory:

```bash
adb pull \
  /sdcard/Android/data/com.metrolist.music.debug/files/nocky-connect/<snapshot-file>.json \
  ./nocky-android-snapshot.json
```

If `/sdcard/Android/data` is restricted on a device, use `run-as` as a fallback:

```bash
adb shell run-as com.metrolist.music.debug \
  ls files/nocky-connect

adb exec-out run-as com.metrolist.music.debug \
  cat files/nocky-connect/<snapshot-file>.json \
  > ./nocky-android-snapshot.json
```

## Import a desktop snapshot

Copy a desktop-exported snapshot into the Android debug storage folder:

```bash
adb shell mkdir -p /sdcard/Android/data/com.metrolist.music.debug/files/nocky-connect

adb push ./nocky-desktop-snapshot.json \
  /sdcard/Android/data/com.metrolist.music.debug/files/nocky-connect/nocky-desktop-snapshot.json
```

Open Nocky Debug on the phone so the service is running, then import by explicit file path:

```bash
adb shell am start \
  -n com.metrolist.music.debug/com.metrolist.music.connect.NockyConnectDebugImportActivity \
  --es snapshot_path /sdcard/Android/data/com.metrolist.music.debug/files/nocky-connect/nocky-desktop-snapshot.json
```

The activity binds to `MusicService`, validates the snapshot, rebuilds a paused `ListQueue`, applies repeat/shuffle/volume state and calls `playQueue(..., playWhenReady = false)`.

If `snapshot_path` is omitted, the activity imports the newest JSON file under:

```text
/sdcard/Android/data/com.metrolist.music.debug/files/nocky-connect/
```

## Current limitations

- This is a debug-only manual export/import path.
- The export assumes the current Media3 queue is YouTube Music compatible.
- Import requires the app/player service to be ready.
- Import restores paused and does not autoplay.
- It does not exchange account tokens, cookies, headers, stream URLs, or private account state.
