# PulseFin: Project Mission & Roadmap

## 1. Project Essence
**PulseFin** is a premium, native Android music client for **Jellyfin**. It aims to replace clunky, web-view based media players with a high-performance, "Material 3 Expressive" experience that feels like a top-tier native app (Apple Music/Spotify) while remaining true to open-source roots (inspired by PixelPlay).

## 2. Core Vision & UX Philosophy
*   **Visual North Star:** Heavily inspired by the **PixelPlay** UI. It must utilize Material 3 "Expressive" design, featuring Dynamic Color (Monet) derived from album art.
*   **Slickness & Polish:** Prioritize smooth Shared Element Transitions (e.g., Album Art → Full Player) and 120fps fluid scrolling. Haptics and micro-animations are expected for a "premium" tactile feel.
*   **The "Set and Forget" UX:** Once the user logs into their Jellyfin server, the experience should be seamless and invisible. No complex server-switching or "hot-swapping" clutter.

## 3. Technical Stack (Strictly Android Native)
*   **Language:** Kotlin.
*   **Package Name:** `com.pulsefin.app`
*   **UI Framework:** Jetpack Compose with Material 3 Expressive.
*   **Navigation:** Jetpack Navigation Component.
*   **Architecture:** MVI (Model-View-Intent) with a custom lightweight implementation.
*   **DI Framework:** Koin (chosen for simplicity and ease of setup).
*   **Networking:** Ktor (modern, asynchronous client).
*   **Image Loading:** Coil (integrated with Ktor for high-performance art rendering).
*   **Playback Engine:** Android Media3 (ExoPlayer) + MediaSessionService.
*   **Local Layer:** Room DB as the Single Source of Truth.
*   **Compatibility:** Min SDK 31 (Android 12+) to leverage native Dynamic Color and modern system APIs.
*   **Programming Style:** Modularized programming is mandatory. The core logic (Playback, Data, UI Components) must be decoupled from the app module to ensure an easy transition when building the 'Pro' version later.

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
*   **Development Strategy:** 
    1.  **Phase 1 (Base):** Focus exclusively on completing the high-performance, open-source Jellyfin client in the current repository.
    2.  **Phase 2 (Pro):** Once the Base version is mature and stable, it will be cloned into a separate, private repository. The Pro features will be developed on top of this modular foundation.
*   **Package Name Split:**
    *   **PulseFin Base:** `com.pulsefin.app` (Open Source)
    *   **PulseFin Pro:** `com.pulsefin.app.pro` (Closed Source, Paid)
*   **Pro Features (Future):**
    *   **Library Analysis:** Analyze listening patterns to create smart mixes.
    *   **AI Recommendations:** "Spotify-like" discovery and natural language search using **Gemini 2.5 Flash-lite** or **Gemini 3.1 Flash-lite** (via **OpenRouter** for flexibility and cost-efficiency).
    *   **Smart Mixes:** High-fidelity, mood-based playlists generated using a "Tagging & Caching" strategy to maintain high margins.
    *   **Hybrid Data:** Merging Jellyfin metadata with MusicBrainz/Last.fm.
    *   **Advanced Auth:** Support for LDAP/SSO.

## 6. Business Model (Pro Version)
*   **Target Price:** $5 / month.
*   **Infrastructure:** Web-First (Stripe + RevenueCat) to bypass 15-30% App Store fees.
*   **Backend:** Supabase for Auth, Database, and secure Edge Functions (AI Gateway).
*   **Margins:** Projected ~90% profit margin by offloading AI analysis to cheap, high-context models (Gemini Flash-lite).

## 7. Constraints & Rules
*   **Visuals:** Dark-mode first priority for the Base version.
*   **Performance:** UI thread must never block. 60/120fps budget.
*   **Privacy:** No secret exposure. All Pro AI features must be delegated to a secure backend (per `Rule_Book.md`).
*   **Efficiency:** Implement "Tagging & Caching" to minimize AI tokens; analyze tracks once, store metadata in Supabase, and perform client-side or Edge-side matching for recommendations.


