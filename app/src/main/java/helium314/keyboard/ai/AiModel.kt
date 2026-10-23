// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.ai

import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed interface ModelState {
    data object Missing : ModelState
    data class Downloading(val done: Long, val total: Long) : ModelState
    data class Failed(val message: String) : ModelState
    data object Ready : ModelState
}

/** Downloads / imports the on-device Gemma model and keeps its state. */
object AiModelManager {
    const val DEFAULT_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
    private const val PREFS = "inkly_ai"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _state = MutableStateFlow<ModelState>(ModelState.Missing)
    val state: StateFlow<ModelState> = _state

    fun modelFile(ctx: Context) = File(File(ctx.filesDir, "ai").apply { mkdirs() }, "model.litertlm")
    private fun partFile(ctx: Context) = File(modelFile(ctx).parentFile, "model.part")

    fun url(ctx: Context): String = ctx.getSharedPreferences(PREFS, 0).getString("url", DEFAULT_URL) ?: DEFAULT_URL
    fun token(ctx: Context): String = ctx.getSharedPreferences(PREFS, 0).getString("token", "") ?: ""
    fun save(ctx: Context, url: String, token: String) {
        ctx.getSharedPreferences(PREFS, 0).edit().putString("url", url.trim()).putString("token", token.trim()).apply()
    }

    fun refresh(ctx: Context) {
        if (_state.value is ModelState.Downloading) return
        _state.value = if (modelFile(ctx).let { it.exists() && it.length() > 50_000_000 }) ModelState.Ready else ModelState.Missing
    }

    fun cancel() { job?.cancel() }

    fun delete(ctx: Context) {
        AiEngine.close()
        modelFile(ctx).delete(); partFile(ctx).delete()
        _state.value = ModelState.Missing
    }

    fun importFrom(ctx: Context, uri: Uri) {
        if (_state.value is ModelState.Downloading) return
        val app = ctx.applicationContext
        job = scope.launch {
            try {
                val part = partFile(app)
                val total = app.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
                _state.value = ModelState.Downloading(0, total)
                app.contentResolver.openInputStream(uri)!!.use { input ->
                    FileOutputStream(part).use { out ->
                        val buf = ByteArray(1 shl 20); var done = 0L
                        while (isActive) {
                            val n = input.read(buf); if (n < 0) break
                            out.write(buf, 0, n); done += n
                            _state.value = ModelState.Downloading(done, total)
                        }
                    }
                }
                if (!isActive) { part.delete(); _state.value = ModelState.Missing; return@launch }
                modelFile(app).delete()
                part.renameTo(modelFile(app))
                _state.value = ModelState.Ready
            } catch (e: Throwable) {
                _state.value = ModelState.Failed(e.message ?: e.toString())
            }
        }
    }

    fun download(ctx: Context) {
        if (_state.value is ModelState.Downloading) return
        val app = ctx.applicationContext
        job = scope.launch {
            try {
                val part = partFile(app)
                var resumeFrom = if (part.exists()) part.length() else 0L
                var current = url(app)
                val tok = token(app)
                val firstHost = URL(current).host
                var conn: HttpURLConnection
                var hops = 0
                while (true) {
                    conn = URL(current).openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = false
                    conn.connectTimeout = 20000; conn.readTimeout = 30000
                    if (tok.isNotEmpty() && URL(current).host == firstHost) conn.setRequestProperty("Authorization", "Bearer $tok")
                    if (resumeFrom > 0) conn.setRequestProperty("Range", "bytes=$resumeFrom-")
                    val code = conn.responseCode
                    if (code in 300..399 && hops++ < 6) {
                        current = URL(URL(current), conn.getHeaderField("Location")).toString(); conn.disconnect(); continue
                    }
                    break
                }
                val code = conn.responseCode
                if (code == 401 || code == 403) throw RuntimeException("Access denied ($code). The model page may need you to accept its licence and give a Hugging Face token, or pick the file manually.")
                if (code == 416) { part.renameTo(modelFile(app)); _state.value = ModelState.Ready; return@launch }
                if (code != 200 && code != 206) throw RuntimeException("Download failed: HTTP $code")
                if (code == 200) resumeFrom = 0
                val total = (conn.contentLengthLong.takeIf { it > 0 } ?: -1L).let { if (it > 0) it + resumeFrom else it }
                _state.value = ModelState.Downloading(resumeFrom, total)
                conn.inputStream.use { input ->
                    FileOutputStream(part, resumeFrom > 0).use { out ->
                        val buf = ByteArray(1 shl 20); var done = resumeFrom; var lastEmit = 0L
                        while (isActive) {
                            val n = input.read(buf); if (n < 0) break
                            out.write(buf, 0, n); done += n
                            val now = System.currentTimeMillis()
                            if (now - lastEmit > 400) { _state.value = ModelState.Downloading(done, total); lastEmit = now }
                        }
                    }
                }
                if (!isActive) { _state.value = ModelState.Missing; return@launch } // keep .part for resume
                modelFile(app).delete()
                if (!part.renameTo(modelFile(app))) throw RuntimeException("Could not store model file")
                _state.value = ModelState.Ready
            } catch (e: Throwable) {
                _state.value = ModelState.Failed(e.message ?: e.toString())
            }
        }
    }
}

