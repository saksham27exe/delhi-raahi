package app.delhiraahi.voice

import app.delhiraahi.data.Lang
import java.util.Locale

/** Everything the assistant says or shows, in English and Hindi. Kept short: it is listened to, not read. */
interface VoiceStrings {
    val languageTag: String
    val locale: Locale
    val title: String; val open: String
    val tapToTalk: String; val tapToInterrupt: String; val tapWhenDone: String; val end: String
    val listening: String; val speaking: String; val thinking: String
    val you: String; val assistant: String; val currentLocation: String
    val micNeeded: String; val noRecognizer: String
    val greeting: String; val firstTip: String; val askWhere: String; val askFrom: String
    val approxLocation: String
    val sorry: String; val goingQuiet: String; val bye: String; val help: String; val hint: String
    val notUnderstood: String; val notUnderstoodNoTrip: String
    val widened: String; val noTrips: String; val noStopNearYou: String; val dataNote: String
    fun noStopAtAll(where: String): String
    fun understood(from: String?, to: String?, extras: List<String>): String
    fun stepFinding(name: String): String; fun stepFound(name: String, match: String, isStop: Boolean): String; fun stepNotFound(name: String): String
    val stepLocating: String; fun stepLocated(accuracyM: Int): String; val stepNoLocation: String
    fun stepTimetable(time: String): String; fun stepRanked(considered: Int, shown: Int): String; val stepWiden: String
    fun stepNoStop(where: String): String; val stepFailed: String
    fun prefName(p: String): String
    val firstOption: String; val lastOption: String; val sayPlaceAgain: String
    fun notFound(name: String): String
    fun planning(from: String?, to: String, leave: String?): String
    fun searchFailed(msg: String): String
    fun found(n: Int): String
    fun option(i: Int, n: Int): String
    fun walkThenBus(min: Int, stop: String, route: String, time: String): String
    fun busFrom(route: String, time: String, stop: String): String
    fun change(alight: String, board: String, walkMin: Int, route: String, time: String): String
    fun summary(direct: Int, change: Int, walkMin: Int?): String
    fun showing(kindDirect: Boolean, n: Int): String
    fun noneOf(kindDirect: Boolean): String
    fun walkOnly(min: Int, m: Int, approx: Boolean): String
    val noWalk: String
    fun guiding(stop: String): String
    fun alerting(stop: String): String
    val noGuide: String
    fun getOff(stop: String, walkMin: Int): String
    fun arrive(time: String, total: Int): String
    fun crowd(cat: String): String
    fun fare(rupees: Double): String
    fun alternatives(list: List<String>): String
    /** p is the wire name: fastest, balanced, least_crowded. */
    fun preferenceSet(p: String): String
    fun leaving(at: String): String
    fun leavingIn(min: Int): String
    /** "17:31" → how a person says it. */
    fun time(hhmm: String): String
}

private fun rupees(x: Double, r: String, p: String): String {
    val whole = x.toInt()
    val paise = Math.round((x - whole) * 100).toInt()
    return if (paise == 0) "$whole $r" else "$whole $r $paise $p"
}

private fun hm(hhmm: String): Pair<Int, Int>? = hhmm.split(":").takeIf { it.size >= 2 }?.let { p ->
    p[0].toIntOrNull()?.let { h -> p[1].toIntOrNull()?.let { m -> (h % 24) to m } }
}

private fun mins(n: Int, en: Boolean) = if (en) (if (n == 1) "1 minute" else "$n minutes") else "$n मिनट"

