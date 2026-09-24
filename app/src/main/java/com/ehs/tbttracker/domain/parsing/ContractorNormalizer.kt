package com.ehs.tbttracker.domain.parsing

/**
 * Groups the many spellings of the same agency found in the sheet, e.g.
 *   "Choudhary construction " / "Choudhary Construction"      -> "Choudhary Construction"
 *   "Alu-wind" / "Alu-wind infratech "                        -> "Alu-wind Infratech"
 *   "Shree sidhivinayak enterprises tbt work team"            -> "Shree Sidhivinayak Enterprises"
 *
 * Rules (deterministic, no fuzzy scoring so results are explainable to EHS auditors):
 *  1. Key = lowercase, punctuation to spaces, whitespace collapsed.
 *  2. If key A's words are a leading prefix of key B's words and A has >= 2 words
 *     (or A is a single word of >= 6 letters that also starts B, e.g. "alu wind" / "stellar"),
 *     they are the same contractor. Single short words never merge, to avoid false matches.
 *  3. The display name is the most frequent spelling in the group (ties -> shorter),
 *     title-cased where the user typed all-lowercase.
 */
class ContractorNormalizer(names: Collection<String>) {

    private val canonicalByKey: Map<String, String>

    init {
        val counts = names.map { it.trim().replace(WS, " ") }.filter { it.isNotEmpty() }
            .groupingBy { it }.eachCount()
        val spellingsByKey = counts.keys.groupBy { key(it) }
        val keys = spellingsByKey.keys.sortedBy { it.split(' ').size }

        // parent key for each key (shortest compatible prefix)
        val root = HashMap<String, String>()
        for (k in keys) {
            val parent = keys.firstOrNull { it != k && isPrefixMatch(it, k) }
            root[k] = parent?.let { root[it] ?: it } ?: k
        }
        val groups = keys.groupBy { root.getValue(it) }
        val canon = HashMap<String, String>()
        for ((_, members) in groups) {
            val display = members.flatMap { spellingsByKey.getValue(it) }
                .maxWith(
                    compareBy<String> { counts.getValue(it) }
                        .thenByDescending { it.length }
                        .thenBy { it.count(Char::isUpperCase) },
                )
            val pretty = titleCase(display)
            members.forEach { canon[it] = pretty }
        }
        canonicalByKey = canon
    }

    /** Canonical display name, or a title-cased trim for names not seen at construction time. */
    fun canonical(raw: String): String {
        val cleaned = raw.trim().replace(WS, " ")
        if (cleaned.isEmpty()) return UNKNOWN
        val k = key(cleaned)
        canonicalByKey[k]?.let { return it }
        canonicalByKey.entries.firstOrNull { isPrefixMatch(it.key, k) }?.let { return it.value }
        return titleCase(cleaned)
    }

    /** Distinct canonical names, alphabetically. */
    val all: List<String> get() = canonicalByKey.values.toSortedSet(String.CASE_INSENSITIVE_ORDER).toList()

    companion object {
        const val UNKNOWN = "Unknown Contractor"
        private val WS = Regex("""\s+""")
        private val NON_ALNUM = Regex("""[^a-z0-9]+""")

        fun key(name: String): String = name.lowercase().replace(NON_ALNUM, " ").trim()

        internal fun isPrefixMatch(shortKey: String, longKey: String): Boolean {
            if (shortKey == longKey) return true
            val a = shortKey.split(' ')
            val b = longKey.split(' ')
            if (a.size >= b.size) return false
            val strong = a.size >= 2 || a[0].length >= 6
            return strong && b.subList(0, a.size) == a
        }

        /** Capitalises words typed in all-lowercase; keeps deliberate casing like "N.A" or "TBT". */
        fun titleCase(s: String): String = s.split(' ').joinToString(" ") { w ->
            if (w == w.lowercase()) w.replaceFirstChar { it.titlecase() } else w
        }
    }
}
