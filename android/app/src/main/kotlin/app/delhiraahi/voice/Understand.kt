package app.delhiraahi.voice

/** When to leave, as spoken. */
sealed interface LeaveSpec {
    data object Now : LeaveSpec
    data class InMinutes(val min: Int) : LeaveSpec
    /** Clock time; [tomorrow] when the traveller said so. Today/tomorrow is settled by the caller. */
    data class At(val hour: Int, val minute: Int, val tomorrow: Boolean = false) : LeaveSpec
}

/** Everything we could pull out of one sentence. Any field may be missing. */
data class Understood(
    val command: Said? = null,
    val from: String? = null,
    val fromHere: Boolean = false,
    val to: String? = null,
    val preference: String? = null,          // "fastest" | "least_crowded" | "balanced"
    val leave: LeaveSpec? = null,
    val wrongPlace: Boolean = false,
) {
    val isEmpty get() = command == null && to == null && from == null && preference == null && leave == null && !wrongPlace
}

/**
 * Whole-sentence understanding: preference and time phrases are found anywhere and removed,
 * what is left is read as a command (short utterances) or a trip.
 */
object Understand {
    private val LEAST = listOf("least crowded", "less crowded", "least crowd", "less crowd", "not crowded", "avoid crowds", "avoid crowd",
        "avoid the crowd", "empty bus", "comfortable", "kam bheed wali", "kam bheed wala", "kam bheed", "kam bhid", "khali bus", "khali",
        "कम भीड़ वाली", "कम भीड़ वाला", "कम भीड़", "कम भीड", "भीड़ से बचना", "खाली बस", "खाली")
    private val FAST = listOf("fastest", "quickest", "as fast as possible", "quickly", "quick", "fast", "jaldi se", "jaldi", "sabse tez", "jaldi wali",
        "सबसे जल्दी", "जल्दी से", "जल्दी", "सबसे तेज़", "तेज़", "तेज")
    private val BALANCED = listOf("balanced", "best option", "संतुलित")
    private val WRONG = listOf("wrong place", "not that place", "wrong stop", "different place", "other place", "galat jagah", "galat jaga",
        "dusri jagah", "गलत जगह", "ग़लत जगह", "दूसरी जगह", "वो नहीं", "wrong destination", "not there")
    private val HERE = setOf("here", "my location", "current location", "where i am", "my place", "yahan", "yaha", "यहाँ", "यहां", "यहा", "मेरी लोकेशन")

    private val NUM_WORDS = mapOf("one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8,
        "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "fifteen" to 15, "twenty" to 20, "thirty" to 30, "forty" to 40,
        "forty five" to 45, "ek" to 1, "do" to 2, "teen" to 3, "char" to 4, "paanch" to 5, "panch" to 5, "chhe" to 6, "saat" to 7,
        "aath" to 8, "nau" to 9, "das" to 10, "gyarah" to 11, "barah" to 12, "एक" to 1, "दो" to 2, "तीन" to 3, "चार" to 4, "पांच" to 5,
        "पाँच" to 5, "छह" to 6, "छः" to 6, "सात" to 7, "आठ" to 8, "नौ" to 9, "दस" to 10, "ग्यारह" to 11, "बारह" to 12)

    private fun cut(t: String, phrase: String): String = " $t ".replace(" $phrase ", " ").replace(Regex("\\s+"), " ").trim()
    private fun has(t: String, p: String) = " $t ".contains(" $p ")

    private fun digitsFor(s: String): String {
        var out = " $s "
        // longest first so "forty five" wins over "forty"
        for ((w, n) in NUM_WORDS.entries.sortedByDescending { it.key.length }) out = out.replace(" $w ", " $n ")
        return out.replace(Regex("[०-९]")) { m -> ('0' + (m.value[0] - '०')).toString() }.trim()
    }

    private fun hour24(h: Int, ampm: String?, period: String?): Int = when {
        ampm != null && ampm.startsWith("p") && h < 12 -> h + 12
        ampm != null && ampm.startsWith("a") && h == 12 -> 0
        period in setOf("evening", "night", "afternoon", "shaam", "sham", "raat", "dopahar", "शाम", "रात", "दोपहर") && h < 12 -> h + 12
        else -> h
    }

