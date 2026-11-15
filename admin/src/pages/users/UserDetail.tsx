import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api, can } from "../../api";
import { useAuth } from "../../auth";
import { Badge, Button, Card, Empty, ErrorNote, Field, fmtDate, fmtRs, fmtTime, Modal, PageHead, Spinner, useAction, useLoad } from "../../ui";
import { KindBadge, StatusBadge } from "../support/common";
import type { UserRow } from ".";

interface Tx { kind: string; amount: number; balance_after: number; ref: string | null; created_at: string }
interface Item { id: string; kind: string; created_at: string; valid_until: string; voided_at: string | null; void_reason: string | null; payload: Record<string, unknown> }
interface Detail extends UserRow {
  last_sign_in?: string | null;
  wallet: { balance: number; transactions: Tx[] };
  items: Item[];
  support: { id: number; ref: string; kind: string; status: string; priority: string; created_at: string }[];
}

const TX: Record<string, string> = { topup: "Demo top-up", ticket: "Ticket", pass: "Pass", adjust: "Support adjustment" };

function itemLine(i: Item): string {
  const p = i.payload as Record<string, unknown>;
  if (i.kind === "pass") return `${p.kind === "monthly" ? "Monthly" : "Daily"} pass · ${p.bus_type === "ac" ? "AC" : "Non-AC"}`;
  const legs = (p.legs as { route?: string; board_stop?: string; alight_stop?: string }[] | undefined) ?? [];
  if (!legs.length) return "Ticket";
  return `${legs.map((l) => l.route).join(" + ")} · ${legs[0].board_stop ?? ""} → ${legs[legs.length - 1].alight_stop ?? ""}`;
}

