package app.delhiraahi.voice

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Test
import java.io.File

/**
 * Scores the rule-based parser on the shared voice test set (backend/tests/data/voice_phrases.jsonl),
 * the same file the LLM is scored on, and writes each reading to build/voice_rules.jsonl so the
 * backend script can score "rules first, LLM as fallback". Reports; never fails the build.
 */
class RulesEvalTest {
    private val data = File("../../backend/tests/data/voice_phrases.jsonl")

    private fun cmdName(c: Said): Pair<String?, Int?> = when (c) {
        Said.Next -> "next" to null; Said.Previous -> "previous" to null; Said.Repeat -> "repeat" to null
        Said.Show -> "show" to null; Said.Stop -> "stop" to null; Said.Help -> "help" to null
        Said.Yes -> "yes" to null; Said.No -> "no" to null; Said.DirectOnly -> "direct_only" to null
        Said.WithChange -> "with_change" to null; Said.WalkOnly -> "walk_only" to null; Said.Alert -> "alert" to null
        Said.Guide -> "guide" to null; Said.NewTrip -> "new_trip" to null; is Said.Number -> "number" to c.n
        else -> null to null
    }

    /** The rules' reading in the same fields as the LLM. */
    fun reading(text: String): JsonObject {
        val u = Understand.parse(text)
        val prefFromCmd = when (u.command) { Said.Fastest -> "fastest"; Said.LeastCrowded -> "least_crowded"; Said.Balanced -> "balanced"; else -> null }
        val (cmd, n) = u.command?.let { cmdName(it) } ?: (null to null)
        val pref = u.preference ?: prefFromCmd
        val lat = { s: String? -> s?.let { Translit.toLatin(it).trim().lowercase() } }
        val intent = when {
            Understand.offTopic(text) -> "off_topic"
            cmd != null || u.wrongPlace -> "command"
            u.to != null || u.from != null || u.fromHere -> "trip"
            pref != null || u.leave != null -> "settings"
            else -> "unclear"
        }
        val off = intent == "off_topic"         // the app replies "bus trips only" and ignores the rest
        val leave = u.leave.takeIf { !off }
        return buildJsonObject {
            put("intent", intent)
            put("from", if (off) null else lat(u.from)); put("to", if (off) null else lat(u.to)); put("from_here", u.fromHere && !off)
            put("command", if (u.wrongPlace && cmd == null) "no" else cmd); put("number", n)
            put("preference", if (off) null else pref)
            put("leave_in_min", (leave as? LeaveSpec.InMinutes)?.min)
            put("leave_at", (leave as? LeaveSpec.At)?.let { "%02d:%02d".format(it.hour, it.minute) })
            put("tomorrow", (leave as? LeaveSpec.At)?.tomorrow ?: false)
        }
    }

    @Test fun scoreRules() {
        if (!data.exists()) { println("voice test set not found: ${data.absolutePath}"); return }
        val fields = listOf("intent", "from", "to", "from_here", "command", "number", "preference", "leave_in_min", "leave_at", "tomorrow")
        val out = StringBuilder()
        var ok = 0; var n = 0
        val misses = mutableListOf<String>()
        data.readLines().filter { it.isNotBlank() }.forEach { line ->
            val row = Json.parseToJsonElement(line).jsonObject
            val text = row["text"]!!.jsonPrimitive.content
            val got = reading(text)
            fun want(k: String): Any? = row[k]?.jsonPrimitive?.let { it.booleanOrNull ?: it.intOrNull ?: it.contentOrNull }
                ?: (if (k == "from_here" || k == "tomorrow") false else null)
            fun have(k: String): Any? = (got[k] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.intOrNull ?: it.contentOrNull }
                ?: (if (k == "from_here" || k == "tomorrow") false else null)
            val d = fields.filter { want(it) != have(it) }.map { "$it: want ${want(it)} got ${have(it)}" }
            n++; if (d.isEmpty()) ok++ else misses += "  x $text\n      ${d.joinToString("; ")}"
            out.append(buildJsonObject { put("text", text); put("rules", got) }).append('\n')
        }
        File("build").mkdirs()
        File("build/voice_rules.jsonl").writeText(out.toString())
        println("RULES accuracy $ok/$n = ${100 * ok / n}%\n" + misses.joinToString("\n"))
    }
}
