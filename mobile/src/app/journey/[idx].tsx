import { router, useLocalSearchParams } from "expo-router";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Pressable, ScrollView, StyleSheet, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { BusMap } from "../../components/BusMap";
import { Icon } from "../../components/Icon";
import { CrowdStrip, fareText, journeyMap } from "../../components/JourneyBits";
import { Sheet } from "../../components/Sheet";
import { Button, CrowdBadge, Empty, IconButton, RoutePill, T } from "../../components/ui";
import { useApp } from "../../lib/state";
import { MAX_WIDTH, radius, space } from "../../lib/theme";
import type { Leg } from "../../lib/types";

function Step({ dot, children, last }: { dot: "walk" | "board" | "alight" | "change"; children: React.ReactNode; last?: boolean }) {
  const { theme } = useApp();
  return (
    <View style={st.step}>
      <View style={st.rail}>
        {dot === "walk" ? <Icon name="walk" size={18} color={theme.muted} />
          : <View style={[st.node, { borderColor: dot === "change" ? theme.accent : theme.text, backgroundColor: dot === "alight" ? theme.text : theme.card }]} />}
        {!last && <View style={[st.bar, { backgroundColor: dot === "walk" ? theme.surface2 : theme.text }]} />}
      </View>
      <View style={{ flex: 1, paddingBottom: space.lg, gap: 4 }}>{children}</View>
    </View>
  );
}

function LegBlock({ l, first }: { l: Leg; first: boolean }) {
  const { t } = useTranslation();
  const { theme } = useApp();
  const [open, setOpen] = useState(false);
  return (
    <>
      <Step dot={first ? "board" : "change"}>
        {!first && <T v="tiny" muted>{t("journey.change").toUpperCase()}</T>}
        <View style={{ flexDirection: "row", alignItems: "center", gap: space.sm }}>
          <T v="bodyStrong">{l.departure}</T>
          <T v="body" style={{ flex: 1 }} numberOfLines={2}>{t("journey.board", { stop: l.board_stop })}</T>
        </View>
        <View style={{ flexDirection: "row", alignItems: "center", gap: space.sm, flexWrap: "wrap" }}>
          <RoutePill route={l.route} />
          {l.fare && <T v="small" muted>{t(`journey.busType.${l.fare.bus_type}`)} · {l.fare.distance_km} {t("common.km")}</T>}
        </View>
        {l.route_desc ? <T v="small" muted numberOfLines={1}>{l.route_desc}</T> : null}
        <Pressable accessibilityRole="button" accessibilityState={{ expanded: open }} onPress={() => setOpen((x) => !x)}
          style={[st.ride, { backgroundColor: theme.surface }]}>
          <T v="small" style={{ flex: 1 }}>{t("journey.rideStops", { count: l.stops, min: Math.round(l.in_vehicle_minutes) })}</T>
          <T v="small" style={{ fontWeight: "600" }}>{t("journey.crowdAlong")}</T>
          <View style={{ transform: [{ rotate: open ? "90deg" : "0deg" }] }}><Icon name="chevron" size={16} color={theme.text} /></View>
        </Pressable>
        {open && l.segments.map((s) => (
          <View key={s.segment_id} style={st.seg}>
            <T v="tiny" muted style={{ width: 40 }}>{s.depart}</T>
            <T v="small" style={{ flex: 1 }} numberOfLines={1}>{s.from_stop} → {s.to_stop}</T>
            <CrowdBadge level={s.crowd?.category ?? "UNKNOWN"} short />
          </View>
        ))}
        {l.fare?.note && <T v="small" style={{ color: theme.crowd.MODERATE.fg }}>{l.fare.note}</T>}
      </Step>
    </>
  );
}

