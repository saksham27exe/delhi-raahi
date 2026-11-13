import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { api, delhiNow, toIso } from "../api";
import { StopPicker } from "../components/StopPicker";
import { CrowdBadge, Empty, ErrorBox, Icon, Loading } from "../components/ui";
import { usePlan, useSettings } from "../state";
import { store } from "../storage";
import type { AdvisorRow, Journey, Preference } from "../types";

const PREFS: Preference[] = ["fastest", "balanced", "least_crowded"];

export function fareText(j: Journey): string | null {
  if (!j.fare) return null;
  return j.fare.total_digital_min === j.fare.total_digital_max
    ? `₹${j.fare.total_digital_min}` : `₹${j.fare.total_digital_min}–${j.fare.total_digital_max}`;
}

export function CrowdStrip({ j }: { j: Journey }) {
  const segs = j.legs.flatMap((l) => l.segments);
  const total = segs.reduce((a, s) => a + Math.max(s.minutes, 0.5), 0) || 1;
  return (
    <div className="crowdbar" aria-hidden="true">
      {segs.map((s) => (
        <span key={s.segment_id + s.depart} className={`b-${s.crowd?.category ?? "UNKNOWN"}`}
          style={{ width: `${(100 * Math.max(s.minutes, 0.5)) / total}%` }} />
      ))}
    </div>
  );
}

function JourneyCard({ j, idx }: { j: Journey; idx: number }) {
  const { t, i18n } = useTranslation();
  const nav = useNavigate();
  const lang = i18n.language === "hi" ? "hi" : "en";
  return (
    <li>
      <button className="card journey stack" style={{ gap: 8 }} onClick={() => nav(`/journey/${idx}`)}
        aria-label={`${j.summary[lang]} ${t("plan.details")}`}>
        <div className="row between">
          <span className="times">{j.departure} → {j.arrival}</span>
          <span className="muted">{t("plan.duration", { min: Math.round(j.duration_minutes) })}</span>
        </div>
        {j.labels.length > 0 && (
          <div className="row">{j.labels.map((l) => <span key={l} className="tag">{t(`plan.labels.${l}`)}</span>)}</div>
        )}
        <div className="legs">
          {j.legs.map((l, i) => (
            <span key={i} className="row" style={{ gap: 4 }}>
              {i > 0 && <span className="muted" aria-hidden="true">›</span>}
              <span className="route-pill">{l.route}</span>
            </span>
          ))}
          <span className="muted small">· {j.transfers === 0 ? t("plan.transfers_0") : t("plan.transfers_1")}</span>
        </div>
        <CrowdStrip j={j} />
        <div className="row between">
          <CrowdBadge level={j.crowd_level} />
          {fareText(j) && <span><span className="muted small">{t("plan.fare")} </span><strong>{fareText(j)}</strong></span>}
        </div>
        {j.explanation[0] && <span className="muted small">{j.explanation[0]}</span>}
      </button>
    </li>
  );
}

