---
type: integration
updated: 2026-10-17
related:
  - "[[fishmod.dev Dashboard]]"
  - "[[RemoteSync Backend]]"
  - "[[Test Board]]"
  - "[[Hypixel API]]"
---

# fishmod-worker

The `fishmod` Cloudflare Worker behind `fishmod.dev`: Hypixel proxy, shared cache, player directory, public boards, admin dashboard. Distinct from the tester board worker (`fmd-board`, [[Test Board]]).

## Where it lives

- Private repo `Redfish247/fishmod-worker`; **one working copy: `D:\FishMod-WorkerBoard`**. Consolidated 2026-10-15 from four stray copies (PR #11). Older notes naming `D:/fishmod/fishmod-worker` or `C:\FishMod\fishmod-worker` point at deleted folders.
- Deploy: `npx wrangler deploy` from the repo. Last deploy wins and there is no CI gate; a manual GitHub Actions "Deploy worker" workflow exists but had never run as of 10-07.

## What it does

- **Mod API:** Hypixel proxy (`/player`, `/skyblock/*`, `/guild`, `/networth`, `/status`) at the legacy paths every released jar uses and under `/api/...`. All need the public `X-FishMod-Token`, else 401. Optional `X-FishMod-Caller` (uuid) is logged and checked against blocked players. A hard IP block list lives in `worker.js`.
- **Caching:** 5-minute shared cache across users (`X-FishMod-Cache` HIT/MISS); `/status` uncached; networth reuses the cached profile and museum. Eli is on a dev Hypixel key, so volume matters.
- **Storage:** D1 `fishmod-nicks` (players via `/seen` heartbeat, nicks, scales, item customs, `api_calls`, badges, roadmap, releases, mojang name cache, sweep log, achievements) plus KV.
- **Routes:** `/` hub; `/incomming-features` (+ `/bugs`) Working On board; `/dashboard/installs` owner-only (Discord OAuth); `/dashboard/claude-changelog` hidden owner page, 404 for everyone else ([[Claude Changelog]]); `/streams`; bearer roadmap-status endpoint. Leaderboards `/lb` were removed 2026-10-08.
- **Crons:** daily 06:00 UTC: revoke nicks with no `/seen` for 14 days; since 10-15 also delete player rows idle 14 days (logs nothing).
- **Secrets:** wrangler secrets (MOD_TOKEN, DISCORD_CLIENT_SECRET, ROADMAP_SECRET, Hypixel key, Twitch creds). Cloudflare never shows a value again; overwrite with `wrangler secret put`.
- **Related services:** Node `networth-service` (Fly.io) because SkyCrypt 403s Cloudflare workers; `local-dashboard.js` on :4949 is an older tool that shells out to wrangler.

## Incidents and gotchas

- **Stale deploys (10-02, 10-10, 10-11, 10-15):** four times a session deployed an out-of-date copy over live and features vanished (bug page, concerning-nick flags, streams, Eli's earlier board changes). The git `_live_snapshot` folder was itself wrong; **the only trustworthy base is code downloaded from Cloudflare**. Rule: diff against live before any deploy; Eli must OK live deploys. A deploy from the wrong checkout on 10-03 was rolled back ("undo undo undo").
- **Abuse (10-08 → 10-10):** one IP sent about 15k fake `X-FishMod-Caller` ids with the public token. Blocked and junk rows deleted. The token is public in the client, so it can return from another IP. Only trust caller ids that have `/seen`'d; consider per-IP limits.
- **Installs dashboard hung (10-10):** one Mojang lookup per uncached id exceeded Cloudflare's per-request fetch limit. Fixed with a cap of 40 lookups per load, cached failures, one 30-day query. Cap outbound fetches per request.
- **Discord login loop (09-21 → 09-23):** redirect URI `https://fishmod.dev/dashboard/callback` and the client secret were not set on this worker. Set both before announcing; run wrangler from the dir with `wrangler.toml`.
- **New public route returns 401:** any route placed after the token check is 401. Route public URLs first. Git Bash rewrites `/paths` to `C:/Program Files/Git/...`; set `MSYS_NO_PATHCONV=1`.
- **Offline UUIDs** (version 3) once created duplicate player rows; the worker now registers only version-4 ids, so cracked users are invisible by design.
- **SkyCrypt 403** broke `/networth` (09-02); solved by the Node service.
- **`wrangler d1 execute --remote` 7403** (auth) recurs; the auto-mode gate also blocks production reads and deploys, so Eli runs those by hand. `npx wrangler` hit an EBUSY npm-cache lock (10-10); use a local `node_modules` wrangler.
- **`fishmod-lb`** worker appeared unexpectedly in Cloudflare (10-07). Ask before creating Cloudflare resources.
- **Open:** a missing player (10-17) is either the new 14-day purge or 26.2 users never checking in (the only 26.2 build on Modrinth is the old `4.5-26.2`). `LATEST_VERSION` is a manual constant, stale at 1.5.42 while most run 1.6.83+. Bump it every release.
- **Streams:** no YouTube API key (uses internal search); Twitch has a 502 path. May break.
- **BetterPV "profile request failed"** (10-18) was a 12 s client timeout while the world loaded, not a server fault; the server answered in about 0.1 s.

## SkyBlock leaderboards (2026-11-02)
- `leaderboard.js`: a SkyHelper-style top 5000 per category. Hypixel has no SkyBlock leaderboard API, so the worker crawls players itself.
- A per-minute cron (`* * * * *`) crawls the `LB_PER_RUN` (25) least-recently-crawled players in `sbl_players`. It stores each player's best value per category, across all their profiles, in `sbl_stats`. It finds new players from co-op members and one guild roster per run, and from `/seen`. The first run seeds the queue from the `players` table (seeded rows have `added = 0`).
- Website only, not the mod. The public page is `fishmod.dev/leaderboard` (`sbleaderboard.html`, with a hub tile). Its data comes from the public `GET /leaderboard/data`: no params returns categories and progress; `?cat=X&page&size&uuid=Y|name=IGN` returns a top-5000 page plus the player's rank (an unknown player gets queued). IGN lookup checks stored names first, then playerdb.co, because api.mojang.com blocks Workers.
- Chosen over the separate `D:\FishMod\fishmod-worker-lb` (`lb.js`) build. Eli decided to keep this one. That build owns `lb_players` / `lb_idx_*` in the same D1, hence the `sbl_` prefix.
- Live deploys come from branch `feat/achievement-leaderboard` (36 commits ahead of `main`). It has `CosmeticHub`, and deploying `main` fails/rolls back.
- The cap is now 2500 (`LB_TOP`). The page, data and hub tile are hidden (404) until `sbl_meta.published = 1`. Eli (dashboard admin session, via the exported `readUser`) can still open the page. It shows him a readiness bar (players ranked by SkyBlock level / 2500) and a Publish/Unpublish button (`POST /leaderboard/publish?on=1|0`). `/leaderboard/status` is always public and returns `{published, admin}`.
- Crawl seed (2026-11-02): Elite's public API (`api.elitebot.dev/leaderboards`, `/leaderboard/<id>?offset&limit<=1000`, 166 current boards with uuid + ign) supplies the top 2500 of every board. Each cron run pulls 2 pages (cursor `sbl_meta.elite_cursor`, then `elite_seeded` once a full pass finishes) and upserts them with `added = 1`, which are crawled ahead of co-op discoveries. Only uuids and names are taken from Elite; the values come from our own Hypixel crawl. The readiness bar shows the share of `added = 1` players crawled. Guild-roster discovery was dropped.
- Keys (2026-11-03): the crawler uses its own secret `HYPIXEL_LB_API_KEY` (Eli's production key, 300 req / 5 min, used by nothing else) at `LB_PER_RUN` = 55/min. Without it, it falls back to the shared `HYPIXEL_API_KEY` (the mod proxy key) at only 15/min. Watch the Workers subrequest cap: each run makes about 55 Hypixel calls plus 2 Elite calls.
- The installs dashboard has a 'SkyBlock leaderboard crawl' panel (`#lbPanel`, `loadLb` in dashboard.html, refreshed every 60 s). It shows lbReadiness from `/leaderboard/data`: % of Elite players crawled, boards pulled, rate per minute, ETA, which key is in use, and a Publish/Unpublish button.
- Admin routes: `/dashboard/leaderboard` (preview of the hidden page), `/dashboard/lb/data|status|publish`. They sit under /dashboard because the session cookie is Path=/dashboard. The crawler runs on the production `HYPIXEL_API_KEY` at 55/min with a 20-call reserve (the mod's own traffic is on another key).
- Profile view: `?profile=1&name=IGN` returns every category the player has, with rank (null = outside the top 2500), best first. The page has a 'My ranks' button and `#me`. New categories (2026-11-03): secrets, class average, per-floor and master completions, `fastest_*_splus` (ms, ranked ascending), HOTM XP, lifetime powder, bestiary kills, Crimson reputation, essence.
- Storage redesign (2026-11-03, for Eli's $10–15/month budget, which means staying inside D1's included 50M rows written / 25B read a month). Each player is one row: `sbl_players.stats` holds JSON `{cat: value}`. `sbl_top(cat, uuids, vals, total, updated)` holds the precomputed top 2500: `uuids` is the 32-char uuids concatenated, `vals` a JSON array. Each cron run rebuilds one category (cursor `sbl_meta.top_cursor`, category list `cats`, refreshed daily via json_each), so a full cycle across roughly 570 categories takes about 9–10 hours. A page is a single-row read; a rank is the uuid's position (profile view: `instr(uuids, ?)`). Anyone outside the top 2500 shows as unranked. `sbl_stats` was migrated into the JSON column and dropped.
