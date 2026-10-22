// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.ai

import android.content.Context

class AiResult(val options: List<String>, val notes: List<String>)

object AiRunner {
    private const val CHUNK = 900
    private const val GENERATE_INPUT_LIMIT = 6000

    @Volatile var cancelled = false

    private fun prompt(task: AiTask, text: String): String = buildString {
        append("You are a text tool inside a phone keyboard.\n")
        append("Task: ").append(task.instruction).append('\n')
        append("Rules: Output ONLY the resulting text. No introduction, no quotes, no explanations, no markdown headings. ")
        append("Reply in the same language as the input unless the task says otherwise. ")
        if (task.kind != Kind.GENERATE) append("Never summarize or shorten. Keep every sentence, name, number, emoji and line break. ")
        append("\n\nINPUT:\n").append(text).append("\n\nOUTPUT:\n")
    }

    private fun clean(s: String): String {
        var t = s.trim()
        t = t.removePrefix("OUTPUT:").trim()
        if (t.length > 1 && t.startsWith("\"") && t.endsWith("\"") && !t.substring(1, t.length - 1).contains('"')) t = t.substring(1, t.length - 1)
        return t
    }

    /** Split into pieces of about [CHUNK] chars along line and sentence boundaries, keeping all whitespace. */
    fun chunk(text: String): List<String> {
        if (text.length <= CHUNK) return listOf(text)
        val pieces = mutableListOf<String>()
        // lines, keeping the newline with the line
        val lines = Regex("[^\n]*\n|[^\n]+$").findAll(text).map { it.value }.toList()
        val units = mutableListOf<String>()
        for (line in lines) {
            if (line.length <= CHUNK) { units += line; continue }
            // sentences
            var rest = line
            while (rest.length > CHUNK) {
                var cut = Regex("[.!?।।](\\s+|$)").findAll(rest.substring(0, CHUNK)).lastOrNull()?.range?.last?.plus(1) ?: -1
                if (cut < CHUNK / 3) cut = rest.lastIndexOf(' ', CHUNK).takeIf { it > CHUNK / 3 }?.plus(1) ?: CHUNK
                units += rest.substring(0, cut); rest = rest.substring(cut)
            }
            if (rest.isNotEmpty()) units += rest
        }
        val sb = StringBuilder()
        for (u in units) {
            if (sb.isNotEmpty() && sb.length + u.length > CHUNK) { pieces += sb.toString(); sb.clear() }
            sb.append(u)
        }
        if (sb.isNotEmpty()) pieces += sb.toString()
        return pieces
    }

    /**
     * Runs a task. Blocking, call off the main thread. [progress] gets (part, total, textSoFar).
     * For REWRITE tasks a piece that comes back much shorter than it went in is kept unchanged instead,
     * so text can never silently lose content.
     */
    fun run(ctx: Context, task: AiTask, input: String, progress: (Int, Int, String) -> Unit): AiResult {
        cancelled = false
        val notes = mutableListOf<String>()
        if (task.kind == Kind.GENERATE) {
            val text = if (input.length > GENERATE_INPUT_LIMIT) input.takeLast(GENERATE_INPUT_LIMIT).also { notes += "Used the last $GENERATE_INPUT_LIMIT characters." } else input
            progress(1, 1, "")
            val out = clean(AiEngine.generate(ctx, prompt(task, text.trim()), task.temperature))
            if (out.isEmpty()) throw RuntimeException("The model returned nothing. Try again.")
            return AiResult(listOf(out), notes)
        }
        val parts = chunk(input)
        val result = StringBuilder()
        parts.forEachIndexed { i, part ->
            if (cancelled) throw InterruptedException()
            progress(i + 1, parts.size, result.toString())
            val core = part.trim()
            if (core.isEmpty()) { result.append(part); return@forEachIndexed }
            val lead = part.substring(0, part.indexOf(core))
            val trail = part.substring(part.indexOf(core) + core.length)
            val out = try { clean(AiEngine.generate(ctx, prompt(task, core), task.temperature)) } catch (e: Throwable) { if (cancelled) throw InterruptedException(); throw e }
            val tooShort = task.kind == Kind.REWRITE && core.length > 40 && out.length < core.length * 0.6
            val tooLong = task.kind == Kind.REWRITE && out.length > core.length * 2.5 + 40
            if (out.isEmpty() || tooShort || tooLong) {
                notes += "Part ${i + 1} of ${parts.size} came back unreliable, so it was kept as you wrote it."
                result.append(part)
            } else {
                result.append(lead).append(out).append(trail)
            }
        }
        return AiResult(listOf(result.toString()), notes)
    }
}