export default function JourneyScreen() {
  const { t, i18n } = useTranslation();
  const { theme, trip } = useApp();
  const insets = useSafeAreaInsets();
  const { idx } = useLocalSearchParams<{ idx: string }>();
  const j = trip.result?.journeys[Number(idx)];
  const lang = i18n.language === "hi" ? "hi" : "en";

  if (!j) {
    return <View style={{ flex: 1, paddingTop: insets.top, justifyContent: "center" }}>
      <Empty icon="bus" title={t("results.none")}><Button title={t("common.back")} onPress={() => router.back()} /></Empty>
    </View>;
  }
  const { lines, markers } = journeyMap(j);
  const last = j.legs[j.legs.length - 1];
  const fare = fareText(j);

  return (
    <View style={{ flex: 1 }}>
      <BusMap lines={lines} markers={markers} bottomInset={440} showsUser={false} fitKey={j.journey_id} />
      <View style={[st.top, { top: insets.top + space.sm }]}>
        <IconButton icon="back" label={t("common.back")} onPress={() => router.back()} />
      </View>
      <Sheet peek={0.55} full={0.9} header={
        <View style={{ gap: space.sm }}>
          <View style={st.between}>
            <T v="h2">{j.departure} – {j.arrival}</T>
            <T v="h3" muted>{Math.round(j.duration_minutes)} {t("common.min")}</T>
          </View>
          <CrowdStrip j={j} />
          <View style={st.between}>
            <CrowdBadge level={j.crowd_level} />
            {fare && <T v="h3">{fare}</T>}
          </View>
        </View>
      }>
        <ScrollView contentContainerStyle={{ paddingHorizontal: space.lg, paddingBottom: 170 }}>
          <T v="body" style={{ marginBottom: space.lg }}>{j.summary[lang]}</T>
          {j.walk.origin_min >= 1 && <Step dot="walk"><T v="small" muted>{t("journey.walkTo", { min: Math.round(j.walk.origin_min) })}</T></Step>}
          {j.legs.map((l, i) => (
            <View key={i}>
              {i > 0 && j.walk.transfer_min >= 1 && <Step dot="walk"><T v="small" muted>{t("journey.walkTo", { min: Math.round(j.walk.transfer_min) })}</T></Step>}
              <LegBlock l={l} first={i === 0} />
            </View>
          ))}
          <Step dot="alight" last>
            <View style={{ flexDirection: "row", gap: space.sm }}>
              <T v="bodyStrong">{last.arrival}</T>
              <T style={{ flex: 1 }}>{t("journey.alight", { stop: last.alight_stop })}</T>
            </View>
            {j.walk.destination_min >= 1 && <T v="small" muted>{t("journey.walkTo", { min: Math.round(j.walk.destination_min) })}</T>}
          </Step>

          {j.explanation.length > 0 && (
            <View style={[st.why, { backgroundColor: theme.surface }]}>
              <T v="h3">{t("journey.why")}</T>
              {j.explanation.map((e) => <T key={e} v="small">• {e}</T>)}
            </View>
          )}
          <T v="tiny" muted style={{ marginTop: space.md }}>{t("journey.timetable")} {t("crowd.note")}</T>
        </ScrollView>
      </Sheet>

      <View style={[st.cta, { paddingBottom: insets.bottom + space.md, backgroundColor: theme.card, borderTopColor: theme.border }]}>
        <View style={{ width: "100%", maxWidth: MAX_WIDTH - 2 * space.lg, gap: space.sm }}>
          <Button title={fare ? t("journey.book", { fare }) : t("journey.bookNoFare")} icon="ticket"
            onPress={() => router.push(`/book?idx=${idx}`)} />
          <Button kind="secondary" title={t("journey.track")} icon="live"
            onPress={() => router.push(`/stop/${encodeURIComponent(j.legs[0].board_stop_id)}?route=${encodeURIComponent(j.legs[0].route_id)}`)} />
        </View>
      </View>
    </View>
  );
}

const st = StyleSheet.create({
  top: { position: "absolute", left: space.lg, flexDirection: "row" },
  between: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  step: { flexDirection: "row", gap: space.md },
  rail: { width: 20, alignItems: "center" },
  node: { width: 16, height: 16, borderRadius: 8, borderWidth: 3.5, marginTop: 2 },
  bar: { width: 3, flex: 1, marginVertical: 2, borderRadius: 2 },
  ride: { flexDirection: "row", alignItems: "center", gap: space.sm, padding: space.md, borderRadius: radius.md, marginTop: 4 },
  seg: { flexDirection: "row", alignItems: "center", gap: space.sm, paddingVertical: 4 },
  why: { padding: space.lg, borderRadius: radius.lg, gap: 4, marginTop: space.md },
  cta: { position: "absolute", left: 0, right: 0, bottom: 0, paddingHorizontal: space.lg, paddingTop: space.md, borderTopWidth: StyleSheet.hairlineWidth, alignItems: "center" },
});
