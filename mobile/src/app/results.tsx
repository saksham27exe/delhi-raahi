import { router } from "expo-router";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Pressable, ScrollView, StyleSheet, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { BusMap } from "../components/BusMap";
import { Icon } from "../components/Icon";
import { CrowdStrip, fareText, journeyMap, LegChain, Times } from "../components/JourneyBits";
import { Sheet } from "../components/Sheet";
import { Button, Chip, CrowdBadge, Empty, ErrorView, IconButton, Loading, Segmented, T } from "../components/ui";
import { addMinutes, api, delhiNow, toIso } from "../lib/api";
import { useApp } from "../lib/state";
import { store } from "../lib/storage";
import { radius, space } from "../lib/theme";
import type { AdvisorRow, Preference } from "../lib/types";

const OFFSETS = [0, 15, 30, 60, 120];

function Advisor() {
  const { t } = useTranslation();
  const { theme, trip, setTrip } = useApp();
  const [rows, setRows] = useState<AdvisorRow[] | null>(null);
  const [reason, setReason] = useState<string | null>(null);
  const [best, setBest] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState<unknown>(null);
  const run = async () => {
    if (!trip.from || !trip.to) return;
    const w = trip.date && trip.time ? { date: trip.date, time: trip.time } : delhiNow();
    setLoading(true); setErr(null);
    try {
      const r = await api.advisor(trip.from.place, trip.to.place, toIso(w.date, w.time), trip.preference);
      setRows(r.departures); setReason(r.recommendation?.reason ?? null); setBest(r.recommendation?.offset_min ?? null);
    } catch (e) { setErr(e); } finally { setLoading(false); }
  };
  if (!rows && !loading && !err) {
    return <Button kind="secondary" icon="sparkle" title={t("results.advisor")} onPress={run} style={{ marginTop: space.md }} />;
  }
  return (
    <View style={[st.adv, { backgroundColor: theme.surface }]}>
      <T v="h3">{t("results.advisor")}</T>
      {loading && <Loading label={t("results.advisorLoading")} />}
      {err != null && <ErrorView error={err} onRetry={run} />}
      {reason && <T v="small" muted>{reason}</T>}
      {rows?.filter((r) => r.available).map((r) => {
        const base = trip.date && trip.time ? { date: trip.date, time: trip.time } : delhiNow();
        return (
          <Pressable key={r.offset_min} accessibilityRole="button"
            onPress={() => { const n = addMinutes(base.date, base.time, r.offset_min); setTrip({ date: n.date, time: n.time, result: null }); }}
            style={[st.advRow, best === r.offset_min && { backgroundColor: theme.card, borderColor: theme.text }]}>
            <View style={{ width: 64 }}>
              <T v="bodyStrong">{r.board_time}</T>
              <T v="tiny" muted>{r.offset_min > 0 ? "+" : ""}{r.offset_min} {t("common.min")}</T>
            </View>
            <T v="small" style={{ flex: 1 }} numberOfLines={1}>{r.routes?.map((x) => x.replace(/(UP|DOWN)$/, "")).join(" › ")}</T>
            <T v="small" muted>{Math.round(r.duration_min ?? 0)} {t("common.min")}</T>
            {r.crowd_level && <CrowdBadge level={r.crowd_level} short />}
          </Pressable>
        );
      })}
    </View>
  );
}

