import { useNavigate, useSearchParams } from "react-router-dom";
import { api, can } from "../../api";
import { useAuth } from "../../auth";
import { ago, Button, Card, Empty, ErrorNote, Field, PageHead, Pager, Spinner, useLoad } from "../../ui";
import { KIND, KindBadge, PriorityBadge, SafetyBadge, StatusBadge, type Counts, type Req } from "./common";

const TABS: [string, string][] = [["open", "Open"], ["new", "New"], ["in_progress", "In progress"], ["waiting", "Waiting"], ["resolved", "Resolved"], ["all", "All"]];

export function SupportList() {
  const { user } = useAuth();
  const nav = useNavigate();
  const [sp, setSp] = useSearchParams();
  const f = {
    status: sp.get("status") ?? "open", kind: sp.get("kind") ?? "", priority: sp.get("priority") ?? "", assignee: sp.get("assignee") ?? "",
    q: sp.get("q") ?? "", route: sp.get("route") ?? "", safety: sp.get("safety") === "1", sort: sp.get("sort") ?? "priority",
    page: Number(sp.get("page") ?? 1),
  };
  const set = (k: string, v: string) => {
    const n = new URLSearchParams(sp);
    if (v) n.set(k, v); else n.delete(k);
    if (k !== "page") n.delete("page");
    setSp(n, { replace: true });
  };
  const { data, error, loading, reload } = useLoad(
    () => api.get<{ items: Req[]; total: number; page: number; pages: number; counts: Counts }>("/support", { ...f, status: f.status === "all" ? undefined : f.status, safety: f.safety || undefined, size: 25 }),
    [sp.toString()],
  );
  const c = data?.counts;
  return (
    <>
      <PageHead title="Support desk" sub={c ? `${c.open} open · ${c.urgent_open} urgent · ${c.unassigned_open} unassigned` : "Complaints and questions from the app."}
        actions={<>
          <Button onClick={() => void reload()}>Refresh</Button>
          {can(user, "agent") && <Button onClick={() => void api.download("/support/export.csv", { status: f.status === "open" || f.status === "all" ? undefined : f.status, kind: f.kind, route: f.route })}>Export CSV</Button>}
        </>} />
      <Card>
        <div className="row gap wrap" role="tablist" aria-label="Status" style={{ marginBottom: 12 }}>
          {TABS.map(([k, label]) => (
            <Button key={k} small kind={f.status === k ? "primary" : "ghost"} role="tab" aria-selected={f.status === k} onClick={() => set("status", k)}>
              {label}{c && k in c ? ` · ${c[k as keyof Counts]}` : ""}
            </Button>
          ))}
        </div>
        <div className="filters" style={{ marginBottom: 12 }}>
          <Field label="Search" className="search"><input placeholder="Reference, words, email, stop…" defaultValue={f.q}
            onKeyDown={(e) => { if (e.key === "Enter") set("q", (e.target as HTMLInputElement).value.trim()); }}
            onBlur={(e) => e.target.value.trim() !== f.q && set("q", e.target.value.trim())} /></Field>
          <Field label="Type"><select value={f.kind} onChange={(e) => set("kind", e.target.value)}>
            <option value="">Any</option>{Object.entries(KIND).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></Field>
          <Field label="Priority"><select value={f.priority} onChange={(e) => set("priority", e.target.value)}>
            <option value="">Any</option><option value="urgent">Urgent</option><option value="high">High</option><option value="normal">Normal</option><option value="low">Low</option></select></Field>
          <Field label="Assigned to"><select value={f.assignee} onChange={(e) => set("assignee", e.target.value)}>
            <option value="">Anyone</option>{user && <option value={String(user.id)}>Me</option>}<option value="none">Nobody</option></select></Field>
          <Field label="Route"><input placeholder="e.g. 534" defaultValue={f.route} onBlur={(e) => e.target.value.trim() !== f.route && set("route", e.target.value.trim())} /></Field>
          <Field label="Sort"><select value={f.sort} onChange={(e) => set("sort", e.target.value)}>
            <option value="priority">Most urgent</option><option value="newest">Newest</option><option value="oldest">Oldest</option><option value="updated">Recently updated</option></select></Field>
          <label className="row gap" style={{ flex: "0 0 auto", paddingBottom: 8 }}>
            <input type="checkbox" checked={f.safety} onChange={(e) => set("safety", e.target.checked ? "1" : "")} /> Safety only
          </label>
        </div>
        <ErrorNote error={error} />
        {loading && !data ? <Spinner /> : !data?.items.length ? <Empty>No requests match.</Empty> : (
          <div className="table-wrap"><table>
            <thead><tr><th>Request</th><th>Message</th><th>Status</th><th>Priority</th><th>Assigned</th><th>Received</th></tr></thead>
            <tbody>{data.items.map((r) => (
              <tr key={r.id} className="click" tabIndex={0} onClick={() => nav(`/support/${r.id}`)} onKeyDown={(e) => e.key === "Enter" && nav(`/support/${r.id}`)}>
                <td style={{ whiteSpace: "nowrap" }}><code>{r.ref}</code><div className="row gap" style={{ marginTop: 4 }}><KindBadge k={r.kind} />{r.safety ? <SafetyBadge /> : null}</div></td>
                <td><div className="clip">{r.message}</div><div className="muted small">{[r.route && `Route ${r.route}`, r.place, r.when_text].filter(Boolean).join(" · ") || (r.email ?? "No email")}</div></td>
                <td><StatusBadge s={r.status} /></td>
                <td><PriorityBadge p={r.priority} /></td>
                <td className="muted">{r.assignee_name ?? "—"}</td>
                <td className="muted" style={{ whiteSpace: "nowrap" }}>{ago(r.created_at)}</td>
              </tr>
            ))}</tbody>
          </table></div>
        )}
        {data && <Pager page={data.page} pages={data.pages} onPage={(p) => set("page", String(p))} />}
      </Card>
    </>
  );
}
