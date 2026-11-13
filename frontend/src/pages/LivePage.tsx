import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useSearchParams } from "react-router-dom";
import { api } from "../api";
import { MapView, type MapPoint } from "../components/MapView";
import { ErrorBox, Icon, Loading } from "../components/ui";
import type { Arrival, StopHit, Vehicle } from "../types";

const REFRESH_MS = 30_000;

interface StopGroup { name: string; ids: string[]; lat: number; lon: number }

function useInterval(fn: () => void, ms: number, deps: unknown[]) {
  useEffect(() => {
    fn();
    const h = setInterval(() => { if (document.visibilityState === "visible") fn(); }, ms);
    return () => clearInterval(h);
  }, deps); // eslint-disable-line react-hooks/exhaustive-deps
}

function ArrivalRow({ a }: { a: Arrival }) {
  const { t } = useTranslation();
  const m = Math.max(0, Math.round(a.eta_min));
  const delay = a.delay_min == null ? null : Math.round(a.delay_min);
  return (
    <li className="row between" style={{ padding: "10px 0", borderBottom: "1px solid var(--border)", flexWrap: "nowrap" }}>
      <div className="stack" style={{ gap: 2 }}>
        <span><span className="route-pill">{a.route}</span> <span className="small muted">{t("live.towards", { stop: a.towards })}</span></span>
        <span className="small">
          <span className={a.basis === "scheduled" ? "muted" : ""} style={{ fontWeight: 600 }}>{t(`live.basis.${a.basis}`)}</span>
          {delay != null && <span className="muted"> · {delay > 1 ? t("live.delay", { min: delay }) : delay < -1 ? t("live.early", { min: -delay }) : t("live.onTime")}</span>}
        </span>
      </div>
      <div style={{ textAlign: "right" }}>
        <strong>{m === 0 ? t("live.now") : t("live.inMin", { min: m })}</strong>
        <div className="muted small">{a.eta}</div>
      </div>
    </li>
  );
}

