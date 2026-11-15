import { useState, type FormEvent } from "react";
import { api } from "../api";
import { useAuth } from "../auth";
import { Button, ErrorNote, Field } from "../ui";

export function Login() {
  const { login } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try { await login(email, password); } catch (err) { setError((err as Error).message); } finally { setBusy(false); }
  };
  return (
    <div className="login">
      <form className="card" onSubmit={submit}>
        <div className="brand" style={{ padding: 0 }}><span className="brand-mark">DR</span><span>DelhiRaahi<small>Admin</small></span></div>
        <h1>Sign in</h1>
        <Field label="Email"><input type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} autoFocus /></Field>
        <Field label="Password"><input type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} /></Field>
        <ErrorNote error={error} />
        <Button kind="primary" type="submit" disabled={busy}>{busy ? "Signing in…" : "Sign in"}</Button>
        <p className="muted small" style={{ margin: 0 }}>Admin accounts are created by an owner. Sessions end after 30 idle minutes.</p>
      </form>
    </div>
  );
}

export function ChangePassword({ forced = false, onDone }: { forced?: boolean; onDone?: () => void }) {
  const { refresh, logout } = useAuth();
  const [old, setOld] = useState("");
  const [pw, setPw] = useState("");
  const [pw2, setPw2] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (pw !== pw2) { setError("The new passwords don't match."); return; }
    setBusy(true);
    setError(null);
    try { await api.post("/me/password", { old_password: old, new_password: pw }); await refresh(); onDone?.(); }
    catch (err) { setError((err as Error).message); } finally { setBusy(false); }
  };
  return (
    <div className={forced ? "login" : ""}>
      <form className="card" onSubmit={submit}>
        <h1>{forced ? "Choose your password" : "Change password"}</h1>
        {forced && <p className="muted" style={{ margin: 0 }}>You signed in with a one-time password. Choose your own to continue.</p>}
        <Field label={forced ? "One-time password" : "Current password"}><input type="password" autoComplete="current-password" required value={old} onChange={(e) => setOld(e.target.value)} /></Field>
        <Field label="New password" hint="At least 12 characters, with upper- and lower-case letters and a digit.">
          <input type="password" autoComplete="new-password" required minLength={12} value={pw} onChange={(e) => setPw(e.target.value)} />
        </Field>
        <Field label="New password again"><input type="password" autoComplete="new-password" required value={pw2} onChange={(e) => setPw2(e.target.value)} /></Field>
        <ErrorNote error={error} />
        <div className="row gap">
          <Button kind="primary" type="submit" disabled={busy}>Save password</Button>
          {forced && <Button kind="ghost" onClick={() => void logout()}>Sign out</Button>}
        </div>
      </form>
    </div>
  );
}
