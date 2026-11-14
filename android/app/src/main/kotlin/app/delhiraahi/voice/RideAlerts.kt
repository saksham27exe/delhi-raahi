package app.delhiraahi.voice

import app.delhiraahi.data.Leg
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

/** One stop on the bus leg. [time]: scheduled minutes since midnight (unwrapped past midnight), if known. */
data class RideStop(val name: String, val lat: Double, val lon: Double, val time: Float?)

/** Something worth saying on the bus. */
sealed interface RideCue {
    data class Boarded(val route: String, val stops: Int, val minutes: Int, val to: String) : RideCue
    data class TwoLeft(val next: String, val byClock: Boolean) : RideCue
    data class NextIsYours(val stop: String, val byClock: Boolean) : RideCue
    data class Arrive(val stop: String, val byClock: Boolean) : RideCue
}

/**
 * Follows one bus leg from boarding stop to alighting stop. Feed it GPS fixes ([update]) or, when GPS is
 * poor, the clock ([byClock]); it says when you are two stops away, one stop away, and at your stop.
 * Progress only moves forward, so a bad fix can't make it announce the same stop twice.
 */
class RideTracker(val stops: List<RideStop>, val route: String, val minutes: Int) {
    private val lat0 = stops.firstOrNull()?.lat ?: 28.6
    private val lon0 = stops.firstOrNull()?.lon ?: 77.2
    private val kx = 111_320.0 * cos(lat0 * PI / 180)
    private val ky = 110_540.0
    private fun xy(lat: Double, lon: Double) = (lon - lon0) * kx to (lat - lat0) * ky
    private val pts = stops.map { xy(it.lat, it.lon) }
    private val cum = DoubleArray(pts.size).also { c ->
        for (i in 1 until pts.size) c[i] = c[i - 1] + hypot(pts[i].first - pts[i - 1].first, pts[i].second - pts[i - 1].second)
    }

    val last: Int get() = stops.lastIndex
    /** Index of the stop we are at or have last passed (0 = boarding stop). */
    var passed = 0
        private set
    val left: Int get() = last - passed
    val nextStop: RideStop? get() = stops.getOrNull(passed + 1)
    var lastWasClock = false
        private set

    private var boardedSaid = false
    private val said = mutableSetOf<Int>()   // remaining-stop counts already announced (2, 1, 0)

    /** Distance along the stop line and distance from it. */
    private fun project(p: Pair<Double, Double>): Pair<Double, Double> {
        if (pts.size < 2) return 0.0 to 0.0
        var best = Double.MAX_VALUE
        var along = 0.0
        for (i in 0 until pts.size - 1) {
            val (ax, ay) = pts[i]; val (bx, by) = pts[i + 1]
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (((p.first - ax) * dx + (p.second - ay) * dy) / len2).coerceIn(0.0, 1.0)
            val d = hypot(p.first - (ax + t * dx), p.second - (ay + t * dy))
            if (d < best) { best = d; along = cum[i] + t * (cum[i + 1] - cum[i]) }
        }
        return along to best
    }

    /** First words when the ride starts. */
    fun start(): List<RideCue> {
        if (boardedSaid || stops.size < 2) return emptyList()
        boardedSaid = true
        return listOf(RideCue.Boarded(route, last, minutes, stops.last().name))
    }

    /** A GPS fix. Fixes far from the bus line (> 400 m, e.g. a bad network fix) are ignored. */
    fun update(lat: Double, lon: Double): List<RideCue> {
        val p = xy(lat, lon)
        val (along, off) = project(p)
        if (off > 400) return emptyList()
        val endD = hypot(p.first - pts.last().first, p.second - pts.last().second)
        // A stop counts as reached when we are within 40 m before it along the line.
        var reached = (0..last).lastOrNull { cum[it] <= along + 40 } ?: 0
        if (endD < 80) reached = last
        return advance(reached, clock = false)
    }

    /** Timetable estimate: the last stop whose scheduled time has passed. */
    fun byClock(nowMin: Float): List<RideCue> {
        val reached = (0..last).lastOrNull { k -> stops[k].time?.let { it <= nowMin } == true } ?: 0
        return advance(reached, clock = true)
    }

    private fun advance(reached: Int, clock: Boolean): List<RideCue> {
        if (reached <= passed) return emptyList()
        passed = reached
        lastWasClock = clock
        val out = mutableListOf<RideCue>()
        val r = left
        when {
            r == 0 && 0 !in said -> { said += 0; out += RideCue.Arrive(stops.last().name, clock) }
            r == 1 && 1 !in said -> { said += 1; out += RideCue.NextIsYours(stops.last().name, clock) }
            r == 2 && 2 !in said -> { said += 2; out += RideCue.TwoLeft(stops[passed + 1].name, clock) }
        }
        return out
    }

