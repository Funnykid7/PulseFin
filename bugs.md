# PulseFin Deep Audit — Bugs (Round 3)

Full-codebase re-audit (93 Kotlin files, ~9,000 lines across `:app`, `:core:common`, `:core:domain`, `:core:designsystem`, `:core:data`, `:core:playback`, `:baselineprofile`), conducted as **6 independent parallel area audits** (playback+domain; data+DB; player/queue/now-playing UI; library/search/playlist UI; settings/auth/nav/root; build config+manifest+CI), each told to read every file fully and *not* consult the prior `bugs.md` until after forming its own conclusions — so agreement between an agent's fresh finding and a prior-round finding is real cross-validation, not an agent parroting the file. Several agents went further than a plain re-read: one decompiled the pinned Media3 1.10.1 jar and Material3 1.5.0-alpha22 sources to verify exact library behavior instead of assuming it; another cross-checked Room's KSP-generated `*_Impl.kt` output to prove which `@Transaction` usages actually take effect. After collection, the highest-stakes and lowest-confidence new claims were independently re-verified by direct re-reading (see "Verification notes" under each).

**Zero test coverage anywhere in the repo** (no `src/test`, no `src/androidTest` in any module) — every finding below is unguarded by regression tests.

**Repo state at audit time:** working tree has uncommitted changes to `PulseFinNavHost.kt` (new tab-slide-direction logic — audited below, bug found), and to `gradlew`/`gradlew.bat`/`gradle/wrapper/gradle-wrapper.properties` (Gradle version bump — audited below, a regression found). `bugs.md`/`improvements.md` from a prior round were present but untracked; this round treats the codebase itself as ground truth, not the prior file's claims.

**Note on secrets:** `release.keystore` and `keystore.properties` exist at the repo root but are confirmed **not tracked by git** — no actual secret exposure.

---

## Critical

### 1. `PulseFinDatabase.clearAll()`'s `@Transaction` is a silent no-op — the logout wipe is not atomic, and can leave a previous user's search history exposed indefinitely
**File:** `core/data/src/main/kotlin/com/pulsefin/core/data/local/PulseFinDatabase.kt:210-219`

```kotlin
@Transaction
open suspend fun clearAll() {
    songDao().clear()
    albumDao().clear()
    artistDao().clear()
    recentSearchDao().clearAll()
    playlistDao().clear()
    downloadDao().clearAll()
}
```

Room's KSP processor only instruments `@Transaction` on methods declared inside an `@Dao`-annotated type. `PulseFinDatabase` is the `@Database` class, not a `@Dao` — verified directly against the KSP-generated output (`build/generated/ksp/{debug,release}/.../PulseFinDatabase_Impl.kt`), which contains **zero** reference to `clearAll`, proving Room never wraps it. Contrast with `SongDao.replaceAll` in the same file, whose generated `SongDao_Impl` **is** correctly wrapped in `performInTransactionSuspending` — confirming the pattern works at the DAO level and specifically fails at the database-class level here.

