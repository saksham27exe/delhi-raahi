import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate, useParams } from "react-router-dom";
import { BookingDialog } from "../components/BookingDialog";
import { MapView, type MapLine } from "../components/MapView";
import { CrowdBadge, Empty } from "../components/ui";
import { usePlan } from "../state";
import type { CrowdCategory } from "../types";
import { CrowdStrip, fareText } from "./PlanPage";

export function JourneyPage() {
  const { t, i18n } = useTranslation();
  const { idx } = useParams();
  const nav = useNavigate();
  const { plan } = usePlan();
  const [booking, setBooking] = useState(false);
  const lang = i18n.language === "hi" ? "hi" : "en";
  const j = plan.result?.journeys[Number(idx)];

  if (!j) {
    return <div className="stack"><h1>{t("journey.title")}</h1>
      <Empty title={t("plan.noResults")}><Link className="btn" to="/">{t("common.back")}</Link></Empty></div>;
  }

  const lines: MapLine[] = j.legs.flatMap((l, li) => l.segments.map((s, si) => ({
    id: `${li}-${si}`, points: [[s.lat, s.lon], [s.to_lat, s.to_lon]] as [number, number][],
    category: (s.crowd?.category ?? "UNKNOWN") as CrowdCategory,
    label: `${l.route}: ${s.from_stop} → ${s.to_stop} · ${t(`crowd.${s.crowd?.category ?? "UNKNOWN"}`)}`,
  })));

  return (
    <div className="stack">
      <div className="row between">
        <h1 style={{ margin: 0 }}>{t("journey.title")}</h1>
        <button className="btn ghost" onClick={() => nav(-1)}>{t("common.back")}</button>
      </div>
      <section className="card stack">
        <div className="row between">
          <strong style={{ fontSize: "1.2em" }}>{j.departure} → {j.arrival}</strong>
          <span>{t("plan.duration", { min: Math.round(j.duration_minutes) })}</span>
        </div>
        <p style={{ margin: 0 }}>{j.summary[lang]}</p>
        <CrowdStrip j={j} />
        <div className="row between">
          <CrowdBadge level={j.crowd_level} />
          {fareText(j) && <strong>{t("plan.fare")} {fareText(j)}</strong>}
        </div>
        <div className="row">
          <button className="btn primary" onClick={() => setBooking(true)}>{t("journey.ticket")}</button>
          <Link className="btn" to={`/live?stop=${encodeURIComponent(j.legs[0].board_stop_id)}&route=${encodeURIComponent(j.legs[0].route_id)}`}>
            {t("journey.track")}
          </Link>
        </div>
      </section>

      <MapView lines={lines} label={t("journey.crowdAlong")} height={300} />

      <section className="card">
        <ol className="timeline">
          {j.legs.map((l, i) => (
            <li key={i}>
              {i > 0 && <p className="muted small" style={{ marginTop: 0 }}>{t("journey.change")}</p>}
              <h3><span className="route-pill">{l.route}</span> <span className="muted small">{l.route_desc}</span></h3>
              <p style={{ margin: "4px 0" }}><strong>{l.departure}</strong> {t("journey.board")} {l.board_stop}</p>
              <p className="muted small" style={{ margin: "4px 0" }}>
                {t("journey.stopsCount", { count: l.stops })} · {Math.round(l.in_vehicle_minutes)} {t("common.min")}
                {l.fare && <> · {l.fare.distance_km} {t("common.km")} · {t(`journey.busType.${l.fare.bus_type}`)}</>}
              </p>
              <details>
                <summary className="small">{t("journey.crowdAlong")}</summary>
                <ul className="stack" style={{ listStyle: "none", padding: 0, margin: "8px 0", gap: 6 }}>
                  {l.segments.map((s) => (
                    <li key={s.segment_id} className="row between small" style={{ border: 0, padding: 0, margin: 0 }}>
                      <span>{s.depart} · {s.from_stop} → {s.to_stop}</span>
                      <CrowdBadge level={s.crowd?.category ?? "UNKNOWN"} short />
                    </li>
                  ))}
                </ul>
              </details>
              <p style={{ margin: "4px 0" }}><strong>{l.arrival}</strong> {t("journey.alight")} {l.alight_stop}</p>
              {l.fare?.note && <p className="notice warn small">{l.fare.note}</p>}
            </li>
          ))}
        </ol>
        <p className="muted small">{t("journey.scheduled")}</p>
      </section>

      {j.explanation.length > 0 && (
        <section className="card">
          <h2 style={{ marginTop: 0 }}>{t("journey.why")}</h2>
          <ul>{j.explanation.map((e) => <li key={e}>{e}</li>)}</ul>
          <p className="muted small">{t("crowd.disclaimer")} {t("crowd.evidence", { level: j.evidence_quality })}</p>
        </section>
      )}
      {booking && <BookingDialog legs={j.legs} onClose={() => setBooking(false)} />}
    </div>
  );
}
