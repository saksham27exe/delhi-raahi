import "leaflet/dist/leaflet.css";
import L from "leaflet";
import { useEffect } from "react";
import { CircleMarker, MapContainer, Polyline, TileLayer, Tooltip, useMap } from "react-leaflet";
import type { CrowdCategory } from "../types";

const DELHI: [number, number] = [28.6315, 77.2167];
const COLOR: Record<CrowdCategory, string> = {
  LOW: "#16a34a", MODERATE: "#ca8a04", HIGH: "#ea580c", VERY_HIGH: "#dc2626", UNKNOWN: "#6b7280",
};

export interface MapLine { id: string; points: [number, number][]; category: CrowdCategory; label: string }
export interface MapPoint { id: string; lat: number; lon: number; label: string; kind: "bus" | "stop" | "me" | "stale"; onClick?: () => void }

function Fit({ lines, points, fitKey }: { lines: MapLine[]; points: MapPoint[]; fitKey: string }) {
  const map = useMap();
  useEffect(() => {
    const all: [number, number][] = [...lines.flatMap((l) => l.points), ...points.map((p) => [p.lat, p.lon] as [number, number])];
    if (all.length > 1) map.fitBounds(L.latLngBounds(all), { padding: [24, 24], maxZoom: 16 });
    else if (all.length === 1) map.setView(all[0], 15);
    // refit only when the caller says the subject changed, not on every refresh of bus positions
  }, [fitKey]); // eslint-disable-line react-hooks/exhaustive-deps
  return null;
}

/** Map with crowd-coloured lines and markers. Colour is never the only signal: every line and
 * marker has a text tooltip, and the same data is always listed beside the map. */
export function MapView({ lines = [], points = [], label, height = 340, fitKey }: {
  lines?: MapLine[]; points?: MapPoint[]; label: string; height?: number; fitKey?: string;
}) {
  return (
    <div className="map" style={{ height }} role="region" aria-label={label}>
      <MapContainer center={DELHI} zoom={11} style={{ height: "100%", width: "100%" }} scrollWheelZoom={false}>
        <TileLayer attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
        {lines.map((l) => (
          <Polyline key={l.id} positions={l.points} pathOptions={{ color: COLOR[l.category], weight: 6, opacity: 0.9 }}>
            <Tooltip>{l.label}</Tooltip>
          </Polyline>
        ))}
        {points.map((p) => (
          <CircleMarker key={p.id} center={[p.lat, p.lon]} radius={p.kind === "bus" ? 7 : p.kind === "me" ? 8 : 6}
            pathOptions={{
              color: "#fff", weight: 2, fillOpacity: 1,
              fillColor: p.kind === "bus" ? "#0f766e" : p.kind === "me" ? "#2563eb" : p.kind === "stale" ? "#9ca3af" : "#111827",
            }}
            eventHandlers={p.onClick ? { click: p.onClick } : undefined}>
            <Tooltip>{p.label}</Tooltip>
          </CircleMarker>
        ))}
        <Fit lines={lines} points={points} fitKey={fitKey ?? lines.map((l) => l.id).join()} />
      </MapContainer>
    </div>
  );
}