**Failure scenario:** the six clears run as independent, non-atomic suspend calls. If the process dies mid-sequence (very plausible right at logout — app backgrounded/swiped immediately after tapping "log out"), only a prefix gets wiped. `recent_searches` is the *last* table cleared and is never re-populated by any other path (only `refreshLibrary`'s four tables get re-synced on next login) — so an interruption before that final clear leaves the previous user's search history on a shared device indefinitely, visible to whoever logs in next.

**Confidence:** 95. **Fix:** use `database.withTransaction { songDao.clear(); albumDao.clear(); artistDao.clear(); recentSearchDao.clearAll(); playlistDao.clear(); downloadDao.clearAll() }` — the real `androidx.room.withTransaction` API, already used correctly elsewhere in this file (`refreshLibrary`) — not the inert annotation.

---

### 2. Logging out during an in-flight library sync resurrects the old user's data right after the wipe
**File:** `core/data/src/main/kotlin/com/pulsefin/core/data/repository/AuthRepositoryImpl.kt:90-94` (`logout()`) and `core/data/src/main/kotlin/com/pulsefin/core/data/repository/MediaRepositoryImpl.kt:107-139` (`refreshLibrary()`)

```kotlin
override suspend fun logout() {
    sessionStore.clear()
    database.clearAll()
    mediaRepository.resetSyncState()
}
```

`MediaRepositoryImpl` is a process-lifetime singleton. `logout()` neither cancels nor awaits any in-flight `refreshLibrary()` coroutine, nor does it hold `refreshMutex`. `refreshLibrary()` reads the session/API client once at the top and never re-validates it before its final `database.withTransaction { songDao.replaceAll(...); ...; playlistDao.replaceAll(...) }` write. `resetSyncState()` only flips an in-memory `hasSyncedThisProcess` flag — it does not stop the outstanding coroutine.

**Failure scenario:** each tab's `init { sync(force=false) }` routinely kicks off a background sync. If the user taps Logout while one is mid-flight, `logout()` runs immediately (wipes session + all Room tables), then the still-running `refreshLibrary()` coroutine finishes its network fetch (using the pre-logout token, never revoked by `JellyfinApiProvider`) and writes the logged-out user's full library back into the just-wiped tables. If a different user then logs in on the same shared device, they see the **previous user's** library until their own sync happens to overwrite it — a real privacy exposure requiring no process-kill.

**Confidence:** 85. **Fix:** give `MediaRepositoryImpl` a cancellable, session-scoped `CoroutineScope` for `refreshLibrary` and cancel it as part of logout, and/or have `refreshLibrary` re-validate the session immediately before its final commit.

---

### 3. `SessionStore`'s async session-populate race causes a spurious "logged out" flash on every cold start, and can make an early caller falsely see "not signed in"
**File:** `core/data/src/main/kotlin/com/pulsefin/core/data/local/SessionStore.kt:49-61`; consumed at `core/data/src/main/kotlin/com/pulsefin/core/data/jellyfin/JellyfinApiProvider.kt:25,29` and `app/src/main/kotlin/com/pulsefin/app/ui/root/PulseFinRoot.kt:29-31`

`_session` is `MutableStateFlow<Session?>(null)`; the real disk-backed value is populated later via a detached `scope.launch { _session.value = readSession() }` in `init` — never awaited by the constructor. `AuthRepositoryImpl.authState` maps `null -> LoggedOut`, and `JellyfinApiProvider`'s calls do `sessionStore.session.first()`, which returns whatever's *currently buffered* immediately rather than waiting for a "better" future emission. `PulseFinApp.onCreate()` tries to narrow this window by warming up `AuthRepository` on `Dispatchers.IO` ahead of first composition, but its own code comment acknowledges it's "racing `PulseFinRoot`'s first composition" — a narrowing, not a fix; nothing actually blocks on `readSession()` completing.

**Failure scenario:** a signed-in user relaunches the app. If `PulseFinRoot`'s collector (or any early `JellyfinApiProvider.api()` call) resolves before the Keystore-backed disk read completes, `AuthState` briefly (and, per one independent audit pass, deterministically on a cold start) resolves to `LoggedOut` rather than `Unknown` — a visible login-screen flash — and any repository call made in that window is incorrectly treated as logged out ("Not signed in" errors) despite a valid persisted session on disk.

**Confidence:** 70 (mechanism confirmed by two independent audits; visible manifestation is timing-dependent but plausible on every cold start). **Fix:** model session loading as a real 3-state type, or build the `StateFlow` via `.stateIn` over a cold `flow { emit(readSession()) }` instead of mutating a pre-seeded `MutableStateFlow` from a detached launch, so `AuthState.Unknown` (which already exists in the sealed type but is never actually emitted by the repository) covers this window instead of `LoggedOut`.

---

### 4. Queue-restore race can silently overwrite/interrupt playback the user just started
**File:** `core/playback/src/main/kotlin/com/pulsefin/core/playback/service/PlaybackService.kt:39-85` (restore coroutine, esp. 70-84)

`onCreate()` builds the `MediaSession`/`ExoPlayer` synchronously, then schedules queue restore as a fire-and-forget `serviceScope.launch` whose first line (`queueStateStore.load()`) suspends on a disk read, followed by a per-item network round trip (`streamUrlResolver.resolveStreamUrl`) before calling `player.setMediaItems(...)` + `player.prepare()` directly. `onGetSession()` returns the already-built session immediately, so a `MediaController` (e.g. the user tapping a song in Search/Home right after a cold start) can reach the same player via a fast Binder call before the restore coroutine finishes — with no coordination between the two paths and no guard like `if (player.mediaItemCount == 0)`.

**Failure scenario:** app was killed mid-queue. User reopens it and, before the restore coroutine's network resolution finishes, taps a different song. The tap wins the race and starts playing — then the restore coroutine finishes and calls `setMediaItems`/`prepare()` again, silently replacing the just-started queue with the stale pre-kill one and knocking playback back to paused. No error shown.

**Confidence:** 80. **Fix:** track the restore coroutine's `Job` and skip/cancel it once `player.mediaItemCount > 0` before it applies its own `setMediaItems`.

---

### 5. Queue swipe-to-remove can silently delete an extra, unrelated track
**File:** `app/src/main/kotlin/com/pulsefin/app/ui/player/QueueScreen.kt:197-200`, interacting with Material3's `SwipeToDismissBox` internals (verified against the pinned `material3-android:1.5.0-alpha22` sources)

```kotlin
val dismissState = rememberSwipeToDismissBoxState()
SwipeToDismissBox(
    state = dismissState,
    onDismiss = { playbackController.removeFromQueue(index) },   // fresh lambda every recomposition
    ...
)
```

Material3's `SwipeToDismissBox` internally runs `LaunchedEffect(state.settledValue, onDismiss) { if (state.settledValue != Settled) onDismiss(...) }`. A `LaunchedEffect` restarts its body whenever *any* key changes, including `onDismiss` — and the `onDismiss` lambda here captures `index` from `itemsIndexed`, so it is a **new object identity on every recomposition of that row**, not memoized. Every row also reads `state.currentIndex` (`isCurrent`), so any `PlaybackController.state` change — a track auto-advancing, play/pause, shuffle/repeat toggling — recomposes every visible row and manufactures a fresh `onDismiss` for each.

**Failure scenario:** user swipes row N to dismiss it. `removeFromQueue(N)` fires once correctly, but crosses the MediaController boundary asynchronously — the `queue` StateFlow hasn't caught up yet, so this row is still composed at the same index. If, in that window, anything changes `PlaybackController.state` (e.g. the current track naturally advances), this row recomposes, produces a **new** `onDismiss` instance (still capturing `index = N`), which changes the internal `LaunchedEffect`'s key tuple (`settledValue` is unchanged, but `onDismiss` differs) — the effect restarts, re-evaluates `settledValue != Settled` as still true, and **fires `onDismiss` a second time**, deleting whatever item now sits at index N — a different, unintended song.

**Confidence:** 70 (mechanism confirmed against actual decompiled library source; trigger requires a state-driven recomposition landing inside the async removal round-trip). **Fix:** stabilize the callback identity, e.g. `val onDismiss = remember(index) { { playbackController.removeFromQueue(index) } }`, or better, have `removeFromQueue` take the item's stable key (mediaId+occurrence) so a duplicate call is a no-op instead of an off-target delete.

---

## High

### 6. Tab-switch slide direction is wrong for every forward tab→detail/utility navigation
**File:** `app/src/main/kotlin/com/pulsefin/app/navigation/PulseFinNavHost.kt:465-486` (uncommitted, in-flight change)

```kotlin
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isForwardTabSwitch(): Boolean {
    val fromIndex = tabRoutes.indexOf(initialState.destination.route.orEmpty())
    val toIndex = tabRoutes.indexOf(targetState.destination.route.orEmpty())
    return toIndex >= fromIndex
}
```

`tabRoutes` only lists the 5 bottom-nav routes. `indexOf` returns `-1` for anything else (Settings, Search, NowPlaying, Queue, Lyrics, all detail screens). The `-1` fallback is asymmetric: non-tab→tab (popping back into a tab) gives `fromIndex=-1`, and any real `toIndex >= -1` is always true (correctly "forward", matching the doc comment). But tab→non-tab (a forward push) gives `toIndex=-1`, and `-1 >= fromIndex` (a real index ≥0) is always false — resolved as "backward", the opposite of the doc comment's stated intent.

**Failure scenario:** tap an artist from the Artists tab → `ARTIST_DETAIL`. The Artists tab exits sliding right (a pop-like motion) while `ArtistDetail` enters sliding in from the right at the same time — the two screens visually cross instead of a clean push. Same defect hits Playlists→PlaylistDetail/Downloads and any tab→Settings.

**Confidence:** 90 (independently confirmed twice). **Fix:** `if (fromIndex == -1 || toIndex == -1) return true`, or give non-tab destinations their own real enter/exit transitions instead of inheriting `tabEnter`/`tabExit`.

---

### 7. Queue reorder-drag can get stuck mid-drag when the queue mutates concurrently
**File:** `app/src/main/kotlin/com/pulsefin/app/ui/player/QueueScreen.kt:237-266`

`draggedIndex`/`dragOffsetY` are only reset from `onDragEnd`/`onDragCancel` inside `detectDragGesturesAfterLongPress`, running inside `Modifier.pointerInput(index, queue.size) { ... }`. A `pointerInput` key change cancels the running gesture-detector coroutine **without** invoking `onDragEnd`/`onDragCancel` — this codebase's own `WavySeekBar.kt` documents exactly this gotcha and works around it with a `LaunchedEffect` reset; `QueueScreen` has no equivalent guard.

**Failure scenario:** user long-press-drags a row to reorder it. While still holding, the queue mutates from any source — another row's swipe-dismiss completing (its removal lands "a beat after" the call, per this file's own comment), a track auto-advancing and changing the list, or a queue edit from elsewhere. `queue.size` changes, every row's `pointerInput(index, queue.size)` key changes, the drag's coroutine is cancelled without `onDragCancel` firing, and `draggedIndex`/`dragOffsetY` freeze at their pre-cancellation values. An unrelated row now inherits `isDragged = true` and stays visually detached/lifted (and the `reflowTarget` calculation keeps shifting a whole cluster of rows), until the user long-presses that exact stale slot again or leaves and re-enters the screen.

**Confidence:** 90. **Fix:** don't key `pointerInput` on `queue.size` — use the row's stable `queueKeys[i]` identity instead — and/or reset `draggedIndex`/`dragOffsetY` in a `DisposableEffect`/`LaunchedEffect(queue.size)` at the screen level, mirroring `WavySeekBar`'s pattern.

---

### 8. `retry()`'s sequential stream-re-resolution can throw `IndexOutOfBoundsException` or silently corrupt the queue if it mutates mid-resolution
**File:** `core/playback/src/main/kotlin/com/pulsefin/core/playback/controller/PlaybackController.kt:179-204`, specifically 188-195

```kotlin
val refreshed = (0 until current.mediaItemCount).mapNotNull { i ->
    val item = current.getMediaItemAt(i)
    streamUrlResolver.resolveStreamUrl(item.mediaId)?.let { item.withRefreshedUri(it) }
}
...
withController { it.replaceMediaItems(0, current.mediaItemCount, refreshed) }
```

Unlike `play()` (which resolves all items concurrently via `async`/`awaitAll`), `retry()` resolves one item at a time with sequential suspend calls, each yielding the main thread. `removeFromQueue`/`moveQueueItem` are plain synchronous calls invokable from a UI click while this loop is suspended on network I/O. The iteration range `(0 until current.mediaItemCount)` is fixed at loop start; if the queue shrinks mid-loop, a later `current.getMediaItemAt(i)` call can index past the live (shrunk) timeline. Verified against the decompiled Media3 1.10.1 `BasePlayer`/`ExoPlayerImpl`: `getMediaItemAt` forwards to `Timeline.getWindow(index, window)`, which throws on an out-of-range index — an uncaught exception inside a `suspend fun` with no try/catch here. Even without a mid-loop removal throwing, the final `replaceMediaItems(0, current.mediaItemCount, refreshed)` re-reads `mediaItemCount` fresh against a `refreshed` list sized/ordered from the stale pre-mutation timeline, which can also silently drop or reorder whatever the user just edited into the queue.

**Confidence:** 60 (requires a 401-triggered retry concurrent with a queue edit — a real but narrow window). **Fix:** resolve all items concurrently as `play()` does (shrinks the window to near-zero), and/or snapshot the timeline once and treat any detected mutation during resolution as invalidating the retry.

---

