# Nocky Connect Android debug export

This debug-only workflow exports the currently loaded Android playback queue as a Nocky Connect JSON snapshot.

It is intentionally manual and ADB-driven so the snapshot format can be tested before adding a user-facing UI or local-network discovery.

## Requirements

- Build variant: `fossDebug`
- App package: `com.metrolist.music.debug`
- A queue loaded in the player before exporting

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

## Current limitations

- This is a debug-only manual export path.
- The export assumes the current Media3 queue is YouTube Music compatible.
- It does not yet import/restore snapshots.
- It does not exchange account tokens, cookies, headers, or stream URLs.
