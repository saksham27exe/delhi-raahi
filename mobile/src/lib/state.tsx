import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { setApiBase } from "./api";
import i18n from "./i18n";
import { DEFAULT_SETTINGS, store, type Settings } from "./storage";
import { useTheme, type Theme } from "./theme";
import type { JourneyResult, PickedPlace, Preference } from "./types";

export interface Trip {
  from: PickedPlace | null; to: PickedPlace | null;
  date: string | null; time: string | null;        // null = leave now
  preference: Preference; result: JourneyResult | null; selected: number;
}

interface Ctx {
  settings: Settings; ready: boolean; theme: Theme; update: (p: Partial<Settings>) => void;
  trip: Trip; setTrip: (p: Partial<Trip>) => void;
}

const AppCtx = createContext<Ctx | null>(null);

export function AppProvider({ children }: { children: ReactNode }) {
  const [settings, setSettings] = useState<Settings>(DEFAULT_SETTINGS);
  const [ready, setReady] = useState(false);
  const [trip, setTripState] = useState<Trip>({ from: null, to: null, date: null, time: null, preference: "balanced", result: null, selected: 0 });
  const theme = useTheme(settings.theme);

  useEffect(() => { store.settings().then((s) => { setSettings(s); setReady(true); }); }, []);
  useEffect(() => {
    i18n.changeLanguage(settings.lang);
    setApiBase(settings.apiBase || null);
    if (ready) store.saveSettings(settings);
  }, [settings, ready]);

  const value = useMemo<Ctx>(() => ({
    settings, ready, theme,
    update: (p) => setSettings((s) => ({ ...s, ...p })),
    trip, setTrip: (p) => setTripState((s) => ({ ...s, ...p })),
  }), [settings, ready, theme, trip]);
  return <AppCtx.Provider value={value}>{children}</AppCtx.Provider>;
}

export function useApp() {
  const c = useContext(AppCtx);
  if (!c) throw new Error("AppProvider missing");
  return c;
}
