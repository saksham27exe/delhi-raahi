import { useState } from "react";
import { api, can } from "../api";
import { useAuth } from "../auth";
import { Badge, Button, Card, Empty, ErrorNote, Field, fmtTime, Modal, PageHead, Spinner, useAction, useConfirm, useLoad } from "../ui";

interface Stop { id: string; name: string }
interface Alert {
  id: number; severity: "info" | "warning" | "critical"; title_en: string; title_hi: string | null; body_en: string | null; body_hi: string | null;
  routes: string[]; stops: Stop[]; starts_at: string; ends_at: string | null; state: "live" | "scheduled" | "ended"; scope: string; created_by: string;
}
type Form = { severity: Alert["severity"]; title_en: string; title_hi: string; body_en: string; body_hi: string; routes: string; stops: Stop[]; starts_at: string; ends_at: string };

const SEV_TONE = { info: "info", warning: "warn", critical: "bad" } as const;
const STATE_TONE = { live: "good", scheduled: "info", ended: "neutral" } as const;

// <input type="datetime-local"> works in the browser's time zone (Delhi for our admins).
const toLocal = (iso?: string | null) => {
  if (!iso) return "";
  const d = new Date(iso);
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
};
const fromLocal = (v: string) => (v ? new Date(v).toISOString() : null);
const empty = (): Form => ({ severity: "warning", title_en: "", title_hi: "", body_en: "", body_hi: "", routes: "", stops: [], starts_at: toLocal(new Date().toISOString()), ends_at: "" });

