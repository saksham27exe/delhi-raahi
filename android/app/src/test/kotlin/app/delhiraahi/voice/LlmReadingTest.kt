package app.delhiraahi.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The LLM's fields become a sentence; the rules must read that sentence back exactly. */
class LlmReadingTest {
    private fun back(r: LlmReading) = Understand.parse(r.sentence()!!)

    @Test fun tripsRoundTrip() {
        back(LlmReading("trip", from = "lajpat nagar", to = "karol bagh")).let { assertEquals("lajpat nagar", it.from); assertEquals("karol bagh", it.to) }
        back(LlmReading("trip", to = "rohini sector 22", from_here = true)).let { assertTrue(it.fromHere); assertEquals("rohini sector 22", it.to) }
        back(LlmReading("trip", from = "munirka", to = "dwarka sector 21", preference = "least_crowded", leave_in_min = 30)).let {
            assertEquals("munirka", it.from); assertEquals("dwarka sector 21", it.to)
            assertEquals("least_crowded", it.preference); assertEquals(LeaveSpec.InMinutes(30), it.leave)
        }
        back(LlmReading("trip", to = "connaught place", leave_at = "18:00", tomorrow = true)).let {
            assertEquals("connaught place", it.to); assertEquals(LeaveSpec.At(18, 0, true), it.leave)
        }
        assertEquals("saket", back(LlmReading("trip", from = "saket")).from)
    }

    @Test fun commandsRoundTrip() {
        mapOf("next" to Said.Next, "previous" to Said.Previous, "repeat" to Said.Repeat, "show" to Said.Show, "help" to Said.Help,
            "yes" to Said.Yes, "no" to Said.No, "direct_only" to Said.DirectOnly, "with_change" to Said.WithChange,
            "walk_only" to Said.WalkOnly, "alert" to Said.Alert, "guide" to Said.Guide, "new_trip" to Said.NewTrip, "stop" to Said.Stop,
        ).forEach { (c, said) -> assertEquals(c, said, back(LlmReading("command", command = c)).command) }
        assertEquals(Said.Number(2), back(LlmReading("command", command = "number", number = 2)).command)
    }

    @Test fun settingsRoundTrip() {
        assertEquals("fastest", back(LlmReading("settings", preference = "fastest")).preference)
        assertEquals(LeaveSpec.InMinutes(45), back(LlmReading("settings", leave_in_min = 45)).leave)
    }

    @Test fun nothingUsable() {
        assertNull(LlmReading("off_topic").sentence())
        assertNull(LlmReading("unclear").sentence())
        assertNull(LlmReading("trip").sentence())
        assertNull(LlmReading("command", command = "dance").sentence())
    }
}
