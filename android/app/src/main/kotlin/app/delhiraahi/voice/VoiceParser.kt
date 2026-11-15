package app.delhiraahi.voice

/** What the traveller said, as far as the assistant understands it. Pure Kotlin: unit-tested. */
sealed interface Said {
    data object Yes : Said
    data object No : Said
    data object Next : Said
    data object Previous : Said
    data object Repeat : Said
    data object Fastest : Said
    data object LeastCrowded : Said
    data object Balanced : Said
    data object Show : Said
    data object Guide : Said
    data object Alert : Said
    data object Help : Said
    data object NewTrip : Said
    data object Stop : Said
    data object DirectOnly : Said
    data object WithChange : Said
    data object WalkOnly : Said
    data class Number(val n: Int) : Said
    data class Trip(val from: String?, val to: String) : Said
    data class Other(val text: String) : Said
}

/**
 * Rule-based English / Hindi (Latin or Devanagari) phrase matching.
 * Commands are recognised only in short utterances so a place name such as "Noida" never reads as "no".
 */
object VoiceParser {
    private val STOP = setOf("stop", "cancel", "exit", "close", "quit", "goodbye", "bye", "band karo", "bas", "ruko",
        "बंद करो", "बंद", "बस", "रुको", "रुक जाओ")

    private val COMMANDS: List<Pair<Said, List<String>>> = listOf(
        Said.Alert to listOf("alert me", "alerts", "remind me", "when to get off", "tell me when", "stop alert", "get off alert",
            "batana", "utarne par batana", "utarna kab hai", "बताना", "उतरने पर बताना", "कब उतरना है", "उतरना कब है"),
        Said.Guide to listOf("guide me", "guide", "navigate", "take me there", "start walking", "walk me", "walk me there",
            "directions", "walking directions", "rasta batao", "rasta bataiye", "le chalo", "रास्ता बताओ", "रास्ता बताइए", "ले चलो", "रास्ता दिखाओ"),
        Said.DirectOnly to listOf("direct", "direct bus", "direct only", "direct buses", "no change", "without change", "without changing",
            "seedhi", "seedhi bus", "sidhi bus", "सीधी", "सीधी बस", "बिना बदले", "सीधे"),
        Said.WithChange to listOf("with change", "with changes", "with a change", "connecting", "connecting bus", "connecting buses",
            "change bus", "badal ke", "badalkar", "बदलाव वाली", "बदलकर", "बदल के", "कनेक्टिंग"),
        Said.WalkOnly to listOf("walk", "walking", "on foot", "just walk", "paidal", "pedal", "पैदल", "चलकर", "पैदल चलना"),
        Said.LeastCrowded to listOf("least crowded", "less crowded", "less crowd", "least crowd", "not crowded", "empty bus", "quiet",
            "kam bheed", "kam bhid", "khali", "कम भीड़", "कम भीड", "खाली"),
        Said.Fastest to listOf("fastest", "quickest", "quick", "fast", "jaldi", "sabse tez", "tez", "जल्दी", "सबसे तेज़", "तेज़", "तेज"),
        Said.Balanced to listOf("balanced", "best", "normal", "सामान्य", "संतुलित"),
        Said.NewTrip to listOf("new trip", "new search", "start over", "another place", "somewhere else", "naya", "नया", "नई यात्रा"),
        Said.Next to listOf("next", "next one", "another", "other", "other option", "show more", "so more", "any more",
            "more options", "more buses", "agla", "aage", "aur dikhao", "aur batao", "aur koi", "aur bus", "अगला", "आगे",
            "दूसरा विकल्प", "और दिखाओ", "और बताओ", "और कोई"),
        Said.Previous to listOf("previous", "go back", "back", "last one", "before", "pichla", "पिछला", "पीछे"),
        Said.Repeat to listOf("repeat", "again", "say again", "say that again", "pardon", "what", "dobara", "phir se", "dubara",
            "दोबारा", "फिर से", "दुबारा", "क्या"),
        Said.Show to listOf("show", "show me", "show on screen", "screen", "open", "details", "dikhao", "dikha do", "दिखाओ", "दिखा दो", "दिखाइए"),
        Said.Help to listOf("help", "what can i say", "options", "madad", "मदद", "सहायता"),
        Said.Yes to listOf("yes", "yeah", "yep", "yup", "sure", "ok", "okay", "correct", "right", "that's right", "go ahead", "please",
            "haan", "han", "ha", "haa", "ji", "ji haan", "theek hai", "thik hai", "sahi", "हाँ", "हां", "हा", "जी", "जी हाँ", "ठीक है", "सही", "हाँ जी"),
        Said.No to listOf("no", "nope", "wrong", "not that", "nahi", "nahin", "na", "galat", "नहीं", "नही", "ना", "गलत"),
    )

