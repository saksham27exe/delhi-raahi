package app.delhiraahi.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import app.delhiraahi.data.Lang

/**
 * In-app strings, switchable instantly (no activity restart). Same wording as the Expo app.
 * Only the strings used by the current build are here; later phases add more.
 */
interface Strings {
    val tabHome: String; val tabLive: String; val tabTickets: String; val tabAccount: String
    val greeting: String; val whereTo: String; val nearby: String; val locating: String
    val noLocation: String; val allowLocation: String; val noStopsNearby: String
    fun metres(m: Int): String
    val errNetwork: String; val retry: String; val loading: String
    val comingSoon: String; val liveTitle: String; val liveOn: String; val liveOff: String
    fun busesLive(n: Int): String
    val ticketsTitle: String; val ticketsEmpty: String; val ticketsDemoNote: String
    val accountTitle: String; val language: String; val appearance: String
    val system: String; val light: String; val dark: String
    val server: String; val serverHint: String; val test: String; val serverOk: String; val serverFail: String
    val about: String; val aboutText: String; val privacy: String
    val now: String; val addHome: String; val addWork: String; val insights: String; val insightsSub: String
    val switchLang: String; val otherLang: String; val locate: String
    // K2: search, results, journey
    val back: String; val swap: String; val from: String; val to: String; val currentLocation: String
    val fromPh: String; val toPh: String; val typeMore: String; val noMatch: String; fun routesServed(n: Int): String
    val chooseTrip: String; val leaveNow: String; fun leaveIn(min: Int): String; fun tomorrowAt(t: String): String
    val fastest: String; val balanced: String; val leastCrowded: String; val preference: String
    fun label(key: String): String
    val direct: String; fun changes(n: Int): String; fun choose(routes: String): String
    val noTrips: String; val noTripsHint: String; fun walkMin(m: Int): String; fun minutes(m: Int): String
    val yourTrip: String; fun board(stop: String): String; fun alight(stop: String): String
    fun ride(stops: Int, min: Int): String; fun walkTo(min: Int): String; val changeBus: String
    fun book(fare: String): String; val bookNoFare: String; val track: String; val why: String
    val crowdAlong: String; val timetable: String; val crowdNote: String; val nextBuildBooking: String
    fun crowd(cat: String): String; fun crowdShort(cat: String): String
    val searching: String; val walkStart: String; val walkEnd: String; fun departsAt(t: String): String
    val showStretches: String; val hideStretches: String; val eTicketFare: String
}

