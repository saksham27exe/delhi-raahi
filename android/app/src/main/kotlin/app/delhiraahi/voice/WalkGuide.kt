package app.delhiraahi.voice

import app.delhiraahi.data.WalkDirections
import app.delhiraahi.data.WalkStep
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Something worth saying while walking. Text is made by [GuideText] so it can be English or Hindi. */
sealed interface Cue {
    /** First words: which way to head, along which street, how far until the next turn. */
    data class Start(val step: WalkStep, val metres: Int, val next: WalkStep?) : Cue
    /** About 50 m before a turn. */
    data class Prepare(val step: WalkStep, val metres: Int) : Cue
    /** At the turn. */
    data class Now(val step: WalkStep) : Cue
    data object Arrive : Cue
    data object OffRoute : Cue
}

/**
 * Follows a walk along an OSRM foot route. Feed it GPS fixes; it returns what to say.
 * Progress is measured along the path (projecting each fix onto it), so a turn counts as passed once
 * you are beyond it on the path, even if no fix landed exactly on the corner.
 */
class WalkGuide(private val dir: WalkDirections) {
    private val lat0 = dir.path.firstOrNull()?.getOrNull(0) ?: dir.steps.firstOrNull()?.lat ?: 28.6
    private val lon0 = dir.path.firstOrNull()?.getOrNull(1) ?: dir.steps.firstOrNull()?.lon ?: 77.2
    private val kx = 111_320.0 * cos(lat0 * PI / 180)
    private val ky = 110_540.0

    private fun xy(lat: Double, lon: Double) = (lon - lon0) * kx to (lat - lat0) * ky

    private val pts: List<Pair<Double, Double>> = dir.path.filter { it.size >= 2 }.map { xy(it[0], it[1]) }
    /** Distance along the path at each path point. */
    private val cum: DoubleArray = DoubleArray(pts.size).also { c ->
        for (i in 1 until pts.size) c[i] = c[i - 1] + hypot(pts[i].first - pts[i - 1].first, pts[i].second - pts[i - 1].second)
    }
    val length: Double get() = cum.lastOrNull() ?: 0.0

    /** Where each manoeuvre sits along the path. */
    private val stepAlong: List<Double> = dir.steps.map { s -> project(xy(s.lat, s.lon), 0.0).first }

    /** Index of the next manoeuvre still ahead (0 is "depart"). */
    var next = 1
        private set
    var progress = 0.0
        private set
    /** Metres to the next manoeuvre, or to the end. */
    val toNext: Double get() = (stepAlong.getOrNull(next) ?: length) - progress
    val nextStep: WalkStep? get() = dir.steps.getOrNull(next)

    private var started = false
    private val prepared = mutableSetOf<Int>()
    private val nowSaid = mutableSetOf<Int>()
    private var arrived = false
    private var offCount = 0
    private var offSaid = false

    /** (distance along path, distance from path) of point p, searching from [minAlong]. */
    private fun project(p: Pair<Double, Double>, minAlong: Double): Pair<Double, Double> {
        if (pts.size < 2) return 0.0 to hypot(p.first, p.second)
        var best = Double.MAX_VALUE
        var along = 0.0
        for (i in 0 until pts.size - 1) {
            if (cum[i + 1] < minAlong) continue
            val (ax, ay) = pts[i]; val (bx, by) = pts[i + 1]
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (((p.first - ax) * dx + (p.second - ay) * dy) / len2).coerceIn(0.0, 1.0)
            val d = hypot(p.first - (ax + t * dx), p.second - (ay + t * dy))
            if (d < best) { best = d; along = cum[i] + t * (cum[i + 1] - cum[i]) }
        }
        return along to best
    }