object VoiceEn : VoiceStrings {
    override val languageTag = "en-IN"
    override val locale: Locale = Locale("en", "IN")
    override val title = "Voice assistant"; override val open = "Voice assistant"
    override val tapToTalk = "Tap to talk"; override val tapToInterrupt = "Tap to interrupt and talk"; override val tapWhenDone = "Tap when you've finished"
    override val end = "End"
    override val listening = "Listening…"; override val speaking = "Speaking…"; override val thinking = "Working on it…"
    override val you = "You"; override val assistant = "Assistant"; override val currentLocation = "Current location"
    override val micNeeded = "The voice assistant needs microphone permission. You can still plan trips by typing."
    override val noRecognizer = "Speech recognition is not available on this device."
    override val greeting = "Hi! Where would you like to go?"
    override val firstTip = "You can say something like: least crowded bus from Saket to AIIMS at 6 PM."
    override val askWhere = "Where to?"
    override val askFrom = "I can't get your location. Where are you starting from?"
    override val approxLocation = "Your location is only approximate. To be exact, say where you're starting from."
    override val sorry = "Sorry, say that again?"
    override val goingQuiet = "Tap the microphone when you're ready."
    override val bye = "Okay, bye!"
    override val help = "Tell me where you want to go, for example: from Saket to AIIMS, least crowded, at 6 PM. " +
        "After I read a trip, you can say next, previous, repeat, fastest, least crowded, a new time, wrong place, or show on screen."
    override val hint = "Say next for more, or show on screen."
    override val notUnderstood = "Sorry, I didn't get that. Say next, repeat, or a new place."
    override val notUnderstoodNoTrip = "Sorry, I didn't get that. Where do you want to go?"
    override val widened = "No bus stop within 600 metres, so I looked up to one and a half kilometres."
    override fun noStopAtAll(where: String) = "There's no bus stop within one and a half kilometres of $where."
    override val noStopNearYou = "There's no Delhi bus stop within one and a half kilometres of your current location. Where are you starting from?"
    override val dataNote = "Bus times: DTC timetable (scheduled, not live). Crowding: model trained on e-ticket data. Places: OpenStreetMap."
    override fun understood(from: String?, to: String?, extras: List<String>) =
        "Understood: " + listOfNotNull(from?.let { "from $it" }, to?.let { "to $it" }).plus(extras).joinToString(", ")
    override fun stepFinding(name: String) = "Finding “$name”…"
    override fun stepFound(name: String, match: String, isStop: Boolean) = "“$name” → $match (${if (isStop) "bus stop" else "place"})"
    override fun stepNotFound(name: String) = "No match for “$name”"
    override val stepLocating = "Getting your location…"
    override fun stepLocated(accuracyM: Int) = "Using your location (±$accuracyM m)"
    override val stepNoLocation = "Location not available"
    override fun stepTimetable(time: String) = "Searching the DTC timetable for buses from $time…"
    override fun stepRanked(considered: Int, shown: Int) = "Checked $considered possible trips, ranked by time and expected crowding: $shown shown"
    override val stepWiden = "No stop within 600 m, widening to 1.5 km…"
    override fun stepNoStop(where: String) = "No bus stop within 1.5 km of $where"
    override val stepFailed = "Search failed"
    override fun prefName(p: String) = when (p) { "fastest" -> "fastest"; "least_crowded" -> "least crowded"; else -> "balanced" }
    override val noTrips = "I couldn't find a bus with at most one change in the next three hours. Try another time or place."
    override val firstOption = "That's the first option."
    override val lastOption = "That's the last option."
    override val sayPlaceAgain = "Okay, say the place again."
    override fun notFound(name: String) = "I couldn't find $name. Try another name."
    override fun planning(from: String?, to: String, leave: String?) =
        "Looking for buses${from?.let { " from $it" } ?: ""} to $to${leave?.let { ", $it" } ?: ""}."
    override fun searchFailed(msg: String) = "Sorry, the search didn't work. $msg"
    override fun found(n: Int) = if (n == 1) "Found one option." else "Found $n options."
    override fun option(i: Int, n: Int) = if (n > 1) "Option $i." else ""
    override fun walkThenBus(min: Int, stop: String, route: String, time: String) = "Walk ${mins(min, true)} to $stop, then take bus $route at $time."
    override fun busFrom(route: String, time: String, stop: String) = "Take bus $route at $time from $stop."
    override fun change(alight: String, board: String, walkMin: Int, route: String, time: String) =
        if (walkMin < 1 || alight == board) "Change at $board to bus $route at $time."
        else "Get off at $alight, walk ${mins(walkMin, true)} to $board, then take bus $route at $time."
    override fun summary(direct: Int, change: Int, walkMin: Int?): String {
        val parts = listOfNotNull(
            direct.takeIf { it > 0 }?.let { if (it == 1) "1 direct bus" else "$it direct buses" },
            change.takeIf { it > 0 }?.let { "$it with a change" },
        )
        val s = if (parts.isEmpty()) "" else "I found " + parts.joinToString(" and ") + "."
        return s + (walkMin?.let { " Walking takes about ${mins(it, true)}." } ?: "")
    }
    override fun showing(kindDirect: Boolean, n: Int) = if (kindDirect) "Direct buses only." else "Trips with a change."
    override fun noneOf(kindDirect: Boolean) = if (kindDirect) "There's no direct bus for this trip." else "There's no trip with a change this time."
    override fun walkOnly(min: Int, m: Int, approx: Boolean) =
        "Walking the whole way takes about ${mins(min, true)}, ${if (m >= 1000) "%.1f kilometres".format(m / 1000.0) else "$m metres"}${if (approx) ", roughly" else " along the roads"}."
    override val noWalk = "It's too far to walk, more than two kilometres."
    override fun guiding(stop: String) = "Okay, I'll guide you on foot to $stop."
    override val noGuide = "First ask me for a trip, then say guide me."
    override fun alerting(stop: String) = "Okay. Once you're on the bus, I'll tell you when $stop is coming."
    override fun getOff(stop: String, walkMin: Int) = "Get off at $stop" + (if (walkMin >= 1) ", then walk ${mins(walkMin, true)}." else ".")
    override fun arrive(time: String, total: Int) = "You'll arrive at $time, $total minutes in all."
    override fun crowd(cat: String) = when (cat) {
        "LOW" -> "Crowding: low."; "MODERATE" -> "Crowding: moderate."; "HIGH" -> "Crowding: high."
        "VERY_HIGH" -> "Crowding: very high."; else -> ""
    }
    override fun fare(rupees: Double) = "Fare ${rupees(rupees, "rupees", "paise")}."
    override fun alternatives(list: List<String>) =
        "Did you mean: " + list.mapIndexed { i, s -> "${i + 1}, $s." }.joinToString(" ") + " Say the number."
    override fun preferenceSet(p: String) = "Okay, " + when (p) { "fastest" -> "fastest"; "least_crowded" -> "least crowded"; else -> "best balance" } + " first."
    override fun leaving(at: String) = "leaving at $at"
    override fun leavingIn(min: Int) = if (min % 60 == 0 && min >= 60) "leaving in ${min / 60} hour${if (min > 60) "s" else ""}" else "leaving in $min minutes"
    override fun time(hhmm: String): String {
        val (h, m) = hm(hhmm) ?: return hhmm
        val h12 = if (h % 12 == 0) 12 else h % 12
        val ap = if (h < 12) "AM" else "PM"
        return if (m == 0) "$h12 $ap" else "$h12:${"%02d".format(m)} $ap"
    }
}