object En : Strings {
    override val tabHome = "Home"; override val tabLive = "Live"; override val tabTickets = "Tickets"; override val tabAccount = "Account"
    override val greeting = "Where are you going?"; override val whereTo = "Where to?"
    override val nearby = "Bus stops near you"; override val locating = "Finding stops near you…"
    override val noLocation = "Turn on location to see nearby stops."; override val allowLocation = "Allow location"
    override val noStopsNearby = "No bus stops within 1 km."
    override fun metres(m: Int) = "$m m"
    override val errNetwork = "Can't reach the DelhiRaahi server. Check Wi-Fi and the server address in Account."
    override val retry = "Try again"; override val loading = "Loading…"
    override val comingSoon = "Trip planning arrives in the next build."
    override val liveTitle = "Live buses"; override val liveOn = "Live feed on"; override val liveOff = "Live feed unavailable · timetable"
    override fun busesLive(n: Int) = "$n buses reporting"
    override val ticketsTitle = "My tickets"; override val ticketsEmpty = "No tickets yet"
    override val ticketsDemoNote = "Tickets here are demo only — not valid for travel, no payment."
    override val accountTitle = "Account"; override val language = "Language"; override val appearance = "Appearance"
    override val system = "System"; override val light = "Light"; override val dark = "Dark"
    override val server = "Server address"; override val serverHint = "Leave empty to use the address set at build time."
    override val test = "Test"; override val serverOk = "Connected"; override val serverFail = "Not reachable"
    override val about = "About DelhiRaahi"
    override val aboutText = "Hackathon prototype. Timetable and live data: Delhi Transport open data. Crowding: modelled from October 2026 ONDC e-tickets. Tickets are demo only."
    override val privacy = "Everything you save stays on this device."
    override val now = "Now"; override val addHome = "Add Home"; override val addWork = "Add Work"
    override val insights = "When are buses quiet?"; override val insightsSub = "Crowd patterns, busiest stretches"
    override val switchLang = "हिंदी में बदलें"; override val otherLang = "हिंदी"; override val locate = "Show my location"
    override val back = "Back"; override val swap = "Swap From and To"; override val from = "From"; override val to = "To"
    override val currentLocation = "Current location"; override val fromPh = "Pick-up stop"; override val toPh = "Where to?"
    override val typeMore = "Type at least 2 letters"; override val noMatch = "No stop found. Try another spelling."
    override fun routesServed(n: Int) = "$n bus routes"
    override val chooseTrip = "Choose a trip"; override val leaveNow = "Leave now"
    override fun leaveIn(min: Int) = if (min < 60) "In $min min" else "In ${min / 60} h"
    override fun tomorrowAt(t: String) = "Tomorrow $t"
    override val fastest = "Fastest"; override val balanced = "Balanced"; override val leastCrowded = "Least crowded"; override val preference = "Trip preference"
    override fun label(key: String) = when (key) { "fastest" -> "Fastest"; "balanced" -> "Best balance"; "least_crowded" -> "Least crowded"; else -> key }
    override val direct = "Direct"; override fun changes(n: Int) = if (n == 1) "1 change" else "$n changes"
    override fun choose(routes: String) = "Choose $routes"
    override val noTrips = "No bus trip found"; override val noTripsHint = "Try another time or a nearby stop. We search direct buses and trips with one change."
    override fun walkMin(m: Int) = "$m min walk"; override fun minutes(m: Int) = "$m min"
    override val yourTrip = "Your trip"; override fun board(stop: String) = "Board at $stop"; override fun alight(stop: String) = "Get off at $stop"
    override fun ride(stops: Int, min: Int) = "Ride $stops stops · $min min"; override fun walkTo(min: Int) = "Walk $min min"; override val changeBus = "Change bus"
    override fun book(fare: String) = "Book ticket · $fare"; override val bookNoFare = "Book ticket"; override val track = "Track bus"; override val why = "Why this trip"
    override val crowdAlong = "Crowding along the way"; override val timetable = "Times are from the timetable."
    override val crowdNote = "Expected crowding comes from e-ticket data per scheduled bus. It is a guide, not a passenger count."
    override val nextBuildBooking = "Booking and live tracking arrive in the next build."
    override fun crowd(cat: String) = when (cat) { "LOW" -> "Low crowd"; "MODERATE" -> "Moderate crowd"; "HIGH" -> "High crowd"; "VERY_HIGH" -> "Very crowded"; else -> "Crowd unknown" }
    override fun crowdShort(cat: String) = when (cat) { "LOW" -> "Low"; "MODERATE" -> "Moderate"; "HIGH" -> "High"; "VERY_HIGH" -> "Very high"; else -> "Unknown" }
    override val searching = "Finding buses…"; override val walkStart = "Walk to the stop"; override val walkEnd = "Walk to your destination"
    override fun departsAt(t: String) = "Departs $t"; override val showStretches = "Show crowding by stretch"; override val hideStretches = "Hide stretches"; override val eTicketFare = "e-ticket fare"
}

