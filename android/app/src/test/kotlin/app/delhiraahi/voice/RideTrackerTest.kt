package app.delhiraahi.voice

import app.delhiraahi.data.Leg
import app.delhiraahi.data.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RideTrackerTest {
    // Five stops 500 m apart going north, 2 minutes apart from 10:00.
    private val lat0 = 28.55
    private val lon0 = 77.25
    private fun latAt(m: Double) = lat0 + m / 110_540.0
    private val names = listOf("A", "B", "C", "D", "E")
    private val stops = names.mapIndexed { i, n -> RideStop(n, latAt(500.0 * i), lon0, 600f + 2 * i) }

    private fun ride(t: RideTracker, from: Int, to: Int) =
        (from..to step 25).flatMap { t.update(latAt(it.toDouble()), lon0) }

    @Test fun announcesTwoOneArrive() {
        val t = RideTracker(stops, "433ADOWN", 8)
        val said = t.start() + ride(t, 0, 2000)
        assertTrue(said[0] is RideCue.Boarded)
        assertEquals(listOf("Boarded", "TwoLeft", "NextIsYours", "Arrive"), said.map { it::class.simpleName })
        assertEquals("D", (said[1] as RideCue.TwoLeft).next)
        assertEquals("E", (said[3] as RideCue.Arrive).stop)
        assertEquals(0, t.left)
    }

    @Test fun badFixIgnoredAndNoRepeat() {
        val t = RideTracker(stops, "433", 8)
        ride(t, 0, 1100)
        assertTrue(t.update(latAt(300.0), lon0 + 0.02).isEmpty())   // 2 km off the line
        assertTrue(t.update(latAt(200.0), lon0).isEmpty())          // going "backwards" doesn't re-announce
        assertEquals(2, t.passed)
    }

    @Test fun clockFallback() {
        val t = RideTracker(stops, "433", 8)
        assertTrue(t.byClock(603f).isEmpty())                       // 10:03: only B passed, 3 to go
        assertTrue(t.byClock(604f).single() is RideCue.TwoLeft)     // 10:04: at C, 2 to go
        val c = t.byClock(608f).single() as RideCue.Arrive
        assertTrue(c.byClock)
    }

    @Test fun jumpStraightToArrive() {
        val t = RideTracker(stops, "433", 8)
        val c = t.update(latAt(1995.0), lon0)
        assertEquals(listOf("Arrive"), c.map { it::class.simpleName })
    }

    @Test fun fromLeg() {
        val segs = (0 until 4).map { i ->
            Segment("s$i", names[i], names[i + 1], depart = "10:0${2 * i}", minutes = 2.0,
                lat = latAt(500.0 * i), lon = lon0, to_lat = latAt(500.0 * (i + 1)), to_lon = lon0)
        }
        val leg = Leg("r", "0405(NS)DOWN", board_stop_id = "a", board_stop = "A", alight_stop_id = "e", alight_stop = "E",
            departure = "10:00", arrival = "10:08", in_vehicle_minutes = 8.0, stops = 4, segments = segs)
        val t = RideTracker.of(leg)
        assertEquals(names, t.stops.map { it.name })
        assertEquals(608f, t.stops.last().time)
        assertEquals("On bus 405 to E: 4 stops, about 8 minutes. I'll tell you when to get off.", RideEn.say(t.start().single()))
    }
}
