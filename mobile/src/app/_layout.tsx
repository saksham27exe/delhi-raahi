import "../lib/i18n";
import { Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { AppProvider, useApp } from "../lib/state";

function Root() {
  const { theme, ready } = useApp();
  if (!ready) return null;
  return (
    <>
      <StatusBar style={theme.bg === "#000000" ? "light" : "dark"} />
      <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: theme.bg }, animation: "slide_from_right" }}>
        <Stack.Screen name="(tabs)" />
        <Stack.Screen name="search" options={{ animation: "slide_from_bottom" }} />
        <Stack.Screen name="results" />
        <Stack.Screen name="journey/[idx]" />
        <Stack.Screen name="book" options={{ presentation: "transparentModal", animation: "fade" }} />
        <Stack.Screen name="ticket/[id]" />
        <Stack.Screen name="stop/[id]" />
        <Stack.Screen name="insights" />
      </Stack>
    </>
  );
}

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <AppProvider>
        <Root />
      </AppProvider>
    </SafeAreaProvider>
  );
}
