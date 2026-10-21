package app.pzip

import android.app.Application
import app.pzip.core.FileOps
import app.pzip.core.Prefs
import app.pzip.core.SafeVault
import app.pzip.core.Trash
import app.pzip.core.tasks.OperationService
import app.pzip.core.tasks.TaskManager
import app.pzip.player.Engine
import java.io.File
import kotlin.concurrent.thread

class PzipApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        FileOps.init(this)
        TaskManager.init(this)
        Engine.init(this)
        OperationService.ensureChannels(this)
        thread(name = "pzip-startup", isDaemon = true) {
            // Decrypted Safe folder previews and archive previews never outlive a session.
            SafeVault.clearViewCache()
            File(cacheDir, "preview").deleteRecursively()
            File(cacheDir, "convert").deleteRecursively()
            Trash.load()
        }
    }
}
