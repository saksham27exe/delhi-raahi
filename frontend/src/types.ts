export type Lang = "en" | "hi";
export type Bilingual = Record<Lang, string>;
export type CrowdCategory = "LOW" | "MODERATE" | "HIGH" | "VERY_HIGH" | "UNKNOWN";
export type Preference = "fastest" | "balanced" | "least_crowded";

export interface StopHit {
  name: string;
  stop_ids: string[];
  lat: number;
  lon: number;
  routes_served: number;
}

export type Place =
  | { type: "stop"; id: string; label?: string }
  | { type: "stop_name"; name: string; label?: string }
  | { type: "coord"; lat: number; lon: number; label?: string };

export interface SegmentCrowd {
  category: CrowdCategory;
  crowd_index: number | null;
  evidence: string;
}

export interface Segment {
  segment_id: string;
  from_stop: string;
  to_stop: string;
  depart: string | null;
  minutes: number;
  lat: number;
  lon: number;
  to_lat: number;
  to_lon: number;
  crowd: SegmentCrowd | null;
}

export interface LegFare {
  distance_km: number;
  bus_type: "ac" | "non_ac" | "mixed" | "unknown";
  fare: number | null;
  fare_non_ac: number;
  fare_ac: number;
  note: string | null;
}

export interface Leg {
  route_id: string;
  route: string;
  route_desc: string;
  board_stop_id: string;
  board_stop: string;
  alight_stop_id: string;
  alight_stop: string;
  departure: string;
  arrival: string;
  in_vehicle_minutes: number;
  stops: number;
  segments: Segment[];
  fare: LegFare | null;
}

export interface Journey {
  journey_id: string;
  labels: Preference[];
  departure: string;
  arrival: string;
  duration_minutes: number;
  walk_minutes: number;
  wait_minutes: number;
  transfers: number;
  crowd_level: CrowdCategory;
  mean_crowd_index: number | null;
  unknown_crowd_share: number;
  evidence_quality: string;
  explanation: string[];
  legs: Leg[];
  fare: { total_min: number; total_max: number; total_digital_min: number; total_digital_max: number; tickets_needed: number } | null;
  summary: Bilingual;
}

export interface JourneyResult {
  query: { origin: { name: string }; destination: { name: string }; departure: string; preference: Preference };
  journeys: Journey[];
  notes: string[];
}

export interface AdvisorRow {
  offset_min: number;
  departure: string;
  available: boolean;
  board_time?: string;
  arrival?: string;
  duration_min?: number;
  crowd_level?: CrowdCategory;
  routes?: string[];
}

export interface Arrival {
  route_id: string;
  route: string;
  towards: string;
  eta: string;
  eta_min: number;
  basis: "live_estimate" | "live_estimate_typical_running_time" | "scheduled";
  vehicle_id: string | null;
  delay_min: number | null;
}

export interface ArrivalsBoard {
  stop_id: string;
  stop: string;
  live_feed: boolean;
  arrivals: Arrival[];
  note: string | null;
  summary: Bilingual;
}

export interface Vehicle {
  vehicle_id: string;
  route_id: string;
  route: string | null;
  lat: number;
  lon: number;
  speed_kmh: number | null;
  age_s: number;
  stale: boolean;
  next_stop: string | null;
  delay_min: number | null;
}

export interface LegRef {
  route_id: string;
  board_stop_id: string;
  alight_stop_id: string;
}

export interface QuoteLeg {
  route: string;
  board_stop: string;
  alight_stop: string;
  distance_km: number;
  bus_type: "ac" | "non_ac";
  digital_fare_per_passenger: number;
  leg_total: number;
  note: string | null;
}

export interface Quote {
  legs: QuoteLeg[];
  passengers: number;
  women_free: number;
  total: number;
  summary: Bilingual;
  watermark: Bilingual;
}

export interface DemoTicket {
  ticket_id: string;
  issued_at: string;
  valid_until: string;
  passengers: number;
  women_free: number;
  total: number;
  legs: { route: string; board_stop: string; alight_stop: string; bus_type: string; leg_total: number }[];
  qr_payload: string;
  watermark: Bilingual;
}

export interface Hotspot {
  segment_id: string;
  route: string | null;
  from_stop: string;
  to_stop: string;
  lat: number;
  lon: number;
  to_lat: number;
  to_lon: number;
  category: CrowdCategory;
  dpd: number;
  support: number;
}

export interface DayBucket {
  time: string;
  category: CrowdCategory;
  mean_crowd_index: number | null;
  busiest_segment: string | null;
}
