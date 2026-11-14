package app.delhiraahi.voice

import app.delhiraahi.voice.Confidence.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfidenceTest {
    @Test fun recogniserScoreIsUsedWhenGiven() {
        assertEquals(0.82f, Confidence.score(listOf("to aiims"), floatArrayOf(0.82f, 0.1f)), 1e-4f)
    }

    @Test fun noScoreFallsBackToAgreement() {
        // 0 means "no score" on many phones: then the other guesses decide.
        val agree = Confidence.score(listOf("from saket to aiims", "from saket to aiims please"), floatArrayOf(0f, 0f))
        val differ = Confidence.score(listOf("from saket to aiims", "from sake to ames"), null)
        assertTrue("$agree > $differ", agree > differ)
        assertTrue(agree >= Confidence.SURE)
        assertEquals(0.75f, Confidence.score(listOf("next"), null), 1e-4f)
    }

    @Test fun verdicts() {
        assertEquals(Verdict.GO, Confidence.verdict(0.9f, hasPlace = true, hasCommand = false))
        assertEquals(Verdict.CONFIRM, Confidence.verdict(0.55f, hasPlace = true, hasCommand = false))
        assertEquals(Verdict.ASK_AGAIN, Confidence.verdict(0.2f, hasPlace = true, hasCommand = false))
        assertEquals(Verdict.GO, Confidence.verdict(0.55f, hasPlace = false, hasCommand = true))      // "next": no nagging
        assertEquals(Verdict.ASK_AGAIN, Confidence.verdict(0.2f, hasPlace = false, hasCommand = true))
        assertEquals(Verdict.GO, Confidence.verdict(0.1f, hasPlace = false, hasCommand = false))      // normal "didn't get that"
    }

    @Test fun percent() {
        assertEquals(82, Confidence.percent(0.823f)); assertEquals(100, Confidence.percent(1.3f))
    }
}
