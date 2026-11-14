package app.delhiraahi.voice

import android.content.Context
import android.util.Log
import app.delhiraahi.BuildConfig
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import app.delhiraahi.data.Api
import app.delhiraahi.data.ApiException
import app.delhiraahi.data.DELHI_ZONE
import app.delhiraahi.data.DeviceLocation
import app.delhiraahi.data.Journey
import app.delhiraahi.data.LeaveAt
import app.delhiraahi.data.Load
import app.delhiraahi.data.PickedPlace
import app.delhiraahi.data.Preference
import app.delhiraahi.data.TripViewModel
import app.delhiraahi.ui.common.routeName
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.roundToInt

private const val TAG = "DelhiRaahiVoice"

enum class VoiceStatus { IDLE, SPEAKING, LISTENING, THINKING }

/** A transcript line: the traveller, the assistant, or a working step (spinner until [done]). */
data class VoiceLine(val user: Boolean, val text: String, val step: Boolean = false, val done: Boolean = true,
                     /** For what the traveller said: how sure the assistant is it heard right (0-100). */
                     val sure: Int? = null)

/** The traveller said "stop" (or went quiet): end the conversation. */
private class EndConversation : Exception()

/**
 * Hands-free trip planning. One loop: listen → understand the whole sentence → act → speak.
 * No confirmation questions: we act on the best match and say what we did; "wrong place" offers alternatives.
 */
class VoiceSession(private val ctx: Context, private val trip: TripViewModel, private val vs: VoiceStrings) {
    var status by mutableStateOf(VoiceStatus.IDLE)
        private set
    var partial by mutableStateOf("")
        private set
    val log = mutableStateListOf<VoiceLine>()

    /** Set by the screen: open option [i] on screen; the conversation ends. */
    var onShow: (Int) -> Unit = {}
    var onGuide: (Double, Double, String) -> Unit = { _, _, _ -> }
    var onRide: (Int) -> Unit = {}
    /** Buses found: put the results page on screen while the options are read out. */
    var onResults: () -> Unit = {}

    private val speaker = Speaker(ctx)
    private val listener = Listener(ctx)

    private data class Candidate(val spoken: String, val place: PickedPlace, val isStop: Boolean)

    private var options: List<Journey> = emptyList()
    private var view: List<Int> = emptyList()            // indices into options under the current filter
    private var walkOption: app.delhiraahi.data.WalkOption? = null
    private var idx = 0                                   // position in view
    private var hintGiven = false
    private var destination: Candidate? = null
    private var alternatives: List<Candidate> = emptyList()
    private var awaitingChoice = false
    private var pendingTo: Candidate? = null      // destination known, waiting for an origin

    /** The big button: cut speech short, or finish listening early. Returns false when there is nothing to do. */
    fun tap(): Boolean = when (status) {
        VoiceStatus.SPEAKING -> { speaker.stop(); true }
        VoiceStatus.LISTENING -> { listener.finish(); true }
        else -> false
    }

    fun close() { speaker.shutdown(); listener.destroy() }

    // ---- visible working steps -----------------------------------------------------------
    private fun step(text: String): Int { log += VoiceLine(false, text, step = true, done = false); return log.lastIndex }
    private fun done(i: Int, text: String) { if (i in log.indices) log[i] = log[i].copy(text = text, done = true) }
    private fun note(text: String) { log += VoiceLine(false, text, step = true, done = true) }

    // ---- speaking and listening ---------------------------------------------------------
    private suspend fun say(text: String) {
        val t = text.replace(Regex("\\s+"), " ").trim()
        if (t.isEmpty()) return
        status = VoiceStatus.SPEAKING
        log += VoiceLine(false, t)
        try { speaker.say(t, vs.locale) } finally { status = VoiceStatus.IDLE }
    }

    private suspend fun hear(): Heard.Text {
        repeat(2) { attempt ->
            listener.beep()
            delay(if (attempt == 0 && log.isEmpty()) 60 else 120)
            status = VoiceStatus.LISTENING
            partial = ""
            val h = try { listener.listen(vs.languageTag) { partial = it } } finally { status = VoiceStatus.IDLE; partial = "" }
            if (BuildConfig.DEBUG && h is Heard.Text) {
                Log.d(TAG, "scores: ${h.scores?.joinToString()}")
                h.options.forEach { Log.d(TAG, "heard: $it -> ${Understand.parse(it)}") }
            }
            when (h) {
                is Heard.Text -> {
                    log += VoiceLine(true, h.options.first(), sure = Confidence.percent(Confidence.score(h.options, h.scores)))
                    return h
                }
                Heard.NoPermission -> { say(vs.micNeeded); throw EndConversation() }
                Heard.Nothing -> if (attempt == 0) say(vs.sorry)
                is Heard.Failed -> if (attempt == 0) delay(400)
            }
        }
        say(vs.goingQuiet)
        throw EndConversation()
    }

