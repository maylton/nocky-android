# Nocky Android Visual Refresh

This note tracks the Android visual-refresh stack used to make the Android app feel closer to the Nocky desktop family without copying the desktop layout or replacing the Android identity.

## Direction

The Android app already follows Material 3 / Material Expressive patterns. The refresh is intentionally conservative and should behave like a CSS pass:

- keep the existing screens, layout and navigation intact;
- preserve the Android-first interaction model;
- align shared visual language with desktop Nocky through color roles, rounded geometry, outlines, depth and subtle accent glow;
- prefer reusable theme primitives before touching individual screens.

## Stack

### 1. Theme baseline

Branch: `agent/nocky-android-visual-refresh-theme`

- Nocky-oriented violet default seed.
- App-owned dynamic color generation instead of wallpaper fallback for the default theme.
- Rounded Material 3 shape baseline.

### 2. Surface depth tokens

Branch: `agent/nocky-android-surface-depth`

- `withNockyVisualDepth` tunes Material 3 surface/container roles.
- Surface roles gain subtle accent tinting.
- Outline roles are strengthened for desktop-like card and menu borders.
- Pure-black mode keeps the black base while preserving container steps.
- Shared `surfaceContainer` and outline roles are strong enough for player chrome such as the mini-player shell.

### 3. Surface primitives and shell chrome

Branch: `agent/nocky-android-surface-primitives`

- `NockySurfaceSpec` records the shared visual contract for a surface.
- `NockySurfaceDefaults` defines island, card, raised card, menu, control and artwork defaults.
- Reusable modifiers provide bordered surfaces, artwork frames and soft accent glow.
- Native window background colors reduce white/light flashes before Compose renders.
- Widget fallback colors and the widget play button now use Nocky-oriented violet roles and a thin outline.
- Navigation bar and rail use a slightly raised container role plus a thin Nocky separator.
- Player chrome gets stronger shared surface and outline tokens.
- The new mini-player shell now uses a raised container, stronger outline, subtle horizontal accent glow and a slightly taller capsule.

### 4. Home media artwork pass

Branch: `agent/nocky-android-home-cards-polish`

- Media thumbnail corner radius moves from the old nearly-square 3 dp shape to a softer 12 dp radius.
- This gives Home rows, grid cards and related media artwork a clearer Nocky/Material surface feel without changing data loading, click behavior or layout structure.

## Application plan

Apply these primitives gradually and only after checking each screen locally:

1. app shell background and top app bar;
2. mini-player and bottom-sheet player surface;
3. Home cards and section containers;
4. bottom sheets and menus;
5. Settings rows and grouped panels;
6. artwork frames and selected controls.

Each step should remain a small PR and should not change playback, navigation, data loading, Nocky Connect, or screen structure.

## Local validation checklist

Run:

```bash
./gradlew :app:assembleDebug
```

Then manually check:

- light, dark and pure-black modes;
- dynamic artwork color changes while playing;
- Home, player, mini-player, Settings, menus and bottom sheets;
- mini-player shell/background/border separation;
- Home media artwork corners in grid rows, quick picks and recommendations;
- bottom navigation bar and landscape/tablet navigation rail;
- widget cards and play buttons in light/dark mode;
- small phones and landscape/tablet mode;
- scrolling performance and touch targets.