    private val NUMBERS = mapOf(
        "1" to 1, "one" to 1, "first" to 1, "ek" to 1, "pehla" to 1, "pahla" to 1, "एक" to 1, "पहला" to 1, "पहली" to 1,
        "2" to 2, "two" to 2, "second" to 2, "do" to 2, "doosra" to 2, "dusra" to 2, "दो" to 2, "दूसरा" to 2, "दूसरी" to 2,
        "3" to 3, "three" to 3, "third" to 3, "teen" to 3, "teesra" to 3, "तीन" to 3, "तीसरा" to 3, "तीसरी" to 3,
    )

    private val LEAD_FILLER = setOf("i", "want", "wanna", "need", "would", "like", "to", "go", "going", "get", "take", "me", "us", "how",
        "do", "can", "could", "reach", "a", "the", "bus", "buses", "route", "routes", "directions", "please", "show", "find", "plan",
        "trip", "journey", "mujhe", "main", "मुझे", "मैं", "हमें", "what's", "whats", "what", "which", "is", "for", "there", "any")
    private val HI_SUFFIX = listOf("jana chahta hoon", "jana chahti hoon", "jaana chahta hoon", "jaana chahti hoon", "jana hai", "jaana hai",
        "kaise jaun", "kaise jaaun", "kaise jaye", "kaise pahunchu", "pahunchna hai", "le chalo", "chalo", "jana", "jaana", "tak",
        "जाना चाहता हूं", "जाना चाहता हूँ", "जाना चाहती हूं", "जाना चाहती हूँ", "जाना है", "कैसे जाऊं", "कैसे जाऊँ", "कैसे जाएं",
        "पहुंचना है", "पहुँचना है", "ले चलो", "चलो", "जाना", "तक", "please", "jaana hai mujhe")

    fun norm(s: String): String = s.lowercase().replace(Regex("""[,!?।;"]|:(?!\d)|\.(?!\d)"""), " ").replace(Regex("\\s+"), " ").trim()

    private fun hasPhrase(text: String, p: String) = " $text ".contains(" $p ")

    fun isStop(text: String): Boolean = norm(text) in STOP

    /** A command if the utterance is short and contains one; numbers count when [numbers] is set. */
    fun command(text: String, numbers: Boolean = true): Said? {
        val t = norm(text)
        if (t.isEmpty()) return null
        if (t in STOP) return Said.Stop
        // "more" / "aur" only on their own: inside a sentence they are often part of a place ("Kakrola More").
        if (t in setOf("more", "more please", "aur", "aur?", "और")) return Said.Next
        val words = t.split(" ").size
        if (numbers && words <= 3) {
            t.split(" ").firstNotNullOfOrNull { NUMBERS[it] }?.let { if (words == 1 || t.startsWith("number") || t.startsWith("option")) return Said.Number(it) }
            NUMBERS[t.removePrefix("number ").removePrefix("option ")]?.let { return Said.Number(it) }
        }
        if (words > 4) return null
        // A whole phrase from a list wins over a word inside it ("what can i say" is Help, not Repeat for "what").
        COMMANDS.firstOrNull { (_, p) -> t in p }?.let { return it.first }
        for ((said, phrases) in COMMANDS) if (phrases.any { hasPhrase(t, it) }) return said
        return null
    }

    /** True if this looks like asking for a trip rather than a stray word. */
    fun looksLikeTrip(text: String): Boolean {
        val t = norm(text)
        return listOf(" to ", " se ", " से ").any { " $t ".contains(it) } || HI_SUFFIX.take(14).any { t.endsWith(it) } ||
            t.startsWith("go to") || t.startsWith("take me")
    }

