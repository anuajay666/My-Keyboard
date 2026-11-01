package com.anuajay.splitup

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Everything is kept in one small file on the phone. No account, no server. */
object Store {
    private fun file(ctx: Context) = File(ctx.filesDir, "splitup.json")

    fun load(ctx: Context): List<Group> = try {
        val f = file(ctx)
        if (!f.exists()) emptyList() else {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { group(arr.getJSONObject(it)) }
        }
    } catch (_: Throwable) { emptyList() }

    fun save(ctx: Context, groups: List<Group>) {
        val arr = JSONArray()
        groups.forEach { arr.put(json(it)) }
        val tmp = File(ctx.filesDir, "splitup.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file(ctx))
    }

    private fun json(g: Group) = JSONObject().apply {
        put("id", g.id); put("name", g.name)
        put("members", JSONArray().also { a -> g.members.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("upi", it.upi)) } })
        put("expenses", JSONArray().also { a ->
            g.expenses.forEach { e ->
                a.put(JSONObject().put("id", e.id).put("title", e.title).put("amount", e.amount).put("paidBy", e.paidBy).put("date", e.date)
                    .put("shares", JSONObject().also { s -> e.shares.forEach { (k, v) -> s.put(k, v) } }))
            }
        })
        put("payments", JSONArray().also { a ->
            g.payments.forEach { p -> a.put(JSONObject().put("id", p.id).put("from", p.from).put("to", p.to).put("amount", p.amount).put("date", p.date)) }
        })
    }

    private fun group(o: JSONObject): Group {
        val ms = o.getJSONArray("members")
        val es = o.getJSONArray("expenses")
        val ps = o.getJSONArray("payments")
        return Group(
            id = o.getString("id"), name = o.getString("name"),
            members = (0 until ms.length()).map { ms.getJSONObject(it).let { m -> Member(m.getString("id"), m.getString("name"), m.optString("upi", "")) } },
            expenses = (0 until es.length()).map {
                val e = es.getJSONObject(it)
                val sh = e.getJSONObject("shares")
                Expense(e.getString("id"), e.getString("title"), e.getLong("amount"), e.getString("paidBy"),
                    sh.keys().asSequence().associateWith { k -> sh.getLong(k) }, e.optLong("date"))
            },
            payments = (0 until ps.length()).map {
                val p = ps.getJSONObject(it)
                Payment(p.getString("id"), p.getString("from"), p.getString("to"), p.getLong("amount"), p.optLong("date"))
            },
        )
    }
}
