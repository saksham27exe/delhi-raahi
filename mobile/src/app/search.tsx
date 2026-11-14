import { router, useLocalSearchParams } from "expo-router";
import { type ComponentRef, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { FlatList, Pressable, StyleSheet, TextInput, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Icon } from "../components/Icon";
import { IconButton, Row, T } from "../components/ui";
import { api } from "../lib/api";
import { useLocation } from "../lib/location";
import { useApp } from "../lib/state";
import { store, type RecentTrip, type SavedPlace } from "../lib/storage";
import { font, MAX_WIDTH, radius, space } from "../lib/theme";
import type { PickedPlace, StopHit } from "../lib/types";

type Field = "from" | "to";

export default function Search() {
  const { t } = useTranslation();
  const { theme, trip, setTrip } = useApp();
  const insets = useSafeAreaInsets();
  const params = useLocalSearchParams<{ focus?: Field }>();
  const { coord } = useLocation();
  const [field, setField] = useState<Field>(params.focus === "from" ? "from" : "to");
  const [from, setFrom] = useState<PickedPlace | null>(trip.from);
  const [to, setTo] = useState<PickedPlace | null>(trip.to);
  const [text, setText] = useState({ from: trip.from?.label ?? "", to: trip.to?.label ?? "" });
  const [hits, setHits] = useState<StopHit[]>([]);
  const [searching, setSearching] = useState(false);
  const [recent, setRecent] = useState<RecentTrip[]>([]);
  const [places, setPlaces] = useState<SavedPlace[]>([]);
  const refs = { from: useRef<ComponentRef<typeof TextInput>>(null), to: useRef<ComponentRef<typeof TextInput>>(null) };

  useEffect(() => { store.recent().then(setRecent); store.places().then(setPlaces); }, []);

  // default origin: current location
  useEffect(() => {
    if (!from && coord) {
      const here: PickedPlace = { label: t("search.currentLocation"), place: { type: "coord", lat: coord.latitude, lon: coord.longitude }, lat: coord.latitude, lon: coord.longitude };
      setFrom(here);
      setText((x) => ({ ...x, from: here.label }));
    }
  }, [coord]); // eslint-disable-line react-hooks/exhaustive-deps

  const q = text[field].trim();
  useEffect(() => {
    const picked = field === "from" ? from : to;
    if (q.length < 2 || picked?.label === q) { setHits([]); return; }
    setSearching(true);
    const h = setTimeout(() => api.searchStops(q).then((r) => setHits(r.results)).catch(() => setHits([])).finally(() => setSearching(false)), 220);
    return () => clearTimeout(h);
  }, [q, field]); // eslint-disable-line react-hooks/exhaustive-deps

  const choose = (p: PickedPlace) => {
    const nf = field === "from" ? p : from;
    const nt = field === "to" ? p : to;
    if (field === "from") setFrom(p); else setTo(p);
    setText((x) => ({ ...x, [field]: p.label }));
    setHits([]);
    if (nf && nt) {
      setTrip({ from: nf, to: nt, result: null, selected: 0 });
      router.replace("/results");
    } else {
      const next: Field = nf ? "to" : "from";
      setField(next);
      refs[next].current?.focus();
    }
  };

  const fromHit = (h: StopHit): PickedPlace => ({ label: h.name, place: { type: "stop_name", name: h.name }, lat: h.lat, lon: h.lon });

  const input = (f: Field) => (
    <TextInput ref={refs[f]} value={text[f]} autoFocus={f === field} onFocus={() => setField(f)}
      onChangeText={(v) => { setText((x) => ({ ...x, [f]: v })); if (f === "from") setFrom(null); else setTo(null); }}
      selectTextOnFocus placeholder={f === "from" ? t("search.fromPh") : t("search.toPh")} placeholderTextColor={theme.muted}
      accessibilityLabel={f === "from" ? t("search.from") : t("search.to")}
      style={[font.body, st.input, { color: theme.text, backgroundColor: field === f ? theme.surface2 : theme.surface }]} />
  );

  const showSuggestions = q.length >= 2 && !(field === "from" ? from : to);
  return (
    <View style={{ flex: 1, backgroundColor: theme.bg, paddingTop: insets.top }}>
      <View style={st.wrap}>
        <View style={st.header}>
          <IconButton icon="back" label={t("common.back")} onPress={() => router.back()} style={{ elevation: 0, shadowOpacity: 0 }} />
          <T v="h3">{t("search.title")}</T>
          <View style={{ width: 46 }} />
        </View>
        <View style={st.fields}>
          <View style={st.rail} accessibilityElementsHidden importantForAccessibility="no-hide-descendants">
            <Icon name="dot" size={14} color={theme.text} />
            <View style={[st.line, { backgroundColor: theme.text }]} />
            <Icon name="square" size={14} color={theme.text} />
          </View>
          <View style={{ flex: 1, gap: space.sm }}>
            {input("from")}
            {input("to")}
          </View>
          <Pressable accessibilityRole="button" accessibilityLabel="Swap" hitSlop={8}
            onPress={() => { setFrom(to); setTo(from); setText({ from: text.to, to: text.from }); }}
            style={[st.swap, { backgroundColor: theme.surface }]}>
            <Icon name="swap" size={20} color={theme.text} />
          </Pressable>
        </View>
      </View>

      <FlatList
        style={{ flex: 1 }} contentContainerStyle={[st.wrap, { paddingHorizontal: space.lg, paddingBottom: insets.bottom + space.xl }]}
        keyboardShouldPersistTaps="handled"
        data={showSuggestions ? hits : []}
        keyExtractor={(h) => h.name}
        renderItem={({ item }) => (
          <Row icon="pin" title={item.name} subtitle={t("search.routes", { count: item.routes_served })} onPress={() => choose(fromHit(item))} />
        )}
        ListHeaderComponent={
          !showSuggestions ? (
            <View>
              {field === "from" && coord && (
                <Row icon="locate" title={t("search.currentLocation")}
                  onPress={() => choose({ label: t("search.currentLocation"), place: { type: "coord", lat: coord.latitude, lon: coord.longitude }, lat: coord.latitude, lon: coord.longitude })} />
              )}
              {places.map((p) => <Row key={p.id} icon={p.icon} title={p.label} subtitle={p.value.label} onPress={() => choose(p.value)} />)}
              {recent.map((r) => (
                <Row key={r.from.label + r.to.label} icon="clock" title={field === "to" ? r.to.label : r.from.label}
                  onPress={() => choose(field === "to" ? r.to : r.from)} />
              ))}
            </View>
          ) : undefined
        }
        ListEmptyComponent={showSuggestions && !searching ? <T v="small" muted style={{ padding: space.lg }}>{t("search.noMatch")}</T> : undefined}
      />
    </View>
  );
}

const st = StyleSheet.create({
  wrap: { width: "100%", maxWidth: MAX_WIDTH, alignSelf: "center" },
  header: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", paddingHorizontal: space.sm, paddingVertical: space.sm },
  fields: { flexDirection: "row", alignItems: "center", gap: space.md, paddingHorizontal: space.lg, paddingBottom: space.lg },
  rail: { alignItems: "center", width: 14 },
  line: { width: 2, height: 34, marginVertical: 2 },
  input: { height: 48, borderRadius: radius.sm, paddingHorizontal: space.md },
  swap: { width: 40, height: 40, borderRadius: 20, alignItems: "center", justifyContent: "center" },
});
