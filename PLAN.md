# Offline Music Player — Implementation Plan

**Target:** Native Android app, Kotlin + Jetpack Compose + Media3 (ExoPlayer)
**Core constraint:** Local-first. The on-device library works fully offline and needs no account. Network access is used for one thing only: YouTube Music streaming (see §1). No telemetry, no ads, no accounts.
**Status:** In build — `c:\music-player`

> **Scope change (2026-10-14):** the original plan mandated 100% offline with no
> `INTERNET` permission. That constraint has been lifted: YouTube Music streaming
> is now in scope, and the manifest carries `INTERNET`, `ACCESS_NETWORK_STATE` and
> `ACCESS_WIFI_STATE`. The sections below reflect the revised scope; anything
> describing a zero-network build is superseded by this note.

> **Assumption:** "Kotlin music player" = Android. If you meant Compose Desktop or Kotlin Multiplatform instead, see [Appendix B](#appendix-b--if-the-target-isnt-android).

---

## 1. Product scope

### What it does
Scans the device for audio files, presents a browsable library, and plays music reliably in the background — including with the screen off, from the lock screen, and over Bluetooth headphones.

### v1.0 feature set (the release bar)

| Area | Included |
|---|---|
| **Library** | Scan via MediaStore; browse by Songs, Albums, Artists, Genres, Folders |
| **Playback** | Play/pause/next/prev, seek, shuffle, repeat (off/all/one), queue with reorder |
| **Background** | Foreground service, MediaStyle notification, lock screen controls, Bluetooth/AVRCP, headset buttons |
| **Playlists** | Create, rename, delete, add/remove/reorder tracks; local only |
| **Metadata** | Title/artist/album/track#/year/duration, embedded album art |
| **Quality of life** | Search, sleep timer, playback speed, favorites, recently played |
| **Audio** | Audio focus handling, "becoming noisy" (unplug → pause), gapless playback |
| **Streaming** | ⚠️ **Unresolved — see note below.** Search works; playback resolves nothing. Streamed tracks do live beside local ones in the library, playlists and queue. |
| **UI** | Material 3, opt-in dynamic color, now-playing screen + mini player |

> **⚠️ OPEN DECISION — streaming source (2026-10-14).** The InnerTube layer is
> built and search is healthy, but **stream resolution currently fails for every
> track**. All three unattested client profiles (`ANDROID_VR`, `ANDROID_MUSIC`,
> `IOS_MUSIC`) now return `LOGIN_REQUIRED`; `WEB_REMIX` requires a PoToken and
> `WebViewPoTokenProvider.mint()` returns `null` unconditionally, so it is sent
> unattested and returns `UNPLAYABLE`; `TVHTML5_SIMPLY_EMBEDDED_PLAYER` is
> deprecated by Google. The design assumed unattested mobile clients would carry
> playback with attestation as an upgrade — that assumption expired.
>
> **The discriminator is the client stack, not the network.** The byte-identical
> request — same machine, same IP, minutes apart, every header permutation
> including a minted `visitorData` — returns `status=OK` with four audio formats
> from Node, and `LOGIN_REQUIRED` from the app. That rules out IP reputation,
> headers and `visitorData`. What is left is TLS fingerprinting: Android's
> OkHttp/Conscrypt handshake does not match the Quest VR app the request claims
> to come from, and Google fingerprints TLS for bot detection. No header tuning
> fixes this; defeating it is precisely what attestation exists for.
>
> There is no sanctioned fix either: the official YouTube Data API returns
> metadata and search results but no audio stream URLs. The realistic choices are
> (a) keep YouTube as best-effort and make the failure legible, (b) point
> `StreamResolver` at a source with a real licensed API (Jamendo, Free Music
> Archive, Internet Archive), or (c) authenticate with a personal Google account —
> which stakes a real account on a ToS violation and is not recommended.
>
> **Until this is settled, the Streaming row above and the streaming line in §8
> are provisional.** Nothing else in this document depends on the outcome: the
> local library is unaffected, which is the §7 mitigation working as intended.

### Explicitly out of scope for v1
Cloud sync, lyrics fetching, scrobbling, online metadata/art lookup for *local* files, podcasts, video, cast/DLNA, equalizer.

> **On the UI row:** Pulse is a deliberately dark single-look theme — the violet
> bloom and glass surfaces only read against a dark canvas — so there is no light
> theme and the app does not follow the system light/dark setting. Dynamic color
> is offered as an opt-in and is wallpaper-derived *dark* only.

### Deferred to v1.1+
Equalizer (`android.media.audiofx.Equalizer`), crossfade, home-screen widget, Android Auto, tag editor, folder-level blacklist, playlist import/export (`.m3u`), Wear OS.

---

## 2. Technology decisions

| Concern | Choice | Rationale |
|---|---|---|
| Language | Kotlin 2.x | Target |
| UI | Jetpack Compose + Material 3 | Declarative, less boilerplate than Views, first-class dynamic color |
| Playback | **AndroidX Media3** (`media3-exoplayer`, `media3-session`, `media3-ui`) | The current supported API. `MediaSessionService` handles notification, lock screen, and Bluetooth for you. Do **not** use the deprecated `android.media.MediaPlayer` or standalone ExoPlayer 2. |
| Local DB | Room | Library cache, playlists, favorites, play counts. Survives cold start and beats re-querying MediaStore every launch. |
| DI | Hilt | Standard, low ceremony, integrates with `ViewModel` and `Service` |
| Async | Coroutines + Flow | Room and DataStore both expose Flow natively |
| Prefs | DataStore (Proto or Preferences) | SharedPreferences is legacy |
| Images | Coil 3 | Compose-native; loads embedded art from a content URI. **Note:** remote (`https://`) artwork needs a separate network fetcher (`coil-network-okhttp` or `-ktor3`) which is **not currently declared**, so streamed album art never loads. |
| Navigation | ~~Navigation Compose~~ — **not used** | The dependency is declared but there is no `NavHost`. Navigation is a `HomeDestination` enum switched on in `MusicPlayerApp`. Fine at five destinations; revisit if deep links or a back stack are needed. |
| Build | Gradle Kotlin DSL + version catalog (`libs.versions.toml`) | Single source of truth for versions |
| Min SDK | 24 (Android 7.0) | ~97% device reach; below this Media3 support gets painful |
| Target SDK | Latest stable | Play Store requirement |

> **Pin versions in `gradle/libs.versions.toml` and verify the latest stable releases at setup time** — don't copy version numbers from this document.

---

## 3. Architecture

Single-module to start (`:app`), split into feature modules only if build times justify it. Premature modularization is the most common way these projects stall.

```
UI (Compose screens)
  ↕ StateFlow / events
ViewModels
  ↕ suspend fns / Flow
Repositories  ──────────┬──────────────┐
  │                     │              │
Room DAOs         MediaStore       Media3 Player
(playlists,       (device audio    (MediaController
 favorites,        scan)            → MediaSessionService)
 cache)
```

**Key rule:** the UI never touches `ExoPlayer` directly. It talks to a `MediaController` (from `media3-session`), which is a thin IPC proxy to the playback service. This is what makes background playback and process death behave correctly.

### Package layout

```
com.example.musicplayer
├── MusicApp.kt                    // @HiltAndroidApp
├── MainActivity.kt
├── core/
│   ├── data/
│   │   ├── db/                    // Room: entities, DAOs, MusicDatabase, migrations
│   │   ├── mediastore/            // MediaStoreScanner
│   │   ├── innertube/             // InnerTube client profiles, stream resolution, PoToken
│   │   ├── prefs/                 // DataStore wrapper
│   │   └── repository/            // SongRepository, PlaylistRepository, YouTubeMusicRepository
│   ├── model/                     // Song, Album, Artist, Playlist (domain types)
│   └── di/                        // Hilt modules
├── playback/
│   ├── PlaybackService.kt         // : MediaSessionService
│   ├── PlaybackConnection.kt      // MediaController lifecycle + state Flows
│   ├── MediaItems.kt              // shared Song -> MediaItem mapping
│   ├── SleepTimerController.kt    // app-scoped; must outlive any ViewModel
│   └── YouTubeStreamDataSource.kt // resolves ytmusic:// at open() time
├── ui/
│   ├── theme/
│   ├── components/                // MiniPlayer, SongRow, ArtworkImage, GlassSurface
│   ├── app/                       // MusicPlayerApp.kt — every screen lives here
│   ├── library/                   // LibraryViewModel
│   ├── playback/                  // PlaybackViewModel
│   └── search/                    // SearchScreen + SearchViewModel
└── util/
```

### Data model sketch

```kotlin
// Cached mirror of MediaStore + app-owned extras
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: Long,        // MediaStore._ID
    val uri: String,                 // content:// URI — never a raw file path
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val albumArtUri: String?,
    val genre: String,               // MediaStore GENRE column; API 30+ only
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int,
    val dateAdded: Long,
    val dateModified: Long,          // incremental rescan key
    val relativePath: String,        // for folder browsing
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
    val source: SongSource = SongSource.LOCAL,   // LOCAL | YOUTUBE
    val remoteId: String? = null,                // videoId when source is YOUTUBE
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

// Junction table with explicit ordering — playlists are ordered, sets are not
@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [/* cascade delete from both parents */]
)
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: Long,
    val position: Int,
)
```

**Persist `content://` URIs, not file paths.** Scoped storage means raw paths are unreliable from Android 10 onward.

---

## 4. The hard parts (read before estimating)

These four are where offline music players actually break. Budget real time for them.

### 4.1 Storage permissions fragment across API levels
- **API ≤ 32:** `READ_EXTERNAL_STORAGE`
- **API 33+:** `READ_MEDIA_AUDIO`
- **API 34+:** partial media access ("Select photos and videos") can apply — handle the case where the user grants access to *some* files
- Requires a runtime rationale flow and a graceful "permission denied" empty state that links to app settings.

### 4.2 Foreground service restrictions
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permissions; declare `android:foregroundServiceType="mediaPlayback"` in the manifest.
- **API 33+:** `POST_NOTIFICATIONS` is a runtime permission. Without it the media notification is invisible and playback controls feel broken.
- **API 31+:** you cannot start a foreground service from the background. Playback must start from a user-visible interaction.
- Manufacturer battery optimizers (Xiaomi, Huawei, Samsung, OnePlus) kill background services aggressively. Plan a settings screen entry that deep-links to battery-optimization exemption.

### 4.3 Library scanning performance
A 10,000-track library will make a naive implementation janky.
- Query MediaStore off the main thread with a **projection** limiting columns to only what's needed.
- Do a full scan on first launch; afterwards do incremental diffs keyed on `DATE_MODIFIED`.
- Paginate the UI with Paging 3 or `LazyColumn` over a Room `Flow<List<...>>` — never load the whole library into a single list in memory.
- Rescan trigger: manual pull-to-refresh in v1. Consider a `ContentObserver` on the MediaStore audio URI in v1.1.

### 4.4 Audio focus and interruptions
Media3 handles most of this via `setAudioAttributes(attrs, handleAudioFocus = true)`, but verify by hand:
- Incoming call → pause, resume after
- Another app plays audio → pause
- Navigation prompt → duck (lower volume), then restore
- Headphones unplugged / Bluetooth disconnect → pause (`ACTION_AUDIO_BECOMING_NOISY`)

---

## 5. Build phases

Nine phases. Each ends at a state you can actually run and check.

### Phase 0 — Project setup *(~0.5 day)*
- Android Studio project, Compose + Kotlin DSL, min SDK 24
- Gradle version catalog; add Hilt, Room, Media3, Coil, Navigation, DataStore
- Package skeleton, `MusicApp` with `@HiltAndroidApp`, Material 3 theme with dynamic color
- **Done when:** empty themed app builds and launches on a device.

### Phase 1 — Permissions & MediaStore scan *(~1.5 days)*
- Version-aware permission request flow + rationale + denied state
- `MediaStoreScanner`: query `Audio.Media.EXTERNAL_CONTENT_URI`, filter `IS_MUSIC != 0`, filter out clips under ~30s
- Map cursor rows → `SongEntity`
- **Done when:** a debug screen lists every track on the device.

### Phase 2 — Room persistence *(~1 day)*
- Entities, DAOs, `MusicDatabase`, Hilt module
- `SongRepository` exposing `Flow<List<Song>>` with sort options
- Scan-on-first-launch + incremental diff; upsert new, delete missing
- **Done when:** library loads instantly from cache on second launch, offline, airplane mode on.

### Phase 3 — Playback service *(~2 days — the critical path)*
- `PlaybackService : MediaSessionService` holding an `ExoPlayer`
- Manifest: service declaration, `mediaPlayback` FSG type, permissions
- `PlaybackConnection`: binds a `MediaController`, exposes `StateFlow` for current item / isPlaying / position / queue
- Audio attributes + focus handling, becoming-noisy handling
- **Done when:** a track plays, keeps playing with the app backgrounded and screen off, and is controllable from the lock screen.

### Phase 4 — Library UI *(~2 days)*
- Tabbed library: Songs / Albums / Artists / Genres / Folders (the pill row scrolls — five no longer fit a phone width)
- `LazyColumn` / `LazyVerticalGrid`, sort and filter controls
- Album art via Coil from the album art content URI, with a placeholder
- Tapping a song sets the queue and starts playback
- **Done when:** you can browse and play from any tab.

### Phase 5 — Now Playing + mini player *(~2 days)*
- Persistent mini player above the bottom nav; expands to full now-playing
- Full screen: large art, seek bar with scrubbing, transport controls, shuffle/repeat
- Queue bottom sheet with drag-to-reorder and swipe-to-remove
- **Done when:** full playback control without leaving the app.

### Phase 6 — Playlists, favorites, search *(~2 days)*
- Playlist CRUD, add-to-playlist sheet, reorderable track list
- Favorite toggle; auto-generated "Recently Played" and "Most Played"
- Search across title/artist/album with debounced query, backed by a Room `LIKE` query (or FTS4 if it's slow)
- **Done when:** playlists persist across restarts and search returns results as you type.

### Phase 7 — Polish *(~2 days)* — *largely done*
- ~~Sleep timer, playback speed, settings screen~~ — done. The sleep timer is
  app-scoped, not in a ViewModel: the service outlives the activity, so a
  countdown in `viewModelScope` dies on swipe-away while the music plays on.
- Gapless playback verification
- Empty states, loading skeletons, error states
- ~~Screen-rotation and process-death state restoration~~ — done
- ~~Playback position restored on app relaunch~~ — done; queue + index + position
  + shuffle/repeat persist to DataStore and restore **paused**, never auto-playing
- Remaining: loading skeletons, and the user-visible half of the playback error
  path (see §7)

### Phase 8 — Test & release prep *(~2 days)* — *started*
- Unit tests: repositories, scanner mapping, queue logic *(19 passing: mediaId
  round-trip, stream expiry margin, itag ordering, client profiles)*
- ~~Room migration tests~~ — 3 passing against `MIGRATION_1_3`, covering
  user-owned column survival, index creation and playlist-membership cascade
- Compose UI tests for the main flows
- Manual matrix: API 24 / 26 / 30 / 33 / 34+, small + large library, Bluetooth, wired headset, call interrupt
- R8/ProGuard rules, signing config, app icon, adaptive icon, store listing
- **Done when:** a release build runs clean on every device in the matrix.

**Rough total: 15–18 working days** for a solid v1 by one developer. Phase 3 is the one that overruns; the rest are predictable.

---

## 6. Testing strategy

| Layer | Tooling | What to cover |
|---|---|---|
| Unit | JUnit 5, MockK, Turbine, `kotlinx-coroutines-test` | Repository logic, MediaStore cursor→model mapping, queue/shuffle/repeat state machine, sleep timer |
| DB | Room in-memory + `MigrationTestHelper` | DAO queries, cascade deletes, playlist reordering, every schema migration |
| UI | Compose UI Test | Library list renders, tap-to-play, mini player expand, playlist creation |
| Manual | Physical devices | Everything in §4 — audio focus, service survival, permission flows. These cannot be meaningfully automated. |

Keep the schema exported (`room.schemaLocation`) and commit it. Migration bugs on a user's only copy of their playlists are unrecoverable.

---

## 7. Risk register

| Risk | Impact | Mitigation |
|---|---|---|
| Service killed by OEM battery manager | High — playback stops silently | Correct FGS type; document the battery-exemption path in settings; test on Xiaomi/Samsung |
| Permission model shifts again in a new API level | Medium | Isolate all permission logic behind one `PermissionManager`; never scatter `checkSelfPermission` through the UI |
| Large-library jank | Medium | Paging + projections + off-main-thread scanning from Phase 1, not retrofitted later |
| Media3 API churn between versions | Low | Pin versions; wrap the player behind `PlaybackConnection` so the surface area to update is small |
| Corrupt/DRM/unsupported files crash playback | Medium | `Player.Listener.onPlayerError` → skip to next, surface a message, never crash. **Resolved:** skipping is now bounded (`MAX_CONSECUTIVE_SKIPS`) so the error survives for controllers to observe; the UI distinguishes breaker-paused, streaming-unavailable and single-track failures. Previously the immediate `seekToNextMediaItem()` + `prepare()` cleared the error before anything could see it, and the queue burned through silently. |
| Scope creep (lyrics, equalizer, cast) | High — the usual killer | v1 scope in §1 is fixed. Everything else waits for v1.1. |
| `@Insert(REPLACE)` on the songs table | High — silent playlist loss | SQLite resolves REPLACE as DELETE+INSERT, firing `ON DELETE CASCADE` on `playlist_songs`, so every rescan emptied all playlists. Fixed by `@Upsert`; keep a regression test on it. |
| ~~Streaming layer breaks on a YouTube change~~ — **MATERIALISED 2026-10-14** | Total — no track resolves on any client profile | The mitigation held: resolution stayed behind `StreamResolver` and local playback is entirely unaffected. The containment worked; the feature did not. See the open-decision note in §1. |
| Signed stream URLs expire (hours, IP-bound) | Medium — restored queues fail to play | Persist `ytmusic://<videoId>` placeholders, never resolved URLs; resolve at `open()` time. |

---

## 8. Definition of done for v1.0

- [ ] Plays local audio with the screen off for 30+ minutes without interruption
- [ ] Lock screen and Bluetooth controls work: play/pause/next/prev
- [x] Tapping the media notification or lock screen card reopens the app *(verified: `contentIntent` is a real `startActivity` PendingIntent; it was `null`)*
- [ ] Library loads in under 1s from cache on a 5,000-track device
- [ ] Playlists and favorites survive force-stop and **reboot** *(force-stop verified; reboot not yet tested)*
- [x] Queue and playback position are restored after process death and reboot *(verified: force-stop at 21353ms → relaunch at 21353ms, paused)*
- [ ] Permission flows work correctly on API 30, 33, and 34+
- [ ] Handles call interrupt, headphone unplug, and focus loss to another app
- [x] Sleep timer still stops playback after the app is swiped from recents *(verified: activity destroyed, same pid, timer kept counting)*
- [ ] ⚠️ **Provisional — blocked on the §1 open decision.** Streamed playback recovers from an expired stream URL without killing the queue. *Unsatisfiable as written today: nothing resolves at all, so expiry is the lesser problem.*
- [x] A failed track tells the user why, instead of the queue skipping in silence *(verified: session reports `ERROR(7)`; snackbar shown; 1 resolution failure instead of 24. Distinguishes breaker-paused / streaming-unavailable / single-track cases)*
- [x] Remote artwork loads *(verified: covers render in search results, mini player and nav)*
- [x] A rescan never empties a playlist *(regression reproduced on device — mixed playlist went 2 → 1 — then fixed by diffing instead of clear-and-reinsert; 5 instrumented tests, confirmed non-vacuous)*
- [x] **The local library is fully usable in airplane mode** *(verified: cold launch, browse, rescan and local playback all work with the radio off)*
- [ ] No crashes across the full manual device matrix

---

## Appendix A — First commands

```bash
cd c:\music-player
git init
# Create the project in Android Studio: New Project → Empty Activity (Compose)
#   Package: com.example.musicplayer
#   Min SDK: 24 | Build config: Kotlin DSL
```

Then work through Phase 0. First real code file: `gradle/libs.versions.toml`.

---

## Appendix B — If the target isn't Android

**Compose Desktop (Windows/Mac/Linux):**
Phases 3 and 4 change substantially. No MediaStore — walk the filesystem yourself and parse tags with **JAudiotagger**. No Media3 — use **VLCJ** (bundles LibVLC, broad codec support) or **JavaFX MediaPlayer** (fewer formats). No foreground service concerns, which removes most of §4. Room works on desktop via Room KMP, or swap in SQLDelight. Roughly the same total effort; the risk profile moves from platform restrictions to codec/packaging issues.

**Kotlin Multiplatform (shared Android + Desktop/iOS):**
Share `core/model`, `core/data/repository`, and the DB layer (SQLDelight or Room KMP). Playback and file scanning stay platform-specific behind an `expect`/`actual` interface. Adds roughly 30–40% to v1 effort — worth it only if a second platform is genuinely committed, not merely hoped for.

Say which one you want and I'll rewrite the affected phases.
