import { StyleSheet, View } from "react-native";
import { useTranslation } from "react-i18next";
import { useApp } from "../lib/state";
import { space } from "../lib/theme";
import type { Journey } from "../lib/types";
import type { MapLine, MapMarker } from "./BusMap";
import { Icon } from "./Icon";
import { RoutePill, T } from "./ui";

export function fareText(j: Journey): string | null {
  if (!j.fare) return null;
  const a = j.fare.total_digital_min, b = j.fare.total_digital_max;
  return a === b ? `₹${a}` : `₹${a}–${b}`;
}

/** Thin bar of crowd colour per segment, proportional to riding time. */
export function CrowdStrip({ j }: { j: Journey }) {
  const { theme } = useApp();
  const segs = j.legs.flatMap((l) => l.segments);
  const total = segs.reduce((a, s) => a + Math.max(s.minutes, 0.5), 0) || 1;
  return (
    <View style={[st.strip, { backgroundColor: theme.surface }]} importantForAccessibility="no-hide-descendants">
      {segs.map((s, i) => (
        <View key={i} style={{ flex: Math.max(s.minutes, 0.5) / total, backgroundColor: theme.crowd[s.crowd?.category ?? "UNKNOWN"].line }} />
      ))}
    </View>
  );
}

export function LegChain({ j }: { j: Journey }) {
  const { theme } = useApp();
  return (
    <View style={st.chain}>
      {j.walk.origin_min >= 1 && <Icon name="walk" size={16} color={theme.muted} />}
      {j.legs.map((l, i) => (
        <View key={i} style={st.chain}>
          {(i > 0 || j.walk.origin_min >= 1) && <Icon name="chevron" size={14} color={theme.muted} />}
          <RoutePill route={l.route} small />
        </View>
      ))}
      {j.walk.destination_min >= 1 && <><Icon name="chevron" size={14} color={theme.muted} /><Icon name="walk" size={16} color={theme.muted} /></>}
    </View>
  );
}

export function journeyMap(j: Journey): { lines: MapLine[]; markers: MapMarker[] } {
  const lines: MapLine[] = j.legs.flatMap((l, li) => l.segments.map((s, si) => ({
    id: `${li}-${si}`, category: s.crowd?.category ?? "UNKNOWN",
    coords: [{ latitude: s.lat, longitude: s.lon }, { latitude: s.to_lat, longitude: s.to_lon }],
  })));
  const first = j.legs[0].segments[0];
  const lastLeg = j.legs[j.legs.length - 1];
  const last = lastLeg.segments[lastLeg.segments.length - 1];
  const markers: MapMarker[] = [];
  if (first) markers.push({ id: "from", kind: "from", coord: { latitude: first.lat, longitude: first.lon }, title: j.legs[0].board_stop });
  if (last) markers.push({ id: "to", kind: "to", coord: { latitude: last.to_lat, longitude: last.to_lon }, title: lastLeg.alight_stop });
  j.legs.slice(1).forEach((l, i) => {
    const s = l.segments[0];
    if (s) markers.push({ id: `x${i}`, kind: "transfer", coord: { latitude: s.lat, longitude: s.lon }, title: l.board_stop });
  });
  return { lines, markers };
}

export function Times({ j }: { j: Journey }) {
  const { t } = useTranslation();
  return (
    <View style={{ flexDirection: "row", alignItems: "baseline", gap: space.sm }}>
      <T v="h3">{j.departure} – {j.arrival}</T>
      <T v="small" muted>{Math.round(j.duration_minutes)} {t("common.min")}</T>
    </View>
  );
}

const st = StyleSheet.create({
  strip: { flexDirection: "row", height: 6, borderRadius: 3, overflow: "hidden" },
  chain: { flexDirection: "row", alignItems: "center", gap: 4, flexWrap: "wrap" },
});
