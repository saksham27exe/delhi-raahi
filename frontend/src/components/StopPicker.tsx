import { useEffect, useId, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { api } from "../api";
import type { Place, StopHit } from "../types";
import { Icon } from "./ui";

export interface PickedPlace { label: string; place: Place }

/** Accessible combobox (WAI-ARIA 1.2 pattern): type to search stops, arrows + Enter to pick. */
export function StopPicker({ label, placeholder, value, onChange, allowLocation = false }: {
  label: string; placeholder: string; value: PickedPlace | null; onChange: (p: PickedPlace | null) => void; allowLocation?: boolean;
}) {
  const { t } = useTranslation();
  const id = useId();
  const [text, setText] = useState(value?.label ?? "");
  const [hits, setHits] = useState<StopHit[]>([]);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const [locating, setLocating] = useState(false);
  const [locError, setLocError] = useState(false);
  const typed = useRef(false);

  useEffect(() => { setText(value?.label ?? ""); }, [value]);

  useEffect(() => {
    if (!typed.current || text.trim().length < 2) { setHits([]); return; }
    const ctl = new AbortController();
    const h = setTimeout(() => {
      api.searchStops(text.trim(), ctl.signal).then((r) => { setHits(r.results); setOpen(true); setActive(-1); }).catch(() => {});
    }, 220);
    return () => { clearTimeout(h); ctl.abort(); };
  }, [text]);

  const pick = (h: StopHit) => {
    typed.current = false;
    onChange({ label: h.name, place: { type: "stop_name", name: h.name } });
    setOpen(false);
  };

  const locate = () => {
    if (!("geolocation" in navigator)) { setLocError(true); return; }
    setLocating(true); setLocError(false);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setLocating(false);
        const { latitude: lat, longitude: lon } = pos.coords;
        onChange({ label: t("plan.myLocation"), place: { type: "coord", lat, lon, label: t("plan.myLocation") } });
      },
      () => { setLocating(false); setLocError(true); },
      { enableHighAccuracy: true, timeout: 10000 },
    );
  };

  return (
    <div className="stack" style={{ gap: 4 }}>
      <label className="field" htmlFor={id}>{label}</label>
      <div className="row" style={{ flexWrap: "nowrap" }}>
        <div className="picker" style={{ flex: 1 }}>
          <input id={id} type="search" role="combobox" aria-expanded={open && hits.length > 0} aria-controls={id + "-list"}
            aria-autocomplete="list" aria-activedescendant={active >= 0 ? `${id}-o${active}` : undefined}
            placeholder={placeholder} value={text} autoComplete="off"
            onChange={(e) => { typed.current = true; setText(e.target.value); if (!e.target.value) onChange(null); }}
            onBlur={() => setTimeout(() => setOpen(false), 150)}
            onKeyDown={(e) => {
              if (e.key === "ArrowDown") { e.preventDefault(); setOpen(true); setActive((a) => Math.min(a + 1, hits.length - 1)); }
              else if (e.key === "ArrowUp") { e.preventDefault(); setActive((a) => Math.max(a - 1, 0)); }
              else if (e.key === "Enter" && open && hits[Math.max(active, 0)]) { e.preventDefault(); pick(hits[Math.max(active, 0)]); }
              else if (e.key === "Escape") setOpen(false);
            }} />
          {open && hits.length > 0 && (
            <ul id={id + "-list"} role="listbox" aria-label={label}>
              {hits.map((h, i) => (
                <li key={h.name} id={`${id}-o${i}`} role="option" aria-selected={i === active}
                  onMouseDown={(e) => { e.preventDefault(); pick(h); }}>
                  <div>{h.name}</div>
                  <div className="muted small">{h.routes_served} bus routes</div>
                </li>
              ))}
            </ul>
          )}
        </div>
        {allowLocation && (
          <button type="button" className="btn icon-btn" onClick={locate} aria-label={t("plan.myLocation")} title={t("plan.myLocation")}>
            <Icon name="locate" />
          </button>
        )}
      </div>
      {locating && <span className="muted small" role="status">{t("plan.locating")}</span>}
      {locError && <span className="muted small" role="alert">{t("plan.locationDenied")}</span>}
    </div>
  );
}