object Hi : Strings {
    override val tabHome = "होम"; override val tabLive = "लाइव"; override val tabTickets = "टिकट"; override val tabAccount = "अकाउंट"
    override val greeting = "कहाँ जाना है?"; override val whereTo = "कहाँ जाना है?"
    override val nearby = "आपके पास के बस स्टॉप"; override val locating = "पास के स्टॉप ढूँढ रहे हैं…"
    override val noLocation = "पास के स्टॉप देखने के लिए लोकेशन चालू करें।"; override val allowLocation = "लोकेशन की अनुमति दें"
    override val noStopsNearby = "1 किमी के भीतर कोई बस स्टॉप नहीं।"
    override fun metres(m: Int) = "$m मी"
    override val errNetwork = "दिल्लीराही सर्वर से संपर्क नहीं हो पा रहा। वाई-फ़ाई और अकाउंट में सर्वर पता जाँचें।"
    override val retry = "फिर कोशिश करें"; override val loading = "लोड हो रहा है…"
    override val comingSoon = "यात्रा योजना अगले बिल्ड में आएगी।"
    override val liveTitle = "लाइव बसें"; override val liveOn = "लाइव फ़ीड चालू"; override val liveOff = "लाइव फ़ीड उपलब्ध नहीं · समय-सारणी"
    override fun busesLive(n: Int) = "$n बसें लोकेशन भेज रही हैं"
    override val ticketsTitle = "मेरे टिकट"; override val ticketsEmpty = "अभी कोई टिकट नहीं"
    override val ticketsDemoNote = "यहाँ के टिकट सिर्फ़ डेमो हैं — यात्रा के लिए मान्य नहीं, कोई भुगतान नहीं।"
    override val accountTitle = "अकाउंट"; override val language = "भाषा"; override val appearance = "दिखावट"
    override val system = "सिस्टम"; override val light = "हल्की"; override val dark = "गहरी"
    override val server = "सर्वर पता"; override val serverHint = "खाली छोड़ें तो बिल्ड के समय वाला पता इस्तेमाल होगा।"
    override val test = "जाँचें"; override val serverOk = "जुड़ा हुआ"; override val serverFail = "संपर्क नहीं"
    override val about = "दिल्लीराही के बारे में"
    override val aboutText = "हैकथॉन प्रोटोटाइप। समय-सारणी और लाइव डेटा: दिल्ली परिवहन ओपन डेटा। भीड़: अक्टूबर 2026 के ONDC ई-टिकट से मॉडल किया गया। टिकट सिर्फ़ डेमो हैं।"
    override val privacy = "आप जो सहेजते हैं वह इसी डिवाइस पर रहता है।"
    override val now = "अभी"; override val addHome = "घर जोड़ें"; override val addWork = "ऑफ़िस जोड़ें"
    override val insights = "बसें कब खाली रहती हैं?"; override val insightsSub = "भीड़ के पैटर्न, सबसे व्यस्त हिस्से"
    override val switchLang = "Switch to English"; override val otherLang = "English"; override val locate = "मेरी लोकेशन दिखाएँ"
    override val back = "वापस"; override val swap = "कहाँ से और कहाँ तक बदलें"; override val from = "कहाँ से"; override val to = "कहाँ तक"
    override val currentLocation = "मौजूदा लोकेशन"; override val fromPh = "चढ़ने का स्टॉप"; override val toPh = "कहाँ जाना है?"
    override val typeMore = "कम से कम 2 अक्षर लिखें"; override val noMatch = "कोई स्टॉप नहीं मिला। दूसरी स्पेलिंग आज़माएँ।"
    override fun routesServed(n: Int) = "$n बस रूट"
    override val chooseTrip = "यात्रा चुनें"; override val leaveNow = "अभी निकलें"
    override fun leaveIn(min: Int) = if (min < 60) "$min मिनट में" else "${min / 60} घंटे में"
    override fun tomorrowAt(t: String) = "कल $t"
    override val fastest = "सबसे तेज़"; override val balanced = "संतुलित"; override val leastCrowded = "सबसे कम भीड़"; override val preference = "यात्रा की पसंद"
    override fun label(key: String) = when (key) { "fastest" -> "सबसे तेज़"; "balanced" -> "सबसे संतुलित"; "least_crowded" -> "सबसे कम भीड़"; else -> key }
    override val direct = "सीधी बस"; override fun changes(n: Int) = "$n बदलाव"
    override fun choose(routes: String) = "$routes चुनें"
    override val noTrips = "कोई बस यात्रा नहीं मिली"; override val noTripsHint = "कोई और समय या पास का स्टॉप आज़माएँ। हम सीधी बस और एक बदलाव वाली यात्राएँ खोजते हैं।"
    override fun walkMin(m: Int) = "$m मिनट पैदल"; override fun minutes(m: Int) = "$m मिनट"
    override val yourTrip = "आपकी यात्रा"; override fun board(stop: String) = "$stop से चढ़ें"; override fun alight(stop: String) = "$stop पर उतरें"
    override fun ride(stops: Int, min: Int) = "$stops स्टॉप · $min मिनट"; override fun walkTo(min: Int) = "$min मिनट पैदल"; override val changeBus = "बस बदलें"
    override fun book(fare: String) = "टिकट बुक करें · $fare"; override val bookNoFare = "टिकट बुक करें"; override val track = "बस ट्रैक करें"; override val why = "यह यात्रा क्यों"
    override val crowdAlong = "रास्ते में भीड़"; override val timetable = "समय, समय-सारणी से है।"
    override val crowdNote = "अनुमानित भीड़ ई-टिकट डेटा से हर निर्धारित बस के हिसाब से है। यह एक संकेत है, यात्रियों की गिनती नहीं।"
    override val nextBuildBooking = "बुकिंग और लाइव ट्रैकिंग अगले बिल्ड में आएँगी।"
    override fun crowd(cat: String) = when (cat) { "LOW" -> "कम भीड़"; "MODERATE" -> "मध्यम भीड़"; "HIGH" -> "ज़्यादा भीड़"; "VERY_HIGH" -> "बहुत ज़्यादा भीड़"; else -> "भीड़ अज्ञात" }
    override fun crowdShort(cat: String) = when (cat) { "LOW" -> "कम"; "MODERATE" -> "मध्यम"; "HIGH" -> "ज़्यादा"; "VERY_HIGH" -> "बहुत ज़्यादा"; else -> "अज्ञात" }
    override val searching = "बसें खोज रहे हैं…"; override val walkStart = "स्टॉप तक पैदल"; override val walkEnd = "मंज़िल तक पैदल"
    override fun departsAt(t: String) = "$t पर रवाना"; override val showStretches = "हिस्सों के हिसाब से भीड़ देखें"; override val hideStretches = "हिस्से छिपाएँ"; override val eTicketFare = "ई-टिकट किराया"
}

fun stringsFor(lang: Lang): Strings = if (lang == Lang.HI) Hi else En

val LocalStrings = staticCompositionLocalOf<Strings> { En }
