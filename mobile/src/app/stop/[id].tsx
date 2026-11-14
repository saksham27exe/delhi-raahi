import { router, useFocusEffect, useLocalSearchParams } from "expo-router";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { AppState, Pressable, ScrollView, StyleSheet, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { BusMap } from "../../components/BusMap";
import { Icon } from "../../components/Icon";
import { Sheet } from "../../components/Sheet";
import { Chip, Empty, ErrorView, IconButton, Loading, RoutePill, T } from "../../components/ui";
import { api } from "../../lib/api";
import { useApp } from "../../lib/state";
import { space } from "../../lib/theme";
import type { Arrival, Vehicle } from "../../lib/types";

const REFRESH_MS = 30_000;

function ArrivalRow({ a }: { a: Arrival }) {
  const { t } = useTranslation();
  const { theme } = useApp();
  const m = Math.max(0, Math.round(a.eta_min));
  const d = a.delay_min == null ? null : Math.round(a.delay_min);
  const live = a.basis !== "scheduled";
  return (
    <View style={[st.arr, { borderBottomColor: theme.border }]}
      accessible accessibilityLabel={`${a.route}, ${t("live.towards", { stop: a.towards })}, ${m === 0 ? t("live.now") : t("live.inMin", { min: m })}, ${t(`live.basis.${a.basis}`)}`}>
      <RoutePill route={a.route} />
      <View style={{ flex: 1, gap: 2 }}>
        <T v="small" numberOfLines={1}>{t("live.towards", { stop: a.towards })}</T>
        <View style={{ flexDirection: "row", alignItems: "center", gap: 6 }}>
          {live && <View style={[st.liveDot, { backgroundColor: theme.crowd.LOW.line }]} />}
          <T v="tiny" muted={!live} style={live ? { color: theme.crowd.LOW.fg, fontWeight: "700" } : undefined}>{t(`live.basis.${a.basis}`)}</T>
          {d != null && <T v="tiny" muted>· {d > 1 ? t("live.late", { min: d }) : d < -1 ? t("live.early", { min: -d }) : t("live.onTime")}</T>}
        </View>
      </View>
      <View style={{ alignItems: "flex-end" }}>
        <T v="h3">{m === 0 ? t("live.now") : t("live.inMin", { min: m })}</T>
        <T v="tiny" muted>{a.eta}</T>
      </View>
    </View>
  );
}

export default function StopScreen() {
  const { t, i18n } = useTranslation();
  const { theme } = useApp();
  const insets = useSafeAreaInsets();
  const { id, route } = useLocalSearchParams<{ id: string; route?: string }>();
  const [stop, setStop] = useState<{ name: string; lat: number; lon: number } | null>(null);
  const [arrivals, setArrivals] = useState<Arrival[] | null>(null);
  const [liveFeed, setLiveFeed] = useState(true);
  const [summary, setSummary] = useState<string>("");
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [filter, setFilter] = useState<string | undefined>(route);
  const [err, setErr] = useState<unknown>(null);
  const lang = i18n.language === "hi" ? "hi" : "en";

  useEffect(() => { api.stop(id).then((s) => setStop({ name: s.name, lat: s.lat, lon: s.lon })).catch(setErr); }, [id]);

  const load = useCallback(async () => {
    try {
      const b = await api.arrivals(id);
      setArrivals(b.arrivals); setLiveFeed(b.live_feed); setSummary(b.summary[lang]); setErr(null);
      if (stop) {
        const d = 0.04;
        const v = await api.vehicles(filter, filter ? undefined : [stop.lat - d, stop.lon - d, stop.lat + d, stop.lon + d].join(","));
        setVehicles(v.vehicles.filter((x) => !x.stale));
      }
    } catch (e) { setErr(e); }
  }, [id, stop, filter, lang]);

  useFocusEffect(useCallback(() => {
    load();
    const h = setInterval(() => { if (AppState.currentState === "active") load(); }, REFRESH_MS);
    return () => clearInterval(h);
  }, [load]));

  const shown = (arrivals ?? []).filter((a) => !filter || a.route_id === filter);
  const routes = Array.from(new Map((arrivals ?? []).map((a) => [a.route_id, a.route])).entries());

  return (
    <View style={{ flex: 1 }}>
      <BusMap bottomInset={420} showsUser fitKey={stop ? id + (filter ?? "") : undefined}
        markers={[
          ...(stop ? [{ id: "stop", kind: "to" as const, coord: { latitude: stop.lat, longitude: stop.lon }, title: stop.name }] : []),
          ...vehicles.map((v) => ({ id: v.vehicle_id, kind: "bus" as const, coord: { latitude: v.lat, longitude: v.lon }, title: (v.route ?? "").replace(/(UP|DOWN)$/, "") })),
        ]} />
      <View style={[st.top, { top: insets.top + space.sm }]}>
        <IconButton icon="back" label={t("common.back")} onPress={() => router.back()} />
      </View>
      <Sheet peek={0.56} full={0.9} header={
        <View style={{ gap: space.sm }}>
          <View style={{ flexDirection: "row", alignItems: "center", gap: space.sm }}>
            <T v="h2" style={{ flex: 1 }} numberOfLines={2}>{stop?.name ?? "…"}</T>
            <Pressable accessibilityRole="button" accessibilityLabel={t("common.retry")} onPress={load}
              style={[st.refresh, { backgroundColor: theme.surface }]}><Icon name="refresh" size={18} color={theme.text} /></Pressable>
          </View>
          <View style={[st.feed, { backgroundColor: liveFeed ? theme.crowd.LOW.bg : theme.crowd.MODERATE.bg }]}>
            <T v="tiny" style={{ color: liveFeed ? theme.crowd.LOW.fg : theme.crowd.MODERATE.fg, fontWeight: "700" }}>
              {liveFeed ? t("live.liveOn") : t("live.liveOff")}
            </T>
          </View>
          {routes.length > 1 && (
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: space.sm }}>
              <Chip label={t("live.arrivals")} selected={!filter} onPress={() => setFilter(undefined)} />
              {routes.map(([rid, r]) => <Chip key={rid} label={r.replace(/(UP|DOWN)$/, "")} selected={filter === rid} onPress={() => setFilter(rid)} />)}
            </ScrollView>
          )}
        </View>
      }>
        <ScrollView contentContainerStyle={{ paddingHorizontal: space.lg, paddingBottom: space.xxl }}>
          {summary ? <T v="small" muted style={{ position: "absolute", opacity: 0 }} >{summary}</T> : null}
          {err != null && <ErrorView error={err} onRetry={load} />}
          {arrivals === null && !err && <Loading />}
          {arrivals && shown.length === 0 && <Empty icon="clock" title={t("live.none")} />}
          {shown.map((a, i) => <ArrivalRow key={a.route_id + a.eta + i} a={a} />)}
        </ScrollView>
      </Sheet>
    </View>
  );
}

const st = StyleSheet.create({
  top: { position: "absolute", left: space.lg, flexDirection: "row" },
  arr: { flexDirection: "row", alignItems: "center", gap: space.md, paddingVertical: space.md, borderBottomWidth: StyleSheet.hairlineWidth },
  liveDot: { width: 8, height: 8, borderRadius: 4 },
  feed: { alignSelf: "flex-start", paddingHorizontal: 8, paddingVertical: 3, borderRadius: 6 },
  refresh: { width: 40, height: 40, borderRadius: 20, alignItems: "center", justifyContent: "center" },
});