### 9. Every device rotation replays the auth-gate loading spinner and login→app transition for an already-logged-in user
**File:** `app/src/main/kotlin/com/pulsefin/app/ui/root/PulseFinRoot.kt:29-38`

No `android:configChanges` is declared, so rotation destroys/recreates `MainActivity`, re-running `setContent {}` from scratch. `authRepository.authState.collectAsStateWithLifecycle(initialValue = AuthState.Unknown)` necessarily renders `Unknown` for the first frame(s) of the fresh composition (collection is gated behind `repeatOnLifecycle(STARTED)`, which starts after composition at `CREATED` — at least one frame is guaranteed wrong). Since `AnimatedContent`'s `contentKey = { it::class }` treats `Unknown`/`LoggedIn` as distinct keys, the `fadeIn()+scaleIn()`/`fadeOut()` transition is guaranteed to actually run on every rotation.

**Failure scenario:** a logged-in user rotates their phone (auto-rotate on). Every single rotation shows a momentary loading-spinner flash followed by the whole nav host visibly fading/scaling back in — deterministic, not a rare timing fluke.

**Confidence:** 85 (raised from a prior round's 75 after independent re-confirmation with a concrete mechanism: guaranteed ≥1 wrong frame due to the `STARTED` gate). **Fix:** seed `initialValue` from `authState`'s current synchronous value if exposed, instead of hardcoding `Unknown`.

---

### 10. Optimistic favorite toggle can be silently clobbered by a concurrent library sync
**File:** `core/data/src/main/kotlin/com/pulsefin/core/data/repository/MediaRepositoryImpl.kt:197-216` (`setFavorite`) vs. `:107-139` (`refreshLibrary`)

`setFavorite`'s per-song mutex only serializes concurrent toggles of the *same* song and has no relationship to `refreshMutex`. `refreshLibrary()`'s `songDao.replaceAll(songs)` wholesale-clears and reinserts the entire songs table from a fresh server snapshot, independent of any in-flight favorite toggle.

**Failure scenario:** user favorites song S1 (Room optimistically set `isFavorite=true`, `markFavoriteItem` sent to the server). A concurrent `refreshLibrary()`'s `GetItems` fetch lands on the server *before* the favorite-mark is processed there, returning S1 as `isFavorite=false`; `refreshLibrary` overwrites Room's S1 row back to unfavorited. Since `markFavoriteItem` itself succeeds, `setFavorite`'s rollback-on-failure branch never fires — the toggle silently reverts even though the server call succeeded.

**Confidence:** 80 (raised from a prior round's 72 — two independent audits now agree with the same mechanism). **Fix:** re-assert the Room value after a successful server call while still holding the per-song mutex, or have the song upsert preserve locally-pending favorite mutations instead of blindly overwriting; or hold `refreshMutex` during `setFavorite`'s server call.

---

### 11. Uncommitted change dropped the Gradle wrapper's distribution checksum
**File:** `gradle/wrapper/gradle-wrapper.properties`

Directly diffed against the committed `HEAD` version:
```diff
-distributionSha256Sum=bafc141b619ad6350fd975fc903156dd5c151998cc8b058e8c1044ab5f7b031f
-distributionUrl=https\://services.gradle.org/distributions/gradle-9.5.1-bin.zip
+distributionUrl=https\://services.gradle.org/distributions/gradle-9.6.1-bin.zip
+validateDistributionUrl=true
```
The prior commit pinned a SHA-256 for the exact Gradle distribution zip. This uncommitted change bumps the Gradle version but drops the checksum entirely rather than updating it to match the new version's official hash. `validateDistributionUrl=true` only checks that the URL points at a legitimate Gradle host over HTTPS — it does **not** verify the downloaded binary's content. Without the checksum, a compromised mirror/CDN or a MITM on a dev/CI machine's first `./gradlew` invocation could substitute a malicious distribution and it would be silently accepted and run with full build authority.

**Confidence:** 95 (directly verified via `git show HEAD` vs. working tree). **Fix:** restore `distributionSha256Sum` with the correct published hash for `gradle-9.6.1-bin.zip` before committing this bump.

---

### 12. Cleartext traffic permitted app-wide, not scoped to the configured Jellyfin server
**File:** `app/src/main/AndroidManifest.xml:17`

`android:usesCleartextTraffic="true"` with no `android:networkSecurityConfig` anywhere — permits unencrypted HTTP for *any* domain the app might ever talk to (including image loading, not just the user's self-hosted server), making any such request MITM-able on a hostile network.

**Confidence:** 85. **Fix:** add a `network_security_config.xml` scoping cleartext to the runtime-configured server domain/IP (or private/local ranges only).

---

## Medium

### 13. `SearchScreen` has no "no results" state, and swallows search failures as if they were zero matches
**File:** `app/src/main/kotlin/com/pulsefin/app/ui/search/SearchScreen.kt:196-279` (UI), `:103-116` (`runSearch`)

The results `LazyColumn` has no `else` branch for "query non-blank, search finished, all three result lists empty" — every sibling screen (Albums/Artists) handles its empty case explicitly, and no `no_results`-style string resource exists anywhere in the app. Separately, `runSearch` does `(result as? PulseResult.Success)?.data ?: SearchResults(emptyList(), emptyList(), emptyList())` — a genuine network failure is silently converted into the exact same empty-results state as a legitimate zero-match search; `SearchUiState` has no `error` field at all, unlike `AlbumDetailUiState`/`ArtistDetailUiState`/`PlaylistDetailUiState`, which all carry one.

**Failure scenario:** a zero-match search renders a blank screen indistinguishable from broken. Separately, searching while offline/server-down renders the identical blank screen with no way to tell the two apart or retry.

**Confidence:** 90 (empty-state gap), 85 (swallowed-failure gap). **Fix:** add an explicit empty-state branch; add an `error: String?` to `SearchUiState` populated on `PulseResult.Failure` and render it like the detail screens do.

---

### 14. `AddToPlaylistSheet` can be dismissed mid-submission, silently desyncing local state from the server
**File:** `app/src/main/kotlin/com/pulsefin/app/ui/playlist/AddToPlaylistSheet.kt:43, 52, 84-93, 107-115`

The in-flight `addToPlaylist`/`createPlaylist` call runs in `rememberCoroutineScope()`, scoped to the sheet's own composition. `ModalBottomSheet(onDismissRequest = onDismiss, ...)` wires the sheet's default swipe/scrim/back dismissal directly to `onDismiss` unconditionally — nothing guards it against `isSubmitting`. Confirmed via sibling comparison: `PlaylistsScreen.createPlaylist()` runs the equivalent call in `viewModelScope` (survives dialog dismissal) — the identical operation is cancel-on-dismiss in this screen and cancel-safe in its sibling.

**Failure scenario:** tap a playlist row (request in flight), swipe the sheet down before the response returns. The scope is cancelled mid-request; if the server had already applied the add, the local Room mirror never gets the follow-up update — local state silently drifts from the server until the next full sync, no error shown.

**Confidence:** 85. **Fix:** `onDismissRequest = { if (!isSubmitting) onDismiss() }`, or move the mutation to a scope that outlives the sheet.

---

### 15. `PlaylistDetailScreen.removeSong` silently discards failures — the only mutating action in the file that doesn't check its result
**File:** `app/src/main/kotlin/com/pulsefin/app/ui/playlist/PlaylistDetailScreen.kt:119-125`

```kotlin
fun removeSong(entryId: String) {
    val id = _playlistId.value ?: return
    viewModelScope.launch {
        repository.removeFromPlaylist(id, listOf(entryId))
        reloadSongs()
    }
}
```
`removeFromPlaylist` returns `PulseResult<Unit>`, but the result is never inspected. `rename` and `delete` in the same ViewModel both explicitly check for `PulseResult.Failure` and emit to `_actionError` (surfaced as a snackbar) — `removeSong` is the one action that doesn't.

**Failure scenario:** user removes a song while offline/server error → call fails silently, no snackbar; `reloadSongs()` reloads the unchanged list, so the song just reappears with zero explanation.

**Confidence:** 88. **Fix:** mirror `rename`/`delete` — check for `PulseResult.Failure` and emit `_actionError` on it.

---

### 16. Rapid playlist song removal can leave a stale song list on screen
**File:** `app/src/main/kotlin/com/pulsefin/app/ui/playlist/PlaylistDetailScreen.kt:119-176` (`removeSong` / `reloadSongs`)

Each `removeSong()` call launches an independent, untracked coroutine that mutates then reloads, unconditionally overwriting `uiState` with whichever network round-trip finishes last — not whichever mutation was issued last. Rapid back-to-back removals can leave a stale list on screen until the user leaves and re-enters. (`PlaylistsScreen.kt` does not share this — its list is a reactive `Flow` over Room, not a manual reload.)

**Confidence:** unchanged from prior round, re-confirmed structurally present. **Fix:** track the reload `Job` and cancel/replace it before launching a new one.

---

### 17. Pull-to-refresh spinner on Artists tab can be cleared by an unrelated concurrent sync
**File:** `app/src/main/kotlin/com/pulsefin/app/ui/library/ArtistsScreen.kt:48-55`

```kotlin
private fun sync(force: Boolean) {
    viewModelScope.launch {
        if (force) isRefreshing = true
        repository.refreshLibrary(force = force)
        isRefreshing = false   // unconditional, unlike every sibling
    }
}
```
Every sibling ViewModel (`HomeViewModel`, `AlbumsScreen`, `PlaylistsScreen`, `YourMixScreen`) guards this with `if (force) isRefreshing = false`, specifically so a concurrently-running non-forced sync can't clear the spinner for a still-in-progress forced (pull-to-refresh) one. `ArtistsScreen.kt` is the one file missing that guard.

**Failure scenario:** `ArtistsViewModel` inits with a `force=false` sync (e.g. the first-ever sync after login, actually doing the slow fetch). User pulls-to-refresh before it finishes → `sync(force=true)`. When the init call completes, it unconditionally clears `isRefreshing`, hiding the spinner while the user's own pull-to-refresh is still running.

**Confidence:** 92 (verified via direct line-by-line diff against all 4 sibling files, which are otherwise textually identical). **Fix:** `if (force) isRefreshing = false`.

---

### 18. `removeFromQueue`/`moveQueueItem` trust caller-supplied indices with zero validation
**File:** `core/playback/src/main/kotlin/com/pulsefin/core/playback/controller/PlaybackController.kt:345-351`

```kotlin
fun moveQueueItem(fromIndex: Int, toIndex: Int) = withController { it.moveMediaItem(fromIndex, toIndex) }
fun removeFromQueue(index: Int) = withController { it.removeMediaItem(index) }
```
`playIndex()` elsewhere in the same file correctly bounds-checks (`if (index in 0 until controller.mediaItemCount)`) — these two don't. Verified against decompiled Media3 1.10.1: an out-of-range-*high* index is silently clamped/no-op (UI/state desync, not a crash), but a **negative** index throws `IllegalArgumentException` via `Preconditions.checkArgument`. The current sole caller (`QueueScreen.kt`) never passes a negative index today, so this isn't presently reachable — but the controller function itself has no defense if a future caller does.

**Confidence:** 60. **Fix:** add the same bounds guard used in `playIndex()` to both functions.

---

### 19. "Sleep at track end" doesn't actually track the track — just a one-time wall-clock estimate
**File:** `core/playback/src/main/kotlin/com/pulsefin/core/playback/controller/PlaybackController.kt:273-306`

`startSleepTimerAtTrackEnd()` computes `duration - currentPosition` once and arms a plain wall-clock countdown. It's never re-armed or cancelled on track transition, seek, or pause.

**Failure scenario:** user is 1 minute into a 4-minute song (3 min left) and taps "Sleep at track end." 30 seconds later they skip to a different 6-minute song. The original 3-minute countdown keeps running against the new song, pausing playback ~2.5 minutes into it — not at that track's actual end.

**Confidence:** unchanged from prior round, re-confirmed present (not re-derived line-by-line this round; playback agent's read of the surrounding file found no re-arming logic anywhere). **Fix:** drive this off the player's own timeline/track-transition events, re-arming or cancelling on track change.

---

### 20. Compose Material3 **alpha** dependency shipped in the production app, with real dependence on its Expressive-only APIs
**File:** `gradle/libs.versions.toml:18` — `material3 = "1.5.0-alpha22"`, consumed via `api(...)` by `app` and `core:designsystem`

Not just an unused-precaution alpha: `core/designsystem/theme/PulseArtShapes.kt` uses `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` for `MaterialShapes.Cookie9Sided`/`Clover4Leaf` etc., confirming the app genuinely depends on unstable Material3 Expressive shape APIs in shipping code, not just an unpinned transitive version.

**Confidence:** 90. **Fix:** pin to the latest stable `material3` compatible with the pinned Compose BOM (`2026.06.00`) if the Expressive shapes aren't load-bearing, or explicitly document the alpha dependency as an accepted tradeoff if they are.

---

### 21. `ui-text-google-fonts` is pinned independently of the Compose BOM, risking a mixed-version classpath
**File:** `gradle/libs.versions.toml:17,70`

Every other `androidx.compose.ui:*` artifact resolves from the BOM (`2026.06.00`) with no explicit version; `ui-text-google-fonts` alone force-pins to a standalone `composeUi = "1.11.3"`. An explicit version always wins over a BOM-managed one, so this artifact can end up on a different Compose UI patch version than its siblings — AGP's own Compose tooling specifically warns this can cause `NoSuchMethodError`/`NoSuchFieldError` at runtime. No comment justifies the independent pin.

**Confidence:** 85. **Fix:** drop the explicit version and let it resolve from the BOM like its siblings; remove the now-unused `composeUi` catalog key if nothing else needs it.

---

## Low / Informational

- **Playlist sync does an extra network round-trip per playlist** (`core/data/.../MediaRepositoryImpl.kt:496-509`, `toPlaylist`) — each playlist in `refreshLibrary` triggers an additional `getPlaylistItems` call purely to compute member-artwork thumbnails, scaling linearly with playlist count. Not a correctness bug, a perf/N+1-style observation.
- **`favoriteMutexes` map grows unbounded for process lifetime** (`MediaRepositoryImpl.kt:83`) — one `Mutex` entry per distinct song ID ever toggled, never evicted. Bounded by library size in practice.
- **`setFavorite`'s optimistic Room write sits outside the `PulseResult` error boundary** (`MediaRepositoryImpl.kt:202-215`) — if the optimistic `songDao.setFavorite` call itself throws (e.g. disk full), the exception propagates uncaught instead of surfacing as `PulseResult.Failure`, inconsistent with the rest of the repository's contract. Confidence 55.
- **Download failures are silent no-ops with no user-visible error** (`core/playback/.../download/DownloadRepositoryImpl.kt:91-107`) — `download()` returns early with no signal if stream-URL resolution fails; `downloadAll()` wraps each in `runCatching` and discards the `Result` entirely. Matches the interface's `Unit`-returning contract (architectural, not accidental) but is a genuine silent-failure path with no log/telemetry. Confidence 85.
- **Cold-start download-index seed vs. live listener update — narrow TOCTOU** (`DownloadRepositoryImpl.kt:46-84`) — the seed loop's "already seen" check-then-act isn't atomic with the listener's add-then-apply, so a listener update landing between the seed's check and its write can be clobbered by a stale seed snapshot. Window is a few instructions wide with no suspension point — structurally real, not reliably reproducible on demand. Self-corrects on the next `DownloadManager` event for non-terminal states; terminal states (`COMPLETED`/`FAILED`) get no further callback to self-correct with. Confidence 55.
- **Disconnected `MediaController` is never explicitly released** (`PlaybackController.kt:138-146`) — `onDisconnected` drops references but never calls `controller.release()`/`releaseFuture(future)`. Mostly harmless since `PlaybackController` is an app-lifetime singleton and the underlying binder is already dead; minor resource-hygiene gap. Confidence 40.
- **`AlbumDetailScreen`/`ArtistDetailScreen` have no explicit empty state** for a genuinely empty track/album list (load succeeded, list is just empty) — unlike `AlbumsScreen`/`ArtistsScreen`/`PlaylistDetailScreen`, which all handle this. Reachability from a real Jellyfin server is uncertain. Confidence 55.
- **Double pull-to-refresh (`force=true` × 2) could still clear the spinner early** even in the correctly-guarded ViewModels (Home/Albums/Playlists/YourMix) — the `if (force)` guard protects against a `force=false` sync clearing a `force=true` one, but not against two overlapping `force=true` calls; whether this is actually reachable depends on whether `PullToRefreshBox` already blocks re-triggering `onRefresh` while a refresh is showing (not verified against the actual Material3 alpha22 source in this pass). Confidence 60.
- **Every rotation also briefly flashes default settings** (`app/src/main/kotlin/com/pulsefin/app/MainActivity.kt:52`) — same `collectAsStateWithLifecycle(initialValue = Settings())` gap as finding #9, applied to theme/haptics settings. `Settings()` defaults to dark theme + dynamic color on; a user who's changed either sees a one-frame flash back to the default on every rotation (no animation attached, so less noticeable than #9). Confidence 60 (re-confirmed independently this round, up from 55).
- **`LyricsScreen.kt:69-120`** — the Synced/Static segmented control derives from the *previous* track's lyrics until the new fetch resolves (can last the full fetch duration on a slow network) — no functional consequence found (a tap during that window is superseded once the real value lands). Cosmetic only.
- **`core/playback/build.gradle.kts:18-26`** — `lint { disable += "UnsafeOptInUsageError" }` is module-wide; grepped the whole module and found no *other* experimental API currently masked by it. The residual risk (a future unrelated opt-in going unflagged) is real but speculative.
- **`app/proguard-rules.pro`** is effectively empty (2 lines) against a reflection-heavy dependency set (Room, Koin, the Jellyfin Kotlin SDK). Room/Koin ship self-protecting consumer rules; the Jellyfin SDK AAR's own consumer-rules coverage was not verified from source in this pass (lives in an external AAR). Confidence 40 — recommend a real signed release-build smoke test before shipping.

---

## Checked and found NOT to be a bug (investigated concretely, not assumed)

- **Gradle wrapper / `gradlew` scripts tampering:** diffed against `HEAD` — only comment-text edits plus the version bump covered in finding #11; `distributionUrl` still points at the legitimate `services.gradle.org` host. Not a supply-chain compromise beyond the missing checksum.
- **KSP/Kotlin version compatibility** (`kotlin = "2.4.0"`, `ksp = "2.3.9"` in `gradle/libs.versions.toml`) — one audit pass flagged this pairing as suspicious since KSP releases are normally Kotlin-version-prefixed. **Personally verified and ruled out:** `core/data/build/generated/ksp/{debug,release}/` both exist with recent build timestamps (Jul 16 and Jul 21), proving the project builds successfully today with these exact pinned versions. Not a live issue.
- **`SongDao`/`AlbumDao`/`ArtistDao`/`PlaylistDao.replaceAll`'s `@Transaction` usage** — confirmed via generated `*_Impl.kt` output that Room correctly wraps these (unlike finding #1's database-class misuse). Working as intended.
- **`MediaRepositoryImpl.refreshLibrary()`'s four-table sync** — already atomic via `database.withTransaction {}`; no partial-sync inconsistency.
- **TOCTOU on `hasSyncedThisProcess`** — checked inside `refreshMutex.withLock`, no interleaving possible.
- **Playlist mutation ordering** (create/rename/delete/add/remove) — server call always precedes the local Room mirror update; a network failure short-circuits before any local write, so no partial/inconsistent local state on failure.
- **`removeFromPlaylist` taking raw `String` entry IDs** — verified against the actual Jellyfin SDK source; the SDK method genuinely takes `String`, not a bug.
- **Secrets/token handling in `core/data`** — `SessionStore` uses `EncryptedSharedPreferences`/Keystore (AES256-GCM/SIV), not plaintext; grepped for `Log.`/`println`/`Timber` calls — no token/credential logging found anywhere in the module.
- **SQL correctness across all DAOs** — no joins exist (all single-table entities); every `@Query`'s WHERE/ORDER BY matches its stated intent. No wrong-clause bugs.
- **Ticks→ms conversions** (song duration, lyrics timing) — consistently use Jellyfin's 100ns-tick convention (`/ 10_000`).
- **`fallbackToDestructiveMigration(dropAllTables = true)` on schema bump** — a real risk (wipes `recent_searches` on any version bump) but already explicitly flagged and reasoned about in the code's own comments as an accepted interim tradeoff; not a new finding.
- **Search debounce (`collectLatest`)** — correctly cancels an in-flight `runSearch` the instant a newer query arrives; no stale-overwrites-fresh race.
- **Home/YourMix/Downloads "resolve by id at click time"** — all three correctly re-resolve the clicked song's index against the live list rather than a captured index, guarding against a concurrent reorder.
- **AlbumDetail/PlaylistDetail click-by-captured-index** — looked inconsistent with the above at first glance, but these lists load once per screen visit with no periodic background reorder while open, so the race the other screens guard against doesn't apply here.
- **`NewPlaylistDialog`/`PlaylistsScreen` double-submission** — `isSubmitting` correctly disables the confirm button; the mutation runs in `viewModelScope` (survives dismissal) — correct, unlike `AddToPlaylistSheet` (finding #14).
- **`RenamePlaylistDialog`/delete confirmation** — dismiss synchronously, fire-and-forget into `viewModelScope`; failures surface via `_actionError`/snackbar. Correct.
- **`DownloadStateSync`** — `previousSongIds - downloads.keys` computed before reassignment; correct ordering, no stale-removal bug.
- **Navigation IDs/stale-selection** — each `navigate()` call creates a fresh backstack entry (and thus a freshly-scoped ViewModel); no stale-id bug found.
- **WavySeekBar's `pointerInput(durationMs)` scrub-state reset** — already has an explicit `LaunchedEffect(durationMs) { scrub = null }` mitigation for exactly the class of bug in finding #7; correctly implemented.
- **Haptics API version-gating** (`Motion.kt`) — `SEGMENT_TICK` (API 34+) and `CONFIRM` (API 30+) both correctly gated for `minSdk = 31`.
- **`pressScale`/`bouncyClickable`** — intentional raw `interactionSource.interactions` collection (documented), correctly scoped, no leaked collectors.
- **MiniPlayer position ticking** — isolated into a sub-composable so it doesn't drag the whole card into 2×/sec recomposition; verified real isolation.
- **NowPlayingScreen's `AnchoredDraggableState` dismiss gesture** — confirmed via NavHost that the screen is a standard nav destination (not a persistent bottom-sheet host), so drag state is fully disposed/recreated on each visit; no stuck-dismissed-state risk.
- **Login double-submit** — `LoginViewModel.submit()` guards synchronously before any suspension point; button also disabled while submitting. No race.
- **Settings toggles** (dark theme, dynamic color, haptics) — all three correctly persist to DataStore, none in-memory-only.
- **Deep link handling** — no deep-link surface exists in the app (no `deepLink {}` blocks, no custom URI scheme, no `onNewIntent`) — nothing to have a bug in.
- **Back-stack / tab `saveState`/`restoreState`** — matches Google's standard recommended bottom-nav pattern exactly; no duplicate entries or stale-destination risk.
- **`JellyfinApiProvider`'s client cache** — correctly invalidates on token change; no stale-client-reuse-after-relogin bug.
- **AndroidManifest exported components / permissions / CI secret handling** — `MainActivity` is the only exported component (required, LAUNCHER-only); `PlaybackService`'s foreground-service permissions are correctly declared in the `core/playback` manifest and merge in; the release CI workflow triggers only on tag push (no `pull_request_target`), pipes the keystore secret through `base64 -d` (never echoed raw), minimal `permissions:` block. No issues.
- **`:baselineprofile` module's alpha `benchmark`/`baselineprofile` version** — structurally isolated `com.android.test` module; cannot leak into `:app`'s shipped classpath. Confirmed non-issue.
- **`keystore.properties`/`release.keystore`** — confirmed untracked by git, correctly gitignored.

---
---

# Round 4 — New Findings

A second fresh pass, targeting angles rounds 1-3 hadn't dug into: the network/API/query-construction layer, accessibility/i18n/formatting, Compose recomposition stability + DI scoping + media-notification correctness, and a cross-subsystem sweep (process death, download/playback interaction, logout-vs-playback, empty-library first run). Each of the 4 parallel audits was handed the complete Round 1-3 findings above and instructed not to re-report anything already there, even under different wording — every finding below is genuinely new. Two of the four audits independently arrived at the same root cause for finding #22 (logout doesn't stop playback), which is real cross-validation, not duplication.

## Critical

### 22. Logging out doesn't stop or disconnect active playback — audio, the notification, and the media session all keep running under the wiped-out session
**Files:** `core/data/src/main/kotlin/com/pulsefin/core/data/repository/AuthRepositoryImpl.kt:90-94` (`logout()`), `app/src/main/kotlin/com/pulsefin/app/ui/settings/SettingsViewModel.kt:41-46` (`signOut()`), `core/playback/src/main/kotlin/com/pulsefin/core/playback/controller/PlaybackController.kt` (whole file), `core/playback/src/main/kotlin/com/pulsefin/core/playback/service/PlaybackService.kt` (whole file), `app/src/main/kotlin/com/pulsefin/app/playback/PlaybackScrobbler.kt`

`logout()`/`signOut()` only call `sessionStore.clear()`, `database.clearAll()`, `mediaRepository.resetSyncState()`, `downloadRepository.clearAllDownloads()`. A full-repo grep for `logout`/`signOut` confirms zero code path touches `PlaybackController`, the `MediaSession`, or `PlaybackScrobbler` — both are process-lifetime Koin singletons with no awareness that a session was logged out. `PulseFinRoot` reacts to `AuthState.LoggedOut` only by swapping the Compose content tree to `LoginScreen()` (removing the UI, including the `MiniPlayer`) — it never calls into `PlaybackController` to stop anything, and the foreground `PlaybackService`/notification is untouched.

**Failure scenario:** user is playing a song and taps "Sign Out." Room is wiped and the UI shows the login screen — but the currently-playing track keeps playing audibly, and the system media notification (from the still-alive `MediaSession`) keeps showing transport controls for the logged-out session's queue, with no in-app UI left to reach it except the notification itself. `PlaybackScrobbler` keeps collecting playback state and calling `reportPlaybackStart/Progress/Stopped` against the now-nulled session — these throw and are silently swallowed by `runCatching`, so scrobbling just goes dead with no error. On a shared device, a second user can log in and browse their own freshly-synced library while the first user's song keeps playing in the background — deterministic on every logout where something is playing, no race window required. Secondarily, `PlaybackScrobbler.playSessionId` is a single `UUID` generated once per process lifetime and never regenerated on login/logout, so a logout→different-user-login within the same process reports both users' playback history under one Jellyfin `playSessionId`.

**Confidence:** 90 (primary finding, independently reached by two separate audit passes with the same root cause). **Fix:** have `logout()`/`signOut()` call `playbackController.stop()` and clear the queue (`setMediaItems(emptyList())`, persist the cleared state) before/alongside the DB wipe; regenerate `PlaybackScrobbler.playSessionId` on each login.

---

### 23. Jellyfin access token is embedded in plaintext artwork URLs, persisted to the unencrypted Room DB, which is not excluded from Android backup
**Files:** `core/data/src/main/kotlin/com/pulsefin/core/data/repository/MediaRepositoryImpl.kt:511-524` (`artworkUrl()`/`ensureApiKey`), `core/data/src/main/kotlin/com/pulsefin/core/data/jellyfin/JellyfinUrls.kt:4-8`, `core/data/src/main/kotlin/com/pulsefin/core/data/local/PulseFinDatabase.kt:239` (plain SQLite, no `SupportFactory`/SQLCipher), `app/src/main/AndroidManifest.xml:9-11` (`allowBackup="true"`), `app/src/main/res/xml/backup_rules.xml`/`data_extraction_rules.xml`

Every song/album/artist/playlist's `artworkUrl` column has `api_key=<accessToken>` appended in plain text (intentional per an in-code comment — the SDK doesn't add one to this URL itself), and that column is written to the unencrypted `pulsefin.db` on every `refreshLibrary()`. The backup-rule files show real security awareness — they explicitly exclude the encrypted session prefs (`pulsefin_session_secure.xml`) and the ExoPlayer cache DB — but do **not** list `pulsefin.db`, so it's included in Android's default cloud backup and device-to-device transfer, carrying the live access token in every artwork URL.

**Failure scenario:** a user backs up their phone (default Google account cloud backup) or transfers to a new device; the live Jellyfin access token — meant to be protected by `EncryptedSharedPreferences` — is trivially recoverable in plaintext from the backed-up `pulsefin.db`. Secondary correctness effect: after a re-login with a new token, existing Room rows keep the *old* token in `artworkUrl` until the next `refreshLibrary()` completes, so artwork briefly 404s post-relogin.

**Confidence:** 80. **Fix:** add `pulsefin.db` to the backup exclusion rules, and/or stop persisting the token in the stored URL — resolve the auth query param at load time via a Coil interceptor/fetcher instead of baking it into the DB string.

---

## High

### 24. No handling anywhere for a server-side session revocation (401) — `AuthState` never reflects an expired/revoked token
**Files:** `core/data/src/main/kotlin/com/pulsefin/core/data/repository/MediaRepositoryImpl.kt` (every `PulseResult.runCatchingResult` call site), `core/data/src/main/kotlin/com/pulsefin/core/data/repository/AuthRepositoryImpl.kt:31-37` (`authState` derived only from local `SessionStore`)

Verified against the actual Jellyfin SDK source: every non-2xx response throws one generic `InvalidStatusException(status)` with no distinct type per code. `PulseResult.Failure` carries no error-type field, and nothing anywhere inspects the status code to detect a 401 outside the ExoPlayer streaming path (`PlaybackController`'s `PlaybackError.Auth`, which is unrelated to this — that only covers the *media* stream URLs, not the general Jellyfin API). `AuthRepositoryImpl.authState` is derived purely from the persisted `SessionStore` value; nothing transitions it to `LoggedOut` on a server-side revocation.

**Failure scenario:** an admin revokes the user's API key, or the server invalidates the session (password change, token-expiry policy) while the app is running. Every subsequent call — library sync, favorite toggle, search, playlist mutations, lyrics — gets a generic, often silently-swallowed `Failure`. `AuthState` stays `LoggedIn` forever; there's no path back to the login screen except the user manually finding Settings → Sign Out → log back in themselves.

**Confidence:** 85. **Fix:** add a status-aware layer that detects `InvalidStatusException.status == 401` and forces `sessionStore.clear()` (or emits a distinct "session expired" result) so `AuthState` reflects reality.

---

### 25. Tapping the media notification always launches a brand-new `MainActivity` instance instead of resuming the running one
**File:** `core/playback/src/main/kotlin/com/pulsefin/core/playback/service/PlaybackService.kt:51-56`

```kotlin
val sessionActivity = PendingIntent.getActivity(
    this, 0,
    Intent().setComponent(ComponentName(packageName, "com.pulsefin.app.MainActivity")),
    PendingIntent.FLAG_IMMUTABLE,
)
```
`FLAG_IMMUTABLE` is correctly set (API 31+ compliant), but the `Intent` carries no `FLAG_ACTIVITY_REORDER_TO_FRONT`/`FLAG_ACTIVITY_CLEAR_TOP`/`FLAG_ACTIVITY_SINGLE_TOP`, and `MainActivity` declares no `android:launchMode` (defaults to `standard`).

**Failure scenario:** app is already running in the background (user was on `ArtistDetailScreen`, then backgrounded it). Tapping the media notification creates a **new** `MainActivity` instance on top of the existing task rather than bringing the running one to front — the old instance stays paused underneath with its own nav back-stack. Pressing system Back from the new instance lands on the stale old instance instead of exiting — a confusing duplicate-activity/double-back-stack, reproducible every time the notification is tapped while the app is already task-resident but not foregrounded.

**Confidence:** 85. **Fix:** declare `android:launchMode="singleTask"` on `MainActivity`, or add `FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP` to the notification's `Intent`.

---

### 26. `DownloadsScreen` only ever shows `COMPLETED` downloads — in-progress and failed downloads are invisible on the one screen dedicated to browsing them
**File:** `app/src/main/kotlin/com/pulsefin/app/ui/playlist/DownloadsScreen.kt:73-92`, specifically line 79:
```kotlin
downloads.filter { it.state == DownloadState.COMPLETED.name }
```
`DownloadState` has 5 values (`NONE, QUEUED, DOWNLOADING, COMPLETED, FAILED`), and the app already has a correct visual vocabulary for all of them elsewhere — `SongOverflowMenu.kt`'s `DownloadStateIndicator` shows a spinner for `QUEUED`/`DOWNLOADING` and an error icon for `FAILED`, used on Home/AlbumDetail/PlaylistDetail/Search rows. `DownloadsScreen` filters the combined download+library flow down to `COMPLETED` only, before the empty-state check even runs.

**Failure scenario:** user downloads an album for offline listening, opens Downloads to watch progress — sees the "no downloads" empty state the entire time anything is queued/in-flight, even with N active downloads. A failed download (bad network, storage full) never appears here at all — no stuck progress bar, no error row, nothing; it simply never surfaces unless it eventually succeeds. This is a total UI blackout for 2 of the 5 states on the one screen meant to show them all.

**Confidence:** 90. **Fix:** don't filter by `COMPLETED` — surface all non-`NONE` states using the existing `DownloadStateIndicator` treatment per row, same as every other screen already does.

---

### 27. `WavySeekBar` — the sole Now Playing seek/scrub control — has zero accessibility semantics and is effectively unusable via TalkBack
**File:** `app/src/main/kotlin/com/pulsefin/app/ui/player/WavySeekBar.kt:57-96`, used at `NowPlayingScreen.kt:354`

The seek bar is a hand-rolled `Box` with `detectTapGestures`/`detectHorizontalDragGestures` driving a purely visual `LinearWavyProgressIndicator` — no `Modifier.semantics{}`, no `progressBarRangeInfo`, no custom accessibility action, no `contentDescription` anywhere in the file.

**Failure scenario:** a TalkBack user reaches Now Playing and gets a wavy graphic with no announced role, current position, or duration, and no way to adjust it — TalkBack intercepts single-finger drags for its own navigation, so the drag-scrub path is unreachable. This is the app's primary seek control; there's no alternate way to seek to an arbitrary position.

**Confidence:** 85. **Fix:** add `Modifier.semantics(mergeDescendants = true) { progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f); contentDescription = ...; customActions = listOf(...) }`, or migrate to a Material3 `Slider`/`WavySlider` which gets this for free.

---

### 28. Every settings toggle row is a broken accessibility target — the switch's semantics aren't merged with its label, and only the tiny switch itself is clickable
**File:** `app/src/main/kotlin/com/pulsefin/app/ui/settings/SettingsScreen.kt:216-228` (`SwitchRow`), used 3× for dark theme, dynamic color, and haptics toggles

```kotlin
Row(... horizontalArrangement = Arrangement.SpaceBetween ...) {
    Text(text = title, ...)
    Switch(checked = checked, onCheckedChange = onCheckedChange)
}
```
No `.toggleable(role = Role.Switch)`/`.semantics(mergeDescendants = true)` on the `Row`. TalkBack treats the label and the switch as two independent nodes — focusing the switch announces only "Switch, on/off" with no indication of which setting it controls. Only the small `Switch` thumb itself is clickable; tapping the label text or the rest of the row does nothing, unlike the standard Material list-item-with-switch pattern.

**Confidence:** 78 (affects every settings toggle in the app). **Fix:** make the `Row` `Modifier.toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)` with `Switch(checked = checked, onCheckedChange = null)`, so semantics merge and the whole row becomes clickable.

---

### 29. A single item with an SDK-unrecognized enum value can fail the entire library sync, not just that item
**File:** `core/data/src/main/kotlin/com/pulsefin/core/data/repository/MediaRepositoryImpl.kt:107-139` (`refreshLibrary`, all four fetches inside one `coroutineScope`), `:443-467` (`fetchAllItems`, no per-page try/catch)

Verified via decompiled Jellyfin SDK sources: any non-decodable response throws `InvalidContentException` (wrapping a `SerializationException`); `BaseItemDto`'s enum-typed fields (`mediaType`, `locationType`, etc. — not just `Type`, which is constrained by the request filter) are plain closed Kotlin enums with no forward-compatible "unknown" member, so a server running a newer Jellyfin version than the pinned client SDK can return an enum value the client can't decode, for any item, in any of the four fetches. `fetchAllItems` has no try/catch around a page fetch, and `refreshLibrary` runs all four type-fetches concurrently inside one plain `coroutineScope { }` — structured concurrency means one bad item in *any* of the four throws and cancels the siblings too, failing the entire sync rather than just the one item or type.

**Confidence:** 60 (mechanism fully verified from source; requires the pinned SDK version to lag the connected server's enum vocabulary — plausible for a self-hosted Jellyfin instance that updates independently of this app). **Fix:** wrap each item's DTO→domain mapping in its own try/catch so one bad item is skipped/logged instead of failing the whole sync; use `supervisorScope` for the four fetches so one type's failure doesn't cancel the others.

---

## Medium

### 30. `PlaybackController.toDomainError()` misclassifies 403/5xx server errors as generic "Network" issues
**File:** `core/playback/src/main/kotlin/com/pulsefin/core/playback/controller/PlaybackController.kt:69-79`

```kotlin
private fun PlaybackException.toDomainError(): PlaybackError = when {
    httpCause?.responseCode == 401 -> PlaybackError.Auth
    ... == 404 -> PlaybackError.NotFound
    errorCode in NETWORK_ERROR_CODES || errorCode == ERROR_CODE_IO_BAD_HTTP_STATUS -> PlaybackError.Network
    else -> PlaybackError.Unknown
}
```
`ERROR_CODE_IO_BAD_HTTP_STATUS` fires for any non-2xx response the 401/404 branches didn't already catch — a 403 (item access revoked) or a 500/502/503 (server-side failure) both land on `PlaybackError.Network`, showing "Connection lost — check your network or server" when the network is actually fine. It also means these cases silently skip `retry()`'s auth-specific stream-URL re-resolution, which might otherwise have been the right move.

**Confidence:** 75. **Fix:** add an explicit branch for `403`/`500..599` distinct from genuine connectivity failure codes, with a "Server error" message.

---

### 31. `playlist_song_count` string hardcodes English plural — renders "1 songs" for singular counts
**File:** `app/src/main/res/values/strings.xml:100` — `<string name="playlist_song_count">%1$d songs</string>`, used in `PlaylistDetailScreen.kt`, `PlaylistsScreen.kt`, `DownloadsScreen.kt`. No `<plurals>` resource exists anywhere in the app's strings.

**Failure scenario:** any playlist with exactly 1 song, or a Downloads screen with exactly 1 downloaded item, displays "1 songs" — reachable in completely ordinary use.

**Confidence:** 92. **Fix:** convert to `<plurals name="playlist_song_count">` with `one`/`other` variants, called via `pluralStringResource`.

---

### 32. Queue reorder drag-handle has a 24dp touch target, well under the 48dp accessibility minimum
**File:** `app/src/main/kotlin/com/pulsefin/app/ui/player/QueueScreen.kt:233-237` — the `DragHandle` `Icon` has the long-press-drag gesture detector attached directly to its bare 24dp intrinsic size, with no wrapping `Box`/`minimumInteractiveComponentSize()`, unlike every `IconButton` elsewhere in the app which gets Material3's 48dp minimum for free. It's the only bare-`Icon`-with-gesture instance in the codebase.

**Failure scenario:** reordering the queue requires a long-press-then-drag precisely on a 24×24dp glyph — under the WCAG 2.5.5/Android touch-target guideline, and harder than normal since it also requires a long press before the drag registers.

**Confidence:** 85. **Fix:** wrap in `Modifier.size(48.dp)` (or `.minimumInteractiveComponentSize()`) centered on the glyph.

---

### 33. `YourMixScreen` — the app's default/first tab — has no empty-state message for a genuinely empty library
**File:** `app/src/main/kotlin/com/pulsefin/app/ui/home/YourMixScreen.kt:143-198`

Unlike `HomeScreen`/`AlbumsScreen`/`ArtistsScreen`/`PlaylistsScreen` (all have explicit empty branches), `YourMixScreen`'s `LazyColumn` only conditionally renders "Recently Added" and "Liked Songs" sections when non-empty, with no fallback message when both are empty. The `Hero` (title + Shuffle Play button) always renders regardless of whether there's anything to shuffle.

**Failure scenario:** a brand-new account with zero library content — this is the very first screen a new user sees — shows only a title and a Shuffle Play button that silently no-ops when tapped (guards on `all.isNotEmpty()`), no albums, no liked songs, no artwork, and no text anywhere explaining the library is empty. Looks broken rather than "empty."

**Confidence:** 80. **Fix:** add an explicit empty-state branch (mirroring `HomeScreen`'s pattern) when recent/favorites/allSongs are all empty; disable or hide Shuffle when there's nothing to shuffle.

---

### 34. `SearchScreen` bundles its entire UI state into one unscoped `mutableStateOf`, forcing the whole result list to redeclare on every keystroke
**File:** `app/src/main/kotlin/com/pulsefin/app/ui/search/SearchScreen.kt:82` (`var uiState by mutableStateOf(SearchUiState())`), read undestructured at the top of the single composable that also declares the `itemsIndexed` blocks for all three result categories (songs/albums/artists, lines 196-279)

Every keystroke does `uiState = uiState.copy(query = value)`. Since `query`, `isSearching`, and the three result lists all live in one state object read at the top of `SearchScreen`, a keystroke invalidates the entire composable — the `itemsIndexed` declarations for all three categories re-execute in full (iterating every result, doing a per-song download-state map lookup) even while the debounced search hasn't returned anything new yet. Individual row composables have narrow stable params so they still skip re-rendering their own content, but the list-walk itself repeats needlessly.

**Confidence:** 60 (mechanism verified; real-world cost scales with result-set size). **Fix:** split `query`/`isSearching` into their own narrowly-scoped state, or hoist the text field and the results list into separate composables each reading only the slice of `uiState` they need.

---

### 35. Pagination loops advance by the fixed page size, not by the actual number of items returned — can silently skip items on a short page
**File:** `core/data/src/main/kotlin/com/pulsefin/core/data/repository/MediaRepositoryImpl.kt:417-467` (`fetchAllItems`/`fetchAllPlaylistItems`)

```kotlin
while (true) {
    val page = api.itemsApi.getItems(GetItemsRequest(..., startIndex = startIndex, limit = pageSize)).content
    val items = page.items.orEmpty()
    if (items.isEmpty()) break
    out += items
    if (out.size >= page.totalRecordCount) break
    startIndex += pageSize   // not += items.size
}
```
Correctness silently depends on every non-final page returning exactly `pageSize` items. If any page (a server-enforced cap below the requested size, or a transient partial response) returns fewer while more remain, `startIndex` jumps past the ungotten items — permanently skipped from that sync with no error surfaced.

**Confidence:** 45 (mechanism is real and unguarded; whether a real Jellyfin server actually triggers it by returning short non-final pages is unconfirmed). **Fix:** `startIndex += items.size`.

---

## Low / Informational

- **`PlaybackScrobbler.playSessionId` is generated once per process and never regenerated on login/logout** (see finding #22's secondary note) — a logout→different-user-login within one process reports both users' playback history under one Jellyfin session id. Cosmetic on the server's "now playing" grouping, not user-facing. Confidence 55.
- **`formatDuration`/`formatTime` use `Locale.getDefault()`-sensitive digit formatting** (`HomeScreen.kt:187-192`, `WavySeekBar.kt:118-124`) — `"%d:%02d".format(...)` with no explicit locale can render native-script digit glyphs (Arabic-Indic, Devanagari, etc.) instead of ASCII on some device locales. Durations are conventionally rendered in Western digits regardless of locale in virtually every media app. Confidence 65. Fix: `String.format(Locale.US, "%d:%02d", ...)`.
- **Album/playlist track index number bypasses locale-aware formatting** (`AlbumDetailScreen.kt:319`, `Text("$index")`) — same underlying mechanism as above via template interpolation. Track-number columns conventionally render in Western digits regardless of locale in most music apps, so this is more a completeness nit. Confidence 55.
- **`kotlinx-collections-immutable` is declared in `libs.versions.toml` but wired into zero modules' `build.gradle.kts`** — dead version-catalog entry. The project instead uses a `compose_stability.conf` file marking `kotlin.collections.List` and domain model types as stable, which was checked and confirmed safe in practice (all `List<T>` params reaching composables use plain immutable data classes/String elements) — so this isn't a functional bug, just an unused dependency worth removing or actually wiring in.
- **`JellyfinClientFactory` uses the SDK's default HTTP timeouts** (connect 6s / request 30s / socket 30s) with no explicit configuration — reasonable defaults, not a bug, just unconfigured; noted in case a self-hosted server on a slow connection needs longer timeouts.

---

## Round 4 — Checked and found NOT to be a bug

- **Local search via Room `LIKE` queries** — no such queries exist; search is entirely server-side via `GetItemsRequest.searchTerm`, no local text-filtering DAO at all.
- **Retry/backoff bounding for transient network failures** — no automatic retry/backoff logic exists anywhere in `core/data`; the only `retry()` is user-triggered and already covered by finding #8 above.
- **Koin DI graph / ViewModel scoping across the whole app** — all ViewModels are registered via `viewModelOf` and obtained via bare `koinViewModel()`, correctly scoped to each `NavBackStackEntry`; no circular dependencies, no expensive eager `single{}` blocks beyond the already-documented `SessionStore` async populate (finding #3).
- **Compose stability configuration** — `compose_stability.conf`'s blanket `List`/domain-model stability override was checked against every `List<T>` parameter reaching a composable in `:app`; all element types are plain immutable data classes/String, so the override is safe. `core/designsystem` has no composable taking a `List<T>` param, so its lack of the same config file isn't presently reachable.
- **Media notification channel importance** — decompiled Media3 1.10.1's `DefaultMediaNotificationProvider` (used as-is, no custom channel code) always creates the channel at `IMPORTANCE_LOW`, correctly silent per Android media guidelines.
- **Notification metadata staleness / artwork bitmap-load exception handling** — `MediaItem.mediaMetadata` is built per-item at queue-construction time and updates atomically off `Player.onMediaItemTransition`, no separate async fetch; the app only ever calls `setArtworkUri(Uri)`, never loads a `Bitmap` itself, so a failed image fetch is Media3's internal concern, not a gap in this app's code.
- **Process-death / `SavedStateHandle` correctness for detail screens** — `PlaylistDetailScreen`/`AlbumDetailScreen`/`ArtistDetailScreen` all use `koinViewModel()` scoped to the `NavBackStackEntry` plus `LaunchedEffect(idArg) { load(idArg) }`; Nav-Compose's default `Saver` restores the back stack (including string args) from the Activity's `onSaveInstanceState` Bundle after process death, and `MainActivity` doesn't override/skip this. No stale-ID/blank-screen bug found.
- **Playback preferring a downloaded file over a network stream** — confirmed by design: both playback and downloads use the identical Media3 cache key (the song ID), and `PlaybackModule` wires one shared `SimpleCache` between `DownloadManager` and the playback `CacheDataSource.Factory`; a fully-downloaded song plays entirely from the shared cache, by design and by code comment.
- **Multi-window / foldable / split-screen layout** — `NowPlayingScreen`/`QueueScreen` (read in full) use `fillMaxSize`/`fillMaxWidth`/`weight`/`aspectRatio` throughout; the only fixed `dp` values are touch-target sizes, not layout constraints. No hardcoded container dimensions that would break in split-screen.
- **HomeScreen/YourMixScreen internals beyond the empty-state gap (#33)** — "Recently Added" sort order, shuffle logic, and click-by-id-at-click-time pattern are all correct; no other stale-data or wrong-selection bug found.
- **DownloadsScreen's cancel-download button** — correctly calls through to the repository's remove path; no storage-full-specific handling exists anywhere in the UI, consistent with finding #26 (a storage-full failure just becomes an invisible `FAILED` entry).
- **RTL layout, text scaling, and hardcoded-string usage across the whole UI tree** — all alignment/padding consistently uses `Start`/`End`, back arrows use the `AutoMirrored` icon variants, all typography comes from `MaterialTheme.typography` with no fixed-size overrides, and virtually all user-facing text routes through `stringResource` (only the one hardcoded track-index case above). No RTL, text-scaling, or i18n-bypass bug beyond what's already reported.
- **Content descriptions across ~90+ `Icon`/`IconButton`/`Image` call sites** — every `contentDescription = null` instance checked is genuinely decorative (art with an adjacent text label, redundant status icons); nothing missing beyond the two semantics gaps already reported (#27, #28).
- **Negative-duration and >99-minute formatting** — both duration formatters correctly guard `ms <= 0` and render minutes unbounded with no truncation/wraparound; only the locale-glyph issue (Low/Informational above) is real.