    // ---- conversation --------------------------------------------------------------------
    suspend fun run() {
        try {
            if (!listener.available()) { say(vs.noRecognizer); return }
            // Like Siri / Bixby: no spoken greeting. A quick sound, and the mic is open at once;
            // the greeting and an example are shown on screen instead.
            var unsure = 0                  // unsure hearings in a row
            while (true) {
                val h = hear()
                val u = Understand.best(h.options)
                val score = Confidence.score(h.options, h.scores)
                when (Confidence.verdict(score, hasPlace = u.to != null || u.from != null, hasCommand = u.command != null)) {
                    Confidence.Verdict.GO -> { unsure = 0; handle(h.options, u) }
                    // Not sure: ask again once; if it is still unclear, check the best guess instead of looping.
                    Confidence.Verdict.ASK_AGAIN -> if (unsure++ == 0) say(vs.notSure) else { unsure = 0; confirm(h, u) }
                    Confidence.Verdict.CONFIRM -> { unsure = 0; confirm(h, u) }
                }
            }
        } catch (_: EndConversation) {
        } finally {
            status = VoiceStatus.IDLE
        }
    }

    /** "Did you say … ?" Yes → carry on with it. No → ask again. A new sentence (with a place) is used instead. */
    private suspend fun confirm(h: Heard.Text, u: Understood) {
        say(vs.checkHeard(h.options.first()))
        val a = hear()
        val ua = Understand.best(a.options)
        when {
            ua.to != null || ua.from != null -> handle(a.options, ua)
            ua.command == Said.Yes -> handle(h.options, u)
            ua.command == Said.Stop -> { say(vs.bye); throw EndConversation() }
            else -> say(vs.sayItAgain)
        }
    }

    private fun okay(x: String) = (if (vs.languageTag.startsWith("hi")) "ठीक है, " else "Okay, ") + x + "."

    private fun leaveSpecText(l: LeaveSpec): String = when (l) {
        LeaveSpec.Now -> vs.leaving(vs.time(ZonedDateTime.now(DELHI_ZONE).let { "%02d:%02d".format(it.hour, it.minute) }))
        is LeaveSpec.InMinutes -> vs.leavingIn(l.min)
        is LeaveSpec.At -> vs.leaving((if (l.tomorrow) (if (vs.languageTag.startsWith("hi")) "कल " else "tomorrow ") else "") + vs.time("%02d:%02d".format(l.hour, l.minute)))
    }

    private suspend fun handle(heard: List<String>, u: Understood) {
        if (u.to != null || u.from != null || u.preference != null || u.leave != null)
            note(vs.understood(u.from ?: if (u.fromHere) vs.currentLocation else null, u.to,
                listOfNotNull(u.preference?.let { vs.prefName(it) }, u.leave?.let { leaveSpecText(it) })))
        val choosing = awaitingChoice
        awaitingChoice = false
        val cmd = u.command
        when {
            cmd == Said.Stop -> { say(vs.bye); throw EndConversation() }
            cmd == Said.Help -> say(vs.help)
            choosing && cmd is Said.Number -> {
                val c = alternatives.getOrNull(cmd.n - 1) ?: run { say(vs.sayPlaceAgain); return }
                val old = destination
                alternatives = listOfNotNull(old) + alternatives.filter { it != c }
                plan(trip.from ?: here() ?: return, c)
            }
            u.wrongPlace || (cmd == Said.No && options.isNotEmpty()) -> {
                if (alternatives.isEmpty()) say(vs.sayPlaceAgain)
                else { say(vs.alternatives(alternatives.take(3).map { it.spoken })); awaitingChoice = true }
            }
            pendingTo != null && cmd == null && (u.to != null || u.from != null) -> {
                val names = placeNames(heard) { it.to ?: it.from }
                val from = resolveFirst(names) ?: return
                val to = pendingTo!!
                pendingTo = null
                applyPrefs(u)
                plan(from.place, to)
            }
            u.to != null -> {
                applyPrefs(u)
                val to = resolveFirst(placeNames(heard) { it.to }, keepAlternatives = true) ?: return
                val from = when {
                    u.from != null -> resolveFirst(placeNames(heard) { it.from })?.place ?: return
                    else -> here() ?: run { pendingTo = to; say(vs.askFrom); return }
                }
                plan(from, to)
            }
            // "from Saket" after a trip: same destination, new start. Only for a short phrase, so a garbled
            // "from A ... B" never silently reuses the old destination.
            u.from != null && destination != null && u.from.split(" ").size <= 3 -> {
                // "from Saket" after a trip: same destination, new start.
                val from = resolveFirst(placeNames(heard) { it.from }) ?: return
                applyPrefs(u)
                plan(from.place, destination!!)
            }
            u.preference != null || u.leave != null -> {
                val said = applyPrefs(u)
                if (destination != null && trip.from != null) { say(said); search() }
                else say(said + " " + vs.askWhere)
            }
            (options.isNotEmpty() || walkOption != null) && cmd != null -> results(cmd)
            else -> say(if (options.isEmpty()) vs.notUnderstoodNoTrip else vs.notUnderstood)
        }
    }

