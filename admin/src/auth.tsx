import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { api, ApiError, onUnauthorized, setCsrf, type AdminUser } from "./api";

interface Auth {
  user: AdminUser | null;
  loading: boolean;
  /** Set when the server could not be asked (down, or the admin store is off): not the same as signed out. */
  unreachable: string | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
}

const Ctx = createContext<Auth | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AdminUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [unreachable, setUnreachable] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      const r = await api.get<{ admin: AdminUser; csrf: string }>("/me");
      setCsrf(r.csrf);
      setUser(r.admin);
      setUnreachable(null);
    } catch (e) {
      setUser(null);
      const status = e instanceof ApiError ? e.status : 0;
      setUnreachable(status === 401 ? null : (e as Error).message || "The server is not answering.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { onUnauthorized(() => setUser(null)); void refresh(); }, [refresh]);

  const login = async (email: string, password: string) => {
    const r = await api.post<{ admin: AdminUser; csrf: string }>("/login", { email, password });
    setCsrf(r.csrf);
    setUser(r.admin);
    setUnreachable(null);
  };
  const logout = async () => {
    try {
      await api.post("/logout");
    } catch {
      // The server did not end the session (e.g. another tab signed in since): ask again with a fresh token.
      try { const r = await api.get<{ csrf: string }>("/me"); setCsrf(r.csrf); await api.post("/logout"); } catch { /* already signed out */ }
    } finally { setUser(null); setCsrf(""); }
  };
  return <Ctx.Provider value={{ user, loading, unreachable, login, logout, refresh }}>{children}</Ctx.Provider>;
}

export function useAuth(): Auth {
  const c = useContext(Ctx);
  if (!c) throw new Error("useAuth outside AuthProvider");
  return c;
}
