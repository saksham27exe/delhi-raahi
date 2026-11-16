// Talks to /api/v1/admin. The session is an HttpOnly cookie; changes also send the CSRF token.

export type Role = "viewer" | "agent" | "owner";
export interface AdminUser { id: number; email: string; name: string; role: Role; must_change: boolean }

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

let csrf = "";
export const setCsrf = (t: string) => { csrf = t; };
let onSignedOut: () => void = () => {};
export const onUnauthorized = (fn: () => void) => { onSignedOut = fn; };

type Query = Record<string, string | number | boolean | undefined | null>;

function url(path: string, q?: Query): string {
  const u = new URL((path.startsWith("/api") ? path : `/api/v1/admin${path}`), location.origin);
  Object.entries(q ?? {}).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== "") u.searchParams.set(k, String(v)); });
  return u.pathname + u.search;
}

async function call<T>(method: string, path: string, body?: unknown, q?: Query): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (method !== "GET") headers["X-CSRF-Token"] = csrf;
  let r: Response;
  try {
    r = await fetch(url(path, q), { method, headers, credentials: "same-origin", body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError(0, "Can't reach the server. Check that the backend is running.");
  }
  if (r.status === 401 && !path.endsWith("/login")) onSignedOut();
  const ct = r.headers.get("content-type") ?? "";
  const data = ct.includes("json") ? await r.json() : await r.text();
  if (!r.ok) {
    const d = (data as { detail?: unknown })?.detail;
    const msg = typeof d === "string" ? d : Array.isArray(d) ? d.map((x: { msg?: string }) => x.msg).join("; ") : `Request failed (${r.status})`;
    throw new ApiError(r.status, msg);
  }
  return data as T;
}

export const api = {
  get: <T>(path: string, q?: Query) => call<T>("GET", path, undefined, q),
  post: <T>(path: string, body?: unknown) => call<T>("POST", path, body ?? {}),
  patch: <T>(path: string, body: unknown) => call<T>("PATCH", path, body),
  del: <T>(path: string) => call<T>("DELETE", path),
  /** Download a CSV (or any file) the server makes, keeping the session cookie. */
  download: async (path: string, q?: Query) => {
    let r: Response;
    try {
      r = await fetch(url(path, q), { credentials: "same-origin" });
    } catch {
      throw new ApiError(0, "Can't reach the server. Check that the backend is running.");
    }
    if (r.status === 401) onSignedOut();
    if (!r.ok) throw new ApiError(r.status, r.status === 403 ? "Your role can't export this." : `Download failed (${r.status})`);
    const name = /filename="?([^"]+)"?/.exec(r.headers.get("content-disposition") ?? "")?.[1] ?? "export.csv";
    const a = document.createElement("a");
    a.href = URL.createObjectURL(await r.blob());
    a.download = name;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 5000);
  },
};

export const roleRank: Record<Role, number> = { viewer: 0, agent: 1, owner: 2 };
export const can = (u: AdminUser | null, role: Role) => !!u && roleRank[u.role] >= roleRank[role];