    /** Feed a fix; returns cues to speak (usually none). [accuracy] in metres. */
    fun update(lat: Double, lon: Double, accuracy: Float = 10f): List<Cue> {
        val out = mutableListOf<Cue>()
        val (along, off) = project(xy(lat, lon), (progress - 40).coerceAtLeast(0.0))
        // Off the route: two fixes in a row clearly away from the path.
        val limit = maxOf(35.0, accuracy * 1.5)
        if (off > limit) { offCount++ } else { offCount = 0; offSaid = false }
        if (offCount >= 2 && !offSaid) { offSaid = true; out += Cue.OffRoute; return out }
        if (off <= limit) progress = maxOf(progress, along)

        if (!started) {
            started = true
            dir.steps.firstOrNull()?.let { out += Cue.Start(it, toNext.roundToInt().coerceAtLeast(0), nextStep) }
            if (toNext <= 70) prepared += next   // the start sentence already named this turn
        }
        // Pass manoeuvres we are already beyond.
        while (next < dir.steps.size - 1 && toNext < 8) {
            if (next !in nowSaid && toNext > -25) { nowSaid += next; out += Cue.Now(dir.steps[next]) }
            next++
        }
        val s = nextStep
        val d = toNext
        val last = next >= dir.steps.size - 1
        if (last) {
            if (!arrived && d <= 15) { arrived = true; out += Cue.Arrive }
        } else if (s != null) {
            if (d <= 15 && next !in nowSaid) { nowSaid += next; out += Cue.Now(s) }
            else if (d in 25.0..70.0 && next !in prepared && next !in nowSaid) { prepared += next; out += Cue.Prepare(s, roundM(d)) }
        }
        return out
    }

    companion object {
        /** Spoken distances: nearest 10 m below 100 m, nearest 50 m above. */
        fun roundM(m: Double): Int = if (m < 100) ((m / 10).roundToInt() * 10).coerceAtLeast(10) else (m / 50).roundToInt() * 50
    }
}

/** Turns cues into words. */
interface GuideText {
    fun say(c: Cue, target: String): String
    val lookingUp: String; val noRoute: String; val tooFar: String; val noLocation: String; val rerouting: String
    val repeat: String; val stop: String; val title: String; val toward: (String) -> String; val metresLeft: (Int) -> String

    companion object { fun of(hindi: Boolean): GuideText = if (hindi) GuideHi else GuideEn }
}

private fun compass(b: Int, names: List<String>) = names[(((b % 360) + 360) % 360 + 22) / 45 % 8]

private fun distEn(m: Int) = if (m >= 1000) "%.1f kilometres".format(m / 1000.0) else "$m metres"
private fun distHi(m: Int) = if (m >= 1000) "%.1f किलोमीटर".format(m / 1000.0) else "$m मीटर"

object GuideEn : GuideText {
    private val dirs = listOf("north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west")
    private fun turn(s: WalkStep): String = when {
        s.type == "arrive" -> "you will reach the end"
        s.type == "roundabout" || s.type == "rotary" || s.type == "exit roundabout" -> "go round the roundabout and take the exit"
        else -> when (s.modifier) {
            "left" -> "turn left"; "right" -> "turn right"
            "slight left" -> "keep slightly left"; "slight right" -> "keep slightly right"
            "sharp left" -> "turn sharp left"; "sharp right" -> "turn sharp right"
            "uturn" -> "turn around"
            else -> "continue straight"
        }
    }
    private fun onto(s: WalkStep) = if (s.name.isNotBlank() && s.type != "arrive") " onto ${s.name}" else ""
    override fun say(c: Cue, target: String): String = when (c) {
        is Cue.Start -> "Head ${compass(c.step.bearing_after, dirs)}" + (if (c.step.name.isNotBlank()) " on ${c.step.name}" else "") +
            " for ${distEn(WalkGuide.roundM(c.metres.toDouble()))}" +
            (c.next?.takeIf { it.type != "arrive" }?.let { ", then ${turn(it)}${onto(it)}." } ?: ", to $target.")
        is Cue.Prepare -> "In ${distEn(c.metres)}, ${turn(c.step)}${onto(c.step)}."
        is Cue.Now -> turn(c.step).replaceFirstChar { it.uppercase() } + onto(c.step) + " now."
        Cue.Arrive -> "You have arrived at $target."
        Cue.OffRoute -> "You seem to be off the route. Finding a new way."
    }
    override val lookingUp = "Finding the walking route…"
    override val noRoute = "Couldn't get walking directions right now. Check your connection and try again."
    override val tooFar = "You are more than 5 kilometres away. Walking directions start when you are closer."
    override val noLocation = "Turn on location so I can guide you."
    override val rerouting = "Finding a new way…"
    override val repeat = "Repeat"; override val stop = "Stop guiding"; override val title = "Walk with voice"
    override val toward: (String) -> String = { "To $it" }
    override val metresLeft: (Int) -> String = { "${distEn(it)} to go" }
}

