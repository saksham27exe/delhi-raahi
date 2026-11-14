package app.delhiraahi.voice

import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceMatchTest {
    private fun ok(said: String, name: String) = assertTrue("$said ~ $name", PlaceMatch.score(said, name) >= PlaceMatch.ACCEPT)
    private fun no(said: String, name: String) = assertTrue("$said !~ $name", PlaceMatch.score(said, name) < PlaceMatch.ACCEPT)

    @Test fun realPlacesMatch() {
        ok("saket", "Saket A block(Malviya Nagar Metro Station)")
        ok("kashmere gate", "Kashmere Gate ISBT")
        ok("kashmir gate", "Kashmere Gate ISBT")              // how the recogniser often spells it
        ok("rajiv chowk metro", "Rajiv Chowk Metro Station")
        ok("aims", "AIIMS")
        ok("aiims", "All India Institute of Medical Sciences")
        ok("india gate", "India Gate")
        ok("connaught place", "Connaught Place, New Delhi")
        ok("nehru place", "Nehru Place Terminal")
        ok("lajpat nagar", "Lajpat Nagar")
    }

    @Test fun nonsenseDoesNotMatch() {
        no("generate a python function", "Jagatpuri A Block")
        no("generate a python function", "East krishna Nagar / Krishna Nagar A Block")
        no("write a python function", "Saket A block(Malviya Nagar Metro Station)")
        no("a", "Jagatpuri A Block")
        no("", "India Gate")
    }
}

class OffTopicTest {
    @Test fun offTopicQuestions() {
        listOf("write a python function", "generate a Python function", "what is the weather", "tell me a joke",
            "who is the prime minister", "play a song", "python function likho").forEach {
            assertTrue(it, Understand.offTopic(it))
        }
    }

    @Test fun tripsAreNotOffTopic() {
        listOf("how do I get to India Gate", "what is the fastest way to Saket", "Saket", "from Saket to AIIMS",
            "next", "least crowded bus to AIIMS at 6 pm", "India Gate jana hai", "where is my bus").forEach {
            assertTrue(it, !Understand.offTopic(it))
        }
    }
}