export function Alerts() {
  const { user } = useAuth();
  const agent = can(user, "agent");
  const [state, setState] = useState("");
  const { data, error, loading, reload } = useLoad(() => api.get<{ alerts: Alert[] }>("/alerts", { state }), [state]);
  const { run, busy } = useAction();
  const confirm = useConfirm();
  const [edit, setEdit] = useState<{ id?: number; f: Form } | null>(null);
  const [stopQ, setStopQ] = useState("");
  const [stopHits, setStopHits] = useState<{ name: string; stop_ids: string[] }[]>([]);

  const open = (a?: Alert) => setEdit(a ? {
    id: a.id, f: { severity: a.severity, title_en: a.title_en, title_hi: a.title_hi ?? "", body_en: a.body_en ?? "", body_hi: a.body_hi ?? "",
      routes: a.routes.join(", "), stops: a.stops, starts_at: toLocal(a.starts_at), ends_at: toLocal(a.ends_at) },
  } : { f: empty() });
  const set = (k: keyof Form, v: unknown) => setEdit((e) => e && { ...e, f: { ...e.f, [k]: v } });
  const save = () => edit && run(async () => {
    const f = edit.f;
    const body = { ...f, routes: f.routes.split(/[,\s]+/).filter(Boolean), starts_at: fromLocal(f.starts_at), ends_at: fromLocal(f.ends_at) };
    if (edit.id) await api.patch(`/alerts/${edit.id}`, body); else await api.post("/alerts", body);
    setEdit(null);
    await reload();
  }, edit.id ? "Alert updated" : "Alert published");
  const searchStops = async (q: string) => {
    setStopQ(q);
    if (q.trim().length < 2) { setStopHits([]); return; }
    try { setStopHits((await api.get<{ results: { name: string; stop_ids: string[] }[] }>("/lookup/stops", { q })).results); } catch { setStopHits([]); }
  };

  return (
    <>
      <PageHead title="Service alerts" sub="Notices shown in the app: on Home for everyone, or on trips and live buses for chosen bus numbers and stops."
        actions={agent && <Button kind="primary" onClick={() => open()}>New alert</Button>} />
      <Card>
        <div className="row gap wrap" style={{ marginBottom: 12 }}>
          {[["", "All"], ["live", "Live"], ["scheduled", "Scheduled"], ["ended", "Ended"]].map(([k, l]) => (
            <Button key={k} small kind={state === k ? "primary" : "ghost"} onClick={() => setState(k)}>{l}</Button>
          ))}
        </div>
        <ErrorNote error={error} />
        {loading && !data ? <Spinner /> : !data?.alerts.length ? <Empty>No alerts.</Empty> : (
          <div className="table-wrap"><table>
            <thead><tr><th>Alert</th><th>For</th><th>When</th><th>State</th><th /></tr></thead>
            <tbody>{data.alerts.map((a) => (
              <tr key={a.id}>
                <td><div className="row gap"><Badge tone={SEV_TONE[a.severity]}>{a.severity}</Badge><strong>{a.title_en}</strong></div>
                  {a.title_hi && <div className="small" lang="hi">{a.title_hi}</div>}
                  {a.body_en && <div className="muted small clip">{a.body_en}</div>}</td>
                <td className="small">{a.scope === "everyone" ? "Everyone" : [a.routes.length ? `Bus ${a.routes.join(", ")}` : "", a.stops.map((s) => s.name).join(", ")].filter(Boolean).join(" · ")}</td>
                <td className="small muted" style={{ whiteSpace: "nowrap" }}>{fmtTime(a.starts_at)}<br />→ {a.ends_at ? fmtTime(a.ends_at) : "until ended"}</td>
                <td><Badge tone={STATE_TONE[a.state]}>{a.state}</Badge></td>
                <td className="num">{agent && <div className="row gap" style={{ justifyContent: "flex-end" }}>
                  <Button small onClick={() => open(a)}>Edit</Button>
                  {a.state !== "ended" && <Button small onClick={() => void run(async () => { await api.post(`/alerts/${a.id}/end`); await reload(); }, "Alert ended")}>End now</Button>}
                  <Button small kind="danger" onClick={async () => { if (await confirm.ask(`Delete “${a.title_en}”?`)) void run(async () => { await api.del(`/alerts/${a.id}`); await reload(); }, "Deleted"); }}>Delete</Button>
                </div>}</td>
              </tr>
            ))}</tbody>
          </table></div>
        )}
      </Card>

      <Modal wide title={edit?.id ? "Edit alert" : "New alert"} open={!!edit} onClose={() => setEdit(null)}
        footer={<><Button kind="ghost" onClick={() => setEdit(null)}>Cancel</Button><Button kind="primary" disabled={busy || !edit?.f.title_en.trim()} onClick={() => void save()}>{edit?.id ? "Save" : "Publish"}</Button></>}>
        {edit && <>
          <div className="row gap wrap">
            {(["info", "warning", "critical"] as const).map((s) => (
              <Button key={s} small kind={edit.f.severity === s ? "primary" : "ghost"} onClick={() => set("severity", s)}>{s === "info" ? "Info" : s === "warning" ? "Warning" : "Critical"}</Button>
            ))}
          </div>
          <div className="grid grid-2">
            <Field label="Title (English)"><input maxLength={120} value={edit.f.title_en} onChange={(e) => set("title_en", e.target.value)} placeholder="e.g. Route 534 diverted near ITO" /></Field>
            <Field label="Title (Hindi)" hint="Shown to people using the app in Hindi."><input lang="hi" maxLength={120} value={edit.f.title_hi} onChange={(e) => set("title_hi", e.target.value)} /></Field>
            <Field label="Details (English)"><textarea maxLength={1000} rows={3} value={edit.f.body_en} onChange={(e) => set("body_en", e.target.value)} /></Field>
            <Field label="Details (Hindi)"><textarea lang="hi" maxLength={1000} rows={3} value={edit.f.body_hi} onChange={(e) => set("body_hi", e.target.value)} /></Field>
          </div>
          <Field label="Bus numbers" hint="Comma-separated, e.g. 534, 764. Leave bus numbers and stops empty to show it to everyone on Home.">
            <input value={edit.f.routes} onChange={(e) => set("routes", e.target.value)} placeholder="534, 764" />
          </Field>
          <Field label="Stops">
            <input value={stopQ} onChange={(e) => void searchStops(e.target.value)} placeholder="Search a stop to add" />
          </Field>
          {stopHits.length > 0 && (
            <div className="row gap wrap">{stopHits.slice(0, 6).map((h) => (
              <Button key={h.name} small onClick={() => { set("stops", [...edit.f.stops, ...h.stop_ids.map((id) => ({ id, name: h.name }))]); setStopHits([]); setStopQ(""); }}>+ {h.name}</Button>
            ))}</div>
          )}
          {edit.f.stops.length > 0 && (
            <div className="row gap wrap">{[...new Set(edit.f.stops.map((s) => s.name))].map((n) => (
              <Badge key={n} tone="info">{n} <button className="icon-btn" style={{ fontSize: 14, padding: 0 }} aria-label={`Remove ${n}`}
                onClick={() => set("stops", edit.f.stops.filter((s) => s.name !== n))}>×</button></Badge>
            ))}</div>
          )}
          <div className="grid grid-2">
            <Field label="Starts"><input type="datetime-local" value={edit.f.starts_at} onChange={(e) => set("starts_at", e.target.value)} /></Field>
            <Field label="Ends" hint="Empty: shown until you end it."><input type="datetime-local" value={edit.f.ends_at} onChange={(e) => set("ends_at", e.target.value)} /></Field>
          </div>
        </>}
      </Modal>
      {confirm.node}
    </>
  );
}
