import { router, useLocalSearchParams } from "expo-router";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { ScrollView, StyleSheet, View } from "react-native";
import QRCode from "react-native-qrcode-svg";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { formatTime, isExpired } from "../../components/TicketCard";
import { Button, Divider, IconButton, Loading, RoutePill, T } from "../../components/ui";
import { api } from "../../lib/api";
import { useApp } from "../../lib/state";
import { store } from "../../lib/storage";
import { MAX_WIDTH, radius, space } from "../../lib/theme";
import type { DemoTicket } from "../../lib/types";

export default function TicketScreen() {
  const { t, i18n } = useTranslation();
  const { theme } = useApp();
  const insets = useSafeAreaInsets();
  const { id, new: isNew } = useLocalSearchParams<{ id: string; new?: string }>();
  const [tk, setTk] = useState<DemoTicket | null | undefined>(undefined);
  const [check, setCheck] = useState<string | null>(null);
  const lang = i18n.language === "hi" ? "hi" : "en";

  useEffect(() => { store.tickets().then((all) => setTk(all.find((x) => x.ticket_id === id) ?? null)); }, [id]);

  if (tk === undefined) return <Loading />;
  if (tk === null) { router.back(); return null; }
  const expired = isExpired(tk);

  return (
    <View style={{ flex: 1, backgroundColor: theme.bg, paddingTop: insets.top }}>
      <View style={st.head}>
        <IconButton icon="back" label={t("common.back")} onPress={() => (isNew ? router.replace("/(tabs)/tickets") : router.back())}
          style={{ elevation: 0, shadowOpacity: 0 }} />
        <T v="h3">{t("ticket.title")}</T>
        <View style={{ width: 46 }} />
      </View>
      <ScrollView contentContainerStyle={[st.wrap, { paddingBottom: insets.bottom + space.xl }]}>
        <View style={[st.card, { backgroundColor: theme.card, borderColor: theme.border }]}>
          <View style={[st.banner, { backgroundColor: theme.crowd.VERY_HIGH.bg }]} accessibilityRole="alert">
            <T v="small" style={{ color: theme.crowd.VERY_HIGH.fg, fontWeight: "700", textAlign: "center" }}>{tk.watermark[lang]}</T>
          </View>
          <View style={st.qrWrap}>
            <View style={[st.qr, { opacity: expired ? 0.35 : 1 }]} accessible accessibilityLabel={`QR · ${tk.ticket_id} · ${tk.watermark[lang]}`}>
              <QRCode value={tk.qr_payload} size={220} ecl="M" />
            </View>
            <T v="tiny" style={[st.wm, { color: theme.crowd.VERY_HIGH.fg }]} >DEMO</T>
          </View>
          <View style={{ alignItems: "center", gap: 2 }}>
            <T v="h3">{tk.ticket_id}</T>
            <T v="small" style={{ color: expired ? theme.muted : theme.crowd.LOW.fg, fontWeight: "700" }}>
              {expired ? t("ticket.expired") : t("ticket.active")}
            </T>
            <T v="small" muted>{t("ticket.validUntil", { time: formatTime(tk.valid_until, lang) })}</T>
          </View>
          <Divider />
          {tk.legs.map((l, i) => (
            <View key={i} style={st.leg}>
              <RoutePill route={l.route} />
              <T v="small" style={{ flex: 1 }}>{l.board_stop} → {l.alight_stop}</T>
              <T v="bodyStrong">₹{l.leg_total}</T>
            </View>
          ))}
          <Divider />
          <View style={st.leg}>
            <T style={{ flex: 1 }}>{t("ticket.passengers", { count: tk.passengers })}</T>
            <T v="h2">₹{tk.total}</T>
          </View>
        </View>
        {check && <T v="small" muted style={{ textAlign: "center" }} >{check}</T>}
        <Button kind="secondary" icon="check" title={t("ticket.verify")}
          onPress={() => { setCheck("…"); api.verify(tk.qr_payload).then((r) => setCheck(r.valid_signature ? t("ticket.verified", { status: r.status }) : r.status)).catch((e) => setCheck(String(e.message))); }} />
        <Button kind="ghost" icon="trash" title={t("ticket.remove")}
          onPress={async () => { await store.removeTicket(tk.ticket_id); router.replace("/(tabs)/tickets"); }} />
      </ScrollView>
    </View>
  );
}

const st = StyleSheet.create({
  head: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", paddingHorizontal: space.sm, paddingVertical: space.sm },
  wrap: { padding: space.lg, gap: space.md, width: "100%", maxWidth: MAX_WIDTH, alignSelf: "center" },
  card: { borderRadius: radius.xl, borderWidth: 1, padding: space.lg, gap: space.md },
  banner: { padding: space.md, borderRadius: radius.md },
  qrWrap: { alignItems: "center", justifyContent: "center" },
  qr: { padding: space.md, backgroundColor: "#fff", borderRadius: radius.md },
  wm: { position: "absolute", fontSize: 56, fontWeight: "900", letterSpacing: 6, opacity: 0.18, transform: [{ rotate: "-20deg" }] },
  leg: { flexDirection: "row", alignItems: "center", gap: space.sm },
});
