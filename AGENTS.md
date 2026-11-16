# Working on Aria

Notes for whoever (human or AI agent) picks this up next. Read this before changing anything.
`README.md` is the user-facing overview; this file covers how the project is built, how it's laid out,
what the owner has decided, and what has already been tried.

## Quick start (new session)

1. Read this whole file. `CLAUDE.md` just includes it, so Claude Code / Cowork load it automatically.
2. `git pull`. Work on `main`.
3. Make the change. Keep it lean, follow "Things to know" for the area you touch.
4. Commit with a user-facing subject (it becomes the release note), push, and watch CI (see Building).
5. Download the APK, run `tools/verify_apk.py`, then tell the owner the version and what to look for.
6. If anything here turned out wrong or you learned something the next agent needs, update this file
   in the same push.

## The product

Aria is a lean, native Android music player for **local files**. The owner uses it every day on a
OnePlus (OxygenOS, Android 16) and adds features only when daily use calls for them.

Principles the owner has stated:

- **No slop features.** If it won't be used weekly, it doesn't go in. Ask before adding
  anything not requested.
- **Polish over breadth.** Smooth motion, no jank, no flashes of the wrong content, no layout jumps.
- **Latest Material.** Material 3 Expressive (`MaterialExpressiveTheme`, `MotionScheme.expressive()`,
  `ShortNavigationBar`, `LoadingIndicator`, expressive button shapes). AMOLED black by default.
- **The widget matters a lot.** "All the existing music players have very bad widgets." Treat
  widget regressions as top priority.
