// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.ai

/** Word-level diff used to highlight what changed in the result. */
object TextDiff {
    private val token = Regex("\\s+|[^\\s]+")

    /** Ranges (in [new]) of word tokens that are not part of the common sequence with [old]. */
    fun changedRanges(old: String, new: String): List<IntRange> {
        val a = token.findAll(old).map { it.value }.toList()
        val bm = token.findAll(new).toList()
        val b = bm.map { it.value }
        if (a.size > 2500 || b.size > 2500) return emptyList()
        val n = a.size; val m = b.size
        val dp = Array(n + 1) { ShortArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0)
            dp[i][j] = if (a[i] == b[j]) (dp[i + 1][j + 1] + 1).toShort() else maxOf(dp[i + 1][j], dp[i][j + 1])
        val keep = BooleanArray(m)
        var i = 0; var j = 0
        while (i < n && j < m) {
            if (a[i] == b[j]) { keep[j] = true; i++; j++ }
            else if (dp[i + 1][j] >= dp[i][j + 1]) i++ else j++
        }
        val out = mutableListOf<IntRange>()
        for (k in 0 until m) if (!keep[k] && b[k].isNotBlank()) out += bm[k].range
        return out
    }
}
