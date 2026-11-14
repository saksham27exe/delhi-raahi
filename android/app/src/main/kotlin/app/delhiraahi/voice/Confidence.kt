package app.delhiraahi.voice

/**
 * How sure the assistant is that it heard the traveller right, from 0 to 1.
 *
 * - The phone's speech recogniser usually gives a confidence for its best guess; that is used when present.
 * - When it doesn't (some phones report 0 or nothing), the score comes from how much its other guesses
 *   agree with the best one: if every guess means the same trip, it is likely right.
 *
 * Then: sure → go ahead; partly sure about a place → check back ("Did you say … ?");
 * unsure → ask to say it again. Pure Kotlin, unit-tested.
 */
object Confidence {
    const val SURE = 0.70f
    const val UNSURE = 0.40f

    enum class Verdict { GO, CONFIRM, ASK_AGAIN }

    fun score(options: List<String>, scores: FloatArray?): Float {
        val given = scores?.firstOrNull()?.takeIf { it > 0f }       // 0 or -1 = the recogniser gave no score
        if (given != null) return given.coerceIn(0f, 1f)
        if (options.size <= 1) return 0.75f                         // a single clear guess
        val k0 = meaning(options[0])
        val same = options.drop(1).count { meaning(it) == k0 }
        return 0.5f + 0.4f * same / (options.size - 1)               // 0.5 (all disagree) … 0.9 (all agree)
    }

    /**
     * Places are where a mishearing costs most (a trip to the wrong stop), so a partly-sure place is checked.
     * Short commands ("next", "stop") are only repeated when really unsure; asking "did you say next?" is tiresome.
     */
    fun verdict(score: Float, hasPlace: Boolean, hasCommand: Boolean): Verdict = when {
        !hasPlace && !hasCommand -> Verdict.GO       // nothing understood: the normal "didn't get that" reply covers it
        score >= SURE -> Verdict.GO
        score < UNSURE -> Verdict.ASK_AGAIN
        hasPlace -> Verdict.CONFIRM
        else -> Verdict.GO
    }

    fun percent(score: Float): Int = (score * 100).toInt().coerceIn(0, 100)

    private fun meaning(s: String): List<String?> = Understand.parse(s).let { u ->
        listOf(u.to?.lowercase()?.trim(), u.from?.lowercase()?.trim(), u.command?.toString(), u.preference)
    }
}
