package app.delhiraahi.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import app.delhiraahi.data.Lang

/**
 * In-app strings, switchable instantly (no activity restart). Same wording as the Expo app.
 * Only the strings used by the current build are here; later phases add more.
 */
interface Strings {
    val tabHome: String; val tabLive: String; val tabTickets: String; val tabAccount: String
    /** Home sentence: each line is prefix + [slot] + suffix, so Hindi can put the word after the blank. */
    val sFromPre: String; val sFromPost: String; val sToPre: String; val sToPost: String; val sWhenPre: String; val sWhenPost: String
    val yourLocation: String; val whereQ: String; val sayIt: String; val sayItSub: String; fun waitFor(min: Int): String; val recentTrips: String
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
    val filterAll: String; val filterDirect: String; val filterChange: String; val filterWalk: String
    val legendWalk: String; val legendWait: String; val legendRide: String; fun leaveAtT(time: String): String
    val walkWhole: String; fun walkDetail(min: Int, m: Int, approx: Boolean): String; fun changeDetail(stop: String, walk: Int, wait: Int): String
    val noneOfKind: String
    val noTrips: String; val noTripsHint: String; fun walkMin(m: Int): String; fun minutes(m: Int): String
    val yourTrip: String; fun board(stop: String): String; fun alight(stop: String): String
    fun ride(stops: Int, min: Int): String; fun walkTo(min: Int): String; val changeBus: String
    fun book(fare: String): String; val bookNoFare: String; val track: String; val why: String
    val crowdAlong: String; val timetable: String; val crowdNote: String; val nextBuildBooking: String
    fun crowd(cat: String): String; fun crowdShort(cat: String): String
    val searching: String; val walkStart: String; val walkEnd: String; fun departsAt(t: String): String
    val showStretches: String; val hideStretches: String; val eTicketFare: String
    // K3: booking, tickets, live
    val bookTitle: String; val passengers: String; val womenFree: String; val womenNote: String
    val busType: String; val ac: String; val nonAc: String; val totalDigital: String
    val confirmDemo: String; val confirming: String; val demoBanner: String; val close: String
    val ticketTitle: String; val ticketActive: String; val ticketExpired: String; fun validUntil(t: String): String
    fun passengersN(n: Int): String; val verify: String; fun verified(status: String): String; val notVerified: String
    val removeTicket: String; val storedOnDevice: String; val emptyHint: String; val planTrip: String
    val searchStop: String; val nearbyStops: String; val allBuses: String; fun inMin(m: Int): String; val nowArriving: String
    fun late(m: Int): String; fun early(m: Int): String; val onTime: String; fun towards(stop: String): String
    val noArrivals: String; fun basis(b: String): String; val zoomIn: String; fun busesShown(n: Int): String; val refresh: String
    // K4: insights, saved places, recent
    val insightsTitle: String; val routeQuiet: String; val routePh: String; fun quietest(q: String, b: String): String
    val hotspots: String; val hotspotsHint: String; val modelTitle: String; val routeModel: String; val segModel: String
    val pickRoute: String; val timeLabel: String; val dayPattern: String; val noData: String; fun coverage(pct: Int): String
    val fares: String; val ours: String; val baseline: String; val exactFare: String; val withinSlab: String
    val limitations: String; val limitationsEnOnly: String; val honest: String; fun chartA11y(parts: String): String
    val home: String; val work: String; val savedPlaces: String; val remove: String; val recent: String; val clearRecent: String
    fun choosePlace(slot: String): String; val fromHere: String; fun fromStop(stop: String): String; val notSet: String
    val expand: String; val collapse: String
    // K5: accounts, wallet, passes, help
    val logIn: String; val signUp: String; val createAccount: String; val mobile: String; val password: String; val yourName: String
    val showPassword: String; val hidePassword: String; val passwordRule: String; val noSmsNote: String
    val guestTitle: String; val guestSub: String; val loginToBook: String; val loginToSeeTickets: String; val loginOrSignUp: String
    val wallet: String; val demoWallet: String; val addMoney: String; fun add(amount: Int): String; val walletHistory: String
    val noHistory: String; fun txKind(kind: String): String; val topupNote: String; fun balance(x: String): String; fun payFromWallet(x: String): String
    val passes: String; val myPasses: String; val buyPass: String; val noPasses: String; val passTitle: String
    val officialPrice: String; val unverifiedPrice: String; fun checkedOn(d: String): String; fun buyFor(price: String): String
    fun holder(h: String): String; val source: String; val buying: String
    val profile: String; val womanTraveller: String; val womanTravellerSub: String; val changePassword: String
    val currentPassword: String; val newPassword: String; val save: String; val saved: String; val logout: String; val logoutSub: String
    val deleteAccount: String; val deleteWarn: String; val cancel: String; val settings: String
    val help: String; val faq: List<Pair<String, String>>; val feedback: String; val feedbackHint: String; val send: String; val thanks: String
    val busStops: String; val placesHeader: String; val osmCredit: String; fun walkToM(min: Int, m: Int): String; val approx: String
    fun noStopNear(m: Int): String; val widerSearch: String
    val privacyTitle: String; val privacyBody: String; val noScreenshots: String; val demoPass: String; val verifyPass: String
}

