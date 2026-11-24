@file:OptIn(ExperimentalMaterial3Api::class)

package dev.savar.aria.ui.screens

import androidx.compose.foundation.shape.CircleShape
import dev.savar.aria.ui.theme.pill
import kotlin.math.roundToInt
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.Slider
import androidx.compose.foundation.background
import dev.savar.aria.ui.theme.cornerDp
import dev.savar.aria.ui.theme.rounded
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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Surface
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import dev.savar.kit.FontRow
import dev.savar.kit.FontSettings
import dev.savar.kit.KitIconTitle
import dev.savar.kit.KitRowIcon
import dev.savar.kit.UpdateRow
import dev.savar.kit.kitTopBarColors
import dev.savar.aria.data.FolderRule
import dev.savar.aria.data.ThemeMode
import dev.savar.aria.data.buildFolderTree
import dev.savar.aria.ui.LocalBottomPadding
import dev.savar.aria.ui.theme.FeaturedFonts
import dev.savar.aria.ui.theme.googleFontFamily

/**
 * Settings pages' top bar: Material's large title that shrinks into the bar as you scroll, like Hiatus, Minutia
 * and Regista. Tints once content scrolls under it, except in pure black, where it stays black (shared kit).
 * The page's Scaffold must pass [scroll]'s nestedScrollConnection.
 */
@Composable
internal fun SimpleTopBar(
    title: String,
    onBack: () -> Unit,
    scroll: TopAppBarScrollBehavior,
    actions: @Composable () -> Unit = {},
) {
    LargeTopAppBar(
        title = { Text(title) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
        actions = { actions() },
        scrollBehavior = scroll,
        colors = kitTopBarColors(pureBlack = MaterialTheme.colorScheme.background == Color.Black),
    )
}

/** A settings row's icon: the shared kit's tonal circle, following the roundness setting. */
@Composable
internal fun SettingIcon(icon: ImageVector) = KitRowIcon(icon, shape = pill())

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 32.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

/**
 * Material 3 Expressive / Android 16 style list group: rows share one rounded block, split by
 * small gaps, with the outer corners rounder than the inner ones.
 */
@Composable
internal fun SettingsGroup(vararg rows: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEachIndexed { i, row ->
            val big = cornerDp(24.dp)
            val small = cornerDp(6.dp)
            val shape = when {
                rows.size == 1 -> RoundedCornerShape(big)
                i == 0 -> RoundedCornerShape(topStart = big, topEnd = big, bottomStart = small, bottomEnd = small)
                i == rows.lastIndex -> RoundedCornerShape(topStart = small, topEnd = small, bottomStart = big, bottomEnd = big)
                else -> RoundedCornerShape(small)
            }
            Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                row()
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, icon: ImageVector, onChange: (Boolean) -> Unit) {
    ListItem(
        leadingContent = { SettingIcon(icon) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Switch(
                checked = checked, onCheckedChange = onChange,
                thumbContent = if (checked) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                } else null,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit, onFolders: () -> Unit, onFonts: () -> Unit) {
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val store = container.settings
    val filter = s.folders
    val context = LocalContext.current

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SimpleTopBar("Settings", onBack, scroll) },
    ) { padding ->
        Column(
            Modifier
                .padding(top = padding.calculateTopPadding())
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            SectionTitle("Appearance")
            SettingsGroup(
                {
                    Column(Modifier.padding(16.dp)) {
                        KitIconTitle(Icons.Rounded.DarkMode, "Theme", iconShape = pill())
                        Spacer(Modifier.height(12.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            ThemeMode.entries.forEachIndexed { i, mode ->
                                SegmentedButton(
                                    selected = s.themeMode == mode,
                                    onClick = { store.setTheme(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size, CircleShape) /* ignores the roundness setting */,
                                ) { Text(mode.name, maxLines = 1) }
                            }
                        }
                    }
                },
                { SwitchRow("Pure black", "For AMOLED screens, in dark theme", s.amoled, Icons.Rounded.Contrast, store::setAmoled) },
                { SwitchRow("Wallpaper colours", "Material You accents from your wallpaper", s.dynamicColor, Icons.Rounded.Palette, store::setDynamicColor) },
                { RoundnessRow(s.cornerScale, store::setCornerScale) },
                { FontRow(FontSettings(s.font), iconShape = pill(), onClick = onFonts) },
            )

            SectionTitle("Library")
            SettingsGroup(
                {
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
                        leadingContent = { SettingIcon(Icons.Rounded.Folder) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(onClick = onFolders),
                    )
                },
                {
                    ListItem(
                        headlineContent = { Text("Rescan library") },
                        supportingContent = { Text("Aria picks up new files automatically; use this if something looks stale") },
                        leadingContent = { SettingIcon(Icons.Rounded.Refresh) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { container.library.refresh() },
                    )
                },
            )

            SectionTitle("Playback")
            SettingsGroup({ BatteryRow() }, { OxygenOsRow() })

            SectionTitle("About")
            SettingsGroup(
                { UpdateRow(iconShape = pill()) },
                {
                    ListItem(
                        headlineContent = { Text("Source code") },
                        supportingContent = { Text("github.com/savar95x/aria-player") },
                        leadingContent = { SettingIcon(Icons.Rounded.Code) },
                        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/savar95x/aria-player")))
                        },
                    )
                },
            )
            // Clear the mini player docked at the bottom.
            Spacer(Modifier.height(LocalBottomPadding.current + 16.dp))
        }
    }
}

/**
 * Playback already runs in a media foreground service, so clearing recents doesn't stop it.
 * OxygenOS can still kill "optimised" apps in the background; unrestricted battery use prevents that.
 */
@Composable
private fun BatteryRow() {
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
        leadingContent = { SettingIcon(Icons.Rounded.BatteryChargingFull) },
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
}

@Composable
private fun OxygenOsRow() {
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text("OnePlus / OxygenOS") },
        supportingContent = {
            Text(
                "OxygenOS can still kill apps you swipe away. Open app info → Battery → turn on " +
                    "\"Allow background activity\" and \"Allow auto launch\". Locking Aria in recents also works.",
            )
        },
        leadingContent = { SettingIcon(Icons.Rounded.Info) },
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

/** Live preview while dragging; saved when you let go. */
@Composable
private fun RoundnessRow(value: Float, onChange: (Float) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingIcon(Icons.Rounded.RoundedCorner)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Corner roundness", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (local == 1f) "Default" else "${(local * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp * local))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            TextButton(shape = pill(), onClick = { local = 1f; onChange(1f) }, enabled = local != 1f) { Text("Reset") }
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local) },
            valueRange = 0f..2f,
            steps = 7,
            modifier = Modifier.padding(end = 8.dp),
        )
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

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SimpleTopBar("Folders", onBack, scroll) {
                if (!filter.isEmpty) TextButton(shape = pill(), onClick = container.settings::clearFolderRules) { Text("Reset") }
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
                    shape = rounded(20.dp),
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
