# DelhiRaahi — Development Plan

**DelhiRaahi** (working name) is a complete Delhi travel app — bus first, metro later — whose
differentiator is crowd intelligence (Transport Stack Hackathon 2026, Problem 2).
Phase 1 deadline: **24 Nov 2026, 23:59 IST** (submission: repo + README, technical note, demo video).
Rule: each phase ends with something that **runs and is tested** before the next phase starts.

## Product decisions (agreed 13 Nov 2026)
| Topic | Decision |
|---|---|
| Platform | Installable mobile-first **PWA** (React). Capacitor wrap for stores later if needed. |
| Scope v1 | Bus only: plan route · check crowd · live bus tracking (GTFS-RT) · fare · demo booking + ticket wallet. |
| Crowd | Shown on every screen (results, journey detail, live bus, stop arrivals), not a separate silo. |
| Booking | **Demo flow**, every ticket marked "Demo ticket — not valid for travel, no payment". Real ONDC booking = later goal (ask organisers about a sandbox). |
| Accounts | None in v1; saved places, recent trips and tickets stored on device. |
| Languages | **English + Hindi from day 1** (i18n framework, all UI strings translated); more languages later. |
| Accessibility | WCAG-minded from day 1 (semantic HTML, ARIA, screen-reader labels, large touch targets, no colour-only crowd cues) — the base for the voice feature. |
| Multimodal | Metro etc. later; router and data model kept mode-agnostic so it is an extension, not a rewrite. |
| Bonus (later) | **Voice assistant for blind / low-vision users**: ask in Hindi or English for bus info, next arrival, crowd and ticket price; spoken answers. |

### Design consequences of the bonus feature (build now, cheaply)
- Every user-facing answer exists as a **structured API response** (route, next bus, fare, crowd) so a voice
  layer can call the same endpoints the screens use.
- Backend returns **human-readable summary strings in en + hi** next to numbers (e.g. crowd explanation).
- Stop-name search tolerant to spelling/transliteration (fuzzy matching already in place); Hindi stop names
  need a transliteration table (GTFS names are English only).
- Voice stack options (decide in that phase): browser Web Speech API (hi-IN / en-IN STT+TTS, free) +
  rule-based intents, or an LLM intent layer for free-form questions.

Status legend: ✅ done · 🟡 code written, not yet run on real data · ⬜ not started

---

## Phase 0 — Foundations ✅
- Repo layout, `.gitignore`, `.env.example`, config (`backend/app/core/config.py`), Python venv.
- Data acquired:
  - `data/raw/October-Delhi.csv` — ONDC e-tickets, 2,559,899 rows (SHA256 verified).
  - `data/gtfs/` — Delhi Buses static GTFS v1-2: 2,554 routes, 6,812 stops, 65,322 trips.
  - `data/raw/shared_mobility_gtfs/` — e-rickshaw GTFS-Flex (Phase 2 idea, not used yet).
  - Transport Stack API key works for GTFS-RT (`scripts/fetch_gtfs.py --rt`); static GTFS is browser-download only.
- Decisions:
  - **DuckDB + Parquet instead of PostgreSQL** — read-only analytical data, zero setup for judges.
  - **Own bus-only router instead of `transport-stack/journey-planner`** — that repo is Django/multimodal and
    needs private data files + depot feeds not supplied; studied and not forked (Apache-2.0).

## Phase 1 — Ticket data audit & demand panel ✅
- `data_pipeline/ingestion/ticketing_loader.py` → `tickets_clean.parquet` (13 s). Raw values kept, rows flagged not dropped.
- Findings: 0 bad timestamps, 0 exact duplicates, 108 same-origin/destination, ~51% missing vehicle number,
  route column encodes direction (`8UP`/`8DOWN`), fares are stage-based, holidays 2 Oct & 20 Oct visible.
- `demand_engine/route_demand.py` → route-direction × day × 30-min panel (1,731 routes, 99.8% of tickets).

## Phase 2 — Route demand forecasting ✅ (re-validate after interval change)
- `ml_engine/features.py` (leakage-safe, 7-day horizon), `train.py`, `metrics.py`, `forecast.py`.
- Split: train 8–21 Oct · validation 22–24 · test 25–31; rolling-origin backtest.
- Test (service hours): LightGBM MAE 0.869 vs seasonal avg 0.865 vs same-weekday 0.925 — tie with best baseline;
  Poisson noise floor 0.647; route-day WAPE 16%, network WAPE 8.5%. Reported honestly.
- Intervals: switched to locally adaptive split-conformal (80%). **TODO:** read new coverage from
  `models/metrics/route_model_evaluation.json`.