    /** Pull a leave-time phrase out of [t]; returns (spec, remaining text). */
    fun extractTime(text: String): Pair<LeaveSpec?, String> {
        var t = digitsFor(VoiceParser.norm(text)).replace("p.m", "pm").replace("a.m", "am").replace("p m", "pm").replace("a m", "am")
        val tomorrow = listOf("tomorrow", "kal", "कल").any { has(t, it) }
        for (w in listOf("tomorrow", "kal", "कल")) t = cut(t, w)

        // relative: "in 30 minutes", "in an hour", "half an hour later", "30 minute baad", "आधे घंटे बाद"
        Regex("(?:in |after )?(half an|half|an|a|\\d+) (minutes?|mins?|hours?|hrs?|minute|ghante|ghanta|ghante|मिनट|घंटे|घंटा)(?: later| baad| mein| बाद| में)?")
            .find(t)?.let { m ->
                val g = m.groupValues[1]
                val unit = m.groupValues[2]
                val hours = unit.startsWith("h") || unit.startsWith("g") || unit.startsWith("घ")
                val min = when {
                    g.startsWith("half") -> 30
                    g == "an" || g == "a" -> if (hours) 60 else 1
                    else -> (g.toIntOrNull() ?: 10_000).coerceAtMost(10_000) * (if (hours) 60 else 1)
                }
                if (m.value.contains(" ") && (m.value.startsWith("in ") || m.value.startsWith("after ") || m.value.endsWith("later") ||
                        m.value.endsWith("baad") || m.value.endsWith("mein") || m.value.endsWith("बाद") || m.value.endsWith("में")))
                    return LeaveSpec.InMinutes(min.coerceIn(0, 600)) to cut(t, m.value)
            }
        Regex("(?:aadhe|आधे) (?:ghante|घंटे) (?:baad|mein|बाद|में)").find(t)?.let { return LeaveSpec.InMinutes(30) to cut(t, it.value) }

        // clock: "at 6 pm", "6:30 pm", "at 18:30", "shaam 6 baje", "सुबह 9 बजे"
        val period = Regex("\\b(morning|evening|night|afternoon|subah|shaam|sham|raat|dopahar|सुबह|शाम|रात|दोपहर)\\b").find(t)?.value
            ?: listOf("सुबह", "शाम", "रात", "दोपहर").firstOrNull { t.contains(it) }
        Regex("(?:at |around |by )?(\\d{1,2})(?::(\\d{2}))? ?(am|pm|baje|बजे|o'clock|oclock)?").findAll(t).forEach { m ->
            val h = m.groupValues[1].toIntOrNull() ?: -1
            val mm = m.groupValues[2].ifEmpty { "0" }.toIntOrNull() ?: -1
            val unit = m.groupValues[3].ifEmpty { null }
            val explicit = unit != null || m.groupValues[2].isNotEmpty() || m.value.startsWith("at ") || period != null
            if (explicit && h in 0..23 && mm in 0..59) {
                var rest = cut(t, m.value.trim())
                period?.let { rest = cut(rest, it) }
                val ampm = unit?.takeIf { it == "am" || it == "pm" }
                return LeaveSpec.At(hour24(h, ampm, period), mm, tomorrow) to rest
            }
        }
        if (tomorrow) {
            val h = when (period) { "evening", "shaam", "sham", "शाम" -> 18; "night", "raat", "रात" -> 20; "afternoon", "dopahar", "दोपहर" -> 14; else -> 8 }
            var rest = t
            period?.let { rest = cut(rest, it) }
            return LeaveSpec.At(h, if (h == 8) 30 else 0, true) to rest
        }
        for (w in listOf("right now", "now", "abhi", "अभी")) if (has(t, w)) return LeaveSpec.Now to cut(t, w)
        return null to VoiceParser.norm(text)
    }

    /**
     * Recognisers often write the spoken "to" as "2", "too" or "two", and glue it to "ji" ("Kalka G2 Saket").
     * Only when the sentence has "from" and no real "to" do we read those as "to".
     */
    fun fixTo(text: String): String {
        val t = VoiceParser.norm(text)
        if (!(" $t ").contains(" from ") || (" $t ").contains(" to ")) return t
        return " $t ".replace(Regex(" (?:g|ji|jee)(?:2|to|too) "), " ji to ")
            .replace(Regex(" (?:2|too|two|tu) "), " to ").trim()
    }