/** Wraps the LiteRT-LM engine. One model load, many short single-turn conversations. */
object AiEngine {
    private var engine: Engine? = null
    @Volatile private var active: Conversation? = null
    private val lock = Any()
    private var lastUse = 0L
    private val idleScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var idleJob: Job? = null

    private fun ensure(ctx: Context): Engine = synchronized(lock) {
        engine?.let { return it }
        val prefs = ctx.getSharedPreferences("inkly_ai", 0)
        val path = AiModelManager.modelFile(ctx).absolutePath
        // if the previous start never finished, the native code crashed: use the gentler setting this time
        val crashedBefore = prefs.getBoolean("engine_loading", false)
        if (crashedBefore) AiLog.add(ctx, "previous engine start did not finish (native crash?) - using safe settings")
        val attempts = listOf(
            "full" to EngineConfig(modelPath = path, backend = Backend.CPU(), maxNumTokens = 4096, cacheDir = ctx.cacheDir.absolutePath),
            "safe" to EngineConfig(modelPath = path, backend = Backend.CPU(), maxNumTokens = 2048),
        ).let { if (crashedBefore) it.reversed() else it }
        var last: Throwable? = null
        for ((name, cfg) in attempts) {
            try {
                AiLog.add(ctx, "loading model ($name) ${File(path).length() / 1_000_000} MB")
                prefs.edit().putBoolean("engine_loading", true).commit()
                val e = Engine(cfg)
                e.initialize()
                prefs.edit().putBoolean("engine_loading", false).commit()
                AiLog.add(ctx, "model loaded ($name)")
                engine = e
                return e
            } catch (t: Throwable) {
                prefs.edit().putBoolean("engine_loading", false).commit()
                AiLog.add(ctx, "load failed ($name): $t")
                last = t
            }
        }
        throw RuntimeException("Could not load the AI model: ${last?.message ?: last}. The file may be damaged or not suited to this phone - use 'Remove AI model', then download or pick it again.", last)
    }

    /** Blocking. Call from a background thread. */
    fun generate(ctx: Context, prompt: String, temperature: Double = 0.3): String {
        val e = ensure(ctx.applicationContext)
        AiLog.add(ctx, "generating (${prompt.length} chars)")
        lastUse = System.currentTimeMillis()
        scheduleIdleClose()
        val conv = e.createConversation(ConversationConfig(samplerConfig = SamplerConfig(topK = 40, topP = 0.9, temperature = temperature)))
        active = conv
        try {
            val out = conv.sendMessage(prompt).toString()
            AiLog.add(ctx, "generated ${out.length} chars")
            return out.replace(Regex("(?s)<think>.*?</think>"), "").trim()
        } finally {
            active = null
            conv.close()
            lastUse = System.currentTimeMillis()
        }
    }

    fun cancel() { try { active?.cancelProcess() } catch (_: Throwable) {} }

    fun close() = synchronized(lock) {
        try { engine?.close() } catch (_: Throwable) {}
        engine = null
    }

    // free the ~3 GB of RAM a few minutes after the last use
    private fun scheduleIdleClose() {
        idleJob?.cancel()
        idleJob = idleScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(30_000)
                if (active == null && System.currentTimeMillis() - lastUse > 180_000) { close(); break }
            }
        }
    }
}