export function UserDetail() {
  const { uid = "" } = useParams();
  const nav = useNavigate();
  const { user: me } = useAuth();
  const agent = can(me, "agent");
  const { data: u, error, loading, reload } = useLoad(() => api.get<Detail>(`/users/${encodeURIComponent(uid)}`), [uid]);
  const { run, busy } = useAction();
  const [modal, setModal] = useState<null | "disable" | "wallet" | "delete" | { void: Item }>(null);
  const [reason, setReason] = useState("");
  const [amount, setAmount] = useState("");
  const [confirm, setConfirm] = useState("");
  const close = () => { setModal(null); setReason(""); setAmount(""); setConfirm(""); };

  if (loading && !u) return <Spinner />;
  if (!u) return <><div><Button small kind="ghost" onClick={() => nav(-1)}>‹ Back</Button></div><ErrorNote error={error ?? "Not found"} /></>;
  const path = `/users/${encodeURIComponent(u.id)}`;
  const act = (fn: () => Promise<unknown>, ok: string) => run(async () => { await fn(); close(); await reload(); }, ok);

  return (
    <>
      <div><Button small kind="ghost" onClick={() => nav(-1)}>‹ Back</Button></div>
      <PageHead title={u.name || "Unnamed user"} sub={<span className="row gap wrap">{u.disabled ? <Badge tone="bad">Disabled</Badge> : <Badge tone="good">Active</Badge>}
        {u.email_verified === false && <Badge tone="warn">Email not confirmed</Badge>}<span>joined {fmtDate(u.created_at)}</span></span>}
        actions={agent && <>
          {u.email && u.email_verified === false && <Button onClick={() => void run(() => api.post(`${path}/resend-verification`), "Confirmation email sent")}>Resend confirmation</Button>}
          <Button onClick={() => setModal("wallet")}>Adjust demo wallet</Button>
          <Button kind={u.disabled ? "plain" : "danger"} onClick={() => setModal("disable")}>{u.disabled ? "Enable account" : "Disable account"}</Button>
          {can(me, "owner") && <Button kind="danger" onClick={() => setModal("delete")}>Delete…</Button>}
        </>} />
      <div className="split">
        <div className="stack">
          <Card title={`Tickets & passes · ${u.items.length}`}>
            {u.items.length === 0 ? <Empty>No tickets or passes.</Empty> : (
              <div className="table-wrap"><table>
                <thead><tr><th>Item</th><th>Bought</th><th>Valid until</th><th className="num">Price</th><th>Status</th><th /></tr></thead>
                <tbody>{u.items.map((i) => {
                  const expired = new Date(i.valid_until) < new Date();
                  return (
                    <tr key={i.id}>
                      <td><strong>{itemLine(i)}</strong><div className="muted small"><code>{i.id}</code></div></td>
                      <td className="muted">{fmtTime(i.created_at)}</td>
                      <td className="muted">{fmtTime(i.valid_until)}</td>
                      <td className="num">{fmtRs((i.payload.price ?? i.payload.total) as number | undefined)}</td>
                      <td>{i.voided_at ? <span title={i.void_reason ?? ""}><Badge tone="bad">Voided</Badge></span> : expired ? <Badge>Expired</Badge> : <Badge tone="good">Valid</Badge>}</td>
                      <td className="num">{agent && !i.voided_at && !expired && <Button small kind="danger" onClick={() => setModal({ void: i })}>Void</Button>}</td>
                    </tr>
                  );
                })}</tbody>
              </table></div>
            )}
          </Card>
          <Card title="Demo wallet" actions={<strong>{fmtRs(u.wallet.balance)}</strong>}>
            <p className="note" style={{ marginTop: 0 }}>Demo money only: no real payments are taken or refunded.</p>
            {u.wallet.transactions.length === 0 ? <Empty>No transactions.</Empty> : (
              <div className="table-wrap"><table>
                <thead><tr><th>When</th><th>What</th><th>Reference</th><th className="num">Amount</th><th className="num">Balance</th></tr></thead>
                <tbody>{u.wallet.transactions.map((t, k) => (
                  <tr key={k}><td className="muted">{fmtTime(t.created_at)}</td><td>{TX[t.kind] ?? t.kind}</td><td className="small clip">{t.ref}</td>
                    <td className="num" style={{ color: t.amount < 0 ? "var(--bad)" : "var(--good)" }}>{t.amount > 0 ? "+" : ""}{fmtRs(t.amount)}</td>
                    <td className="num">{fmtRs(t.balance_after)}</td></tr>
                ))}</tbody>
              </table></div>
            )}
          </Card>
        </div>
        <div className="stack">
          <Card title="Profile">
            <dl className="kv">
              <dt>User id</dt><dd><code>{u.id}</code></dd>
              {u.email && <><dt>Email</dt><dd>{u.email}</dd></>}
              {u.phone && <><dt>Phone</dt><dd>{u.phone}</dd></>}
              <dt>Joined</dt><dd>{fmtTime(u.created_at)}</dd>
              {u.last_sign_in !== undefined && <><dt>Last sign-in</dt><dd>{fmtTime(u.last_sign_in)}</dd></>}
            </dl>
          </Card>
          <Card title="Support requests">
            {u.support.length === 0 ? <Empty>None.</Empty> : (
              <ul className="timeline">{u.support.map((s) => (
                <li key={s.id}><Link to={`/support/${s.id}`}><code>{s.ref}</code></Link><div className="row gap" style={{ marginTop: 4 }}><KindBadge k={s.kind} /><StatusBadge s={s.status} /></div></li>
              ))}</ul>
            )}
          </Card>
        </div>
      </div>

      <Modal title={u.disabled ? "Enable account" : "Disable account"} open={modal === "disable"} onClose={close}
        footer={<><Button kind="ghost" onClick={close}>Cancel</Button><Button kind={u.disabled ? "primary" : "danger"} disabled={busy || reason.trim().length < 3}
          onClick={() => void act(() => api.post(`${path}/disable`, { disabled: !u.disabled, reason }), u.disabled ? "Account enabled" : "Account disabled")}>{u.disabled ? "Enable" : "Disable"}</Button></>}>
        <p>{u.disabled ? "They will be able to sign in again." : "They are signed out everywhere and can't sign in or book until it's enabled again. Their data is kept."}</p>
        <Field label="Reason (kept in the audit log)"><input value={reason} onChange={(e) => setReason(e.target.value)} /></Field>
      </Modal>

      <Modal title="Adjust demo wallet" open={modal === "wallet"} onClose={close}
        footer={<><Button kind="ghost" onClick={close}>Cancel</Button><Button kind="primary" disabled={busy || !Number(amount) || reason.trim().length < 3}
          onClick={() => void act(() => api.post(`${path}/wallet`, { amount: Number(amount), reason }), "Wallet adjusted")}>Apply</Button></>}>
        <p className="note">Demo wallet only. Use a negative amount to take money back. Current balance {fmtRs(u.wallet.balance)}.</p>
        <Field label="Amount (₹)"><input type="number" step="1" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="e.g. 15 or -15" /></Field>
        <Field label="Reason (shown on their wallet)"><input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. refund for ticket booked twice" /></Field>
      </Modal>

      <Modal title="Void ticket or pass" open={typeof modal === "object" && modal !== null} onClose={close}
        footer={<><Button kind="ghost" onClick={close}>Cancel</Button><Button kind="danger" disabled={busy || reason.trim().length < 3}
          onClick={() => typeof modal === "object" && modal && void act(() => api.post(`/items/${encodeURIComponent(modal.void.id)}/void`, { reason }), "Voided")}>Void</Button></>}>
        <p>Checking its QR will then say <strong>voided</strong>, and the app marks it cancelled. This can't be undone. To refund the demo money, adjust the wallet afterwards.</p>
        <Field label="Reason"><input value={reason} onChange={(e) => setReason(e.target.value)} /></Field>
      </Modal>

      <Modal title="Delete account for good" open={modal === "delete"} onClose={close}
        footer={<><Button kind="ghost" onClick={close}>Cancel</Button><Button kind="danger" disabled={busy || !confirm.trim() || reason.trim().length < 3}
          onClick={() => void run(async () => { await api.post(`${path}/delete`, { confirm, reason }); nav("/users"); }, "Account deleted")}>Delete for good</Button></>}>
        <p>This removes the sign-in, profile, demo wallet, tickets, passes and support requests. It can't be undone.</p>
        <Field label={`Type ${u.email || u.phone || u.id} to confirm`}><input value={confirm} onChange={(e) => setConfirm(e.target.value)} /></Field>
        <Field label="Reason (kept in the audit log)"><input value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. user asked by email (DR-…)" /></Field>
      </Modal>
    </>
  );
}
