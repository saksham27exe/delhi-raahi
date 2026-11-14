import { router, useFocusEffect } from "expo-router";
import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Pressable, ScrollView, StyleSheet, TextInput, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Icon } from "../../components/Icon";
import { Button, Chip, Divider, Row, Segmented, T } from "../../components/ui";
import { api, getApiBase } from "../../lib/api";
import { useApp } from "../../lib/state";
import { store, type SavedPlace } from "../../lib/storage";
import { font, MAX_WIDTH, radius, space } from "../../lib/theme";
import type { Lang, StopHit } from "../../lib/types";

type Kind = SavedPlace["icon"];

/** Inline stop picker used to save Home / Work / other places. */
function AddPlace({ onDone }: { onDone: () => void }) {
  const { t } = useTranslation();
  const { theme } = useApp();
  const [kind, setKind] = useState<Kind>("home");
  const [name, setName] = useState("");
  const [q, setQ] = useState("");
  const [hits, setHits] = useState<StopHit[]>([]);

  useEffect(() => {
    if (q.trim().length < 2) { setHits([]); return; }
    const h = setTimeout(() => api.searchStops(q.trim()).then((r) => setHits(r.results)).catch(() => setHits([])), 220);
    return () => clearTimeout(h);
  }, [q]);

  const save = async (h: StopHit) => {
    const label = kind === "home" ? t("account.home") : kind === "work" ? t("account.work") : (name.trim() || h.name);
    await store.savePlace({
      id: kind === "star" ? `star-${Date.now()}` : kind, label, icon: kind,
      value: { label: h.name, place: { type: "stop_name", name: h.name }, lat: h.lat, lon: h.lon },
    });
    onDone();
  };

  return (
    <View style={[st.box, { backgroundColor: theme.surface }]}>
      <Segmented label={t("account.addPlace")} value={kind} onChange={setKind}
        options={[{ key: "home", label: t("account.home") }, { key: "work", label: t("account.work") }, { key: "star", label: t("account.other") }]} />
      {kind === "star" && (
        <TextInput value={name} onChangeText={setName} placeholder={t("account.placeName")} placeholderTextColor={theme.muted}
          accessibilityLabel={t("account.placeName")} style={[font.body, st.input, { color: theme.text, backgroundColor: theme.card }]} />
      )}
      <TextInput value={q} onChangeText={setQ} placeholder={t("live.search")} placeholderTextColor={theme.muted} autoFocus
        accessibilityLabel={t("live.search")} style={[font.body, st.input, { color: theme.text, backgroundColor: theme.card }]} />
      {hits.map((h) => (
        <Row key={h.name} icon="pin" title={h.name} subtitle={t("search.routes", { count: h.routes_served })} onPress={() => save(h)} />
      ))}
      <Button kind="ghost" title={t("common.close")} onPress={onDone} />
    </View>
  );
}

export default function Account() {
  const { t } = useTranslation();
  const { theme, settings, update } = useApp();
  const insets = useSafeAreaInsets();
  const [places, setPlaces] = useState<SavedPlace[]>([]);
  const [adding, setAdding] = useState(false);
  const [server, setServer] = useState(settings.apiBase);
  const [status, setStatus] = useState<"ok" | "fail" | "busy" | null>(null);
  const [cleared, setCleared] = useState(false);

  const reload = useCallback(() => { store.places().then(setPlaces); }, []);
  useFocusEffect(reload);

  const test = async () => {
    update({ apiBase: server.trim() });
    setStatus("busy");
    // setApiBase runs in the provider effect; give it a tick before calling
    setTimeout(() => api.health().then(() => setStatus("ok")).catch(() => setStatus("fail")), 50);
  };

  return (
    <View style={{ flex: 1, backgroundColor: theme.bg, paddingTop: insets.top }}>
      <ScrollView contentContainerStyle={st.wrap} keyboardShouldPersistTaps="handled">
        <T v="h1">{t("account.title")}</T>

        <T v="h3" style={st.h}>{t("account.language")}</T>
        <Segmented<Lang> label={t("account.language")} value={settings.lang} onChange={(lang) => update({ lang })}
          options={[{ key: "en", label: "English" }, { key: "hi", label: "हिन्दी" }]} />

        <T v="h3" style={st.h}>{t("account.theme")}</T>
        <Segmented label={t("account.theme")} value={settings.theme} onChange={(theme) => update({ theme })}
          options={[{ key: "system", label: t("account.system") }, { key: "light", label: t("account.light") }, { key: "dark", label: t("account.dark") }]} />

        <T v="h3" style={st.h}>{t("account.places")}</T>
        {places.length === 0 && !adding && <T v="small" muted>{t("account.noPlaces")}</T>}
        {places.map((p) => (
          <Row key={p.id} icon={p.icon} title={p.label} subtitle={p.value.label}
            right={
              <Pressable accessibilityRole="button" accessibilityLabel={`${t("account.remove")} ${p.label}`} hitSlop={10}
                onPress={async () => { await store.removePlace(p.id); reload(); }}>
                <Icon name="trash" size={18} color={theme.muted} />
              </Pressable>
            } />
        ))}
        {adding ? <AddPlace onDone={() => { setAdding(false); reload(); }} />
          : <View style={{ flexDirection: "row" }}><Chip icon="star" label={t("account.addPlace")} onPress={() => setAdding(true)} /></View>}
        <Button kind="ghost" icon={cleared ? "check" : "trash"} title={t("account.clearRecent")}
          onPress={async () => { await store.clearRecent(); setCleared(true); }} />

        <Divider />
        <Row icon="chart" title={t("account.insights")} subtitle={t("home.insightsSub")} onPress={() => router.push("/insights")}
          right={<Icon name="chevron" size={18} color={theme.muted} />} />
        <Divider />

        <T v="h3" style={st.h}>{t("account.server")}</T>
        <T v="small" muted>{t("account.serverHint")}</T>
        <View style={{ flexDirection: "row", gap: space.sm, alignItems: "center" }}>
          <TextInput value={server} onChangeText={(v) => { setServer(v); setStatus(null); }} placeholder={getApiBase()}
            placeholderTextColor={theme.muted} autoCapitalize="none" autoCorrect={false} keyboardType="url"
            accessibilityLabel={t("account.server")} style={[font.body, st.input, { flex: 1, color: theme.text, backgroundColor: theme.surface }]} />
          <Button kind="secondary" title={t("account.test")} loading={status === "busy"} onPress={test} style={{ paddingHorizontal: space.lg }} />
        </View>
        {status === "ok" && <T v="small" style={{ color: theme.crowd.LOW.fg, fontWeight: "700" }}>✓ {t("account.serverOk")} · {getApiBase()}</T>}
        {status === "fail" && <T v="small" style={{ color: theme.crowd.VERY_HIGH.fg, fontWeight: "700" }}>{t("account.serverFail")} · {getApiBase()}</T>}

        <Divider />
        <T v="h3">{t("account.about")}</T>
        <T v="small" muted>{t("account.aboutText")}</T>
        <T v="small" muted>{t("account.privacy")}</T>
      </ScrollView>
    </View>
  );
}

const st = StyleSheet.create({
  wrap: { padding: space.lg, paddingBottom: space.xxl, gap: space.sm, width: "100%", maxWidth: MAX_WIDTH, alignSelf: "center" },
  h: { marginTop: space.md },
  box: { padding: space.md, borderRadius: radius.lg, gap: space.sm },
  input: { height: 48, borderRadius: radius.md, paddingHorizontal: space.md },
});
