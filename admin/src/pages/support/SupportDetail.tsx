import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api, can } from "../../api";
import { useAuth } from "../../auth";
import { ago, Button, Card, ErrorNote, Field, fmtTime, PageHead, Spinner, useAction, useLoad } from "../../ui";
import { CANNED, KIND, KindBadge, PRIORITY, PriorityBadge, SafetyBadge, STATUS, StatusBadge, type Event, type ReqDetail } from "./common";

function describe(e: Event): string {
  const m = e.meta ?? {};
  switch (e.kind) {
    case "status": return `changed status from ${STATUS[m.from ?? ""] ?? m.from} to ${STATUS[m.to ?? ""] ?? m.to}`;
    case "priority": return `changed priority from ${PRIORITY[m.from ?? ""] ?? m.from} to ${PRIORITY[m.to ?? ""] ?? m.to}`;
    case "assign": return m.to ? `assigned it to ${m.to}` : "unassigned it";
    case "reply": return `emailed a reply to ${m.to ?? "the person"}`;
    case "note": return "added an internal note";
    default: return e.kind;
  }
}

export function SupportDetail() {
  const { id } = useParams();
  const nav = useNavigate();
  const { user } = useAuth();
  const agent = can(user, "agent");
  const { data: r, error, loading, setData } = useLoad(() => api.get<ReqDetail>(`/support/${id}`), [id]);
  const { data: agents } = useLoad(() => api.get<{ agents: { id: number; name: string }[] }>("/agents"));
  const { run, busy } = useAction();
  const [reply, setReply] = useState("");
  const [then, setThen] = useState("waiting");
  const [note, setNote] = useState("");

  if (loading && !r) return <Spinner />;
  if (!r) return <ErrorNote error={error ?? "Not found"} />;
  const patch = (body: object) => run(async () => setData(await api.patch<ReqDetail>(`/support/${r.id}`, body)), "Saved");
  const sendReply = () => run(async () => { setData(await api.post<ReqDetail>(`/support/${r.id}/reply`, { body: reply, then })); setReply(""); }, "Reply emailed");
  const addNote = () => run(async () => { setData(await api.post<ReqDetail>(`/support/${r.id}/notes`, { body: note })); setNote(""); }, "Note added");

  return (
    <>
      <div><Button small kind="ghost" onClick={() => nav(-1)}>‹ Back</Button></div>
      <PageHead title={r.ref} sub={<span className="row gap wrap"><KindBadge k={r.kind} /><StatusBadge s={r.status} /><PriorityBadge p={r.priority} />{r.safety ? <SafetyBadge /> : null}<span>received {fmtTime(r.created_at)} ({ago(r.created_at)})</span></span>} />
      <div className="split">
        <div className="stack">
          <Card title={`${KIND[r.kind] ?? r.kind} from the app`}>
            <div className="msg" lang={r.lang}>{r.message}</div>
            {r.safety ? <p className="note" style={{ marginTop: 12 }}>Flagged as a possible safety matter from its wording. If someone may be in danger, ask them to call 112 and escalate to the operator now.</p> : null}
          </Card>

          {agent && (
            <Card title="Reply by email">
              {r.email ? (
                <div className="stack">
                  <div className="row gap wrap">
                    <span className="muted small">Start from:</span>
                    {CANNED.map((c) => <Button key={c.label} small kind="ghost" onClick={() => setReply(r.lang === "hi" ? c.hi : c.en)}>{c.label}</Button>)}
                  </div>
                  <Field label={`To ${r.email}`} hint={`Sent from the support address with the reference ${r.ref}, quoting their message. Written in ${r.lang === "hi" ? "Hindi" : "English"} is best.`}>
                    <textarea value={reply} onChange={(e) => setReply(e.target.value)} rows={6} />
                  </Field>
                  <div className="row gap wrap">
                    <Field label="Then mark as"><select value={then} onChange={(e) => setThen(e.target.value)} style={{ width: "auto" }}>
                      <option value="waiting">Waiting on person</option><option value="resolved">Resolved</option><option value="in_progress">In progress</option></select></Field>
                    <Button kind="primary" disabled={busy || reply.trim().length < 2} onClick={() => void sendReply()} style={{ alignSelf: "flex-end" }}>Send reply</Button>
                  </div>
                </div>
              ) : <p className="note">They didn't give an email address, so they can't be answered. Keep notes below for the team.</p>}
            </Card>
          )}

          <Card title="History">
            <ol className="timeline">
              <li><div className="who">{fmtTime(r.created_at)} · from the app{r.emailed ? " · emailed to the support inbox" : ""}</div>Request received</li>
              {r.events.map((e) => (
                <li key={e.id} className={e.kind === "reply" ? "reply" : e.kind === "note" ? "internal" : ""}>
                  <div className="who">{fmtTime(e.at)} · {e.admin_name ?? "system"} {describe(e)}</div>
                  {e.body && <div className="msg" style={{ marginTop: 4, borderLeftColor: e.kind === "note" ? "var(--warn)" : undefined }}>{e.body}</div>}
                </li>
              ))}
            </ol>
            {agent && (
              <div className="stack" style={{ marginTop: 16 }}>
                <Field label="Internal note" hint="Only admins see notes."><textarea rows={3} value={note} onChange={(e) => setNote(e.target.value)} /></Field>
                <div><Button disabled={busy || !note.trim()} onClick={() => void addNote()}>Add note</Button></div>
              </div>
            )}
          </Card>
        </div>

        <div className="stack">
          <Card title="Handling">
            <div className="stack">
              <Field label="Status"><select disabled={!agent || busy} value={r.status} onChange={(e) => void patch({ status: e.target.value })}>
                {Object.entries(STATUS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></Field>
              <Field label="Priority"><select disabled={!agent || busy} value={r.priority} onChange={(e) => void patch({ priority: e.target.value })}>
                {Object.entries(PRIORITY).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></Field>
              <Field label="Assigned to"><select disabled={!agent || busy} value={r.assignee_id ?? ""}
                onChange={(e) => void patch(e.target.value ? { assignee_id: Number(e.target.value) } : { unassign: true })}>
                <option value="">Nobody</option>{agents?.agents.map((a) => <option key={a.id} value={a.id}>{a.name}{a.id === user?.id ? " (me)" : ""}</option>)}</select></Field>
              {agent && r.assignee_id !== user?.id && <Button small onClick={() => void patch({ assignee_id: user?.id })}>Assign to me</Button>}
            </div>
          </Card>
          <Card title="Details">
            <dl className="kv">
              <dt>Email</dt><dd>{r.email ?? "—"}</dd>
              <dt>Account</dt><dd>{r.user_id ? <Link to={`/users/${encodeURIComponent(r.user_id)}`}>Open user</Link> : "Guest"}</dd>
              {r.route && <><dt>Bus route</dt><dd><Link to={`/support?status=all&route=${encodeURIComponent(r.route)}`}>{r.route}</Link></dd></>}
              {r.when_text && <><dt>When</dt><dd>{r.when_text}</dd></>}
              {r.place && <><dt>Stop or area</dt><dd>{r.place}</dd></>}
              <dt>Language</dt><dd>{r.lang === "hi" ? "Hindi" : "English"}</dd>
              <dt>App</dt><dd>{r.app_version ?? "—"}</dd>
              {r.resolved_at && <><dt>Resolved</dt><dd>{fmtTime(r.resolved_at)}</dd></>}
            </dl>
          </Card>
          {r.other_requests.length > 0 && (
            <Card title="Other requests from them">
              <ul className="timeline">{r.other_requests.map((o) => (
                <li key={o.id}><Link to={`/support/${o.id}`}><code>{o.ref}</code></Link> <span className="muted small">{KIND[o.kind]} · {STATUS[o.status]} · {ago(o.created_at)}</span></li>
              ))}</ul>
            </Card>
          )}
        </div>
      </div>
    </>
  );
}
