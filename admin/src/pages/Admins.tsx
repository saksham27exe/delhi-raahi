import { Fragment, useState } from "react";
import { api, type Role } from "../api";
import { useAuth } from "../auth";
import { Badge, Button, Card, ErrorNote, Field, fmtTime, Modal, PageHead, Secret, Spinner, useAction, useConfirm, useLoad } from "../ui";
import { ChangePassword } from "./Login";

interface Row { id: number; email: string; name: string; role: Role; disabled: number; must_change: number; created_at: string; last_login: string | null }

const ROLE_HELP: Record<Role, string> = {
  owner: "Everything, including managing admins",
  agent: "Support desk, users and bookings, alerts",
  viewer: "Read only",
};

export function Admins() {
  const { user } = useAuth();
  const { data, error, loading, reload } = useLoad(() => api.get<{ admins: Row[] }>("/admins"));
  const { run, busy } = useAction();
  const confirm = useConfirm();
  const [adding, setAdding] = useState(false);
  const [form, setForm] = useState({ email: "", name: "", role: "agent" as Role });
  const [secret, setSecret] = useState<{ email: string; pw: string } | null>(null);
  const [changing, setChanging] = useState(false);

  const add = () => run(async () => {
    const r = await api.post<{ one_time_password: string }>("/admins", form);
    setSecret({ email: form.email, pw: r.one_time_password });
    setAdding(false);
    setForm({ email: "", name: "", role: "agent" });
    await reload();
  });
  const patch = (id: number, body: object, ok: string) => run(async () => { await api.patch(`/admins/${id}`, body); await reload(); }, ok);
  const reset = async (r: Row) => {
    if (!(await confirm.ask(`Give ${r.email} a new one-time password? They are signed out everywhere.`))) return;
    await run(async () => {
      const x = await api.post<{ one_time_password: string }>(`/admins/${r.id}/reset-password`);
      setSecret({ email: r.email, pw: x.one_time_password });
    });
  };

  return (
    <>
      <PageHead title="Admins" sub="Who can sign in to this website, and what they can do."
        actions={<><Button onClick={() => setChanging(true)}>Change my password</Button><Button kind="primary" onClick={() => setAdding(true)}>Add admin</Button></>} />
      <Card>
        <ErrorNote error={error} />
        {loading && !data ? <Spinner /> : (
          <div className="table-wrap"><table>
            <thead><tr><th>Name</th><th>Role</th><th>Status</th><th>Last sign-in</th><th /></tr></thead>
            <tbody>
              {data?.admins.map((r) => (
                <tr key={r.id}>
                  <td><strong>{r.name}</strong><div className="muted small">{r.email}</div></td>
                  <td>
                    <select aria-label={`Role of ${r.email}`} value={r.role} disabled={busy || r.id === user?.id}
                      onChange={(e) => void patch(r.id, { role: e.target.value }, "Role changed")} style={{ width: "auto" }}>
                      <option value="owner">Owner</option><option value="agent">Agent</option><option value="viewer">Viewer</option>
                    </select>
                  </td>
                  <td>{r.disabled ? <Badge tone="bad">Disabled</Badge> : r.must_change ? <Badge tone="warn">Password not set</Badge> : <Badge tone="good">Active</Badge>}</td>
                  <td className="muted">{fmtTime(r.last_login)}</td>
                  <td className="num">
                    {r.id !== user?.id && (
                      <div className="row gap" style={{ justifyContent: "flex-end" }}>
                        <Button small onClick={() => void reset(r)}>Reset password</Button>
                        <Button small kind={r.disabled ? "plain" : "danger"} onClick={() => void patch(r.id, { disabled: !r.disabled }, r.disabled ? "Enabled" : "Disabled and signed out")}>
                          {r.disabled ? "Enable" : "Disable"}
                        </Button>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table></div>
        )}
      </Card>
      <Card title="Roles">
        <dl className="kv">{(Object.keys(ROLE_HELP) as Role[]).map((r) => <Fragment key={r}><dt>{r}</dt><dd>{ROLE_HELP[r]}</dd></Fragment>)}</dl>
      </Card>

      <Modal title="Add admin" open={adding} onClose={() => setAdding(false)}
        footer={<><Button kind="ghost" onClick={() => setAdding(false)}>Cancel</Button><Button kind="primary" disabled={busy || !form.email} onClick={() => void add()}>Add</Button></>}>
        <Field label="Email"><input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></Field>
        <Field label="Name"><input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></Field>
        <Field label="Role" hint={ROLE_HELP[form.role]}>
          <select value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as Role })}>
            <option value="agent">Agent</option><option value="viewer">Viewer</option><option value="owner">Owner</option>
          </select>
        </Field>
      </Modal>
      <Modal title="One-time password" open={!!secret} onClose={() => setSecret(null)} footer={<Button kind="primary" onClick={() => setSecret(null)}>Done</Button>}>
        <p>Give this to <strong>{secret?.email}</strong> privately. It's shown only once; they choose their own password at first sign-in.</p>
        {secret && <Secret value={secret.pw} />}
      </Modal>
      <Modal title="Change my password" open={changing} onClose={() => setChanging(false)}>
        <ChangePassword onDone={() => setChanging(false)} />
      </Modal>
      {confirm.node}
    </>
  );
}
