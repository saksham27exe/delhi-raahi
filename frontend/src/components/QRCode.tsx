import QR from "qrcode";
import { useEffect, useRef } from "react";

export function QRCode({ value, label }: { value: string; label: string }) {
  const ref = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    if (ref.current) QR.toCanvas(ref.current, value, { margin: 1, width: 220, errorCorrectionLevel: "M" }).catch(() => {});
  }, [value]);
  return <div className="qr"><canvas ref={ref} role="img" aria-label={label} /></div>;
}
