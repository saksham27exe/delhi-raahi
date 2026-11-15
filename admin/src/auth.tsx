import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { api, onUnauthorized, setCsrf, type AdminUser } from "./api";

interface Auth {
  user: AdminUser | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
}

const Ctx = createContext<Auth | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AdminUser | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    try {
      const r = await api.get<{ admin: AdminUser; csrf: string }>("/me");
      setCsrf(r.csrf);
      setUser(r.admin);
    } catch {
      setUser(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { onUnauthorized(() => setUser(null)); void refresh(); }, [refresh]);

  const login = async (email: string, password: string) => {
    const r = await api.post<{ admin: AdminUser; csrf: string }>("/login", { email, password });
    setCsrf(r.csrf);
    setUser(r.admin);
  };
  const logout = async () => {
    try { await api.post("/logout"); } finally { setUser(null); setCsrf(""); }
  };
  return <Ctx.Provider value={{ user, loading, login, logout, refresh }}>{children}</Ctx.Provider>;
}

export function useAuth(): Auth {
  const c = useContext(Ctx);
  if (!c) throw new Error("useAuth outside AuthProvider");
  return c;
}
