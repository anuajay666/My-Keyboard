// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.ai

import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.widget.Toast
import android.content.ClipData
import android.content.ClipboardManager

enum class Scope { SELECTION, ALL, CURSOR }

/** Text captured from the editor when the AI panel opens, and the result to put back afterwards. */
class AiSession(val original: String, val scope: Scope)

class AiPending(val session: AiSession, val result: String, val insertOnly: Boolean)

object AiBridge {
    @Volatile var session: AiSession? = null
    @Volatile private var pending: AiPending? = null

    fun hasPending() = pending != null
    fun setPending(p: AiPending) { pending = p }
    fun takePending(): AiPending? { val p = pending; pending = null; return p }
}

object AiLauncher {
    private const val MAX_CHARS = 200_000

    @JvmStatic
    fun launch(ime: InputMethodService) {
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
        AiBridge.session = AiSession(text, scope)
        ime.requestHideSelf(0)
        ime.startActivity(Intent(ime, AiActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }

    @JvmStatic
    fun applyPending(ime: InputMethodService) {
        val p = AiBridge.takePending() ?: return
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
