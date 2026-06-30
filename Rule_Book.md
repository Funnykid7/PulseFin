# SYSTEM SPECIFICATION: AI DEVELOPMENT WORKFLOW & SECURITY ARCHITECTURE

## 1. Core Tooling Layout & CLI Execution Constraints
* **Deep Reasoning Execution:** Append the explicit structural tag `ultrathink` into complex multi-file prompt contexts to scale the underlying token bounds for complex debugging sessions.
* **UI High-Fidelity Rule:** Keep the `frontend-design` plugin active inside the environment toolsets. Feed multi-view design screenshots directly to the model when constructing responsive layouts.

## 2. Security Bounds & Server-Side Encapsulation (Pro/Cloud Layer)
> *Note: These rules apply to future AI/Spotify-like features; the core Jellyfin client remains local-to-server.*
* **Zero Secret Exposure:** Absolute prohibition against compiling production keys, database credentials, or AI models (OpenAI, Anthropic, Vertex AI) directly on frontend client-side architectures or plain-text environmental parameters.
* **Backend Delegation Rule:** Wrap all transactional workflows, payment setups (Stripe), messaging providers (SendGrid, Postmark), and data transactions behind secure Edge layers, such as Supabase Edge Functions or Firebase Cloud Functions.
* **Infrastructure Cost Failsafes:** Set active monthly budget ceilings and token throttling levels at the absolute API cluster layer. Avoid uncapped parameters to shield against sudden downstream financial burn.

## 3. Database Layering & Row Level Security (RLS)
* **Default Deactivation:** Every data entity block must block public access points completely down to zero rights by default.
* **Privilege Architecture Splitting:** Do not mix sensitive infrastructure controls (e.g., `subscription_status`, `cost_rate_limits`) onto editable user profiles. Isolate authentication keys and tiers onto server-managed, read-only structures.
* **Live Inspection Audits:** Keep the `/mcp` server tools active for database structures. Have the agent perform active live verification steps on production policies rather than relying on static schema files.

## 4. Multi-Layer Request Throttling
* **Back-End Rate Limiting:** UI-level buttons or state counters are easily bypassed. Implement granular, backend-enforced verification tables that check request tallies before downstream script computations can execute.
* **Dual Edge Limiting:** Pair per-user application counts directly with active IP-address throttling rules at the network gateway layer to negate fast identity recycling or bot network traffic.

## 5. Telemetry & Analytics Dashboard Mapping
* **Telemetry Priority:** Integrate active telemetry event suites (such as PostHog) from the initial MVP release.
* **FUNNEL TARGETS:** Setup explicit tracking indicators across the main user completion sequence:
  * `onboarding_started` -> Location/Notification Permissions -> `onboarding_completed` / `identify`
* **LLM COST OBSERVABILITY:** Instrument granular telemetry monitoring tracking the three essential vectors:
  1. **Traces:** Visual volume mapping call density timelines.
  2. **Generative AI Users:** Tracking distinct call counts per individual profile.
  3. **Total Cost (USD):** Granular token model tracking mapping precise currency burn weights.
* **NORTH STAR SCORE:** Optimize all system modifications, UI changes, and functional iterations to directly defend the **Week 1 Retention** benchmark (the volume of users returning exactly 7 days post-signup).
* **SLICKNESS METRICS:** Monitor `playback_error` rates and `time_to_start_playback` (TTSP) to ensure the Jellyfin experience rivals native local players.

## 6. Native Media Excellence (PulseFin Core)
* **Media3 & MediaSession:** Standardize on Android Media3 (ExoPlayer) for all transport controls. All playback logic must reside within a `MediaSessionService` to ensure deep system integration (Lock screen, Android Auto, WearOS).
* **Material 3 Expressive UI:** Implement Dynamic Color (Monet) palettes derived directly from active Album Art. Utilize Shared Element Transitions (e.g., Album -> Player) and maintain strict adherence to Adaptive Layouts (Compact, Medium, Expanded window classes).
* **Local-First Architecture:** Use Room as the Single Source of Truth. Jellyfin API data must be mirrored to local storage to ensure "instant" UI response and offline playback capability.
* **Performance Constraints:** Maintain a strict 60/120fps UI performance budget. Offload all metadata extraction and heavy API transformations to dedicated background Coroutine Dispatchers.
