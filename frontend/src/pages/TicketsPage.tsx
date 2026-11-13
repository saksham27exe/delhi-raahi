import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useLocation } from "react-router-dom";
import { api } from "../api";
import { QRCode } from "../components/QRCode";
import { Empty } from "../components/ui";
import { store } from "../storage";
import type { DemoTicket } from "../types";

function TicketCard({ tk, open, onRemove }: { tk: DemoTicket; open: boolean; onRemove: () => void }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language === "hi" ? "hi" : "en";
  const [show, setShow] = useState(open);
  const [check, setCheck] = useState<string | null>(null);
  const expired = new Date(tk.valid_until).getTime() < Date.now();
  const until = new Date(tk.valid_until).toLocaleString(lang === "hi" ? "hi-IN" : "en-IN", { dateStyle: "medium", timeStyle: "short" });
  return (
    <li className="card ticket stack" aria-label={`${tk.ticket_id}, ${tk.watermark[lang]}`}>
      <div className="wm" aria-hidden="true">DEMO</div>
      <div className="banner" role="note">{tk.watermark[lang]}</div>
      <div className="row between">
        <strong>{tk.ticket_id}</strong>
        <span className={`badge ${expired ? "c-UNKNOWN" : "c-LOW"}`}>{expired ? t("tickets.expired") : t("tickets.active")}</span>
      </div>
      {tk.legs.map((l, i) => (
        <div key={i}><span className="route-pill">{l.route}</span> <span className="small">{l.board_stop} → {l.alight_stop}</span></div>
      ))}
      <div className="row between small">
        <span>{t("tickets.passengers", { count: tk.passengers })}</span>
        <strong>₹{tk.total}</strong>
      </div>
      <span className="muted small">{t("tickets.validUntil", { time: until })}</span>
      {show && <QRCode value={tk.qr_payload} label={`QR ${tk.ticket_id} — ${tk.watermark[lang]}`} />}
      {check && <p className="notice small" role="status" style={{ margin: 0 }}>{check}</p>}
      <div className="row">
        <button className="btn" onClick={() => setShow((s) => !s)} aria-expanded={show}>{t("tickets.show")}</button>
        <button className="btn" onClick={() => api.verify(tk.qr_payload)
          .then((r) => setCheck(r.valid_signature ? t("tickets.verified", { status: r.status }) : r.status))
          .catch((e) => setCheck(String(e.message ?? e)))}>{t("tickets.verify")}</button>
        <button className="btn ghost" onClick={onRemove}>{t("tickets.remove")}</button>
      </div>
    </li>
  );
}

export function TicketsPage() {
  const { t } = useTranslation();
  const loc = useLocation();
  const just = (loc.state as { justBooked?: string } | null)?.justBooked;
  const [tickets, setTickets] = useState(() => store.tickets());
  return (
    <div className="stack">
      <h1>{t("tickets.title")}</h1>
      {just && <p className="notice" role="status">{t("booking.done")}</p>}
      {tickets.length === 0
        ? <Empty title={t("tickets.empty")} hint={t("tickets.emptyHint")}><Link className="btn primary" to="/">{t("tabs.plan")}</Link></Empty>
        : (
          <ul className="stack" style={{ listStyle: "none", padding: 0, margin: 0 }}>
            {tickets.map((tk) => (
              <TicketCard key={tk.ticket_id} tk={tk} open={tk.ticket_id === just}
                onRemove={() => { store.removeTicket(tk.ticket_id); setTickets(store.tickets()); }} />
            ))}
          </ul>
        )}
      <p className="muted small">{t("tickets.stored")}</p>
    </div>
  );
}
