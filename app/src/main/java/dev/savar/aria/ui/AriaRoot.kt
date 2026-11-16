package dev.savar.aria.ui

import dev.savar.aria.ui.theme.pill
import kotlinx.coroutines.launch
import androidx.compose.animation.core.FastOutSlowInEasing
import dev.savar.aria.ui.theme.LocalCornerScale
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.DisposableEffect
import dev.savar.aria.ui.theme.cornerDp
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.coroutines.cancellation.CancellationException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.savar.aria.AppContainer
import dev.savar.aria.ui.components.SongActions
import dev.savar.aria.ui.screens.AlbumScreen
import dev.savar.aria.ui.screens.ArtistScreen
import dev.savar.aria.ui.screens.ArtistPhotoViewer
import dev.savar.aria.data.Artist
import androidx.compose.ui.geometry.Rect
import dev.savar.aria.ui.screens.FoldersScreen
import dev.savar.aria.ui.screens.FontsScreen
import dev.savar.aria.ui.screens.TypefaceScreen
import dev.savar.aria.ui.screens.HomeScreen
import dev.savar.aria.ui.screens.HomeTab
import dev.savar.aria.ui.screens.MiniPlayer
import dev.savar.aria.ui.screens.NowPlayingScreen
import dev.savar.aria.ui.screens.SearchScreen
import dev.savar.aria.ui.screens.SettingsScreen
import dev.savar.aria.ui.screens.TagEditorScreen

/** Extra bottom padding lists need so the mini player doesn't cover their last rows. */
val LocalBottomPadding = compositionLocalOf { 0.dp }