    fun parse(text: String): Understood {
        if (VoiceParser.isStop(text)) return Understood(command = Said.Stop)
        val (leave, afterTime) = extractTime(fixTo(text))
        var t = afterTime
        var pref: String? = null
        for ((name, list) in listOf("least_crowded" to LEAST, "fastest" to FAST, "balanced" to BALANCED)) {
            for (p in list) if (has(t, p)) { pref = pref ?: name; t = cut(t, p) }
        }
        val wrong = WRONG.any { has(t, it) }
        if (wrong) WRONG.forEach { t = cut(t, it) }
        for (w in listOf("route", "way", "bus", "wali", "wala", "वाली", "वाला", "बस", "रूट")) if (pref != null) t = cut(t, w)

        // After a preference or time was taken out, leftover filler ("please", "make it the", "leave") is not a command.
        if (pref != null || leave != null) t = t.split(" ").filterNot { it in LEFTOVER }.joinToString(" ")
        // Short leftovers are commands ("next", "show", "haan") unless they read like a trip.
        // "alert me when to get off" / "guide me to the bus stop" contain "to" but are requests, not trips.
        val strong = when {
            ALERT_ASK.any { " $t ".contains(" $it ") } -> Said.Alert
            GUIDE_ASK.any { " $t ".contains(" $it ") } -> Said.Guide
            else -> null
        }
        val cmd = strong ?: if (t.isNotBlank() && !VoiceParser.looksLikeTrip(t)) VoiceParser.command(t) else null
        if (cmd != null) return Understood(command = cmd, preference = pref, leave = leave, wrongPlace = wrong)

        val trip = if (t.isBlank()) null else VoiceParser.trip(t)
        var from = trip?.from
        var to = trip?.to
        var fromHere = false
        if (from != null && from in HERE) { from = null; fromHere = true }
        if (to != null && (to in HERE || to.length < 2)) to = null
        // "from Saket" alone is an origin.
        val n = VoiceParser.norm(text)
        if (to != null && trip?.from == null && n.startsWith("from ") && !n.contains(" to ")) { from = VoiceParser.placeOnly(t); to = null }
        return Understood(from = from, fromHere = fromHere, to = to, preference = pref, leave = leave, wrongPlace = wrong)
    }

    private val OFF_START = setOf("write", "generate", "create", "make", "code", "tell", "explain", "what", "whats", "who",
        "why", "play", "sing", "calculate", "translate", "define", "call", "send", "remind", "kya", "kaun", "kyun", "likho")
    private val OFF_END = setOf("likho", "sunao", "banao", "gao", "samjhao")
    private val TRAVEL = Regex("""\b(bus|buses|route|stop|metro|reach|go|going|get|way|travel|ride|ticket|pass|fare|crowd|crowded|alert|jana|jaana|rasta|raasta|fastest|quickest|least|direct|walk|leave|leaving|minutes|baje|option)\b""")
    private val ALERT_ASK = listOf("alert me", "when to get off", "tell me when", "remind me", "wake me")
    private val GUIDE_ASK = listOf("guide me", "navigate me", "walk me to the stop", "take me to the bus stop", "take me to the stop")
    private val LEFTOVER = setOf("please", "pls", "make", "it", "the", "a", "leave", "leaving", "depart", "nikalna", "nikalenge", "chalna", "kar", "do", "karo")

    /**
     * Clearly not a travel request ("write a Python function", "tell me a joke"): the assistant says what
     * it can help with and asks again, instead of treating the sentence as a place name.
     */
    fun offTopic(text: String): Boolean {
        val t = VoiceParser.norm(text)
        if (t.isBlank() || VoiceParser.looksLikeTrip(t) || TRAVEL.containsMatchIn(t) ||
            VoiceParser.command(t).let { it != null && it != Said.Repeat }) return false   // "what" alone is Repeat, not a travel word
        val w = t.split(" ")
        return w.first() in OFF_START || w.last() in OFF_END
    }

    /** Best reading among the recogniser's guesses: prefer one with a destination, then a command. */
    fun best(heard: List<String>): Understood {
        val all = heard.map { parse(it) }
        return all.firstOrNull { it.to != null } ?: all.firstOrNull { it.command != null } ?: all.firstOrNull { !it.isEmpty } ?: all.firstOrNull() ?: Understood()
    }
}