- **No playlists** (they can't sync with other apps). Future maybe: online streaming and downloads.

## How the owner works with you

- Every push to `main` is built by CI and published as a GitHub release `v1.0.<n>` (version scheme in
  `gradle.properties`: `aria.version` + run number minus `aria.versionBase`; 1.0.0 shipped 2026-11-15,
  the owner's "daily driver" milestone). Commit subjects become the release notes. The owner installs from the Releases page and reports back,
  often with screenshots or screen recordings. **Ship by pushing to `main`**; no PRs or branches needed.
- Commit subjects are user-facing (they become release notes). Write them as plain outcomes:
  "Queue opens without a blank pause", not "refactor QueueSheet".
- Keep replies short: what changed, what to look for, anything you couldn't verify.
- The owner's feedback is precise. If something was reverted, it stays reverted (see "Tried and reverted").

## Building

There is usually **no Android SDK in the agent sandbox**: CI is the compiler.

1. Push to `main`.
2. Watch: `gh run list -R savar95x/aria-player -L 1 --json status,conclusion,databaseId`.
3. On failure: `gh run view <id> -R savar95x/aria-player --log-failed | grep -E "e: |error:"`.
   CI also publishes the log as a `ci-log` pre-release.
4. On success, the new tag: `gh api repos/savar95x/aria-player/releases/latest --jq .tag_name`.

`gh` GraphQL is blocked in agent sessions, so `gh release list/download` fail. Use the REST
API (`gh api repos/...`). `tools/verify_apk.py` shows how to download the APK.

CI (`.github/workflows/build.yml`): JDK 17, `./gradlew assembleRelease`; on failure it posts the
errors as an annotation and a `ci-log` pre-release; on success on `main` it deletes `ci-log` and
creates release `v<aria.version>.<run − aria.versionBase>` with `aria.apk` and the commit subjects since
the previous tag as notes. A newer push cancels a running build (concurrency group).

Agent sessions can create commits and read releases, but **editing a release returns 403**. If the
owner wants hand-written notes (e.g. for a milestone), write them to a file and hand it over.

**With a local SDK** (e.g. Cowork on the owner's machine, or Android Studio): JDK 17+, Android SDK
with platform 37, then `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
(signed with the shared keystore, installs over the release builds). `adb install -r` it to test.
Still push to `main` afterwards; the owner installs from Releases.

Before telling the owner a build is ready, run `python3 tools/verify_apk.py aria.apk`. It catches:
- manifest components that R8 stripped;
- calls into library methods that don't exist (version mismatches).

Toolchain (all deliberately current; see gotchas before changing):

| | |
|---|---|
| AGP | 9.1.1, **built-in Kotlin**: no `kotlin-android` plugin, no `kotlinOptions` block |
| Gradle | 9.3.1 |
| Kotlin | 2.4.10, pinned through `buildscript` classpath in the root `build.gradle.kts` |
| SDK | compileSdk 37 (required by Compose 1.12), targetSdk 35, minSdk 31 |
| Compose | BOM 2026.08.00, material3 1.5.0-alpha (Expressive APIs). No Navigation-Compose (own `Pages`) |
| Media3 | 1.5.1 (session + exoplayer + hls) |
| Coil | 3.6.3 with `coil-network-okhttp` |

Signing: a shared keystore (`app/aria.keystore`, password in `app/build.gradle.kts`), so every
build installs over the previous one. `versionCode` = CI run number (always increasing); `versionName` = 1.0.<n>.

Versioning: `gradle.properties` holds `aria.version=1.0` and `aria.versionBase=52`. To start 1.1 (or 2.0),
set `aria.version=1.1` and `aria.versionBase` to the **next** CI run number (last run + 1), so the
first build of it is 1.1.0. A failed run still consumes a run number. Only bump when the owner asks.

`*.apk` is git-ignored; download it to the repo root for verification without worrying about committing it.

## Layout

```
app/src/main/java/dev/savar/aria/
  AriaApp.kt           Application + AppContainer (manual DI: settings, library, queueStore, lyrics,
                       artistPhotos), Coil ImageLoader with SongArt fetcher/keyer, crash handler install
  MainActivity.kt      Single activity, edge-to-edge, hosts AriaRoot
  CrashActivity.kt     Plain-View crash screen in a separate ":crash" process (shows the stack trace
                       so the owner can paste it). Installed in attachBaseContext, before library init.
  playback/
    PlaybackService    Media3 MediaSessionService (single process). Restores the saved queue on start,
                       handles custom commands (Remote.kt), pushes widget updates, onPlaybackResumption
    Remote.kt          Custom SessionCommands (widget actions, sleep timer) + session-extras encoding
    SleepTimer.kt      Fade-out or end-of-song timer
    SongArtBitmapLoader  Notification artwork from embedded art
  data/
    MediaStoreQuery    Songs from MediaStore (+ folder rules in Folders.kt)
    MusicRepository    songs/albums/artists StateFlows. Artists tab groups "A/B", "A feat. B" etc under
                       the main artist (mainArtist()); ONLY the Artists tab and artist navigation do this
    Models.kt          Song/Album/Artist, sort enums (SongSort/AlbumSort/ArtistSort), SongArt (Coil model)
    EmbeddedArt/SongArt  Embedded artwork only (MediaMetadataRetriever). Never folder/online art
                       (that showed wrong covers). SongArt.sizePx is part of the cache key.
    Lyrics.kt          File tags first, then LRCLIB (/api/get needs album+duration, else /api/search);
                       cached in filesDir/lyrics
    ArtistPhotos.kt    Deezer (matched through the user's own albums) + Wikipedia; user overrides;
                       files in filesDir/artists (format documented in the class)
    SettingsStore      DataStore prefs (theme, AMOLED, dynamic colour, Google Font + text size/weight/
                       letter spacing, sorts, folders, pinned albums, corner roundness)
    FontCatalog.kt     All Google Fonts (fonts.google.com/metadata/fonts), cached weekly in filesDir;
                       falls back to a featured list offline
    QueueStore         Saved queue + position (restored at service start and by the widget)
    Links.kt           "Play a link" (http audio, HLS, radio). Media ids prefixed "url:"
  metadata/            iTunes Search lookup + jaudiotagger tag writer (tag editor screen)
  widget/
    AriaWidget.kt      RemoteViews widget (small / wide / tall layouts in res/layout/widget_*.xml)
    WidgetState.kt     What the widget shows; rebuilt from QueueStore after a cold start
    WidgetUpdateReceiver  player -> widget broadcast
    WidgetActionReceiver  widget buttons -> player (direct call if running, else MediaController)
  ui/
    Pages.kt           Own navigator: back stack, PageHost, transitions (see Navigation below)
    AriaRoot.kt        Routes + Pages (own navigator, see Pages.kt), dock (mini player + ShortNavigationBar), now-playing overlay with
                       predictive back, lyrics state (back closes lyrics first)
    PlayerViewModel    MediaController wrapper; physical shuffle (reorders the real queue around the
                       current item, keeps the original order for un-shuffle)
    Motion.kt          Motion tokens, sharedArt/sharedContainer helpers, verticalSwipe
    theme/             Theme (Expressive), Fonts (downloadable Google Fonts; catalogue in data/FontCatalog), Corners
    components/        ArtImage, ArtistImage, SongRow, FastScroller...
    screens/           Library (home tabs, album, artist, search), Player (now playing, mini player,
                       seek bar), QueueSheet, LyricsPane, TagEditor, Settings, Fonts (font page +
                       Typeface picker), ArtistPhotoViewer
tools/verify_apk.py    Static APK checks (see Building)
docs/                  README images (hero.png is a composite made with Pillow from docs/screenshots)
```

## Things to know before touching an area

**Corner roundness.** Settings has a 0–2× roundness slider (`LocalCornerScale`). Never write a raw
`RoundedCornerShape(n.dp)` in UI code. Use `rounded(n.dp)` / `cornerDp(n.dp)` from
`ui/theme/Corners.kt`. Material's own `Shapes` are scaled in `AriaTheme`. Artwork that morphs
between screens (mini player ↔ now playing, album tile ↔ album page) uses `playerArtShape()` /
`albumArtShape()`: the radius follows the item's size, so it eases during the shared-element
transition instead of snapping. Pills and circles (all Buttons, search bar, segmented buttons) use `pill()`: fully round at default, square at 0. Material's own buttons default to a fixed
full shape, so always pass `shape = pill()` (or `ButtonDefaults.shapes(shape = pill())`). Inside
`graphicsLayer {}` lambdas (not composable), compute the shape outside first.
Exceptions the owner asked for: **artist photos are always circles** (`CircleShape`, ignore the slider);
the big **play/pause button** uses `playButtonCorner()`: unscaled Material morph (26dp paused ↔ 40dp
playing) from 50% up, one fixed `26dp × scale` shape below 50% (a small-radius morph looked like a glitch).

**Shared elements.** Keys: `now-playing-art`, `now-playing-art-mode` (big ↔ lyrics header),
`album-<id>`, `artist-<name>`. `sharedElement` remeasures content to the animated bounds.
The artist photo viewer is **not** a Dialog (its window fade was the complaint): AriaRoot draws it over
everything, and it does its own container transform from the header circle's `positionInRoot` bounds
(header hidden while it's open; back closes it with the reverse animation).

**Navigation (ui/Pages.kt).** No Navigation-Compose. Modelled on Fragment transitions in Retro/Metro:
the instant you navigate, the page you're leaving stops being touchable and only animates out; the
destination is live and on top from its first frame. `PageHost` gives every on-screen page its own
`Transition.AnimatedVisibility` (driven by one `SeekableTransitionState`, so predictive back seeks it),
composes the destination last (on top), and puts a touch-swallowing layer over every non-destination page.
Popped pages sit in `Pages.leaving` until their exit finishes. Page state is kept per visit with
`SaveableStateHolder`. History: NavHost delivered taps during a back animation to the page sliding away
(tapping an artist in the list played a song from the closing artist page) and jammed when back was
pressed mid-animation; only reordering z (AnimatedContent `targetContentZIndex`) did NOT fix the taps.
**Never block or debounce input on the destination**: the owner compares against Metro/Retro, where you
can tap through any transition. Earlier guards (RESUMED-only taps, whole-screen touch-eating overlay,
a 400ms debounce) froze the app or swallowed taps.

**Routes** (AriaRoot): `home`, `search`, `album/<id>`, `artist/<Uri-encoded name>`, `settings`, `fonts`,
`typeface`, `folders`, `tags/<songId>`. Always open artists through `library.mainArtist(name)` so
"A feat. B" lands on A. From now playing, `onOpenArtist` / `onOpenAlbum` close the player first.

**Fonts.** Settings → Font is one scrolling page: live preview, a Typeface row (opens the separate
`typeface` page: pinned search + category chips over a list of every Google Font), and Text style
sliders: size 0.8–1.3×, weight −300..+300, letter spacing −5..+5 px (smooth, snaps to 0 near 0).
`typographyFor(font, textScale, weightDelta, letterSpacing)` in `ui/theme/Fonts.kt` builds the
Typography; downloadable fonts request weights 100–900 so the weight slider works. "Reset to
defaults" lives in the ⋮ menu behind a confirmation dialog (the owner didn't want it one tap away).

**Seek bar.** Holds at 0 until the new song's duration is known (otherwise it races to the end on skip).

**Queue sheet.** Rows are keyed by song id + occurrence (not queue position), the local list is never
replaced mid-drag, and a swap waits for the previous one to be laid out. Fixed height, list composed one frame after the sheet starts, art pre-decoded while
the player is open. It stuttered when the list was built during the slide. If it stutters again,
the next step is an in-window sheet instead of `ModalBottomSheet` (which opens a new window).

**Widget.** Plain RemoteViews on purpose. It used Glance 1.1.1, which pulled in WorkManager 2.7.1
and its Room database. That broke twice (start-up crash under AGP 9 R8, then the widget silently
stopped working) and Glance adds nothing we need. Keep the receiver class name
`dev.savar.aria.widget.AriaWidgetReceiver`, or widgets already on home screens vanish.
It reads each widget's real sizes (`OPTION_APPWIDGET_SIZES`) and builds one layout per size, setting
the 4x2 cover's size in code (`setViewLayoutWidth/Height`). Don't rely on `adjustViewBounds`: the first
RemoteViews version did and letterboxed the cover. Bitmaps go over binder, so keep the cover ≤ 400px.

**Artist photos.** Automatic pick = Deezer search with `artist:"X" album:"<one of your albums>"`, then
the most-followed exact-name match. Exact name or nothing. Deezer's grey placeholder (empty hash or
md5-of-empty) is skipped. A user choice (`*.pick.jpg` / `*.off`) is never replaced automatically.
Bump the `.v2` marker logic in `loadIndex()` if the automatic matcher changes and old picks should be redone.

**Background playback on OxygenOS.** Swiping Aria from recents can kill playback; that's OxygenOS.
A separate player process was tried and made things worse (reverted). Settings has
battery-optimisation and OnePlus guidance rows. Don't re-attempt without a new idea.

## Decisions the owner made (don't regress)

- Search bar: a pill at the top of home, in the original **lighter** style (not the darker
  "new" search bar).
- Dock: mini player + nav bar in one surface, **no highlighted border/rim**. Swipe up on the dock
  opens now playing; swipe up inside now playing opens the queue.
- Now playing bottom row: **Sleep** and **Queue** as text buttons (not icon-only). Lyrics lives in
  the ⋮ menu as plain text with no icon ("Lyrics" / "Hide lyrics").
- Tapping the artist name on now playing (big view and lyrics header) opens the artist. The mini
  player's text is not separately tappable: a tap anywhere on it opens the player.
- Back with lyrics open closes the lyrics (cover morphs back) before closing the player.
- Fonts slightly smaller than M3 defaults. Artist names must never clip.
- Motion: fluid, shared-axis / container transforms, not plain fades.
- Pinned albums (long-press an album) show first.
- Folder include **and** exclude rules together; the most specific wins.
- Album/artist sorting exists like songs; sorts persist.
- App icon: **Wave**, the owner's own design: four rounded bars (third one coral) on graphite. Palette: tile #1C1C1E, cream #EEE7DC, accent #E2957A.
  Spec in `docs/icon/README.md`. Don't redesign it; it ended a long run of rejected icons.
- Now-playing row in lists: faint tint (secondaryContainer at 32%) + equaliser mark. Plain colour was too dim, a solid pill too loud.
- Widget look (the owner calls it perfect, so don't restyle without asking): #16161C body, 28dp corners,
  blurred cover backdrop + 38% black scrim, square cover with 18dp corners (4x2) / 20dp (4x4),
  white 14sp medium (not bold) title, 72%-white 12sp artist, white prev/next, **white** pill play button with a black icon.

## Tried and reverted

- Player in its own process ("daemon") to survive swipe-away: didn't help on OxygenOS, more bugs.
- Icon-only buttons for Sleep/Queue/Lyrics at the bottom of now playing.
- Darker redesigned search bar; heavy home header.
- Swipe on the dock opening the queue (it should open the player).
- Glance widget (see above).

## Gotchas

- **R8 under AGP 9** stops keeping no-arg constructors of kept classes. `gradle.properties` sets
  `android.r8.strictFullModeForKeepRules=false` to keep the old behaviour; keep it unless you've
  verified every reflective path (jaudiotagger is kept wholesale in `proguard-rules.pro`).
- **Kotlin version** must match the stdlib metadata of the AndroidX libs (2.4.x for the 2026 libs).
  It's pinned via the root `buildscript` because AGP 9 bundles its own.
- Compose 1.12 renamed `sharedElement(state = …)` to `sharedContentState`.
- Media3 1.5 `MediaItemsWithStartPosition` field names differ from older samples.
- The sandbox's network allowlist blocks most sites from the shell and some from WebFetch.
  Deezer and Google Maven aren't reachable from the shell; the phone has no such limits.

## Ideas / open threads

- Owner's possible future features: online streaming and downloads (keep it lean).
- Confirmed on device by the owner: RemoteViews widget look, back/tap during page transitions, font page.
- Not yet confirmed: widget buttons from a cold start on all three sizes; queue opening smoothness
  (fallback plan in "Queue sheet"); queue drag-reorder fix.
- Corner roundness doesn't yet reach the nav bar indicator, the fast scroller, or the widget (widget
  corners are fixed by design). Only change if the owner asks.
- Artist photo cache stores 1000px images (for zoom). Fine for hundreds of artists. Add a
  "clear" action only if it becomes a problem.
