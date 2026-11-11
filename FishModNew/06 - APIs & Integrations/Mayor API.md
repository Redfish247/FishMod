---
type: integration
status: implemented
updated: 2026-11-11
related:
  - "[[Hypixel API]]"
  - "[[Dungeon Map]]"
related_files:
  - "dungeons-26.1.2/src/main/kotlin/fishmod/utils/MayorApi.kt"
---

# Mayor API

`MayorApi.kt` (133 lines). SkyBlock election / mayor data. **Direct, unauthenticated** —
`GET https://api.hypixel.net/v2/resources/skyblock/election` (own `HttpClient`, 5 s connect / 8 s
request). Not through the proxy.

## Data

- `aatroxSlayerBonus` — Aatrox mayor (or minister with the slayer perk).
- `paulDungeonBonus` — **Paul mayor with active EZPZ perk** (checks the `perks` array, not just
  `mayor.key`; or minister Paul with EZPZ). → `+10` dungeon score. Consumed by
  `DungeonScore.calculatePaulScore()` (see [[Dungeon Map]]).

## Caching / threading

- 10‑min TTL (`CACHE_MS`).
- `backoff()` — on any failure (non-2xx, parse, transport) sets `retryAt` with exponential backoff
  (1, 2, 4 ... 30 min); `stale()` returns false until then. Success resets it. `lastFetch` is no
  longer faked on failure.

## Bug rule: non-2xx bodies and 403s (fishmod.dev #542/#543, 2026-11-11)

- **Symptom:** `[MayorApi.1] HTTP 403` then `[MayorApi.2] MalformedJsonException at line 1 column 8`.
- **Root cause:** the code only *logged* a non-200 then parsed the body anyway. A 403 from Hypixel's
  Cloudflare is plain text/HTML (`error code: 1020`), so Gson threw. The old `failStamp()` also
  hammered: right after a mayor-term switch, `now - 9 min` was still before the switch, so
  `stale()` was true on every call (scoreboard calls it per frame).
- **Fix:** check status first, never parse a non-2xx body; log a body snippet; report `MayorApi.1`
  to FishDiag once per session; back off via `retryAt` (independent of `lastFetch`).
- **Request:** honest `User-Agent: FishMod` (like `ItemsDb`, same host) instead of a bare
  `Mozilla/5.0`, which looks like a spoofed browser to Cloudflare WAF rules. The endpoint answers
  200 to both UAs from a normal network, so a 403 is per-IP/network (VPN, ASN block). If it keeps
  happening, the real fix is a `/hypixel/resources/...` route on the fishmod.dev worker.
- **Pattern for every HTTP caller:** `if (code !in 200..299) { log snippet; backoff; return }`
  before `JsonParser.parseString`. `FireSaleInfo` and `ItemsDb` already do this.
- `CompletableFuture.supplyAsync` (common pool) → `thenAccept` parse; `fetching` volatile guard;
  `exceptionally` clears it.
- Refreshed on `ClientPlayConnectionEvents.JOIN` and lazily when the TTL expires.

## Related

[[Dungeon Map]] (score) · [[Hypixel API]]
