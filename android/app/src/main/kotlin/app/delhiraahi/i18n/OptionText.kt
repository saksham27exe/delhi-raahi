package app.delhiraahi.i18n

/** Words for one trip option on the results page: leave, walk, bus, change, reach. */
interface OptionText {
    fun leave(t: String): String
    fun reach(t: String): String
    fun walkTo(min: Int, m: Int?, stop: String): String
    fun atStop(stop: String): String
    fun busAt(route: String, time: String, inMin: Int?): String
    fun change(at: String, walk: Int, wait: Int): String
    fun reachAt(place: String, time: String, walk: Int, from: String): String
    fun liveNext(route: String, stopsAway: Int, inMin: Int): String
    val liveTooSoon: String
    val tagTimetable: String
    val tagLive: String

    companion object { fun of(hindi: Boolean): OptionText = if (hindi) OptionHi else OptionEn }
}

object OptionEn : OptionText {
    override fun leave(t: String) = "Leave $t"
    override fun reach(t: String) = "Reach $t"
    override fun walkTo(min: Int, m: Int?, stop: String) = "Walk $min min" + (m?.let { " ($it m)" } ?: "") + " to $stop"
    override fun atStop(stop: String) = "Start at $stop"
    override fun busAt(route: String, time: String, inMin: Int?) =
        "Bus $route at $time" + when { inMin == null -> ""; inMin <= 0 -> ", now"; else -> ", in $inMin min" }
    override fun change(at: String, walk: Int, wait: Int) =
        "Change at $at" + (if (walk >= 1) ", walk $walk min" else "") + (if (wait >= 1) ", wait $wait min" else "")
    override fun reachAt(place: String, time: String, walk: Int, from: String) =
        "Reach $place at $time" + if (walk >= 1) " ($walk min walk from $from)" else ""
    override fun liveNext(route: String, stopsAway: Int, inMin: Int) =
        "A $route bus is ${if (stopsAway == 1) "1 stop" else "$stopsAway stops"} away, ${if (inMin <= 0) "arriving now" else "about $inMin min"}"
    override val liveTooSoon = " — before you can walk there"
    override val tagTimetable = "timetable"
    override val tagLive = "live"
}

object OptionHi : OptionText {
    override fun leave(t: String) = "$t निकलें"
    override fun reach(t: String) = "$t पहुँचें"
    override fun walkTo(min: Int, m: Int?, stop: String) = "$stop तक $min मिनट पैदल" + (m?.let { " ($it मी)" } ?: "")
    override fun atStop(stop: String) = "$stop से शुरू"
    override fun busAt(route: String, time: String, inMin: Int?) =
        "बस $route, $time पर" + when { inMin == null -> ""; inMin <= 0 -> ", अभी"; else -> ", $inMin मिनट में" }
    override fun change(at: String, walk: Int, wait: Int) =
        "$at पर बस बदलें" + (if (walk >= 1) ", $walk मिनट पैदल" else "") + (if (wait >= 1) ", $wait मिनट इंतज़ार" else "")
    override fun reachAt(place: String, time: String, walk: Int, from: String) =
        "$place $time पर पहुँचें" + if (walk >= 1) " ($from से $walk मिनट पैदल)" else ""
    override fun liveNext(route: String, stopsAway: Int, inMin: Int) =
        "एक $route बस $stopsAway स्टॉप दूर है, ${if (inMin <= 0) "अभी आ रही है" else "लगभग $inMin मिनट"}"
    override val liveTooSoon = " — आपके पहुँचने से पहले"
    override val tagTimetable = "समय-सारणी"
    override val tagLive = "लाइव"
}
