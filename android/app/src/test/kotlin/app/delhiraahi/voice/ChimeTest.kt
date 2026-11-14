package app.delhiraahi.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ChimeTest {
    @Test fun softIsShortAndQuiet() {
        val s = Chime.samples(Chime.Kind.SOFT, 22_050)
        val sec = s.size / 22_050.0
        assertTrue("length $sec", sec in 0.9..1.3)
        val peak = s.maxOf { abs(it.toInt()) }
        assertTrue("peak $peak", peak in 8_000..12_000)        // ~0.32 of full scale: soft, never clipped
    }

    @Test fun startsAndEndsSilent() {
        val s = Chime.samples(Chime.Kind.ARRIVE, 22_050)
        assertEquals(0, s.first().toInt())
        assertTrue(abs(s.last().toInt()) < 50)
    }

    @Test fun arriveIsLongerThanSoft() {
        assertTrue(Chime.samples(Chime.Kind.ARRIVE).size > Chime.samples(Chime.Kind.SOFT).size)
    }

    @Test fun listenDingIsQuick() {
        val sec = Chime.samples(Chime.Kind.LISTEN, 22_050).size / 22_050.0
        assertTrue("length $sec", sec < 0.45)          // the mic opens right after it
    }
}
