import type {
  AdvisorRow, ArrivalsBoard, DayBucket, DemoTicket, Hotspot, JourneyResult, LegRef, Place, Preference, Quote,
  StopHit, Vehicle,
} from "./types";

const BASE = (import.meta.env.VITE_API_BASE as string | undefined) ?? "";

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

async function call<T>(path: string, init?: RequestInit & { json?: unknown }): Promise<T> {
  const { json, ...rest } = init ?? {};
  let res: Response;
  try {
    res = await fetch(BASE + "/api/v1" + path, {
      ...rest,
      method: json !== undefined ? "POST" : rest.method ?? "GET",
      headers: json !== undefined ? { "Content-Type": "application/json" } : undefined,
      body: json !== undefined ? JSON.stringify(json) : undefined,
    });
  } catch {
    throw new ApiError(0, "network");
  }
  if (!res.ok) {
    let msg = res.statusText;
    try {
      const body = await res.json();
      msg = typeof body.detail === "string" ? body.detail : body.detail?.message ?? JSON.stringify(body.detail);
    } catch { /* keep statusText */ }
    throw new ApiError(res.status, msg);
  }
  return res.json() as Promise<T>;
}

const q = (o: Record<string, string | number | undefined>) =>
  "?" + new URLSearchParams(Object.entries(o).filter(([, v]) => v !== undefined).map(([k, v]) => [k, String(v)]));

export const api = {
  health: () => call<{ status: string; components: Record<string, boolean> }>("/health"),
  searchStops: (text: string, signal?: AbortSignal) =>
    call<{ results: StopHit[] }>("/stops/search" + q({ q: text, limit: 8 }), { signal }),
  nearbyStops: (lat: number, lon: number) =>
    call<{ results: { stop_id: string; name: string; lat: number; lon: number; distance_m: number }[] }>(
      "/stops/nearby" + q({ lat, lon })),
  stop: (id: string) => call<{ stop_id: string; name: string; lat: number; lon: number }>(`/stops/${encodeURIComponent(id)}`),
  searchRoutes: (text: string) =>
    call<{ results: { route_id: string; route: string; description: string }[] }>("/routes/search" + q({ q: text })),
  journeys: (origin: Place, destination: Place, departure: string, preference: Preference) =>
    call<JourneyResult>("/journeys/search", { json: { origin, destination, departure_datetime: departure, preference } }),
  advisor: (origin: Place, destination: Place, departure: string, preference: Preference) =>
    call<{ departures: AdvisorRow[]; recommendation: { offset_min: number; reason: string } | null }>(
      "/journeys/advisor", { json: { origin, destination, departure_datetime: departure, preference } }),
  liveStatus: () => call<{ live: boolean; vehicles: number; enabled: boolean }>("/live/status"),
  vehicles: (route_id?: string, bbox?: string) => call<{ live: boolean; vehicles: Vehicle[] }>("/live/vehicles" + q({ route_id, bbox })),
  arrivals: (stopId: string) => call<ArrivalsBoard>(`/live/arrivals/${encodeURIComponent(stopId)}`),
  quote: (legs: LegRef[], passengers: number, women_free: number, bus_types?: (string | null)[]) =>
    call<Quote>("/fares/quote", { json: { legs, passengers, women_free, bus_types } }),
  book: (legs: LegRef[], passengers: number, women_free: number, bus_types?: (string | null)[]) =>
    call<DemoTicket>("/tickets/book", { json: { legs, passengers, women_free, bus_types } }),
  verify: (qr_payload: string) => call<{ status: string; valid_signature: boolean }>("/tickets/verify", { json: { qr_payload } }),
  routeDay: (routeId: string, date: string) =>
    call<{ route: string; buckets: DayBucket[]; quietest: string | null; busiest: string | null; summary?: Record<string, string>; forecast_basis: string }>(
      `/crowd/route/${encodeURIComponent(routeId)}` + q({ date })),
  hotspots: (date: string, time: string) =>
    call<{ segments: Hotspot[]; forecast_basis: string; definition: string }>("/crowd/hotspots" + q({ date, time, limit: 150 })),
  modelCard: () => call<Record<string, any>>("/insights/model"), // eslint-disable-line @typescript-eslint/no-explicit-any
};

/** Local date/time in Delhi as YYYY-MM-DD and HH:MM. */
export function delhiNow(d = new Date()): { date: string; time: string } {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Kolkata", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false,
  }).formatToParts(d);
  const g = (t: string) => parts.find((p) => p.type === t)?.value ?? "00";
  return { date: `${g("year")}-${g("month")}-${g("day")}`, time: `${g("hour") === "24" ? "00" : g("hour")}:${g("minute")}` };
}

export const toIso = (date: string, time: string) => `${date}T${time}:00+05:30`;
