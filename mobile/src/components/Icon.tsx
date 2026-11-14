import Svg, { Circle, Path, Rect } from "react-native-svg";

const P: Record<string, string> = {
  home: "M3 10.5L12 3l9 7.5V20a1 1 0 01-1 1h-5v-6H9v6H4a1 1 0 01-1-1v-9.5z",
  live: "M12 21s-7-6.2-7-11.5A7 7 0 0119 9.5C19 14.8 12 21 12 21z",
  ticket: "M4 7a2 2 0 012-2h12a2 2 0 012 2v3a2 2 0 000 4v3a2 2 0 01-2 2H6a2 2 0 01-2-2v-3a2 2 0 000-4V7zm9-2v14",
  user: "M12 12a4 4 0 100-8 4 4 0 000 8zm-7 9a7 7 0 0114 0",
  search: "M11 18a7 7 0 100-14 7 7 0 000 14zm9 3l-4.3-4.3",
  back: "M15 5l-7 7 7 7",
  close: "M6 6l12 12M18 6L6 18",
  swap: "M7 4v16m0 0l-3-3m3 3l3-3M17 20V4m0 0l-3 3m3-3l3 3",
  clock: "M12 21a9 9 0 100-18 9 9 0 000 18zm0-13v4l3 2",
  bus: "M6 17h12M7 17v2m10-2v2M6 4h12a1 1 0 011 1v11H5V5a1 1 0 011-1zm-1 7h14",
  walk: "M13 4a1.5 1.5 0 11-3 0 1.5 1.5 0 013 0zM9 21l2-6 2 2v4m-4-9l2-3 3 2 2 2M8 12l1-4",
  pin: "M12 21s-7-6.2-7-11.5A7 7 0 0119 9.5C19 14.8 12 21 12 21zm0-9a2.5 2.5 0 100-5 2.5 2.5 0 000 5z",
  locate: "M12 2v3m0 14v3M2 12h3m14 0h3m-10 5a5 5 0 100-10 5 5 0 000 10z",
  work: "M4 8h16v11H4zM9 8V5h6v3",
  star: "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z",
  chart: "M4 20V10m6 10V4m6 16v-7m4 7H2",
  chevron: "M9 6l6 6-6 6",
  globe: "M12 21a9 9 0 100-18 9 9 0 000 18zM3 12h18M12 3c2.5 2.5 3.5 5.5 3.5 9s-1 6.5-3.5 9c-2.5-2.5-3.5-5.5-3.5-9s1-6.5 3.5-9z",
  moon: "M20 14.5A8 8 0 019.5 4a8 8 0 1010.5 10.5z",
  server: "M4 5h16v6H4zm0 8h16v6H4zm3-5h.01M7 16h.01",
  info: "M12 21a9 9 0 100-18 9 9 0 000 18zm0-5v-5m0-3h.01",
  check: "M5 12l5 5L20 7",
  trash: "M4 7h16M9 7V4h6v3m-8 0l1 13h8l1-13",
  refresh: "M20 11a8 8 0 10-2.3 5.7M20 4v7h-7",
  sparkle: "M12 3v4m0 10v4M3 12h4m10 0h4M6 6l2.5 2.5m7 7L18 18m0-12l-2.5 2.5m-7 7L6 18",
};

export type IconName = keyof typeof P | "dot" | "square";

export function Icon({ name, size = 22, color = "#000", strokeWidth = 2 }: { name: IconName; size?: number; color?: string; strokeWidth?: number }) {
  if (name === "dot") return <Svg width={size} height={size} viewBox="0 0 24 24"><Circle cx="12" cy="12" r="6" fill={color} /></Svg>;
  if (name === "square") return <Svg width={size} height={size} viewBox="0 0 24 24"><Rect x="6" y="6" width="12" height="12" fill={color} /></Svg>;
  return (
    <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth={strokeWidth}
      strokeLinecap="round" strokeLinejoin="round">
      <Path d={P[name]} />
    </Svg>
  );
}
