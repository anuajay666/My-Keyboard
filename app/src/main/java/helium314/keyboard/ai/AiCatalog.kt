// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * How a task treats the text.
 * REWRITE: same content, different wording -> chunked, and guarded so nothing gets cut short.
 * TRANSLATE: chunked, language changes so length is not compared.
 * GENERATE: the text is an instruction / topic / message -> one call, result is new text.
 */
enum class Kind { REWRITE, TRANSLATE, GENERATE }

class AiTask(
    val id: String,
    val label: String,
    val instruction: String,
    val kind: Kind,
    /** inserted at the cursor instead of replacing the text (replies, new writing) */
    val insertOnly: Boolean = false,
    val temperature: Double = 0.3,
)

object AiCatalog {
    val fix = AiTask("fix", "Fix grammar", "Fix spelling, grammar and punctuation. Change as little else as possible.", Kind.REWRITE)
    val paraphrase = AiTask("paraphrase", "Paraphrase", "Paraphrase the text using different wording while keeping the exact meaning and every detail.", Kind.REWRITE, temperature = 0.8)
    val improve = AiTask("improve", "Improve writing", "Improve clarity, flow and word choice while keeping the meaning and every detail.", Kind.REWRITE)
    val expand = AiTask("expand", "Expand draft", "The text is a short draft or a few key points. Expand it into a complete, well written message that covers every point.", Kind.GENERATE, temperature = 0.6)
    val cleanup = AiTask("cleanup", "Clean voice text", "This text was dictated by voice. Remove filler words and false starts, add correct punctuation and capital letters, and turn spoken lists into proper lists. Keep all the content and the same language.", Kind.REWRITE)
    val polish = listOf(fix, paraphrase, improve, expand, cleanup)

    val toneNames = listOf(
        "Professional", "Casual", "Friendly", "Confident", "Formal", "Polite", "Persuasive",
        "Empathetic", "Witty", "Enthusiastic", "Direct", "Diplomatic", "Poetic",
    )
    val tones = toneNames.map { tone(it, "the $it tone") }
    fun tone(name: String, description: String) =
        AiTask("tone_$name", name, "Rewrite the text in $description. Keep the meaning and every detail.", Kind.REWRITE, temperature = 0.5)

    private fun assistant(label: String, instruction: String, insert: Boolean = false) =
        AiTask("as_$label", label, instruction, Kind.GENERATE, insertOnly = insert, temperature = 0.6)

    val assistants = listOf(
        assistant("Email writer", "Write a complete email based on the notes or request below. Include a subject line, greeting and sign-off."),
        assistant("Text message", "Write a short, natural text message based on the request below."),
        assistant("Social media post", "Write an engaging social media post based on the topic below."),
        assistant("LinkedIn post", "Write a professional LinkedIn post based on the topic below."),
        assistant("Tweet / short post", "Write a short post of under 280 characters based on the topic below."),
        assistant("Instagram caption", "Write a catchy Instagram caption with a few fitting hashtags based on the topic below."),
        assistant("Cover letter", "Write a tailored cover letter based on the details below."),
        assistant("Apology", "Write a sincere apology message based on the situation below."),
        assistant("Thank-you note", "Write a warm thank-you note based on the details below."),
        assistant("Complaint letter", "Write a firm but polite complaint letter based on the details below."),
        assistant("Product description", "Write a persuasive product description based on the details below."),
        assistant("Meeting follow-up", "Write a clear follow-up message with action items based on the meeting notes below."),
        assistant("Blog intro", "Write an engaging introduction paragraph for a blog post on the topic below."),
        assistant("Headline ideas", "Give 8 attention-grabbing headline ideas for the topic below, one per line."),
        assistant("Summarize", "Summarize the text below clearly in a few sentences."),
        assistant("Bullet points", "Turn the text below into clear bullet points that keep every important detail."),
        assistant("Simplify", "Explain the text below in very simple words that a child could follow, without losing the main points."),
        assistant("Make it longer", "Make the text below longer and richer with more detail, keeping the same meaning and style."),
        assistant("Proofread report", "List every spelling, grammar and style problem in the text below, then give the corrected text."),
        assistant("Poem", "Write a short poem about the topic below."),
        assistant("Cold outreach", "Write a friendly, concise cold outreach message based on the details below."),
        assistant("Resignation letter", "Write a polite, professional resignation letter based on the details below."),
        assistant("Birthday wish", "Write a heartfelt birthday wish based on the details below."),
        assistant("Ask AI anything", "Answer the question or follow the request below. Be accurate and concise."),
    )

    val languages = listOf(
        "Malayalam", "English", "Tamil", "Hindi", "Telugu", "Kannada", "Marathi", "Bengali", "Gujarati", "Punjabi",
        "Urdu", "Arabic", "Spanish", "French", "German", "Italian", "Portuguese", "Russian", "Chinese (Simplified)",
        "Chinese (Traditional)", "Japanese", "Korean", "Turkish", "Dutch", "Polish", "Swedish", "Norwegian", "Danish",
        "Finnish", "Greek", "Hebrew", "Thai", "Vietnamese", "Indonesian", "Malay", "Filipino", "Swahili", "Ukrainian",
        "Czech", "Romanian", "Hungarian", "Persian", "Sinhala", "Nepali",
    )

    fun translate(lang: String) = AiTask("tr_$lang", lang, "Translate the text into $lang. Keep the tone, meaning and formatting.", Kind.TRANSLATE)

    val replyStyles = listOf(
        AiTask("reply_friendly", "Friendly", "Write a warm, friendly reply to the message below.", Kind.GENERATE, true, 0.7),
        AiTask("reply_pro", "Professional", "Write a polite, professional reply to the message below.", Kind.GENERATE, true, 0.5),
        AiTask("reply_short", "Short", "Write a very short reply (one sentence) to the message below.", Kind.GENERATE, true, 0.6),
    )
}

/** User-made tones and assistants, stored on the phone only. */
object AiCustom {
    private const val PREFS = "inkly_ai"
    class Item(val name: String, val instruction: String)

    fun load(ctx: Context, key: String): List<Item> {
        val raw = ctx.getSharedPreferences(PREFS, 0).getString(key, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it).let { o -> Item(o.getString("n"), o.getString("i")) } }
        } catch (_: Exception) { emptyList() }
    }

    fun save(ctx: Context, key: String, items: List<Item>) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("n", it.name).put("i", it.instruction)) }
        ctx.getSharedPreferences(PREFS, 0).edit().putString(key, arr.toString()).apply()
    }

    const val TONES = "custom_tones"
    const val ASSISTANTS = "custom_assistants"
}
