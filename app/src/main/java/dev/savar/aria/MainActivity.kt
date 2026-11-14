package dev.savar.aria

import android.content.Intent
import android.os.Bundle
import dev.savar.aria.data.Links
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.savar.aria.ui.AriaRoot
import dev.savar.aria.ui.theme.AriaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = container
        handleIntent(intent)
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            settings?.let { s ->
                AriaTheme(s) { AriaRoot(container) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        } ?: return
        Links.find(text)?.let { container.pendingLink.value = it }
    }
}
