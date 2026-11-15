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
    fun crowd(cat: String): String; fun crowdShort(cat: String): String; fun confidence(evidence: String): String
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
    val noArrivals: String; fun basis(b: String): String; val zoomIn: String; fun busesShown(n: Int, stops: Int?): String; val searchStopPlace: String; fun stopsNear(p: String): String; val refresh: String
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
    val profile: String; val changePassword: String
    val filterWhen: String; val filterSort: String; val filterBus: String; val busAny: String
    val busAc: String; val busNonAc: String; val busMixed: String; val busTypeNote: String
    val currentPassword: String; val newPassword: String; val save: String; val saved: String; val logout: String; val logoutSub: String
    val deleteAccount: String; val deleteWarn: String; val cancel: String; val settings: String
    val alertsTitle: String; val alertArrive: String; val alertArriveSub: String; val alertGetOff: String; val alertGetOffSub: String; val alertsOffNote: String; val alertSpoken: String; val alertSpokenSub: String; val hearChime: String
    val voiceTitle: String; val wakeTitle: String; val wakeSub: String; val wakeHint: String
    val help: String; val helpSub: String; val faq: List<Pair<String, String>>; val send: String
    val faqTitle: String; val supportTitle: String; val supportSub: String; val quickFaq: String; val quickSupport: String; val quickComplaint: String
    val kindComplaint: String; val kindProblem: String; val kindTicket: String; val kindIdea: String; fun messageHint(kind: String): String
    val complaintRoute: String; val complaintWhen: String; val complaintWhere: String; val complaintDetails: String
    val yourEmail: String; val emailCopyNote: String; val noEmailNote: String; val tooShort: String; val badEmail: String
    fun supportSent(ref: String, copyTo: String?): String; fun supportSaved(ref: String): String
    val emailUs: String; val emailUsSub: String; val noMailApp: String; val emergencyNote: String; val voidedTicket: String
    val busStops: String; val placesHeader: String; val osmCredit: String; fun walkToM(min: Int, m: Int): String; val approx: String
    fun noStopNear(m: Int): String; val widerSearch: String
    val groupMoney: String; val groupMore: String; val alertsVoice: String; val langLook: String; val advanced: String
    val security: String; val aboutPrivacy: String; fun onOf(on: Int, total: Int): String; val serverDefault: String
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
    override fun confidence(evidence: String) = when (evidence) { "HIGH" -> "Based on many tickets"; "MEDIUM" -> "Based on a fair number of tickets"; "LOW" -> "Few tickets on this trip, so the crowd level is less certain"; else -> "" }
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
    override val verify = "Verify ticket"; override fun verified(status: String) = "Signature valid · " + when (status) { "active_demo" -> "active (demo)"; "expired" -> "expired"; "not_on_record" -> "not on record: do not accept"; "voided" -> "cancelled by support: do not accept"; else -> status }; override val notVerified = "Signature not valid"
    override val removeTicket = "Delete ticket"; override val storedOnDevice = "Tickets are stored only on this device."
    override val emptyHint = "Plan a trip and book a demo ticket."; override val planTrip = "Plan a trip"
    override val searchStop = "Search a stop"; override val nearbyStops = "Nearby stops"; override val allBuses = "All buses"
    override fun inMin(m: Int) = "$m min"; override val nowArriving = "Now"
    override fun late(m: Int) = "$m min late"; override fun early(m: Int) = "$m min early"; override val onTime = "On time"
    override fun towards(stop: String) = "to $stop"; override val noArrivals = "No buses in the next hour."
    override fun basis(b: String) = when (b) { "live_estimate" -> "Live"; "live_estimate_typical_running_time" -> "Live · approx."; else -> "Scheduled" }
    override val zoomIn = "Zoom in to see live buses"; override fun busesShown(n: Int, stops: Int?) = (if (n == 1) "1 bus" else "$n buses") +
        when (stops) { null -> " on map · zoom in for stops"; 1 -> " · 1 stop on map"; else -> " · $stops stops on map" }
    override val searchStopPlace = "Search a stop or place"; override fun stopsNear(p: String) = "Stops near $p"; override val refresh = "Refresh"
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
    override val profile = "Profile"
    override val filterWhen = "When"; override val filterSort = "Sort by"; override val filterBus = "Bus type"; override val busAny = "Any bus"
    override val busAc = "AC"; override val busNonAc = "Non-AC"; override val busMixed = "AC / non-AC"
    override val busTypeNote = "AC / non-AC is estimated for each route from its e-ticket sales; the bus that comes may differ."
    override val changePassword = "Change password"; override val currentPassword = "Current password"; override val newPassword = "New password"
    override val save = "Save"; override val saved = "Saved"; override val logout = "Log out"; override val logoutSub = "Signs you out on all devices."
    override val deleteAccount = "Delete account"
    override val deleteWarn = "This permanently deletes your account, wallet history, tickets and passes. Enter your password to confirm."
    override val cancel = "Cancel"; override val settings = "Settings"
    override val alertsTitle = "Ride alerts"; override val alertArrive = "Bus arriving"
    override val alertArriveSub = "A soft chime and vibration when your bus is arriving"
    override val alertGetOff = "Time to get off"
    override val alertGetOffSub = "A soft chime and vibration one stop before yours and when you reach it"
    override val alertSpoken = "Speak alerts aloud"; override val hearChime = "Hear the chime"
    override val voiceTitle = "Voice assistant"; override val wakeTitle = "Start with “Hey Raahi”"
    override val wakeSub = "Say “Hey Raahi” to start the assistant without touching the screen. Listens on this phone only, while the app is open; nothing is recorded or sent. On by default when TalkBack is on."
    override val wakeHint = "or say “Hey Raahi”"
    override val alertSpokenSub = "Also say each alert in words (uses the speaker; earphones recommended)"
    override val alertsOffNote = "Some alerts are off in Settings, so they're only shown here"
    override val help = "Help & support"
    override val helpSub = "FAQ, contact support, complaints"
    override val faq = listOf(
        "Are these real tickets?" to "No. Tickets and passes are demos: not valid for travel and no money is taken.",
        "Where do the crowd levels come from?" to "From October 2026 ONDC e-ticket data, per scheduled bus. They are an estimate, not a head count.",
        "Why can I not take a screenshot of a ticket?" to "Tickets and passes belong to your account. Screenshots are blocked and each QR shows the holder's masked number.",
        "Do I need an account?" to "No for planning trips, live buses and insights. Yes for booking tickets and buying passes.",
        "How is a bus marked AC or non-AC?" to "From the share of AC e-tickets sold on each route in October 2026. “AC / non-AC” means the route runs both kinds of bus. It's a guide, not a promise for one particular bus.",
        "How up to date are live buses?" to "Positions come from Delhi's open real-time bus feed and refresh about every 30 seconds.",
        "How do I use the voice assistant?" to "Tap the mic button, or say “Hey Raahi” if that's on under Account → Voice assistant. Say where you want to go, like “Karol Bagh to Akshardham”. If the assistant isn't sure what it heard, it asks again.",
        "Is my voice recorded?" to "No. “Hey Raahi” is heard on your phone only. Your phone's speech service turns speech into text; if the app can't read a sentence, only that text (not audio) goes to our server to be understood, and it isn't stored.",
        "What does the SOS button do?" to "It offers to call 112 (emergency), 1091 (women's helpline) or 102 (ambulance), or to share your location through an app you choose. It never calls or shares anything by itself.",
        "How do I complain about a bus, driver or conductor?" to "Use “Make a complaint” on this screen. Add the route number, time and stop if you can. You get a reference number, and a copy by email if you give an address. Complaints reach the DelhiRaahi team, not the bus operator. In an emergency, call 112.",
        "How do I delete my data?" to "Account → Delete account removes your account, demo wallet, tickets, passes and the messages you sent us.",
    )
    override val send = "Send"
    override val faqTitle = "Frequently asked questions"; override val supportTitle = "Contact support"
    override val supportSub = "Tell us what happened. We read every message."
    override val quickFaq = "FAQ"; override val quickSupport = "Support"; override val quickComplaint = "Make a complaint"
    override val kindComplaint = "Complaint"; override val kindProblem = "App problem"; override val kindTicket = "Ticket or wallet"; override val kindIdea = "Suggestion"
    override fun messageHint(kind: String) = when (kind) {
        "complaint" -> "What happened? E.g. the bus didn't stop, rude behaviour, overcrowding, unsafe driving"
        "problem" -> "What went wrong, and on which screen?"
        "ticket" -> "Which ticket, pass or top-up, and what's wrong with it?"
        else -> "What should we add or improve?"
    }
    override val complaintRoute = "Bus route (e.g. 534)"; override val complaintWhen = "When (e.g. today 9 am)"; override val complaintWhere = "Stop or area"
    override val complaintDetails = "About the bus (optional)"
    override val yourEmail = "Your email (for our reply)"; override val emailCopyNote = "We'll send a copy with your reference number to this address."
    override val noEmailNote = "Without an email we can't reply to you."
    override val tooShort = "Please write at least 10 characters."; override val badEmail = "That email address doesn't look right."
    override fun supportSent(ref: String, copyTo: String?) = "Sent. Your reference is $ref." + (copyTo?.let { " A copy was emailed to $it." } ?: "")
    override fun supportSaved(ref: String) = "Saved with reference $ref. Our email isn't working right now, so the team will see it in the app's records."
    override val emailUs = "Email us"; override val emailUsSub = "Opens your email app"; override val noMailApp = "No email app found. The address was copied."
    override val emergencyNote = "In an emergency on a bus, use SOS on the Home screen or call 112."
    override val voidedTicket = "Cancelled by DelhiRaahi support. This ticket is no longer valid."
    override val groupMoney = "Wallet & passes"; override val groupMore = "Explore & help"; override val alertsVoice = "Alerts & voice"
    override val langLook = "Language & appearance"; override val advanced = "Advanced"; override val security = "Account & security"
    override val aboutPrivacy = "About & privacy"; override fun onOf(on: Int, total: Int) = "$on of $total on"; override val serverDefault = "Default server"
    override val privacyTitle = "Privacy"
    override val busStops = "Bus stops"; override val placesHeader = "Places and addresses"; override val osmCredit = "Places © OpenStreetMap contributors"
    override fun walkToM(min: Int, m: Int) = "Walk $min min · $m m"; override val approx = "approx."
    override fun noStopNear(m: Int) = "No bus stop within $m m of this place."; override val widerSearch = "Search within 1.5 km"
    override val privacyBody = "We store your name, your email address or mobile number, and your demo wallet, tickets, passes and the messages you send us (passwords are never stored in plain text). No location is stored on our server. When you search for a place, or plan a trip from a place or your location, the search text and the position rounded to about 10 m are sent to free OpenStreetMap services (Photon place search, FOSSGIS walking routes) without your name or number. You can delete everything under Account → Delete account."
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
    override fun confidence(evidence: String) = when (evidence) { "HIGH" -> "बहुत से टिकटों पर आधारित"; "MEDIUM" -> "काफ़ी टिकटों पर आधारित"; "LOW" -> "इस यात्रा पर कम टिकट हैं, इसलिए भीड़ का अनुमान कम पक्का है"; else -> "" }
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
    override val verify = "टिकट जाँचें"; override fun verified(status: String) = "हस्ताक्षर सही · " + when (status) { "active_demo" -> "चालू (डेमो)"; "expired" -> "समय खत्म"; "not_on_record" -> "रिकॉर्ड में नहीं: स्वीकार न करें"; "voided" -> "सहायता ने रद्द किया: स्वीकार न करें"; else -> status }; override val notVerified = "हस्ताक्षर सही नहीं"
    override val removeTicket = "टिकट हटाएँ"; override val storedOnDevice = "टिकट सिर्फ़ इसी डिवाइस पर रखे जाते हैं।"
    override val emptyHint = "यात्रा खोजें और डेमो टिकट बुक करें।"; override val planTrip = "यात्रा खोजें"
    override val searchStop = "स्टॉप खोजें"; override val nearbyStops = "पास के स्टॉप"; override val allBuses = "सभी बसें"
    override fun inMin(m: Int) = "$m मिनट"; override val nowArriving = "अभी"
    override fun late(m: Int) = "$m मिनट देर"; override fun early(m: Int) = "$m मिनट जल्दी"; override val onTime = "समय पर"
    override fun towards(stop: String) = "$stop की ओर"; override val noArrivals = "अगले एक घंटे में कोई बस नहीं।"
    override fun basis(b: String) = when (b) { "live_estimate" -> "लाइव"; "live_estimate_typical_running_time" -> "लाइव · अनुमान"; else -> "समय-सारणी" }
    override val zoomIn = "लाइव बसें देखने के लिए ज़ूम करें"; override fun busesShown(n: Int, stops: Int?) = "मैप पर $n बसें · " + (stops?.let { "$it स्टॉप" } ?: "स्टॉप देखने के लिए ज़ूम करें")
    override val searchStopPlace = "स्टॉप या जगह खोजें"; override fun stopsNear(p: String) = "$p के पास के स्टॉप"; override val refresh = "ताज़ा करें"
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
    override val profile = "प्रोफ़ाइल"
    override val filterWhen = "कब"; override val filterSort = "क्रम"; override val filterBus = "बस का प्रकार"; override val busAny = "कोई भी बस"
    override val busAc = "एसी"; override val busNonAc = "नॉन-एसी"; override val busMixed = "एसी / नॉन-एसी"
    override val busTypeNote = "एसी / नॉन-एसी हर रूट की ई-टिकट बिक्री से अनुमानित है; आने वाली बस अलग हो सकती है।"
    override val changePassword = "पासवर्ड बदलें"; override val currentPassword = "मौजूदा पासवर्ड"; override val newPassword = "नया पासवर्ड"
    override val save = "सहेजें"; override val saved = "सहेजा गया"; override val logout = "लॉग आउट"; override val logoutSub = "सभी डिवाइस से लॉग आउट करता है।"
    override val deleteAccount = "अकाउंट मिटाएँ"
    override val deleteWarn = "इससे आपका अकाउंट, वॉलेट का इतिहास, टिकट और पास हमेशा के लिए मिट जाएँगे। पुष्टि के लिए पासवर्ड डालें।"
    override val cancel = "रद्द करें"; override val settings = "सेटिंग्स"
    override val alertsTitle = "यात्रा अलर्ट"; override val alertArrive = "बस आ रही है"
    override val alertArriveSub = "बस आने पर हल्की घंटी और वाइब्रेशन"
    override val alertGetOff = "उतरने का समय"
    override val alertGetOffSub = "आपके स्टॉप से एक स्टॉप पहले और पहुँचने पर हल्की घंटी और वाइब्रेशन"
    override val alertSpoken = "अलर्ट बोलकर भी बताएँ"; override val hearChime = "घंटी सुनें"
    override val voiceTitle = "आवाज़ सहायक"; override val wakeTitle = "“हे राही” बोलकर शुरू करें"
    override val wakeSub = "स्क्रीन छुए बिना सहायक शुरू करने के लिए “हे राही” बोलें। सिर्फ़ इसी फ़ोन पर, ऐप खुला होने पर ही सुना जाता है; कुछ भी रिकॉर्ड या भेजा नहीं जाता। TalkBack चालू होने पर यह अपने-आप चालू रहता है।"
    override val wakeHint = "या “हे राही” बोलें"
    override val alertSpokenSub = "हर अलर्ट शब्दों में भी सुनाया जाए (ईयरफ़ोन बेहतर रहेंगे)"
    override val alertsOffNote = "कुछ अलर्ट सेटिंग्स में बंद हैं, इसलिए वे सिर्फ़ यहाँ दिखेंगे"
    override val help = "मदद और सहायता"
    override val helpSub = "सवाल-जवाब, सहायता, शिकायत"
    override val faq = listOf(
        "क्या ये असली टिकट हैं?" to "नहीं। टिकट और पास डेमो हैं: यात्रा के लिए मान्य नहीं और कोई पैसा नहीं लिया जाता।",
        "भीड़ का अनुमान कहाँ से आता है?" to "अक्टूबर 2026 के ONDC ई-टिकट डेटा से, हर निर्धारित बस के हिसाब से। यह अनुमान है, गिनती नहीं।",
        "टिकट का स्क्रीनशॉट क्यों नहीं ले सकते?" to "टिकट और पास आपके अकाउंट के हैं। स्क्रीनशॉट बंद हैं और हर QR में धारक का छिपा हुआ नंबर होता है।",
        "क्या अकाउंट ज़रूरी है?" to "यात्रा प्लान करने, लाइव बसें और जानकारी देखने के लिए नहीं। टिकट बुक करने और पास खरीदने के लिए हाँ।",
        "बस AC या नॉन-AC कैसे तय होती है?" to "अक्टूबर 2026 में हर रूट पर बिके AC ई-टिकटों के हिस्से से। “AC / नॉन-AC” का मतलब है कि उस रूट पर दोनों तरह की बसें चलती हैं। यह अंदाज़ा है, किसी एक बस की गारंटी नहीं।",
        "लाइव बसें कितनी ताज़ा हैं?" to "बसों की जगह दिल्ली की खुली रियल-टाइम बस फ़ीड से आती है और लगभग हर 30 सेकंड में बदलती है।",
        "वॉइस सहायक कैसे इस्तेमाल करें?" to "माइक बटन दबाएँ, या अगर अकाउंट → वॉइस सहायक में चालू है तो “हे राही” बोलें। फिर बताएँ कहाँ जाना है, जैसे “करोल बाग से अक्षरधाम”। सुनी बात पक्की न हो तो सहायक दोबारा पूछता है।",
        "क्या मेरी आवाज़ रिकॉर्ड होती है?" to "नहीं। “हे राही” सिर्फ़ आपके फ़ोन पर सुना जाता है। फ़ोन की स्पीच सेवा बोली को शब्दों में बदलती है; अगर ऐप कोई वाक्य न समझ पाए, तो सिर्फ़ वह लिखा हुआ वाक्य (आवाज़ नहीं) समझने के लिए हमारे सर्वर पर जाता है, और रखा नहीं जाता।",
        "SOS बटन क्या करता है?" to "यह 112 (आपातकाल), 1091 (महिला हेल्पलाइन) या 102 (एम्बुलेंस) पर कॉल करने, या आपकी चुनी ऐप से लोकेशन भेजने का विकल्प देता है। यह अपने-आप कोई कॉल या लोकेशन नहीं भेजता।",
        "बस, ड्राइवर या कंडक्टर की शिकायत कैसे करें?" to "इसी स्क्रीन पर “शिकायत करें” चुनें। हो सके तो रूट नंबर, समय और स्टॉप लिखें। आपको एक संदर्भ संख्या मिलेगी, और ईमेल देने पर उसकी प्रति भी। शिकायत DelhiRaahi टीम को जाती है, बस ऑपरेटर को नहीं। आपातकाल में 112 पर कॉल करें।",
        "अपना डेटा कैसे मिटाएँ?" to "अकाउंट → अकाउंट मिटाएँ से आपका अकाउंट, डेमो वॉलेट, टिकट, पास और हमें भेजे संदेश मिट जाते हैं।",
    )
    override val send = "भेजें"
    override val faqTitle = "अक्सर पूछे जाने वाले सवाल"; override val supportTitle = "सहायता से संपर्क"
    override val supportSub = "बताइए क्या हुआ। हर संदेश पढ़ा जाता है।"
    override val quickFaq = "सवाल-जवाब"; override val quickSupport = "सहायता"; override val quickComplaint = "शिकायत करें"
    override val kindComplaint = "शिकायत"; override val kindProblem = "ऐप में दिक्कत"; override val kindTicket = "टिकट या वॉलेट"; override val kindIdea = "सुझाव"
    override fun messageHint(kind: String) = when (kind) {
        "complaint" -> "क्या हुआ? जैसे बस नहीं रुकी, बुरा बर्ताव, बहुत भीड़, लापरवाही से चलाना"
        "problem" -> "क्या गड़बड़ हुई, और किस स्क्रीन पर?"
        "ticket" -> "कौन सा टिकट, पास या टॉप-अप, और उसमें क्या दिक्कत है?"
        else -> "हम क्या जोड़ें या बेहतर करें?"
    }
    override val complaintRoute = "बस रूट (जैसे 534)"; override val complaintWhen = "कब (जैसे आज सुबह 9 बजे)"; override val complaintWhere = "स्टॉप या इलाका"
    override val complaintDetails = "बस के बारे में (ज़रूरी नहीं)"
    override val yourEmail = "आपका ईमेल (जवाब के लिए)"; override val emailCopyNote = "इस पते पर संदर्भ संख्या के साथ एक प्रति भेजी जाएगी।"
    override val noEmailNote = "ईमेल के बिना आपको जवाब नहीं भेजा जा सकता।"
    override val tooShort = "कम से कम 10 अक्षर लिखें।"; override val badEmail = "यह ईमेल पता सही नहीं लगता।"
    override fun supportSent(ref: String, copyTo: String?) = "भेज दिया गया। आपकी संदर्भ संख्या $ref है।" + (copyTo?.let { " एक प्रति $it पर भेजी गई।" } ?: "")
    override fun supportSaved(ref: String) = "संदर्भ संख्या $ref के साथ सहेजा गया। अभी ईमेल नहीं जा पा रहा, इसलिए टीम इसे ऐप के रिकॉर्ड में देखेगी।"
    override val emailUs = "हमें ईमेल करें"; override val emailUsSub = "आपकी ईमेल ऐप खुलेगी"; override val noMailApp = "कोई ईमेल ऐप नहीं मिली। पता कॉपी कर लिया गया।"
    override val emergencyNote = "बस में आपात स्थिति हो तो होम स्क्रीन पर SOS दबाएँ या 112 पर कॉल करें।"
    override val voidedTicket = "DelhiRaahi सहायता ने इसे रद्द कर दिया है। यह टिकट अब मान्य नहीं है।"
    override val groupMoney = "वॉलेट और पास"; override val groupMore = "जानकारी और मदद"; override val alertsVoice = "अलर्ट और आवाज़"
    override val langLook = "भाषा और दिखावट"; override val advanced = "उन्नत"; override val security = "अकाउंट और सुरक्षा"
    override val aboutPrivacy = "परिचय और निजता"; override fun onOf(on: Int, total: Int) = "$total में से $on चालू"; override val serverDefault = "डिफ़ॉल्ट सर्वर"
    override val privacyTitle = "निजता"
    override val busStops = "बस स्टॉप"; override val placesHeader = "जगहें और पते"; override val osmCredit = "जगहें © OpenStreetMap योगदानकर्ता"
    override fun walkToM(min: Int, m: Int) = "$min मिनट पैदल · $m मी"; override val approx = "अनुमान"
    override fun noStopNear(m: Int) = "इस जगह से $m मीटर के अंदर कोई बस स्टॉप नहीं।"; override val widerSearch = "1.5 किमी तक खोजें"
    override val privacyBody = "हम आपका नाम, आपका ईमेल पता या मोबाइल नंबर, और आपका डेमो वॉलेट, टिकट, पास और हमें भेजे संदेश रखते हैं (पासवर्ड कभी सादे रूप में नहीं रखे जाते)। आपकी लोकेशन हमारे सर्वर पर नहीं रखी जाती। जब आप कोई जगह खोजते हैं या किसी जगह/अपनी लोकेशन से यात्रा खोजते हैं, तो खोज का शब्द और लगभग 10 मीटर तक गोल की गई लोकेशन मुफ़्त OpenStreetMap सेवाओं (Photon, FOSSGIS) को भेजी जाती है, आपके नाम या नंबर के बिना। अकाउंट → अकाउंट मिटाएँ से सब कुछ मिटाया जा सकता है।"
    override val noScreenshots = "टिकट और पास पर स्क्रीनशॉट बंद हैं।"
    override val demoPass = "डेमो पास — यात्रा के लिए मान्य नहीं, असली पैसे नहीं"; override val verifyPass = "पास जाँचें"
}

fun stringsFor(lang: Lang): Strings = if (lang == Lang.HI) Hi else En

val LocalStrings = staticCompositionLocalOf<Strings> { En }
