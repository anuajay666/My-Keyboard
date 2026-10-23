// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.widget.Toast
import org.json.JSONObject
import java.io.File

enum class Scope { SELECTION, ALL, CURSOR }

/** Text captured from the editor when the AI panel opens. */
class AiSession(val original: String, val scope: Scope)

class AiPending(val session: AiSession, val result: String, val insertOnly: Boolean)

/**
 * The AI panel runs in its own process (so a native crash in the model can never take the keyboard down),
 * which means the hand-over between keyboard and panel goes through small files.
 */
object AiBridge {
    private fun dir(ctx: Context) = File(ctx.filesDir, "ai").apply { mkdirs() }
    private fun sessionFile(ctx: Context) = File(dir(ctx), "session.json")
    private fun pendingFile(ctx: Context) = File(dir(ctx), "pending.json")

    fun writeSession(ctx: Context, s: AiSession) {
        sessionFile(ctx).writeText(JSONObject().put("t", s.original).put("s", s.scope.name).toString())
    }

    fun readSession(ctx: Context): AiSession {
        return try {
            val o = JSONObject(sessionFile(ctx).readText())
            AiSession(o.getString("t"), Scope.valueOf(o.getString("s")))
        } catch (_: Throwable) { AiSession("", Scope.CURSOR) }
    }

    @JvmStatic fun hasPending(ctx: Context) = pendingFile(ctx).exists()

    fun setPending(ctx: Context, p: AiPending) {
        val tmp = File(dir(ctx), "pending.tmp")
        tmp.writeText(JSONObject().put("o", p.session.original).put("s", p.session.scope.name).put("r", p.result).put("i", p.insertOnly).toString())
        tmp.renameTo(pendingFile(ctx))
    }

    fun takePending(ctx: Context): AiPending? {
        val f = pendingFile(ctx)
        if (!f.exists()) return null
        return try {
            val o = JSONObject(f.readText())
            AiPending(AiSession(o.getString("o"), Scope.valueOf(o.getString("s"))), o.getString("r"), o.getBoolean("i"))
        } catch (_: Throwable) { null } finally { f.delete() }
    }
}

/** Tiny persistent log so a crash can be diagnosed afterwards. */
object AiLog {
    private fun file(ctx: Context) = File(File(ctx.filesDir, "ai").apply { mkdirs() }, "log.txt")

    fun add(ctx: Context, msg: String) {
        try {
            val f = file(ctx)
            if (f.length() > 30_000) f.writeText(f.readText().takeLast(10_000))
            f.appendText("${java.text.SimpleDateFormat("dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())} $msg\n")
        } catch (_: Throwable) {}
    }

    fun read(ctx: Context): String = try { file(ctx).readText().takeLast(6000) } catch (_: Throwable) { "" }
    fun clear(ctx: Context) { try { file(ctx).delete() } catch (_: Throwable) {} }

    fun installCrashHandler(ctx: Context) {
        val app = ctx.applicationContext
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            add(app, "CRASH in ${t.name}: ${android.util.Log.getStackTraceString(e).take(2500)}")
            old?.uncaughtException(t, e)
        }
    }
}

object AiLauncher {
    private const val MAX_CHARS = 200_000

    @JvmStatic
    fun launch(ime: InputMethodService, tab: Int, autorun: Boolean) {
        val ic = ime.currentInputConnection
        var scope = Scope.CURSOR
        var text = ""
        if (ic != null) {
            val sel = ic.getSelectedText(0)?.toString().orEmpty()
            if (sel.isNotEmpty()) {
                text = sel; scope = Scope.SELECTION
            } else {
                val before = ic.getTextBeforeCursor(MAX_CHARS, 0)?.toString().orEmpty()
                val after = ic.getTextAfterCursor(MAX_CHARS, 0)?.toString().orEmpty()
                text = before + after
                scope = if (text.isNotEmpty()) Scope.ALL else Scope.CURSOR
            }
        }
        try { AiBridge.writeSession(ime, AiSession(text, scope)) } catch (e: Throwable) { AiLog.add(ime, "session write failed: $e") }
        ime.requestHideSelf(0)
        ime.startActivity(Intent(ime, AiActivity::class.java).putExtra("tab", tab).putExtra("autorun", autorun).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }

    @JvmStatic
    fun applyPending(ime: InputMethodService) {
        val p = AiBridge.takePending(ime) ?: return
        val ic = ime.currentInputConnection
        if (ic == null) { fallback(ime, p.result); return }
        var ok = false
        ic.beginBatchEdit()
        try {
            if (p.insertOnly || p.session.scope == Scope.CURSOR) {
                ok = ic.commitText(p.result, 1)
            } else when (p.session.scope) {
                Scope.SELECTION -> {
                    val sel = ic.getSelectedText(0)?.toString()
                    if (sel == p.session.original) ok = ic.commitText(p.result, 1)
                }
                Scope.ALL -> {
                    val before = ic.getTextBeforeCursor(MAX_CHARS, 0)?.toString().orEmpty()
                    val after = ic.getTextAfterCursor(MAX_CHARS, 0)?.toString().orEmpty()
                    if (before + after == p.session.original) {
                        ic.deleteSurroundingText(before.length, after.length)
                        ok = ic.commitText(p.result, 1)
                    }
                }
                else -> {}
            }
        } finally {
            ic.endBatchEdit()
        }
        if (!ok) fallback(ime, p.result)
    }

    private fun fallback(ctx: Context, result: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Inkly AI", result))
        Toast.makeText(ctx, "Text changed meanwhile - result copied, paste it manually", Toast.LENGTH_LONG).show()
    }
}
