package com.anuajay.splitup

import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().take(8)

data class Member(val id: String, val name: String, val upi: String = "")

/** [shares] maps member id to the paise that member owes for this expense. */
data class Expense(
    val id: String,
    val title: String,
    val amount: Long,
    val paidBy: String,
    val shares: Map<String, Long>,
    val date: Long,
)

/** A payment already made: [from] paid [to] [amount] paise outside the expenses. */
data class Payment(val id: String, val from: String, val to: String, val amount: Long, val date: Long)

data class Group(
    val id: String,
    val name: String,
    val members: List<Member>,
    val expenses: List<Expense> = emptyList(),
    val payments: List<Payment> = emptyList(),
)

data class Transfer(val from: String, val to: String, val amount: Long)

/** Money is kept in paise (1 rupee = 100 paise) so nothing is ever lost to rounding. */
object Money {
    fun parse(text: String): Long? {
        val t = text.trim().replace(",", "")
        if (t.isEmpty()) return null
        val d = t.toBigDecimalOrNull() ?: return null
        if (d.signum() <= 0) return null
        return d.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
    }

    fun format(paise: Long): String {
        val neg = paise < 0
        val p = kotlin.math.abs(paise)
        val rupees = p / 100
        val rest = p % 100
        val s = String.format(java.util.Locale.US, "%d.%02d", rupees, rest)
        return (if (neg) "-" else "") + "₹" + s
    }

    /** Plain number for UPI links, e.g. "250.00". */
    fun plain(paise: Long): String = String.format(java.util.Locale.US, "%d.%02d", paise / 100, paise % 100)
}

object Split {
    /** Splits [total] paise equally over [ids]; leftover paise go to the first people, so the shares add up exactly. */
    fun equal(total: Long, ids: List<String>): Map<String, Long> {
        if (ids.isEmpty()) return emptyMap()
        val base = total / ids.size
        val extra = (total % ids.size).toInt()
        return ids.mapIndexed { i, id -> id to base + if (i < extra) 1 else 0 }.toMap()
    }

    /** Positive = is owed money, negative = owes money. */
    fun balances(g: Group): Map<String, Long> {
        val b = HashMap<String, Long>()
        g.members.forEach { b[it.id] = 0L }
        for (e in g.expenses) {
            b[e.paidBy] = (b[e.paidBy] ?: 0L) + e.amount
            for ((m, s) in e.shares) b[m] = (b[m] ?: 0L) - s
        }
        for (p in g.payments) {
            b[p.from] = (b[p.from] ?: 0L) + p.amount
            b[p.to] = (b[p.to] ?: 0L) - p.amount
        }
        return b
    }

    /** Fewest-payments plan that settles everyone. */
    fun plan(g: Group): List<Transfer> {
        val b = balances(g)
        val owe = b.filter { it.value < 0 }.map { it.key to -it.value }.sortedByDescending { it.second }.toMutableList()
        val get = b.filter { it.value > 0 }.map { it.key to it.value }.sortedByDescending { it.second }.toMutableList()
        val out = mutableListOf<Transfer>()
        var i = 0
        var j = 0
        while (i < owe.size && j < get.size) {
            val pay = minOf(owe[i].second, get[j].second)
            if (pay > 0) out += Transfer(owe[i].first, get[j].first, pay)
            owe[i] = owe[i].first to owe[i].second - pay
            get[j] = get[j].first to get[j].second - pay
            if (owe[i].second == 0L) i++
            if (get[j].second == 0L) j++
        }
        return out
    }
}
