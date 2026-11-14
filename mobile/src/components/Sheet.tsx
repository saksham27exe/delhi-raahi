import { useEffect, useMemo, useRef, type ReactNode } from "react";
import { Animated, PanResponder, StyleSheet, useWindowDimensions, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useApp } from "../lib/state";
import { MAX_WIDTH, radius, space } from "../lib/theme";

/** Draggable bottom sheet over a map (two snap points: `peek` and `full`, as fractions of the
 * screen height). Drag the handle/header; the body scrolls normally. */
export function Sheet({ header, children, peek = 0.42, full = 0.86, startFull = false }: {
  header?: ReactNode; children: ReactNode; peek?: number; full?: number; startFull?: boolean;
}) {
  const { theme } = useApp();
  const { height } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const hFull = height * full, hPeek = height * peek;
  const closed = hFull - hPeek;                       // translateY when collapsed
  const y = useRef(new Animated.Value(startFull ? 0 : closed)).current;
  const last = useRef(startFull ? 0 : closed);

  useEffect(() => { y.setValue(last.current === 0 ? 0 : closed); last.current = last.current === 0 ? 0 : closed; }, [closed]); // eslint-disable-line react-hooks/exhaustive-deps

  const pan = useMemo(() => PanResponder.create({
    onMoveShouldSetPanResponder: (_, g) => Math.abs(g.dy) > 6,
    onPanResponderMove: (_, g) => y.setValue(Math.min(closed, Math.max(0, last.current + g.dy))),
    onPanResponderRelease: (_, g) => {
      const pos = last.current + g.dy;
      const to = g.vy > 0.5 || (g.vy > -0.5 && pos > closed / 2) ? closed : 0;
      last.current = to;
      Animated.spring(y, { toValue: to, useNativeDriver: true, bounciness: 2 }).start();
    },
  }), [closed]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <Animated.View style={[st.sheet, {
      height: hFull, backgroundColor: theme.card, shadowColor: theme.shadow, paddingBottom: insets.bottom,
      transform: [{ translateY: y }],
    }]}>
      <View {...pan.panHandlers} style={st.head} accessibilityHint="Drag up or down to resize">
        <View style={[st.handle, { backgroundColor: theme.surface2 }]} />
        {header}
      </View>
      <View style={{ flex: 1 }}>{children}</View>
    </Animated.View>
  );
}

const st = StyleSheet.create({
  sheet: {
    position: "absolute", bottom: 0, alignSelf: "center", width: "100%", maxWidth: MAX_WIDTH,
    borderTopLeftRadius: radius.xl, borderTopRightRadius: radius.xl,
    elevation: 16, shadowOpacity: 0.18, shadowRadius: 16, shadowOffset: { width: 0, height: -4 },
  },
  head: { paddingHorizontal: space.lg, paddingBottom: space.sm },
  handle: { width: 40, height: 5, borderRadius: 3, alignSelf: "center", marginVertical: space.sm },
});
