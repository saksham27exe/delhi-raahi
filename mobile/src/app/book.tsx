import { router, useLocalSearchParams } from "expo-router";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Pressable, ScrollView, StyleSheet, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Button, Divider, ErrorView, IconButton, Loading, RoutePill, Segmented, T } from "../components/ui";
import { api } from "../lib/api";
import { useApp } from "../lib/state";
import { store } from "../lib/storage";
import { MAX_WIDTH, radius, space } from "../lib/theme";
import type { Quote } from "../lib/types";

function Stepper({ label, value, min, max, onChange }: { label: string; value: number; min: number; max: number; onChange: (v: number) => void }) {
  const { theme } = useApp();
  const btn = (d: number, sym: string) => (
    <Pressable accessibilityRole="button" accessibilityLabel={`${label} ${d > 0 ? "+" : "−"}`} disabled={value + d < min || value + d > max}
      onPress={() => onChange(value + d)} style={[st.stepBtn, { backgroundColor: theme.surface, opacity: value + d < min || value + d > max ? 0.35 : 1 }]}>
      <T v="h2">{sym}</T>
    </Pressable>
  );
  return (
    <View style={st.stepper} accessibilityLabel={`${label}: ${value}`}>
      <T v="body" style={{ flex: 1 }}>{label}</T>
      {btn(-1, "−")}
      <T v="h3" style={{ width: 28, textAlign: "center" }}>{value}</T>
      {btn(1, "+")}
    </View>
  );
}

export default function Book() {
  const { t, i18n } = useTranslation();
  const { theme, trip } = useApp();
  const insets = useSafeAreaInsets();
  const { idx } = useLocalSearchParams<{ idx: string }>();
  const j = trip.result?.journeys[Number(idx)];
  const legs = j?.legs ?? [];
  const lang = i18n.language === "hi" ? "hi" : "en";
  const [passengers, setPassengers] = useState(1);
  const [women, setWomen] = useState(0);
  const [types, setTypes] = useState<(string | null)[]>(legs.map((l) => (l.fare?.bus_type === "ac" || l.fare?.bus_type === "non_ac" ? l.fare.bus_type : null)));
  const [quote, setQuote] = useState<Quote | null>(null);
  const [err, setErr] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);
  const refs = legs.map((l) => ({ route_id: l.route_id, board_stop_id: l.board_stop_id, alight_stop_id: l.alight_stop_id }));

  useEffect(() => {
    if (!refs.length) return;
    let live = true;
    setErr(null);
    api.quote(refs, passengers, Math.min(women, passengers), types).then((q) => live && setQuote(q)).catch((e) => live && setErr(e));
    return () => { live = false; };
  }, [passengers, women, types.join()]); // eslint-disable-line react-hooks/exhaustive-deps

  const confirm = async () => {
    setBusy(true); setErr(null);
    try {
      const tk = await api.book(refs, passengers, Math.min(women, passengers), types);
      await store.addTicket(tk);
      router.dismissAll();
      router.push(`/ticket/${encodeURIComponent(tk.ticket_id)}?new=1`);
    } catch (e) { setErr(e); } finally { setBusy(false); }
  };

  return (
    <View style={[st.backdrop, { backgroundColor: theme.overlay }]}>
      <Pressable style={{ flex: 1 }} onPress={() => router.back()} accessibilityLabel={t("common.close")} />
      <View style={[st.card, { backgroundColor: theme.card, paddingBottom: insets.bottom + space.lg }]}>
        <View style={st.head}>
          <T v="h2">{t("booking.title")}</T>
          <IconButton icon="close" label={t("common.close")} onPress={() => router.back()} style={{ elevation: 0, shadowOpacity: 0, backgroundColor: theme.surface }} />
        </View>
        <View style={[st.demo, { backgroundColor: theme.crowd.VERY_HIGH.bg }]} accessibilityRole="alert">
          <T v="small" style={{ color: theme.crowd.VERY_HIGH.fg, fontWeight: "700" }}>{t("booking.demo")}</T>
        </View>
        <ScrollView style={{ maxHeight: 420 }} contentContainerStyle={{ gap: space.md, paddingVertical: space.md }}>
          <Stepper label={t("booking.passengers")} value={passengers} min={1} max={6}
            onChange={(v) => { setPassengers(v); setWomen((w) => Math.min(w, v)); }} />
          <Stepper label={t("booking.women")} value={women} min={0} max={passengers} onChange={setWomen} />
          <T v="tiny" muted>{t("booking.womenNote")}</T>
          <Divider />
          {legs.map((l, i) => (
            <View key={i} style={{ gap: space.sm }}>
              <View style={st.between}>
                <View style={{ flexDirection: "row", alignItems: "center", gap: space.sm, flex: 1 }}>
                  <RoutePill route={l.route} />
                  <T v="small" numberOfLines={1} style={{ flex: 1 }}>{l.board_stop} → {l.alight_stop}</T>
                </View>
                {quote?.legs[i] && <T v="bodyStrong">₹{quote.legs[i].leg_total}</T>}
              </View>
              {(l.fare?.bus_type === "mixed" || l.fare?.bus_type === "unknown") && (
                <Segmented label={`${t("booking.busType")} ${l.route}`} value={(types[i] ?? "ac") as "ac" | "non_ac"}
                  onChange={(k) => setTypes((ts) => ts.map((x, n) => (n === i ? k : x)))}
                  options={[{ key: "non_ac", label: t("journey.busType.non_ac") }, { key: "ac", label: t("journey.busType.ac") }]} />
              )}
            </View>
          ))}
        </ScrollView>
        {!quote && !err && <Loading />}
        {err != null && <ErrorView error={err} />}
        {quote && (
          <View style={[st.between, { marginBottom: space.md }]} accessibilityLiveRegion="polite">
            <View>
              <T v="bodyStrong">{t("booking.total")}</T>
              <T v="tiny" muted>{quote.summary[lang]}</T>
            </View>
            <T v="h1">₹{quote.total}</T>
          </View>
        )}
        <Button title={busy ? t("booking.confirming") : t("booking.confirm")} loading={busy} disabled={!quote} onPress={confirm} />
      </View>
    </View>
  );
}

const st = StyleSheet.create({
  backdrop: { flex: 1, justifyContent: "flex-end" },
  card: { width: "100%", maxWidth: MAX_WIDTH, alignSelf: "center", borderTopLeftRadius: radius.xl, borderTopRightRadius: radius.xl, padding: space.lg },
  head: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", marginBottom: space.sm },
  demo: { padding: space.md, borderRadius: radius.md },
  between: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", gap: space.md },
  stepper: { flexDirection: "row", alignItems: "center", gap: space.md },
  stepBtn: { width: 44, height: 44, borderRadius: 22, alignItems: "center", justifyContent: "center" },
});
