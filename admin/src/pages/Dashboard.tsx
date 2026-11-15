import { api } from "../api";
import { Badge, Card, ErrorNote, PageHead, Spinner, useLoad } from "../ui";

interface Health { status: string; components: Record<string, boolean>; load_errors: Record<string, string>; accounts_store: string | null; model_version?: string; data_window?: unknown }

const NAMES: Record<string, string> = {
  gtfs_network: "Bus network (GTFS)", crowd_model: "Crowd model", fares: "Fares & AC tags", demo_tickets: "Demo tickets",
  accounts: "Accounts", live_feed: "Live bus feed",
};

/** System status. Phase E turns this into the full operations dashboard. */
export function Dashboard() {
  const { data, error, loading } = useLoad(() => api.get<Health>("/api/v1/health"));
  return (
    <>
      <PageHead title="Dashboard" sub="How DelhiRaahi is running right now." />
      <ErrorNote error={error} />
      {loading && !data ? <Spinner /> : data && (
        <Card title="System" actions={<Badge tone={data.status === "ok" ? "good" : "warn"}>{data.status}</Badge>}>
          <div className="table-wrap"><table><tbody>
            {Object.entries(data.components).map(([k, v]) => (
              <tr key={k}><td>{NAMES[k] ?? k}</td><td className="num">{v ? <Badge tone="good">Up</Badge> : <Badge tone="bad">Down</Badge>}</td></tr>
            ))}
          </tbody></table></div>
          {Object.keys(data.load_errors).length > 0 && (
            <div className="stack" style={{ marginTop: 12 }}>
              {Object.entries(data.load_errors).map(([k, v]) => <p key={k} className="error"><strong>{k}:</strong> {v}</p>)}
            </div>
          )}
        </Card>
      )}
    </>
  );
}