export default function Results() {
  const { t, i18n } = useTranslation();
  const { theme, trip, setTrip } = useApp();
  const insets = useSafeAreaInsets();
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState<unknown>(null);
  const [showTimes, setShowTimes] = useState(false);
  const lang = i18n.language === "hi" ? "hi" : "en";

  const load = async () => {
    if (!trip.from || !trip.to) return;
    const w = trip.date && trip.time ? { date: trip.date, time: trip.time } : delhiNow();
    setLoading(true); setErr(null);
    try {
      const r = await api.journeys(trip.from.place, trip.to.place, toIso(w.date, w.time), trip.preference);
      setTrip({ result: r, selected: 0 });
      store.addRecent({ from: trip.from, to: trip.to, at: new Date().toISOString() });
    } catch (e) { setErr(e); } finally { setLoading(false); }
  };
  useEffect(() => { if (!trip.result) load(); }, [trip.preference, trip.date, trip.time, trip.result]); // eslint-disable-line react-hooks/exhaustive-deps

  const js = trip.result?.journeys ?? [];
  const sel = js[trip.selected];
  const { lines, markers } = sel ? journeyMap(sel) : { lines: [], markers: [] };
  const leaveLabel = trip.time ? t("results.leaveAt", { time: trip.time }) : t("results.leaveNow");
  const now = delhiNow();

  return (
    <View style={{ flex: 1 }}>
      <BusMap lines={lines} markers={markers} bottomInset={420} showsUser={false} fitKey={sel?.journey_id} />
      <View style={[st.top, { top: insets.top + space.sm }]}>
        <IconButton icon="back" label={t("common.back")} onPress={() => router.back()} />
        <Pressable accessibilityRole="button" onPress={() => router.push("/search")}
          style={[st.od, { backgroundColor: theme.card, shadowColor: theme.shadow }]}>
          <T v="bodyStrong" numberOfLines={1} style={{ flexShrink: 1 }}>{trip.from?.label}</T>
          <Icon name="chevron" size={16} color={theme.muted} />
          <T v="bodyStrong" numberOfLines={1} style={{ flexShrink: 1 }}>{trip.to?.label}</T>
        </Pressable>
      </View>

      <Sheet peek={0.58} full={0.9} header={
        <View style={{ gap: space.md }}>
          <Segmented label={t("results.title")} value={trip.preference}
            onChange={(p: Preference) => setTrip({ preference: p, result: null })}
            options={(["fastest", "balanced", "least_crowded"] as Preference[]).map((k) => ({ key: k, label: t(`results.${k}`) }))} />
          <View style={{ flexDirection: "row", gap: space.sm, flexWrap: "wrap" }}>
            <Chip icon="clock" label={leaveLabel} selected={showTimes} onPress={() => setShowTimes((x) => !x)} />
          </View>
          {showTimes && (
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: space.sm }}>
              {OFFSETS.map((o) => {
                const v = addMinutes(now.date, now.time, o);
                return <Chip key={o} label={o === 0 ? t("common.now") : `+${o >= 60 ? o / 60 + "h" : o + "m"} · ${v.time}`}
                  selected={o === 0 ? !trip.time : trip.time === v.time}
                  onPress={() => { setTrip(o === 0 ? { date: null, time: null, result: null } : { date: v.date, time: v.time, result: null }); setShowTimes(false); }} />;
              })}
              {[["08:30", "results.tomorrow"], ["18:00", "results.tomorrow"]].map(([tm]) => {
                const d = addMinutes(now.date, "00:00", 24 * 60).date;
                return <Chip key={tm} label={`${t("results.tomorrow")} ${tm}`} selected={trip.date === d && trip.time === tm}
                  onPress={() => { setTrip({ date: d, time: tm, result: null }); setShowTimes(false); }} />;
              })}
            </ScrollView>
          )}
        </View>
      }>
        <ScrollView contentContainerStyle={{ paddingHorizontal: space.lg, paddingBottom: 120 }}>
          {loading && <Loading />}
          {err != null && <ErrorView error={err} onRetry={load} />}
          {!loading && trip.result && js.length === 0 && <Empty icon="bus" title={t("results.none")} hint={t("results.noneHint")} />}
          {!loading && js.map((j, i) => {
            const on = i === trip.selected;
            return (
              <Pressable key={j.journey_id} accessibilityRole="radio" accessibilityState={{ selected: on }} accessibilityLabel={j.summary[lang]}
                onPress={() => (on ? router.push(`/journey/${i}`) : setTrip({ selected: i }))}
                style={[st.opt, { borderColor: on ? theme.text : "transparent", backgroundColor: on ? theme.card : "transparent" }]}>
                <View style={{ flex: 1, gap: 6 }}>
                  <View style={st.between}>
                    <Times j={j} />
                    {fareText(j) && <T v="h3">{fareText(j)}</T>}
                  </View>
                  <LegChain j={j} />
                  <CrowdStrip j={j} />
                  <View style={st.between}>
                    <CrowdBadge level={j.crowd_level} />
                    <T v="small" muted>{j.transfers === 0 ? t("results.direct") : t("results.change")}
                      {j.walk_minutes >= 1 ? ` · ${t("results.walk", { min: Math.round(j.walk_minutes) })}` : ""}</T>
                  </View>
                  {j.labels.length > 0 && (
                    <View style={{ flexDirection: "row", gap: 6 }}>
                      {j.labels.map((l) => (
                        <View key={l} style={[st.label, { backgroundColor: theme.surface }]}><T v="tiny">{t(`results.labels.${l}`)}</T></View>
                      ))}
                    </View>
                  )}
                </View>
              </Pressable>
            );
          })}
          {!loading && js.length > 0 && <T v="tiny" muted style={{ marginTop: space.sm }}>{t("crowd.note")}</T>}
          {!loading && js.length > 0 && <Advisor key={`${trip.date}${trip.time}${trip.preference}`} />}
        </ScrollView>
      </Sheet>

      {sel && !loading && (
        <View style={[st.cta, { paddingBottom: insets.bottom + space.md, backgroundColor: theme.card, borderTopColor: theme.border }]}>
          <Button title={t("results.choose", { route: sel.legs.map((l) => l.route.replace(/(UP|DOWN)$/, "")).join(" + ") })}
            onPress={() => router.push(`/journey/${trip.selected}`)} />
        </View>
      )}
    </View>
  );
}

const st = StyleSheet.create({
  top: { position: "absolute", left: space.lg, right: space.lg, flexDirection: "row", alignItems: "center", gap: space.sm },
  od: { flex: 1, flexDirection: "row", alignItems: "center", gap: 6, height: 46, paddingHorizontal: space.lg, borderRadius: radius.pill, elevation: 4, shadowOpacity: 0.15, shadowRadius: 6, shadowOffset: { width: 0, height: 2 } },
  opt: { flexDirection: "row", padding: space.md, borderRadius: radius.lg, borderWidth: 2, marginBottom: space.sm },
  between: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", gap: space.sm },
  label: { paddingHorizontal: 8, paddingVertical: 2, borderRadius: 6 },
  cta: { position: "absolute", left: 0, right: 0, bottom: 0, paddingHorizontal: space.lg, paddingTop: space.md, borderTopWidth: StyleSheet.hairlineWidth, alignItems: "center" },
  adv: { marginTop: space.lg, padding: space.lg, borderRadius: radius.lg, gap: space.sm },
  advRow: { flexDirection: "row", alignItems: "center", gap: space.sm, padding: space.sm, borderRadius: radius.md, borderWidth: 1.5, borderColor: "transparent" },
});
