# Demo video script (about 3 minutes)

Record the phone or tablet screen (Android: Quick settings → Screen recorder, with microphone on), plus
a browser window for the admin part. Start the API first, and sign in to the app before recording.

| # | Time | Show | Say (roughly) |
|---|---|---|---|
| 1 | 0:00–0:15 | App home screen | "A packed bus matters as much as a slow one. DelhiRaahi shows how crowded each bus option usually is, using a month of Delhi e-tickets." |
| 2 | 0:15–0:50 | Plan **Rainy Well → Delhi Secretariat**, weekday (e.g. Fri 20 Nov) 9:00 am, *Balanced*. Scroll the options | "Each option shows travel time, fare and a crowd strip, one block per stretch. The fastest bus, 118 Ext, takes 5 minutes but is very crowded. Route 316 takes 7 minutes and is usually quiet." |
| 3 | 0:50–1:10 | Switch *Fastest* ↔ *Least crowded*; then plan the brief's example **Anand Vihar ISBT → Connaught Place, 6 pm** | "Choosing what matters re-ranks the same options. On this longer trip every option is usually low, so the planner just picks the quickest." |
| 4 | 1:10–1:30 | Open an option: journey screen, crowd-coloured map, stop-by-stop list, "Based on many tickets" line | "The level comes from tickets crossing each stretch at the time this bus gets there. We also say how many tickets back it, and thin data is marked less certain." |
| 5 | 1:30–1:45 | Departure advisor ("leave later") | "It also checks buses leaving up to an hour later." Read out what it suggests; it only proposes a later bus when one is quieter (the brief's third option). |
| 6 | 1:45–2:05 | Insights: a busy route through the day; the hotspot map at 8:30 vs 13:30 | "For planners: the same estimates by route and time. You can see the morning and evening peaks and the busy south-Delhi corridor." |
| 7 | 2:05–2:20 | Live tab: buses on the map, arrivals with crowd | "Live positions come from GTFS-Realtime, with crowd shown on arrivals too." |
| 8 | 2:20–2:35 | Voice: tap the mic and ask in Hindi, e.g. "534 में भीड़ कितनी है?" | "Riders who can't see the screen can ask in Hindi or English. It runs on the phone, with no paid service." |
| 9 | 2:35–2:50 | Admin site: dashboard → hotspots → a complaint about a route | "Operators get a dashboard with hotspots, complaints by route and service alerts that appear in the app." |
| 10 | 2:50–3:00 | README / technical note on screen | "Method, checks and limits are in the technical note. Crowd levels are relative because we only see e-tickets. Everything reruns from the scripts in the repo." |

Tips
- Do a dry run first. Checked on 15 Nov via the API: Rainy Well → Delhi Secretariat at 9:00 gives Very crowded (4.9 min) vs Low (7.3 min);
  Ambedkar Nagar Depot → Saket Metro Station at 8:30 also mixes Moderate and Very crowded options.
- Turn on Do Not Disturb, and use a test account rather than your personal one if the email shows on screen.
- Tickets in the app are demos (watermarked). If you show booking, say so.