    companion object {
        private fun clock(hhmm: String?): Int? {
            val p = hhmm?.split(":") ?: return null
            if (p.size < 2) return null
            return (p[0].trim().toIntOrNull() ?: return null) * 60 + (p[1].take(2).toIntOrNull() ?: return null)
        }

        /** Stops of a leg from its segments: boarding stop, then the end of each stretch. */
        fun of(leg: Leg): RideTracker {
            val ref = clock(leg.departure) ?: 0
            fun un(m: Int?) = m?.let { (if (it < ref - 360) it + 1440 else it).toFloat() }
            val segs = leg.segments
            val stops = if (segs.isEmpty()) emptyList() else buildList {
                add(RideStop(segs[0].from_stop, segs[0].lat, segs[0].lon, un(clock(segs[0].depart) ?: ref)))
                segs.forEachIndexed { i, sg ->
                    val t = if (i == segs.lastIndex) clock(leg.arrival) else clock(segs[i + 1].depart)
                    add(RideStop(sg.to_stop, sg.to_lat, sg.to_lon, un(t)))
                }
            }
            return RideTracker(stops, leg.route, leg.in_vehicle_minutes.roundToInt())
        }
    }
}

/** Words for ride cues. */
interface RideText {
    fun say(c: RideCue): String
    fun changeHere(route: String, stop: String, walkMin: Int): String
    val destinationNext: String
    val title: String; val stopsLeft: (Int) -> String; val nextStop: (String) -> String; val byTimetable: String
    val walkNext: String; val done: String

    companion object { fun of(hindi: Boolean): RideText = if (hindi) RideHi else RideEn }
}

object RideEn : RideText {
    private fun stopsWord(n: Int) = if (n == 1) "1 stop" else "$n stops"
    override fun say(c: RideCue): String = when (c) {
        is RideCue.Boarded -> "On bus ${spokenRoute(c.route)} to ${c.to}: ${stopsWord(c.stops)}, about ${c.minutes} minutes. I'll tell you when to get off."
        is RideCue.TwoLeft -> "Two stops to go. Next stop: ${c.next}." + if (c.byClock) " Going by the timetable." else ""
        is RideCue.NextIsYours -> "Your stop, ${c.stop}, is next. Get ready to get off." + if (c.byClock) " Going by the timetable, so please check." else ""
        is RideCue.Arrive -> if (c.byClock) "By the timetable you should be at ${c.stop} now. Please check before getting off."
            else "This is ${c.stop}. Get off here."
    }
    override fun changeHere(route: String, stop: String, walkMin: Int) =
        if (walkMin >= 1) "Next, walk about $walkMin minutes to $stop and take bus ${spokenRoute(route)}."
        else "Next, take bus ${spokenRoute(route)} from $stop."
    override val destinationNext = "Next, walk to your destination."
    override val title = "Ride with alerts"
    override val stopsLeft: (Int) -> String = { if (it == 0) "Your stop" else if (it == 1) "1 stop to go" else "$it stops to go" }
    override val nextStop: (String) -> String = { "Next: $it" }
    override val byTimetable = "GPS is weak; following the timetable."
    override val walkNext = "Walk with voice"
    override val done = "Done"
}

object RideHi : RideText {
    override fun say(c: RideCue): String = when (c) {
        is RideCue.Boarded -> "बस ${spokenRoute(c.route)} में ${c.to} तक: ${c.stops} स्टॉप, लगभग ${c.minutes} मिनट। उतरने से पहले बताया जाएगा।"
        is RideCue.TwoLeft -> "दो स्टॉप बाकी। अगला स्टॉप: ${c.next}।" + if (c.byClock) " समय-सारणी के हिसाब से।" else ""
        is RideCue.NextIsYours -> "अगला स्टॉप आपका है, ${c.stop}। उतरने के लिए तैयार हो जाइए।" + if (c.byClock) " यह समय-सारणी के हिसाब से है, कृपया देख लीजिए।" else ""
        is RideCue.Arrive -> if (c.byClock) "समय-सारणी के हिसाब से अब ${c.stop} आ गया होगा। उतरने से पहले देख लीजिए।"
            else "${c.stop} आ गया। यहाँ उतरिए।"
    }
    override fun changeHere(route: String, stop: String, walkMin: Int) =
        if (walkMin >= 1) "अब लगभग $walkMin मिनट पैदल $stop जाइए और बस ${spokenRoute(route)} लीजिए।"
        else "अब $stop से बस ${spokenRoute(route)} लीजिए।"
    override val destinationNext = "अब पैदल अपनी मंज़िल तक जाइए।"
    override val title = "उतरने की सूचना"
    override val stopsLeft: (Int) -> String = { if (it == 0) "आपका स्टॉप" else "$it स्टॉप बाकी" }
    override val nextStop: (String) -> String = { "अगला: $it" }
    override val byTimetable = "GPS कमज़ोर है; समय-सारणी से चल रहे हैं।"
    override val walkNext = "बोलकर पैदल रास्ता"
    override val done = "ठीक है"
}

/** "0405(NS)DOWN" → "405": route numbers as people say them (same rule as the voice assistant). */
internal fun spokenRoute(route: String): String =
    app.delhiraahi.ui.common.routeName(route).replace(Regex("\\(.*?\\)"), "").trim().trimStart('0').ifEmpty { route }
