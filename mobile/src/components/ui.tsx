import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import {
  ActivityIndicator, Pressable, StyleSheet, Text, View, type StyleProp, type TextStyle, type ViewStyle,
} from "react-native";
import { ApiError } from "../lib/api";
import { useApp } from "../lib/state";
import { font, radius, space } from "../lib/theme";
import type { CrowdCategory } from "../lib/types";
import { Icon, type IconName } from "./Icon";

export function T({ children, style, v = "body", muted, numberOfLines }: {
  children: ReactNode; style?: StyleProp<TextStyle>; v?: keyof typeof font; muted?: boolean; numberOfLines?: number;
}) {
  const { theme } = useApp();
  return <Text numberOfLines={numberOfLines} style={[font[v], { color: muted ? theme.muted : theme.text }, style]}>{children}</Text>;
}

export function Button({ title, onPress, kind = "primary", icon, disabled, loading, style, a11y }: {
  title: string; onPress: () => void; kind?: "primary" | "secondary" | "ghost"; icon?: IconName;
  disabled?: boolean; loading?: boolean; style?: StyleProp<ViewStyle>; a11y?: string;
}) {
  const { theme } = useApp();
  const bg = kind === "primary" ? theme.primary : kind === "secondary" ? theme.surface : "transparent";
  const fg = kind === "primary" ? theme.onPrimary : theme.text;
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={a11y ?? title} accessibilityState={{ disabled: !!disabled || !!loading }}
      disabled={disabled || loading} onPress={onPress}
      style={({ pressed }) => [s.btn, { backgroundColor: bg, opacity: disabled ? 0.45 : pressed ? 0.8 : 1 }, style]}>
      {loading ? <ActivityIndicator color={fg} /> : (
        <>
          {icon && <Icon name={icon} color={fg} size={20} />}
          <Text style={[font.h3, { color: fg }]}>{title}</Text>
        </>
      )}
    </Pressable>
  );
}

export function Chip({ label, icon, onPress, selected, a11y }: { label: string; icon?: IconName; onPress?: () => void; selected?: boolean; a11y?: string }) {
  const { theme } = useApp();
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={a11y ?? label} accessibilityState={{ selected: !!selected }} onPress={onPress}
      style={({ pressed }) => [s.chip, { backgroundColor: selected ? theme.primary : theme.surface, opacity: pressed ? 0.8 : 1 }]}>
      {icon && <Icon name={icon} size={16} color={selected ? theme.onPrimary : theme.text} />}
      <Text style={[font.bodyStrong, { color: selected ? theme.onPrimary : theme.text }]}>{label}</Text>
    </Pressable>
  );
}

