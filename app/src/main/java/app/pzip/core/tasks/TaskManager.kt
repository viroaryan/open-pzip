package app.pzip.core.tasks

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.pzip.core.archive.CancelledException
import app.pzip.core.archive.PasswordRequiredException
import app.pzip.core.archive.Progress
import app.pzip.core.archive.WrongPasswordException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

enum class TaskKind(val verb: String, val done: String) {
    COMPRESS("Compressing", "Compressed"),
    EXTRACT("Extracting", "Extracted"),
    TEST("Testing", "Tested"),
    CONVERT("Converting", "Converted"),
    COPY("Copying", "Copied"),
    MOVE("Moving", "Moved"),
    DELETE("Deleting", "Deleted"),
    SHRED("Shredding", "Shredded"),
    EDIT("Updating archive", "Archive updated"),
    SAFE("Securing", "Secured"),
}

enum class TaskState { RUNNING, DONE, FAILED, CANCELLED }

/** A long-running operation with observable progress. */
class PzipTask(val id: Long, val kind: TaskKind, val title: String) : Progress {
    var state by mutableStateOf(TaskState.RUNNING)
    var fraction by mutableFloatStateOf(0f)
    var processed by mutableLongStateOf(0L)
    var totalBytes by mutableLongStateOf(0L)
    var currentFile by mutableStateOf("")
    var speed by mutableLongStateOf(0L)
    var message by mutableStateOf("")
    var output by mutableStateOf<File?>(null)
    var needsPassword by mutableStateOf(false)
    var filesDone by mutableIntStateOf(0)
    val started = System.currentTimeMillis()
    var finished by mutableLongStateOf(0L)
    /** Re-runs the operation, e.g. after asking for the right password. */
    var retry: (() -> Unit)? = null

    @Volatile private var cancelFlag = false
    private val done = AtomicLong(0)
    @Volatile private var total = 0L
    @Volatile private var lastPublish = 0L
    @Volatile private var name = ""
    private val startClock = SystemClock.elapsedRealtime()

    override val cancelled: Boolean get() = cancelFlag
    fun cancel() {
        cancelFlag = true
    }

    override fun total(bytes: Long) {
        total = bytes
        publish(true)
    }

    override fun add(bytes: Long) {
        done.addAndGet(bytes)
        publish(false)
    }

    override fun file(name: String) {
        this.name = name
        publish(false)
    }

    /** Throttled state updates keep the UI smooth even with tiny buffers. */
    private fun publish(force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastPublish < 120) return
        lastPublish = now
        val d = done.get()
        processed = d
        totalBytes = total
        currentFile = name
        fraction = if (total > 0) (d.toFloat() / total).coerceIn(0f, 1f) else 0f
        val elapsed = (now - startClock).coerceAtLeast(1)
        speed = d * 1000 / elapsed
    }

    val etaMillis: Long get() = if (speed > 0 && totalBytes > processed) (totalBytes - processed) * 1000 / speed else -1

    internal fun finish(s: TaskState, msg: String) {
        publish(true)
        if (s == TaskState.DONE) fraction = 1f
        state = s
        message = msg
        finished = System.currentTimeMillis()
    }
}

object TaskManager {
    val tasks = mutableStateListOf<PzipTask>()
    /** Bumped when a task finishes so file lists can refresh. */
    var version by mutableIntStateOf(0)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var nextId = 1L
    private lateinit var app: Context

    fun init(context: Context) {
        app = context.applicationContext
    }

    val running: List<PzipTask> get() = tasks.filter { it.state == TaskState.RUNNING }

    /**
     * Starts [block] in the background. It returns a short success message.
     * [onDone] runs on the main thread after success.
     */
    fun start(kind: TaskKind, title: String, onDone: (PzipTask) -> Unit = {}, block: suspend (PzipTask) -> String): PzipTask {
        val task = PzipTask(nextId++, kind, title)
        tasks.add(0, task)
        startService()
        scope.launch {
            val (state, msg) = try {
                TaskState.DONE to block(task)
            } catch (e: CancelledException) {
                TaskState.CANCELLED to "Cancelled"
            } catch (e: PasswordRequiredException) {
                task.needsPassword = true
                TaskState.FAILED to "Password required"
            } catch (e: WrongPasswordException) {
                task.needsPassword = true
                TaskState.FAILED to "Wrong password"
            } catch (e: OutOfMemoryError) {
                TaskState.FAILED to "Not enough memory"
            } catch (e: Exception) {
                TaskState.FAILED to (e.message ?: e.javaClass.simpleName)
            }
            withContext(Dispatchers.Main) {
                task.finish(state, msg)
                version++
                if (state == TaskState.DONE) onDone(task)
                OperationService.notifyFinished(app, task)
            }
        }
        return task
    }

    fun clearFinished() {
        tasks.removeAll { it.state != TaskState.RUNNING }
    }

    fun cancelAll() {
        running.forEach { it.cancel() }
    }

    fun refresh() {
        version++
    }

    private fun startService() {
        val intent = Intent(app, OperationService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(intent) else app.startService(intent)
        }
    }
}
