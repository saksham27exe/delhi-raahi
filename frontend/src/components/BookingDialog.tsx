import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { api } from "../api";
import { store } from "../storage";
import type { Leg, Quote } from "../types";
import { ErrorBox, Loading } from "./ui";

function Stepper({ label, value, min, max, onChange }: { label: string; value: number; min: number; max: number; onChange: (v: number) => void }) {
  return (
    <div className="row between">
      <span>{label}</span>
      <div className="stepper" role="group" aria-label={label}>
        <button type="button" className="btn icon-btn" aria-label={`${label} −`} disabled={value <= min} onClick={() => onChange(value - 1)}>−</button>
        <output aria-live="polite">{value}</output>
        <button type="button" className="btn icon-btn" aria-label={`${label} +`} disabled={value >= max} onClick={() => onChange(value + 1)}>+</button>
      </div>
    </div>
  );
}

/** Demo booking: the server prices the trip (client prices are never sent), then issues a signed DEMO ticket. */
export function BookingDialog({ legs, onClose }: { legs: Leg[]; onClose: () => void }) {
  const { t, i18n } = useTranslation();
  const nav = useNavigate();
  const ref = useRef<HTMLDialogElement>(null);
  const lang = i18n.language === "hi" ? "hi" : "en";
  const [passengers, setPassengers] = useState(1);
  const [women, setWomen] = useState(0);
  const [types, setTypes] = useState<(string | null)[]>(legs.map((l) => (l.fare?.bus_type === "ac" || l.fare?.bus_type === "non_ac" ? l.fare.bus_type : null)));
  const [quote, setQuote] = useState<Quote | null>(null);
  const [err, setErr] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const refs = legs.map((l) => ({ route_id: l.route_id, board_stop_id: l.board_stop_id, alight_stop_id: l.alight_stop_id }));

  useEffect(() => { ref.current?.showModal(); }, []);
  useEffect(() => {
    let live = true;
    setErr(null);
    api.quote(refs, passengers, Math.min(women, passengers), types).then((q) => live && setQuote(q)).catch((e) => live && setErr(e));
    return () => { live = false; };
  }, [passengers, women, types.join()]); // eslint-disable-line react-hooks/exhaustive-deps

  const confirm = async () => {
    setBusy(true); setErr(null);
    try {
      const tk = await api.book(refs, passengers, Math.min(women, passengers), types);
      store.addTicket(tk);
      ref.current?.close();
      nav("/tickets", { state: { justBooked: tk.ticket_id } });
    } catch (e) { setErr(e); } finally { setBusy(false); }
  };

  return (
    <dialog ref={ref} onClose={onClose} aria-labelledby="bk-h">
      <div className="dlg stack">
        <div className="row between">
          <h2 id="bk-h" style={{ margin: 0 }}>{t("booking.title")}</h2>
          <button className="btn ghost" onClick={() => ref.current?.close()}>{t("common.close")}</button>
        </div>
        <div className="ticket card" style={{ boxShadow: "none" }}>
          <div className="banner" role="note">{t("booking.watermark")}</div>
        </div>
        <Stepper label={t("booking.passengers")} value={passengers} min={1} max={6} onChange={(v) => { setPassengers(v); setWomen((w) => Math.min(w, v)); }} />
        <Stepper label={t("booking.womenFree")} value={women} min={0} max={passengers} onChange={setWomen} />
        <p className="muted small" style={{ margin: 0 }}>{t("booking.concession")}</p>
        {legs.map((l, i) => (
          <div key={i} className="stack" style={{ gap: 4 }}>
            <div className="row between">
              <span><span className="route-pill">{l.route}</span> <span className="small">{l.board_stop} → {l.alight_stop}</span></span>
              {quote?.legs[i] && <strong>₹{quote.legs[i].leg_total}</strong>}
            </div>
            {(l.fare?.bus_type === "mixed" || l.fare?.bus_type === "unknown") && (
              <div className="seg" role="group" aria-label={`${t("booking.busType")} ${l.route}`}>
                {(["non_ac", "ac"] as const).map((bt) => (
                  <button key={bt} type="button" aria-pressed={(types[i] ?? "ac") === bt}
                    onClick={() => setTypes((ts) => ts.map((x, k) => (k === i ? bt : x)))}>{t(`journey.busType.${bt}`)}</button>
                ))}
              </div>
            )}
            {quote?.legs[i]?.note && <span className="notice warn small">{quote.legs[i].note}</span>}
          </div>
        ))}
        {!quote && !err && <Loading />}
        {quote && (
          <div className="row between" aria-live="polite">
            <strong>{t("booking.total")}</strong><strong style={{ fontSize: "1.3em" }}>₹{quote.total}</strong>
          </div>
        )}
        {quote && <p className="muted small" style={{ margin: 0 }}>{quote.summary[lang]}</p>}
        {err != null && <ErrorBox error={err} />}
        <button className="btn primary block" disabled={!quote || busy} onClick={confirm}>
          {busy ? t("booking.getting") : t("booking.confirm")}
        </button>
      </div>
    </dialog>
  );
}