    private suspend fun results(cmd: Said) {
        when (cmd) {
            Said.Next -> if (idx < view.lastIndex) { idx++; say(describe(idx)) } else say(vs.lastOption)
            Said.Previous -> if (idx > 0) { idx--; say(describe(idx)) } else say(vs.firstOption)
            Said.Repeat, Said.Yes -> say(describe(idx))
            is Said.Number -> if (cmd.n - 1 in view.indices) { idx = cmd.n - 1; say(describe(idx)) } else say(vs.lastOption)
            Said.DirectOnly, Said.WithChange -> {
                val direct = cmd == Said.DirectOnly
                val v = options.indices.filter { (options[it].transfers == 0) == direct }
                trip.kind = if (direct) app.delhiraahi.data.TripKind.DIRECT else app.delhiraahi.data.TripKind.CHANGE
                if (v.isEmpty()) say(vs.noneOf(direct)) else { view = v; idx = 0; say(vs.showing(direct, v.size) + " " + describe(0)) }
            }
            Said.WalkOnly -> walkOption?.let { w ->
                trip.kind = app.delhiraahi.data.TripKind.WALK
                say(vs.walkOnly(w.minutes.roundToInt(), w.distance_m.roundToInt(), w.basis != "osm_road"))
            } ?: say(vs.noWalk)
            Said.Fastest, Said.LeastCrowded, Said.Balanced -> {
                trip.preference = when (cmd) { Said.Fastest -> Preference.FASTEST; Said.LeastCrowded -> Preference.LEAST_CROWDED; else -> Preference.BALANCED }
                say(vs.preferenceSet(trip.preference.wire))
                search()
            }
            Said.Alert -> {
                val i = view.getOrNull(idx)
                if (i == null || options.getOrNull(i)?.legs.isNullOrEmpty()) say(vs.noGuide) else {
                    say(vs.alerting(options[i].legs.first().alight_stop))
                    trip.selected = i
                    onRide(i)
                    throw EndConversation()
                }
            }
            Said.Guide -> {
                val j = options.getOrNull(view.getOrNull(idx) ?: 0)
                val first = j?.legs?.firstOrNull()
                val seg = first?.segments?.firstOrNull()
                if (first == null || seg == null) say(vs.noGuide) else {
                    say(vs.guiding(first.board_stop))
                    onGuide(seg.lat, seg.lon, first.board_stop)
                    throw EndConversation()
                }
            }
            Said.Show -> {
                val j = view.getOrNull(idx) ?: 0
                trip.selected = j
                onShow(j)
                throw EndConversation()
            }
            Said.NewTrip -> { options = emptyList(); destination = null; say(vs.askWhere) }
            else -> say(vs.notUnderstood)
        }
    }

    /** Apply preference / leave time from the sentence; returns what to say about it ("Okay, least crowded first."). */
    private fun applyPrefs(u: Understood): String {
        val parts = mutableListOf<String>()
        u.preference?.let { p ->
            trip.preference = Preference.entries.first { it.wire == p }
            parts += vs.preferenceSet(p)
        }
        u.leave?.let { l ->
            trip.leaveAt = toLeaveAt(l)
            parts += okay(leaveText() ?: "")
        }
        return parts.joinToString(" ")
    }

