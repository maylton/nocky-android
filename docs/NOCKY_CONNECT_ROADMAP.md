# Nocky Connect Roadmap

This document records the current state of Nocky Connect and the product direction change from a manual `Send / Receive` diagnostic flow to a Spotify Connect-like device picker.

Nocky Connect is not an implementation of Spotify Connect and must not depend on proprietary Spotify protocols. The intended user experience is similar: devices advertise local presence, the app shows available devices, and the user can move the current playback session safely between them.

## Product direction

The main Nocky Connect surface should be a live `Available devices` surface instead of two separate `Send` and `Receive` actions.

Target experience:

```text
Nocky Connect

This device
✓ Nocky Android

Available on your network
💻 Nocky Desktop
   Linux desktop · available now

Troubleshooting
No devices? Check same network and firewall UDP/TCP rules.
```

Selecting a device should start a safe local handoff flow:

1. discover LAN devices;
2. show devices in a list;
3. user selects a target device;
4. sender offers a playback-session snapshot;
5. receiver shows accept/decline, at least until the device is trusted;
6. accepted snapshot is imported paused by default;
7. receiver can show a clear `Play` action.

## Current implementation status

### Shared protocol pieces

- Playback session snapshot schema version 1 exists and is used by both desktop and Android.
- Device descriptor schema version 1 exists and identifies device id, device name, platform, app name, app version, supported features, and optional handoff endpoint.
- LAN discovery schema version 1 exists with:
  - UDP port `34987`;
  - magic `NOCKY_CONNECT_DISCOVERY_V1`;
  - message kinds `hello` and `announce`.
- Handoff message schema version 1 exists with:
  - `handoff_offer`;
  - `handoff_accept`;
  - `handoff_decline`;
  - `handoff_result`.
- Local HTTP handoff currently uses TCP port `35187` and paths:
  - `/nocky-connect/handoff`;
  - `/nocky-connect/snapshot`.
- Discovery and handoff packets intentionally do not contain account tokens, cookies, request headers, stream URLs, or other secrets.

### Validated end-to-end flows

Validated:

- Desktop -> Android:
  - Desktop discovers Android;
  - Desktop sends handoff offer;
  - Android accepts;
  - Desktop transfers snapshot;
  - Android saves pending restore as fallback;
  - Android applies restore automatically;
  - Android player switches to the desktop queue paused.
- Android -> Desktop:
  - Android discovers Desktop;
  - Android sends handoff offer;
  - Desktop accepts;
  - Android transfers snapshot;
  - Desktop applies restore paused.
- Android device-picker presence states:
  - Android compile/install passed after the presence-state update;
  - Desktop rows show `available now` when freshly discovered;
  - cached Desktop rows show `recently seen` with a relative `last seen` time;
  - Android -> Desktop handoff remains valid after the UI/cache-state update.

Known implementation notes:

- Android snapshot export must read ExoPlayer state on the main thread.
- Android handoff receiver timeout is intentionally longer than the short presence window, so Desktop -> Android can still complete during the advertised window.
- Pending restore remains an internal fallback for received desktop snapshots.
- Restored queues can still have incomplete artwork/metadata. Metadata hydration is a later polish phase.

### Current Android product surface

The Android player entry point now opens a device-picker style bottom-sheet surface.

Implemented on Android:

- player main action opens Nocky Connect directly;
- duplicate three-dot menu entry has been removed;
- the surface shows `This device` and `Available devices`;
- selecting a desktop row sends the current Android playback snapshot to Desktop;
- opening the surface starts a cancellable presence session tied to the picker lifecycle;
- the picker presence session keeps Android discoverable while the sheet is open;
- the picker presence session keeps a short handoff receiver window refreshed while active;
- `Scan again` refreshes the device list without owning Android presence lifecycle;
- discovered devices are cached in memory for 5 minutes;
- device rows distinguish `available now` from `recently seen`;
- recently seen device rows show a relative `last seen` age;
- failed scans keep recently seen devices visible instead of clearing the list;
- the old manual `Make this device available for Desktop` action was removed from the UI;
- old diagnostic `Send / Receive` branching was removed from the Android player surface;
- the Android HTTP receiver remains available behind the automatic presence flow for Desktop -> Android handoff;
- Nocky Connect picker/status/toast copy is now in Android string resources for English and Brazilian Portuguese.

Still temporary on Android:

- presence is currently tied to the foreground picker surface, not a full app/player lifecycle service;
- receiver confirmation is automatic; explicit accept/decline UI is still a future trust/polish phase;
- device cache is in-memory only and resets when the app process dies;
- lower-level protocol exception messages may still need release copy review.

### Current Desktop product surface

Desktop has a device-picker popover with cache and periodic refresh while open.

