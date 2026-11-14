import { useColorScheme } from "react-native";
import type { CrowdCategory } from "./types";

/** Design tokens: monochrome, high-contrast "ride app" look; colour is reserved for crowd levels. */
const light = {
  bg: "#FFFFFF", surface: "#F3F3F3", surface2: "#E8E8E8", card: "#FFFFFF",
  text: "#000000", muted: "#5E5E5E", border: "#E5E5E5",
  primary: "#000000", onPrimary: "#FFFFFF", accent: "#0F766E", danger: "#B42318",
  overlay: "rgba(0,0,0,0.45)", shadow: "#000000",
  crowd: {
    LOW: { fg: "#0E7A3B", bg: "#DFF5E7", line: "#16A34A" },
    MODERATE: { fg: "#8A5A00", bg: "#FFF1CC", line: "#D19A00" },
    HIGH: { fg: "#B33D00", bg: "#FFE4D1", line: "#EA580C" },
    VERY_HIGH: { fg: "#B11B1B", bg: "#FDE0E0", line: "#DC2626" },
    UNKNOWN: { fg: "#4B5563", bg: "#ECEDEF", line: "#9CA3AF" },
  } as Record<CrowdCategory, { fg: string; bg: string; line: string }>,
};

const dark: typeof light = {
  bg: "#000000", surface: "#1C1C1E", surface2: "#2C2C2E", card: "#141414",
  text: "#FFFFFF", muted: "#A1A1A6", border: "#2C2C2E",
  primary: "#FFFFFF", onPrimary: "#000000", accent: "#2DD4BF", danger: "#F87171",
  overlay: "rgba(0,0,0,0.6)", shadow: "#000000",
  crowd: {
    LOW: { fg: "#4ADE80", bg: "#12301F", line: "#22C55E" },
    MODERATE: { fg: "#FACC15", bg: "#3A2F0B", line: "#EAB308" },
    HIGH: { fg: "#FB923C", bg: "#3B2210", line: "#F97316" },
    VERY_HIGH: { fg: "#F87171", bg: "#3D1515", line: "#EF4444" },
    UNKNOWN: { fg: "#CBD5E1", bg: "#2A313B", line: "#6B7280" },
  },
};

export type Theme = typeof light;
export const space = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32 };
export const radius = { sm: 8, md: 12, lg: 16, xl: 24, pill: 999 };
export const font = {
  h1: { fontSize: 28, fontWeight: "700" as const, letterSpacing: -0.5 },
  h2: { fontSize: 22, fontWeight: "700" as const, letterSpacing: -0.3 },
  h3: { fontSize: 17, fontWeight: "600" as const },
  body: { fontSize: 15, fontWeight: "400" as const },
  bodyStrong: { fontSize: 15, fontWeight: "600" as const },
  small: { fontSize: 13, fontWeight: "400" as const },
  tiny: { fontSize: 11, fontWeight: "500" as const },
};
/** Content never stretches past this on tablets. */
export const MAX_WIDTH = 560;

export function useTheme(pref: "system" | "light" | "dark" = "system"): Theme {
  const sys = useColorScheme();
  const mode = pref === "system" ? sys : pref;
  return mode === "dark" ? dark : light;
}