export function LivePage() {
  const { t, i18n } = useTranslation();
  const lang = i18n.language === "hi" ? "hi" : "en";
  const [params, setParams] = useSearchParams();
  const routeFilter = params.get("route") ?? undefined;
  const [stop, setStop] = useState<StopGroup | null>(null);
  const [query, setQuery] = useState("");
  const [hits, setHits] = useState<StopHit[]>([]);
  const [nearby, setNearby] = useState<StopGroup[] | null>(null);
  const [arrivals, setArrivals] = useState<Arrival[] | null>(null);
  const [summary, setSummary] = useState<Record<string, string> | null>(null);
  const [liveFeed, setLiveFeed] = useState<boolean | null>(null);
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [updated, setUpdated] = useState<string>("");
  const [err, setErr] = useState<unknown>(null);
  const [me, setMe] = useState<{ lat: number; lon: number } | null>(null);

  // a stop passed in the URL (from "Track this bus")
  useEffect(() => {
    const id = params.get("stop");
    if (!id || stop?.ids.includes(id)) return;
    api.stop(id)
      .then((s) => setStop({ name: s.name, ids: [id], lat: s.lat, lon: s.lon })).catch(() => {});
  }, [params]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (query.trim().length < 2) { setHits([]); return; }
    const ctl = new AbortController();
    const h = setTimeout(() => api.searchStops(query.trim(), ctl.signal).then((r) => setHits(r.results)).catch(() => {}), 220);
    return () => { clearTimeout(h); ctl.abort(); };
  }, [query]);

  const loadArrivals = useCallback(async () => {
    if (!stop) return;
    try {
      const boards = await Promise.all(stop.ids.slice(0, 4).map((id) => api.arrivals(id)));
      const all = boards.flatMap((b) => b.arrivals).filter((a) => !routeFilter || a.route_id === routeFilter);
      all.sort((a, b) => a.eta_min - b.eta_min);
      setArrivals(all.slice(0, 15));
      setSummary(boards[0]?.summary ?? null);
      setLiveFeed(boards.some((b) => b.live_feed));
      setErr(null);
    } catch (e) { setErr(e); }
  }, [stop, routeFilter]);

  const loadVehicles = useCallback(async () => {
    try {
      const bbox = !routeFilter && stop ? [stop.lat - 0.03, stop.lon - 0.03, stop.lat + 0.03, stop.lon + 0.03].join(",") : undefined;
      if (!routeFilter && !bbox) { setVehicles([]); return; }
      const r = await api.vehicles(routeFilter, bbox);
      setVehicles(r.vehicles);
      setLiveFeed((x) => x ?? r.live);
      setUpdated(new Date().toLocaleTimeString(lang === "hi" ? "hi-IN" : "en-IN", { hour: "2-digit", minute: "2-digit" }));
    } catch (e) { setErr(e); }
  }, [stop, routeFilter, lang]);

  useInterval(() => { loadArrivals(); loadVehicles(); }, REFRESH_MS, [loadArrivals, loadVehicles]);

  const findNearby = () => {
    navigator.geolocation?.getCurrentPosition(async (pos) => {
      const { latitude: lat, longitude: lon } = pos.coords;
      setMe({ lat, lon });
      try {
        const r = await api.nearbyStops(lat, lon);
        setNearby(r.results.map((s) => ({ name: `${s.name} · ${s.distance_m} m`, ids: [s.stop_id], lat: s.lat, lon: s.lon })));
      } catch (e) { setErr(e); }
    }, () => setNearby([]));
  };

  const choose = (g: StopGroup) => {
    setStop(g); setArrivals(null); setHits([]); setQuery("");
    const p = new URLSearchParams(params); p.set("stop", g.ids[0]); setParams(p, { replace: true });
  };

  const points: MapPoint[] = [
    ...vehicles.map((v) => ({
      id: v.vehicle_id, lat: v.lat, lon: v.lon, kind: (v.stale ? "stale" : "bus") as MapPoint["kind"],
      label: `${v.route ?? "?"}${v.next_stop ? " → " + v.next_stop : ""}${v.speed_kmh != null ? ` · ${Math.round(v.speed_kmh)} km/h` : ""}`,
    })),
    ...(stop ? [{ id: "stop", lat: stop.lat, lon: stop.lon, kind: "stop" as const, label: stop.name }] : []),
    ...(me ? [{ id: "me", lat: me.lat, lon: me.lon, kind: "me" as const, label: t("plan.myLocation") }] : []),
  ];

  return (
    <div className="stack">
      <h1>{t("live.title")}</h1>
      {liveFeed !== null && (
        <p className={`notice ${liveFeed ? "" : "warn"}`} role="status">{liveFeed ? t("live.liveOn") : t("live.liveOff")}</p>
      )}
      {routeFilter && (
        <div className="row between notice">
          <span>{t("live.filterRoute", { route: arrivals?.[0]?.route ?? vehicles[0]?.route ?? routeFilter })}</span>
          <button className="btn ghost" onClick={() => { const p = new URLSearchParams(params); p.delete("route"); setParams(p); }}>{t("live.clearFilter")}</button>
        </div>
      )}

      <div className="card stack">
        <label className="field" htmlFor="live-q">{t("live.searchStop")}</label>
        <div className="row" style={{ flexWrap: "nowrap" }}>
          <input id="live-q" type="search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder={t("plan.fromPh")} />
          <button className="btn icon-btn" onClick={findNearby} aria-label={t("live.nearby")} title={t("live.nearby")}><Icon name="locate" /></button>
        </div>
        {(hits.length > 0 || (nearby && nearby.length > 0)) && (
          <ul className="stack" style={{ listStyle: "none", padding: 0, margin: 0, gap: 4 }}>
            {(hits.length ? hits.map((h) => ({ name: h.name, ids: h.stop_ids, lat: h.lat, lon: h.lon })) : nearby!).map((g) => (
              <li key={g.name + g.ids[0]}><button className="btn block" style={{ justifyContent: "flex-start" }} onClick={() => choose(g)}>{g.name}</button></li>
            ))}
          </ul>
        )}
        {nearby && nearby.length === 0 && <span className="muted small">{t("plan.locationDenied")}</span>}
      </div>

      <MapView points={points} label={t("live.title")} height={300} fitKey={(stop?.ids[0] ?? "") + (routeFilter ?? "")} />
      {updated && <p className="muted small" style={{ margin: 0 }}>{t("live.vehicles", { count: vehicles.length })} · {t("live.updated", { time: updated })}</p>}

      {err != null && <ErrorBox error={err} onRetry={() => { loadArrivals(); loadVehicles(); }} />}

      <section className="card" aria-labelledby="arr-h">
        <div className="row between">
          <h2 id="arr-h" style={{ margin: 0 }}>{t("live.arrivals")}{stop ? ` · ${stop.name}` : ""}</h2>
          {stop && <button className="btn ghost" onClick={() => { loadArrivals(); loadVehicles(); }}>{t("live.refresh")}</button>}
        </div>
        {!stop && <p className="muted">{t("live.pickStop")}</p>}
        {stop && arrivals === null && <Loading />}
        {summary && <p className="sr-only" aria-live="polite">{summary[lang]}</p>}
        {arrivals && arrivals.length === 0 && <p className="muted">{t("live.noArrivals")}</p>}
        {arrivals && arrivals.length > 0 && (
          <ul style={{ listStyle: "none", padding: 0, margin: 0 }}>
            {arrivals.map((a, i) => <ArrivalRow key={a.route_id + a.eta + i} a={a} />)}
          </ul>
        )}
      </section>
    </div>
  );
}
