# PulseFin

A native Android music client for [Jellyfin](https://jellyfin.org/) media servers, built with
Jetpack Compose and Material 3 Expressive.

## Design

PulseFin leans into Material 3's newer "Expressive" design language rather than a stock/default
Material look:

- **Expressive shapes** — Compose's `MaterialShapes` (e.g. a 9-sided cookie shape on the play
  button) alongside custom squircle and rounded-hero shapes for artwork and cards.
- **Per-track dynamic color** — the Now Playing screen extracts a color seed from the currently
  playing album art (via `Palette` + Material's color-utilities) and builds a full tonal scheme
  from it live, so the player re-themes itself to whatever's playing.
- **Material You elsewhere** — wallpaper-based dynamic color for the rest of the app, with a
  dark/light toggle in Settings.
- **Shared-element transitions** — album art morphs between a list row and the full player via
  Compose's `SharedTransitionLayout`, rather than a hard cut.
- **Haptic feedback** on key interactions (playback controls, favoriting, drag-to-reorder).

## Features

- Browse your library: Home mix, Songs, Albums, Artists, Playlists
- Full playback control: queue reorder, play next / add to queue, lyrics view, queue survives
  app restarts
- Server-backed playlists (create/rename/delete/reorder — round-trips with the Jellyfin web UI
  and other clients, not a local-only copy)
- Offline downloads, with a toggle to restrict them to Wi-Fi, and a Settings view of how much
  space they're using
- Scrobbling back to your Jellyfin server (play history / "continue listening")
- No telemetry, crash reporting, or analytics of any kind — nothing leaves your device except
  calls to your own Jellyfin server

## Requirements

- A running Jellyfin server you control (server URL + login credentials)
- Android 12 (API 31) or newer

## Building

Requires JDK 21 (Android Studio's bundled JBR is the simplest source — point `JAVA_HOME` at it):

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"  # macOS example
./gradlew :app:assembleDebug
```

Debug builds install with no further setup. For a signed release build, see
[docs/RELEASE_SIGNING.md](docs/RELEASE_SIGNING.md); tagging a `vX.Y.Z` release also builds and
publishes a signed APK automatically via GitHub Actions.

## Architecture

Multi-module Gradle project:

| Module | Role |
| --- | --- |
| `:app` | UI (Jetpack Compose), navigation, screen-level ViewModels |
| `:core:domain` | Repository interfaces and domain models, no Android dependency |
| `:core:data` | Jellyfin API access ([official Kotlin SDK](https://github.com/jellyfin/jellyfin-sdk-kotlin)), Room-backed local mirror, settings/session storage |
| `:core:playback` | Media3/ExoPlayer playback engine, download manager, queue persistence |
| `:core:designsystem` | Theme, shapes, typography |
| `:core:common` | Shared utilities |

Dependency injection via [Koin](https://insert-koin.io/). The server is treated as the source of
truth throughout — local Room storage is a mirror/cache, not a parallel data model.

## License

GPL-3.0 — see [LICENSE](LICENSE).

## Disclaimer

This is an independent, unofficial client. Not affiliated with or endorsed by the Jellyfin project.
