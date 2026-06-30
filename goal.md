# PulseFin: Project Mission & Roadmap

## 1. Project Essence
**PulseFin** is a premium, native Android music client for **Jellyfin**. It aims to replace clunky, web-view based media players with a high-performance, "Material 3 Expressive" experience that feels like a top-tier native app (Apple Music/Spotify) while remaining true to open-source roots (inspired by PixelPlay).

## 2. Core Vision & UX Philosophy
*   **Visual North Star:** Heavily inspired by the **PixelPlay** UI. It must utilize Material 3 "Expressive" design, featuring Dynamic Color (Monet) derived from album art.
*   **Slickness & Polish:** Prioritize smooth Shared Element Transitions (e.g., Album Art → Full Player) and 120fps fluid scrolling. Haptics and micro-animations are expected for a "premium" tactile feel.
*   **The "Set and Forget" UX:** Once the user logs into their Jellyfin server, the experience should be seamless and invisible. No complex server-switching or "hot-swapping" clutter.

## 3. Technical Stack (Strictly Android Native)
*   **Language:** Kotlin.
*   **UI Framework:** Jetpack Compose with Material 3 Expressive.
*   **Architecture:** MVI (Model-View-Intent) for robust state management.
*   **DI Framework:** Koin (chosen for simplicity and ease of setup).
*   **Networking:** Ktor (modern, asynchronous client).
*   **Image Loading:** Coil (integrated with Ktor for high-performance art rendering).
*   **Playback Engine:** Android Media3 (ExoPlayer) + MediaSessionService.
*   **Local Layer:** Room DB as the Single Source of Truth.
*   **Compatibility:** Min SDK 31 (Android 12+) to leverage native Dynamic Color and modern system APIs.

## 4. Feature Roadmap

### v1: The "Slick Stable" Release (MVP)
*   **Direct Play Streaming:** High-fidelity playback of Jellyfin library over the network.
*   **Global Search:** Instant, unified search across Artists, Albums, and Songs.
*   **Material 3 UI:** Dark-mode focused "Expressive" design.
*   **Set-and-Forget Login:** Standard authentication (Username/Password/API Key).

### v2: The "Power User" Update
*   **Offline Downloads:** Encrypted local storage of media for offline playback.
*   **Local Lyrics:** Support for user-provided `.lrc` or embedded lyric files.
*   **Metadata Enrichment:** Initial scraping of additional song details.

### v3: The "Intelligence" Layer
*   **Scraped Lyrics:** Automated scraping and caching of lyrics from web sources.
*   **Performance Maturity:** Server-side transcoding for data-saving modes.

## 5. The "Pro" Vision & Business Model
*   **Development Split:** 
    *   **PulseFin Base:** Open-source, high-performance Jellyfin client.
    *   **PulseFin Pro:** Closed-source project built on the core, adding advanced features, Light Mode support, and a payment model.
*   **Pro Features (Future):**
    *   **Library Analysis:** Analyze listening patterns to create smart mixes.
    *   **AI Recommendations:** "Spotify-like" discovery and natural language search.
    *   **Hybrid Data:** Merging Jellyfin metadata with MusicBrainz/Last.fm.
    *   **Advanced Auth:** Support for LDAP/SSO.

## 6. Constraints & Rules
*   **Visuals:** Dark-mode first priority for the Base version.
*   **Performance:** UI thread must never block. 60/120fps budget.
*   **Privacy:** No secret exposure. All Pro AI features must be delegated to a secure backend (per `Rule_Book.md`).


