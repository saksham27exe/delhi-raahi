import AsyncStorage from "@react-native-async-storage/async-storage";
import type { DemoTicket, Lang, PickedPlace } from "./types";

/** On-device storage (no accounts). Failures never break the app: it just forgets. */
export interface Settings { lang: Lang; theme: "system" | "light" | "dark"; apiBase: string }
export interface SavedPlace { id: string; label: string; icon: "home" | "work" | "star"; value: PickedPlace }
export interface RecentTrip { from: PickedPlace; to: PickedPlace; at: string }

export const DEFAULT_SETTINGS: Settings = { lang: "en", theme: "system", apiBase: "" };
const K = { settings: "dr.settings", tickets: "dr.tickets", places: "dr.places", recent: "dr.recent" };

async function read<T>(k: string, fallback: T): Promise<T> {
  try {
    const v = await AsyncStorage.getItem(k);
    return v ? (JSON.parse(v) as T) : fallback;
  } catch {
    return fallback;
  }
}
async function write(k: string, v: unknown) {
  try { await AsyncStorage.setItem(k, JSON.stringify(v)); } catch { /* ignore */ }
}

export const store = {
  settings: async () => ({ ...DEFAULT_SETTINGS, ...(await read<Partial<Settings>>(K.settings, {})) }),
  saveSettings: (s: Settings) => write(K.settings, s),
  tickets: () => read<DemoTicket[]>(K.tickets, []),
  addTicket: async (t: DemoTicket) => write(K.tickets, [t, ...(await store.tickets())].slice(0, 50)),
  removeTicket: async (id: string) => write(K.tickets, (await store.tickets()).filter((t) => t.ticket_id !== id)),
  places: () => read<SavedPlace[]>(K.places, []),
  savePlace: async (p: SavedPlace) => write(K.places, [p, ...(await store.places()).filter((x) => x.id !== p.id)].slice(0, 12)),
  removePlace: async (id: string) => write(K.places, (await store.places()).filter((x) => x.id !== id)),
  recent: () => read<RecentTrip[]>(K.recent, []),
  addRecent: async (r: RecentTrip) => {
    const key = (x: RecentTrip) => x.from.label + "→" + x.to.label;
    write(K.recent, [r, ...(await store.recent()).filter((x) => key(x) !== key(r))].slice(0, 6));
  },
  clearRecent: () => write(K.recent, []),
};
