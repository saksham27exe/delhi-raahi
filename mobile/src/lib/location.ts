import * as Location from "expo-location";
import { useCallback, useEffect, useState } from "react";

export interface Coord { latitude: number; longitude: number }

/** Foreground location only, asked once. `null` coord + `denied` when unavailable. */
export function useLocation() {
  const [coord, setCoord] = useState<Coord | null>(null);
  const [denied, setDenied] = useState(false);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const { status } = await Location.requestForegroundPermissionsAsync();
      if (status !== "granted") { setDenied(true); return; }
      const last = await Location.getLastKnownPositionAsync();
      if (last) setCoord({ latitude: last.coords.latitude, longitude: last.coords.longitude });
      const pos = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
      setCoord({ latitude: pos.coords.latitude, longitude: pos.coords.longitude });
      setDenied(false);
    } catch {
      setDenied(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { refresh(); }, [refresh]);
  return { coord, denied, loading, refresh };
}
