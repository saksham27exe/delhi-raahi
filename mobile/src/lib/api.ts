import Constants from "expo-constants";
import type {
  AdvisorRow, ArrivalsBoard, DayBucket, DemoTicket, Hotspot, JourneyResult, LegRef, NearbyStop, Place,
  Preference, Quote, StopHit, Vehicle,
} from "./types";

/** Server address. In development the backend runs on the same computer as the Expo dev server,
 * so its LAN IP is taken from Expo's host URI; EXPO_PUBLIC_API_BASE overrides everything, and the
 * Account screen can override it at runtime (e.g. for a demo server). */
function defaultBase(): string {
  const env = process.env.EXPO_PUBLIC_API_BASE;
  if (env) return env.replace(/\/$/, "");
  const host = Constants.expoConfig?.hostUri?.split(":")[0];
  const port = process.env.EXPO_PUBLIC_API_PORT ?? "8000";
  return `http://${host ?? "10.0.2.2"}:${port}`;
}

let base = defaultBase();
export const getApiBase = () => base;
export const setApiBase = (b: string | null) => { base = (b && b.trim()) ? b.trim().replace(/\/$/, "") : defaultBase(); };

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

async function call<T>(path: string, json?: unknown, timeoutMs = 30000): Promise<T> {
  const ctl = new AbortController();
  const timer = setTimeout(() => ctl.abort(), timeoutMs);
  let res: Response;
  try {
    res = await fetch(base + "/api/v1" + path, {
      method: json === undefined ? "GET" : "POST",
      headers: json === undefined ? undefined : { "Content-Type": "application/json" },
      body: json === undefined ? undefined : JSON.stringify(json),
      signal: ctl.signal,
    });
  } catch {
    throw new ApiError(0, "network");
  } finally {
    clearTimeout(timer);
  }
  if (!res.ok) {
    let msg = String(res.status);
    try {
      const b = await res.json();
      msg = typeof b.detail === "string" ? b.detail : b.detail?.message ?? JSON.stringify(b.detail);
    } catch { /* keep status */ }
    throw new ApiError(res.status, msg);
  }
  return res.json() as Promise<T>;
}

const qs = (o: Record<string, string | number | undefined>) =>
  "?" + Object.entries(o).filter(([, v]) => v !== undefined)
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`).join("&");
const enc = encodeURIComponent;

export const api = {
  health: () => call<{ status: string; components: Record<string, boolean> }>("/health", undefined, 8000),
  searchStops: (q: string) => call<{ results: StopHit[] }>("/stops/search" + qs({ q, limit: 8 })),
  nearbyStops: (lat: number, lon: number, radius_m = 800) =>
    call<{ results: NearbyStop[] }>("/stops/nearby" + qs({ lat, lon, radius_m })),
  stop: (id: string) => call<{ stop_id: string; name: string; lat: number; lon: number; routes: { route_id: string; route: string; towards: string }[] }>(`/stops/${enc(id)}`),
  searchRoutes: (q: string) => call<{ results: { route_id: string; route: string; description: string }[] }>("/routes/search" + qs({ q })),
  journeys: (origin: Place, destination: Place, departure: string, preference: Preference) =>
    call<JourneyResult>("/journeys/search", { origin, destination, departure_datetime: departure, preference }),
  advisor: (origin: Place, destination: Place, departure: string, preference: Preference) =>
    call<{ departures: AdvisorRow[]; recommendation: { offset_min: number; reason: string } | null }>(
      "/journeys/advisor", { origin, destination, departure_datetime: departure, preference }, 60000),
  arrivals: (stopId: string) => call<ArrivalsBoard>(`/live/arrivals/${enc(stopId)}`),
  vehicles: (route_id?: string, bbox?: string) => call<{ live: boolean; vehicles: Vehicle[] }>("/live/vehicles" + qs({ route_id, bbox })),
  liveStatus: () => call<{ live: boolean; vehicles: number }>("/live/status"),
  quote: (legs: LegRef[], passengers: number, women_free: number, bus_types?: (string | null)[]) =>
    call<Quote>("/fares/quote", { legs, passengers, women_free, bus_types }),
  book: (legs: LegRef[], passengers: number, women_free: number, bus_types?: (string | null)[]) =>
    call<DemoTicket>("/tickets/book", { legs, passengers, women_free, bus_types }),
  verify: (qr_payload: string) => call<{ status: string; valid_signature: boolean }>("/tickets/verify", { qr_payload }),
  routeDay: (routeId: string, date: string) =>
    call<{ route: string; buckets: DayBucket[]; quietest: string | null; busiest: string | null }>(`/crowd/route/${enc(routeId)}` + qs({ date })),
  hotspots: (date: string, time: string) => call<{ segments: Hotspot[] }>("/crowd/hotspots" + qs({ date, time, limit: 120 })),
  modelCard: () => call<Record<string, any>>("/insights/model"),
};

/** Delhi date/time (YYYY-MM-DD, HH:MM) whatever the device timezone. */
export function delhiNow(d = new Date()): { date: string; time: string } {
  const ist = new Date(d.getTime() + (330 + d.getTimezoneOffset()) * 60000);
  const p = (n: number) => String(n).padStart(2, "0");
  return { date: `${ist.getFullYear()}-${p(ist.getMonth() + 1)}-${p(ist.getDate())}`, time: `${p(ist.getHours())}:${p(ist.getMinutes())}` };
}
export const toIso = (date: string, time: string) => `${date}T${time}:00+05:30`;
export function addMinutes(date: string, time: string, min: number): { date: string; time: string } {
  const t = new Date(`${date}T${time}:00+05:30`).getTime() + min * 60000;
  return delhiNow(new Date(t));
}
