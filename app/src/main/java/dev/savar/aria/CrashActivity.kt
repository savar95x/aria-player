package dev.savar.aria

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.Gravity
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.system.exitProcess

/**
 * Plain-View crash screen (deliberately no Compose, so it works even when the crash is in Compose).
 * Shows the stack trace with Copy / Restart, so a crash can be reported instead of just "it closed".
 */
class CrashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = intent.getStringExtra(EXTRA_REPORT).orEmpty()
        val pad = (16 * resources.displayMetrics.density).toInt()

        val title = TextView(this).apply {
            text = "Aria crashed"
            textSize = 22f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        }
        val hint = TextView(this).apply {
            text = "Copy this and send it over so it can be fixed."
            setTextColor(Color.LTGRAY)
            setPadding(0, pad / 2, 0, pad)
        }
        val trace = TextView(this).apply {
            text = report
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.rgb(230, 230, 230))
            setTextIsSelectable(true)
        }
        val copy = Button(this).apply {
            text = "Copy"
            setOnClickListener {
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("Aria crash", report))
                Toast.makeText(this@CrashActivity, "Copied", Toast.LENGTH_SHORT).show()
            }
        }
        val restart = Button(this).apply {
            text = "Restart"
            setOnClickListener {
                startActivity(
                    Intent(this@CrashActivity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                )
                finish()
            }
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            addView(copy)
            addView(restart)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 3, pad, pad * 2)
            addView(title)
            addView(hint)
            addView(buttons)
            addView(HorizontalScrollView(this@CrashActivity).apply { addView(trace) })
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(content)
        })
    }

    companion object {
        private const val EXTRA_REPORT = "report"

        fun install(app: Application) {
            if (Application.getProcessName().endsWith(":crash")) return
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                try {
                    val report = buildString {
                        appendLine("Aria ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                        appendLine("${Build.MANUFACTURER} ${Build.MODEL} · thread ${thread.name}")
                        appendLine()
                        append(error.stackTraceToString())
                    }.take(60_000)
                    app.startActivity(
                        Intent(app, CrashActivity::class.java)
                            .putExtra(EXTRA_REPORT, report)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                    )
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                } catch (t: Throwable) {
                    previous?.uncaughtException(thread, error)
                }
            }
        }
    }
}
