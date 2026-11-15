import { Badge } from "../../ui";

export interface Req {
  id: number; ref: string; kind: string; status: string; priority: string; safety: number; message: string;
  email: string | null; user_id: string | null; route: string | null; when_text: string | null; place: string | null;
  app_version: string | null; lang: string; assignee_id: number | null; assignee_name: string | null; emailed: number;
  created_at: string; updated_at: string; resolved_at: string | null;
}
export interface Event { id: number; at: string; admin_name: string | null; kind: string; body: string | null; meta: Record<string, string | null> | null }
export interface ReqDetail extends Req { events: Event[]; other_requests: { id: number; ref: string; kind: string; status: string; created_at: string }[] }
export interface Counts { new: number; in_progress: number; waiting: number; resolved: number; open: number; urgent_open: number; unassigned_open: number }

export const KIND: Record<string, string> = { complaint: "Complaint", problem: "App problem", ticket: "Ticket / wallet", idea: "Suggestion" };
export const STATUS: Record<string, string> = { new: "New", in_progress: "In progress", waiting: "Waiting on person", resolved: "Resolved" };
export const PRIORITY: Record<string, string> = { urgent: "Urgent", high: "High", normal: "Normal", low: "Low" };

export const StatusBadge = ({ s }: { s: string }) => (
  <Badge tone={s === "new" ? "accent" : s === "in_progress" ? "info" : s === "waiting" ? "warn" : "good"}>{STATUS[s] ?? s}</Badge>
);
export const PriorityBadge = ({ p }: { p: string }) => (
  <Badge tone={p === "urgent" ? "bad" : p === "high" ? "warn" : "neutral"}>{PRIORITY[p] ?? p}</Badge>
);
export const KindBadge = ({ k }: { k: string }) => <Badge tone={k === "complaint" ? "accent" : "neutral"}>{KIND[k] ?? k}</Badge>;
export const SafetyBadge = () => <Badge tone="bad">⚠ Safety</Badge>;

/** Ready-made replies an agent can start from (edited before sending). */
export const CANNED: { label: string; en: string; hi: string }[] = [
  { label: "Thanks, looking into it",
    en: "Thank you for telling us. We're looking into this and will get back to you within two working days.",
    hi: "हमें बताने के लिए धन्यवाद। हम इसकी जाँच कर रहे हैं और दो कामकाजी दिनों में जवाब देंगे।" },
  { label: "Need more details",
    en: "Thanks for writing. To look into this, could you tell us the bus route number, the date and roughly what time it happened, and the stop?",
    hi: "लिखने के लिए धन्यवाद। जाँच के लिए कृपया बस का रूट नंबर, तारीख, लगभग समय और स्टॉप बताएँ।" },
  { label: "Passed to operator",
    en: "We've passed your complaint, with the route and time, to the bus operator's grievance team. We'll update you when we hear back.",
    hi: "आपकी शिकायत रूट और समय के साथ बस ऑपरेटर की शिकायत टीम को भेज दी गई है। जवाब मिलते ही आपको बताया जाएगा।" },
  { label: "Demo tickets",
    en: "DelhiRaahi tickets, passes and wallet are demos: no real money is taken and they aren't valid for travel. Nothing was charged to you.",
    hi: "DelhiRaahi के टिकट, पास और वॉलेट डेमो हैं: कोई असली पैसा नहीं लिया जाता और ये यात्रा के लिए मान्य नहीं हैं। आपसे कुछ नहीं लिया गया।" },
  { label: "Fixed in update",
    en: "Thanks for reporting this. It's fixed in the latest version of the app; please update and let us know if you still see it.",
    hi: "बताने के लिए धन्यवाद। यह ऐप के नए वर्ज़न में ठीक कर दिया गया है; कृपया ऐप अपडेट करें और फिर भी दिक्कत हो तो बताएँ।" },
  { label: "Safety: please call 112",
    en: "We're sorry this happened. If you are in danger, please call 112 right away. We have flagged this as urgent and passed it to the operator with the route and time.",
    hi: "हमें खेद है कि ऐसा हुआ। अगर आप खतरे में हैं तो तुरंत 112 पर कॉल करें। इसे ज़रूरी मानकर रूट और समय के साथ ऑपरेटर को भेज दिया गया है।" },
];
