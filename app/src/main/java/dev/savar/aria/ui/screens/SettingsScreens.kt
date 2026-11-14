@file:OptIn(ExperimentalMaterial3Api::class)

package dev.savar.aria.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.savar.aria.AppContainer
import dev.savar.aria.BuildConfig
import dev.savar.aria.data.FolderRule
import dev.savar.aria.data.ThemeMode
import dev.savar.aria.data.buildFolderTree
import dev.savar.aria.ui.LocalBottomPadding
import dev.savar.aria.ui.theme.FeaturedFonts
import dev.savar.aria.ui.theme.googleFontFamily

@Composable
private fun SimpleTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit, onFolders: () -> Unit, onStopPlayer: () -> Unit) {
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val store = container.settings
    val filter = s.folders
    var showFonts by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SimpleTopBar("Settings", onBack) },
    ) { padding ->
        Column(
            Modifier
                .padding(top = padding.calculateTopPadding())
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            SectionTitle("Appearance")
            Text(
                "Theme",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                ThemeMode.entries.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = s.themeMode == mode,
                        onClick = { store.setTheme(mode) },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                    ) { Text(mode.name) }
                }
            }
            Spacer(Modifier.height(8.dp))
            SwitchRow("Pure black", "AMOLED-friendly dark theme", s.amoled, store::setAmoled)
            SwitchRow("Wallpaper colors", "Material You accents from your wallpaper", s.dynamicColor, store::setDynamicColor)
            ListItem(
                headlineContent = { Text("Font") },
                supportingContent = { Text(s.font ?: "System default") },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { showFonts = true },
            )

            SectionTitle("Library")
            ListItem(
                headlineContent = { Text("Folders") },
                supportingContent = {
                    Text(
                        when {
                            filter.isEmpty -> "All folders"
                            else -> "${filter.include.size} included · ${filter.exclude.size} excluded"
                        },
                    )
                },
                leadingContent = { Icon(Icons.Rounded.Folder, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(onClick = onFolders),
            )
            ListItem(
                headlineContent = { Text("Rescan library") },
                supportingContent = { Text("Aria picks up new files automatically; use this if something looks stale") },
                leadingContent = { Icon(Icons.Rounded.Refresh, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { container.library.refresh() },
            )

            SectionTitle("Playback")
            BackgroundPlaybackRow()
            ListItem(
                headlineContent = { Text("Stop player") },
                supportingContent = {
                    Text("Aria plays from its own background process. This shuts it down completely; it starts again when you play something.")
                },
                leadingContent = { Icon(Icons.Rounded.PowerSettingsNew, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(onClick = onStopPlayer),
            )

            SectionTitle("About")
            ListItem(
                headlineContent = { Text("Aria ${BuildConfig.VERSION_NAME}") },
                supportingContent = { Text("github.com/savar95x/aria-player") },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/savar95x/aria-player")))
                },
            )
            // Clear the mini player docked at the bottom.
            Spacer(Modifier.height(LocalBottomPadding.current + 16.dp))
        }
    }

    if (showFonts) {
        FontPicker(current = s.font, onPick = { store.setFont(it); showFonts = false }, onDismiss = { showFonts = false })
    }
}

/**
 * Playback already runs in a media foreground service, so clearing recents doesn't stop it.
 * OxygenOS can still kill "optimised" apps in the background; unrestricted battery use prevents that.
 */
@Composable
private fun BackgroundPlaybackRow() {
    val context = LocalContext.current
    val power = remember { context.getSystemService(PowerManager::class.java) }
    fun check() = power.isIgnoringBatteryOptimizations(context.packageName)
    var unrestricted by remember { mutableStateOf(check()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { unrestricted = check() }
    ListItem(
        headlineContent = { Text("Keep playing in the background") },
        supportingContent = {
            Text(
                if (unrestricted) "Unrestricted battery use is on. Music keeps playing after you clear recents."
                else "Tap to allow unrestricted battery use, so OxygenOS doesn't stop music in the background",
            )
        },
        leadingContent = { Icon(Icons.Rounded.BatteryChargingFull, null) },
        trailingContent = {
            if (unrestricted) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(enabled = !unrestricted) {
            context.startActivity(
                Intent(
                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        },
    )
    ListItem(
        headlineContent = { Text("OnePlus / OxygenOS") },
        supportingContent = {
            Text(
                "OxygenOS can still kill apps you swipe away. Open app info → Battery → turn on " +
                    "\"Allow background activity\" and \"Allow auto launch\". Locking Aria in recents also works.",
            )
        },
        leadingContent = { Icon(Icons.Rounded.Info, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable {
            context.startActivity(
                Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )
        },
    )
}

@Composable
private fun FontPicker(current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf(current?.takeIf { it !in FeaturedFonts }.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Font") },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item {
                        FontOption("System default", FontFamily.Default, current == null) { onPick(null) }
                    }
                    items(FeaturedFonts) { name ->
                        FontOption(name, googleFontFamily(name), current == name) { onPick(name) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it },
                    singleLine = true,
                    label = { Text("Any Google Font") },
                    placeholder = { Text("e.g. Bricolage Grotesque") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Exact name from fonts.google.com. Downloads once, then works offline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(custom.trim()) }, enabled = custom.isNotBlank()) { Text("Use custom") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FontOption(name: String, family: FontFamily, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(name, fontFamily = family, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun FoldersScreen(container: AppContainer, onBack: () -> Unit) {
    val all by container.library.allSongs.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val filter = settings?.folders ?: return
    val tree = remember(all, filter.include, filter.exclude) {
        buildFolderTree(all.orEmpty(), filter.include + filter.exclude)
    }
    val visible = remember(all, filter) { all.orEmpty().count { filter.allows(it.folder) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SimpleTopBar("Folders", onBack) {
                if (!filter.isEmpty) TextButton(onClick = container.settings::clearFolderRules) { Text("Reset") }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize(),
            contentPadding = PaddingValues(bottom = LocalBottomPadding.current + 24.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("$visible of ${all.orEmpty().size} songs shown", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "✓ Include: once any folder is included, only included folders show up.\n" +
                                "⊘ Exclude: always hidden.\n" +
                                "The most specific rule wins, so you can include Music but exclude Music/Recordings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(tree, key = { it.path }) { node ->
                val rule = filter.ruleFor(node.path)
                val shown = filter.allows(node.path)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp + (node.depth * 16).dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Folder,
                        null,
                        tint = if (shown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            node.name.ifEmpty { node.path },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (shown) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            buildString {
                                if (node.depth == 0) append("${node.volumeLabel} · ")
                                append("${node.songCount} songs")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconToggleButton(
                        checked = rule == FolderRule.Include,
                        onCheckedChange = {
                            container.settings.setFolderRule(node.path, if (it) FolderRule.Include else FolderRule.None)
                        },
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            "Include",
                            tint = if (rule == FolderRule.Include) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    IconToggleButton(
                        checked = rule == FolderRule.Exclude,
                        onCheckedChange = {
                            container.settings.setFolderRule(node.path, if (it) FolderRule.Exclude else FolderRule.None)
                        },
                    ) {
                        Icon(
                            Icons.Rounded.Block,
                            "Exclude",
                            tint = if (rule == FolderRule.Exclude) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}
