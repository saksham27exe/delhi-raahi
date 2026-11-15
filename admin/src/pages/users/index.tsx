import { useState } from "react";
import { Route, Routes, useNavigate, useSearchParams } from "react-router-dom";
import { api } from "../../api";
import { Badge, Button, Card, Empty, ErrorNote, Field, fmtDate, fmtRs, Modal, PageHead, Spinner, useLoad } from "../../ui";
import { UserDetail } from "./UserDetail";

export interface UserRow { id: string; email: string; phone: string; name: string; created_at: string; balance: number; disabled: boolean; email_verified: boolean | null }

export function Users() {
  return (
    <Routes>
      <Route index element={<UserList />} />
      <Route path=":uid" element={<UserDetail />} />
    </Routes>
  );
}

function UserList() {
  const nav = useNavigate();
  const [sp, setSp] = useSearchParams();
  const q = sp.get("q") ?? "";
  const [extra, setExtra] = useState<UserRow[]>([]);
  const [next, setNext] = useState<string | null>(null);
  const { data, error, loading } = useLoad(async () => {
    const r = await api.get<{ users: UserRow[]; next: string | null; store: string }>("/users", { q });
    setExtra([]);
    setNext(r.next);
    return r;
  }, [q]);
  const more = async () => {
    const r = await api.get<{ users: UserRow[]; next: string | null }>("/users", { q, cursor: next });
    setExtra((x) => [...x, ...r.users]);
    setNext(r.next);
  };
  const rows = [...(data?.users ?? []), ...extra];
  const [lookup, setLookup] = useState(false);
  return (
    <>
      <PageHead title="Users & bookings" sub={data?.store === "firebase" ? "App accounts (Firebase). Search by email prefix or user id." : "App accounts. Search by phone, name or id."}
        actions={<Button onClick={() => setLookup(true)}>Find ticket / check QR</Button>} />
      <Card>
        <div className="filters" style={{ marginBottom: 12 }}>
          <Field label="Search" className="search"><input placeholder={data?.store === "firebase" ? "e.g. riya@ or a user id" : "phone, name or id"} defaultValue={q}
            onKeyDown={(e) => { if (e.key === "Enter") setSp((e.target as HTMLInputElement).value.trim() ? { q: (e.target as HTMLInputElement).value.trim() } : {}); }} /></Field>
        </div>
        <ErrorNote error={error} />
        {loading && !data ? <Spinner /> : rows.length === 0 ? <Empty>No users found.</Empty> : (
          <div className="table-wrap"><table>
            <thead><tr><th>Name</th><th>Contact</th><th>Joined</th><th className="num">Demo balance</th><th>Status</th></tr></thead>
            <tbody>{rows.map((u) => (
              <tr key={u.id} className="click" tabIndex={0} onClick={() => nav(`/users/${encodeURIComponent(u.id)}`)}
                onKeyDown={(e) => e.key === "Enter" && nav(`/users/${encodeURIComponent(u.id)}`)}>
                <td><strong>{u.name || "—"}</strong><div className="muted small"><code>{u.id}</code></div></td>
                <td>{u.email || u.phone}</td>
                <td className="muted">{fmtDate(u.created_at)}</td>
                <td className="num">{fmtRs(u.balance)}</td>
                <td>{u.disabled ? <Badge tone="bad">Disabled</Badge> : <Badge tone="good">Active</Badge>}</td>
              </tr>
            ))}</tbody>
          </table></div>
        )}
        {next && <div className="pager"><Button small onClick={() => void more()}>Load more</Button></div>}
      </Card>
      <TicketLookup open={lookup} onClose={() => setLookup(false)} />
    </>
  );
}

interface VerifyResult { status?: string; valid_signature?: boolean; ticket?: Record<string, unknown>; reason?: string }

function TicketLookup({ open, onClose }: { open: boolean; onClose: () => void }) {
  const nav = useNavigate();
  const [id, setId] = useState("");
  const [qr, setQr] = useState("");
  const [err, setErr] = useState<string | null>(null);
  const [res, setRes] = useState<VerifyResult | null>(null);
  const find = async () => {
    setErr(null);
    try {
      const r = await api.get<{ user_id: string }>(`/items/${encodeURIComponent(id.trim())}`);
      onClose();
      nav(`/users/${encodeURIComponent(r.user_id)}`);
    } catch (e) { setErr((e as Error).message); }
  };
  const check = async () => {
    setErr(null);
    try { setRes(await api.post<VerifyResult>("/verify", { qr_payload: qr.trim() })); } catch (e) { setErr((e as Error).message); }
  };
  const tone = res?.status === "active_demo" ? "good" : res?.status === "expired" || res?.status === "not_on_record" ? "warn" : "bad";
  const LABEL: Record<string, string> = { active_demo: "Valid (demo)", expired: "Expired", voided: "Voided by support", not_on_record: "Not on any account",
    unreadable: "Unreadable", tampered_or_foreign: "Tampered or not ours" };
  return (
    <Modal title="Find a ticket or check a QR" open={open} onClose={onClose}>
      <Field label="Ticket or pass id" hint="Opens the account that holds it.">
        <div className="row gap"><input value={id} onChange={(e) => setId(e.target.value)} placeholder="e.g. DEMO-1A2B3C4D5E" /><Button disabled={!id.trim()} onClick={() => void find()}>Find</Button></div>
      </Field>
      <Field label="QR text" hint="Paste the text from a scanned ticket or pass QR to check it as an inspector would.">
        <textarea rows={3} value={qr} onChange={(e) => setQr(e.target.value)} />
      </Field>
      <div><Button disabled={!qr.trim()} onClick={() => void check()}>Check QR</Button></div>
      <ErrorNote error={err} />
      {res && (
        <div className="stack">
          <div className="row gap"><Badge tone={tone}>{LABEL[res.status ?? ""] ?? res.status ?? "unknown"}</Badge>{res.valid_signature ? <Badge tone="good">Signature OK</Badge> : <Badge tone="bad">Bad signature</Badge>}</div>
          {res.reason && <p className="muted">{res.reason}</p>}
          {res.ticket && <pre className="note" style={{ margin: 0, overflow: "auto" }}>{JSON.stringify(res.ticket, null, 2)}</pre>}
        </div>
      )}
    </Modal>
  );
}