## Phase 3 — GTFS + mapping + segment demand ✅
- `python scripts/run_pipeline.py --skip-tickets` → all tables + `data_quality.json` in ~2.3 min.
- Route mapping: 2,257 / 2,259 ticket routes exact (99.98% of tickets).
- Stop mapping (route-constrained ladder): exact → fuzzy → space-insensitive → '/'-name parts →
  word-subset (unique or co-located ≤600 m) → same name elsewhere ≤400 m → 4 reviewed aliases
  (`data/mappings/stop_aliases.csv`). Tickets usable for segment analysis: 56.8% → **87.4%**.
- OD outcomes: 2,238,435 resolved · 312,805 stop unmapped (long tail, 2k names) · 6,957 reverse order.
- **Validation:** fare-vs-ride-length correlation is 0.57–0.64 for every match method (exact: 0.615),
  same stops per fare band → looser matches are as reliable as exact ones (`fare_consistency_ac_tickets`).
- Segment demand + supply: 83,772 segments, 76,876 with demand; 717AUP spot check physically plausible
  (load builds to Saket, drops at Saket Metro; 69 scheduled departures/day).
- Tests: 28 passing (`backend/tests`): normaliser, route/stop ladder, OD resolver incl. loops,
  ticket loader (IST, no imputation, schema), end-to-end pipeline with hand-computed segment counts.
- Bug found by tests & fixed: invalid tickets could leak into segment flow (filter on od_status).
- Known gaps: remaining 12% unmapped stop names (abbreviations); improve via alias list later.

## Phase 4 — Segment crowd model & calibration ✅
- Route intervals: Mondrian split-conformal by predicted size → test coverage 77–82% in every size bin
  (nominal 80%); single global calibration had under-covered busy hours (73%).
- Segment method changed after evaluation: share-spreading (first design) was **worse** than a segment's
  own history (WAPE 0.90 vs 0.71, F1 0.36 vs 0.52; partly a 19% matched-ticket bias). Replaced by
  hierarchical adjustment `segment history × (route forecast + k)/(route history + k)`, k = 2 fixed a priori.
- Test week (25–31 Oct, 4.1M segment-buckets): WAPE 0.717 vs history 0.710; macro-F1 0.581 vs 0.584 —
  **ties the strong baseline**, unbiased; ML adds route-level holiday/trend adjustment. Accuracy scales with
  evidence: WAPE 0.31 at ≥300 supporting tickets, 0.21 at ≥1000 → evidence HIGH ≥300, MEDIUM ≥100.
- Crowd categories: passenger-weighted p50/p80/p95 of historical demand per scheduled departure
  (1.25 / 3.23 / 7.36) — the crowding a typical rider experiences. Unweighted cut-offs made busy routes
  VERY_HIGH all day. Sanity: 717AUP Fri 08:30 VERY_HIGH, Sun HIGH, Fri 13:30 MODERATE, 21:30 LOW.
- Crowd service loads precomputed arrays (`crowd_arrays.npz`) in 1.4 s; ~85 µs per prediction.
- Tests: 37 passing (metrics, interval coverage incl. busy periods, leakage, weekday mapping, fallbacks,
  UNKNOWN never LOW, post-calibration data ignored, weighted quantile).
- **Limitation (document):** ONDC uptake differs by route, so cross-route comparisons are partly
  confounded by uptake; per-route uptake calibration (GPS/ETM) is a Phase-2 item.

## Phase 5 — Journey planner & crowd-aware ranking ✅
- Network: vectorised build 7.3 s, pickle cache 0.3 s. Journey service ready ~6 s after start (incl. crowd arrays).
- Search: direct + one transfer (footpaths ≤ 300 m). One option kept per route / route pair; options slower
  than 1.6 × fastest + 5 min are pruned before crowd scoring (max 60 scored). Real queries
  (Kashmere Gate → Connaught Place / Nehru Place / Anand Vihar, Fri 08:30): **0.6–1.3 s**.
- Crowd is looked up per segment at the scheduled time that bus crosses it (2nd leg uses its own times).
- Modes re-order results: e.g. to Anand Vihar, fastest = 214→357A (47 min, CI 0.29), balanced = 347→D-006
  (49 min, −52 % exposure), least-crowded = 0261→236 (56 min, CI 0.01).
- Stop search: WRatio + share of query words found (typo-tolerant) + prefix + routes-served tiebreak.
  Known gap: area names with no matching stop name (e.g. "Connaught Place" → Regal/Janpath) need a
  landmark alias table (Phase 8/9).
- Departure advisor: −30…+60 min; ~7 s for 7 offsets (each a full plan) — acceptable, cache later if needed.
- Tests: 44 passing (+7: direct, transfer with walk, no service / wrong direction / bad stop / bad mode,
  crowd at crossing time, preference changes ranking, stop search, advisor).

## Phase 6 — Live bus tracking ✅
- `services/live_service.py`: background poller (30 s, key only in a request header) → in-memory tracker.
  Started by `state.start_live()`; without a key the app runs on timetables.
