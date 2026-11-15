import { useState } from "react";
import { api } from "../api";
import { Badge, Button, Card, ErrorNote, fmtTime, Spinner, useAction, useLoad } from "../ui";

interface Block { id: number; at: string; admin_email: string | null; action: string; target: string | null; prev_hash: string; hash: string }
interface Verdict { ok: boolean; blocks: number; head?: string; head_block?: number; broken_at: number | null; reason: string | null }

const short = (h?: string | null) => (h ? `${h.slice(0, 8)}…${h.slice(-6)}` : "—");
const label = (a: string) => a.replace(/_/g, " ");

/** The audit log as a hash chain: each block seals the one before it, so past admin actions can't be quietly edited. */
export function Ledger() {
  const { data, error, loading, reload } = useLoad(() => api.get<{ blocks: Block[] }>("/ledger", { limit: 12 }));
  const { run, busy } = useAction();
  const [verdict, setVerdict] = useState<(Verdict & { checked: string }) | null>(null);

  const verify = () => run(async () => {
    const r = await api.post<Verdict>("/ledger/verify");
    setVerdict({ ...r, checked: new Date().toISOString() });
    await reload();
  });

  return (
    <Card title="Audit ledger (blockchain)" actions={<Button kind="primary" small disabled={busy} onClick={() => void verify()}>{busy ? "Checking…" : "Verify chain"}</Button>}>
      <p className="muted small" style={{ marginTop: 0 }}>
        Every admin action is a block. Each block's hash covers its own details and the previous block's hash, signed with a
        key kept on the server. If anyone edits or deletes a past entry, every block after it stops matching and the check fails.
      </p>
      {verdict && (
        <div className={`verdict ${verdict.ok ? "verdict-ok" : "verdict-bad"}`} role="status">
          {verdict.ok ? (
            <><Badge tone="good">✓ Chain intact</Badge> <span>{verdict.blocks.toLocaleString("en-IN")} blocks checked · head <code>{short(verdict.head)}</code></span></>
          ) : (
            <><Badge tone="bad">✕ Chain broken at block #{verdict.broken_at}</Badge> <span>{verdict.reason}</span></>
          )}
          <span className="muted small"> · {fmtTime(verdict.checked)}</span>
        </div>
      )}
      <ErrorNote error={error} />
      {loading && !data ? <Spinner /> : (
        <ol className="chain" aria-label="Newest blocks first">
          {data?.blocks.map((b) => (
            <li key={b.id} className="block">
              <div className="row between">
                <strong>#{b.id} · {label(b.action)}</strong>
                <span className="muted small">{fmtTime(b.at)}</span>
              </div>
              <div className="muted small">{b.admin_email ?? "system"}{b.target ? ` → ${b.target}` : ""}</div>
              <div className="hashes small">
                <span title={b.hash}>hash <code>{short(b.hash)}</code></span>
                <span title={b.prev_hash}>prev <code>{short(b.prev_hash)}</code></span>
              </div>
            </li>
          ))}
        </ol>
      )}
    </Card>
  );
}
