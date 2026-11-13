import type { DayBucket } from "../types";

const FILL: Record<string, string> = {
  LOW: "var(--low)", MODERATE: "var(--mod)", HIGH: "var(--high)", VERY_HIGH: "var(--vhigh)", UNKNOWN: "var(--unk-bg)",
};

/** Mean crowd index per half hour as bars. A text table with the same data follows for screen readers. */
export function DayChart({ buckets, caption, levelText }: {
  buckets: DayBucket[]; caption: string; levelText: (c: string) => string;
}) {
  const W = 600, H = 150, pad = 18;
  const slot = (t: string) => { const [h, m] = t.split(":").map(Number); return (h * 60 + m) / 30; };
  const bw = (W - 2 * pad) / 48;
  return (
    <figure style={{ margin: 0 }}>
      <svg className="daychart" viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" aria-hidden="true">
        {buckets.map((b) => {
          const v = b.mean_crowd_index ?? 0.03;
          const h = Math.max(3, v * (H - 2 * pad));
          return <rect key={b.time} x={pad + slot(b.time) * bw + 1} y={H - pad - h} width={bw - 2} height={h} rx={2} fill={FILL[b.category]} />;
        })}
        {[0, 6, 12, 18, 24].map((h) => (
          <text key={h} x={pad + h * 2 * bw} y={H - 4} textAnchor="middle">{String(h).padStart(2, "0")}:00</text>
        ))}
      </svg>
      <figcaption className="muted small">{caption}</figcaption>
      <table className="sr-only">
        <caption>{caption}</caption>
        <tbody>{buckets.map((b) => <tr key={b.time}><th scope="row">{b.time}</th><td>{levelText(b.category)}</td></tr>)}</tbody>
      </table>
    </figure>
  );
}
