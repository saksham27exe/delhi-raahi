package app.delhiraahi.voice

/**
 * How well a found stop or place matches the name the traveller said, from 0 to 1.
 *
 * The search is forgiving (it finds something for almost any text), so "generate a Python function"
 * used to match "Jagatpuri A Block" through the word "a". Here a match only counts when the
 * meaningful words said are really in the name (exactly, nearly, or as initials like "AIIMS").
 * Pure Kotlin, unit-tested.
 */
object PlaceMatch {
    /** Below this, the place is not what was said: ask again rather than plan a trip there. */
    const val ACCEPT = 0.5f
    /** Mid-conversation a lone phrase must match this well to count as a new place (else it's a misheard command). */
    const val SURE = 0.8f

    private val FILLER = setOf(
        "a", "an", "the", "of", "to", "from", "me", "my", "please", "bus", "buses", "stop", "station", "near", "nearby",
        "i", "want", "go", "take", "get", "reach", "need", "can", "you", "is", "for", "at", "in", "on", "and", "by",
        "ka", "ki", "ke", "se", "tak", "ko", "jana", "jaana", "hai", "mujhe", "chalo", "wala", "wali",
        "jaane", "jane", "rasta", "raasta", "kaise", "batao", "bataiye", "dikhao", "chahiye", "liye",
    )

    fun words(s: String): List<String> =
        s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 2 && it !in FILLER }

    fun score(said: String, name: String): Float {
        val s = words(said)
        if (s.isEmpty()) return 0f
        val all = name.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        val n = all.filter { it !in FILLER }
        val glued = all.joinToString("")
        val initials = n.filter { it.length > 2 || it.all(Char::isLetter) }.joinToString("") { it.take(1) }
        val hit = s.count { w -> n.any { close(w, it) } || (w.length >= 5 && glued.contains(w)) || (w.length >= 3 && initials.contains(w)) }
        return hit.toFloat() / s.size
    }

    private fun close(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length < 4 || b.length < 4) return false
        if (a.startsWith(b) || b.startsWith(a)) return true
        val allowed = if (minOf(a.length, b.length) >= 7) 2 else 1
        return edits(a, b, allowed) <= allowed
    }

    /** Edit distance, stopping early once it exceeds [cap]. */
    private fun edits(a: String, b: String, cap: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > cap) return cap + 1
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1); cur[0] = i
            for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            if (cur.min() > cap) return cap + 1
            prev = cur
        }
        return prev[b.length]
    }
}
