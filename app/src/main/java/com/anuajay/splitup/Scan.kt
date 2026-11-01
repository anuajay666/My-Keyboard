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
        Regex("""(?i)(paid to|sent to|payment (successful|completed|done)|transaction (id|successful)|upi (ref|transaction)|money sent|received from|google pay|phonepe|paytm|\bcompleted\b.*\n)""").containsMatchIn(raw)

    private fun lines(raw: String) = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }

    /** "1905.00", "35,000", "1905, 00" (a misread dot) -> paise */
    private fun money(token: String): Long? {
        val t = token.trim()
        val dec = Regex("""^(\d[\d,]*)[.,]\s?(\d{2})$""").find(t)
        val clean = if (dec != null) dec.groupValues[1].replace(",", "") + "." + dec.groupValues[2] else t.replace(",", "")
        return Money.parse(clean)
    }
    private val moneyTok = Regex("""\d[\d,]*(?:[.,]\s?\d{2})?""")
    private fun lastMoney(s: String): Long? = moneyTok.findAll(s).mapNotNull { money(it.value) }.lastOrNull()

    private val totalWords = listOf("grand total", "net amount", "net payable", "amount payable", "total sale", "bill amount", "total amount", "net amt", "total amt", "amount due", "grand")

    private val decMoney = Regex("""\d[\d,]*[.,]\s?\d{2}\b""")
    private fun lastDecimal(s: String): Long? = decMoney.findAll(s).mapNotNull { money(it.value) }.lastOrNull()
    private val billWords = Regex("""(?i)\b(gst|gstin|invoice|bill|receipt|qty|tax|sale|total|amount|cashier|pump|sgst|cgst)\b""")

    /** A shop bill / fuel slip / restaurant receipt: the amount to pay, found by its label (Grand Total, Net Amount…). */
    fun billTotal(raw: String): Long? {
        if (raw.contains("total expense", true)) return null // that is the WhatsApp list format
        val ls = lines(raw)
        val minimum = 1000L // ignore anything under ₹10 (battery %, quantities, pump numbers)
        for (k in totalWords) {
            val i = ls.indexOfFirst { it.lowercase().contains(k) }
            if (i < 0) continue
            // same line as the label: any amount
            lastMoney(ls[i].substring(ls[i].lowercase().indexOf(k) + k.length))?.takeIf { it >= minimum }?.let { return it }
            // the amount may sit on the next lines: only accept proper amounts with paise (1905.00), never bare numbers
            for (j in i + 1..minOf(i + 3, ls.lastIndex)) lastDecimal(ls[j])?.takeIf { it >= minimum }?.let { return it }
        }
        // no label found: for something that looks like a bill, take the amount that is printed most often, else the largest
        if (!billWords.containsMatchIn(raw)) return null
        val all = ls.flatMap { l -> decMoney.findAll(l).mapNotNull { money(it.value) }.toList() }.filter { it >= minimum }
        if (all.isEmpty()) return null
        return all.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenByDescending { it.key }).first().key
    }

    /** First line that looks like a shop name (mostly letters), used as the title of a bill. */
    fun merchant(raw: String): String? = lines(raw).firstOrNull { l ->
        l.length in 5..40 && l.count { it.isLetter() } >= l.length * 0.7 &&
            !Regex("""(?i)bill|invoice|receipt|tax|date|cashier|name|you|reply""").containsMatchIn(l)
    }?.trim(' ', '-', '~', '.')

    fun paymentAmount(raw: String): Long? {
        Regex("""[₹&®]\s*([\d,]+(?:\.\d{1,2})?)""").find(raw)?.groupValues?.get(1)?.let { money(it) }?.let { return it }
        val ls = lines(raw)
        val to = ls.indexOfFirst { it.startsWith("to ", true) }
        if (to >= 0) for (j in to + 1..minOf(to + 3, ls.lastIndex)) {
            if (Regex("""^\D{0,2}[\d,]{1,9}(\.\d{1,2})?$""").matches(ls[j])) money(ls[j].filter { it.isDigit() || it == '.' || it == ',' })?.let { return it }
        }
        // last resort: a short number (not an id, a date or a time)
        return moneyTok.findAll(raw).map { it.value }.filter { it.filter(Char::isDigit).length in 1..7 }.mapNotNull { money(it) }.firstOrNull()
    }

    fun paymentCounterparty(raw: String): String? =
        Regex("""(?im)^\s*(?:paid to|sent to|to|received from)\s*:?\s+(.+)$""").find(raw)?.groupValues?.get(1)?.substringBefore('(')?.trim()?.ifBlank { null }

    fun paymentFrom(raw: String): String? =
        Regex("""(?im)^\s*from\s*:?\s*(.+)$""").find(raw)?.groupValues?.get(1)?.substringBefore('(')?.trim()?.ifBlank { null }

    /** Date printed on the bill or payment screen → UTC midnight, or null. */
    fun dateOf(raw: String): Long? {
        fun utc(d: Int, m: Int, y: Int): Long? {
            if (m !in 1..12 || d !in 1..31 || y !in 2000..2100) return null
            return java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { clear(); set(y, m - 1, d) }.timeInMillis
        }
        Regex("""\b(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})\b""").find(raw)?.let {
            val y = it.groupValues[3].toInt().let { v -> if (v < 100) 2000 + v else v }
            utc(it.groupValues[1].toInt(), it.groupValues[2].toInt(), y)?.let { r -> return r }
        }
        Regex("""\b(\d{1,2})\s+([A-Za-z]{3,9})\.?,?\s+(\d{4})\b""").find(raw)?.let {
            val m = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec").indexOf(it.groupValues[2].take(3).lowercase()) + 1
            utc(it.groupValues[1].toInt(), m, it.groupValues[3].toInt())?.let { r -> return r }
        }
        return null
    }

    fun matchMember(members: List<Member>, name: String?): Member? {
        if (name.isNullOrBlank()) return null
        val n = name.trim().lowercase()
        return members.firstOrNull { it.name.lowercase() == n }
            ?: members.firstOrNull { it.name.lowercase().startsWith(n) || n.startsWith(it.name.lowercase()) }
            ?: members.firstOrNull { n.contains(it.name.lowercase()) || it.name.lowercase().contains(n) }
    }
}