export function Segmented<K extends string>({ options, value, onChange, label }: {
  options: { key: K; label: string }[]; value: K; onChange: (k: K) => void; label: string;
}) {
  const { theme } = useApp();
  return (
    <View accessibilityRole="radiogroup" accessibilityLabel={label} style={[s.seg, { backgroundColor: theme.surface }]}>
      {options.map((o) => {
        const on = o.key === value;
        return (
          <Pressable key={o.key} accessibilityRole="radio" accessibilityState={{ selected: on }} onPress={() => onChange(o.key)}
            style={[s.segItem, on && { backgroundColor: theme.card, shadowColor: theme.shadow, elevation: 2 }]}>
            <Text numberOfLines={1} style={[font.bodyStrong, { color: on ? theme.text : theme.muted, fontSize: 14 }]}>{o.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

export function CrowdBadge({ level, short }: { level: CrowdCategory; short?: boolean }) {
  const { theme } = useApp();
  const { t } = useTranslation();
  const c = theme.crowd[level];
  return (
    <View style={[s.badge, { backgroundColor: c.bg }]} accessibilityLabel={t(`crowd.${level}`)}>
      <View style={[s.dot, { backgroundColor: c.fg }]} />
      <Text style={[font.tiny, { color: c.fg, fontSize: 12, fontWeight: "700" }]}>{short ? t(`crowd.short.${level}`) : t(`crowd.${level}`)}</Text>
    </View>
  );
}

export function RoutePill({ route, small }: { route: string; small?: boolean }) {
  const { theme } = useApp();
  return (
    <View style={[s.pill, { backgroundColor: theme.primary }, small && { paddingVertical: 1, paddingHorizontal: 6 }]}>
      <Text style={[font.bodyStrong, { color: theme.onPrimary, fontSize: small ? 12 : 14 }]}>{route.replace(/(UP|DOWN)$/, "")}</Text>
    </View>
  );
}

export function Row({ icon, title, subtitle, right, onPress, a11y }: {
  icon?: IconName; title: string; subtitle?: string; right?: ReactNode; onPress?: () => void; a11y?: string;
}) {
  const { theme } = useApp();
  return (
    <Pressable accessibilityRole={onPress ? "button" : undefined} accessibilityLabel={a11y ?? [title, subtitle].filter(Boolean).join(", ")}
      onPress={onPress} disabled={!onPress} style={({ pressed }) => [s.row, { opacity: pressed ? 0.6 : 1 }]}>
      {icon && <View style={[s.rowIcon, { backgroundColor: theme.surface }]}><Icon name={icon} size={20} color={theme.text} /></View>}
      <View style={{ flex: 1 }}>
        <T v="bodyStrong" numberOfLines={1}>{title}</T>
        {subtitle ? <T v="small" muted numberOfLines={1}>{subtitle}</T> : null}
      </View>
      {right}
    </Pressable>
  );
}

export function Divider() {
  const { theme } = useApp();
  return <View style={{ height: StyleSheet.hairlineWidth, backgroundColor: theme.border, marginVertical: space.xs }} />;
}

export function Loading({ label }: { label?: string }) {
  const { theme } = useApp();
  const { t } = useTranslation();
  return (
    <View style={s.center} accessibilityLiveRegion="polite">
      <ActivityIndicator color={theme.text} />
      <T v="small" muted>{label ?? t("common.loading")}</T>
    </View>
  );
}

export function ErrorView({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { theme } = useApp();
  const { t } = useTranslation();
  const msg = error instanceof ApiError && error.status === 0 ? t("common.errNetwork")
    : t("common.errGeneric", { msg: error instanceof Error ? error.message : String(error) });
  return (
    <View style={[s.err, { backgroundColor: theme.crowd.VERY_HIGH.bg }]} accessibilityRole="alert">
      <T style={{ color: theme.crowd.VERY_HIGH.fg }}>{msg}</T>
      {onRetry && <Button title={t("common.retry")} kind="secondary" onPress={onRetry} />}
    </View>
  );
}

export function Empty({ icon, title, hint, children }: { icon: IconName; title: string; hint?: string; children?: ReactNode }) {
  const { theme } = useApp();
  return (
    <View style={[s.center, { paddingVertical: space.xxl }]}>
      <View style={[s.emptyIcon, { backgroundColor: theme.surface }]}><Icon name={icon} size={28} color={theme.text} /></View>
      <T v="h3">{title}</T>
      {hint ? <T v="small" muted style={{ textAlign: "center" }}>{hint}</T> : null}
      {children}
    </View>
  );
}

export function IconButton({ icon, onPress, label, style }: { icon: IconName; onPress: () => void; label: string; style?: StyleProp<ViewStyle> }) {
  const { theme } = useApp();
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={label} onPress={onPress} hitSlop={8}
      style={({ pressed }) => [s.iconBtn, { backgroundColor: theme.card, shadowColor: theme.shadow, opacity: pressed ? 0.8 : 1 }, style]}>
      <Icon name={icon} size={22} color={theme.text} />
    </Pressable>
  );
}

export const s = StyleSheet.create({
  btn: { minHeight: 54, borderRadius: radius.md, paddingHorizontal: space.lg, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: space.sm },
  chip: { flexDirection: "row", alignItems: "center", gap: 6, paddingHorizontal: 14, minHeight: 40, borderRadius: radius.pill },
  seg: { flexDirection: "row", padding: 4, borderRadius: radius.md, gap: 4 },
  segItem: { flex: 1, minHeight: 40, alignItems: "center", justifyContent: "center", borderRadius: 9, paddingHorizontal: 6, shadowOpacity: 0.08, shadowRadius: 4, shadowOffset: { width: 0, height: 1 } },
  badge: { flexDirection: "row", alignItems: "center", gap: 6, paddingHorizontal: 10, paddingVertical: 4, borderRadius: radius.pill, alignSelf: "flex-start" },
  dot: { width: 7, height: 7, borderRadius: 4 },
  pill: { paddingHorizontal: 8, paddingVertical: 2, borderRadius: 6 },
  row: { flexDirection: "row", alignItems: "center", gap: space.md, minHeight: 56, paddingVertical: space.sm },
  rowIcon: { width: 40, height: 40, borderRadius: 20, alignItems: "center", justifyContent: "center" },
  center: { alignItems: "center", justifyContent: "center", gap: space.sm, padding: space.lg },
  err: { padding: space.lg, borderRadius: radius.md, gap: space.md },
  emptyIcon: { width: 64, height: 64, borderRadius: 32, alignItems: "center", justifyContent: "center", marginBottom: space.sm },
  iconBtn: { width: 46, height: 46, borderRadius: 23, alignItems: "center", justifyContent: "center", elevation: 4, shadowOpacity: 0.15, shadowRadius: 6, shadowOffset: { width: 0, height: 2 } },
});
