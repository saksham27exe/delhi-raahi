import { router, useFocusEffect } from "expo-router";
import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Pressable, ScrollView, StyleSheet, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { BusMap, type BusMapHandle } from "../../components/BusMap";
import { Icon } from "../../components/Icon";
import { Sheet } from "../../components/Sheet";
import { Chip, Divider, IconButton, Row, T } from "../../components/ui";
import { api } from "../../lib/api";
import { useLocation } from "../../lib/location";
import { useApp } from "../../lib/state";
import { store, type RecentTrip, type SavedPlace } from "../../lib/storage";
import { font, radius, space } from "../../lib/theme";
import type { NearbyStop } from "../../lib/types";

export default function Home() {
  const { t } = useTranslation();
  const { theme, settings, update, setTrip } = useApp();
  const insets = useSafeAreaInsets();
  const { coord, denied, loading, refresh } = useLocation();
  const map = useRef<BusMapHandle>(null);
  const [nearby, setNearby] = useState<NearbyStop[]>([]);
  const [recent, setRecent] = useState<RecentTrip[]>([]);
  const [places, setPlaces] = useState<SavedPlace[]>([]);

  useFocusEffect(useCallback(() => {
    store.recent().then(setRecent);
    store.places().then(setPlaces);
  }, []));

  useEffect(() => {
    if (!coord) return;
    map.current?.center(coord, 0.015);
    api.nearbyStops(coord.latitude, coord.longitude).then((r) => setNearby(r.results.slice(0, 5))).catch(() => setNearby([]));
  }, [coord]);

  const goSearch = (to?: SavedPlace["value"]) => {
    setTrip({ result: null, to: to ?? null, from: null });
    router.push(to ? "/search?focus=from" : "/search");
  };

  return (
    <View style={{ flex: 1 }}>
      <BusMap ref={map} bottomInset={300}
        markers={nearby.map((s) => ({ id: s.stop_id, coord: { latitude: s.lat, longitude: s.lon }, kind: "stop" as const, title: s.name,
          onPress: () => router.push(`/stop/${encodeURIComponent(s.stop_id)}`) }))} />

      <View style={[st.top, { top: insets.top + space.sm }]} pointerEvents="box-none">
        <Pressable accessibilityRole="button" onPress={() => update({ lang: settings.lang === "en" ? "hi" : "en" })}
          accessibilityLabel={settings.lang === "en" ? "हिंदी में बदलें" : "Switch to English"}
          style={[st.langPill, { backgroundColor: theme.card, shadowColor: theme.shadow }]}>
          <Icon name="globe" size={18} color={theme.text} />
          <T v="bodyStrong">{settings.lang === "en" ? "हिंदी" : "English"}</T>
        </Pressable>
        <IconButton icon="locate" label={t("live.nearby")} onPress={() => (coord ? map.current?.center(coord, 0.015) : refresh())} />
      </View>

      <Sheet peek={0.46} full={0.88} header={
        <View style={{ gap: space.md }}>
          <T v="h2">{t("home.greeting")}</T>
          <Pressable accessibilityRole="search" accessibilityLabel={t("home.whereTo")} onPress={() => goSearch()}
            style={({ pressed }) => [st.where, { backgroundColor: theme.surface, opacity: pressed ? 0.8 : 1 }]}>
            <Icon name="search" size={22} color={theme.text} strokeWidth={2.4} />
            <T style={[font.h3, { flex: 1 }]}>{t("home.whereTo")}</T>
            <View style={[st.now, { backgroundColor: theme.card }]}>
              <Icon name="clock" size={16} color={theme.text} />
              <T v="small" style={{ fontWeight: "600" }}>{t("common.now")}</T>
            </View>
          </Pressable>
        </View>
      }>
        <ScrollView contentContainerStyle={{ paddingHorizontal: space.lg, paddingBottom: space.xxl }} keyboardShouldPersistTaps="handled">
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: space.sm, paddingVertical: space.sm }}>
            {places.map((p) => <Chip key={p.id} icon={p.icon} label={p.label} onPress={() => goSearch(p.value)} />)}
            {!places.some((p) => p.icon === "home") && <Chip icon="home" label={t("home.addHome")} onPress={() => router.push("/(tabs)/account")} />}
            {!places.some((p) => p.icon === "work") && <Chip icon="work" label={t("home.addWork")} onPress={() => router.push("/(tabs)/account")} />}
          </ScrollView>

          {recent.length > 0 && (
            <>
              {recent.slice(0, 3).map((r) => (
                <Row key={r.from.label + r.to.label} icon="clock" title={r.to.label} subtitle={`${t("search.from")}: ${r.from.label}`}
                  onPress={() => { setTrip({ from: r.from, to: r.to, result: null, date: null, time: null }); router.push("/results"); }} />
              ))}
              <Divider />
            </>
          )}

          <T v="h3" style={{ marginTop: space.md, marginBottom: space.xs }}>{t("home.nearby")}</T>
          {loading && !coord && <T v="small" muted>{t("home.locating")}</T>}
          {denied && !coord && <T v="small" muted>{t("home.noLocation")}</T>}
          {nearby.map((s) => (
            <Row key={s.stop_id} icon="bus" title={s.name} subtitle={`${s.distance_m} m`}
              right={<Icon name="chevron" size={18} color={theme.muted} />}
              onPress={() => router.push(`/stop/${encodeURIComponent(s.stop_id)}`)} />
          ))}

          <Pressable accessibilityRole="button" onPress={() => router.push("/insights")}
            style={({ pressed }) => [st.promo, { backgroundColor: theme.surface, opacity: pressed ? 0.85 : 1 }]}>
            <View style={{ flex: 1, gap: 2 }}>
              <T v="h3">{t("home.insights")}</T>
              <T v="small" muted>{t("home.insightsSub")}</T>
            </View>
            <View style={[st.promoIcon, { backgroundColor: theme.primary }]}><Icon name="chart" color={theme.onPrimary} /></View>
          </Pressable>
        </ScrollView>
      </Sheet>
    </View>
  );
}

const st = StyleSheet.create({
  top: { position: "absolute", left: space.lg, right: space.lg, flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  langPill: { flexDirection: "row", alignItems: "center", gap: 6, paddingHorizontal: 14, height: 44, borderRadius: radius.pill, elevation: 4, shadowOpacity: 0.15, shadowRadius: 6, shadowOffset: { width: 0, height: 2 } },
  where: { flexDirection: "row", alignItems: "center", gap: space.md, paddingLeft: space.lg, paddingRight: space.sm, height: 56, borderRadius: radius.md },
  now: { flexDirection: "row", alignItems: "center", gap: 4, paddingHorizontal: 10, height: 36, borderRadius: radius.pill },
  promo: { flexDirection: "row", alignItems: "center", gap: space.md, padding: space.lg, borderRadius: radius.lg, marginTop: space.lg },
  promoIcon: { width: 44, height: 44, borderRadius: 22, alignItems: "center", justifyContent: "center" },
});
