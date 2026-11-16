import { useState } from "react";
import { api } from "../api";
import { Badge, Button, Card, Empty, ErrorNote, Field, fmtTime, PageHead, Spinner, useLoad } from "../ui";

interface Entry { id: number; at: string; admin_email: string | null; action: string; target: string | null; detail: Record<string, unknown> | null; ip: string | null }

const tone = (a: string) => (a.includes("failed") || a.includes("delete") || a.includes("disable") || a.includes("void") ? "bad"
  : a.startsWith("login") || a === "logout" ? "neutral" : "info");

export function Audit() {
  const [action, setAction] = useState("");
  const [admin, setAdmin] = useState("");
  const [pages, setPages] = useState<Entry[][]>([]);
  const { data, error, loading } = useLoad(async () => {
    const r = await api.get<{ entries: Entry[] }>("/audit", { limit: 100, action, admin });
    setPages([]);
    return r.entries;
  }, [action, admin]);
  const all = [...(data ?? []), ...pages.flat()];
  const lastBatch = pages.length ? pages[pages.length - 1].length : (data?.length ?? 0);
  const [moreError, setMoreError] = useState<string | null>(null);
  const more = async () => {
    const last = all[all.length - 1];
    const key = `${action}|${admin}`;
    try {
      const r = await api.get<{ entries: Entry[] }>("/audit", { limit: 100, before: last?.id, action, admin });
      if (key === `${action}|${admin}`) setPages((p) => [...p, r.entries]);
      setMoreError(null);
    } catch (e) { setMoreError((e as Error).message); }
  };
  return (
    <>
      <PageHead title="Audit log" sub="Every sign-in and every change made from this website, newest first." />
      <Card>
        <div className="filters" style={{ marginBottom: 12 }}>
          <Field label="Action"><input placeholder="e.g. user_disabled" value={action} onChange={(e) => setAction(e.target.value.trim())} /></Field>
          <Field label="Admin email"><input placeholder="anyone" value={admin} onChange={(e) => setAdmin(e.target.value.trim())} /></Field>
        </div>
        <ErrorNote error={error} />
        {loading && !data ? <Spinner /> : all.length === 0 ? <Empty>Nothing logged yet.</Empty> : (
          <div className="table-wrap"><table>
            <thead><tr><th>When</th><th>Admin</th><th>Action</th><th>Target</th><th>Details</th><th>IP</th></tr></thead>
            <tbody>{all.map((e) => (
              <tr key={e.id}>
                <td className="muted" style={{ whiteSpace: "nowrap" }}>{fmtTime(e.at)}</td>
                <td>{e.admin_email ?? "—"}</td>
                <td><Badge tone={tone(e.action)}>{e.action.replace(/_/g, " ")}</Badge></td>
                <td><code>{e.target ?? ""}</code></td>
                <td className="small clip" title={e.detail ? JSON.stringify(e.detail) : ""}>{e.detail ? Object.entries(e.detail).map(([k, v]) => `${k}: ${typeof v === "object" ? JSON.stringify(v) : v}`).join(" · ") : ""}</td>
                <td className="muted small">{e.ip}</td>
              </tr>
            ))}</tbody>
          </table></div>
        )}
        <ErrorNote error={moreError} />
        {all.length > 0 && lastBatch === 100 && <div className="pager"><Button small onClick={() => void more()}>Load older</Button></div>}
      </Card>
    </>
  );
}
