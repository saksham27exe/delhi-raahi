import { useTranslation } from "react-i18next";
import { Pressable, StyleSheet, View } from "react-native";
import { useApp } from "../lib/state";
import { radius, space } from "../lib/theme";
import type { DemoTicket } from "../lib/types";
import { Icon } from "./Icon";
import { RoutePill, T } from "./ui";

export const isExpired = (tk: DemoTicket) => new Date(tk.valid_until).getTime() < Date.now();

export function formatTime(iso: string, lang: string) {
  const d = new Date(iso);
  const p = (n: number) => String(n).padStart(2, "0");
  const ist = new Date(d.getTime() + (330 + d.getTimezoneOffset()) * 60000);
  const months = lang === "hi" ? ["जन", "फ़र", "मार्च", "अप्रै", "मई", "जून", "जुला", "अग", "सित", "अक्टू", "नव", "दिस"]
    : ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
  return `${ist.getDate()} ${months[ist.getMonth()]}, ${p(ist.getHours())}:${p(ist.getMinutes())}`;
}

/** Wallet row. The DEMO status is always visible as text, not just colour. */
export function TicketCard({ tk, onPress }: { tk: DemoTicket; onPress: () => void }) {
  const { t, i18n } = useTranslation();
  const { theme } = useApp();
  const expired = isExpired(tk);
  return (
    <Pressable accessibilityRole="button" onPress={onPress}
      accessibilityLabel={`${tk.ticket_id}, ${tk.legs.map((l) => l.route).join(", ")}, ₹${tk.total}, ${expired ? t("ticket.expired") : t("ticket.active")}`}
      style={({ pressed }) => [st.card, { backgroundColor: theme.card, borderColor: theme.border, opacity: pressed ? 0.85 : 1 }]}>
      <View style={st.between}>
        <View style={[st.status, { backgroundColor: expired ? theme.surface : theme.crowd.LOW.bg }]}>
          <T v="tiny" style={{ color: expired ? theme.muted : theme.crowd.LOW.fg, fontWeight: "700" }}>{expired ? t("ticket.expired") : t("ticket.active")}</T>
        </View>
        <T v="h3">₹{tk.total}</T>
      </View>
      {tk.legs.map((l, i) => (
        <View key={i} style={{ flexDirection: "row", alignItems: "center", gap: space.sm }}>
          <RoutePill route={l.route} small />
          <T v="small" numberOfLines={1} style={{ flex: 1 }}>{l.board_stop} → {l.alight_stop}</T>
        </View>
      ))}
      <View style={st.between}>
        <T v="tiny" muted>{tk.ticket_id} · {formatTime(tk.issued_at, i18n.language)}</T>
        <Icon name="chevron" size={16} color={theme.muted} />
      </View>
    </Pressable>
  );
}

const st = StyleSheet.create({
  card: { padding: space.lg, borderRadius: radius.lg, borderWidth: 1, gap: space.sm },
  between: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  status: { paddingHorizontal: 8, paddingVertical: 3, borderRadius: 6 },
});
