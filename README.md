# DelhiRaahi — crowd-aware bus journeys for Delhi

Transport Stack Hackathon 2026 · **Problem 2: crowdedness-aware journey planner** (Phase 1)

DelhiRaahi turns a month of ONDC bus e-tickets into an estimate of how crowded each stretch of each
Delhi bus route usually is, half hour by half hour. It shows that level next to every journey option,
so a rider can trade a few minutes for a calmer bus. *Fastest*, *balanced* and *least crowded* re-rank
the same options.

> **Read first:** [`docs/technical_note.md`](docs/technical_note.md): method, assumptions, accuracy,
> checks against known patterns, and where the estimates are least reliable.

| Deliverable (brief §4) | Where |
|---|---|
| Pipeline from raw tickets to crowding estimates, rerunnable | `scripts/run_pipeline.py` → `scripts/train_model.py` ([below](#rerun-the-pipeline)) |
| Crowding you can query or map, by route, stretch and time | API `/api/v1/crowd/*`, Insights screen in the app, hotspot map on the admin site |
| Journey planner with crowding next to each option and its travel time | Android app (`android/`) on top of the API (`/api/v1/journeys/search`) |
| Short technical note | [`docs/technical_note.md`](docs/technical_note.md) |
| Checks against known patterns (peaks, corridors, weekday vs weekend, week to week) | `scripts/check_patterns.py` → `models/metrics/pattern_checks.json`, summarised in the note §4 |

**Demo only:** tickets and passes in the app are marked *DEMO — not valid for travel*. No real money
is taken.

---

## What's in the repo

```
backend/     FastAPI service: data pipeline, crowd model, journey planner, fares, live buses, accounts, admin API
  app/data_pipeline/   ticket cleaning, route + stop matching, data-quality report
  app/demand_engine/   route and stretch (segment) demand
  app/ml_engine/       LightGBM route forecast, conformal intervals, stretch calibration + evaluation
  app/journey_engine/  own bus router on static GTFS + crowd-aware ranking (ranking_config.json)
  app/fare_engine/     DTC slab fares (fare_rules.json), validated against e-ticket amounts
  tests/               143 tests
scripts/     run_pipeline.py · train_model.py · check_patterns.py · validate_fares.py · fetch_gtfs.py
android/     the rider app (Kotlin, Jetpack Compose): plan, live buses, demo tickets, insights, voice, en + hi
admin/       admin website for operators (React): dashboard, crowd hotspots, support, users, alerts, audit
frontend/    earlier web version (PWA) of the rider app, Phase 9
mobile/      earlier Expo prototype, kept for reference
models/metrics/   evaluation results (JSON) — committed, so the numbers in the note can be checked
docs/        technical note, development plan
```

## Quick start

Needs **Python 3.12**. Node 20+ is only needed for the admin site; JDK 17+ and the Android SDK only for the app.

```bash
python -m venv .venv
.venv/Scripts/pip install -r requirements.txt      # Linux/macOS: .venv/bin/pip
cp .env.example .env                               # add TS_API_KEY for live buses (optional)
```

### Data

The raw data is not redistributed (licence). Download it from
[delhi.transportstack.in](https://delhi.transportstack.in) and place it as follows:

| File | Put it in |
|---|---|
| `October-Delhi.csv` (ONDC e-tickets, 1–31 Oct 2026) | `data/raw/October-Delhi.csv` |
| Delhi buses static GTFS (zip) | unzip into `data/gtfs/` (or `python scripts/fetch_gtfs.py` with `TS_API_KEY`) |

### Rerun the pipeline

```bash
python scripts/run_pipeline.py        # tickets + GTFS → mappings → stretch demand + data-quality report (~2.5 min)
python scripts/train_model.py         # route model, forecasts, crowd-level calibration, test-week evaluation (~5 min)
python scripts/check_patterns.py      # peaks, day types, tidal flow, busy corridors, week-to-week stability (~1 min)
python scripts/validate_fares.py      # fare rules vs e-ticket amounts, AC/non-AC per route
```

Outputs go to `data/processed/` (Parquet tables, crowd arrays), `models/artifacts/` (model and calibration)
and `models/metrics/` (evaluation JSON).

### Run the API

```bash
cd backend
../.venv/Scripts/python -m uvicorn app.main:app --host 0.0.0.0 --port 8010
```

Interactive API docs are at http://localhost:8010/docs. Some examples:

```bash
curl "localhost:8010/api/v1/stops/search?q=kashmere"
curl "localhost:8010/api/v1/crowd/route/<route_id>?date=2026-11-20"             # through the day
curl "localhost:8010/api/v1/crowd/route/<route_id>?date=2026-11-20&time=08:30"  # stop by stop
curl "localhost:8010/api/v1/crowd/hotspots?time=08:30"
curl -X POST localhost:8010/api/v1/journeys/search -H "Content-Type: application/json" \
     -d '{"origin":{"type":"stop_name","name":"GGS University ISBT Kashmere Gate"},
          "destination":{"type":"stop_name","name":"Anand Vihar ISBT"},
          "departure_datetime":"2026-11-20T18:00:00+05:30","preference":"least_crowded"}'
```

Without `TS_API_KEY` the API runs on timetables (no live buses). Without a Firebase service-account file,
accounts are kept in local SQLite. Both are optional for judging the crowding model.

### Admin website (operators)

```bash
cd admin && npm install && npm run build            # served by the API at /admin
cd ../backend && ../.venv/Scripts/python scripts/create_admin.py --email you@example.org --name "You" --role owner
```

The one-time password is written to `data/admin/new_admin_password.txt`, never printed. Sign in at
http://localhost:8010/admin, choose your own password, then delete the file. The site has a dashboard with
crowd hotspots, a support desk with complaints by route, users and demo bookings, service alerts shown in the
app, stop-name fixes, and a tamper-evident audit log.

### Android app (riders)

Set these in `android/local.properties`:

```
api.base=http://<your-computer's-LAN-IP>:8010
MAPS_API_KEY=<Google Maps SDK key>
```

Then build:

```bash
cd android
./gradlew assembleDebug                              # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The server address can also be changed inside the app under **Account → Settings → Advanced**.

## Tests

```bash
cd backend && ../.venv/Scripts/python -m pytest -q          # 143 tests: pipeline, model, planner, fares, API, admin
cd android && ./gradlew testDebugUnitTest                   # app unit tests
```

## Key numbers

All numbers are in `models/metrics/` and explained in the technical note.

- 2.56 M e-tickets; **87.4 %** mapped onto a stop-to-stop path, and 99.98 % onto a route.
- Test week 25–31 Oct, held out: stretch-level crowd ties a strong "same as usual" baseline (WAPE 0.717
  vs 0.710, macro-F1 0.581 vs 0.584). WAPE is **0.21–0.31** where an estimate rests on 300+ tickets.
- 80 % prediction intervals cover 77–82 % in every size bin.
- Weekday peaks at **09:00 and 18:00**. Sundays and holidays carry about half the weekday volume. The busier
  direction swaps between the morning and evening peaks on **76 %** of two-way routes.
- Slab fares match the e-ticket amount exactly for 82 % of tickets, and within one slab for 99.4 %.

## Limitations (short)

We have e-tickets only, with no ETM or pass data. So crowd levels are **relative**: busier or quieter than
riders usually experience. They are not seat counts. Comparisons between routes are partly confounded by
e-ticket uptake, and bus size is unknown. Details and what would fix each one are in the
[technical note](docs/technical_note.md#6-where-the-estimates-are-least-reliable).
