# DelhiRaahi — technical note

Transport Stack Hackathon 2026 · Problem 2 (crowdedness-aware journey planner) · Phase 1

DelhiRaahi estimates how crowded each stretch of each Delhi bus route usually is at each half hour,
shows that level next to every journey option, and lets the rider choose between *fastest*,
*balanced* and *least crowded*. This note covers the method, the assumptions, the checks and where
the estimates should not be trusted. All numbers are reproduced by the scripts named in brackets.

---

## 1. Data used

| Data | What we used | Notes |
|---|---|---|
| ONDC e-tickets, 1–31 Oct 2026 | 2,559,899 tickets: time, route + direction (`534UP`), boarding and alighting stop names, fare, passengers | No ETM (conductor) records were available to us — see §6 |
| Static GTFS (Delhi buses v1-2) | 2,554 routes, 6,812 stops, 65,322 trips with stop times | Stop order, running times and scheduled departures |
| GTFS-Realtime vehicle positions | Live feed (30 s poll) | Only for live arrivals in the app; not used by the crowding model |

Data audit (`scripts/run_pipeline.py` → `data/processed/data_quality.json`): 0 bad timestamps, 0 exact
duplicates, 108 tickets with the same origin and destination (flagged, not dropped), ~51 % with no vehicle
number, two public holidays visible (2 Oct Gandhi Jayanti, 20 Oct Dussehra).

## 2. Method — from tickets to a crowd level

**Step 1 · Map tickets onto the network.** The route field already encodes direction, and 2,257 of 2,259
ticket routes match a GTFS route exactly (99.98 % of tickets). Stop names are matched only *within that
route's own stop list*, using a ladder of increasingly loose rules: exact → fuzzy → space-insensitive →
parts of "A / B" names → word subset (unique or within 600 m) → the same name elsewhere within 400 m → four
hand-reviewed aliases. 87.4 % of tickets end up with a boarding and an alighting stop on the right
pattern (2,238,435 resolved; 312,805 with an unmatched stop name; 6,957 in reverse order).
*Check:* if loose matches were wrong, fare would stop tracking ride length. The fare-vs-distance correlation is
0.57–0.64 for every match rule (exact: 0.615), so loose matches are as good as exact ones.

