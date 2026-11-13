import { useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";
import { BrowserRouter, Link, NavLink, Route, Routes, useLocation } from "react-router-dom";
import { Icon } from "./components/ui";
import { InsightsPage } from "./pages/InsightsPage";
import { JourneyPage } from "./pages/JourneyPage";
import { LivePage } from "./pages/LivePage";
import { PlanPage } from "./pages/PlanPage";
import { ProfilePage } from "./pages/ProfilePage";
import { TicketsPage } from "./pages/TicketsPage";
import { PlanProvider, SettingsProvider, useSettings } from "./state";

const TABS = [
  { to: "/", key: "plan", end: true },
  { to: "/live", key: "live" },
  { to: "/tickets", key: "tickets" },
  { to: "/insights", key: "insights" },
  { to: "/profile", key: "profile" },
] as const;

function Shell() {
  const { t } = useTranslation();
  const { settings, update } = useSettings();
  const loc = useLocation();
  const mainRef = useRef<HTMLElement>(null);
  const first = useRef(true);

  // move focus to the new page's heading so screen readers announce the page change (not on first load)
  useEffect(() => {
    if (first.current) { first.current = false; return; }
    const h = mainRef.current?.querySelector("h1");
    if (h) { h.setAttribute("tabindex", "-1"); h.focus({ preventScroll: true }); }
    window.scrollTo(0, 0);
  }, [loc.pathname]);

  return (
    <div className="app">
      <a href="#main" className="skip">{t("app.skip")}</a>
      <header className="topbar">
        <Link to="/" className="brand">
          <img src="/icon.svg" alt="" />
          <span>{t("app.name")}<small>{t("app.tagline")}</small></span>
        </Link>
        <button className="btn" onClick={() => update({ lang: settings.lang === "en" ? "hi" : "en" })}
          aria-label={settings.lang === "en" ? "हिंदी में बदलें" : "Switch to English"}>
          {settings.lang === "en" ? "हिंदी" : "English"}
        </button>
      </header>
      <main id="main" ref={mainRef}>
        <Routes>
          <Route path="/" element={<PlanPage />} />
          <Route path="/journey/:idx" element={<JourneyPage />} />
          <Route path="/live" element={<LivePage />} />
          <Route path="/tickets" element={<TicketsPage />} />
          <Route path="/insights" element={<InsightsPage />} />
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="*" element={<PlanPage />} />
        </Routes>
      </main>
      <nav className="tabbar" aria-label="Main">
        {TABS.map((tab) => (
          <NavLink key={tab.key} to={tab.to} end={"end" in tab}>
            <Icon name={tab.key} />
            <span>{t(`tabs.${tab.key}`)}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  );
}

export default function App() {
  return (
    <SettingsProvider>
      <PlanProvider>
        <BrowserRouter>
          <Shell />
        </BrowserRouter>
      </PlanProvider>
    </SettingsProvider>
  );
}