object En : Strings {
    override val tabHome = "Home"; override val tabLive = "Live"; override val tabTickets = "Tickets"; override val tabAccount = "Account"
    override val sFromPre = "From"; override val sFromPost = ""; override val sToPre = "to"; override val sToPost = ""
    override val sWhenPre = "leaving"; override val sWhenPost = ""
    override val yourLocation = "where I am"; override val whereQ = "where?"
    override val sayIt = "Say it instead"; override val sayItSub = "Speak the same sentence, in English or Hindi"
    override fun waitFor(min: Int) = "Wait $min min"; override val recentTrips = "Again?"
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
    override val privacy = "Saved places and recent trips stay on this device. Your account, wallet, tickets and passes are stored on the DelhiRaahi server."
    override val now = "Now"; override val addHome = "Add Home"; override val addWork = "Add Work"
    override val insights = "When are buses quiet?"; override val insightsSub = "Crowd patterns, busiest stretches"
    override val switchLang = "हिंदी में बदलें"; override val otherLang = "हिंदी"; override val locate = "Show my location"
    override val back = "Back"; override val swap = "Swap From and To"; override val from = "From"; override val to = "To"
    override val currentLocation = "Current location"; override val fromPh = "Pick-up stop"; override val toPh = "Where to?"
    override val typeMore = "Type at least 2 letters"; override val noMatch = "Nothing found. Try another spelling."
    override fun routesServed(n: Int) = "$n bus routes"
    override val chooseTrip = "Choose a trip"; override val leaveNow = "Leave now"
    override fun leaveIn(min: Int) = if (min < 60) "In $min min" else "In ${min / 60} h"
    override fun tomorrowAt(t: String) = "Tomorrow $t"
    override val fastest = "Fastest"; override val balanced = "Balanced"; override val leastCrowded = "Least crowded"; override val preference = "Trip preference"
    override fun label(key: String) = when (key) { "fastest" -> "Fastest"; "balanced" -> "Best balance"; "least_crowded" -> "Least crowded"; else -> key }
    override val direct = "Direct"; override fun changes(n: Int) = if (n == 1) "1 change" else "$n changes"
    override fun choose(routes: String) = "Choose $routes"
    override val noTrips = "No bus trip found"; override val noTripsHint = "Try another time or a nearby stop. We search direct buses and trips with up to two changes."
    override val filterAll = "All"; override val filterDirect = "Direct"; override val filterChange = "With change"; override val filterWalk = "Walk"
    override val legendWalk = "Walk"; override val legendWait = "Wait"; override val legendRide = "Bus, by crowding"
    override fun leaveAtT(time: String) = "Leave $time"
    override val walkWhole = "Walk the whole way"
    override fun walkDetail(min: Int, m: Int, approx: Boolean) = "$min min · ${if (m >= 1000) "%.1f km".format(m / 1000.0) else "$m m"}" + if (approx) " (approx.)" else " along roads"
    override fun changeDetail(stop: String, walk: Int, wait: Int) =
        "Change at $stop" + listOfNotNull(walk.takeIf { it >= 1 }?.let { "$it min walk" }, wait.takeIf { it >= 1 }?.let { "$it min wait" }).joinToString(", ", " (", ")").takeIf { walk >= 1 || wait >= 1 }.orEmpty()
    override val noneOfKind = "No trip of this kind for this search."
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
    override val bookTitle = "Book demo ticket"; override val passengers = "Passengers"; override val womenFree = "Women travelling free"
    override val womenNote = "Free on DTC and cluster buses (pink ticket / Saheli card). Check current rules."
    override val busType = "Bus type"; override val ac = "AC"; override val nonAc = "Non-AC"; override val totalDigital = "Total (digital fare)"
    override val confirmDemo = "Confirm demo booking"; override val confirming = "Issuing ticket…"
    override val demoBanner = "DEMO TICKET — not valid for travel, no payment taken"; override val close = "Close"
    override val ticketTitle = "Ticket"; override val ticketActive = "Active · demo"; override val ticketExpired = "Expired"
    override fun validUntil(t: String) = "Valid until $t"; override fun passengersN(n: Int) = if (n == 1) "1 passenger" else "$n passengers"
    override val verify = "Verify ticket"; override fun verified(status: String) = "Signature valid · " + when (status) { "active_demo" -> "active (demo)"; "expired" -> "expired"; "not_on_record" -> "not on record: do not accept"; else -> status }; override val notVerified = "Signature not valid"
    override val removeTicket = "Delete ticket"; override val storedOnDevice = "Tickets are stored only on this device."
    override val emptyHint = "Plan a trip and book a demo ticket."; override val planTrip = "Plan a trip"
    override val searchStop = "Search a stop"; override val nearbyStops = "Nearby stops"; override val allBuses = "All buses"
    override fun inMin(m: Int) = "$m min"; override val nowArriving = "Now"
    override fun late(m: Int) = "$m min late"; override fun early(m: Int) = "$m min early"; override val onTime = "On time"
    override fun towards(stop: String) = "to $stop"; override val noArrivals = "No buses in the next hour."
    override fun basis(b: String) = when (b) { "live_estimate" -> "Live"; "live_estimate_typical_running_time" -> "Live · approx."; else -> "Scheduled" }
    override val zoomIn = "Zoom in to see live buses"; override fun busesShown(n: Int) = "$n buses on map"; override val refresh = "Refresh"
    override val insightsTitle = "Insights"; override val routeQuiet = "When is my bus quiet?"; override val routePh = "Bus number, e.g. 433"
    override fun quietest(q: String, b: String) = "Quietest around $q, busiest around $b."
    override val hotspots = "Busiest stretches"; override val hotspotsHint = "Top road segments by expected crowding (well-supported estimates only)."
    override val modelTitle = "How good are these numbers?"
    override val routeModel = "Route busy-level score (macro F1, test week, higher is better)"
    override val segModel = "Segment crowding error (passengers, MAE, lower is better)"
    override val pickRoute = "Pick a route to see its day"; override val timeLabel = "Time"; override val dayPattern = "Typical pattern for today"
    override val noData = "Not enough data for this route."; override fun coverage(pct: Int) = "80% forecast interval covered $pct% of test buckets."
    override val fares = "Fare engine accuracy"; override val ours = "Our model"; override val baseline = "Baseline"
    override val exactFare = "Exact fare match"; override val withinSlab = "Within one slab"
    override val limitations = "Limitations"; override val limitationsEnOnly = ""; override val honest = "Our segment model ties a strong baseline, so we show both."
    override fun chartA11y(parts: String) = "Crowd through the day: $parts"
    override val home = "Home"; override val work = "Work"; override val savedPlaces = "Saved places"; override val remove = "Remove"
    override val recent = "Recent"; override val clearRecent = "Clear"
    override fun choosePlace(slot: String) = "Set $slot"; override val fromHere = "From your location"
    override fun fromStop(stop: String) = "From $stop"; override val notSet = "Not set"
    override val expand = "Expand panel"; override val collapse = "Collapse panel"
    override val logIn = "Log in"; override val signUp = "Sign up"; override val createAccount = "Create account"
    override val mobile = "Mobile number"; override val password = "Password"; override val yourName = "Your name"
    override val showPassword = "Show password"; override val hidePassword = "Hide password"
    override val passwordRule = "At least 8 characters, with a letter and a number."
    override val noSmsNote = "Demo: your number is not verified by SMS."
    override val guestTitle = "You are using DelhiRaahi as a guest"
    override val guestSub = "Plan trips freely. Log in to book tickets, buy passes and keep them on your account."
    override val loginToBook = "Log in to book this ticket"; override val loginToSeeTickets = "Log in to see your tickets and passes."
    override val loginOrSignUp = "Log in or sign up"
    override val wallet = "Wallet"; override val demoWallet = "DEMO wallet — no real money"; override val addMoney = "Add money"
    override fun add(amount: Int) = "Add ₹$amount"; override val walletHistory = "History"; override val noHistory = "No transactions yet."
    override fun txKind(kind: String) = when (kind) { "topup" -> "Added (demo)"; "ticket" -> "Ticket"; "pass" -> "Bus pass"; else -> kind }
    override val topupNote = "Demo top-up: no payment is taken and the balance works only in this demo."
    override fun balance(x: String) = "Balance $x"; override fun payFromWallet(x: String) = "Paid from your demo wallet (balance $x)"
    override val passes = "Bus passes"; override val myPasses = "My passes"; override val buyPass = "Buy a pass"; override val noPasses = "No passes yet."
    override val passTitle = "Bus pass"; override val officialPrice = "Official DTC fare"; override val unverifiedPrice = "Price not confirmed by DTC"
    override fun checkedOn(d: String) = "Prices checked $d"; override fun buyFor(price: String) = "Buy for $price"
    override fun holder(h: String) = "Holder $h"; override val source = "Source"; override val buying = "Buying…"
    override val profile = "Profile"; override val womanTraveller = "Woman traveller"
    override val womanTravellerSub = "Pre-fills free travel for women when booking (Delhi scheme)."
    override val changePassword = "Change password"; override val currentPassword = "Current password"; override val newPassword = "New password"
    override val save = "Save"; override val saved = "Saved"; override val logout = "Log out"; override val logoutSub = "Signs you out on all devices."
    override val deleteAccount = "Delete account"
    override val deleteWarn = "This permanently deletes your account, wallet history, tickets and passes. Enter your password to confirm."
    override val cancel = "Cancel"; override val settings = "Settings"
    override val help = "Help & feedback"
    override val faq = listOf(
        "Are these real tickets?" to "No. Tickets and passes are demos: not valid for travel and no money is taken.",
        "Where do the crowd levels come from?" to "From October 2026 ONDC e-ticket data, per scheduled bus. They are an estimate, not a head count.",
        "Why can I not take a screenshot of a ticket?" to "Tickets and passes belong to your account. Screenshots are blocked and each QR shows the holder's masked number.",
        "Do I need an account?" to "No for planning trips, live buses and insights. Yes for booking tickets and buying passes.",
    )
    override val feedback = "Send feedback"; override val feedbackHint = "Tell us what to improve"; override val send = "Send"
    override val thanks = "Thanks! Your feedback was sent."
    override val privacyTitle = "Privacy"
    override val busStops = "Bus stops"; override val placesHeader = "Places and addresses"; override val osmCredit = "Places © OpenStreetMap contributors"
    override fun walkToM(min: Int, m: Int) = "Walk $min min · $m m"; override val approx = "approx."
    override fun noStopNear(m: Int) = "No bus stop within $m m of this place."; override val widerSearch = "Search within 1.5 km"
    override val privacyBody = "We store your name, mobile number, a hashed password, and your demo wallet, tickets and passes. No location is stored on our server. When you search for a place, or plan a trip from a place or your location, the search text and the position rounded to about 10 m are sent to free OpenStreetMap services (Photon place search, FOSSGIS walking routes) without your name or number. You can delete everything from this screen."
    override val noScreenshots = "Screenshots are blocked on tickets and passes."
    override val demoPass = "DEMO PASS — not valid for travel, no real money"; override val verifyPass = "Verify pass"
}

