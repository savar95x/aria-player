@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.savar.aria.ui.screens

import kotlin.math.roundToInt
import dev.savar.aria.ui.theme.pill
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.savar.aria.AppContainer
import dev.savar.aria.data.FontCatalog
import dev.savar.aria.data.FontCategory
import dev.savar.aria.data.FontEntry
import dev.savar.aria.ui.theme.googleFontFamily
import dev.savar.aria.ui.theme.rounded

/**
 * Font settings: a live preview on top (the whole app switches the moment you tap a font), then a
 * search over every Google Font with style filters, most popular first, each name in its own face.
 */
@Composable
fun FontsScreen(container: AppContainer, onBack: () -> Unit) {
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val current = settings?.font
    val context = LocalContext.current
    val catalog = remember { FontCatalog(context.applicationContext) }
    val fonts by produceState<List<FontEntry>?>(null) { value = catalog.load() }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<FontCategory?>(null) }
    val q = query.trim()
    val shown = remember(fonts, q, category) {
        fonts.orEmpty().filter { (category == null || it.category == category) && (q.isEmpty() || it.name.contains(q, ignoreCase = true)) }
    }
    val pick: (String?) -> Unit = { container.settings.setFont(it) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SimpleTopBar("Font", onBack) },
    ) { padding ->
        Column(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
            FontPreview(current)
            settings?.let { TextStyleControls(it, container) }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search all Google Fonts") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") }
                },
                shape = rounded(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") }) }
                items(FontCategory.entries) { c ->
                    FilterChip(selected = category == c, onClick = { category = if (category == c) null else c }, label = { Text(c.label) })
                }
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            ) {
                if (q.isEmpty() && category == null) {
                    item(key = "system") { FontRow("System default", FontFamily.Default, "Your phone's font", current == null) { pick(null) } }
                }
                when {
                    fonts == null -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                    }
                    shown.isEmpty() -> item(key = "none") {
                        Text(
                            "No Google Font matches “$q”.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                    else -> items(shown, key = { it.name }) { f ->
                        FontRow(f.name, googleFontFamily(f.name), f.category.label, current == f.name) { pick(f.name) }
                    }
                }
            }
        }
    }
}

/** Size, weight and letter spacing: live while dragging, saved when you let go. */
@Composable
private fun TextStyleControls(s: dev.savar.aria.data.AppSettings, container: AppContainer) {
    val store = container.settings
    var size by remember(s.textScale) { mutableFloatStateOf(s.textScale) }
    var weight by remember(s.weightDelta) { mutableFloatStateOf(s.weightDelta.toFloat()) }
    var spacing by remember(s.letterSpacing) { mutableFloatStateOf(s.letterSpacing) }
    val changed = s.textScale != 1f || s.weightDelta != 0 || s.letterSpacing != 0f
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)) {
        StyleSlider("Size", "${(size * 100).roundToInt()}%", size, 0.8f..1.3f, steps = 9, onChange = { size = it }) {
            store.setTextScale(size)
        }
        StyleSlider("Weight", weightLabel(weight.roundToInt()), weight, -300f..300f, steps = 5, onChange = { weight = it }) {
            store.setWeightDelta(weight.roundToInt())
        }
        StyleSlider("Letter spacing", if (spacing == 0f) "Default" else "%+.2f em".format(spacing), spacing, -0.05f..0.15f, steps = 7, onChange = { spacing = it }) {
            store.setLetterSpacing(spacing)
        }
        if (changed) {
            TextButton(shape = pill(), onClick = store::resetTextStyle, modifier = Modifier.align(Alignment.End)) { Text("Reset size, weight & spacing") }
        }
    }
}

private fun weightLabel(delta: Int) = when {
    delta == 0 -> "Default"
    delta < 0 -> "Lighter ${-delta / 100}"
    else -> "Bolder ${delta / 100}"
}

@Composable
private fun StyleSlider(
    label: String,
    value: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Slider(value = current, onValueChange = onChange, onValueChangeFinished = onDone, valueRange = range, steps = steps)
}

/** What the app looks like in the chosen font: uses the live theme, so it updates as you pick. */
@Composable
private fun FontPreview(current: String?) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = rounded(24.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Aa", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(14.dp))
                Text(
                    current ?: "System default",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text("Song title", style = MaterialTheme.typography.bodyLarge)
            Text("Artist name · Album · 3:45", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Text("Now playing", style = MaterialTheme.typography.headlineSmall)
            Text("0123456789 · The quick brown fox", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FontRow(name: String, family: FontFamily, caption: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, fontFamily = family, style = MaterialTheme.typography.titleMedium.copy(fontFamily = family), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (caption.isNotEmpty()) {
                Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selected) Icon(Icons.Rounded.Check, "Selected", Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
    }
}