function Advisor() {
  const { t } = useTranslation();
  const { plan } = usePlan();
  const [rows, setRows] = useState<AdvisorRow[] | null>(null);
  const [rec, setRec] = useState<{ offset_min: number; reason: string } | null>(null);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState<unknown>(null);
  const run = async () => {
    if (!plan.from || !plan.to) return;
    setLoading(true); setErr(null);
    try {
      const r = await api.advisor(plan.from.place, plan.to.place, toIso(plan.date, plan.time), plan.preference);
      setRows(r.departures); setRec(r.recommendation);
    } catch (e) { setErr(e); } finally { setLoading(false); }
  };
  return (
    <section className="card stack" aria-labelledby="adv-h">
      <div className="row between">
        <div><h2 id="adv-h" style={{ margin: 0 }}>{t("plan.advisor")}</h2><span className="muted small">{t("plan.advisorHint")}</span></div>
        {!rows && !loading && <button className="btn" onClick={run}>{t("plan.advisor")}</button>}
      </div>
      {loading && <Loading label={t("plan.advisorLoading")} />}
      {err != null && <ErrorBox error={err} onRetry={run} />}
      {rec && <p className="notice" role="status" style={{ margin: 0 }}>{rec.reason}</p>}
      {rows && (
        <div style={{ overflowX: "auto" }}>
          <table>
            <thead><tr><th>{t("plan.advisorCol.leave")}</th><th>{t("plan.advisorCol.trip")}</th><th>{t("plan.advisorCol.crowd")}</th><th>{t("plan.advisorCol.buses")}</th></tr></thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.offset_min} className={rec && rec.offset_min === r.offset_min ? "best" : undefined}>
                  <td>{r.board_time ?? r.departure.slice(11, 16)}<div className="muted small">{r.offset_min > 0 ? "+" : ""}{r.offset_min} {t("common.min")}</div></td>
                  <td>{r.available ? `${Math.round(r.duration_min!)} ${t("common.min")}` : t("plan.nothing")}</td>
                  <td>{r.crowd_level ? <CrowdBadge level={r.crowd_level} short /> : t("plan.nothing")}</td>
                  <td className="small">{r.routes?.join(" › ") ?? t("plan.nothing")}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

export function PlanPage() {
  const { t } = useTranslation();
  const { plan, setPlan } = usePlan();
  const { settings } = useSettings();
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState<unknown>(null);
  const recent = store.recent();
  const saved = store.places();

  const search = async (e?: React.FormEvent) => {
    e?.preventDefault();
    if (!plan.from || !plan.to) { setErr(new Error(t("plan.pickBoth"))); return; }
    const when = plan.useNow ? delhiNow() : { date: plan.date, time: plan.time };
    setLoading(true); setErr(null);
    try {
      const r = await api.journeys(plan.from.place, plan.to.place, toIso(when.date, when.time), plan.preference);
      setPlan({ result: r, date: when.date, time: when.time });
      store.addRecent({ from: plan.from, to: plan.to, at: new Date().toISOString() });
    } catch (ex) { setErr(ex); } finally { setLoading(false); }
  };

  const res = plan.result;
  return (
    <div className="stack">
      <h1>{t("plan.title")}</h1>
      <form className="card stack" onSubmit={search} aria-label={t("plan.title")}>
        <StopPicker label={t("plan.from")} placeholder={t("plan.fromPh")} value={plan.from} allowLocation
          onChange={(p) => setPlan({ from: p, result: null })} />
        <div className="row" style={{ justifyContent: "center", margin: "-6px 0" }}>
          <button type="button" className="btn ghost icon-btn" aria-label={t("plan.swap")} title={t("plan.swap")}
            onClick={() => setPlan({ from: plan.to, to: plan.from, result: null })}><Icon name="swap" /></button>
        </div>
        <StopPicker label={t("plan.to")} placeholder={t("plan.toPh")} value={plan.to}
          onChange={(p) => setPlan({ to: p, result: null })} />
        <fieldset style={{ border: 0, padding: 0, margin: 0 }} className="stack">
          <legend className="field" style={{ fontWeight: 600, fontSize: "0.9em", marginBottom: 4 }}>{t("plan.when")}</legend>
          <div className="seg" role="group" aria-label={t("plan.when")}>
            <button type="button" aria-pressed={plan.useNow} onClick={() => setPlan({ useNow: true })}>{t("plan.now")}</button>
            <button type="button" aria-pressed={!plan.useNow} onClick={() => setPlan({ useNow: false })}>{t("plan.date")} / {t("plan.time")}</button>
          </div>
          {!plan.useNow && (
            <div className="row" style={{ flexWrap: "nowrap" }}>
              <label className="field" style={{ flex: 1 }}>{t("plan.date")}
                <input type="date" value={plan.date} onChange={(e) => setPlan({ date: e.target.value })} /></label>
              <label className="field" style={{ flex: 1 }}>{t("plan.time")}
                <input type="time" value={plan.time} onChange={(e) => setPlan({ time: e.target.value })} /></label>
            </div>
          )}
        </fieldset>
        <div className="stack" style={{ gap: 4 }}>
          <span className="field" id="pref-l">{t("plan.prefer")}</span>
          <div className="seg" role="group" aria-labelledby="pref-l">
            {PREFS.map((p) => (
              <button key={p} type="button" aria-pressed={plan.preference === p} onClick={() => setPlan({ preference: p })}>{t(`plan.${p}`)}</button>
            ))}
          </div>
        </div>
        <button className="btn primary block" type="submit" disabled={loading}>{loading ? t("plan.searching") : t("plan.search")}</button>
      </form>

      {!res && !loading && (saved.length > 0 || recent.length > 0) && (
        <section className="stack">
          {saved.length > 0 && <>
            <h2>{t("plan.saved")}</h2>
            <div className="row">
              {saved.map((s) => (
                <button key={s.id} className="btn" onClick={() => setPlan(plan.from ? { to: { label: s.label, place: s.place } } : { from: { label: s.label, place: s.place } })}>{s.label}</button>
              ))}
            </div>
          </>}
          {recent.length > 0 && <>
            <h2>{t("plan.recent")}</h2>
            <ul className="stack" style={{ listStyle: "none", padding: 0, margin: 0 }}>
              {recent.map((r) => (
                <li key={r.from.label + r.to.label}>
                  <button className="card journey" onClick={() => setPlan({ from: r.from, to: r.to, result: null })}>
                    {r.from.label} → {r.to.label}
                  </button>
                </li>
              ))}
            </ul>
          </>}
        </section>
      )}

      {loading && <Loading label={t("plan.searching")} />}
      {err != null && <ErrorBox error={err} onRetry={plan.from && plan.to ? () => search() : undefined} />}

      {res && !loading && (
        <section className="stack" aria-labelledby="res-h" aria-live="polite">
          <h2 id="res-h">{res.journeys.length ? t("plan.results", { count: res.journeys.length }) : t("plan.noResults")}</h2>
          {res.journeys.length === 0 && <Empty title={t("plan.noResults")} hint={t("plan.noResultsHint")} />}
          <ol className="stack" style={{ listStyle: "none", padding: 0, margin: 0 }}>
            {res.journeys.map((j, i) => <JourneyCard key={j.journey_id} j={j} idx={i} />)}
          </ol>
          {res.journeys.length > 0 && <p className="notice">{t("crowd.disclaimer")} {res.notes.join(" ")}</p>}
          {res.journeys.length > 0 && <Advisor key={res.query.departure + settings.lang} />}
        </section>
      )}
    </div>
  );
}
