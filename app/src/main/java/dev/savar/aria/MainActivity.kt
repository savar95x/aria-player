package dev.savar.aria

import android.os.Bundle
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
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            settings?.let { s ->
                AriaTheme(s) { AriaRoot(container) }
            }
        }
    }
}
