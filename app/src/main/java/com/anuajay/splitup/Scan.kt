package com.anuajay.splitup

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

data class Draft(val title: String, val amount: Long, val include: Boolean)

/** Reads a screenshot on the phone (no internet, nothing leaves the phone) and turns the text into expense drafts. */
object Scan {
    /** Text found in the image, one line per row. Returns null if it could not be read. */
    suspend fun ocr(ctx: Context, uri: Uri): String? = suspendCancellableCoroutine { cont ->
        try {
            val img = InputImage.fromFilePath(ctx, uri)
            val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            rec.process(img)
                .addOnSuccessListener { t -> cont.resume(t.textBlocks.flatMap { it.lines }.joinToString("\n") { it.text }) }
                .addOnFailureListener { cont.resume(null) }
        } catch (_: Throwable) { cont.resume(null) }
    }

    private val num = Regex("""\d[\d,]*(?:\.\d+)?""")
    private val time = Regex("""\b\d{1,2}:\d{2}\b""")
    private val date = Regex("""\b\d{1,2}[/-]\d{1,2}[/-]\d{2,4}\b""")

    private fun paise(s: String): Long? = Money.parse(s.replace(",", ""))

    /** One expense per line like "Lunch - Saturday- 2153/-" or "Rent = 10500+24500=35,000/-". */
    fun expenses(raw: String): List<Draft> {
        val cleaned = raw.replace("₹", " ").replace("/-", " ").replace(Regex("""(?i)\brs\.?"""), " ").replace(Regex("""(?i)\binr\b"""), " ")
        val lines = cleaned.lines().map { it.trim() }.filter { it.isNotEmpty() }
        // a title line that ends with "=" continues on the next line
        val merged = ArrayList<String>()
        var i = 0
        while (i < lines.size) {
            var l = lines[i]
            while (i + 1 < lines.size && (l.endsWith("=") || l.endsWith(":")) && !l.any { it.isDigit() }) { i++; l = l + " " + lines[i] }
            merged += l; i++
        }
        val out = ArrayList<Draft>()
        for (l in merged) {
            if (!l.any { it.isDigit() } || time.containsMatchIn(l) || date.containsMatchIn(l)) continue
            val amount: Long?
            val title: String
            if (l.contains('=')) {
                title = l.substringBefore('=')
                amount = num.find(l.substringAfterLast('='))?.value?.let { paise(it) }
                    ?: num.findAll(l.substringAfter('=')).lastOrNull()?.value?.let { paise(it) }
            } else {
                val firstDigit = l.indexOfFirst { it.isDigit() }
                title = l.substring(0, firstDigit)
                val tail = l.substring(firstDigit)
                amount = if (tail.contains('+') && Regex("""^[\d,.\s+]+$""").matches(tail))
                    tail.split('+').mapNotNull { paise(it.trim()) }.sum().takeIf { it > 0 }
                else num.findAll(tail).lastOrNull()?.value?.let { paise(it) }
            }
            val t = title.trim().trim('-', '=', ':', '–', '•', '.', ' ').trim()
            if (t.length < 2 || amount == null || amount <= 0) continue
            val isTotal = t.lowercase().let { it.startsWith("total") || it.startsWith("grand") || it.startsWith("sum") }
            out += Draft(t, amount, !isTotal)
        }
        return out
    }

    /** "Total Expense Anush" style header → the name of whoever paid. */
    fun payerName(raw: String): String? =
        Regex("""(?i)total\s+expenses?\s*[:\-]?\s*([A-Za-z]{2,})""").find(raw)?.groupValues?.get(1)

    fun looksLikePayment(raw: String): Boolean =
        Regex("""(?i)(paid to|sent to|payment (successful|completed|done)|transaction (id|successful)|upi (ref|transaction)|money sent|received from)""").containsMatchIn(raw)

    fun paymentAmount(raw: String): Long? {
        Regex("""₹\s*([\d,]+(?:\.\d{1,2})?)""").find(raw)?.groupValues?.get(1)?.let { paise(it) }?.let { return it }
        return num.findAll(raw).mapNotNull { paise(it.value) }.maxOrNull()
    }

    fun paymentCounterparty(raw: String): String? =
        Regex("""(?i)(?:paid to|sent to|to|received from|from)\s*[:\-]?\s*([A-Za-z][A-Za-z .]{1,30})""").find(raw)?.groupValues?.get(1)?.trim()

    fun matchMember(members: List<Member>, name: String?): Member? {
        if (name.isNullOrBlank()) return null
        val n = name.trim().lowercase()
        return members.firstOrNull { it.name.lowercase() == n }
            ?: members.firstOrNull { it.name.lowercase().startsWith(n) || n.startsWith(it.name.lowercase()) }
            ?: members.firstOrNull { n.contains(it.name.lowercase()) || it.name.lowercase().contains(n) }
    }
}
