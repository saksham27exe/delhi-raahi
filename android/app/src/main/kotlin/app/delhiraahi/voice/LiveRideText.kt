package app.delhiraahi.voice

import kotlin.math.abs
import kotlin.math.roundToInt

/** Words for the live ride (ticket "Start ride") and the Live tab's bus panel. */
interface LiveText {
    val title: String; val startRide: String; val notMyBus: String; val endRide: String
    fun approaching(route: String, stops: Int, min: Int): String
    fun fiveMin(route: String): String
    fun arrivingNow(route: String): String
    fun boarded(route: String, stops: Int, to: String): String
    fun noLive(route: String, time: String?): String
    val missed: String
    fun stopsAway(n: Int): String
    fun arrivesAt(time: String, min: Int): String
    fun basis(b: String?): String
    fun updated(sec: Int): String
    fun delay(min: Double?): String?
    fun speed(kmh: Double): String
    fun towards(stop: String): String
    val upcoming: String; val close: String; val unmatched: String

    companion object { fun of(hindi: Boolean): LiveText = if (hindi) LiveHi else LiveEn }
}

object LiveEn : LiveText {
    override val title = "Live ride"; override val startRide = "Start ride"; override val notMyBus = "Not my bus"; override val endRide = "End ride"
    private fun stopsW(n: Int) = if (n == 1) "1 stop" else "$n stops"
    private fun minW(n: Int) = if (n <= 1) "about a minute" else "about $n minutes"
    override fun approaching(route: String, stops: Int, min: Int) =
        "Bus ${spokenRoute(route)} is ${stopsW(stops)} away, ${minW(min)}. I'll tell you when it's close."
    override fun fiveMin(route: String) = "Bus ${spokenRoute(route)} is about 5 minutes away."
    override fun arrivingNow(route: String) = "Bus ${spokenRoute(route)} is arriving now. Get ready to board."
    override fun boarded(route: String, stops: Int, to: String) = "You're on bus ${spokenRoute(route)}. ${stopsW(stops)} to $to. I'll tell you when to get off."
    override fun noLive(route: String, time: String?) =
        "No live bus of route ${spokenRoute(route)} is heading to your stop yet." + (time?.let { " The timetable says the next one is at $it." } ?: "") + " I'll keep checking."
    override val missed = "Looks like you didn't get on that bus. Following the next one."
    override fun stopsAway(n: Int) = if (n == 0) "At your stop" else "${stopsW(n)} away"
    override fun arrivesAt(time: String, min: Int) = "about $time" + if (min >= 1) " (in $min min)" else " (now)"
    override fun basis(b: String?) = when (b) {
        "live_estimate" -> "Live bus position and its delay"
        "live_estimate_typical_running_time" -> "Live bus position and typical running time"
        else -> "Timetable only"
    }
    override fun updated(sec: Int) = if (sec < 60) "updated $sec s ago" else "updated ${sec / 60} min ago"
    override fun delay(min: Double?) = min?.let { val m = it.roundToInt(); when { m >= 1 -> "$m min late"; m <= -1 -> "${abs(m)} min early"; else -> "on time" } }
    override fun speed(kmh: Double) = "${kmh.roundToInt()} km/h"
    override fun towards(stop: String) = "towards $stop"
    override val upcoming = "Next stops"; override val close = "Close"
    override val unmatched = "This bus could not be matched to a route in the timetable."
}

object LiveHi : LiveText {
    override val title = "लाइव यात्रा"; override val startRide = "यात्रा शुरू करें"; override val notMyBus = "यह मेरी बस नहीं"; override val endRide = "यात्रा खत्म"
    override fun approaching(route: String, stops: Int, min: Int) =
        "बस ${spokenRoute(route)} $stops स्टॉप दूर है, लगभग ${min.coerceAtLeast(1)} मिनट। पास आने पर बताया जाएगा।"
    override fun fiveMin(route: String) = "बस ${spokenRoute(route)} लगभग 5 मिनट में आ रही है।"
    override fun arrivingNow(route: String) = "बस ${spokenRoute(route)} आ रही है। चढ़ने के लिए तैयार हो जाइए।"
    override fun boarded(route: String, stops: Int, to: String) = "आप बस ${spokenRoute(route)} में हैं। $to तक $stops स्टॉप। उतरने से पहले बताया जाएगा।"
    override fun noLive(route: String, time: String?) =
        "रूट ${spokenRoute(route)} की कोई लाइव बस अभी आपके स्टॉप की ओर नहीं दिख रही।" + (time?.let { " समय-सारणी के हिसाब से अगली बस $it पर है।" } ?: "") + " हम देखते रहेंगे।"
    override val missed = "लगता है आप उस बस में नहीं चढ़े। अगली बस पर नज़र रखी जा रही है।"
    override fun stopsAway(n: Int) = if (n == 0) "आपके स्टॉप पर" else "$n स्टॉप दूर"
    override fun arrivesAt(time: String, min: Int) = "लगभग $time" + if (min >= 1) " ($min मिनट में)" else " (अभी)"
    override fun basis(b: String?) = when (b) {
        "live_estimate" -> "बस की लाइव जगह और देरी से"
        "live_estimate_typical_running_time" -> "बस की लाइव जगह और सामान्य समय से"
        else -> "सिर्फ़ समय-सारणी"
    }
    override fun updated(sec: Int) = if (sec < 60) "$sec सेकंड पहले" else "${sec / 60} मिनट पहले"
    override fun delay(min: Double?) = min?.let { val m = it.roundToInt(); when { m >= 1 -> "$m मिनट देर"; m <= -1 -> "${abs(m)} मिनट पहले"; else -> "समय पर" } }
    override fun speed(kmh: Double) = "${kmh.roundToInt()} किमी/घं"
    override fun towards(stop: String) = "$stop की ओर"
    override val upcoming = "अगले स्टॉप"; override val close = "बंद करें"
    override val unmatched = "यह बस समय-सारणी के किसी रूट से नहीं मिल पाई।"
}
