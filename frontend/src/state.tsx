import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { delhiNow } from "./api";
import i18n from "./i18n";
import { store, type Settings } from "./storage";
import type { JourneyResult, Preference } from "./types";
import type { PickedPlace } from "./components/StopPicker";

// ---- settings (language, theme, accessibility) ------------------------------------------
const SettingsCtx = createContext<{ settings: Settings; update: (p: Partial<Settings>) => void } | null>(null);

export function SettingsProvider({ children }: { children: ReactNode }) {
  const [settings, setSettings] = useState<Settings>(() => store.settings());
  useEffect(() => {
    const el = document.documentElement;
    el.lang = settings.lang;
    if (settings.theme === "system") el.removeAttribute("data-theme"); else el.dataset.theme = settings.theme;
    el.dataset.largeText = String(settings.largeText);
    el.dataset.contrast = settings.highContrast ? "high" : "normal";
    i18n.changeLanguage(settings.lang);
    store.saveSettings(settings);
  }, [settings]);
  const value = useMemo(() => ({ settings, update: (p: Partial<Settings>) => setSettings((s) => ({ ...s, ...p })) }), [settings]);
  return <SettingsCtx.Provider value={value}>{children}</SettingsCtx.Provider>;
}

export function useSettings() {
  const c = useContext(SettingsCtx);
  if (!c) throw new Error("SettingsProvider missing");
  return c;
}

// ---- trip planning state (survives switching tabs) ----------------------------------------
export interface PlanState {
  from: PickedPlace | null; to: PickedPlace | null; date: string; time: string; useNow: boolean;
  preference: Preference; result: JourneyResult | null;
}

const PlanCtx = createContext<{ plan: PlanState; setPlan: (p: Partial<PlanState>) => void } | null>(null);

export function PlanProvider({ children }: { children: ReactNode }) {
  const now = delhiNow();
  const [plan, set] = useState<PlanState>({
    from: null, to: null, date: now.date, time: now.time, useNow: true, preference: "balanced", result: null,
  });
  const value = useMemo(() => ({ plan, setPlan: (p: Partial<PlanState>) => set((s) => ({ ...s, ...p })) }), [plan]);
  return <PlanCtx.Provider value={value}>{children}</PlanCtx.Provider>;
}

export function usePlan() {
  const c = useContext(PlanCtx);
  if (!c) throw new Error("PlanProvider missing");
  return c;
}
