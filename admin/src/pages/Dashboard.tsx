import { Link, useNavigate } from "react-router-dom";
import { api, can } from "../api";
import { useAuth } from "../auth";
import { DailyBars, fillDays, RankBars } from "../charts";
import { ago, Badge, Button, Card, Empty, ErrorNote, fmtNum, fmtRs, fmtTime, PageHead, Spinner, Stat, useLoad } from "../ui";
import { KIND } from "./support/common";

type Err = { error: string };
interface Overview {
  generated_at: string;
  system: {
    components: Record<string, boolean>; load_errors: Record<string, string>; load_seconds: number; accounts_store: string | null;
    email: { configured: boolean; support_inbox_set: boolean };
    live: { live: boolean; vehicles: number; fresh_vehicles: number; matched_to_route: number; last_success: string | null; last_error: string | null } | Err | null;
    voice: { model_file: string; installed: boolean; loaded: boolean };
  };
  users: { users: number; users_7d: number; disabled: number; tickets: number; passes: number; tickets_7d: number; voided: number; demo_topups: number; demo_spent: number;
    per_day: { day: string; users: number; tickets: number; passes: number }[] } | Err | null;
  support: { open: number; new: number; urgent_open: number; unassigned_open: number; resolved: number; by_kind: Record<string, number>;
    complaints_by_route: { route: string; n: number }[]; per_day: { day: string; n: number }[]; avg_first_reply_hours: number | null; avg_resolve_hours: number | null } | Err | null;
  alerts: { live: number; scheduled: number; live_titles: { id: number; title: string; severity: string }[] };
  model: { version: string; training_period: string[]; route_wape: number | null; route_wape_baseline: number | null; interval_nominal: number | null;
    interval_coverage: number | null; segment_f1: number | null; segment_f1_baseline: number | null } | null;
  data: { routes: number | null; stops: number | null };
}
interface Hotspot { segment_id: string; route: string; from_stop: string; to_stop: string; category: string; crowd_index: number }

const ok = <T,>(x: T | Err | null): x is T => !!x && !(x as Err).error;
const COMP: Record<string, string> = { gtfs_network: "Bus network (GTFS)", crowd_model: "Crowd model", fares: "Fares & AC tags", demo_tickets: "Demo tickets",
  accounts: "Accounts", support_desk: "Support desk" };
const pct = (x: number | null | undefined) => (x == null ? "—" : `${Math.round(x * 100)}%`);

function nowDelhi() {
  const p = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Kolkata", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false }).formatToParts(new Date());
  const g = (t: string) => p.find((x) => x.type === t)?.value ?? "";
  return { date: `${g("year")}-${g("month")}-${g("day")}`, time: `${g("hour") === "24" ? "00" : g("hour")}:${Number(g("minute")) < 30 ? "00" : "30"}` };
}

