import i18n from "i18next";
import { initReactI18next } from "react-i18next";

const en = {
  app: { name: "DelhiRaahi", tagline: "Delhi buses, less crowded", skip: "Skip to content" },
  tabs: { plan: "Plan", live: "Live", tickets: "Tickets", insights: "Insights", profile: "Profile" },
  common: {
    loading: "Loading…", retry: "Try again", close: "Close", cancel: "Cancel", min: "min", km: "km",
    errorNetwork: "Can't reach the DelhiRaahi server. Check your connection and try again.",
    errorGeneric: "Something went wrong: {{msg}}", back: "Back", demo: "DEMO",
  },
  crowd: {
    LOW: "Low crowd", MODERATE: "Moderate crowd", HIGH: "High crowd", VERY_HIGH: "Very high crowd", UNKNOWN: "Crowd unknown",
    short: { LOW: "Low", MODERATE: "Moderate", HIGH: "High", VERY_HIGH: "Very high", UNKNOWN: "Unknown" },
    disclaimer: "Expected crowding is estimated from e-ticket data per scheduled bus — a guide, not a passenger count.",
    evidence: "Evidence: {{level}}",
  },
  plan: {
    title: "Plan a bus trip", from: "From", to: "To", fromPh: "Stop or area, e.g. Kashmere Gate", toPh: "Stop or area, e.g. Nehru Place",
    swap: "Swap from and to", myLocation: "Use my location", locating: "Finding you…", locationDenied: "Location not available. Type a stop instead.",
    when: "Leave at", date: "Date", time: "Time", now: "Now", prefer: "I prefer",
    fastest: "Fastest", balanced: "Balanced", least_crowded: "Least crowded",
    search: "Find buses", searching: "Finding buses…", results: "{{count}} options", noResults: "No bus journey found",
    noResultsHint: "Try another time or a nearby stop. Only direct buses and one change are searched.",
    pickBoth: "Choose where you start and where you're going.", recent: "Recent trips", saved: "Saved places",
    labels: { fastest: "Fastest", balanced: "Best balance", least_crowded: "Least crowded" },
    leave: "Leave {{time}}", arrive: "arrive {{time}}", duration: "{{min}} min", transfers_0: "Direct", transfers_1: "1 change",
    walk: "{{min}} min walk", fare: "Fare", fareRange: "₹{{lo}}–{{hi}}", details: "Details",
    advisor: "Best time to leave", advisorHint: "Compare leaving earlier or later", advisorLoading: "Comparing departure times (takes a few seconds)…",
    advisorCol: { leave: "Leave", trip: "Trip", crowd: "Crowd", buses: "Buses" }, nothing: "—",
  },
  journey: {
    title: "Journey details", board: "Board at", alight: "Get off at", stopsCount: "{{count}} stops", change: "Change bus",
    track: "Track this bus", ticket: "Get demo ticket", crowdAlong: "Crowding along the way", why: "Why this option",
    busType: { ac: "AC", non_ac: "Non-AC", mixed: "AC or non-AC", unknown: "AC or non-AC" },
    scheduled: "Times are from the timetable, not live.",
  },
  booking: {
    title: "Demo ticket", passengers: "Passengers", womenFree: "Women travelling free", busType: "Bus type",
    total: "Total (digital fare)", confirm: "Get demo ticket", getting: "Issuing…",
    watermark: "DEMO TICKET — not valid for travel, no payment taken", done: "Ticket saved in your wallet",
    concession: "Women travel free on DTC and cluster buses (pink ticket / Saheli card). Check current rules.",
  },
  tickets: {
    title: "My tickets", empty: "No tickets yet", emptyHint: "Plan a trip and tap “Get demo ticket”.",
    validUntil: "Valid until {{time}}", expired: "Expired", active: "Active (demo)", remove: "Delete ticket",
    passengers: "{{count}} passenger(s)", show: "Show QR", verify: "Check ticket", verified: "Signature valid: {{status}}",
    stored: "Tickets are stored only on this device.",
  },
  live: {
    title: "Live buses", searchStop: "Find a stop", nearby: "Stops near me", arrivals: "Next buses",
    liveOn: "Live feed on", liveOff: "Live feed unavailable — showing timetable", vehicles: "{{count}} buses on map",
    inMin: "in {{min}} min", now: "now", basis: { live_estimate: "Live", live_estimate_typical_running_time: "Live (approx.)", scheduled: "Timetable" },
    delay: "{{min}} min late", early: "{{min}} min early", onTime: "on time", towards: "towards {{stop}}",
    pickStop: "Pick a stop to see the next buses.", noArrivals: "No buses due in the next hour.", refresh: "Refresh",
    filterRoute: "Showing bus {{route}}", clearFilter: "Show all buses", updated: "Updated {{time}}",
  },
  insights: {
    title: "Insights", map: "Busiest stretches", mapHint: "Top road segments by expected crowding at this time (well-supported estimates only).",
    route: "When is my bus quiet?", routePh: "Bus number, e.g. 717", quietest: "Usually quietest around {{t}}, busiest around {{b}}.",
    model: "How good are these numbers?", limitations: "Limitations",
    routeModel: "Route demand forecast (test week)", segModel: "Crowding per road segment (test week)", fares: "Fare engine",
    mae: "Mean error (passengers per 30 min)", baseline: "Baseline (seasonal average)", wape: "Weighted % error",
    exact: "Exact fare match", withinSlab: "Within one fare slab", honest: "Our model ties a strong baseline — we show both.",
  },
  profile: {
    title: "Profile", language: "Language", theme: "Theme", system: "System", light: "Light", dark: "Dark",
    largeText: "Larger text", highContrast: "High contrast", places: "Saved places", noPlaces: "No saved places yet.",
    save: "Save", savePlace: "Save a place", label: "Name (e.g. Home)", remove: "Remove", clearRecent: "Clear recent trips",
    about: "About", aboutText: "DelhiRaahi is a hackathon prototype. Timetable and live data: Delhi Transport open data. Crowding: modelled from October 2026 ONDC e-tickets. Tickets are demo only.",
    privacy: "Nothing you save leaves this device.",
  },
};