object Hi : Strings {
    override val tabHome = "होम"; override val tabLive = "लाइव"; override val tabTickets = "टिकट"; override val tabAccount = "अकाउंट"
    override val sFromPre = ""; override val sFromPost = "से"; override val sToPre = ""; override val sToPost = "तक"
    override val sWhenPre = ""; override val sWhenPost = "निकलना है"
    override val yourLocation = "मेरी जगह"; override val whereQ = "कहाँ?"
    override val sayIt = "बोलकर बताइए"; override val sayItSub = "यही वाक्य बोलिए, हिंदी या अंग्रेज़ी में"
    override fun waitFor(min: Int) = "$min मिनट इंतज़ार"; override val recentTrips = "फिर से?"
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
    override val privacy = "सहेजी जगहें और हाल की यात्राएँ इसी डिवाइस पर रहती हैं। आपका अकाउंट, वॉलेट, टिकट और पास दिल्लीराही सर्वर पर रखे जाते हैं।"
    override val now = "अभी"; override val addHome = "घर जोड़ें"; override val addWork = "ऑफ़िस जोड़ें"
    override val insights = "बसें कब खाली रहती हैं?"; override val insightsSub = "भीड़ के पैटर्न, सबसे व्यस्त हिस्से"
    override val switchLang = "Switch to English"; override val otherLang = "English"; override val locate = "मेरी लोकेशन दिखाएँ"
    override val back = "वापस"; override val swap = "कहाँ से और कहाँ तक बदलें"; override val from = "कहाँ से"; override val to = "कहाँ तक"
    override val currentLocation = "मौजूदा लोकेशन"; override val fromPh = "चढ़ने का स्टॉप"; override val toPh = "कहाँ जाना है?"
    override val typeMore = "कम से कम 2 अक्षर लिखें"; override val noMatch = "कुछ नहीं मिला। दूसरी स्पेलिंग आज़माएँ।"
    override fun routesServed(n: Int) = "$n बस रूट"
    override val chooseTrip = "यात्रा चुनें"; override val leaveNow = "अभी निकलें"
    override fun leaveIn(min: Int) = if (min < 60) "$min मिनट में" else "${min / 60} घंटे में"
    override fun tomorrowAt(t: String) = "कल $t"
    override val fastest = "सबसे तेज़"; override val balanced = "संतुलित"; override val leastCrowded = "सबसे कम भीड़"; override val preference = "यात्रा की पसंद"
    override fun label(key: String) = when (key) { "fastest" -> "सबसे तेज़"; "balanced" -> "सबसे संतुलित"; "least_crowded" -> "सबसे कम भीड़"; else -> key }
    override val direct = "सीधी बस"; override fun changes(n: Int) = "$n बदलाव"
    override fun choose(routes: String) = "$routes चुनें"
    override val noTrips = "कोई बस यात्रा नहीं मिली"; override val noTripsHint = "कोई और समय या पास का स्टॉप आज़माएँ। हम सीधी बस और दो बदलाव तक वाली यात्राएँ खोजते हैं।"
    override val filterAll = "सभी"; override val filterDirect = "सीधी"; override val filterChange = "बदलाव वाली"; override val filterWalk = "पैदल"
    override val legendWalk = "पैदल"; override val legendWait = "इंतज़ार"; override val legendRide = "बस, भीड़ के हिसाब से"
    override fun leaveAtT(time: String) = "$time पर निकलें"
    override val walkWhole = "पूरा रास्ता पैदल"
    override fun walkDetail(min: Int, m: Int, approx: Boolean) = "$min मिनट · ${if (m >= 1000) "%.1f किमी".format(m / 1000.0) else "$m मी"}" + if (approx) " (अनुमान)" else " सड़क से"
    override fun changeDetail(stop: String, walk: Int, wait: Int) =
        "$stop पर बदलें" + listOfNotNull(walk.takeIf { it >= 1 }?.let { "$it मिनट पैदल" }, wait.takeIf { it >= 1 }?.let { "$it मिनट इंतज़ार" }).joinToString(", ", " (", ")").takeIf { walk >= 1 || wait >= 1 }.orEmpty()
    override val noneOfKind = "इस खोज में इस तरह की कोई यात्रा नहीं।"
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
    override val bookTitle = "डेमो टिकट बुक करें"; override val passengers = "यात्री"; override val womenFree = "मुफ़्त यात्रा करने वाली महिलाएँ"
    override val womenNote = "डीटीसी और क्लस्टर बसों में मुफ़्त (पिंक टिकट / सहेली कार्ड)। मौजूदा नियम देख लें।"
    override val busType = "बस का प्रकार"; override val ac = "एसी"; override val nonAc = "नॉन-एसी"; override val totalDigital = "कुल (डिजिटल किराया)"
    override val confirmDemo = "डेमो बुकिंग पक्की करें"; override val confirming = "टिकट जारी हो रहा है…"
    override val demoBanner = "डेमो टिकट — यात्रा के लिए मान्य नहीं, कोई भुगतान नहीं लिया गया"; override val close = "बंद करें"
    override val ticketTitle = "टिकट"; override val ticketActive = "सक्रिय · डेमो"; override val ticketExpired = "समाप्त"
    override fun validUntil(t: String) = "$t तक मान्य"; override fun passengersN(n: Int) = "$n यात्री"
    override val verify = "टिकट जाँचें"; override fun verified(status: String) = "हस्ताक्षर सही · " + when (status) { "active_demo" -> "चालू (डेमो)"; "expired" -> "समय खत्म"; "not_on_record" -> "रिकॉर्ड में नहीं: स्वीकार न करें"; else -> status }; override val notVerified = "हस्ताक्षर सही नहीं"
    override val removeTicket = "टिकट हटाएँ"; override val storedOnDevice = "टिकट सिर्फ़ इसी डिवाइस पर रखे जाते हैं।"
    override val emptyHint = "यात्रा खोजें और डेमो टिकट बुक करें।"; override val planTrip = "यात्रा खोजें"
    override val searchStop = "स्टॉप खोजें"; override val nearbyStops = "पास के स्टॉप"; override val allBuses = "सभी बसें"
    override fun inMin(m: Int) = "$m मिनट"; override val nowArriving = "अभी"
    override fun late(m: Int) = "$m मिनट देर"; override fun early(m: Int) = "$m मिनट जल्दी"; override val onTime = "समय पर"
    override fun towards(stop: String) = "$stop की ओर"; override val noArrivals = "अगले एक घंटे में कोई बस नहीं।"
    override fun basis(b: String) = when (b) { "live_estimate" -> "लाइव"; "live_estimate_typical_running_time" -> "लाइव · अनुमान"; else -> "समय-सारणी" }
    override val zoomIn = "लाइव बसें देखने के लिए ज़ूम करें"; override fun busesShown(n: Int) = "मैप पर $n बसें"; override val refresh = "ताज़ा करें"
    override val insightsTitle = "जानकारी"; override val routeQuiet = "मेरी बस कब खाली रहती है?"; override val routePh = "बस नंबर, जैसे 433"
    override fun quietest(q: String, b: String) = "$q के आसपास सबसे कम, $b के आसपास सबसे ज़्यादा भीड़।"
    override val hotspots = "सबसे भीड़ वाले हिस्से"; override val hotspotsHint = "अनुमानित भीड़ के हिसाब से सबसे व्यस्त सड़क-हिस्से (सिर्फ़ पक्के अनुमान)।"
    override val modelTitle = "ये आँकड़े कितने सही हैं?"
    override val routeModel = "रूट भीड़-स्तर स्कोर (macro F1, टेस्ट सप्ताह, ज़्यादा बेहतर)"
    override val segModel = "सड़क-हिस्से की भीड़ त्रुटि (यात्री, MAE, कम बेहतर)"
    override val pickRoute = "दिन का पैटर्न देखने के लिए रूट चुनें"; override val timeLabel = "समय"; override val dayPattern = "आज का आम पैटर्न"
    override val noData = "इस रूट के लिए पर्याप्त डेटा नहीं।"; override fun coverage(pct: Int) = "80% अनुमान-सीमा ने $pct% टेस्ट समय को कवर किया।"
    override val fares = "किराया गणना की सटीकता"; override val ours = "हमारा मॉडल"; override val baseline = "आधार"
    override val exactFare = "किराया बिल्कुल सही"; override val withinSlab = "एक स्लैब के भीतर"
    override val limitations = "सीमाएँ"; override val limitationsEnOnly = "(सर्वर से, अंग्रेज़ी में)"; override val honest = "हमारा सड़क-हिस्सा मॉडल एक मज़बूत आधार के बराबर है — हम दोनों दिखाते हैं।"
    override fun chartA11y(parts: String) = "दिन भर की भीड़: $parts"
    override val home = "घर"; override val work = "ऑफ़िस"; override val savedPlaces = "सहेजी जगहें"; override val remove = "हटाएँ"
    override val recent = "हाल की यात्राएँ"; override val clearRecent = "साफ़ करें"
    override fun choosePlace(slot: String) = "$slot चुनें"; override val fromHere = "आपकी लोकेशन से"
    override fun fromStop(stop: String) = "$stop से"; override val notSet = "तय नहीं"
    override val expand = "पैनल बड़ा करें"; override val collapse = "पैनल छोटा करें"
    override val logIn = "लॉग इन"; override val signUp = "साइन अप"; override val createAccount = "अकाउंट बनाएँ"
    override val mobile = "मोबाइल नंबर"; override val password = "पासवर्ड"; override val yourName = "आपका नाम"
    override val showPassword = "पासवर्ड दिखाएँ"; override val hidePassword = "पासवर्ड छिपाएँ"
    override val passwordRule = "कम से कम 8 अक्षर, जिसमें एक अक्षर और एक अंक हो।"
    override val noSmsNote = "डेमो: आपका नंबर SMS से सत्यापित नहीं होता।"
    override val guestTitle = "आप मेहमान के रूप में दिल्लीराही इस्तेमाल कर रहे हैं"
    override val guestSub = "यात्रा खुलकर प्लान करें। टिकट बुक करने, पास खरीदने और उन्हें अकाउंट में रखने के लिए लॉग इन करें।"
    override val loginToBook = "यह टिकट बुक करने के लिए लॉग इन करें"; override val loginToSeeTickets = "अपने टिकट और पास देखने के लिए लॉग इन करें।"
    override val loginOrSignUp = "लॉग इन या साइन अप"
    override val wallet = "वॉलेट"; override val demoWallet = "डेमो वॉलेट — असली पैसे नहीं"; override val addMoney = "पैसे जोड़ें"
    override fun add(amount: Int) = "₹$amount जोड़ें"; override val walletHistory = "लेन-देन"; override val noHistory = "अभी कोई लेन-देन नहीं।"
    override fun txKind(kind: String) = when (kind) { "topup" -> "जोड़े गए (डेमो)"; "ticket" -> "टिकट"; "pass" -> "बस पास"; else -> kind }
    override val topupNote = "डेमो टॉप-अप: कोई भुगतान नहीं लिया जाता और यह बैलेंस सिर्फ़ इस डेमो में चलता है।"
    override fun balance(x: String) = "बैलेंस $x"; override fun payFromWallet(x: String) = "आपके डेमो वॉलेट से भुगतान (बैलेंस $x)"
    override val passes = "बस पास"; override val myPasses = "मेरे पास"; override val buyPass = "पास खरीदें"; override val noPasses = "अभी कोई पास नहीं।"
    override val passTitle = "बस पास"; override val officialPrice = "DTC का आधिकारिक किराया"; override val unverifiedPrice = "कीमत DTC से पुष्ट नहीं"
    override fun checkedOn(d: String) = "कीमतें $d को जाँची गईं"; override fun buyFor(price: String) = "$price में खरीदें"
    override fun holder(h: String) = "धारक $h"; override val source = "स्रोत"; override val buying = "खरीद रहे हैं…"
    override val profile = "प्रोफ़ाइल"; override val womanTraveller = "महिला यात्री"
    override val womanTravellerSub = "बुकिंग में महिलाओं की मुफ़्त यात्रा पहले से भर देता है (दिल्ली योजना)।"
    override val changePassword = "पासवर्ड बदलें"; override val currentPassword = "मौजूदा पासवर्ड"; override val newPassword = "नया पासवर्ड"
    override val save = "सहेजें"; override val saved = "सहेजा गया"; override val logout = "लॉग आउट"; override val logoutSub = "सभी डिवाइस से लॉग आउट करता है।"
    override val deleteAccount = "अकाउंट मिटाएँ"
    override val deleteWarn = "इससे आपका अकाउंट, वॉलेट का इतिहास, टिकट और पास हमेशा के लिए मिट जाएँगे। पुष्टि के लिए पासवर्ड डालें।"
    override val cancel = "रद्द करें"; override val settings = "सेटिंग्स"
    override val help = "मदद और सुझाव"
    override val faq = listOf(
        "क्या ये असली टिकट हैं?" to "नहीं। टिकट और पास डेमो हैं: यात्रा के लिए मान्य नहीं और कोई पैसा नहीं लिया जाता।",
        "भीड़ का अनुमान कहाँ से आता है?" to "अक्टूबर 2026 के ONDC ई-टिकट डेटा से, हर निर्धारित बस के हिसाब से। यह अनुमान है, गिनती नहीं।",
        "टिकट का स्क्रीनशॉट क्यों नहीं ले सकते?" to "टिकट और पास आपके अकाउंट के हैं। स्क्रीनशॉट बंद हैं और हर QR में धारक का छिपा हुआ नंबर होता है।",
        "क्या अकाउंट ज़रूरी है?" to "यात्रा प्लान करने, लाइव बसें और जानकारी देखने के लिए नहीं। टिकट बुक करने और पास खरीदने के लिए हाँ।",
    )
    override val feedback = "सुझाव भेजें"; override val feedbackHint = "बताइए क्या बेहतर करें"; override val send = "भेजें"
    override val thanks = "धन्यवाद! आपका सुझाव भेज दिया गया।"
    override val privacyTitle = "निजता"
    override val busStops = "बस स्टॉप"; override val placesHeader = "जगहें और पते"; override val osmCredit = "जगहें © OpenStreetMap योगदानकर्ता"
    override fun walkToM(min: Int, m: Int) = "$min मिनट पैदल · $m मी"; override val approx = "अनुमान"
    override fun noStopNear(m: Int) = "इस जगह से $m मीटर के अंदर कोई बस स्टॉप नहीं।"; override val widerSearch = "1.5 किमी तक खोजें"
    override val privacyBody = "हम आपका नाम, मोबाइल नंबर, हैश किया हुआ पासवर्ड और आपका डेमो वॉलेट, टिकट और पास रखते हैं। आपकी लोकेशन हमारे सर्वर पर नहीं रखी जाती। जब आप कोई जगह खोजते हैं या किसी जगह/अपनी लोकेशन से यात्रा खोजते हैं, तो खोज का शब्द और लगभग 10 मीटर तक गोल की गई लोकेशन मुफ़्त OpenStreetMap सेवाओं (Photon, FOSSGIS) को भेजी जाती है, आपके नाम या नंबर के बिना। आप इसी स्क्रीन से सब कुछ मिटा सकते हैं।"
    override val noScreenshots = "टिकट और पास पर स्क्रीनशॉट बंद हैं।"
    override val demoPass = "डेमो पास — यात्रा के लिए मान्य नहीं, असली पैसे नहीं"; override val verifyPass = "पास जाँचें"
}

fun stringsFor(lang: Lang): Strings = if (lang == Lang.HI) Hi else En

val LocalStrings = staticCompositionLocalOf<Strings> { En }