    private fun toLeaveAt(l: LeaveSpec): LeaveAt = when (l) {
        LeaveSpec.Now -> LeaveAt.Now
        is LeaveSpec.InMinutes -> if (l.min <= 0) LeaveAt.Now else LeaveAt.InMinutes(l.min)
        is LeaveSpec.At -> {
            val now = ZonedDateTime.now(DELHI_ZONE)
            var at = now.withHour(l.hour).withMinute(l.minute).withSecond(0)
            // "at 6" said in the evening means 6 PM, not tomorrow morning.
            if (!l.tomorrow && at.isBefore(now.minusMinutes(5)) && l.hour < 12 && at.plusHours(12).isAfter(now)) at = at.plusHours(12)
            when {
                l.tomorrow || at.isBefore(now.minusMinutes(5)) -> LeaveAt.Tomorrow(l.hour, l.minute)
                else -> Duration.between(now, at).toMinutes().toInt().let { if (it <= 2) LeaveAt.Now else LeaveAt.InMinutes(it) }
            }
        }
    }

    private fun leaveText(): String? = when (val l = trip.leaveAt) {
        LeaveAt.Now -> null
        is LeaveAt.InMinutes -> if (l.min % 60 == 0) vs.leavingIn(l.min)
            else vs.leaving(vs.time(ZonedDateTime.now(DELHI_ZONE).plusMinutes(l.min.toLong()).let { "%02d:%02d".format(it.hour, it.minute) }))
        is LeaveAt.Tomorrow -> vs.leaving((if (vs.languageTag.startsWith("hi")) "कल " else "tomorrow ") + vs.time("%02d:%02d".format(l.hour, l.minute)))
    }

    private fun placeNames(heard: List<String>, pick: (Understood) -> String?): List<String> =
        heard.mapNotNull { pick(Understand.parse(it)) }.distinct().take(3)

    private suspend fun here(): PickedPlace? {
        status = VoiceStatus.THINKING
        val s = step(vs.stepLocating)
        val loc = runCatching { DeviceLocation.current(ctx) }.getOrNull()
        done(s, loc?.let { vs.stepLocated(it.accuracy.roundToInt()) } ?: vs.stepNoLocation)
        status = VoiceStatus.IDLE
        if (BuildConfig.DEBUG && loc != null) Log.d(TAG, "location accuracy ${loc.accuracy} m from ${loc.provider}")
        if (loc != null && loc.hasAccuracy() && loc.accuracy > 400) say(vs.approxLocation)
        return loc?.let { PickedPlace.here(vs.currentLocation, it.latitude, it.longitude) }
    }

    private suspend fun candidates(name: String): List<Candidate> = coroutineScope {
        val q = Translit.toLatin(name).trim()
        if (q.length < 2) return@coroutineScope emptyList()
        val places = async { runCatching { Api.searchPlaces(q).results }.getOrDefault(emptyList()) }
        val stops = runCatching { Api.searchStops(q).results }.getOrDefault(emptyList())
        val p = places.await()
        val stopC = stops.map { Candidate(it.name, PickedPlace.stop(it), isStop = true) }
        val placeC = p.map { h ->
            val area = h.area.split(",").firstOrNull()?.trim()?.takeIf { it.isNotEmpty() && !it.equals(h.name, true) }
            Candidate(listOfNotNull(h.name, area).joinToString(", "), PickedPlace.place(h), isStop = false)
        }
        val stopsFirst = p.isEmpty() || stops.any { it.name.contains(q, ignoreCase = true) }
        (if (stopsFirst) stopC + placeC else placeC + stopC).take(4)
    }

    /** First of the recogniser's readings that finds a place; the rest of its matches become "wrong place" alternatives. */
    private suspend fun resolveFirst(names: List<String>, keepAlternatives: Boolean = false): Candidate? {
        if (names.isEmpty()) { say(vs.notUnderstoodNoTrip); return null }
        status = VoiceStatus.THINKING
        for (n in names) {
            val s = step(vs.stepFinding(n))
            val c = candidates(n)
            done(s, c.firstOrNull()?.let { vs.stepFound(n, it.spoken, it.isStop) } ?: vs.stepNotFound(n))
            if (c.isNotEmpty()) {
                if (BuildConfig.DEBUG) Log.d(TAG, "resolve '$n' -> ${c.map { it.spoken }}")
                status = VoiceStatus.IDLE
                if (keepAlternatives) alternatives = c.drop(1)
                return c[0]
            }
        }
        status = VoiceStatus.IDLE
        say(vs.notFound(names.first()))
        return null
    }

    private suspend fun plan(from: PickedPlace, to: Candidate) {
        trip.from = from
        trip.to = to.place
        destination = to
        trip.search(600)
        if (BuildConfig.DEBUG) Log.d(TAG, "plan ${from.label} (~${from.lat?.let { "%.3f".format(it) }},${from.lon?.let { "%.3f".format(it) }}) -> ${to.spoken}")
        say(vs.planning(if (from.isCoord && from.label == vs.currentLocation) null else from.label, to.spoken, leaveText()))
        search(alreadyStarted = true)
    }