@Composable
fun AriaRoot(container: AppContainer) {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        askedOnce = true
    }
    // Picks up a grant made from system settings while we were in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    LaunchedEffect(granted) { if (granted) container.library.refresh() }

    // A Surface (not a bare Box) so text and icons inherit the theme's content colour in dark mode.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (granted) {
            Library(container)
        } else {
            PermissionScreen(
                askedOnce = askedOnce,
                onGrant = { launcher.launch(permission) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalAnimationApi::class)
@Composable
private fun Library(container: AppContainer) {
    val vm: PlayerViewModel = viewModel()
    val player by vm.state.collectAsStateWithLifecycle()
    // Our own back stack (see Pages.kt): the destination page is always on top, nothing blocks input.
    val pages = rememberSaveable(saver = Pages.Saver) { Pages(listOf(Page("home", 0))) }
    val pageStates = rememberSaveableStateHolder()
    val pageState = remember { SeekableTransitionState(pages.top) }
    val pageTransition = rememberTransition(pageState, label = "pages")
    val pageScope = rememberCoroutineScope()
    var inPageBack by remember { mutableStateOf(false) }
    // Each change animates on from wherever the current animation is, so taps never wait.
    LaunchedEffect(pages.top) { if (!inPageBack) pageState.animateTo(pages.top) }
    fun go(route: String) = pages.push(route)
    fun back() { pages.pop() }
    var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }
    // Full-screen artist photo (artist + where its header image is), drawn over everything.
    var photoViewer by remember { mutableStateOf<Pair<Artist, Rect>?>(null) }
    var showLyrics by rememberSaveable { mutableStateOf(false) }

    val actions = remember(vm, pages) {
        SongActions(
            onPlayNext = vm::playNext,
            onAddToQueue = vm::addToQueue,
            onOpenAlbum = { go("album/${it.albumId}") },
            onOpenArtist = { go("artist/${Uri.encode(container.library.mainArtist(it.artist))}") },
            onEditTags = { go("tags/${it.id}") },
        )
    }
    var tab by rememberSaveable { mutableStateOf(HomeTab.Songs) }
    val route = pages.top.route
    // Like Google's apps: the bar stays on top-level pages and the pages you drill into from them.
    val showNavBar = route == null || route == "home" || route.startsWith("album/") || route.startsWith("artist/")

    val hasMini = player.current != null
    val bottomPadding: Dp = (if (hasMini) 78.dp else 0.dp) + (if (showNavBar) 80.dp else 8.dp) + 8.dp

    // Links shared into Aria play as soon as the player is connected.
    val pendingLink by container.pendingLink.collectAsStateWithLifecycle()
    LaunchedEffect(pendingLink, player.connected) {
        val link = pendingLink ?: return@LaunchedEffect
        if (!player.connected) return@LaunchedEffect
        container.pendingLink.value = null
        vm.playUrl(link)
        nowPlayingOpen = true
    }

    // Now playing is driven by a seekable transition, so dragging the dock moves it 1:1 with the
    // finger (cover flying out of the mini player included); taps and releases animate the rest.
    val npTarget = nowPlayingOpen && hasMini
    val npState = remember { SeekableTransitionState(npTarget) }
    val npTransition = rememberTransition(npState, label = "nowPlaying")
    var npDragging by remember { mutableStateOf(false) }
    var npDrag by remember { mutableFloatStateOf(0f) }
    var rootHeight by remember { mutableIntStateOf(1) }
    val npScope = rememberCoroutineScope()
    LaunchedEffect(npTarget) {
        if (!npDragging) npState.animateTo(npTarget, if (npTarget) Motion.SheetSettle else null)
    }
    LaunchedEffect(Unit) {
        snapshotFlow { if (npDragging) npDrag else null }.collect { dist ->
            if (dist == null) return@collect
            if (npState.targetState != true) npState.seekTo(0f, targetState = true)
            // The sheet slides its full height linearly over Motion.SheetMs, so this play-time
            // fraction puts its top edge exactly under the finger.
            val total = npTransition.totalDurationNanos.coerceAtLeast(1L)
            val travel = (dist / rootHeight).coerceIn(0f, 1f)
            npState.seekTo((travel * Motion.SheetMs * 1_000_000L / total).coerceIn(0f, 1f), targetState = true)
        }
    }
    val dockDrag = Modifier.pointerInput(hasMini) {
        if (!hasMini) return@pointerInput
        val tracker = VelocityTracker()
        val fling = 600.dp.toPx()
        fun release() {
            val velocity = tracker.calculateVelocity().y
            val open = npDrag / rootHeight > 0.3f || velocity < -fling
            npDragging = false
            if (open && npDrag > 0f) nowPlayingOpen = true
            else npScope.launch { npState.animateTo(false, Motion.SheetSettle) }
        }
        detectVerticalDragGestures(
            onDragStart = {
                tracker.resetTracking()
                npDrag = 0f
                npDragging = true
            },
            onDragEnd = { release() },
            onDragCancel = { release() },
        ) { change, dy ->
            change.consume()
            tracker.addPosition(change.uptimeMillis, change.position)
            npDrag = (npDrag - dy).coerceAtLeast(0f)
        }
    }

    // Drag-back progress for the now-playing sheet (0 = open, 1 = fully pulled back).
    var backProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(nowPlayingOpen) { if (nowPlayingOpen) backProgress = 0f }

    CompositionLocalProvider(LocalBottomPadding provides bottomPadding) {
      SharedTransitionLayout(Modifier.fillMaxSize()) {
       CompositionLocalProvider(LocalSharedTransitionScope provides this) {
        Box(Modifier.fillMaxSize().onSizeChanged { rootHeight = it.height.coerceAtLeast(1) }) {
            PageHost(pages, pageTransition, pageStates, Modifier.fillMaxSize()) { page ->
                    val r = page.route
                    when {
                        r == "home" -> HomeScreen(
                            container = container,
                            player = player,
                            vm = vm,
                            actions = actions,
                            onSearch = { go("search") },
                            onSettings = { go("settings") },
                            onAlbum = { go("album/$it") },
                            onArtist = { go("artist/${Uri.encode(container.library.mainArtist(it))}") },
                            tab = tab,
                        )
                        r == "search" -> SearchScreen(
                            container = container,
                            player = player,
                            vm = vm,
                            actions = actions,
                            onBack = { back() },
                            onAlbum = { go("album/$it") },
                            onArtist = { go("artist/${Uri.encode(container.library.mainArtist(it))}") },
                        )
                        r.startsWith("album/") -> AlbumScreen(
                            albumId = r.removePrefix("album/").toLongOrNull() ?: 0L,
                            container = container,
                            player = player,
                            vm = vm,
                            actions = actions,
                            onBack = { back() },
                            onArtist = { go("artist/${Uri.encode(container.library.mainArtist(it))}") },
                        )
                        r.startsWith("artist/") -> ArtistScreen(
                            name = Uri.decode(r.removePrefix("artist/")),
                            container = container,
                            player = player,
                            vm = vm,
                            actions = actions,
                            onBack = { back() },
                            photoOpen = photoViewer != null,
                            onViewPhoto = { a, bounds -> photoViewer = a to bounds },
                        )
                        r == "settings" -> SettingsScreen(
                            container,
                            onBack = { back() },
                            onFolders = { go("folders") },
                            onFonts = { go("fonts") },
                        )
                        r == "fonts" -> FontsScreen(container, onBack = { back() }, onTypeface = { go("typeface") })
                        r == "typeface" -> TypefaceScreen(container, onBack = { back() })
                        r == "folders" -> FoldersScreen(container, onBack = { back() })
                        r.startsWith("tags/") -> TagEditorScreen(
                            songId = r.removePrefix("tags/").toLongOrNull() ?: 0L,
                            container = container,
                            onBack = { back() },
                        )
                    }
            }

            val dockCorner = 28.dp * LocalCornerScale.current.coerceAtMost(1f)
            // One dock for mini player + nav bar: a single surface, so they read as one piece.
            AnimatedVisibility(
                visible = hasMini || showNavBar,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                // One tone above the page; the tonal step alone separates it.
                Surface(
                    // Swipe up anywhere on the dock (just above the home gesture) to open the player.
                    modifier = dockDrag,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    // Never rounder than default: past 100% the dock's top edge bulged into a big arc.
                    shape = RoundedCornerShape(topStart = dockCorner, topEnd = dockCorner),
                ) {
                    Column {
                        AnimatedVisibility(
                            visible = hasMini,
                            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
                        ) {
                            MiniPlayer(
                                container = container,
                                player = player,
                                vm = vm,
                                npTransition = npTransition,
                                onClick = { nowPlayingOpen = true },
                            )
                        }
                        AnimatedVisibility(
                            visible = showNavBar,
                            enter = expandVertically(expandFrom = Alignment.Top),
                            exit = shrinkVertically(shrinkTowards = Alignment.Top),
                        ) {
                            AriaNavigationBar(
                                selected = tab.takeIf { route == "home" },
                                onSelect = { t ->
                                    tab = t
                                    pages.popToRoot()
                                },
                            )
                        }
                        AnimatedVisibility(visible = !showNavBar) {
                            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(8.dp))
                        }
                    }
                }
            }

            // The page fades up while the artwork flies out of the mini player (shared element).
            val backCorner = cornerDp(32.dp)
            npTransition.AnimatedVisibility(
                visible = { it },
                // Linear in play time on purpose: drags seek it 1:1; taps ease the fraction (SheetSettle).
                // An opaque sheet sliding its full height, like Metro/Retro: no fade, so it never shows
                // the list through it.
                enter = slideInVertically(tween(Motion.SheetMs, easing = LinearEasing)) { it },
                exit = slideOutVertically(tween(320, easing = FastOutSlowInEasing)) { it },
                modifier = Modifier.graphicsLayer {
                    val p = backProgress
                    scaleX = 1f - 0.1f * p
                    scaleY = 1f - 0.1f * p
                    translationY = p * 48.dp.toPx()
                    shape = RoundedCornerShape(backCorner * p)
                    clip = p > 0f
                },
            ) {
              CompositionLocalProvider(LocalAnimatedScope provides this) {
                NowPlayingScreen(
                    container = container,
                    player = player,
                    vm = vm,
                    onCollapse = { nowPlayingOpen = false },
                    onOpenAlbum = { id -> nowPlayingOpen = false; go("album/$id") },
                    onOpenArtist = { name -> nowPlayingOpen = false; go("artist/${Uri.encode(container.library.mainArtist(name))}") },
                    onEditTags = { id -> nowPlayingOpen = false; go("tags/$id") },
                    showLyrics = showLyrics,
                    onShowLyrics = { showLyrics = it },
                )
              }
            }

            photoViewer?.let { (artist, bounds) ->
                ArtistPhotoViewer(artist, container.artistPhotos, bounds) { photoViewer = null }
            }
        }
       }
      }
    }
    // Predictive back: the sheet shrinks with your finger, and closes if you commit.
    PredictiveBackHandler(enabled = nowPlayingOpen && !showLyrics) { events ->
        try {
            events.collect { backProgress = it.progress }
            nowPlayingOpen = false
        } catch (e: CancellationException) {
            backProgress = 0f
            throw e
        }
    }
    // With lyrics open, back first returns to the cover.
    BackHandler(enabled = nowPlayingOpen && showLyrics) { showLyrics = false }

    // Predictive back between pages: the previous page follows your finger, and back commits on release.
    // If a page animation is still running, back simply pops on release (seeking mid-animation is what
    // jammed NavHost).
    PredictiveBackHandler(enabled = !nowPlayingOpen && photoViewer == null && pages.stack.size > 1) { events ->
        val prev = pages.previous
        if (prev == null || pageState.currentState != pageState.targetState) {
            events.collect { }
            back()
            return@PredictiveBackHandler
        }
        inPageBack = true
        pages.popping = true
        try {
            events.collect { pageState.seekTo(it.progress, targetState = prev) }
            back()
        } catch (e: CancellationException) {
            pageScope.launch { pageState.animateTo(pages.top) }
            throw e
        } finally {
            inPageBack = false
        }
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AriaNavigationBar(selected: HomeTab?, onSelect: (HomeTab) -> Unit) {
    // M3 Expressive "short" navigation bar: shorter, pill indicator, labels always shown.
    ShortNavigationBar(containerColor = Color.Transparent) {
        HomeTab.entries.forEach { t ->
            val isSelected = t == selected
            ShortNavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(t) },
                icon = {
                    Icon(
                        when (t) {
                            HomeTab.Songs -> if (isSelected) Icons.Rounded.MusicNote else Icons.Outlined.MusicNote
                            HomeTab.Albums -> if (isSelected) Icons.Rounded.Album else Icons.Outlined.Album
                            HomeTab.Artists -> if (isSelected) Icons.Rounded.Person else Icons.Outlined.Person
                        },
                        contentDescription = null,
                    )
                },
                label = { Text(t.label) },
            )
        }
    }
}

@Composable
private fun PermissionScreen(askedOnce: Boolean, onGrant: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.LibraryMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text("Aria needs your music", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Allow access to audio files so Aria can find the songs on this phone. Nothing leaves your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(shape = pill(), onClick = onGrant) { Text("Allow access") }
        if (askedOnce) {
            TextButton(shape = pill(), onClick = onOpenSettings) { Text("Open app settings") }
        }
    }
}