export function Dashboard() {
  const { user } = useAuth();
  const nav = useNavigate();
  const { data: o, error, loading, reload } = useLoad(() => api.get<Overview>("/overview"));
  const when = nowDelhi();
  const hot = useLoad(() => api.get<{ segments: Hotspot[] }>("/api/v1/crowd/hotspots", { ...when, limit: 8 }));
  const recent = useLoad(() => (can(user, "agent") ? api.get<{ entries: { id: number; at: string; admin_email: string; action: string; target: string }[] }>("/audit", { limit: 8 }) : Promise.resolve({ entries: [] })));

  if (loading && !o) return <Spinner />;
  if (!o) return <ErrorNote error={error} />;
  const u = ok(o.users) ? o.users : null;
  const sp = ok(o.support) ? o.support : null;
  const live = ok(o.system.live) ? o.system.live : null;
  const errors = Object.entries(o.system.load_errors);
  const down = Object.entries(o.system.components).filter(([, v]) => !v).map(([k]) => COMP[k] ?? k);

  return (
    <>
      <PageHead title="Dashboard" sub={`Updated ${fmtTime(o.generated_at)}`} actions={<Button onClick={() => { void reload(); void hot.reload(); void recent.reload(); }}>Refresh</Button>} />

      {(sp?.urgent_open || down.length || errors.length || (live && !live.live) || o.alerts.live) ? (
        <Card title="Needs attention">
          <ul className="timeline">
            {sp && sp.urgent_open > 0 && <li><span className="dot dot-bad" /><Link to="/support?status=open&priority=urgent">{sp.urgent_open} urgent support request{sp.urgent_open > 1 ? "s" : ""}</Link> open</li>}
            {sp && sp.unassigned_open > 0 && <li><span className="dot dot-warn" /><Link to="/support?status=open&assignee=none">{sp.unassigned_open} open request{sp.unassigned_open > 1 ? "s" : ""}</Link> with nobody assigned</li>}
            {live && !live.live && <li><span className="dot dot-bad" />Live bus feed is down{live.last_error ? `: ${live.last_error}` : ""}</li>}
            {down.map((d) => <li key={d}><span className="dot dot-bad" />{d} is not loaded</li>)}
            {errors.map(([k, v]) => <li key={k}><span className="dot dot-bad" /><strong>{k}</strong>: <span className="muted">{v}</span></li>)}
            {o.alerts.live > 0 && <li><span className="dot dot-warn" /><Link to="/alerts?state=live">{o.alerts.live} service alert{o.alerts.live > 1 ? "s" : ""}</Link> showing in the app: {o.alerts.live_titles.map((a) => a.title).join(" · ")}</li>}
          </ul>
        </Card>
      ) : null}

      <div className="stats">
        <Stat label="App users" value={fmtNum(u?.users)} sub={u ? `+${u.users_7d} this week${u.disabled ? ` · ${u.disabled} disabled` : ""}` : ok(o.users) ? "" : "unavailable"} />
        <Stat label="Demo tickets" value={fmtNum(u?.tickets)} sub={u ? `+${u.tickets_7d} this week` : ""} />
        <Stat label="Demo passes" value={fmtNum(u?.passes)} sub={u?.voided ? `${u.voided} voided recently` : ""} />
        <Stat label="Open requests" value={fmtNum(sp?.open)} tone={sp && sp.urgent_open ? "bad" : undefined} sub={sp ? `${sp.new} new · ${sp.urgent_open} urgent` : ""} />
        <Stat label="First reply" value={sp?.avg_first_reply_hours != null ? `${sp.avg_first_reply_hours} h` : "—"} sub={sp?.avg_resolve_hours != null ? `resolved in ${sp.avg_resolve_hours} h on average` : "average, last 30 days"} />
        <Stat label="Live buses" value={fmtNum(live?.fresh_vehicles)} tone={live && !live.live ? "bad" : undefined} sub={live ? `${live.vehicles} tracked · ${live.matched_to_route} on a route` : "feed off"} />
      </div>

      <div className="grid grid-3">
        <Card>{u ? <DailyBars title="New app users per day" unit="new users" data={fillDays(u.per_day, "users")} /> : <Empty>User numbers unavailable.</Empty>}</Card>
        <Card>{u ? <DailyBars title="Demo tickets per day" unit="tickets" data={fillDays(u.per_day, "tickets")} /> : <Empty>Booking numbers unavailable.</Empty>}</Card>
        <Card>{sp ? <DailyBars title="Support requests per day" unit="requests" data={fillDays(sp.per_day, "n")} /> : <Empty>Support desk unavailable.</Empty>}</Card>
      </div>

      <div className="grid grid-2">
        <Card title="Complaints by bus · 30 days">
          {sp && sp.complaints_by_route.length ? <RankBars data={sp.complaints_by_route.map((r) => ({ label: r.route, value: r.n }))}
            onPick={(r) => nav(`/support?status=all&kind=complaint&route=${encodeURIComponent(r)}`)} /> : <Empty>No complaints naming a bus.</Empty>}
        </Card>
        <Card title="Requests by type · 30 days">
          {sp && Object.keys(sp.by_kind).length ? <RankBars data={Object.entries(sp.by_kind).sort((a, b) => b[1] - a[1]).map(([k, n]) => ({ label: KIND[k] ?? k, value: n }))} />
            : <Empty>No requests yet.</Empty>}
        </Card>
      </div>

      <div className="grid grid-2">
        <Card title={`Busiest stretches now · ${when.time}`} actions={<span className="muted small">crowd model forecast</span>}>
          <ErrorNote error={hot.error} />
          {hot.loading && !hot.data ? <Spinner /> : hot.error ? null : !hot.data?.segments.length ? <Empty>No well-supported estimates for this time.</Empty> : (
            <div className="table-wrap"><table>
              <thead><tr><th>Bus</th><th>Stretch</th><th>Crowding</th></tr></thead>
              <tbody>{hot.data.segments.map((h) => (
                <tr key={h.segment_id}><td><strong>{h.route.replace(/(UP|DOWN)$/, "")}</strong></td><td className="small">{h.from_stop} → {h.to_stop}</td>
                  <td><Badge tone={h.category === "VERY_HIGH" ? "bad" : h.category === "HIGH" ? "warn" : "neutral"}>{h.category.replace("_", " ").toLowerCase()}</Badge></td></tr>
              ))}</tbody>
            </table></div>
          )}
        </Card>
        <Card title="System health" actions={<span className="muted small">loaded in {o.system.load_seconds}s</span>}>
          <div className="table-wrap"><table><tbody>
            {Object.entries(o.system.components).map(([k, v]) => (
              <tr key={k}><td>{COMP[k] ?? k}</td><td className="num">{v ? <Badge tone="good">✓ Up</Badge> : <Badge tone="bad">✕ Down</Badge>}</td></tr>
            ))}
            <tr><td>Live bus feed<div className="muted small">{live?.last_success ? `last update ${ago(live.last_success)}` : "no update yet"}</div></td>
              <td className="num">{live?.live ? <Badge tone="good">✓ Live</Badge> : <Badge tone="bad">✕ Down</Badge>}</td></tr>
            <tr><td>Voice understanding model<div className="muted small">{o.system.voice.model_file}</div></td>
              <td className="num">{o.system.voice.loaded ? <Badge tone="good">✓ Loaded</Badge> : o.system.voice.installed ? <Badge tone="warn">! Not loaded</Badge> : <Badge>Not installed</Badge>}</td></tr>
            <tr><td>Email (account & support)</td><td className="num">{o.system.email.configured ? <Badge tone="good">✓ Set up</Badge> : <Badge tone="warn">! Not set up</Badge>}</td></tr>
            <tr><td>Accounts store</td><td className="num"><Badge>{o.system.accounts_store ?? "none"}</Badge></td></tr>
            <tr><td>Network</td><td className="num muted">{fmtNum(o.data.routes)} routes · {fmtNum(o.data.stops)} stops</td></tr>
          </tbody></table></div>
        </Card>
      </div>

      <div className="grid grid-2">
        {o.model && (
          <Card title="Crowd model quality" actions={<span className="muted small">{o.model.version}</span>}>
            <div className="stats" style={{ gridTemplateColumns: "repeat(auto-fit, minmax(140px, 1fr))" }}>
              <Stat label="Route demand error (WAPE)" value={pct(o.model.route_wape)} sub={`last-week baseline ${pct(o.model.route_wape_baseline)} · lower is better`} />
              <Stat label="80% range hit rate" value={pct(o.model.interval_coverage)} sub="service hours, test week" />
              <Stat label="Crowd level F1" value={o.model.segment_f1?.toFixed(2) ?? "—"} sub={`history baseline ${o.model.segment_f1_baseline?.toFixed(2) ?? "—"}`} />
            </div>
            <p className="note" style={{ marginBottom: 0 }}>From October 2026 ONDC e-ticket data (trained {o.model.training_period?.join(" to ")}). Estimates, not head counts.</p>
          </Card>
        )}
        <Card title="Demo money" actions={<span className="muted small">no real payments</span>}>
          <div className="stats" style={{ gridTemplateColumns: "repeat(auto-fit, minmax(140px, 1fr))" }}>
            <Stat label="Demo top-ups" value={fmtRs(u?.demo_topups)} />
            <Stat label="Spent on tickets & passes" value={fmtRs(u?.demo_spent)} />
          </div>
          {can(user, "agent") && <div className="row gap" style={{ marginTop: 12 }}>
            <Button small onClick={() => void api.download("/users/export.csv")}>Export users CSV</Button>
            <Button small onClick={() => void api.download("/support/export.csv")}>Export support CSV</Button>
            {can(user, "owner") && <Button small onClick={() => void api.download("/audit/export.csv")}>Export audit CSV</Button>}
          </div>}
        </Card>
      </div>

      {can(user, "agent") && (
        <Card title="Recent admin activity" actions={<Link to="/audit">All</Link>}>
          {!recent.data?.entries.length ? <Empty>Nothing yet.</Empty> : (
            <ul className="timeline">{recent.data.entries.map((e) => (
              <li key={e.id}><div className="who">{fmtTime(e.at)} · {e.admin_email}</div>{e.action.replace(/_/g, " ")} {e.target && <code>{e.target}</code>}</li>
            ))}</ul>
          )}
        </Card>
      )}
    </>
  );
}
