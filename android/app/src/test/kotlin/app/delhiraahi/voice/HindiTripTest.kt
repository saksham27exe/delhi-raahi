package app.delhiraahi.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/** Sentences the tablet's recogniser actually produced, plus close variants. */
class HindiTripTest {
    private fun p(s: String) = Understand.parse(s)

    @Test fun requestWordsAreNotPartOfThePlace() {
        p("Mujhe Saket se Akshardham jaane ka rasta batao").let { assertEquals("saket", it.from); assertEquals("akshardham", it.to) }
        assertEquals("karol bagh", p("Karol Bagh kaise jaun").to)
        assertEquals("india gate", p("India Gate ke liye bus dikhao").to)
        assertEquals("akshardham", p("Akshardham jana hai").to)
        p("Saket se Akshardham").let { assertEquals("saket", it.from); assertEquals("akshardham", it.to) }
        assertEquals("karol bagh", p("Karol Bagh").to)
    }
}

/** Follow-ups said after results are shown: these are commands, not new destinations. */
class FollowUpTest {
    private fun cmd(s: String) = Understand.parse(s).command

    @Test fun showMoreMeansNext() {
        listOf("show more", "so more", "more", "aur dikhao", "aur koi bus", "next one").forEach { assertEquals(it, Said.Next, cmd(it)) }
    }

    @Test fun placesWithMoreStayPlaces() {
        assertEquals(null, cmd("Kakrola More"))
        assertEquals("kakrola more", Understand.parse("Kakrola More").to)
    }

    @Test fun loneMisheardWordIsWeakPlaceMatch() {
        // "so more" against a shop called "More": not a clear enough match to replace the trip.
        org.junit.Assert.assertTrue(PlaceMatch.score("so more", "More, Smt. Ram Pyari Bhalla Marg") < PlaceMatch.SURE)
        org.junit.Assert.assertTrue(PlaceMatch.score("karol bagh", "Karol Bagh Terminal") >= PlaceMatch.SURE)
    }
}