Implemented on Desktop:

- desktop caches discovered devices for 5 minutes;
- opening the popover renders cached devices immediately;
- Desktop starts its handoff receiver when the Nocky Connect popover is opened;
- Desktop scans once when the popover opens;
- while the popover remains open, Desktop refreshes discovery periodically;
- scan overlap is guarded to avoid concurrent LAN discovery workers;
- selecting an Android device sends the current desktop playback snapshot to Android.

Still temporary on Desktop:

- presence/listener is not started at app startup yet;
- the receiver is still tied to opening the Nocky Connect surface;
- the surface is still a popover, not a full internal page;
- verbose diagnostic logs should be removed or gated before release.

## Important environment findings

On the tested Linux desktop, UFW was active with default input policy `drop`. UDP broadcast packets from Android were visible in `tcpdump`, but were not delivered to Nocky or to a minimal Python UDP listener until UDP port `34987` was allowed.

Permanent discovery rule used on the desktop:

```bash
sudo ufw allow in proto udp from 192.168.0.0/24 to any port 34987 comment 'Nocky Connect LAN discovery'
sudo ufw reload
```

The reverse Android -> Desktop handoff also requires local TCP access to the desktop receiver:

```bash
sudo ufw allow in proto tcp from 192.168.0.0/24 to any port 35187 comment 'Nocky Connect handoff HTTP'
sudo ufw reload
```

Troubleshooting commands used:

```bash
sudo tcpdump -ni any udp port 34987
sudo tcpdump -ni any tcp port 35187
systemctl is-active ufw
systemctl is-active firewalld
systemctl is-active nftables
sudo nft list ruleset | grep -niE 'hook input|policy|drop|reject|34987|35187'
sudo iptables-save | grep -niE '34987|35187|DROP|REJECT'
```

This firewall finding should be reflected in user-facing troubleshooting copy before release.

## What should be reused

Keep and build on:

- snapshot schema and mappers;
- descriptor schema and persistent device identity;
- discovery envelope format;
- UDP discovery transport;
- HTTP handoff offer/snapshot transport;
- firewall troubleshooting note;
- paused-by-default restore semantics;
- strict no-secrets rule;
- pending restore store as an internal Android fallback;
- Android device picker surface;
- Android picker-bound presence session;
- Android and Desktop recent-device caches;
- `available now` / `recently seen` row semantics;
- localized Android Nocky Connect string resources.

Do not reintroduce the old manual `Send / Receive` Android UI as the primary flow. It has been replaced by the device picker.

## What should change next

### UX

Continue evolving the device picker with:

- live device list semantics;
- clearer device row states:
  - scanning;
  - available now;
  - recently seen;
  - connecting;
  - waiting for confirmation;
  - failed / firewall hint;
- current device section;
- troubleshooting footer;
- localized strings.

Desktop should eventually move from a popover to an internal Nocky Connect page/surface similar to the Queue surface.

### Always-on discovery / presence

Future product behavior should not require opening the Nocky Connect surface to make a device visible.

Target behavior:

- Desktop starts local presence/listener when the app is running, not only when the Nocky Connect popover opens.
- Android starts local presence/listener when the app/player lifecycle allows it.
- Each side maintains a small live cache of recently seen devices.
- Opening the Nocky Connect surface renders the already-known device list and can trigger a manual refresh.
- Discovery should be rate-limited and lifecycle-aware, especially on Android, to avoid unnecessary battery/network usage.
- Receiver HTTP should become a background singleton with clear lifecycle ownership.

### Handoff confirmation and trust

Current restore acceptance is automatic while the receiver is active. That is acceptable for the local prototype, but the release flow should eventually add explicit confirmation or trusted-device memory:

- first handoff from a new device should show accept/decline;
- trusted devices may skip confirmation;
- declined handoffs should return a `handoff_decline` envelope;
- UI should explain that playback will restore paused;
- device trust should be stored locally only.

### Metadata and artwork

Snapshot transfer should continue to avoid raw stream URLs and account secrets. Missing polish should be solved by better portable metadata and local hydration:

- include enough safe metadata for good receiving UI;
- hydrate artwork/title/artist through each platform's own resolver when possible;
- keep restore paused by default.

## Validation checklist

Before considering the device-picker milestone complete:

- Android compile passes.
- Desktop `cargo check --all-targets` passes.
- Desktop discovers Android from the popover.
- Android discovers Desktop from the player Nocky Connect surface.
- Desktop -> Android transfer restores the queue paused.
- Android -> Desktop transfer restores the queue paused.
- Closing and reopening the picker preserves recently seen devices.
- Firewall-disabled and firewall-blocked states produce understandable troubleshooting copy.
