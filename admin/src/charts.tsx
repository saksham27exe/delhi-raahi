import { useState } from "react";

// Single-series charts in the brand chart colour (validated: #A63D2A on light, #D66F55 on dark).
// Each has a hover/focus tooltip and a table view, so nothing depends on colour or the mouse.

export interface Point { label: string; value: number }

/** Last `days` calendar days (YYYY-MM-DD) with zeros where there is no data. */
export function fillDays(rows: { day: string }[], key: string, days = 30): Point[] {
  const by = new Map(rows.map((r) => [r.day, Number((r as Record<string, unknown>)[key] ?? 0)]));
  const out: Point[] = [];
  for (let i = days - 1; i >= 0; i--) {
    const d = new Date(Date.now() - i * 86400000);
    const iso = new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
    out.push({ label: iso, value: by.get(iso) ?? 0 });
  }
  return out;
}

const dayLabel = (iso: string) => new Date(`${iso}T12:00:00`).toLocaleDateString("en-IN", { day: "numeric", month: "short" });

export function DailyBars({ data, title, unit }: { data: Point[]; title: string; unit: string }) {
  const [hover, setHover] = useState<number | null>(null);
  const W = 320, H = 130, padL = 22, padB = 18, padT = 6;
  const max = Math.max(1, ...data.map((d) => d.value));
  const nice = max <= 4 ? 4 : Math.ceil(max / 4) * 4;
  const bw = (W - padL) / data.length;
  const y = (v: number) => padT + (H - padT - padB) * (1 - v / nice);
  const total = data.reduce((s, d) => s + d.value, 0);
  const h = hover != null ? data[hover] : null;
  return (
    <figure className="chart" style={{ margin: 0, position: "relative" }}>
      <figcaption className="row between" style={{ marginBottom: 6 }}>
        <span className="small muted">{title}</span><strong>{total.toLocaleString("en-IN")} <span className="muted small">in {data.length} days</span></strong>
      </figcaption>
      <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label={`${title}: ${total} in ${data.length} days`} onMouseLeave={() => setHover(null)}>
        {[0, 0.5, 1].map((f) => (
          <g key={f}>
            <line x1={padL} x2={W} y1={y(nice * f)} y2={y(nice * f)} stroke="var(--line)" strokeWidth={1} />
            <text x={padL - 6} y={y(nice * f) + 4} textAnchor="end">{Math.round(nice * f)}</text>
          </g>
        ))}
        {data.map((d, i) => {
          const x = padL + i * bw + 1, w = Math.max(2, bw - 2), top = y(d.value), base = y(0);
          const hgt = base - top;
          return (
            <g key={d.label} onMouseEnter={() => setHover(i)} onFocus={() => setHover(i)} onBlur={() => setHover(null)} tabIndex={0}
              aria-label={`${dayLabel(d.label)}: ${d.value} ${unit}`}>
              <rect x={x - 1} y={padT} width={bw} height={base - padT} fill="transparent" />
              {d.value > 0 && (hgt > 4
                ? <path d={`M${x},${base} V${top + 4} q0,-4 4,-4 h${w - 8} q4,0 4,4 V${base} Z`} fill="var(--chart)" opacity={hover == null || hover === i ? 1 : 0.55} />
                : <rect x={x} y={base - Math.max(2, hgt)} width={w} height={Math.max(2, hgt)} fill="var(--chart)" />)}
            </g>
          );
        })}
        {[0, Math.floor(data.length / 2), data.length - 1].map((i) => data[i] && (
          <text key={i} x={padL + i * bw + bw / 2} y={H - 6} textAnchor={i === 0 ? "start" : i === data.length - 1 ? "end" : "middle"}>{dayLabel(data[i].label)}</text>
        ))}
      </svg>
      {h && hover != null && (
        <div className="tip" style={{ left: `${((padL + hover * bw + bw / 2) / W) * 100}%` }}>
          <strong>{h.value.toLocaleString("en-IN")}</strong> {unit}<div className="muted small">{dayLabel(h.label)}</div>
        </div>
      )}
      <details className="small"><summary className="muted">Table</summary>
        <table><tbody>{data.filter((d) => d.value).map((d) => <tr key={d.label}><td>{dayLabel(d.label)}</td><td className="num">{d.value}</td></tr>)}</tbody></table>
      </details>
    </figure>
  );
}

/** Ranked horizontal bars with the value written at the end (direct labels; no legend needed). */
export function RankBars({ data, onPick }: { data: Point[]; onPick?: (label: string) => void }) {
  const max = Math.max(1, ...data.map((d) => d.value));
  return (
    <div role="list">
      {data.map((d) => (
        <div key={d.label} role="listitem" className="bar-row" title={`${d.label}: ${d.value}`}>
          {onPick ? <button className="link-btn" onClick={() => onPick(d.label)}>{d.label}</button> : <span>{d.label}</span>}
          <div className="bar-track"><div className="bar-fill" style={{ width: `${(d.value / max) * 100}%` }} /></div>
          <span className="num">{d.value}</span>
        </div>
      ))}
    </div>
  );
}
