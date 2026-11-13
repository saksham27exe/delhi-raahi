/** On-device storage (no accounts in v1). Every access is guarded: private mode or blocked
 * storage must never break the app — it just forgets. */
import type { DemoTicket, Lang, Place } from "./types";

export interface SavedPlace { id: string; label: string; place: Place }
export interface RecentTrip { from: { label: string; place: Place }; to: { label: string; place: Place }; at: string }
export interface Settings { lang: Lang; theme: "system" | "light" | "dark"; largeText: boolean; highContrast: boolean }

const KEY = { tickets: "dr.tickets", places: "dr.places", recent: "dr.recent", settings: "dr.settings" };
export const DEFAULT_SETTINGS: Settings = { lang: "en", theme: "system", largeText: false, highContrast: false };

function read<T>(k: string, fallback: T): T {
  try {
    const v = localStorage.getItem(k);
    return v ? (JSON.parse(v) as T) : fallback;
  } catch {
    return fallback;
  }
}

function write(k: string, v: unknown): void {
  try {
    localStorage.setItem(k, JSON.stringify(v));
  } catch { /* storage unavailable: keep working in memory */ }
}

export const store = {
  settings: (): Settings => ({ ...DEFAULT_SETTINGS, ...read<Partial<Settings>>(KEY.settings, {}) }),
  saveSettings: (s: Settings) => write(KEY.settings, s),

  tickets: (): DemoTicket[] => read<DemoTicket[]>(KEY.tickets, []),
  addTicket: (t: DemoTicket) => write(KEY.tickets, [t, ...store.tickets()].slice(0, 50)),
  removeTicket: (id: string) => write(KEY.tickets, store.tickets().filter((t) => t.ticket_id !== id)),

  places: (): SavedPlace[] => read<SavedPlace[]>(KEY.places, []),
  savePlace: (p: SavedPlace) => write(KEY.places, [p, ...store.places().filter((x) => x.id !== p.id)].slice(0, 20)),
  removePlace: (id: string) => write(KEY.places, store.places().filter((x) => x.id !== id)),

  recent: (): RecentTrip[] => read<RecentTrip[]>(KEY.recent, []),
  addRecent: (r: RecentTrip) => {
    const key = (x: RecentTrip) => x.from.label + "→" + x.to.label;
    write(KEY.recent, [r, ...store.recent().filter((x) => key(x) !== key(r))].slice(0, 8));
  },
  clearRecent: () => write(KEY.recent, []),
};
