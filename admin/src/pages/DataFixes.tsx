import { useState } from "react";
import { api, can } from "../api";
import { useAuth } from "../auth";
import { Badge, Button, Card, Empty, ErrorNote, Field, fmtDate, PageHead, Spinner, useAction, useLoad } from "../ui";

interface Alias { alias: string; stop_name: string; created_by: string; created_at: string }
interface Override { route: string; bus_type: string; note: string | null; created_by: string; created_at: string }
interface RouteHit { route: string; directions: string[]; estimate: string | null; ac_share: number | null; override: string | null }

const BT: Record<string, string> = { ac: "AC", non_ac: "Non-AC", mixed: "AC / non-AC", unknown: "Unknown" };

export function DataFixes() {
  const { user } = useAuth();
  const agent = can(user, "agent");
  const { run, busy } = useAction();
  const al = useLoad(() => api.get<{ aliases: Alias[] }>("/data/aliases"));
  const ov = useLoad(() => api.get<{ overrides: Override[] }>("/data/bus-types"));

  // alias form
  const [alias, setAlias] = useState("");
  const [stopQ, setStopQ] = useState("");
  const [stops, setStops] = useState<{ name: string; routes_served: number }[]>([]);
  const [stop, setStop] = useState("");
  const findStop = async (q: string) => {
    setStopQ(q); setStop("");
    if (q.trim().length < 2) { setStops([]); return; }
    try { setStops((await api.get<{ results: { name: string; routes_served: number }[] }>("/lookup/stops", { q })).results); } catch { setStops([]); }
  };
  // bus type form
  const [routeQ, setRouteQ] = useState("");
  const [routes, setRoutes] = useState<RouteHit[]>([]);
  const [note, setNote] = useState("");
  const findRoute = async (q: string) => {
    setRouteQ(q);
    if (!q.trim()) { setRoutes([]); return; }
    try { setRoutes((await api.get<{ results: RouteHit[] }>("/lookup/routes", { q })).results.slice(0, 8)); } catch { setRoutes([]); }
  };
  const setType = (route: string, bus_type: string) => run(async () => {
    ov.setData(await api.post<{ overrides: Override[] }>("/data/bus-types", { route, bus_type, note: note || null }));
    await findRoute(routeQ);
  }, `Bus ${route} set to ${BT[bus_type]}`);

  return (
    <>
      <PageHead title="Data fixes" sub="Corrections that take effect in the app straight away, without retraining or a new release." />
      <div className="grid grid-2">
        <Card title="Stop search aliases">
          <p className="muted small" style={{ marginTop: 0 }}>Other names people use for a stop. Searching the alias in the app finds the stop first.</p>
          {agent && (
            <div className="stack">
              <Field label="Other name"><input value={alias} onChange={(e) => setAlias(e.target.value)} placeholder="e.g. Safdarjung hospital, SJ Hospital" /></Field>
              <Field label="Stop it means"><input value={stopQ} onChange={(e) => void findStop(e.target.value)} placeholder="Search the stop" /></Field>
              {stops.length > 0 && !stop && (
                <div className="row gap wrap">{stops.slice(0, 6).map((s) => <Button key={s.name} small onClick={() => { setStop(s.name); setStopQ(s.name); }}>{s.name} <span className="muted">· {s.routes_served} routes</span></Button>)}</div>
              )}
              <div><Button kind="primary" disabled={busy || alias.trim().length < 2 || !stop}
                onClick={() => void run(async () => { al.setData(await api.post<{ aliases: Alias[] }>("/data/aliases", { alias, stop_name: stop })); setAlias(""); setStop(""); setStopQ(""); setStops([]); }, "Alias added")}>Add alias</Button></div>
            </div>
          )}
          <ErrorNote error={al.error} />
          {al.loading && !al.data ? <Spinner /> : !al.data?.aliases.length ? <Empty>No aliases yet.</Empty> : (
            <div className="table-wrap" style={{ marginTop: 12 }}><table>
              <thead><tr><th>Alias</th><th>Stop</th><th>Added</th><th /></tr></thead>
              <tbody>{al.data.aliases.map((a) => (
                <tr key={a.alias}><td><strong>{a.alias}</strong></td><td>{a.stop_name}</td><td className="muted small">{fmtDate(a.created_at)}<br />{a.created_by}</td>
                  <td className="num">{agent && <Button small kind="danger" onClick={() => void run(async () => { al.setData(await api.del<{ aliases: Alias[] }>(`/data/aliases/${encodeURIComponent(a.alias)}`)); }, "Removed")}>Remove</Button>}</td></tr>
              ))}</tbody>
            </table></div>
          )}
        </Card>

        <Card title="AC / non-AC corrections">
          <p className="muted small" style={{ marginTop: 0 }}>Each bus number's tag is estimated from its share of AC e-tickets. Set it here when the estimate is wrong.</p>
          {agent && (
            <div className="stack">
              <Field label="Bus number"><input value={routeQ} onChange={(e) => void findRoute(e.target.value)} placeholder="e.g. 534" /></Field>
              <Field label="Note (optional)"><input value={note} onChange={(e) => setNote(e.target.value)} placeholder="e.g. depot confirmed all-AC fleet" /></Field>
              {routes.map((r) => (
                <div key={r.route} className="row gap wrap between" style={{ borderTop: "1px solid var(--line)", paddingTop: 8 }}>
                  <div><strong>Bus {r.route}</strong> <span className="muted small">estimate {BT[r.estimate ?? "unknown"]}{r.ac_share != null ? ` (${Math.round(r.ac_share * 100)}% AC tickets)` : ""}</span>
                    {r.override && <> · <Badge tone="accent">set to {BT[r.override]}</Badge></>}</div>
                  <div className="row gap">{["ac", "non_ac", "mixed"].map((t) => <Button key={t} small kind={r.override === t ? "primary" : "plain"} disabled={busy} onClick={() => void setType(r.route, t)}>{BT[t]}</Button>)}</div>
                </div>
              ))}
            </div>
          )}
          <ErrorNote error={ov.error} />
          {ov.loading && !ov.data ? <Spinner /> : !ov.data?.overrides.length ? <Empty>No corrections yet.</Empty> : (
            <div className="table-wrap" style={{ marginTop: 12 }}><table>
              <thead><tr><th>Bus</th><th>Set to</th><th>Note</th><th /></tr></thead>
              <tbody>{ov.data.overrides.map((o) => (
                <tr key={o.route}><td><strong>{o.route}</strong></td><td><Badge tone="accent">{BT[o.bus_type]}</Badge></td>
                  <td className="small">{o.note ?? ""}<div className="muted">{o.created_by} · {fmtDate(o.created_at)}</div></td>
                  <td className="num">{agent && <Button small kind="danger" onClick={() => void run(async () => { ov.setData(await api.del<{ overrides: Override[] }>(`/data/bus-types/${encodeURIComponent(o.route)}`)); }, "Back to the estimate")}>Remove</Button>}</td></tr>
              ))}</tbody>
            </table></div>
          )}
        </Card>
      </div>
    </>
  );
}