type Tree = { [k: string]: string | Tree };

const hi: Tree = {
  app: { name: "दिल्लीराही", tagline: "दिल्ली की बसें, कम भीड़ में", skip: "मुख्य सामग्री पर जाएँ" },
  tabs: { plan: "यात्रा", live: "लाइव", tickets: "टिकट", insights: "जानकारी", profile: "प्रोफ़ाइल" },
  common: {
    loading: "लोड हो रहा है…", retry: "फिर कोशिश करें", close: "बंद करें", cancel: "रद्द करें", min: "मिनट", km: "किमी",
    errorNetwork: "दिल्लीराही सर्वर से संपर्क नहीं हो पा रहा। इंटरनेट जाँचें और फिर कोशिश करें।",
    errorGeneric: "कुछ गड़बड़ हुई: {{msg}}", back: "वापस", demo: "डेमो",
  },
  crowd: {
    LOW: "कम भीड़", MODERATE: "मध्यम भीड़", HIGH: "ज़्यादा भीड़", VERY_HIGH: "बहुत ज़्यादा भीड़", UNKNOWN: "भीड़ अज्ञात",
    short: { LOW: "कम", MODERATE: "मध्यम", HIGH: "ज़्यादा", VERY_HIGH: "बहुत ज़्यादा", UNKNOWN: "अज्ञात" },
    disclaimer: "अनुमानित भीड़ ई-टिकट डेटा से हर निर्धारित बस के हिसाब से निकाली गई है — यह एक संकेत है, यात्रियों की गिनती नहीं।",
    evidence: "प्रमाण: {{level}}",
  },
  plan: {
    title: "बस यात्रा की योजना", from: "कहाँ से", to: "कहाँ तक", fromPh: "स्टॉप या इलाका, जैसे कश्मीरी गेट", toPh: "स्टॉप या इलाका, जैसे नेहरू प्लेस",
    swap: "कहाँ से और कहाँ तक बदलें", myLocation: "मेरी लोकेशन", locating: "आपको ढूँढ रहे हैं…", locationDenied: "लोकेशन उपलब्ध नहीं। स्टॉप का नाम लिखें।",
    when: "कब निकलना है", date: "तारीख़", time: "समय", now: "अभी", prefer: "मेरी पसंद",
    fastest: "सबसे तेज़", balanced: "संतुलित", least_crowded: "सबसे कम भीड़",
    search: "बसें खोजें", searching: "बसें खोज रहे हैं…", results: "{{count}} विकल्प", noResults: "कोई बस यात्रा नहीं मिली",
    noResultsHint: "कोई और समय या पास का स्टॉप आज़माएँ। सिर्फ़ सीधी बस और एक बदलाव वाली यात्राएँ खोजी जाती हैं।",
    pickBoth: "शुरुआत और मंज़िल चुनें।", recent: "हाल की यात्राएँ", saved: "सहेजी जगहें",
    labels: { fastest: "सबसे तेज़", balanced: "सबसे संतुलित", least_crowded: "सबसे कम भीड़" },
    leave: "{{time}} पर निकलें", arrive: "{{time}} पर पहुँचें", duration: "{{min}} मिनट", transfers_0: "सीधी बस", transfers_1: "1 बदलाव",
    walk: "{{min}} मिनट पैदल", fare: "किराया", fareRange: "₹{{lo}}–{{hi}}", details: "विवरण",
    advisor: "निकलने का सबसे अच्छा समय", advisorHint: "पहले या बाद में निकलने की तुलना करें", advisorLoading: "अलग-अलग समय की तुलना हो रही है (कुछ सेकंड लगेंगे)…",
    advisorCol: { leave: "निकलें", trip: "यात्रा", crowd: "भीड़", buses: "बसें" }, nothing: "—",
  },
  journey: {
    title: "यात्रा का विवरण", board: "यहाँ से चढ़ें", alight: "यहाँ उतरें", stopsCount: "{{count}} स्टॉप", change: "बस बदलें",
    track: "यह बस ट्रैक करें", ticket: "डेमो टिकट लें", crowdAlong: "रास्ते में भीड़", why: "यह विकल्प क्यों",
    busType: { ac: "एसी", non_ac: "नॉन-एसी", mixed: "एसी या नॉन-एसी", unknown: "एसी या नॉन-एसी" },
    scheduled: "समय, समय-सारणी से है, लाइव नहीं।",
  },
  booking: {
    title: "डेमो टिकट", passengers: "यात्री", womenFree: "मुफ़्त यात्रा करने वाली महिलाएँ", busType: "बस का प्रकार",
    total: "कुल (डिजिटल किराया)", confirm: "डेमो टिकट लें", getting: "जारी हो रहा है…",
    watermark: "डेमो टिकट — यात्रा के लिए मान्य नहीं, कोई भुगतान नहीं लिया गया", done: "टिकट आपके वॉलेट में सहेजा गया",
    concession: "डीटीसी और क्लस्टर बसों में महिलाओं के लिए मुफ़्त यात्रा (पिंक टिकट / सहेली कार्ड)। मौजूदा नियम देख लें।",
  },
  tickets: {
    title: "मेरे टिकट", empty: "अभी कोई टिकट नहीं", emptyHint: "यात्रा खोजें और “डेमो टिकट लें” दबाएँ।",
    validUntil: "{{time}} तक मान्य", expired: "समाप्त", active: "सक्रिय (डेमो)", remove: "टिकट हटाएँ",
    passengers: "{{count}} यात्री", show: "QR दिखाएँ", verify: "टिकट जाँचें", verified: "हस्ताक्षर सही: {{status}}",
    stored: "टिकट सिर्फ़ इसी डिवाइस पर रखे जाते हैं।",
  },
  live: {
    title: "लाइव बसें", searchStop: "स्टॉप खोजें", nearby: "मेरे पास के स्टॉप", arrivals: "अगली बसें",
    liveOn: "लाइव फ़ीड चालू", liveOff: "लाइव फ़ीड उपलब्ध नहीं — समय-सारणी दिखा रहे हैं", vehicles: "नक्शे पर {{count}} बसें",
    inMin: "{{min}} मिनट में", now: "अभी", basis: { live_estimate: "लाइव", live_estimate_typical_running_time: "लाइव (अनुमान)", scheduled: "समय-सारणी" },
    delay: "{{min}} मिनट देर", early: "{{min}} मिनट जल्दी", onTime: "समय पर", towards: "{{stop}} की ओर",
    pickStop: "अगली बसें देखने के लिए स्टॉप चुनें।", noArrivals: "अगले एक घंटे में कोई बस नहीं।", refresh: "रीफ़्रेश",
    filterRoute: "बस {{route}} दिखा रहे हैं", clearFilter: "सभी बसें दिखाएँ", updated: "{{time}} पर अपडेट",
  },
  insights: {
    title: "जानकारी", map: "सबसे भीड़ वाले हिस्से", mapHint: "इस समय अनुमानित भीड़ के हिसाब से सबसे व्यस्त सड़क-हिस्से (सिर्फ़ पक्के अनुमान)।",
    route: "मेरी बस कब खाली रहती है?", routePh: "बस नंबर, जैसे 717", quietest: "आम तौर पर {{t}} के आसपास सबसे कम, {{b}} के आसपास सबसे ज़्यादा भीड़।",
    model: "ये आँकड़े कितने सही हैं?", limitations: "सीमाएँ",
    routeModel: "रूट माँग पूर्वानुमान (टेस्ट सप्ताह)", segModel: "सड़क-हिस्से पर भीड़ (टेस्ट सप्ताह)", fares: "किराया गणना",
    mae: "औसत त्रुटि (यात्री प्रति 30 मिनट)", baseline: "आधार (मौसमी औसत)", wape: "भारित % त्रुटि",
    exact: "किराया बिल्कुल सही", withinSlab: "एक स्लैब के भीतर", honest: "हमारा मॉडल एक मज़बूत आधार के बराबर है — हम दोनों दिखाते हैं।",
  },
  profile: {
    title: "प्रोफ़ाइल", language: "भाषा", theme: "थीम", system: "सिस्टम", light: "हल्की", dark: "गहरी",
    largeText: "बड़े अक्षर", highContrast: "हाई कॉन्ट्रास्ट", places: "सहेजी जगहें", noPlaces: "अभी कोई जगह सहेजी नहीं।",
    save: "सहेजें", savePlace: "जगह सहेजें", label: "नाम (जैसे घर)", remove: "हटाएँ", clearRecent: "हाल की यात्राएँ मिटाएँ",
    about: "जानकारी", aboutText: "दिल्लीराही एक हैकथॉन प्रोटोटाइप है। समय-सारणी और लाइव डेटा: दिल्ली परिवहन ओपन डेटा। भीड़: अक्टूबर 2026 के ONDC ई-टिकट से मॉडल किया गया। टिकट सिर्फ़ डेमो हैं।",
    privacy: "आप जो सहेजते हैं वह इसी डिवाइस पर रहता है।",
  },
};

export const resources = { en: { translation: en }, hi: { translation: hi } };

i18n.use(initReactI18next).init({
  resources,
  lng: "en",
  fallbackLng: "en",
  interpolation: { escapeValue: false },
});

export default i18n;
