package app.delhiraahi.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnderstandTest {
    private fun p(s: String) = Understand.parse(s)

    @Test fun wholeSentence() {
        val u = p("least crowded bus from Saket to AIIMS at 6 pm")
        assertEquals("saket", u.from); assertEquals("aiims", u.to)
        assertEquals("least_crowded", u.preference); assertEquals(LeaveSpec.At(18, 0), u.leave)
    }

    @Test fun relativeAndTomorrow() {
        assertEquals(LeaveSpec.InMinutes(30), p("to India Gate in 30 minutes").leave)
        assertEquals("india gate", p("to India Gate in 30 minutes").to)
        assertEquals(LeaveSpec.InMinutes(60), p("in an hour").leave)
        assertEquals(LeaveSpec.InMinutes(30), p("half an hour later to Saket").leave)
        val k = p("कल सुबह एम्स जाना है")
        assertEquals(LeaveSpec.At(8, 30, tomorrow = true), k.leave); assertEquals("एम्स", k.to)
        val s = p("शाम 6 बजे कश्मीरी गेट से लाजपत नगर")
        assertEquals(LeaveSpec.At(18, 0), s.leave); assertEquals("कश्मीरी गेट", s.from); assertEquals("लाजपत नगर", s.to)
        assertEquals(LeaveSpec.At(9, 15), p("at 9:15 to Okhla").leave)
    }

    @Test fun hereAndPreferenceOnly() {
        val u = p("from here to Connaught Place quickly")
        assertTrue(u.fromHere); assertEquals(null, u.from); assertEquals("connaught place", u.to); assertEquals("fastest", u.preference)
        val l = p("least crowded")
        assertEquals("least_crowded", l.preference); assertEquals(null, l.to); assertEquals(null, l.command)
        assertEquals("least_crowded", p("kam bheed wali").preference)
    }

    @Test fun commandsAndWrongPlace() {
        assertEquals(Said.Next, p("next").command)
        assertEquals(Said.Show, p("show me").command)
        assertEquals(Said.Number(2), p("two").command)
        assertTrue(p("wrong place").wrongPlace)
        assertTrue(p("गलत जगह").wrongPlace)
        assertEquals(Said.Stop, p("stop").command)
        assertEquals("noida sector 18", p("Noida sector 18").to)
        assertEquals("saket", p("from Saket").from)
    }

    @Test fun bestOfGuesses() {
        val u = Understand.best(listOf("next stop", "to next stop", "go to nehru place"))
        assertEquals("next stop", u.to)
    }

    @Test fun garbledTo() {
        val a = p("bus from Kalka G2 Saket")
        assertEquals("kalka ji", a.from); assertEquals("saket", a.to)
        val b = p("from Kalkaji 2 Saket")
        assertEquals("kalkaji", b.from); assertEquals("saket", b.to)
        val c = p("bus from kalkaji too saket")
        assertEquals("kalkaji", c.from); assertEquals("saket", c.to)
        assertEquals("kalka ji", p("bus from Kalka ji to Saket").from)
        assertEquals(LeaveSpec.At(14, 0), p("from Kalkaji to Saket at 2 pm").leave)
    }

    @Test fun tripKinds() {
        assertEquals(Said.DirectOnly, p("direct bus").command)
        assertEquals(Said.DirectOnly, p("सीधी बस").command)
        assertEquals(Said.WithChange, p("connecting bus").command)
        assertEquals(Said.WalkOnly, p("walk").command)
        assertEquals(Said.WalkOnly, p("पैदल").command)
        assertEquals("india gate", p("walk to India Gate").to)
    }

    // QA 16 Nov: the demo-video sentence, and numbers too big for an Int never crash the assistant.
    @Test fun demoSentenceAndHugeNumbers() {
        val d = p("कश्मीरी गेट से आनंद विहार कम भीड़ वाली बस")
        assertEquals("कश्मीरी गेट", d.from); assertEquals("आनंद विहार", d.to); assertEquals("least_crowded", d.preference)
        assertEquals(LeaveSpec.InMinutes(600), p("to Saket in 99999999999 minutes").leave)
        p("at 99999999999 to Saket")
    }
}