- Real feed (13 Nov, ~23:50): ~1,000 buses, all fresh positions. **route_id matches static GTFS (~94 %);
  trip_id does NOT** (runtime ids like `1597_23_48_8177`), and there is no stop_id or occupancy.
- Matching: route → snap position onto each pattern's stop polyline (median 77 m off-route) → scheduled
  trip with start within 30 min of `trip.start_time` whose schedule is closest at that position.
  Matches with |delay| > 60 min are rejected as wrong. ~37 % of buses matched to a trip; delay median −1.6 min,
  P10/P90 −28/+18 min (late-night snapshot; re-check in daytime).
- Arrivals board per stop: tracked buses = scheduled time + live delay (`live_estimate`); untracked but moving
  buses = typical running time (`live_estimate_typical_running_time`); everything else `scheduled`.
  Stale (>10 min) positions, >300 m off-route, parked unmatched buses and terminal stops are excluded.
  Feed down → timetable with a note. ~6 ms per stop.
- Feed speed is m/s (checked against displacement between two polls).
- Tests: 50 passing (+6: parse, snap & delay, live + timetable merge without duplicates, unusable vehicles,
  outage fallback & poller survives errors, terminal stop).

## Phase 7 — Fare engine + demo booking ✅
- Ticket data: every e-ticket is exactly 90 % of a DTC slab fare (non-AC 4.5/9/13.5, AC 9/13.5/18/22.5)
  → 10 % digital discount. GTFS `fare_stage` is mostly 0 and `stop_dist` empty → fares use distance.
- `fare_engine/fare_rules.json` (editable): non-AC ₹5/10/15 (≤4 / ≤10 / >10 km), AC ₹10/15/20/25
  (≤4 / ≤8 / ≤12 / >12 km), distance = straight line between consecutive stops along the bus's pattern.
- **Validation** (`scripts/validate_fares.py` → `metrics/fare_validation.json`, 2.24 M resolved tickets):
  exact rupee match 82 % (non-AC 87 %, AC 81 %), within one slab 99.4 %, mean error +₹0.06.
  Misses are near slab boundaries; 0.65 % non-standard amounts = interstate routes (8, 33, 34, 392, 443…),
  flagged per route with a note.
- Bus type per route from AC e-ticket share (`route_fare_profile.parquet`): 1,722 AC, 379 non-AC,
  141 mixed. Unknown/mixed → both fares shown (range); booking prices mixed fleets at AC unless chosen.
- Journeys now carry per-leg and total fares (one ticket per bus).
- `services/ticket_service.py` (demo): quote → book → verify. Server recomputes fares (client prices
  ignored); women-free passengers priced 0 (concession text en/hi, "check current rules");
  ticket = HMAC-signed QR payload (`DEMO-…`, valid 120 min), stateless, kept in the on-device wallet;
  watermark en/hi "DEMO TICKET — not valid for travel, no payment taken".
- Tests: 58 passing (+8: slabs/discount, unknown & profiled bus type, server-side quote, bad requests,
  mixed fleet, sign/verify/expiry/tamper/foreign key, fares on journeys).

## Phase 8 — API ✅
Run: `cd backend && ../.venv/Scripts/python -m uvicorn app.main:app --port 8000` → docs at `/docs`.
Startup loads everything once (~6 s) and starts the GTFS-RT poller if `TS_API_KEY` is set.

| Area | Endpoints |
|---|---|
| meta | `GET /api/v1/health` (components, load errors, model version) |
| stops & routes | `GET /stops/search?q=`, `/stops/nearby?lat&lon`, `/stops/{id}`, `/routes/search?q=717`, `/routes/{id}` |
| journeys | `POST /journeys/search`, `POST /journeys/advisor` |
| live | `GET /live/status`, `/live/vehicles?route_id&bbox`, `/live/arrivals/{stop_id}` |
| fares & demo tickets | `GET /fares/rules`, `POST /fares/quote`, `POST /tickets/book`, `POST /tickets/verify` |
| crowding | `GET /crowd/route/{id}?date` (through the day), `?time=HH:MM` (stop by stop), `/crowd/segment/{id}`, `/crowd/hotspots?time` |
| insights | `GET /insights/model` (model evaluations, fare validation, data quality, limitations) |

- User-facing answers carry `summary: {en, hi}` (journeys, arrivals, fare quote, crowd) — one sentence each,
  reusable by the later voice assistant (`services/summaries.py`).
- Every crowd response states the metric, its definition and `forecast_basis`.
- Hotspots use a vectorised `CrowdService.predict_all` (84 k segments in ~80 ms), restricted to
  MEDIUM+ evidence; a test checks it equals the single-segment prediction.
- Real-data latency (warm connection): health 39 ms, stop search 37 ms, hotspots 79 ms, route day profile
  130 ms, journey search ~1.1 s. **Advisor ~7.8 s** (7 full searches) → frontend loads it on demand;
  optimise later if needed.