    private fun stripSuffix(t: String): String {
        var s = t
        var changed = true
        while (changed) {
            changed = false
            for (x in HI_SUFFIX) if (s.endsWith(" $x") || s == x) { s = s.removeSuffix(x).trim(); changed = true }
        }
        return s
    }

    private fun stripLead(t: String): String {
        val w = t.split(" ").toMutableList()
        while (w.isNotEmpty() && w.first() in LEAD_FILLER) w.removeAt(0)
        return w.joinToString(" ")
    }

    private fun place(t: String): String = stripLead(stripSuffix(cutTail(t.trim()))).removeSuffix(" please").trim()

    /**
     * Words that ask for the trip rather than name the place: "Akshardham jaane ka rasta batao",
     * "Karol Bagh kaise jaun", "India Gate ke liye bus dikhao". The place ends where the first one starts.
     */
    private val TAIL = setOf("jaane", "jane", "jaana", "jana", "jaun", "jaaun", "jaun?", "jaye", "jaaye", "jaayen", "jayen", "jaoon",
        "pahunchna", "pahuchna", "pahunchu", "pahunchun", "pahunchne", "rasta", "raasta", "route", "kaise", "kaisey", "kidhar",
        "batao", "bataiye", "bataye", "bataao", "dikhao", "dikhaiye", "chahiye", "chahta", "chahti", "liye", "wali", "wala",
        "जाने", "जाना", "जाऊं", "जाऊँ", "जाएं", "पहुंचना", "पहुँचना", "रास्ता", "कैसे", "बताओ", "बताइए", "बताएं", "दिखाओ", "चाहिए", "लिए", "वाली", "वाला")
    private val TAIL_GLUE = setOf("ka", "ke", "ki", "ko", "tak", "se", "का", "के", "की", "को", "तक", "से")

    private fun cutTail(t: String): String {
        val w = t.split(" ")
        val i = w.indexOfFirst { it in TAIL }
        if (i <= 0) return t
        var keep = w.subList(0, i)
        while (keep.size > 1 && keep.last() in TAIL_GLUE) keep = keep.subList(0, keep.size - 1)
        return keep.joinToString(" ")
    }

    /** A place name on its own (answer to "where from?"). */
    fun placeOnly(text: String): String = place(norm(text).removePrefix("from ").removePrefix("i am at ").removePrefix("i'm at "))

    /** "from X to Y", "X to Y", "to Y", "X se Y jana hai", "Y जाना है", or just "Y". */
    fun trip(text: String): Said.Trip? {
        val t = norm(text)
        if (t.isEmpty()) return null
        Regex("(?:^|\\s)from (.+?) to (.+)$").find(t)?.let { m ->
            val f = place(m.groupValues[1]); val d = place(m.groupValues[2])
            if (d.isNotEmpty()) return Said.Trip(f.ifEmpty { null }, d)
        }
        for (sep in listOf(" se ", " से ")) {
            val i = t.indexOf(sep)
            if (i > 0) {
                val f = place(t.substring(0, i)); val d = place(t.substring(i + sep.length))
                if (d.isNotEmpty()) return Said.Trip(f.ifEmpty { null }, d)
            }
        }
        val padded = " $t"
        val i = padded.lastIndexOf(" to ")
        if (i >= 0) {
            val d = place(padded.substring(i + 4))
            val f = place(padded.substring(0, i))
            if (d.isNotEmpty()) return Said.Trip(f.ifEmpty { null }, d)
        }
        return place(t).ifEmpty { null }?.let { Said.Trip(null, it) }
    }
}

