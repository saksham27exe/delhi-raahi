import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { ApiError } from "../api";
import type { CrowdCategory } from "../types";

export function CrowdBadge({ level, short = false }: { level: CrowdCategory; short?: boolean }) {
  const { t } = useTranslation();
  return (
    <span className={`badge c-${level}`}>
      <span className="dot" aria-hidden="true" />
      {short ? t(`crowd.short.${level}`) : t(`crowd.${level}`)}
    </span>
  );
}

export function Loading({ label }: { label?: string }) {
  const { t } = useTranslation();
  return (
    <div className="row" role="status" aria-live="polite">
      <div className="spinner" aria-hidden="true" />
      <span className="muted">{label ?? t("common.loading")}</span>
    </div>
  );
}

export function ErrorBox({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { t } = useTranslation();
  const msg = error instanceof ApiError && error.status === 0
    ? t("common.errorNetwork")
    : t("common.errorGeneric", { msg: error instanceof Error ? error.message : String(error) });
  return (
    <div className="error stack" role="alert">
      <span>{msg}</span>
      {onRetry && <button className="btn" onClick={onRetry}>{t("common.retry")}</button>}
    </div>
  );
}

export function Empty({ title, hint, children }: { title: string; hint?: string; children?: ReactNode }) {
  return (
    <div className="card stack" style={{ textAlign: "center" }}>
      <h3>{title}</h3>
      {hint && <p className="muted small" style={{ margin: 0 }}>{hint}</p>}
      {children}
    </div>
  );
}

const paths: Record<string, string> = {
  plan: "M9 20l-5.4-2.7A1 1 0 013 16.4V4.6a1 1 0 011.4-.9L9 6m0 14l6-3m-6 3V6m6 11l4.6 2.3a1 1 0 001.4-.9V7.6a1 1 0 00-.6-.9L15 4m0 13V4m0 0L9 6",
  live: "M12 21s-7-6.2-7-11.5A7 7 0 0119 9.5C19 14.8 12 21 12 21zm0-9a2.5 2.5 0 100-5 2.5 2.5 0 000 5z",
  tickets: "M4 7a2 2 0 012-2h12a2 2 0 012 2v3a2 2 0 000 4v3a2 2 0 01-2 2H6a2 2 0 01-2-2v-3a2 2 0 000-4V7zm9-2v14",
  insights: "M4 20V10m6 10V4m6 16v-7m4 7H2",
  profile: "M12 12a4 4 0 100-8 4 4 0 000 8zm-7 9a7 7 0 0114 0",
  swap: "M7 4v16m0 0l-3-3m3 3l3-3M17 20V4m0 0l-3 3m3-3l3 3",
  locate: "M12 2v3m0 14v3M2 12h3m14 0h3m-10 5a5 5 0 100-10 5 5 0 000 10z",
  bus: "M6 17h12M7 17v2m10-2v2M6 4h12a1 1 0 011 1v11H5V5a1 1 0 011-1zm-1 7h14",
  walk: "M13 4a1.5 1.5 0 11-3 0 1.5 1.5 0 013 0zM9 21l2-6 2 2v4m-4-9l2-3 3 2 2 2M8 12l1-4",
};

export function Icon({ name, size = 22 }: { name: keyof typeof paths | string; size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
      <path d={paths[name]} />
    </svg>
  );
}
