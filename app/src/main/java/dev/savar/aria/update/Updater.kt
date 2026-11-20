package dev.savar.aria.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import dev.savar.aria.BuildConfig
import dev.savar.aria.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updates from the GitHub Releases page (the same releases the owner installs by hand).
 *
 * - [checkOnLaunch]: when the app opens and there's internet, asks GitHub for the latest release
 *   (`releases/latest` skips the `ci-log` pre-release) and compares its tag with this build.
 * - [install]: downloads the APK once into `filesDir/updates/aria-<tag>.apk` (resumable via a
 *   `.part` file, reused if already complete, every other file there deleted), then hands it to
 *   [PackageInstaller]. Same signing key, so it installs over the current app.
 * - Once the running version has caught up, the next check empties `updates/`, so nothing piles up.
 *
 * After the first in-app update Aria is its own "installer of record", and on Android 12+ later
 * updates can install without the confirmation sheet ([PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED]).
 */
class Updater(private val context: Context, private val scope: CoroutineScope) {

    data class Release(val tag: String, val version: String, val notes: String, val apkUrl: String, val size: Long)

    sealed interface State {
        data object Idle : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val progress: Float) : State
        data class Installing(val release: Release) : State
        /** Android needs "Install unknown apps" switched on for Aria first. */
        data class NeedsPermission(val release: Release) : State
        data class Failed(val release: Release, val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    /** The prompt was closed with "Later": stays quiet until the next launch (Settings can reopen it). */
    val dismissed = MutableStateFlow(false)

    private val dir get() = File(context.filesDir, "updates")
    private var lastCheck = 0L
    private var job: Job? = null

    fun checkOnLaunch() {
        val now = SystemClock.elapsedRealtime()
        if (lastCheck != 0L && now - lastCheck < 10 * 60_000) return // once per launch, not per recomposition
        if (!online()) return
        lastCheck = now
        scope.launch {
            val release = runCatching { withContext(Dispatchers.IO) { fetchLatest() } }.getOrNull() ?: return@launch
            if (isNewer(release.version, BuildConfig.VERSION_NAME)) {
                withContext(Dispatchers.IO) { dir.listFiles()?.filter { !it.name.startsWith(fileStem(release)) }?.forEach { it.delete() } }
                if (_state.value is State.Idle) {
                    _state.value = State.Available(release)
                    dismissed.value = false
                }
            } else {
                // Up to date: whatever was downloaded for the update we're now running is done with.
                withContext(Dispatchers.IO) { dir.deleteRecursively() }
                _state.value = State.Idle
            }
        }
    }

    fun install() {
        val release = when (val s = _state.value) {
            is State.Available -> s.release
            is State.NeedsPermission -> s.release
            is State.Failed -> s.release
            else -> return
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = State.NeedsPermission(release)
            return
        }
        job?.cancel()
        job = scope.launch {
            try {
                val apk = withContext(Dispatchers.IO) { download(release) }
                _state.value = State.Installing(release)
                withContext(Dispatchers.IO) { commit(apk) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = State.Failed(release, e.message ?: "Download failed")
            }
        }
    }

    /** Opens Android's "Install unknown apps" switch for Aria. */
    fun openInstallPermission() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Back from the permission screen: carry on if it was granted. */
    fun onResume() {
        if (_state.value is State.NeedsPermission && context.packageManager.canRequestPackageInstalls()) install()
    }

    internal fun onInstallResult(status: Int, message: String?) {
        val release = (_state.value as? State.Installing)?.release ?: return
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> Unit // the app is being replaced
            PackageInstaller.STATUS_FAILURE_ABORTED -> _state.value = State.Available(release) // "Cancel" on the sheet
            else -> _state.value = State.Failed(release, message ?: "Install failed")
        }
    }

    // --- network ---

    private fun online(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun fetchLatest(): Release? {
        val conn = open("https://api.github.com/repos/$REPO/releases/latest")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (conn.responseCode !in 200..299) return null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name")
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    return Release(
                        tag = tag,
                        version = tag.removePrefix("v"),
                        notes = json.optString("body").trim(),
                        apkUrl = a.getString("browser_download_url"),
                        size = a.optLong("size"),
                    )
                }
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    /** Downloads once; a finished file for this tag is reused, a partial one is resumed. */
    private fun download(release: Release): File {
        dir.mkdirs()
        val done = File(dir, "${fileStem(release)}.apk")
        if (done.exists() && (release.size <= 0 || done.length() == release.size)) return done
        val part = File(dir, "${fileStem(release)}.part")
        var have = if (part.exists()) part.length() else 0L
        if (release.size > 0 && have >= release.size) { part.delete(); have = 0 }
        val conn = open(release.apkUrl)
        if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
        try {
            val resumed = conn.responseCode == 206
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            if (!resumed) have = 0
            val total = if (release.size > 0) release.size else have + conn.contentLengthLong
            conn.inputStream.use { input ->
                java.io.FileOutputStream(part, resumed).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var lastEmit = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        have += n
                        val now = SystemClock.elapsedRealtime()
                        if (total > 0 && now - lastEmit > 100) {
                            lastEmit = now
                            _state.value = State.Downloading(release, (have.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (release.size > 0 && part.length() != release.size) error("Download incomplete")
        if (!part.renameTo(done)) error("Couldn't save the update")
        return done
    }

    private fun commit(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("aria.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = PendingIntent.getBroadcast(
                context, id,
                Intent(context, InstallResultReceiver::class.java),
                // Mutable: the installer fills in the status extras.
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 20_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Aria/${BuildConfig.VERSION_NAME} (+https://github.com/$REPO)")
    }

    private fun fileStem(release: Release) = "aria-${release.tag}"

    companion object {
        private const val REPO = "savar95x/aria-player"

        /** "1.1.12" > "1.1.9", numerically per part. */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
            val b = current.split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}

/** PackageInstaller's answer: shows the confirmation sheet when Android asks for one. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
            context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        context.container.updater.onInstallResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}
