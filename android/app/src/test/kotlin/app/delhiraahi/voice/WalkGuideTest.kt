package app.delhiraahi.voice

import app.delhiraahi.data.WalkDirections
import app.delhiraahi.data.WalkStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class WalkGuideTest {
    // 100 m east along "Main Road", left turn, 100 m north to the stop.
    private val lat0 = 28.55
    private val lon0 = 77.25
    private val dLon = 100.0 / (111_320.0 * cos(lat0 * Math.PI / 180))
    private val dLat = 100.0 / 110_540.0
    private val corner = listOf(lat0, lon0 + dLon)
    private val end = listOf(lat0 + dLat, lon0 + dLon)
    private val dir = WalkDirections(
        distance_m = 200.0,
        path = listOf(listOf(lat0, lon0), corner, end),
        steps = listOf(
            WalkStep("depart", "", "Main Road", 100.0, lat0, lon0, 90),
            WalkStep("turn", "left", "", 100.0, corner[0], corner[1], 0),
            WalkStep("arrive", "", "", 0.0, end[0], end[1], 0),
        ),
    )

    /** A point [east] m east and [north] m north of the start. */
    private fun at(east: Double, north: Double) = (lat0 + north / 110_540.0) to (lon0 + east / (111_320.0 * cos(lat0 * Math.PI / 180)))

    private fun walk(g: WalkGuide, east: Double, north: Double): List<Cue> = at(east, north).let { (la, lo) -> g.update(la, lo, 5f) }

    @Test fun fullWalk() {
        val g = WalkGuide(dir)
        val said = mutableListOf<Cue>()
        for (e in 0..100 step 5) said += walk(g, e.toDouble(), 0.0)
        for (n in 5..100 step 5) said += walk(g, 100.0, n.toDouble())
        assertTrue(said[0] is Cue.Start)
        assertEquals(1, said.count { it is Cue.Prepare })
        assertEquals(1, said.count { it is Cue.Now })
        assertEquals(1, said.count { it == Cue.Arrive })
        val prep = said.first { it is Cue.Prepare } as Cue.Prepare
        assertTrue(prep.metres in 30..70)
        assertTrue(said.indexOfFirst { it is Cue.Prepare } < said.indexOfFirst { it is Cue.Now })
        assertEquals(Cue.Arrive, said.last())
    }

    @Test fun startText() {
        val g = WalkGuide(dir)
        val c = walk(g, 0.0, 0.0).first()
        assertEquals("Head east on Main Road for 100 metres, then turn left.", GuideEn.say(c, "Kalkaji Depot"))
        assertEquals("Main Road पर पूर्व की ओर 100 मीटर चलिए, फिर बाएँ मुड़िए।", GuideHi.say(c, "Kalkaji Depot"))
    }

    @Test fun offRouteNeedsTwoFixes() {
        val g = WalkGuide(dir)
        walk(g, 20.0, 0.0)
        assertTrue(walk(g, 40.0, -80.0).none { it == Cue.OffRoute })
        assertTrue(walk(g, 42.0, -85.0).any { it == Cue.OffRoute })
        assertTrue(walk(g, 44.0, -90.0).none { it == Cue.OffRoute })   // said once
    }

    @Test fun skippedCornerStillAnnounced() {
        val g = WalkGuide(dir)
        walk(g, 0.0, 0.0)
        walk(g, 50.0, 0.0)
        val c = walk(g, 100.0, 20.0)   // GPS jumped past the corner
        assertTrue(c.any { it is Cue.Now })
    }

    @Test fun rounding() {
        assertEquals(50, WalkGuide.roundM(47.0))
        assertEquals(10, WalkGuide.roundM(3.0))
        assertEquals(250, WalkGuide.roundM(240.0))
    }
}
