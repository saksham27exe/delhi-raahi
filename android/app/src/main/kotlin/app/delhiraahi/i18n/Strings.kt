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
}

fun stringsFor(lang: Lang): Strings = if (lang == Lang.HI) Hi else En

val LocalStrings = staticCompositionLocalOf<Strings> { En }
