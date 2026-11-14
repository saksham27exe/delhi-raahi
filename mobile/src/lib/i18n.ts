import i18n from "i18next";
import { initReactI18next } from "react-i18next";

export const en = {
  tabs: { home: "Home", live: "Live", tickets: "Tickets", account: "Account" },
  common: {
    min: "min", km: "km", retry: "Try again", close: "Close", done: "Done", back: "Back", save: "Save", now: "Now",
    errNetwork: "Can't reach the DelhiRaahi server. Check Wi-Fi and the server address in Account.",
    errGeneric: "Something went wrong: {{msg}}", loading: "Loading…",
  },
  crowd: {
    LOW: "Low crowd", MODERATE: "Moderate crowd", HIGH: "High crowd", VERY_HIGH: "Very crowded", UNKNOWN: "Crowd unknown",
    short: { LOW: "Low", MODERATE: "Moderate", HIGH: "High", VERY_HIGH: "Very high", UNKNOWN: "Unknown" },
    note: "Expected crowding comes from e-ticket data per scheduled bus. It's a guide, not a passenger count.",
  },
  home: {
    greeting: "Where are you going?", whereTo: "Where to?", nearby: "Bus stops near you", recent: "Recent",
    saved: "Saved places", addHome: "Add Home", addWork: "Add Work", insights: "When are buses quiet?",
    insightsSub: "Crowd patterns, busiest stretches", locating: "Finding stops near you…", noLocation: "Turn on location to see nearby stops.",
  },
  search: {
    title: "Plan your trip", from: "From", to: "To", currentLocation: "Current location", fromPh: "Pick-up stop",
    toPh: "Where to?", typeMore: "Type at least 2 letters", noMatch: "No stop found. Try another spelling.", routes: "{{count}} bus routes",
  },
  results: {
    title: "Choose a trip", leaveAt: "Leave {{time}}", leaveNow: "Leave now", fastest: "Fastest", balanced: "Balanced",
    least_crowded: "Least crowded", direct: "Direct", change: "1 change", choose: "Choose {{route}}", none: "No bus trip found",
    noneHint: "Try another time or a nearby stop. We search direct buses and trips with one change.",
    advisor: "Best time to leave", advisorLoading: "Comparing departure times…", walk: "{{min}} min walk",
    when: "When do you want to leave?", custom: "Pick time", today: "Today", tomorrow: "Tomorrow",
    labels: { fastest: "Fastest", balanced: "Best balance", least_crowded: "Least crowded" },
  },
  journey: {
    title: "Your trip", board: "Board at {{stop}}", alight: "Get off at {{stop}}", rideStops: "Ride {{count}} stops · {{min}} min",
    walkTo: "Walk {{min}} min", change: "Change bus", book: "Book ticket · {{fare}}", bookNoFare: "Book ticket", track: "Track bus",
    why: "Why this trip", crowdAlong: "Crowding along the way", timetable: "Times are from the timetable.",
    busType: { ac: "AC", non_ac: "Non-AC", mixed: "AC or non-AC", unknown: "AC or non-AC" },
  },
  booking: {
    title: "Book demo ticket", passengers: "Passengers", women: "Women travelling free", womenNote: "Free on DTC and cluster buses (pink ticket / Saheli card). Check current rules.",
    busType: "Bus type", total: "Total (digital fare)", confirm: "Confirm demo booking", confirming: "Issuing ticket…",
    demo: "DEMO TICKET — not valid for travel, no payment taken",
  },
  ticket: {
    title: "Ticket", wallet: "My tickets", empty: "No tickets yet", emptyHint: "Plan a trip and book a demo ticket.",
    active: "Active · demo", expired: "Expired", validUntil: "Valid until {{time}}", passengers: "{{count}} passenger(s)",
    verify: "Verify ticket", verified: "Signature valid · {{status}}", remove: "Delete ticket", stored: "Tickets are stored only on this device.",
  },
  live: {
    title: "Live buses", search: "Search a stop", nearby: "Nearby stops", arrivals: "Next buses", liveOn: "Live",
    liveOff: "Live feed unavailable · timetable", inMin: "{{min}} min", now: "Now", late: "{{min}} min late", early: "{{min}} min early",
    onTime: "On time", towards: "to {{stop}}", none: "No buses in the next hour.", buses: "{{count}} buses nearby",
    basis: { live_estimate: "Live", live_estimate_typical_running_time: "Live · approx.", scheduled: "Scheduled" },
  },
  insights: {
    title: "Insights", route: "When is my bus quiet?", routePh: "Bus number, e.g. 717", quietest: "Quietest around {{q}}, busiest around {{b}}.",
    hotspots: "Busiest stretches", hotspotsHint: "Top road segments by expected crowding (well-supported estimates only).",
    model: "How good are these numbers?", routeModel: "Route busy-level score (macro F1, test week, higher is better)", segModel: "Segment crowding error (passengers, MAE, lower is better)",
    pickRoute: "Pick a route to see its day", time: "Time", day: "Today's pattern", noData: "Not enough data for this route.", coverage: "80% forecast interval covered {{pct}}% of test buckets.",
    fares: "Fare engine accuracy", ours: "Our model", baseline: "Baseline", exact: "Exact fare match", slab: "Within one slab",
    limitations: "Limitations", honest: "Our model ties a strong baseline — we show both.",
  },
  account: {
    title: "Account", language: "Language", theme: "Appearance", system: "System", light: "Light", dark: "Dark",
    places: "Saved places", noPlaces: "No saved places yet.", remove: "Remove", clearRecent: "Clear recent trips",
    server: "Server address", serverHint: "Leave empty to use the development computer automatically.", serverOk: "Connected",
    serverFail: "Not reachable", test: "Test", insights: "Crowd insights", about: "About DelhiRaahi",
    aboutText: "Hackathon prototype. Timetable and live data: Delhi Transport open data. Crowding: modelled from October 2026 ONDC e-tickets. Tickets are demo only.",
    privacy: "Everything you save stays on this device.", addPlace: "Add a place", placeName: "Name (e.g. Gym)", home: "Home", work: "Work", other: "Other",
  },
};

