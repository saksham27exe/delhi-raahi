package app.delhiraahi.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceParserTest {
    private fun trip(s: String) = VoiceParser.trip(s)

    @Test fun englishTrips() {
        assertEquals(Said.Trip("kashmere gate", "aiims"), trip("From Kashmere Gate to AIIMS"))
        assertEquals(Said.Trip(null, "india gate"), trip("to India Gate"))
        assertEquals(Said.Trip(null, "india gate"), trip("I want to go to India Gate please"))
        assertEquals(Said.Trip(null, "lajpat nagar"), trip("how do I get to Lajpat Nagar?"))
        assertEquals(Said.Trip(null, "saket"), trip("take me to Saket"))
        assertEquals(Said.Trip("saket", "connaught place"), trip("Saket to Connaught Place"))
        assertEquals(Said.Trip(null, "nehru place"), trip("Nehru Place"))
    }

    @Test fun hindiTrips() {
        assertEquals(Said.Trip("kashmiri gate", "aiims"), trip("kashmiri gate se aiims jana hai"))
        assertEquals(Said.Trip(null, "india gate"), trip("mujhe india gate jana hai"))
        assertEquals(Said.Trip("कश्मीरी गेट", "एम्स"), trip("कश्मीरी गेट से एम्स जाना है"))
        assertEquals(Said.Trip(null, "लाजपत नगर"), trip("मुझे लाजपत नगर जाना है"))
    }

    @Test fun commandsOnlyInShortUtterances() {
        assertEquals(Said.Yes, VoiceParser.command("Yes"))
        assertEquals(Said.Yes, VoiceParser.command("हाँ"))
        assertEquals(Said.No, VoiceParser.command("nahi"))
        assertEquals(Said.Next, VoiceParser.command("next one"))
        assertEquals(Said.Next, VoiceParser.command("अगला"))
        assertEquals(Said.LeastCrowded, VoiceParser.command("least crowded"))
        assertEquals(Said.LeastCrowded, VoiceParser.command("कम भीड़"))
        assertEquals(Said.Show, VoiceParser.command("show on screen"))
        assertEquals(Said.Number(2), VoiceParser.command("two"))
        assertEquals(Said.Number(3), VoiceParser.command("number three"))
        assertEquals(Said.Stop, VoiceParser.command("stop"))
        assertNull(VoiceParser.command("Noida"))                      // not "no"
        assertNull(VoiceParser.command("I want to go to the next bus stop near Okhla"))
        assertTrue(VoiceParser.isStop("Cancel."))
        assertTrue(!VoiceParser.isStop("Bus stop"))
    }

    @Test fun placeOnly() {
        assertEquals("saket", VoiceParser.placeOnly("From Saket"))
        assertEquals("rajiv chowk", VoiceParser.placeOnly("I'm at Rajiv Chowk"))
    }

    @Test fun transliteration() {
        assertEquals("kashmiri gate", Translit.toLatin("कश्मीरी गेट"))
        assertEquals("lajapat nagar", Translit.toLatin("लाजपत नगर"))
        assertEquals("india gate", Translit.toLatin("india gate"))
        assertEquals("aiims", Translit.toLatin("एम्स"))
        assertEquals("rajiv chowk", Translit.toLatin("राजीव चौक"))
    }
}
