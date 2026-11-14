import { router } from "expo-router";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { ScrollView, StyleSheet, TextInput, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import Svg, { Rect } from "react-native-svg";
import { BusMap } from "../components/BusMap";
import { Chip, CrowdBadge, Divider, ErrorView, IconButton, Loading, RoutePill, Row, T } from "../components/ui";
import { api, delhiNow } from "../lib/api";
import { useApp } from "../lib/state";
import { font, MAX_WIDTH, radius, space } from "../lib/theme";
import type { CrowdCategory, DayBucket, Hotspot } from "../lib/types";

const LEVEL: Record<CrowdCategory, number> = { LOW: 1, MODERATE: 2, HIGH: 3, VERY_HIGH: 4, UNKNOWN: 0 };

/** One bar per time bucket; height = crowd level, colour = category (labelled for screen readers). */
function DayChart({ buckets }: { buckets: DayBucket[] }) {
  const { theme } = useApp();
  const { t } = useTranslation();
  const [w, setW] = useState(0);
  const H = 120;
  const bw = buckets.length ? w / buckets.length : 0;
  const ticks = buckets.filter((b) => b.time.endsWith(":00") && Number(b.time.slice(0, 2)) % 4 === 0);
  return (
    <View onLayout={(e) => setW(e.nativeEvent.layout.width)} accessible
      accessibilityLabel={buckets.filter((b) => b.category !== "UNKNOWN").map((b) => `${b.time} ${t(`crowd.short.${b.category}`)}`).join(", ")}>
      <Svg width={w} height={H}>
        {buckets.map((b, i) => {
          const h = (LEVEL[b.category] / 4) * (H - 4);
          return <Rect key={b.time} x={i * bw + 1} y={H - h} width={Math.max(1, bw - 2)} height={h} rx={2}
            fill={theme.crowd[b.category].line} />;
        })}
      </Svg>
      <View style={{ height: 16 }}>
        {ticks.map((b) => (
          <T key={b.time} v="tiny" muted style={{ position: "absolute", left: buckets.indexOf(b) * bw }}>{b.time}</T>
        ))}
      </View>
    </View>
  );
}

const pct = (x?: number) => (x == null ? "–" : `${Math.round(x * 1000) / 10}%`);
const num = (x?: number, d = 2) => (x == null ? "–" : x.toFixed(d));

export default function Insights() {
  const { t } = useTranslation();
  const { theme } = useApp();
  const insets = useSafeAreaInsets();
  const today = delhiNow().date;

  // route day profile
  const [q, setQ] = useState("");
  const [routes, setRoutes] = useState<{ route_id: string; route: string; description: string }[]>([]);
  const [route, setRoute] = useState<{ route_id: string; route: string } | null>(null);
  const [day, setDay] = useState<{ buckets: DayBucket[]; quietest: string | null; busiest: string | null } | null>(null);
  const [dayErr, setDayErr] = useState<unknown>(null);

  // hotspots
  const [time, setTime] = useState("08:30");
  const [spots, setSpots] = useState<Hotspot[] | null>(null);
  const [spotErr, setSpotErr] = useState<unknown>(null);

  // model card
  const [card, setCard] = useState<Record<string, any> | null>(null);

  useEffect(() => {
    if (q.trim().length < 1 || route?.route === q.trim()) { setRoutes([]); return; }
    const h = setTimeout(() => api.searchRoutes(q.trim()).then((r) => setRoutes(r.results.slice(0, 6))).catch(() => setRoutes([])), 220);
    return () => clearTimeout(h);
  }, [q]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!route) return;
    setDay(null); setDayErr(null);
    api.routeDay(route.route_id, today).then(setDay).catch(setDayErr);
  }, [route, today]);

  useEffect(() => {
    setSpots(null); setSpotErr(null);
    api.hotspots(today, time).then((r) => setSpots(r.segments)).catch(setSpotErr);
  }, [time, today]);

  useEffect(() => { api.modelCard().then(setCard).catch(() => setCard({})); }, []);

  const rm = card?.route_model?.test;
  const sm = card?.segment_model;
  const fv = card?.fare_validation?.all_resolved_tickets;
  const top = (spots ?? []).slice(0, 8);

  return (
    <View style={{ flex: 1, backgroundColor: theme.bg, paddingTop: insets.top }}>
      <View style={st.head}>
        <IconButton icon="back" label={t("common.back")} onPress={() => router.back()} style={{ elevation: 0, shadowOpacity: 0 }} />
        <T v="h3">{t("insights.title")}</T>
        <View style={{ width: 46 }} />
      </View>
      <ScrollView contentContainerStyle={st.wrap} keyboardShouldPersistTaps="handled">
        {/* 1. When is my bus quiet? */}
        <T v="h2">{t("insights.route")}</T>
        <TextInput value={q} onChangeText={(v) => { setQ(v); if (route && v !== route.route) setRoute(null); }}
          placeholder={t("insights.routePh")} placeholderTextColor={theme.muted} autoCapitalize="characters"
          accessibilityLabel={t("insights.routePh")} style={[font.body, st.input, { color: theme.text, backgroundColor: theme.surface }]} />
        {routes.map((r) => (
          <Row key={r.route_id} icon="bus" title={r.route.replace(/(UP|DOWN)$/, "")} subtitle={r.description}
            onPress={() => { setRoute(r); setQ(r.route); setRoutes([]); }} />
        ))}
        {!route && routes.length === 0 && <T v="small" muted>{t("insights.pickRoute")}</T>}
        {route && (
          <View style={[st.card, { backgroundColor: theme.card, borderColor: theme.border }]}>
            <View style={{ flexDirection: "row", alignItems: "center", gap: space.sm }}>
              <RoutePill route={route.route} />
              <T v="small" muted>{t("insights.day")}</T>
            </View>
            {dayErr != null ? <ErrorView error={dayErr} /> : !day ? <Loading /> :
              day.buckets.every((b) => b.category === "UNKNOWN") ? <T v="small" muted>{t("insights.noData")}</T> : (
                <>
                  <DayChart buckets={day.buckets} />
                  {day.quietest && day.busiest && <T v="bodyStrong">{t("insights.quietest", { q: day.quietest, b: day.busiest })}</T>}
                  <View style={{ flexDirection: "row", flexWrap: "wrap", gap: space.xs }}>
                    {(["LOW", "MODERATE", "HIGH", "VERY_HIGH"] as const).map((c) => <CrowdBadge key={c} level={c} short />)}
                  </View>
                </>
              )}
          </View>
        )}
        <T v="tiny" muted>{t("crowd.note")}</T>

        <Divider />

        {/* 2. Busiest stretches */}
        <T v="h2">{t("insights.hotspots")}</T>
        <T v="small" muted>{t("insights.hotspotsHint")}</T>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: space.sm }}>
          {["07:00", "08:30", "10:00", "13:00", "17:00", "18:30", "21:00"].map((x) => (
            <Chip key={x} label={x} icon="clock" selected={time === x} onPress={() => setTime(x)} a11y={`${t("insights.time")} ${x}`} />
          ))}
        </ScrollView>
        <View style={[st.map, { borderColor: theme.border }]}>
          <BusMap bottomInset={0} fitKey={spots ? time : undefined}
            lines={(spots ?? []).map((s) => ({
              id: s.segment_id, category: s.category,
              coords: [{ latitude: s.lat, longitude: s.lon }, { latitude: s.to_lat, longitude: s.to_lon }],
            }))} />
        </View>
        {spotErr != null && <ErrorView error={spotErr} onRetry={() => setTime((x) => x)} />}
        {spots === null && !spotErr && <Loading />}
        {top.map((s) => (
          <View key={s.segment_id} style={[st.spot, { borderBottomColor: theme.border }]}>
            {s.route ? <RoutePill route={s.route} small /> : null}
            <T v="small" style={{ flex: 1 }} numberOfLines={2}>{s.from_stop} → {s.to_stop}</T>
            <CrowdBadge level={s.category} short />
          </View>
        ))}

        <Divider />

        {/* 3. How good are the numbers? */}
        <T v="h2">{t("insights.model")}</T>
        {!card ? <Loading /> : (
          <View style={[st.card, { backgroundColor: theme.card, borderColor: theme.border }]}>
            <T v="bodyStrong">{t("insights.routeModel")}</T>
            <View style={st.kv}><T muted>{t("insights.ours")}</T><T v="bodyStrong">{num(rm?.lightgbm?.macro_f1_demand_class, 3)}</T></View>
            <View style={st.kv}><T muted>{t("insights.baseline")}</T><T v="bodyStrong">{num(rm?.daytype_seasonal_avg?.macro_f1_demand_class, 3)}</T></View>
            {card.route_model?.interval_coverage_test?.all_buckets != null && (
              <T v="tiny" muted>{t("insights.coverage", { pct: Math.round(card.route_model.interval_coverage_test.all_buckets * 100) })}</T>
            )}
            <Divider />
            <T v="bodyStrong">{t("insights.segModel")}</T>
            <View style={st.kv}><T muted>{t("insights.ours")}</T><T v="bodyStrong">{num(sm?.model?.mae, 3)}</T></View>
            <View style={st.kv}><T muted>{t("insights.baseline")}</T><T v="bodyStrong">{num(sm?.historical_average_baseline?.mae, 3)}</T></View>
            <T v="tiny" muted>{t("insights.honest")}</T>
            <Divider />
            <T v="bodyStrong">{t("insights.fares")}</T>
            <View style={st.kv}><T muted>{t("insights.exact")}</T><T v="bodyStrong">{pct(fv?.exact_match)}</T></View>
            <View style={st.kv}><T muted>{t("insights.slab")}</T><T v="bodyStrong">{pct(fv?.within_one_slab)}</T></View>
          </View>
        )}
        {Array.isArray(card?.limitations) && card.limitations.length > 0 && (
          <>
            <T v="h3" style={{ marginTop: space.md }}>{t("insights.limitations")}</T>
            {card.limitations.map((l: string, i: number) => <T key={i} v="small" muted>• {l}</T>)}
          </>
        )}
      </ScrollView>
    </View>
  );
}

const st = StyleSheet.create({
  head: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", paddingHorizontal: space.sm, paddingVertical: space.sm },
  wrap: { padding: space.lg, paddingBottom: space.xxl, gap: space.sm, width: "100%", maxWidth: MAX_WIDTH, alignSelf: "center" },
  input: { height: 48, borderRadius: radius.md, paddingHorizontal: space.md },
  card: { borderRadius: radius.lg, borderWidth: 1, padding: space.lg, gap: space.sm },
  map: { height: 260, borderRadius: radius.lg, overflow: "hidden", borderWidth: 1 },
  spot: { flexDirection: "row", alignItems: "center", gap: space.sm, paddingVertical: space.sm, borderBottomWidth: StyleSheet.hairlineWidth },
  kv: { flexDirection: "row", justifyContent: "space-between" },
});