/** Rough Devanagari → Latin so spoken Hindi place names can match our (English) stop names; the search is typo-tolerant. */
object Translit {
    private val VOWELS = mapOf('अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo", 'ऋ' to "ri", 'ए' to "e",
        'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o", 'ऍ' to "e")
    private val SIGNS = mapOf('ा' to "a", 'ि' to "i", 'ी' to "i", 'ु' to "u", 'ू' to "u", 'ृ' to "ri", 'े' to "e", 'ै' to "ai",
        'ो' to "o", 'ौ' to "au", 'ॉ' to "o", 'ॅ' to "e")
    private val CONS = mapOf('क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n", 'च' to "ch", 'छ' to "chh", 'ज' to "j",
        'झ' to "jh", 'ञ' to "n", 'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n", 'त' to "t", 'थ' to "th", 'द' to "d",
        'ध' to "dh", 'न' to "n", 'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m", 'य' to "y", 'र' to "r", 'ल' to "l",
        'व' to "v", 'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h", 'क़' to "q", 'ख़' to "kh", 'ग़' to "g", 'ज़' to "z", 'ड़' to "r",
        'ढ़' to "rh", 'फ़' to "f", 'य़' to "y")
    // Consonant + nukta written as two characters.
    private val NUKTA_CONS = mapOf('ज' to "z", 'फ' to "f", 'ड' to "r", 'ढ' to "rh", 'क' to "q", 'ख' to "kh", 'ग' to "g")
    private const val VIRAMA = '्'
    private const val NUKTA = '़'

    fun hasDevanagari(s: String) = s.any { it in 'ऀ'..'ॿ' }

    /** Common Delhi place words whose usual English spelling differs from a letter-by-letter reading. */
    private val WORDS = mapOf("एम्स" to "aiims", "गेट" to "gate", "चौक" to "chowk", "स्टेशन" to "station", "रेलवे" to "railway",
        "मेट्रो" to "metro", "अस्पताल" to "hospital", "हॉस्पिटल" to "hospital", "बाग" to "bagh", "बाग़" to "bagh", "कॉलोनी" to "colony",
        "मार्केट" to "market", "बाजार" to "bazar", "बाज़ार" to "bazar", "टर्मिनल" to "terminal", "डिपो" to "depot", "इंडिया" to "india",
        "दिल्ली" to "delhi", "नई" to "new", "आईएसबीटी" to "isbt", "कनॉट" to "connaught", "प्लेस" to "place", "चांदनी" to "chandni",
        "सराय" to "sarai", "यूनिवर्सिटी" to "university", "कॉलेज" to "college", "मंदिर" to "mandir", "मस्जिद" to "masjid",
        "एयरपोर्ट" to "airport", "हवाई" to "hawai", "अड्डा" to "adda", "पार्क" to "park", "एन्क्लेव" to "enclave", "सेक्टर" to "sector",
        "फेज" to "phase", "फ़ेज़" to "phase", "ब्लॉक" to "block", "रोड" to "road", "मार्ग" to "marg", "मोड़" to "mor", "बस" to "bus", "स्टॉप" to "stop")

    fun toLatin(s: String): String {
        if (!hasDevanagari(s)) return s
        return s.split(" ").joinToString(" ") { w -> WORDS[w] ?: word(w) }
    }

    private fun word(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            var c = s[i]
            val nukta = i + 1 < s.length && s[i + 1] == NUKTA
            val cons = if (nukta) NUKTA_CONS[c] ?: CONS[c] else CONS[c]
            if (cons != null) {
                if (nukta) i++
                out.append(cons)
                val nxt = s.getOrNull(i + 1)
                when {
                    nxt == VIRAMA -> i++
                    nxt != null && nxt in SIGNS -> { out.append(SIGNS.getValue(nxt)); i++ }
                    // Inherent "a", dropped at the end of a word (schwa deletion).
                    nxt == null || !(nxt in 'ऀ'..'ॿ') || nxt == 'ं' || nxt == 'ँ' -> if (nxt == 'ं' || nxt == 'ँ') out.append('a')
                    else -> out.append('a')
                }
            } else {
                c = s[i]
                when {
                    c in VOWELS -> out.append(VOWELS.getValue(c))
                    c == 'ं' || c == 'ँ' -> out.append('n')
                    c == 'ः' -> out.append('h')
                    c in SIGNS -> out.append(SIGNS.getValue(c))
                    c == NUKTA || c == VIRAMA -> {}
                    c in '०'..'९' -> out.append('0' + (c - '०'))
                    else -> out.append(c)
                }
            }
            i++
        }
        return out.toString()
    }
}