    private suspend fun awaitResult(): Load<*> {
        status = VoiceStatus.THINKING
        return snapshotFlow { trip.result }.first { it is Load.Ok || it is Load.Failed }.also { status = VoiceStatus.IDLE }
    }

    private fun noStop(r: Load<*>) = r is Load.Failed && (r.error as? ApiException)?.reason == "no_stop_nearby"

    private suspend fun search(alreadyStarted: Boolean = false) {
        if (!alreadyStarted) trip.search(600)
        val when0 = trip.leaveAt.resolve().let { "%02d:%02d".format(it.hour, it.minute) }
        var s = step(vs.stepTimetable(vs.time(when0)))
        var r = awaitResult()
        var widened = false
        if (noStop(r)) {
            done(s, vs.stepWiden)
            trip.search(1500)
            s = step(vs.stepTimetable(vs.time(when0)))
            r = awaitResult()
            widened = true
        }
        when {
            noStop(r) -> {
                options = emptyList()
                val side = ((r as Load.Failed).error as ApiException).side
                val place = if (side == "origin") trip.from else trip.to
                val fromMe = side == "origin" && place?.isCoord == true && place.label == vs.currentLocation
                done(s, vs.stepNoStop(place?.label ?: "?"))
                if (fromMe) { pendingTo = destination; say(vs.noStopNearYou) }
                else say(vs.noStopAtAll(place?.label ?: ""))
            }
            r is Load.Failed -> {
                val e = r.error
                options = emptyList()
                done(s, vs.stepFailed)
                say(vs.searchFailed(if (e is ApiException && e.status == 0) "" else e.message.orEmpty()))
            }
            r is Load.Ok -> {
                val res = r.value as app.delhiraahi.data.JourneyResult
                options = res.journeys
                view = options.indices.toList()
                walkOption = res.walk_option
                done(s, vs.stepRanked(res.candidates_considered, options.size))
                idx = 0
                if (options.isEmpty()) {
                    val w = walkOption?.let { " " + vs.walkOnly(it.minutes.roundToInt(), it.distance_m.roundToInt(), it.basis != "osm_road") }.orEmpty()
                    say((if (widened) vs.widened + " " else "") + vs.noTrips + w)
                    return
                }
                val intro = (if (widened) vs.widened + " " else "") +
                    vs.summary(options.count { it.transfers == 0 }, options.count { it.transfers > 0 }, walkOption?.minutes?.roundToInt())
                val hint = if (!hintGiven && options.size > 1) " " + vs.hint else ""
                hintGiven = true
                onResults()
                say(intro + " " + describe(0) + hint)
            }
        }
    }

    private fun spokenRoute(r: String) = routeName(r).replace(Regex("\\(.*?\\)"), "").trim().trimStart('0').ifEmpty { r }

    private fun describe(i: Int): String {
        val j = options.getOrNull(view.getOrNull(i) ?: return "") ?: return ""
        trip.selected = view[i]          // the option being read out is the one highlighted on screen
        if (j.legs.isEmpty()) return ""
        val parts = mutableListOf(vs.option(i + 1, view.size))
        val walkO = j.walk.origin_min.roundToInt()
        val first = j.legs[0]
        parts += if (walkO >= 1) vs.walkThenBus(walkO, first.board_stop, spokenRoute(first.route), vs.time(first.departure))
        else vs.busFrom(spokenRoute(first.route), vs.time(first.departure), first.board_stop)
        j.legs.drop(1).forEachIndexed { k, leg ->
            val c = j.connections.getOrNull(k)
            parts += vs.change(c?.alight ?: j.legs[k].alight_stop, leg.board_stop, c?.walk_min?.roundToInt() ?: 0,
                spokenRoute(leg.route), vs.time(leg.departure))
        }
        parts += vs.getOff(j.legs.last().alight_stop, j.walk.destination_min.roundToInt())
        parts += vs.arrive(vs.time(j.arrival), j.duration_minutes.roundToInt())
        parts += vs.crowd(j.crowd_level)
        j.fare?.let { if (it.total_digital_min > 0) parts += vs.fare(it.total_digital_min) }
        return parts.filter { it.isNotBlank() }.joinToString(" ")
    }

    companion object {
        /** The example sentence is spoken only the first time the assistant opens. */
        private var tipGiven = false
    }
}
