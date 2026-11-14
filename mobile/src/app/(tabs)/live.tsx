import { router, useFocusEffect } from "expo-router";
import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { AppState, ScrollView, StyleSheet, TextInput, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { BusMap, type BusMapHandle } from "../../components/BusMap";
import { Icon } from "../../components/Icon";
import { Sheet } from "../../components/Sheet";
import { IconButton, Row, T } from "../../components/ui";
import { api } from "../../lib/api";
import { useLocation } from "../../lib/location";
import { useApp } from "../../lib/state";
import { font, radius, space } from "../../lib/theme";
import type { NearbyStop, StopHit, Vehicle } from "../../lib/types";

const REFRESH_MS = 30_000;

export default function Live() {
  const { t } = useTranslation();
  const { theme } = useApp();
  const insets = useSafeAreaInsets();
  const { coord, refresh } = useLocation();
  const map = useRef<BusMapHandle>(null);
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [live, setLive] = useState<boolean | null>(null);
  const [nearby, setNearby] = useState<NearbyStop[]>([]);
  const [q, setQ] = useState("");
  const [hits, setHits] = useState<StopHit[]>([]);
  const [focused, setFocused] = useState(false);

  const loadVehicles = useCallback(async () => {
    if (!coord) return;
    const d = 0.03;
    try {
      const r = await api.vehicles(undefined, [coord.latitude - d, coord.longitude - d, coord.latitude + d, coord.longitude + d].join(","));
      setVehicles(r.vehicles); setLive(r.live);
    } catch { setLive(false); }
  }, [coord]);

  // refresh while this tab is visible and the app is in the foreground
  useFocusEffect(useCallback(() => {
    loadVehicles();
    const h = setInterval(() => { if (AppState.currentState === "active") loadVehicles(); }, REFRESH_MS);
    return () => clearInterval(h);
  }, [loadVehicles]));

  useEffect(() => {
    if (!coord) return;
    map.current?.center(coord, 0.03);
    api.nearbyStops(coord.latitude, coord.longitude, 1000).then((r) => setNearby(r.results)).catch(() => {});
  }, [coord]);

  useEffect(() => {
    if (q.trim().length < 2) { setHits([]); return; }
    const h = setTimeout(() => api.searchStops(q.trim()).then((r) => setHits(r.results)).catch(() => {}), 220);
    return () => clearTimeout(h);
  }, [q]);

  const openStop = (id: string) => router.push(`/stop/${encodeURIComponent(id)}`);

  return (
    <View style={{ flex: 1 }}>
      <BusMap ref={map} bottomInset={320}
        markers={[
          ...vehicles.filter((v) => !v.stale).map((v) => ({ id: v.vehicle_id, kind: "bus" as const, coord: { latitude: v.lat, longitude: v.lon },
            title: `${(v.route ?? "?").replace(/(UP|DOWN)$/, "")}${v.next_stop ? " → " + v.next_stop : ""}` })),
          ...nearby.map((s) => ({ id: "s" + s.stop_id, kind: "stop" as const, coord: { latitude: s.lat, longitude: s.lon }, title: s.name, onPress: () => openStop(s.stop_id) })),
        ]} />
      <View style={[st.top, { top: insets.top + space.sm }]} pointerEvents="box-none">
        <View style={[st.status, { backgroundColor: theme.card, shadowColor: theme.shadow }]} accessibilityLiveRegion="polite">
          <View style={[st.liveDot, { backgroundColor: live ? theme.crowd.LOW.line : theme.crowd.UNKNOWN.line }]} />
          <T v="small" style={{ fontWeight: "600" }}>
            {live === null ? t("common.loading") : live ? `${t("live.liveOn")} · ${t("live.buses", { count: vehicles.length })}` : t("live.liveOff")}
          </T>
        </View>
        <IconButton icon="locate" label={t("live.nearby")} onPress={() => (coord ? map.current?.center(coord, 0.03) : refresh())} />
      </View>

      <Sheet peek={0.42} full={0.88} startFull={false} header={
        <View style={[st.searchBox, { backgroundColor: theme.surface }]}>
          <Icon name="search" size={20} color={theme.text} />
          <TextInput value={q} onChangeText={setQ} onFocus={() => setFocused(true)} onBlur={() => setFocused(false)}
            placeholder={t("live.search")} placeholderTextColor={theme.muted} accessibilityLabel={t("live.search")}
            style={[font.body, { flex: 1, color: theme.text, height: 48 }]} />
        </View>
      }>
        <ScrollView contentContainerStyle={{ paddingHorizontal: space.lg, paddingBottom: space.xxl }} keyboardShouldPersistTaps="handled">
          {hits.length > 0 ? hits.map((h) => (
            <Row key={h.name} icon="pin" title={h.name} subtitle={t("search.routes", { count: h.routes_served })} onPress={() => openStop(h.stop_ids[0])} />
          )) : (
            <>
              <T v="h3" style={{ marginBottom: space.xs }}>{t("live.nearby")}</T>
              {!coord && !focused && <T v="small" muted>{t("home.noLocation")}</T>}
              {nearby.map((s) => (
                <Row key={s.stop_id} icon="bus" title={s.name} subtitle={`${s.distance_m} m`}
                  right={<Icon name="chevron" size={18} color={theme.muted} />} onPress={() => openStop(s.stop_id)} />
              ))}
            </>
          )}
        </ScrollView>
      </Sheet>
    </View>
  );
}

const st = StyleSheet.create({
  top: { position: "absolute", left: space.lg, right: space.lg, flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  status: { flexDirection: "row", alignItems: "center", gap: 8, paddingHorizontal: 14, height: 44, borderRadius: radius.pill, elevation: 4, shadowOpacity: 0.15, shadowRadius: 6, shadowOffset: { width: 0, height: 2 } },
  liveDot: { width: 10, height: 10, borderRadius: 5 },
  searchBox: { flexDirection: "row", alignItems: "center", gap: space.sm, paddingHorizontal: space.md, borderRadius: radius.md },
});
