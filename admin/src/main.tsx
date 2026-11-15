import { StrictMode, useState } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Navigate, NavLink, Route, Routes, useLocation } from "react-router-dom";
import { can, type Role } from "./api";
import { AuthProvider, useAuth } from "./auth";
import { Spinner, ToastProvider } from "./ui";
import { ChangePassword, Login } from "./pages/Login";
import { Admins } from "./pages/Admins";
import { Audit } from "./pages/Audit";
import { Dashboard } from "./pages/Dashboard";
import { Support } from "./pages/support";
import { Users } from "./pages/users";
import { Alerts } from "./pages/Alerts";
import { DataFixes } from "./pages/DataFixes";
import "./styles.css";

interface Page { path: string; label: string; icon: string; role: Role; group: string; el: JSX.Element }

// Each phase adds its pages here.
const PAGES: Page[] = [
  { path: "/", label: "Dashboard", icon: "◧", role: "viewer", group: "Overview", el: <Dashboard /> },
  { path: "/support", label: "Support desk", icon: "✉", role: "viewer", group: "People", el: <Support /> },
  { path: "/users", label: "Users & bookings", icon: "☺", role: "viewer", group: "People", el: <Users /> },
  { path: "/alerts", label: "Service alerts", icon: "⚑", role: "viewer", group: "Network", el: <Alerts /> },
  { path: "/data", label: "Data fixes", icon: "✎", role: "viewer", group: "Network", el: <DataFixes /> },
  { path: "/audit", label: "Audit log", icon: "≣", role: "agent", group: "Admin", el: <Audit /> },
  { path: "/admins", label: "Admins", icon: "⚿", role: "owner", group: "Admin", el: <Admins /> },
];

function Shell() {
  const { user, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const loc = useLocation();
  const pages = PAGES.filter((p) => can(user, p.role));
  const groups = [...new Set(pages.map((p) => p.group))];
  return (
    <div className="shell">
      <aside className={`side ${open ? "open" : ""}`} onClick={() => setOpen(false)}>
        <div className="brand"><span className="brand-mark">DR</span><span>DelhiRaahi<small>Admin</small></span></div>
        <nav className="nav" aria-label="Main">
          {groups.map((g) => (
            <div key={g}>
              <div className="nav-group">{g}</div>
              {pages.filter((p) => p.group === g).map((p) => (
                <NavLink key={p.path} to={p.path} end={p.path === "/"}><span aria-hidden>{p.icon}</span>{p.label}</NavLink>
              ))}
            </div>
          ))}
        </nav>
        <div className="side-foot stack">
          <div><strong>{user?.name}</strong><div className="muted small">{user?.email} · {user?.role}</div></div>
          <button className="btn btn-sm" onClick={() => void logout()}>Sign out</button>
        </div>
      </aside>
      <main className="main">
        <div className="content">
          <button className="btn btn-sm menu-btn" style={{ alignSelf: "flex-start" }} onClick={() => setOpen(true)} aria-label="Open menu">☰ Menu</button>
          <Routes location={loc}>
            {pages.map((p) => <Route key={p.path} path={p.path === "/" ? "/" : `${p.path}/*`} element={p.el} />)}
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </div>
      </main>
    </div>
  );
}

function Gate() {
  const { user, loading } = useAuth();
  if (loading) return <div className="login"><Spinner /></div>;
  if (!user) return <Login />;
  if (user.must_change) return <ChangePassword forced />;
  return <Shell />;
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter basename="/admin">
      <ToastProvider>
        <AuthProvider><Gate /></AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  </StrictMode>,
);