**Step 2 · Turn tickets into load on each stretch.** A ticket from stop A to stop D adds one passenger
(× passenger count) to stretches A→B, B→C and C→D. Each stretch is credited at the time the bus
actually crosses it: ticket time + the scheduled running time from A (median of the pattern's trips).
Summing over a 30-minute bucket gives the e-ticket passengers crossing that stretch in that half hour.

The brief suggests grouping tickets by bus trip and keeping a running count. We could not do that
reliably, because half the tickets have no vehicle number and ticket times can't be tied to a trip
without one. So we divide the passenger flow by the number of **scheduled departures** over that stretch
in the same half hour. The result is *demand per scheduled departure* (DPD): the average number of
e-ticket riders on board one bus on that stretch. This is the running count averaged over the buses in
the slot. It is an index, not a head count, because e-tickets are only part of all riders (§4).

**Step 3 · Expected level for a future date.** For each stretch, day type (weekday / Saturday /
Sunday or holiday) and half hour, the base is the historical mean DPD over 1–24 Oct. A route-level LightGBM
(Tweedie) forecast, which knows about holidays, recent trend and day of week, scales the history:

    expected stretch load = history × (route forecast + k) / (route history + k),   k = 2

The k = 2 term pulls the adjustment towards "same as history" when the route has few tickets. We tried a
first design that spread the route forecast over stretches by fixed shares. It did worse on the test week
(WAPE 0.90 vs 0.71), so we dropped it.

**Step 4 · Crowd level a rider understands.** DPD is cut into four levels at the **passenger-weighted**
50th / 80th / 95th percentiles of historical DPD: 1.25 / 3.23 / 7.36 e-ticket riders per bus.
"Passenger-weighted" means the cut-offs describe what a typical *rider* experiences, not a typical empty
late-night bus. Plain (unweighted) cut-offs had made every busy route "very high" all day.

| Level (app label) | Meaning for a rider |
|---|---|
| Low | Quieter than what half of all riders experience |
| Moderate | Busier than usual, but not among the busiest fifth |
| High | Among the busiest 20 % of rides |
| Very crowded | Among the busiest 5 % of rides |
| Unknown | No defensible estimate (§5); never shown as "Low" |

**Step 5 · Evidence.** Each estimate carries how many tickets support it: **High** (≥ 300 tickets for that
stretch, day type and time band), **Medium** (≥ 100), or **Low**. The thresholds come from measured
accuracy (§3). A journey's evidence is High when ≥ 80 % of its riding time is on High-evidence stretches,
Medium when ≥ 60 % is High or Medium, otherwise Low. The journey screen says so next to the crowd level
("Based on many tickets" … "Few tickets on this trip, so the crowd level is less certain"), and option cards repeat the
warning when evidence is Low.

**Step 6 · Journey planner.** We built our own bus router on the static GTFS: direct and one-transfer
journeys, walks of up to 300 m between stops, and options more than 1.6 × the fastest + 5 min pruned.
Typical queries take 0.6–1.3 s. Every leg looks up the crowd on each stretch *at the scheduled time that
bus reaches it*. A journey's crowd exposure = Σ (crowd index × minutes on that stretch). Stretches with no
estimate are scored at the 75th percentile, so missing data never looks like an empty bus. The
options are ranked by

    cost = w_time·time + w_crowd·exposure + w_transfer·changes + w_walk·walking   (each scaled 0–1 within the search)

with the weights per mode in `backend/app/journey_engine/ranking_config.json` (fastest 1.0/0.15,
balanced 0.5/0.5, least crowded 0.2/1.0). A departure advisor re-plans from −30 to +60 minutes to show
when a quieter bus leaves. Example (Kashmere Gate → Anand Vihar, Fri 08:30): fastest 214→357A, 47 min;
balanced 347→D-006, 49 min with 52 % less crowd exposure; least crowded 0261→236, 56 min.

## 3. How good is it?

Training uses 1–24 Oct; **25–31 Oct is a held-out test week**, used only for evaluation
(`scripts/train_model.py` → `models/metrics/*.json`).

| Check | Result |
|---|---|
| Route demand, test week, service hours | LightGBM MAE 0.869 vs best baseline (seasonal average) 0.865; Poisson noise floor 0.647. Network WAPE 8.5 %, route-day 16 % |
| 80 % prediction intervals | 77–82 % actual coverage in every size bin (Mondrian split-conformal) |
| Stretch level, test week (4.1 M stretch × half-hour rows) | WAPE 0.717 vs history 0.710; crowd-level macro-F1 0.581 vs 0.584; unbiased |
| Accuracy vs evidence | WAPE 1.41 below 10 supporting tickets → 0.50 at 100–300 → **0.31 at 300–1,000 → 0.21 above 1,000** |

In plain words, **the model ties a strong "same as usual" baseline at stretch level**. That is expected
with only four weeks: a busy bus line's usual pattern *is* the best guess. The ML part adds the
route-level corrections for holidays and trends. We report it as a tie and do not claim an improvement.
Accuracy depends mostly on how many tickets back an estimate, which is why the app shows the evidence level.

## 4. Checks against known patterns

From `scripts/check_patterns.py` → `models/metrics/pattern_checks.json`. Weekdays exclude the two holidays.

**Morning and evening peaks.** On weekdays, e-ticket riders per hour peak at **09:00 (4.6 × the midday
level)** and **18:00 (3.1 × midday)**. Sundays and holidays have the same peak hours but are much flatter
(2.1× and 1.7×). Saturday sits in between (4.1× / 2.4×), consistent with many offices working on Saturdays.

**Weekday vs weekend.** On average there are 102,298 e-ticket riders per weekday, **0.79×** that on
Saturdays, **0.49×** on Sundays and **0.50×** on public holidays. Holidays behave like Sundays, as expected,
and the model treats them as one day type.

**Tidal flow.** On two-way routes with enough riders (420 routes, ≥ 200 tickets in each peak), the
busier direction in the morning peak (07–10) is the quieter one in the evening peak (17–20) on **76 %**
of routes. Among routes that are clearly one-sided in both peaks (225), it swaps on **89 %**. This is the
home→work, work→home commute pattern.

**Busy corridors.** The busiest stretches (all routes together) are in south Delhi on the
Ambedkar Nagar – Khanpur – Saket – Mehrauli axis, plus Rainy Well ↔ Delhi Secretariat (59 routes). The
corridor reverses as expected: *Asian Market → Saket Crossing* is the busiest stretch in the morning
(≈ 1,300 e-ticket riders per weekday, 28 routes), and *Saket Crossing → Asian Market* is the busiest in the
evening. The south-Delhi dominance partly reflects where ONDC e-tickets are used (§6).

**Week-to-week stability.** Per stretch × half hour on weekdays, comparing each week with the next
(5–9, 12–16, 19–23 and 26–30 Oct):

| Tickets behind the estimate (two weeks) | Correlation (log DPD) | Same crowd level | Within one level |
|---|---|---|---|
| 10–30 | 0.24–0.38 | 61–63 % | 98 % |
| 30–100 | 0.57–0.67 | 60–65 % | 98–99 % |
| 100–300 | 0.75–0.77 | 75 % | 99.6–99.7 % |
| ≥ 300 | 0.66–0.81 | 100 % | 100 % |

Across all stretches, the log-DPD correlation between consecutive weeks is 0.75–0.77. The level
is the same in 86–87 % of rows, or 69–71 % weighted by riders. Busy, well-observed stretches are
stable from week to week; thin ones are noisy, which is what the evidence level warns about.

## 5. Assumptions

1. **Ticket time = boarding time.** E-tickets are bought on or just before boarding.
2. **Riders get off where the ticket says.** Some ride further or get off earlier. Over many tickets this
   averages out, but it is not checked against real alightings.
3. **Unresolved tickets (12.6 %) travel like resolved ones on the same route.** They count in route
   totals. They are not given to specific stretches.
4. **Riders not in the data** (cash/ETM tickets, pass holders, free travel for women on DTC/cluster buses)
   **are assumed to ride like e-ticket riders on the same route and time.** Under that assumption they scale
   every bus on a route by the same factor, so the levels *within* a route (which stretch, which hour) stay
   right. Comparisons *between* routes can still be off, because e-ticket uptake differs by route.
5. **Buses run to timetable.** Load is divided by *scheduled* departures. A cancelled trip makes the
   remaining buses busier than we estimate.
6. **One bus size.** GTFS has no vehicle type, and the data does not say whether a route runs 12 m or 9 m
   buses. A level means "busier than usual for riders", not "N standing". AC vs non-AC is known per route
   (from fares) but does not change the crowd level.

## 6. Where the estimates are least reliable

- **Thin stretches and hours** (early morning, late night, rarely used routes): under 30 supporting tickets,
  week-to-week correlation is below 0.4. Journeys over them are marked "less certain" in the app, and the planner
  treats missing estimates as fairly busy.
- **Absolute crowding.** We see e-tickets only. We **do not know the share of riders with e-tickets**, so we
  cannot say "seats available" or give a head count. That is why the app says *Low / Moderate / High / Very
  crowded* (relative to what riders usually experience) instead of *seats / standing room*. ETM records
  or automatic passenger counts for a sample of buses would let us convert DPD into passengers per bus
  and real seat/standing levels.
- **Between-route comparisons.** E-ticket uptake is higher on some corridors (the south-Delhi dominance in
  §4 is partly this). Within a route, levels are reliable; across routes, treat them as indicative.
- **Bus size** is not modelled (§5.6). A "High" on a 9 m bus is worse than on a 12 m bus.
- **Unusual days.** Four weeks show weekly patterns and two holidays only. There are no seasons, festivals
  beyond Dussehra, monsoon, strikes or events. Diwali-week demand, for example, is outside what the model has seen.
- **12.6 % of tickets have stop names we could not match** (mostly abbreviations). Stretches served mainly
  by those stops are under-counted. The hand-reviewed alias list (and the admin website's alias tool)
  reduces this.
- **Ticket timing** does not include waiting at stops or dwell time, and real running times vary with traffic.
  The live GPS feed is used for arrivals but not yet to correct crossing times.

## 7. Outputs and how to rerun

- **Pipeline** (someone else can rerun it): `scripts/run_pipeline.py` (tickets + GTFS → mappings →
  stretch demand + data-quality report, ~2.5 min) → `scripts/train_model.py` (route model, forecasts,
  calibration, evaluation) → `scripts/check_patterns.py` (§4). See the README.
- **Queryable crowding** (API, docs at `/docs`): `GET /api/v1/crowd/route/{id}?date&time` (stop by stop or
  through the day), `/crowd/segment/{id}`, `/crowd/hotspots?time` (map of busy stretches),
  `/insights/model` (evaluation numbers and limitations).
- **Planner**: `POST /api/v1/journeys/search` (crowd per leg and stretch, time, fare, ranking explanation),
  `/journeys/advisor`.
- **For riders**: the Android app shows a crowd strip and level on each option, plus crowd on the journey
  map, stop arrivals and live buses, in English and Hindi, with a voice assistant.
- **For operators and planners**: the admin website (`/admin`) shows crowd hotspots by time, support
  requests and complaints by route, and service alerts. The Insights screen in the app shows each route
  through the day.

## 8. What would make it better

1. ETM records for the same period: absolute loads, all routes, real seat and standing levels.
2. The October GPS archive: match tickets to vehicles and trips, which gives a true running count, and use real
   instead of scheduled running times and cancelled trips.
3. Fleet data (bus length, seats) per route.
4. More weeks: seasonal and festival patterns, and a model that can beat the "same as usual" baseline.
