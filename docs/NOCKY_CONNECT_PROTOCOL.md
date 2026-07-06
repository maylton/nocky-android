# Nocky Connect Android integration

Nocky Connect is the compatibility layer that will let the Android fork and Nocky Desktop continue playback across devices without sharing private account state or raw audio streams.

This repository is still intentionally kept close to upstream Metrolist. Rebranding and broader app restructuring should happen later. The first integration work must stay isolated under `com.metrolist.music.connect` and should not change existing playback behaviour.

## Goal

The first goal is to convert the Android player state to and from a portable JSON snapshot that Nocky Desktop can understand.

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

This PR implements the Android snapshot foundation only:

- serializable Nocky Connect protocol models;
- JSON codec;
- export mapper from `PersistQueue` and `PersistPlayerState` to `PlaybackSessionSnapshot`;
- restore mapper from `PlaybackSessionSnapshot` back to `PersistQueue` and paused `PersistPlayerState`;
- device descriptor model for future LAN discovery and capability negotiation;
- gateway for export/import validation and restore planning;
- local private file store for manual/dev snapshot round trips;
- local device identity helper backed by app-private `SharedPreferences`;
- `MusicService` bridge helpers in a separate file, without modifying `MusicService.kt`;
- shared v1 JSON fixture compatibility tests for snapshots and device descriptors;
- unit tests for queue export, JSON round-trip, local best-effort identity, paused restore, descriptor validation, gateway flow and file storage.

It does not implement networking, WebSocket sync, UI actions, foreground services, automatic playback handoff or desktop-side code. The service bridge is opt-in: nothing calls it yet, so the existing Metrolist playback flow remains unchanged.

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

## Restore behavior

Restoring a snapshot is intentionally conservative:

- the queue is rebuilt from portable metadata;
- the current index is clamped to the available item range;
- position, repeat and shuffle state are preserved;
- the resulting player state is paused even when the remote snapshot was playing.

The actual one-tap handoff flow can later decide when to start playback after the destination resolves the current stream and the source device acknowledges the transfer.

## LAN-first discovery direction

QR pairing is not planned as the default flow. The intended first real handoff flow is same-network discovery:

1. both apps advertise a `NockyConnectDeviceDescriptor` on the local network;
2. each app shows compatible devices discovered on the same LAN;
3. the receiving device requires an explicit accept/deny confirmation before a restore happens;
4. YouTube Music account state is not exchanged or inspected by Nocky Connect.

Using the same Google/YouTube Music account can make the handoff more likely to resolve the same songs, but it should not be used as a pairing secret. Nocky Connect should transfer public playable IDs and metadata, not account cookies, tokens or headers.

## Gateway, file store and service bridge

`NockyConnectGateway` is the future service/UI boundary. It can:

- export a `PlaybackSessionSnapshot` from existing persisted queue/player state;
- encode that snapshot as JSON;
- decode received JSON;
- reject unsupported schema names or schema versions;
- produce a restore plan containing the rebuilt `PersistQueue` and paused `PersistPlayerState`.

`NockyConnectFileStore` stores JSON snapshots in an app-private `nocky-connect` directory under the provided base directory. This is for manual and development round trips before LAN pairing exists.

`NockyConnectMusicServiceBridge` exposes extension helpers for `MusicService`:

- export the current player queue as a snapshot or JSON;
- write the current snapshot to the private file store;
- prepare a restore plan from JSON;
- restore a snapshot as a paused `ListQueue`;
- restore the latest saved snapshot file.

`NockyConnectDeviceIdentity` creates a random app-local device ID and stores it in app-private preferences. It is intentionally not based on Android hardware IDs.

## Compatibility fixtures

`app/src/test/resources/nocky-connect-snapshot-v1.json` is a shared protocol fixture. `NockyConnectCompatibilityFixtureTest` decodes it and prepares a paused restore plan to verify Android remains compatible with the desktop-side v1 snapshot contract.

`app/src/test/resources/nocky-connect-device-descriptor-v1.json` is a shared descriptor fixture. `NockyConnectDeviceDescriptorTest` decodes it to verify Android remains compatible with the future LAN discovery descriptor contract.

## Next steps

1. Add a development export UI/action that calls the `MusicService` bridge and writes/shares JSON.
2. Add a development import UI/action that accepts pasted/shared JSON and restores a paused queue.
3. Wire the desktop gateway to real playback/session state.
4. Add same-network discovery and explicit accept/deny confirmation after manual JSON round trips work both ways.
