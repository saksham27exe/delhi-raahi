package app.delhiraahi.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the offline model heard for "Hey Raahi" in several voices, and for ordinary speech. */
class WakeMatchTest {
    @Test fun wakePhraseVariants() {
        listOf("hey ra hee", "hey rah hee", "[unk] hey rah hee", "rah hee", "hey ra hi", "hey rah i").forEach {
            assertTrue(it, WakeMatch.heard(it))
        }
    }

    @Test fun ordinarySpeechDoesNotWake() {
        listOf("hey how are you", "hey are you coming", "today rain here", "going to delhi tomorrow right",
            "take me to can your gate", "hey rather", "he he", "", "[unk]").forEach {
            assertFalse(it, WakeMatch.heard(it))
        }
    }

    @Test fun earlyWakeNeedsTheWholePhrase() {
        assertTrue(WakeMatch.heardFull("hey rah hee"))
        assertFalse(WakeMatch.heardFull("rah hee"))      // waits for the end of the sentence instead
        assertFalse(WakeMatch.heardFull("hey rah"))
    }

    @Test fun grammarIsJsonList() {
        val g = WakeMatch.grammar
        assertTrue(g.startsWith("[\"hey ra hee\"") && g.endsWith("\"[unk]\"]"))
    }
}
