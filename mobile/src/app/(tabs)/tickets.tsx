import { router, useFocusEffect } from "expo-router";
import { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";
import { FlatList, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { TicketCard } from "../../components/TicketCard";
import { Button, Empty, T } from "../../components/ui";
import { useApp } from "../../lib/state";
import { store } from "../../lib/storage";
import { MAX_WIDTH, space } from "../../lib/theme";
import type { DemoTicket } from "../../lib/types";

export default function Tickets() {
  const { t } = useTranslation();
  const { theme } = useApp();
  const insets = useSafeAreaInsets();
  const [tickets, setTickets] = useState<DemoTicket[]>([]);
  useFocusEffect(useCallback(() => { store.tickets().then(setTickets); }, []));
  return (
    <View style={{ flex: 1, backgroundColor: theme.bg, paddingTop: insets.top }}>
      <FlatList
        data={tickets}
        keyExtractor={(x) => x.ticket_id}
        contentContainerStyle={{ padding: space.lg, gap: space.md, width: "100%", maxWidth: MAX_WIDTH, alignSelf: "center", flexGrow: 1 }}
        ListHeaderComponent={<T v="h1" style={{ marginBottom: space.sm }}>{t("ticket.wallet")}</T>}
        renderItem={({ item }) => <TicketCard tk={item} onPress={() => router.push(`/ticket/${encodeURIComponent(item.ticket_id)}`)} />}
        ListEmptyComponent={
          <Empty icon="ticket" title={t("ticket.empty")} hint={t("ticket.emptyHint")}>
            <Button title={t("home.whereTo")} icon="search" onPress={() => router.push("/search")} style={{ marginTop: space.md, paddingHorizontal: space.xl }} />
          </Empty>
        }
        ListFooterComponent={tickets.length ? <T v="tiny" muted style={{ textAlign: "center" }}>{t("ticket.stored")}</T> : undefined}
      />
    </View>
  );
}
