package dev.savar.aria.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.savar.aria.AppContainer
import dev.savar.aria.ui.components.SongActions
import dev.savar.aria.ui.screens.AlbumScreen
import dev.savar.aria.ui.screens.ArtistScreen
import dev.savar.aria.ui.screens.FoldersScreen
import dev.savar.aria.ui.screens.HomeScreen
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

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
    val hasMini = player.current != null
    val bottomPadding: Dp = if (hasMini) 92.dp else 16.dp

    CompositionLocalProvider(LocalBottomPadding provides bottomPadding) {
        Box(Modifier.fillMaxSize()) {
            NavHost(navController = nav, startDestination = "home", modifier = Modifier.fillMaxSize()) {
                composable("home") {
                    HomeScreen(
                        container = container,
                        player = player,
                        vm = vm,
                        actions = actions,
                        onSearch = { nav.navigate("search") },
                        onSettings = { nav.navigate("settings") },
                        onAlbum = { nav.navigate("album/$it") },
                        onArtist = { nav.navigate("artist/${Uri.encode(it)}") },
                    )
                }
                composable("search") {
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
                composable("album/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
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
                composable("artist/{name}") { entry ->
                    ArtistScreen(
                        name = Uri.decode(entry.arguments?.getString("name").orEmpty()),
                        container = container,
                        player = player,
                        vm = vm,
                        actions = actions,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable("settings") {
                    SettingsScreen(container, onBack = { nav.popBackStack() }, onFolders = { nav.navigate("folders") })
                }
                composable("folders") {
                    FoldersScreen(container, onBack = { nav.popBackStack() })
                }
                composable("tags/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    TagEditorScreen(
                        songId = entry.arguments?.getLong("id") ?: 0L,
                        container = container,
                        onBack = { nav.popBackStack() },
                    )
                }
            }

            AnimatedVisibility(
                visible = hasMini && !nowPlayingOpen,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                MiniPlayer(container = container, player = player, vm = vm, onClick = { nowPlayingOpen = true })
            }

            AnimatedVisibility(
                visible = nowPlayingOpen && hasMini,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
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
    BackHandler(enabled = nowPlayingOpen) { nowPlayingOpen = false }
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
