# PulseFin

A native Android music client for [Jellyfin](https://jellyfin.org/) media servers.

## Features

- Browse your library: Home mix, Songs, Albums, Artists, Playlists
- Full playback control: queue reorder, play next / add to queue, lyrics view, queue survives
  app restarts
- Offline downloads, with a toggle to restrict them to Wi-Fi
- Scrobbling back to your Jellyfin server (play history / "continue listening")
- Material You dynamic color, dark/light theme
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
[docs/RELEASE_SIGNING.md](docs/RELEASE_SIGNING.md).

## Architecture

Multi-module: `:app` + `:core:{common,domain,designsystem,data,playback}`, [Koin](https://insert-koin.io/)
for dependency injection, the official [Jellyfin Kotlin SDK](https://github.com/jellyfin/jellyfin-sdk-kotlin),
and Media3/ExoPlayer for playback.

## License

GPL-3.0 — see [LICENSE](LICENSE).

## Disclaimer

This is an independent, unofficial client. Not affiliated with or endorsed by the Jellyfin project.
