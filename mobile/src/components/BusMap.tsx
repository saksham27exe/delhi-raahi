import { forwardRef, useEffect, useImperativeHandle, useRef } from "react";
import { StyleSheet, View } from "react-native";
import MapView, { Marker, Polyline, PROVIDER_GOOGLE, type LatLng } from "react-native-maps";
import { useApp } from "../lib/state";
import type { CrowdCategory } from "../lib/types";

export interface MapLine { id: string; coords: LatLng[]; category: CrowdCategory; dashed?: boolean }
export interface MapMarker { id: string; coord: LatLng; kind: "stop" | "bus" | "from" | "to" | "transfer"; title?: string; onPress?: () => void }
export interface BusMapHandle { fit: () => void; center: (c: LatLng, delta?: number) => void }

const DELHI = { latitude: 28.6315, longitude: 77.2167, latitudeDelta: 0.25, longitudeDelta: 0.25 };

function Dot({ kind }: { kind: MapMarker["kind"] }) {
  const { theme } = useApp();
  if (kind === "bus") return <View style={[m.bus, { backgroundColor: theme.accent }]} />;
  if (kind === "to") return <View style={[m.sq, { backgroundColor: theme.primary, borderColor: theme.card }]} />;
  if (kind === "from") return <View style={[m.ring, { borderColor: theme.primary, backgroundColor: theme.card }]} />;
  if (kind === "transfer") return <View style={[m.ring, { borderColor: theme.accent, backgroundColor: theme.card }]} />;
  return <View style={[m.stop, { backgroundColor: theme.card, borderColor: theme.primary }]} />;
}

/** Map with crowd-coloured route lines and simple markers. `bottomInset` keeps fitted content
 * above the bottom sheet. Lines always come with a text list elsewhere on the screen. */
export const BusMap = forwardRef<BusMapHandle, {
  lines?: MapLine[]; markers?: MapMarker[]; bottomInset?: number; showsUser?: boolean; fitKey?: string;
}>(function BusMap({ lines = [], markers = [], bottomInset = 0, showsUser = true, fitKey }, ref) {
  const { theme } = useApp();
  const map = useRef<MapView>(null);

  const fit = () => {
    const pts = [...lines.flatMap((l) => l.coords), ...markers.map((x) => x.coord)];
    if (pts.length > 1) {
      map.current?.fitToCoordinates(pts, { edgePadding: { top: 80, right: 40, bottom: bottomInset + 40, left: 40 }, animated: true });
    } else if (pts.length === 1) {
      map.current?.animateToRegion({ ...pts[0], latitudeDelta: 0.02, longitudeDelta: 0.02 }, 400);
    }
  };
  useImperativeHandle(ref, () => ({
    fit,
    center: (c, d = 0.02) => map.current?.animateToRegion({ ...c, latitudeDelta: d, longitudeDelta: d }, 400),
  }));
  useEffect(() => { const h = setTimeout(fit, 300); return () => clearTimeout(h); }, [fitKey]); // eslint-disable-line react-hooks/exhaustive-deps

  const dark = theme.bg === "#000000";
  return (
    <MapView ref={map} style={StyleSheet.absoluteFill} provider={PROVIDER_GOOGLE} initialRegion={DELHI}
      showsUserLocation={showsUser} showsMyLocationButton={false} toolbarEnabled={false} showsCompass={false}
      customMapStyle={dark ? DARK_STYLE : LIGHT_STYLE} mapPadding={{ top: 0, right: 0, left: 0, bottom: bottomInset }}>
      {lines.map((l) => (
        <Polyline key={l.id} coordinates={l.coords} strokeWidth={6} strokeColor={theme.crowd[l.category].line}
          lineDashPattern={l.dashed ? [6, 6] : undefined} lineCap="round" />
      ))}
      {markers.map((x) => (
        <Marker key={x.id} coordinate={x.coord} title={x.title} onPress={x.onPress} anchor={{ x: 0.5, y: 0.5 }} tracksViewChanges={false}>
          <Dot kind={x.kind} />
        </Marker>
      ))}
    </MapView>
  );
});

const m = StyleSheet.create({
  bus: { width: 16, height: 16, borderRadius: 8, borderWidth: 2.5, borderColor: "#fff" },
  stop: { width: 12, height: 12, borderRadius: 6, borderWidth: 3 },
  ring: { width: 16, height: 16, borderRadius: 8, borderWidth: 4 },
  sq: { width: 16, height: 16, borderWidth: 3 },
});

// muted, uncluttered base map so crowd colours stand out (Uber-like)
const LIGHT_STYLE = [
  { elementType: "geometry", stylers: [{ color: "#f5f5f5" }] },
  { elementType: "labels.icon", stylers: [{ visibility: "off" }] },
  { elementType: "labels.text.fill", stylers: [{ color: "#616161" }] },
  { featureType: "poi", stylers: [{ visibility: "off" }] },
  { featureType: "transit", stylers: [{ visibility: "off" }] },
  { featureType: "road", elementType: "geometry", stylers: [{ color: "#ffffff" }] },
  { featureType: "road.highway", elementType: "geometry", stylers: [{ color: "#e3e3e3" }] },
  { featureType: "water", elementType: "geometry", stylers: [{ color: "#d6e4ef" }] },
];
const DARK_STYLE = [
  { elementType: "geometry", stylers: [{ color: "#1d1d1d" }] },
  { elementType: "labels.icon", stylers: [{ visibility: "off" }] },
  { elementType: "labels.text.fill", stylers: [{ color: "#8a8a8a" }] },
  { elementType: "labels.text.stroke", stylers: [{ color: "#1d1d1d" }] },
  { featureType: "poi", stylers: [{ visibility: "off" }] },
  { featureType: "transit", stylers: [{ visibility: "off" }] },
  { featureType: "road", elementType: "geometry", stylers: [{ color: "#2c2c2c" }] },
  { featureType: "water", elementType: "geometry", stylers: [{ color: "#0e1626" }] },
];
