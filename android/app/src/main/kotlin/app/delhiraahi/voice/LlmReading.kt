package app.delhiraahi.voice

import kotlinx.serialization.Serializable

/**
 * What the backend's local LLM read from a sentence the rules couldn't (POST /voice/understand).
 * Fields only, never free text.
 */
@Serializable
data class LlmReading(
    val intent: String = "unclear",
    val from: String? = null,
    val to: String? = null,
    val from_here: Boolean = false,
    val command: String? = null,
    val number: Int? = null,
    val preference: String? = null,
    val leave_in_min: Int? = null,
    val leave_at: String? = null,
    val tomorrow: Boolean = false,
    val ms: Int? = null,
) {
    /**
     * The reading as a plain sentence the rules understand exactly ("from lajpat nagar to karol bagh fastest
     * in 30 minutes"). The conversation then carries on as if the traveller had said that, so the LLM
     * adds no new code paths: places still go through the stop search and match check.
     * Null for off_topic / unclear, or when nothing usable came back.
     */
    fun sentence(): String? {
        val time = when {
            leave_in_min != null && leave_in_min > 0 -> "in $leave_in_min minutes"
            leave_at != null && Regex("""\d{2}:\d{2}""").matches(leave_at) -> (if (tomorrow) "tomorrow " else "") + "at $leave_at"
            tomorrow -> "tomorrow"
            else -> null
        }
        val pref = when (preference) { "fastest" -> "fastest"; "least_crowded" -> "least crowded"; "balanced" -> "balanced"; else -> null }
        val f = from?.trim()?.takeIf { it.isNotEmpty() }
        val t = to?.trim()?.takeIf { it.isNotEmpty() }
        return when (intent) {
            "trip" -> {
                val trip = when {
                    t != null && from_here -> "from here to $t"
                    t != null && f != null -> "from $f to $t"
                    t != null -> "to $t"
                    f != null -> "from $f"
                    else -> return null
                }
                listOfNotNull(trip, pref, time).joinToString(" ")
            }
            "command" -> when (command) {
                "next" -> "next"; "previous" -> "previous"; "repeat" -> "repeat"; "show" -> "show"; "stop" -> "stop"
                "help" -> "help"; "yes" -> "yes"; "no" -> "no"; "direct_only" -> "direct only"; "with_change" -> "with change"
                "walk_only" -> "walk"; "alert" -> "alert me"; "guide" -> "guide"; "new_trip" -> "new trip"
                "number" -> number?.takeIf { it in 1..9 }?.let { "number $it" }
                else -> null
            }
            "settings" -> listOfNotNull(pref, time).joinToString(" ").ifEmpty { null }
            else -> null
        }
    }
}
