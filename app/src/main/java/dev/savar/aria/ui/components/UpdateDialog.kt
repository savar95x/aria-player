package dev.savar.aria.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.savar.aria.BuildConfig
import dev.savar.aria.ui.theme.pill
import dev.savar.aria.update.Updater

/** The "new version" prompt: release notes, then Update → download progress → Android's installer. */
@Composable
fun UpdateDialog(updater: Updater) {
    val state by updater.state.collectAsStateWithLifecycle()
    val dismissed by updater.dismissed.collectAsStateWithLifecycle()
    // Coming back from the "Install unknown apps" screen continues the install.
    LifecycleResumeEffect(updater) {
        updater.onResume()
        onPauseOrDispose { }
    }
    val release = when (val s = state) {
        is Updater.State.Available -> s.release
        is Updater.State.Downloading -> s.release
        is Updater.State.Installing -> s.release
        is Updater.State.NeedsPermission -> s.release
        is Updater.State.Failed -> s.release
        Updater.State.Idle -> null
    } ?: return
    if (dismissed) return

    val busy = state is Updater.State.Downloading || state is Updater.State.Installing
    AlertDialog(
        onDismissRequest = { if (!busy) updater.dismissed.value = true },
        title = { Text("Aria ${release.version} is out") },
        text = {
            Column {
                Text(
                    "You have ${BuildConfig.VERSION_NAME}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (release.notes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        release.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                    )
                }
                when (val s = state) {
                    is Updater.State.Downloading -> {
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    }
                    is Updater.State.Installing -> {
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    is Updater.State.NeedsPermission -> {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Android needs you to allow installs from Aria once. Turn it on, come back, and the update continues.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    is Updater.State.Failed -> {
                        Spacer(Modifier.height(12.dp))
                        Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                    else -> Unit
                }
            }
        },
        confirmButton = {
            when (state) {
                is Updater.State.NeedsPermission ->
                    Button(shape = pill(), onClick = updater::openInstallPermission) { Text("Allow") }
                is Updater.State.Failed ->
                    Button(shape = pill(), onClick = updater::install) { Text("Retry") }
                is Updater.State.Downloading -> Button(shape = pill(), onClick = {}, enabled = false) { Text("Downloading…") }
                is Updater.State.Installing -> Button(shape = pill(), onClick = {}, enabled = false) { Text("Installing…") }
                else -> Button(shape = pill(), onClick = updater::install) { Text("Update") }
            }
        },
        dismissButton = {
            if (!busy) TextButton(shape = pill(), onClick = { updater.dismissed.value = true }) { Text("Later") }
        },
    )
}