object GuideHi : GuideText {
    private val dirs = listOf("उत्तर", "उत्तर-पूर्व", "पूर्व", "दक्षिण-पूर्व", "दक्षिण", "दक्षिण-पश्चिम", "पश्चिम", "उत्तर-पश्चिम")
    private fun turn(s: WalkStep): String = when {
        s.type == "arrive" -> "आप पहुँच जाएँगे"
        s.type == "roundabout" || s.type == "rotary" || s.type == "exit roundabout" -> "गोल चक्कर से घूमकर निकलिए"
        else -> when (s.modifier) {
            "left" -> "बाएँ मुड़िए"; "right" -> "दाएँ मुड़िए"
            "slight left" -> "हल्का बाएँ रहिए"; "slight right" -> "हल्का दाएँ रहिए"
            "sharp left" -> "पूरा बाएँ मुड़िए"; "sharp right" -> "पूरा दाएँ मुड़िए"
            "uturn" -> "पीछे मुड़िए"
            else -> "सीधे चलते रहिए"
        }
    }
    private fun onto(s: WalkStep) = if (s.name.isNotBlank() && s.type != "arrive") ", ${s.name} पर" else ""
    override fun say(c: Cue, target: String): String = when (c) {
        is Cue.Start -> (if (c.step.name.isNotBlank()) "${c.step.name} पर " else "") + "${compass(c.step.bearing_after, dirs)} की ओर " +
            "${distHi(WalkGuide.roundM(c.metres.toDouble()))} चलिए" +
            (c.next?.takeIf { it.type != "arrive" }?.let { ", फिर ${turn(it)}${onto(it)}।" } ?: ", $target तक।")
        is Cue.Prepare -> "${distHi(c.metres)} बाद ${turn(c.step)}${onto(c.step)}।"
        is Cue.Now -> "अब ${turn(c.step)}${onto(c.step)}।"
        Cue.Arrive -> "आप $target पहुँच गए हैं।"
        Cue.OffRoute -> "लगता है आप रास्ते से हट गए हैं। नया रास्ता ढूँढा जा रहा है।"
    }
    override val lookingUp = "पैदल रास्ता ढूँढा जा रहा है…"
    override val noRoute = "अभी पैदल रास्ता नहीं मिल पाया। इंटरनेट देखकर फिर कोशिश कीजिए।"
    override val tooFar = "आप 5 किलोमीटर से ज़्यादा दूर हैं। पास पहुँचने पर पैदल रास्ता बताया जाएगा।"
    override val noLocation = "रास्ता बताने के लिए लोकेशन चालू कीजिए।"
    override val rerouting = "नया रास्ता ढूँढा जा रहा है…"
    override val repeat = "फिर से सुनें"; override val stop = "रास्ता बताना बंद करें"; override val title = "बोलकर पैदल रास्ता"
    override val toward: (String) -> String = { "$it तक" }
    override val metresLeft: (Int) -> String = { "${distHi(it)} बाकी" }
}