object VoiceHi : VoiceStrings {
    override val languageTag = "hi-IN"
    override val locale: Locale = Locale("hi", "IN")
    override val title = "आवाज़ सहायक"; override val open = "आवाज़ सहायक"
    override val tapToTalk = "बोलने के लिए दबाएँ"; override val tapToInterrupt = "रोककर बोलने के लिए दबाएँ"; override val tapWhenDone = "बोल चुके हों तो दबाएँ"
    override val end = "बंद करें"
    override val listening = "सुन रहे हैं…"; override val speaking = "बोल रहे हैं…"; override val thinking = "खोज रहे हैं…"
    override val you = "आप"; override val assistant = "सहायक"; override val currentLocation = "मौजूदा लोकेशन"
    override val micNeeded = "आवाज़ सहायक को माइक्रोफ़ोन की अनुमति चाहिए। आप लिखकर भी यात्रा खोज सकते हैं।"
    override val noRecognizer = "इस डिवाइस पर आवाज़ पहचान उपलब्ध नहीं है।"
    override val greeting = "नमस्ते! आपको कहाँ जाना है?"
    override val firstTip = "आप ऐसे बोल सकते हैं: साकेत से एम्स, कम भीड़ वाली बस, शाम 6 बजे।"
    override val askWhere = "कहाँ जाना है?"
    override val askFrom = "आपकी लोकेशन नहीं मिल रही। आप कहाँ से चलेंगे?"
    override val approxLocation = "आपकी लोकेशन सिर्फ़ अंदाज़न है। सही नतीजे के लिए बताइए आप कहाँ से चलेंगे।"
    override val sorry = "माफ़ कीजिए, फिर से बोलिए?"
    override val goingQuiet = "तैयार हों तो माइक बटन दबाइए।"
    override val bye = "ठीक है, नमस्ते!"
    override val help = "बताइए कहाँ जाना है, जैसे: साकेत से एम्स, कम भीड़ वाली, शाम 6 बजे। " +
        "यात्रा सुनने के बाद आप बोल सकते हैं: अगला, पिछला, दोबारा, सबसे जल्दी, कम भीड़, नया समय, गलत जगह, या दिखाओ।"
    override val hint = "और विकल्पों के लिए अगला बोलिए, या दिखाओ।"
    override val notUnderstood = "माफ़ कीजिए, समझ नहीं आया। अगला, दोबारा, या नई जगह बोलिए।"
    override val notUnderstoodNoTrip = "माफ़ कीजिए, समझ नहीं आया। कहाँ जाना है?"
    override val widened = "600 मीटर के अंदर कोई बस स्टॉप नहीं था, इसलिए डेढ़ किलोमीटर तक देखा।"
    override fun noStopAtAll(where: String) = "$where से डेढ़ किलोमीटर के अंदर कोई बस स्टॉप नहीं है।"
    override val noStopNearYou = "आपकी मौजूदा लोकेशन से डेढ़ किलोमीटर के अंदर दिल्ली का कोई बस स्टॉप नहीं है। आप कहाँ से चलेंगे?"
    override val dataNote = "बस का समय: DTC टाइमटेबल (तय समय, लाइव नहीं)। भीड़: ई-टिकट डेटा से बना मॉडल। जगहें: OpenStreetMap।"
    override fun understood(from: String?, to: String?, extras: List<String>) =
        "समझा: " + listOfNotNull(from?.let { "$it से" }, to?.let { "$it तक" }).plus(extras).joinToString(", ")
    override fun stepFinding(name: String) = "“$name” खोज रहे हैं…"
    override fun stepFound(name: String, match: String, isStop: Boolean) = "“$name” → $match (${if (isStop) "बस स्टॉप" else "जगह"})"
    override fun stepNotFound(name: String) = "“$name” नहीं मिला"
    override val stepLocating = "आपकी लोकेशन ले रहे हैं…"
    override fun stepLocated(accuracyM: Int) = "आपकी लोकेशन (±$accuracyM मी)"
    override val stepNoLocation = "लोकेशन नहीं मिली"
    override fun stepTimetable(time: String) = "$time के बाद की बसें DTC टाइमटेबल में खोज रहे हैं…"
    override fun stepRanked(considered: Int, shown: Int) = "$considered संभावित यात्राएँ जाँचीं, समय और भीड़ के हिसाब से क्रम: $shown दिखाईं"
    override val stepWiden = "600 मी में कोई स्टॉप नहीं, 1.5 किमी तक देख रहे हैं…"
    override fun stepNoStop(where: String) = "$where से 1.5 किमी में कोई बस स्टॉप नहीं"
    override val stepFailed = "खोज नहीं हो पाई"
    override fun prefName(p: String) = when (p) { "fastest" -> "सबसे जल्दी"; "least_crowded" -> "कम भीड़"; else -> "संतुलित" }
    override val noTrips = "अगले तीन घंटे में एक बदलाव तक वाली कोई बस नहीं मिली। कोई और समय या जगह बोलिए।"
    override val firstOption = "यह पहला विकल्प है।"
    override val lastOption = "यह आख़िरी विकल्प है।"
    override val sayPlaceAgain = "ठीक है, जगह फिर से बोलिए।"
    override fun notFound(name: String) = "$name नहीं मिला। कोई और नाम बोलिए।"
    override fun planning(from: String?, to: String, leave: String?) =
        "${from?.let { "$it से " } ?: ""}$to के लिए बसें देख रहे हैं${leave?.let { ", $it" } ?: ""}।"
    override fun searchFailed(msg: String) = "माफ़ कीजिए, खोज नहीं हो पाई। $msg"
    override fun found(n: Int) = if (n == 1) "एक विकल्प मिला।" else "$n विकल्प मिले।"
    override fun option(i: Int, n: Int) = if (n > 1) "विकल्प $i।" else ""
    override fun walkThenBus(min: Int, stop: String, route: String, time: String) = "$stop तक ${mins(min, false)} पैदल चलिए, फिर $time बस $route लीजिए।"
    override fun busFrom(route: String, time: String, stop: String) = "$stop से $time बस $route लीजिए।"
    override fun change(alight: String, board: String, walkMin: Int, route: String, time: String) =
        if (walkMin < 1 || alight == board) "$board पर $time बस $route में बदलिए।"
        else "$alight पर उतरकर ${mins(walkMin, false)} पैदल $board जाइए, फिर $time बस $route लीजिए।"
    override fun summary(direct: Int, change: Int, walkMin: Int?): String {
        val parts = listOfNotNull(
            direct.takeIf { it > 0 }?.let { "$it सीधी बस" },
            change.takeIf { it > 0 }?.let { "$it बदलाव वाली यात्रा" },
        )
        val s = if (parts.isEmpty()) "" else parts.joinToString(" और ") + " मिलीं।"
        return s + (walkMin?.let { " पैदल लगभग ${mins(it, false)}।" } ?: "")
    }
    override fun showing(kindDirect: Boolean, n: Int) = if (kindDirect) "सिर्फ़ सीधी बसें।" else "बदलाव वाली यात्राएँ।"
    override fun noneOf(kindDirect: Boolean) = if (kindDirect) "इस यात्रा के लिए कोई सीधी बस नहीं है।" else "इस बार बदलाव वाली कोई यात्रा नहीं है।"
    override fun walkOnly(min: Int, m: Int, approx: Boolean) =
        "पूरा रास्ता पैदल लगभग ${mins(min, false)}, ${if (m >= 1000) "%.1f किलोमीटर".format(m / 1000.0) else "$m मीटर"}${if (approx) ", अंदाज़न" else ", सड़क के रास्ते"}।"
    override val noWalk = "पैदल के लिए बहुत दूर है, दो किलोमीटर से ज़्यादा।"
    override fun guiding(stop: String) = "ठीक है, $stop तक पैदल रास्ता बताया जा रहा है।"
    override val noGuide = "पहले कोई यात्रा पूछिए, फिर कहिए रास्ता बताओ।"
    override fun alerting(stop: String) = "ठीक है। बस में बैठने के बाद $stop आने से पहले बताया जाएगा।"
    override fun getOff(stop: String, walkMin: Int) = "$stop पर उतरिए" + (if (walkMin >= 1) ", फिर ${mins(walkMin, false)} पैदल।" else "।")
    override fun arrive(time: String, total: Int) = "आप $time पहुँचेंगे, कुल $total मिनट।"
    override fun crowd(cat: String) = when (cat) {
        "LOW" -> "भीड़ कम।"; "MODERATE" -> "भीड़ मध्यम।"; "HIGH" -> "भीड़ ज़्यादा।"; "VERY_HIGH" -> "बहुत ज़्यादा भीड़।"; else -> ""
    }
    override fun fare(rupees: Double) = "किराया ${rupees(rupees, "रुपये", "पैसे")}।"
    override fun alternatives(list: List<String>) =
        "क्या आपका मतलब था: " + list.mapIndexed { i, s -> "${i + 1}, $s।" }.joinToString(" ") + " नंबर बोलिए।"
    override fun preferenceSet(p: String) = "ठीक है, पहले " + when (p) { "fastest" -> "सबसे जल्दी"; "least_crowded" -> "कम भीड़ वाले"; else -> "संतुलित" } + " विकल्प।"
    override fun leaving(at: String) = "$at निकलना"
    override fun leavingIn(min: Int) = if (min % 60 == 0 && min >= 60) "${min / 60} घंटे बाद निकलना" else "$min मिनट बाद निकलना"
    override fun time(hhmm: String): String {
        val (h, m) = hm(hhmm) ?: return hhmm
        val part = when (h) { in 4..11 -> "सुबह"; in 12..15 -> "दोपहर"; in 16..19 -> "शाम"; else -> "रात" }
        val h12 = if (h % 12 == 0) 12 else h % 12
        return if (m == 0) "$part $h12 बजे" else "$part $h12:${"%02d".format(m)}"
    }
}

fun voiceStrings(lang: Lang): VoiceStrings = if (lang == Lang.HI) VoiceHi else VoiceEn
