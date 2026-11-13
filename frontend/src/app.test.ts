import { describe, expect, it, vi } from "vitest";
import { delhiNow, toIso } from "./api";
import { resources } from "./i18n";
import { store } from "./storage";

function keys(o: object, p = ""): string[] {
  return Object.entries(o).flatMap(([k, v]) => (typeof v === "object" ? keys(v, p + k + ".") : [p + k]));
}

describe("i18n", () => {
  it("Hindi has exactly the same keys as English", () => {
    expect(keys(resources.hi.translation).sort()).toEqual(keys(resources.en.translation).sort());
  });
  it("placeholders match in both languages", () => {
    const en = resources.en.translation as Record<string, any>, hi = resources.hi.translation as Record<string, any>;
    const get = (o: any, k: string) => k.split(".").reduce((a, x) => a[x], o) as string;
    for (const k of keys(en)) {
      const ph = (s: string) => (s.match(/{{\w+}}/g) ?? []).sort().join();
      expect(ph(get(hi, k)), k).toBe(ph(get(en, k)));
    }
  });
});

describe("time helpers", () => {
  it("formats Delhi time regardless of device timezone", () => {
    const d = new Date("2026-11-20T03:00:00Z"); // 08:30 IST
    expect(delhiNow(d)).toEqual({ date: "2026-11-20", time: "08:30" });
    expect(toIso("2026-11-20", "08:30")).toBe("2026-11-20T08:30:00+05:30");
  });
});

describe("storage", () => {
  it("keeps tickets and recent trips, de-duplicated", () => {
    localStorage.clear();
    const tk = { ticket_id: "DEMO-1" } as any;
    store.addTicket(tk);
    expect(store.tickets().map((t) => t.ticket_id)).toEqual(["DEMO-1"]);
    store.removeTicket("DEMO-1");
    expect(store.tickets()).toEqual([]);
    const r = { from: { label: "A", place: { type: "stop", id: "1" } }, to: { label: "B", place: { type: "stop", id: "2" } }, at: "" } as any;
    store.addRecent(r); store.addRecent(r);
    expect(store.recent()).toHaveLength(1);
  });
  it("never throws when storage is blocked (private mode)", () => {
    const get = vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => { throw new Error("blocked"); });
    const set = vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => { throw new Error("blocked"); });
    expect(store.tickets()).toEqual([]);
    expect(() => store.addTicket({ ticket_id: "X" } as any)).not.toThrow();
    expect(store.settings().lang).toBe("en");
    get.mockRestore(); set.mockRestore();
  });
});
