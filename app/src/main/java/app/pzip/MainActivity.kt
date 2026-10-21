package app.pzip

import android.content.Intent
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import app.pzip.core.Files
import app.pzip.core.Prefs
import app.pzip.ui.App
import app.pzip.ui.AppLock
import app.pzip.ui.Nav
import app.pzip.ui.theme.PzipTheme
import app.pzip.ui.theme.isDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && Prefs.appLock && Prefs.pinHash.isNotEmpty()) AppLock.locked = true
        AppLock.biometric = { showBiometric() }
        setContent {
            PzipTheme {
                val dark = isDarkTheme()
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                LaunchedEffect(Prefs.blockScreenshots) {
                    if (Prefs.blockScreenshots) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                App()
            }
        }
        if (savedInstanceState == null) handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStop() {
        super.onStop()
        AppLock.backgroundAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        val away = System.currentTimeMillis() - AppLock.backgroundAt
        if (AppLock.backgroundAt > 0 && away > 60_000 && Prefs.appLock && Prefs.pinHash.isNotEmpty()) AppLock.locked = true
    }

    private fun showBiometric() {
        if (Build.VERSION.SDK_INT < 28 || !Prefs.useBiometric) return
        runCatching {
            BiometricPrompt.Builder(this)
                .setTitle("Unlock pzip")
                .setNegativeButton("Use PIN", mainExecutor) { _, _ -> }
                .build()
                .authenticate(CancellationSignal(), mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        AppLock.locked = false
                    }
                })
        }
    }

    /** Opens archives and media shared or "opened with" from other apps. */
    private fun handle(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { uri ->
                lifecycleScope.launch {
                    val f = withContext(Dispatchers.IO) { resolve(uri) } ?: return@launch
                    Nav.open(this@MainActivity, f)
                }
            }
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> {
                val uris = mutableListOf<Uri>()
                if (intent.action == Intent.ACTION_SEND) {
                    @Suppress("DEPRECATION")
                    (intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { uris += it }
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris += it }
                }
                if (uris.isEmpty()) return
                lifecycleScope.launch {
                    val files = withContext(Dispatchers.IO) { uris.mapNotNull { resolve(it) } }
                    if (files.isNotEmpty()) Nav.compress = files
                }
            }
        }
    }

    /** Turns a file:// or content:// link into a real file, copying it to the cache when needed. */
    private fun resolve(uri: Uri): File? = runCatching {
        if (uri.scheme == "file") return@runCatching File(uri.path!!).canonicalFile
        // With all-files access many providers expose a readable path.
        contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { File(it).canonicalFile }?.takeIf { it.canRead() }?.let { return@runCatching it }
        }
        val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "shared_${System.currentTimeMillis()}"
        val dir = File(cacheDir, "incoming").also { it.mkdirs() }
        val out = Files.uniqueFile(dir, name.replace('/', '_'))
        contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it, 256 * 1024) } }
        out
    }.getOrNull()
}
