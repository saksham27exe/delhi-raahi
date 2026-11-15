import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";

// ---------- data loading ----------
export function useLoad<T>(fn: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const seq = useRef(0);
  const reload = useCallback(async () => {
    const n = ++seq.current;
    setLoading(true);
    try {
      const d = await fn();
      if (n === seq.current) { setData(d); setError(null); }
    } catch (e) {
      if (n === seq.current) setError((e as Error).message);
    } finally {
      if (n === seq.current) setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  useEffect(() => { void reload(); }, [reload]);
  return { data, error, loading, reload, setData };
}

// ---------- formatting (Delhi time) ----------
const dtf = new Intl.DateTimeFormat("en-IN", { timeZone: "Asia/Kolkata", day: "numeric", month: "short", hour: "2-digit", minute: "2-digit", hour12: false });
const df = new Intl.DateTimeFormat("en-IN", { timeZone: "Asia/Kolkata", day: "numeric", month: "short", year: "numeric" });
export const fmtTime = (iso?: string | null) => (iso ? dtf.format(new Date(iso)) : "—");
export const fmtDate = (iso?: string | null) => (iso ? df.format(new Date(iso)) : "—");
export const fmtNum = (n?: number | null) => (n == null ? "—" : n.toLocaleString("en-IN"));
export const fmtRs = (n?: number | null) => (n == null ? "—" : `₹${n.toLocaleString("en-IN", { maximumFractionDigits: 2 })}`);
export function ago(iso?: string | null): string {
  if (!iso) return "—";
  const s = (Date.now() - new Date(iso).getTime()) / 1000;
  if (s < 60) return "just now";
  if (s < 3600) return `${Math.floor(s / 60)} min ago`;
  if (s < 86400) return `${Math.floor(s / 3600)} h ago`;
  return `${Math.floor(s / 86400)} d ago`;
}

// ---------- building blocks ----------
export function PageHead({ title, sub, actions }: { title: string; sub?: ReactNode; actions?: ReactNode }) {
  return (
    <header className="page-head">
      <div>
        <h1>{title}</h1>
        {sub && <p className="muted">{sub}</p>}
      </div>
      {actions && <div className="row gap">{actions}</div>}
    </header>
  );
}

export const Card = ({ title, actions, children, className = "" }: { title?: ReactNode; actions?: ReactNode; children: ReactNode; className?: string }) => (
  <section className={`card ${className}`}>
    {(title || actions) && <div className="card-head"><h2>{title}</h2>{actions}</div>}
    {children}
  </section>
);

type BtnProps = React.ButtonHTMLAttributes<HTMLButtonElement> & { kind?: "primary" | "ghost" | "danger" | "plain"; small?: boolean };
export const Button = ({ kind = "plain", small, className = "", ...p }: BtnProps) => (
  <button type="button" {...p} className={`btn btn-${kind} ${small ? "btn-sm" : ""} ${className}`} />
);

export const Badge = ({ tone = "neutral", children }: { tone?: "neutral" | "good" | "warn" | "bad" | "info" | "accent"; children: ReactNode }) => (
  <span className={`badge badge-${tone}`}>{children}</span>
);

export function Field({ label, hint, children }: { label: string; hint?: ReactNode; children: ReactNode }) {
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      {children}
      {hint && <span className="field-hint">{hint}</span>}
    </label>
  );
}

export const Spinner = () => <span className="spinner" role="status" aria-label="Loading" />;
export const ErrorNote = ({ error }: { error: string | null }) => (error ? <p className="error" role="alert">{error}</p> : null);
export const Empty = ({ children }: { children: ReactNode }) => <p className="empty">{children}</p>;

export function Stat({ label, value, sub, tone }: { label: string; value: ReactNode; sub?: ReactNode; tone?: "good" | "warn" | "bad" }) {
  return (
    <div className={`stat ${tone ? `stat-${tone}` : ""}`}>
      <span className="stat-label">{label}</span>
      <span className="stat-value">{value}</span>
      {sub && <span className="stat-sub">{sub}</span>}
    </div>
  );
}

export function Modal({ title, open, onClose, children, footer, wide }: { title: string; open: boolean; onClose: () => void; children: ReactNode; footer?: ReactNode; wide?: boolean }) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (open && !d.open) d.showModal();
    if (!open && d.open) d.close();
  }, [open]);
  return (
    <dialog ref={ref} className={`modal ${wide ? "modal-wide" : ""}`} onClose={onClose} onCancel={onClose} aria-labelledby="modal-title">
      {open && (
        <>
          <div className="modal-head"><h2 id="modal-title">{title}</h2><button className="icon-btn" aria-label="Close" onClick={onClose}>×</button></div>
          <div className="modal-body">{children}</div>
          {footer && <div className="modal-foot">{footer}</div>}
        </>
      )}
    </dialog>
  );
}

/** Asks before something that can't be undone; returns a promise of the answer. */
export function useConfirm() {
  const [state, setState] = useState<{ text: string; danger: boolean; resolve: (b: boolean) => void } | null>(null);
  const ask = (text: string, danger = true) => new Promise<boolean>((resolve) => setState({ text, danger, resolve }));
  const close = (b: boolean) => { state?.resolve(b); setState(null); };
  const node = (
    <Modal title="Are you sure?" open={!!state} onClose={() => close(false)}
      footer={<><Button kind="ghost" onClick={() => close(false)}>Cancel</Button><Button kind={state?.danger ? "danger" : "primary"} onClick={() => close(true)}>Confirm</Button></>}>
      <p>{state?.text}</p>
    </Modal>
  );
  return { ask, node };
}

// ---------- toasts ----------
const ToastCtx = createContext<(msg: string, tone?: "good" | "bad") => void>(() => {});
export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<{ id: number; msg: string; tone: string }[]>([]);
  const push = useCallback((msg: string, tone: "good" | "bad" = "good") => {
    const id = Date.now() + Math.random();
    setItems((x) => [...x, { id, msg, tone }]);
    setTimeout(() => setItems((x) => x.filter((t) => t.id !== id)), 4500);
  }, []);
  return (
    <ToastCtx.Provider value={push}>
      {children}
      <div className="toasts" aria-live="polite">{items.map((t) => <div key={t.id} className={`toast toast-${t.tone}`}>{t.msg}</div>)}</div>
    </ToastCtx.Provider>
  );
}
export const useToast = () => useContext(ToastCtx);

/** Runs an action, shows a toast for the result, and returns whether it worked. */
export function useAction() {
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const run = async (fn: () => Promise<unknown>, ok?: string) => {
    setBusy(true);
    try { await fn(); if (ok) toast(ok); return true; } catch (e) { toast((e as Error).message, "bad"); return false; } finally { setBusy(false); }
  };
  return { run, busy };
}

export function Pager({ page, pages, onPage }: { page: number; pages: number; onPage: (p: number) => void }) {
  if (pages <= 1) return null;
  return (
    <nav className="pager" aria-label="Pages">
      <Button small kind="ghost" disabled={page <= 1} onClick={() => onPage(page - 1)}>‹ Prev</Button>
      <span className="muted">Page {page} of {pages}</span>
      <Button small kind="ghost" disabled={page >= pages} onClick={() => onPage(page + 1)}>Next ›</Button>
    </nav>
  );
}

/** Copy-once secret (e.g. a one-time password) with a copy button. */
export function Secret({ value }: { value: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <div className="secret">
      <code>{value}</code>
      <Button small onClick={async () => { await navigator.clipboard.writeText(value); setCopied(true); }}>{copied ? "Copied" : "Copy"}</Button>
    </div>
  );
}
