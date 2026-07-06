# Nocky Connect Android integration

Nocky Connect is the compatibility layer that will let the Android fork and Nocky Desktop continue playback across devices without sharing private account state or raw audio streams.

This repository is still intentionally kept close to upstream Metrolist. Rebranding and broader app restructuring should happen later. The first integration work must stay isolated under `com.metrolist.music.connect` and should not change existing playback behaviour.

## Goal

The first goal is to export the Android player state into a portable JSON snapshot that Nocky Desktop can understand.

The snapshot contains:

- current playback state;
- current track index;
- current position in milliseconds;
- repeat and shuffle state;
- queue title;
- portable queue items;
- public YouTube Music identifiers and display metadata.

The snapshot must not contain:

- cookies;
- authorization headers;
- raw stream URLs;
- account tokens;
- library add/remove tokens;
- private upload/account identifiers;
- Android-only internal service state.

## Current implementation phase

This PR implements the Android export foundation only:

- serializable Nocky Connect protocol models;
- JSON codec;
- mapper from `PersistQueue` and `PersistPlayerState`;
- unit tests for queue export, JSON round-trip and privacy boundaries.

It does not implement networking, QR pairing, WebSocket sync, import/restore, UI actions, foreground services or desktop-side code.

## Portable snapshot shape

The top-level object is `PlaybackSessionSnapshot`.

```json
{
  "schema": "io.github.maylton.nocky.connect.PlaybackSessionSnapshot",
  "schema_version": 1,
  "session_id": "session-id",
  "revision": 1,
  "origin_device_id": "android-device-id",
  "updated_at_epoch_ms": 1783283000000,
  "updated_at_monotonic_ms": null,
  "source": "youtube",
  "playback": {
    "state": "paused",
    "position_ms": 92345,
    "duration_ms": 214000,
    "rate": 1.0,
    "volume": 0.82,
    "muted": false
  },
  "queue": {
    "title": "Liked songs",
    "current_index": 3,
    "repeat_mode": "all",
    "shuffle_enabled": false,
    "shuffle_seed": null,
    "items": []
  }
}
```

## Source handling

For now, Metrolist queues are treated as YouTube Music queues unless the existing queue type explicitly indicates a local album radio queue.

YouTube queue items use:

- `source = "youtube"`;
- `provider = "youtube_music"`;
- `playable_id = MediaMetadata.id`;
- `set_video_id = MediaMetadata.setVideoId` when available;
- public title, artist, album, duration and artwork metadata.

Local queue items are marked as best-effort and must not be assumed playable on another device unless a future local-library identity resolver confirms they can be resolved.

## Next steps

1. Add a development export entry point that writes the snapshot JSON to a file or share sheet.
2. Add a matching Android import path that can restore a received snapshot in paused state.
3. Implement the equivalent desktop models and importer/exporter.
4. Add local-network pairing only after manual JSON round trips work both ways.
