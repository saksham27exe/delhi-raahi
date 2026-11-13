import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { api, delhiNow } from "../api";
import { DayChart } from "../components/DayChart";
import { MapView, type MapLine } from "../components/MapView";
import { CrowdBadge, ErrorBox, Loading } from "../components/ui";
import type { DayBucket, Hotspot } from "../types";

const TIMES = ["07:00", "08:30", "10:00", "13:00", "17:30", "19:00", "21:00"];

function Hotspots() {
  const { t } = useTranslation();
  const [time, setTime] = useState("08:30");
  const [date] = useState(() => delhiNow().date);
  const [data, setData] = useState<Hotspot[] | null>(null);
  const [err, setErr] = useState<unknown>(null);
  const load = () => {
    setData(null); setErr(null);
    api.hotspots(date, time).then((r) => setData(r.segments)).catch(setErr);
  };
  useEffect(load, [time]); // eslint-disable-line react-hooks/exhaustive-deps
  const lines: MapLine[] = (data ?? []).map((h) => ({
    id: h.segment_id, points: [[h.lat, h.lon], [h.to_lat, h.to_lon]], category: h.category,
    label: `${h.route ?? ""}: ${h.from_stop} → ${h.to_stop} · ${t(`crowd.${h.category}`)}`,
  }));
  return (
    <section className="stack" aria-labelledby="hs-h">
      <h2 id="hs-h">{t("insights.map")}</h2>
      <p className="muted small" style={{ margin: 0 }}>{t("insights.mapHint")}</p>
      <label className="field">{t("plan.time")}
        <select value={time} onChange={(e) => setTime(e.target.value)}>{TIMES.map((x) => <option key={x}>{x}</option>)}</select>
      </label>
      {err != null && <ErrorBox error={err} onRetry={load} />}
      {!data && !err && <Loading />}
      {data && <MapView lines={lines} label={t("insights.map")} height={320} fitKey={time} />}
      {data && data.length > 0 && (
        <details className="card">
          <summary>{t("insights.map")} — top 10</summary>
          <ol className="small">
            {data.slice(0, 10).map((h) => (
              <li key={h.segment_id} style={{ margin: "6px 0" }}>
                <span className="route-pill">{h.route}</span> {h.from_stop} → {h.to_stop} <CrowdBadge level={h.category} short />
              </li>
            ))}
          </ol>
        </details>
      )}
    </section>
  );
}

function RouteProfile() {
  const { t, i18n } = useTranslation();
  const [q, setQ] = useState("");
  const [routes, setRoutes] = useState<{ route_id: string; route: string; description: string }[]>([]);
  const [sel, setSel] = useState<{ route_id: string; route: string } | null>(null);
  const [prof, setProf] = useState<{ buckets: DayBucket[]; quietest: string | null; busiest: string | null } | null>(null);
  const [err, setErr] = useState<unknown>(null);
  useEffect(() => {
    if (!q.trim()) { setRoutes([]); return; }
    const h = setTimeout(() => api.searchRoutes(q.trim()).then((r) => setRoutes(r.results.slice(0, 8))).catch(() => {}), 200);
    return () => clearTimeout(h);
  }, [q]);
  useEffect(() => {
    if (!sel) return;
    setProf(null); setErr(null);
    api.routeDay(sel.route_id, delhiNow().date).then(setProf).catch(setErr);
  }, [sel]);
  return (
    <section className="stack" aria-labelledby="rp-h">
      <h2 id="rp-h">{t("insights.route")}</h2>
      <label className="field" htmlFor="rq">{t("insights.routePh")}</label>
      <input id="rq" type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="717" />
      {routes.length > 0 && !sel && (
        <div className="row">{routes.map((r) => (
          <button key={r.route_id} className="btn" onClick={() => { setSel(r); setRoutes([]); }} title={r.description}>{r.route}</button>
        ))}</div>
      )}
      {sel && (
        <div className="card stack">
          <div className="row between"><span className="route-pill">{sel.route}</span>
            <button className="btn ghost" onClick={() => { setSel(null); setProf(null); }}>{t("common.close")}</button></div>
          {err != null && <ErrorBox error={err} />}
          {!prof && !err && <Loading />}
          {prof && (
            <>
              {prof.quietest && <p style={{ margin: 0 }}>{t("insights.quietest", { t: prof.quietest, b: prof.busiest })}</p>}
              <DayChart buckets={prof.buckets} caption={`${sel.route} · ${i18n.language === "hi" ? "पूरे दिन की भीड़" : "crowding through the day"}`}
                levelText={(c) => t(`crowd.${c}`)} />
            </>
          )}
        </div>
      )}
    </section>
  );
}

function ModelCard() {
  const { t } = useTranslation();
  const [m, setM] = useState<Record<string, any> | null>(null); // eslint-disable-line @typescript-eslint/no-explicit-any
  const [err, setErr] = useState<unknown>(null);
  useEffect(() => { api.modelCard().then(setM).catch(setErr); }, []);
  const n = (x: unknown, d = 2) => (typeof x === "number" ? x.toFixed(d) : "—");
  const pct = (x: unknown) => (typeof x === "number" ? `${(100 * x).toFixed(1)}%` : "—");
  return (
    <section className="stack" aria-labelledby="mc-h">
      <h2 id="mc-h">{t("insights.model")}</h2>
      {err != null && <ErrorBox error={err} />}
      {!m && !err && <Loading />}
      {m && (
        <>
          <div className="card">
            <h3>{t("insights.routeModel")}</h3>
            <table><tbody>
              <tr><th scope="row">{t("insights.mae")} — LightGBM</th><td>{n(m.route_model?.test?.lightgbm?.service_hours?.mae, 3)}</td></tr>
              <tr><th scope="row">{t("insights.mae")} — {t("insights.baseline")}</th><td>{n(m.route_model?.test?.daytype_seasonal_avg?.service_hours?.mae, 3)}</td></tr>
            </tbody></table>
          </div>
          <div className="card">
            <h3>{t("insights.segModel")}</h3>
            <table><tbody>
              <tr><th scope="row">{t("insights.wape")} — model</th><td>{pct(m.segment_model?.model?.wape)}</td></tr>
              <tr><th scope="row">{t("insights.wape")} — {t("insights.baseline")}</th><td>{pct(m.segment_model?.historical_average_baseline?.wape)}</td></tr>
            </tbody></table>
            <p className="muted small">{t("insights.honest")}</p>
          </div>
          <div className="card">
            <h3>{t("insights.fares")}</h3>
            <table><tbody>
              <tr><th scope="row">{t("insights.exact")}</th><td>{pct(m.fare_validation?.all_resolved_tickets?.exact_match)}</td></tr>
              <tr><th scope="row">{t("insights.withinSlab")}</th><td>{pct(m.fare_validation?.all_resolved_tickets?.within_one_slab)}</td></tr>
            </tbody></table>
          </div>
          <div className="card">
            <h3>{t("insights.limitations")}</h3>
            <ul className="small">{(m.limitations as string[]).map((l) => <li key={l}>{l}</li>)}</ul>
          </div>
        </>
      )}
    </section>
  );
}

export function InsightsPage() {
  const { t } = useTranslation();
  return (
    <div className="stack">
      <h1>{t("insights.title")}</h1>
      <p className="notice">{t("crowd.disclaimer")}</p>
      <RouteProfile />
      <Hotspots />
      <ModelCard />
    </div>
  );
}
