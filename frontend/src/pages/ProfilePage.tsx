import { useState } from "react";
import { useTranslation } from "react-i18next";
import { StopPicker, type PickedPlace } from "../components/StopPicker";
import { useSettings } from "../state";
import { store } from "../storage";
import type { Lang } from "../types";

export function ProfilePage() {
  const { t } = useTranslation();
  const { settings, update } = useSettings();
  const [places, setPlaces] = useState(() => store.places());
  const [label, setLabel] = useState("");
  const [picked, setPicked] = useState<PickedPlace | null>(null);

  const save = (e: React.FormEvent) => {
    e.preventDefault();
    if (!picked || !label.trim()) return;
    store.savePlace({ id: label.trim().toLowerCase(), label: label.trim(), place: picked.place });
    setPlaces(store.places()); setLabel(""); setPicked(null);
  };

  return (
    <div className="stack">
      <h1>{t("profile.title")}</h1>

      <section className="card stack" aria-labelledby="set-h">
        <h2 id="set-h" style={{ margin: 0 }} className="sr-only">{t("profile.title")}</h2>
        <div className="stack" style={{ gap: 4 }}>
          <span className="field" id="lang-l">{t("profile.language")}</span>
          <div className="seg" role="group" aria-labelledby="lang-l">
            {([["en", "English"], ["hi", "हिंदी"]] as [Lang, string][]).map(([k, v]) => (
              <button key={k} type="button" lang={k} aria-pressed={settings.lang === k} onClick={() => update({ lang: k })}>{v}</button>
            ))}
          </div>
        </div>
        <div className="stack" style={{ gap: 4 }}>
          <span className="field" id="theme-l">{t("profile.theme")}</span>
          <div className="seg" role="group" aria-labelledby="theme-l">
            {(["system", "light", "dark"] as const).map((k) => (
              <button key={k} type="button" aria-pressed={settings.theme === k} onClick={() => update({ theme: k })}>{t(`profile.${k}`)}</button>
            ))}
          </div>
        </div>
        <label className="row between">
          <span>{t("profile.largeText")}</span>
          <input type="checkbox" role="switch" checked={settings.largeText} onChange={(e) => update({ largeText: e.target.checked })}
            style={{ width: 24, height: 24 }} />
        </label>
        <label className="row between">
          <span>{t("profile.highContrast")}</span>
          <input type="checkbox" role="switch" checked={settings.highContrast} onChange={(e) => update({ highContrast: e.target.checked })}
            style={{ width: 24, height: 24 }} />
        </label>
      </section>

      <section className="card stack" aria-labelledby="pl-h">
        <h2 id="pl-h" style={{ margin: 0 }}>{t("profile.places")}</h2>
        {places.length === 0 && <p className="muted" style={{ margin: 0 }}>{t("profile.noPlaces")}</p>}
        <ul className="stack" style={{ listStyle: "none", padding: 0, margin: 0, gap: 6 }}>
          {places.map((p) => (
            <li key={p.id} className="row between">
              <span><strong>{p.label}</strong> <span className="muted small">{p.place.type === "stop_name" ? p.place.name : ""}</span></span>
              <button className="btn ghost" onClick={() => { store.removePlace(p.id); setPlaces(store.places()); }}>{t("profile.remove")}</button>
            </li>
          ))}
        </ul>
        <form className="stack" onSubmit={save} aria-label={t("profile.savePlace")}>
          <label className="field">{t("profile.label")}
            <input type="text" value={label} onChange={(e) => setLabel(e.target.value)} maxLength={30} /></label>
          <StopPicker label={t("plan.to")} placeholder={t("plan.toPh")} value={picked} onChange={setPicked} />
          <button className="btn" type="submit" disabled={!picked || !label.trim()}>{t("profile.save")}</button>
        </form>
        <button className="btn ghost" onClick={() => store.clearRecent()}>{t("profile.clearRecent")}</button>
      </section>

      <section className="card stack" aria-labelledby="ab-h">
        <h2 id="ab-h" style={{ margin: 0 }}>{t("profile.about")}</h2>
        <p style={{ margin: 0 }}>{t("profile.aboutText")}</p>
        <p className="muted small" style={{ margin: 0 }}>{t("profile.privacy")}</p>
      </section>
    </div>
  );
}