type Tree = { [k: string]: string | Tree };
export const hi: Tree = {
  tabs: { home: "होम", live: "लाइव", tickets: "टिकट", account: "अकाउंट" },
  common: {
    min: "मिनट", km: "किमी", retry: "फिर कोशिश करें", close: "बंद करें", done: "हो गया", back: "वापस", save: "सहेजें", now: "अभी",
    errNetwork: "दिल्लीराही सर्वर से संपर्क नहीं हो पा रहा। वाई-फ़ाई और अकाउंट में सर्वर पता जाँचें।",
    errGeneric: "कुछ गड़बड़ हुई: {{msg}}", loading: "लोड हो रहा है…",
  },
  crowd: {
    LOW: "कम भीड़", MODERATE: "मध्यम भीड़", HIGH: "ज़्यादा भीड़", VERY_HIGH: "बहुत ज़्यादा भीड़", UNKNOWN: "भीड़ अज्ञात",
    short: { LOW: "कम", MODERATE: "मध्यम", HIGH: "ज़्यादा", VERY_HIGH: "बहुत ज़्यादा", UNKNOWN: "अज्ञात" },
    note: "अनुमानित भीड़ ई-टिकट डेटा से हर निर्धारित बस के हिसाब से है। यह एक संकेत है, यात्रियों की गिनती नहीं।",
  },
  home: {
    greeting: "कहाँ जाना है?", whereTo: "कहाँ जाना है?", nearby: "आपके पास के बस स्टॉप", recent: "हाल की यात्राएँ",
    saved: "सहेजी जगहें", addHome: "घर जोड़ें", addWork: "ऑफ़िस जोड़ें", insights: "बसें कब खाली रहती हैं?",
    insightsSub: "भीड़ के पैटर्न, सबसे व्यस्त हिस्से", locating: "पास के स्टॉप ढूँढ रहे हैं…", noLocation: "पास के स्टॉप देखने के लिए लोकेशन चालू करें।",
  },
  search: {
    title: "यात्रा की योजना", from: "कहाँ से", to: "कहाँ तक", currentLocation: "मौजूदा लोकेशन", fromPh: "चढ़ने का स्टॉप",
    toPh: "कहाँ जाना है?", typeMore: "कम से कम 2 अक्षर लिखें", noMatch: "कोई स्टॉप नहीं मिला। दूसरी स्पेलिंग आज़माएँ।", routes: "{{count}} बस रूट",
  },
  results: {
    title: "यात्रा चुनें", leaveAt: "{{time}} पर निकलें", leaveNow: "अभी निकलें", fastest: "सबसे तेज़", balanced: "संतुलित",
    least_crowded: "सबसे कम भीड़", direct: "सीधी बस", change: "1 बदलाव", choose: "{{route}} चुनें", none: "कोई बस यात्रा नहीं मिली",
    noneHint: "कोई और समय या पास का स्टॉप आज़माएँ। हम सीधी बस और एक बदलाव वाली यात्राएँ खोजते हैं।",
    advisor: "निकलने का सबसे अच्छा समय", advisorLoading: "अलग-अलग समय की तुलना हो रही है…", walk: "{{min}} मिनट पैदल",
    when: "कब निकलना है?", custom: "समय चुनें", today: "आज", tomorrow: "कल",
    labels: { fastest: "सबसे तेज़", balanced: "सबसे संतुलित", least_crowded: "सबसे कम भीड़" },
  },
  journey: {
    title: "आपकी यात्रा", board: "{{stop}} से चढ़ें", alight: "{{stop}} पर उतरें", rideStops: "{{count}} स्टॉप · {{min}} मिनट",
    walkTo: "{{min}} मिनट पैदल", change: "बस बदलें", book: "टिकट बुक करें · {{fare}}", bookNoFare: "टिकट बुक करें", track: "बस ट्रैक करें",
    why: "यह यात्रा क्यों", crowdAlong: "रास्ते में भीड़", timetable: "समय, समय-सारणी से है।",
    busType: { ac: "एसी", non_ac: "नॉन-एसी", mixed: "एसी या नॉन-एसी", unknown: "एसी या नॉन-एसी" },
  },
  booking: {
    title: "डेमो टिकट बुक करें", passengers: "यात्री", women: "मुफ़्त यात्रा करने वाली महिलाएँ", womenNote: "डीटीसी और क्लस्टर बसों में मुफ़्त (पिंक टिकट / सहेली कार्ड)। मौजूदा नियम देख लें।",
    busType: "बस का प्रकार", total: "कुल (डिजिटल किराया)", confirm: "डेमो बुकिंग पक्की करें", confirming: "टिकट जारी हो रहा है…",
    demo: "डेमो टिकट — यात्रा के लिए मान्य नहीं, कोई भुगतान नहीं लिया गया",
  },
  ticket: {
    title: "टिकट", wallet: "मेरे टिकट", empty: "अभी कोई टिकट नहीं", emptyHint: "यात्रा खोजें और डेमो टिकट बुक करें।",
    active: "सक्रिय · डेमो", expired: "समाप्त", validUntil: "{{time}} तक मान्य", passengers: "{{count}} यात्री",
    verify: "टिकट जाँचें", verified: "हस्ताक्षर सही · {{status}}", remove: "टिकट हटाएँ", stored: "टिकट सिर्फ़ इसी डिवाइस पर रखे जाते हैं।",
  },
  live: {
    title: "लाइव बसें", search: "स्टॉप खोजें", nearby: "पास के स्टॉप", arrivals: "अगली बसें", liveOn: "लाइव",
    liveOff: "लाइव फ़ीड उपलब्ध नहीं · समय-सारणी", inMin: "{{min}} मिनट", now: "अभी", late: "{{min}} मिनट देर", early: "{{min}} मिनट जल्दी",
    onTime: "समय पर", towards: "{{stop}} की ओर", none: "अगले एक घंटे में कोई बस नहीं।", buses: "पास में {{count}} बसें",
    basis: { live_estimate: "लाइव", live_estimate_typical_running_time: "लाइव · अनुमान", scheduled: "समय-सारणी" },
  },
  insights: {
    title: "जानकारी", route: "मेरी बस कब खाली रहती है?", routePh: "बस नंबर, जैसे 717", quietest: "{{q}} के आसपास सबसे कम, {{b}} के आसपास सबसे ज़्यादा भीड़।",
    hotspots: "सबसे भीड़ वाले हिस्से", hotspotsHint: "अनुमानित भीड़ के हिसाब से सबसे व्यस्त सड़क-हिस्से (सिर्फ़ पक्के अनुमान)।",
    model: "ये आँकड़े कितने सही हैं?", routeModel: "रूट भीड़-स्तर स्कोर (macro F1, टेस्ट सप्ताह, ज़्यादा बेहतर)", segModel: "सड़क-हिस्से की भीड़ त्रुटि (यात्री, MAE, कम बेहतर)",
    pickRoute: "दिन का पैटर्न देखने के लिए रूट चुनें", time: "समय", day: "आज का पैटर्न", noData: "इस रूट के लिए पर्याप्त डेटा नहीं।", coverage: "80% अनुमान-सीमा ने {{pct}}% टेस्ट समय को कवर किया।",
    fares: "किराया गणना की सटीकता", ours: "हमारा मॉडल", baseline: "आधार", exact: "किराया बिल्कुल सही", slab: "एक स्लैब के भीतर",
    limitations: "सीमाएँ", honest: "हमारा मॉडल एक मज़बूत आधार के बराबर है — हम दोनों दिखाते हैं।",
  },
  account: {
    title: "अकाउंट", language: "भाषा", theme: "दिखावट", system: "सिस्टम", light: "हल्की", dark: "गहरी",
    places: "सहेजी जगहें", noPlaces: "अभी कोई जगह सहेजी नहीं।", remove: "हटाएँ", clearRecent: "हाल की यात्राएँ मिटाएँ",
    server: "सर्वर पता", serverHint: "खाली छोड़ें तो डेवलपमेंट कंप्यूटर अपने-आप इस्तेमाल होगा।", serverOk: "जुड़ा हुआ",
    serverFail: "संपर्क नहीं", test: "जाँचें", insights: "भीड़ की जानकारी", about: "दिल्लीराही के बारे में",
    aboutText: "हैकथॉन प्रोटोटाइप। समय-सारणी और लाइव डेटा: दिल्ली परिवहन ओपन डेटा। भीड़: अक्टूबर 2026 के ONDC ई-टिकट से मॉडल किया गया। टिकट सिर्फ़ डेमो हैं।",
    privacy: "आप जो सहेजते हैं वह इसी डिवाइस पर रहता है।", addPlace: "जगह जोड़ें", placeName: "नाम (जैसे जिम)", home: "घर", work: "ऑफ़िस", other: "अन्य",
  },
};

i18n.use(initReactI18next).init({
  resources: { en: { translation: en }, hi: { translation: hi } },
  lng: "en", fallbackLng: "en", interpolation: { escapeValue: false },
});

export default i18n;