- Tests: 65 passing (+7 API tests via TestClient on the synthetic network + crowd model).

## Phase 9 — PWA frontend ✅ (first version)
Run: backend `uvicorn app.main:app --port 8000`, then `cd frontend && npm run dev` → http://localhost:5173
(`API_PROXY=http://127.0.0.1:8010 npm run dev` if the backend runs on another port).

- React 18 + TS + Vite 5 + react-router + i18next + react-leaflet (OpenStreetMap) + qrcode; plain CSS with
  design tokens instead of Tailwind, small SVG chart instead of Recharts (lean bundle: 144 kB gzip JS).
- PWA: manifest + service worker (vite-plugin-pwa); stops/routes/fare rules/insights cached
  stale-while-revalidate, map tiles cache-first; live data and journeys never served from cache.
- Tabs: **Plan** (stop combobox + my location, now/date-time, fastest/balanced/least-crowded, journey cards
  with crowd strip, labels, fare; departure advisor on demand) → **Journey** (summary, crowd-coloured map,
  per-leg timeline with per-segment crowd, Track bus, Get demo ticket) · **Live** (stop search / nearby,
  arrivals with live/timetable basis and delay, buses on map, 30 s refresh paused when hidden, route filter)
  · **Tickets** (on-device wallet, DEMO watermark + banner, QR, verify, delete) · **Insights** (route
  through-the-day chart, hotspot map by time, model vs baseline numbers, fare accuracy, limitations) ·
  **Profile** (language, theme, larger text, high contrast, saved places, clear recent, about/privacy).
- Booking dialog: passengers / women-free steppers, AC/non-AC choice for mixed fleets, server quote.
- English + Hindi for every UI string (top-bar toggle + Profile); test enforces identical keys/placeholders.
- Accessibility: skip link, landmarks, focus moved to page heading on navigation, ARIA combobox for stop
  search, aria-pressed toggles, live regions for results/arrivals, colour never the only signal (text
  badges, map tooltips + lists, chart has a screen-reader table), 44 px targets, reduced motion,
  safe-area insets, storage failures never break the app.
- Tests: `npm test` 5 (i18n parity, Delhi time, storage incl. blocked storage); `npm run build` type-checks.
- **Not yet verified in a real browser by me** (no browser tool this session): needs a manual pass on phone
  + desktop, a screen reader pass (TalkBack/NVDA), and Lighthouse PWA/a11y.
- Known gaps: backend notes, ranking explanations and advisor reasons are English only; stop names are
  English (GTFS has no Hindi names).

## Phase 11 — Admin website (agreed 15 Nov 2026: all areas, in phases) ✅
React + TypeScript app in `admin/`, built into the backend and served at `/admin` (same origin).
Admin data lives in `data/admin/admin.db` (SQLite, git-ignored): admins, sessions, support requests,
service alerts, overrides, audit log. Admin accounts are server-only (created by
`scripts/create_admin.py`), scrypt-hashed passwords, HttpOnly SameSite=Strict cookie sessions, CSRF header,
roles: owner (everything incl. admins), agent (support + users), viewer (read only). Every write is audited.
- **A — Foundation** ✅: admin store + auth API + `/admin` static serving; app shell, login, roles, audit log.
- **B — Support desk** ✅: support requests move to admin.db (status, priority, assignee, notes, email
  replies with the reference, CSV export); deleting an account deletes its requests.
- **C — Users & bookings** ✅: search users; profile, demo wallet, tickets, passes, requests; disable/enable,
  resend verification, demo wallet adjustment, void ticket/pass (verify rejects it), delete account.
- **D — Service alerts & data fixes** ✅: notices for all / routes / stops with start–end time, shown in the
  Android app (Home banner, trip results, live bus); stop name aliases for search; AC/non-AC overrides.
- **E — Ops dashboard** ✅: KPIs + charts (users, bookings, requests by type/route, crowd hotspots, model
  card), health of live feed / voice model / email / data loads, audit log viewer, CSV exports.

## Phase 10 — Submission ⬜  ← NEXT
README, `docs/technical_note.md` (methodology, assumptions, limitations), demo script + video,
reproducible setup (`requirements.txt`, `docker-compose.yml`), final test run, git history clean.

---

### After 24 Nov (hackathon Phase 2 and beyond)
- **Voice assistant for blind / low-vision users** (bonus feature, en + hi).
- Metro (Delhi Metro static GTFS is on Transport Stack) → multimodal journeys; more languages.
- Real ticketing via ONDC sandbox; accounts.
- GPS history (Oct SQLite) to validate supply & ticket timestamp semantics; vehicle-level occupancy;
  anomaly detection; operator dashboard; e-rickshaw last-mile legs.
