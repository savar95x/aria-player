package dev.savar.aria.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import dev.savar.aria.ui.screens.QueueSheet
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.savar.aria.AppContainer
import dev.savar.aria.ui.components.SongActions
import dev.savar.aria.ui.screens.AlbumScreen
import dev.savar.aria.ui.screens.ArtistScreen
import dev.savar.aria.ui.screens.FoldersScreen
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Library(container: AppContainer) {
    val vm: PlayerViewModel = viewModel()
    val player by vm.state.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }

    val actions = remember(vm, nav) {
        SongActions(
            onPlayNext = vm::playNext,
            onAddToQueue = vm::addToQueue,
            onOpenAlbum = { nav.navigate("album/${it.albumId}") },
            onOpenArtist = { nav.navigate("artist/${Uri.encode(it.artist)}") },
            onEditTags = { nav.navigate("tags/${it.id}") },
        )
    }
    var tab by rememberSaveable { mutableStateOf(HomeTab.Songs) }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    // Like Google's apps: the bar stays on top-level pages and the pages you drill into from them.
    val showNavBar = route == null || route == "home" || route.startsWith("album/") || route.startsWith("artist/")

    val hasMini = player.current != null
    val bottomPadding: Dp = (if (hasMini) 78.dp else 0.dp) + (if (showNavBar) 80.dp else 8.dp) + 8.dp

    var showQueue by remember { mutableStateOf(false) }

    // Links shared into Aria play as soon as the player is connected.
    val pendingLink by container.pendingLink.collectAsStateWithLifecycle()
    LaunchedEffect(pendingLink, player.connected) {
        val link = pendingLink ?: return@LaunchedEffect
        if (!player.connected) return@LaunchedEffect
        container.pendingLink.value = null
        vm.playUrl(link)
        nowPlayingOpen = true
    }

    // Drag-back progress for the now-playing sheet (0 = open, 1 = fully pulled back).
    var backProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(nowPlayingOpen) { if (nowPlayingOpen) backProgress = 0f }

    CompositionLocalProvider(LocalBottomPadding provides bottomPadding) {
      SharedTransitionLayout(Modifier.fillMaxSize()) {
       CompositionLocalProvider(LocalSharedTransitionScope provides this) {
        Box(Modifier.fillMaxSize()) {
            NavHost(
                navController = nav,
                startDestination = "home",
                modifier = Modifier.fillMaxSize(),
                enterTransition = Motion.Enter,
                exitTransition = Motion.Exit,
                popEnterTransition = Motion.PopEnter,
                popExitTransition = Motion.PopExit,
            ) {
                screen("home") {
                    HomeScreen(
                        container = container,
                        player = player,
                        vm = vm,
                        actions = actions,
                        onSearch = { nav.navigate("search") },
                        onSettings = { nav.navigate("settings") },
                        onAlbum = { nav.navigate("album/$it") },
                        onArtist = { nav.navigate("artist/${Uri.encode(it)}") },
                        tab = tab,
                    )
                }
                screen("search") {
                    SearchScreen(
                        container = container,
                        player = player,
                        vm = vm,
                        actions = actions,
                        onBack = { nav.popBackStack() },
                        onAlbum = { nav.navigate("album/$it") },
                        onArtist = { nav.navigate("artist/${Uri.encode(it)}") },
                    )
                }
                screen("album/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    AlbumScreen(
                        albumId = entry.arguments?.getLong("id") ?: 0L,
                        container = container,
                        player = player,
                        vm = vm,
                        actions = actions,
                        onBack = { nav.popBackStack() },
                        onArtist = { nav.navigate("artist/${Uri.encode(it)}") },
                    )
                }
                screen("artist/{name}") { entry ->
                    ArtistScreen(
                        name = Uri.decode(entry.arguments?.getString("name").orEmpty()),
                        container = container,
                        player = player,
                        vm = vm,
                        actions = actions,
                        onBack = { nav.popBackStack() },
                    )
                }
                screen("settings") {
                    SettingsScreen(container, onBack = { nav.popBackStack() }, onFolders = { nav.navigate("folders") })
                }
                screen("folders") {
                    FoldersScreen(container, onBack = { nav.popBackStack() })
                }
                screen("tags/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    TagEditorScreen(
                        songId = entry.arguments?.getLong("id") ?: 0L,
                        container = container,
                        onBack = { nav.popBackStack() },
                    )
                }
            }

            // One dock for mini player + nav bar: a single surface, so they read as one piece.
            AnimatedVisibility(
                visible = hasMini || showNavBar,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                // A tone above the page plus a soft rim along the top edge that fades out down the
                // sides: separates the dock from a pure-black list without boxing it in.
                val rim = MaterialTheme.colorScheme.outlineVariant
                Surface(
                    // Swipe up anywhere on the dock (just above the home gesture) for the queue.
                    modifier = Modifier.pointerInput(hasMini) {
                        if (!hasMini) return@pointerInput
                        val threshold = 36.dp.toPx()
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = { if (total < -threshold) showQueue = true },
                        ) { change, dy ->
                            change.consume()
                            total += dy
                        }
                    },
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    border = BorderStroke(
                        1.dp,
                        Brush.verticalGradient(0f to rim.copy(alpha = 0.7f), 0.18f to Color.Transparent),
                    ),
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
                                artVisible = !nowPlayingOpen,
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
                                    if (route != "home") nav.popBackStack("home", inclusive = false)
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
            AnimatedVisibility(
                visible = nowPlayingOpen && hasMini,
                enter = fadeIn(tween(260)) +
                    slideInVertically(tween(420, easing = Motion.EmphasizedDecelerate)) { it / 6 },
                exit = fadeOut(tween(220)) +
                    slideOutVertically(tween(260, easing = Motion.EmphasizedAccelerate)) { it / 6 },
                modifier = Modifier.graphicsLayer {
                    val p = backProgress
                    scaleX = 1f - 0.1f * p
                    scaleY = 1f - 0.1f * p
                    translationY = p * 48.dp.toPx()
                    shape = RoundedCornerShape((32 * p).dp)
                    clip = p > 0f
                },
            ) {
              CompositionLocalProvider(LocalAnimatedScope provides this) {
                NowPlayingScreen(
                    container = container,
                    player = player,
                    vm = vm,
                    onCollapse = { nowPlayingOpen = false },
                    onOpenAlbum = { id -> nowPlayingOpen = false; nav.navigate("album/$id") },
                    onOpenArtist = { name -> nowPlayingOpen = false; nav.navigate("artist/${Uri.encode(name)}") },
                    onEditTags = { id -> nowPlayingOpen = false; nav.navigate("tags/$id") },
                )
              }
            }
        }
       }
      }
    }
    if (showQueue && hasMini) QueueSheet(player, vm) { showQueue = false }

    // Predictive back: the sheet shrinks with your finger, and closes if you commit.
    PredictiveBackHandler(enabled = nowPlayingOpen) { events ->
        try {
            events.collect { backProgress = it.progress }
            nowPlayingOpen = false
        } catch (e: CancellationException) {
            backProgress = 0f
            throw e
        }
    }
}

private fun NavGraphBuilder.screen(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry ->
    CompositionLocalProvider(LocalAnimatedScope provides this) { content(entry) }
}

@Composable
private fun AriaNavigationBar(selected: HomeTab?, onSelect: (HomeTab) -> Unit) {
    NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
        HomeTab.entries.forEach { t ->
            val isSelected = t == selected
            NavigationBarItem(
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
        Button(onClick = onGrant) { Text("Allow access") }
        if (askedOnce) {
            TextButton(onClick = onOpenSettings) { Text("Open app settings") }
        }
    }
}
