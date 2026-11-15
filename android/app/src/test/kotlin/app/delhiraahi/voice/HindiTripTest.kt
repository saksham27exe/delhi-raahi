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
