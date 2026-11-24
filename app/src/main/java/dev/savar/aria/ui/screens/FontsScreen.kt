@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package dev.savar.aria.ui.screens

import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import dev.savar.aria.ui.theme.LocalCornerScale
import dev.savar.kit.FontPageContent
import dev.savar.kit.FontSample
import dev.savar.kit.FontSettings
import dev.savar.kit.TypefaceContent
import dev.savar.kit.groupShape

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.AlertDialog
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
import dev.savar.aria.ui.theme.googleFontFamily
import dev.savar.aria.ui.theme.rounded

/**
 * Font settings: the shared kit's font page (preview, typeface, size / weight / letter spacing), the same in all
 * four apps, inside Aria's page. Reset lives in the overflow menu behind a confirmation.
 */
@Composable
fun FontsScreen(container: AppContainer, onBack: () -> Unit, onTypeface: () -> Unit) {
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val store = container.settings
    var menu by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SimpleTopBar("Font", onBack, scroll) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Reset to defaults") }, onClick = { menu = false; confirmReset = true })
                    }
                }
            }
        },
    ) { padding ->
        val current = FontSettings(s.font, s.textScale, s.weightDelta, s.letterSpacing)
        val cornerScale = LocalCornerScale.current
        FontPageContent(
            current,
            FontSample("Song title", "Artist name · Album · 3:45", "Now playing"),
            onTypeface = onTypeface,
            onChange = { n ->
                if (n.textScale != current.textScale) store.setTextScale(n.textScale)
                if (n.weightDelta != current.weightDelta) store.setWeightDelta(n.weightDelta)
                if (n.letterSpacing != current.letterSpacing) store.setLetterSpacing(n.letterSpacing)
                if (n.font != current.font) store.setFont(n.font)
            },
            modifier = Modifier.padding(top = padding.calculateTopPadding()).navigationBarsPadding(),
            previewShape = rounded(24.dp),
            groupShape = { outer, inner, first, last -> groupShape(outer * cornerScale, inner * cornerScale, first, last) },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset font settings?") },
            text = { Text("Typeface, size, weight and letter spacing go back to their defaults.") },
            confirmButton = {
                TextButton(shape = pill(), onClick = { store.resetFontSettings(); confirmReset = false }) { Text("Reset") }
            },
            dismissButton = { TextButton(shape = pill(), onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

/** Every Google Font (shared kit): search and style filters pinned on top; a tap applies it. */
@Composable
fun TypefaceScreen(container: AppContainer, onBack: () -> Unit) {
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SimpleTopBar("Typeface", onBack, scroll) },
    ) { padding ->
        TypefaceContent(settings?.font, onPick = { container.settings.setFont(it) }, modifier = Modifier.padding(top = padding.calculateTopPadding()))
    }
}
