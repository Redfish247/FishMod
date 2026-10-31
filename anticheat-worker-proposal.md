# Achievement anti-cheat: worker proposal (not deployed)

Status: proposal only. Nothing here is deployed or changed in any worker repo.

## What the server sees today

`POST /api/achievements/submit` (header `X-FishMod-Token`, public, plus `X-FishMod-Caller` uuid) with body `{uuid, name, points, secrets, unlocked}`. These are three totals computed by the client from `achievements.json` (counters editable by hand). The server cannot tell a real 400 AP from a typed one. Client throttles to one submit per 3 minutes and only when totals change; the server answers 429 if it already holds a recent copy.

Caveat: the route is NOT in the repo copy `D:\FishMod-WorkerBoard\worker.js` (no submit/leaderboard handler, no table in `schema.sql`), so the live code is from some other deploy. Per the stale-deploy incidents, download the live worker before changing anything.

## Honest limits

The token is public and the client is open source, so no scheme is tamper-proof. Goal: make casual editing (set points to 9999) obvious and cheap to ignore, without banning anyone or collecting more than needed.

## Checks (all server-side, cheap, D1 only)

1. **Caller match:** body `uuid` must equal `X-FishMod-Caller` and have `/seen`'d (version-4 uuid). Rejects drive-by fake ids.
2. **Hard caps:** `points <= MAX_POINTS`, `secrets <= SECRET_TOTAL`, `unlocked <= TOTAL_ACH`, and `secrets <= unlocked`. Constants shipped with each mod release; keep a worker-side table keyed by mod version, or just generous constants.
3. **Monotonic-ish:** unlocks only grow. A drop is allowed (reset/new install) but resets the player's trust window.
4. **Growth rate:** compare with the previous stored row. Flag if `points` rose more than X (suggest 300 AP) within one hour, or more than Y per day. Real play unlocks in bursts but is bounded by content.
5. **Unlock-set vs total (needs client change):** client sends compact unlocked ids; server recomputes points from the registry. Mismatch means a hand-edited total. Costly in size (~300 ids); alternative is a hash of the sorted id list plus count, checked only when flagged.
6. **Counter deltas (needs client change):** client sends `since` (ms of last submit) and a small `delta` map for a few high-value counters (secrets, runs, slayer kills). Server checks delta against max physically possible per elapsed time (e.g. secrets per minute).
7. **API-backed cross-check:** many counters come from the Hypixel profile. Server already proxies and caches it; for a flagged player, fetch the profile and compare cata level, secrets, kills. Only on flags, so API volume stays low.
8. **Optional signature:** per-install random key generated client-side, registered on first submit (trust on first use); later submits carry HMAC over body+timestamp. Stops replay and other users submitting for you, not local editing. Low value, skip unless impersonation shows up.

## Response: flag, don't ban

Add `flagged` (int reasons bitmask) and `flagged_at` to the row. Flagged rows stay stored but are excluded from `/leaderboard` ranks; the player still sees their own number. Eli reviews flags on the dashboard and can clear or hide. No client-visible error beyond the normal 200, so cheaters get no feedback to iterate on.

## Minimal data

Nothing new beyond uuid, name, totals. Client additions are optional numbers (`since`, small `delta` map, id-list hash). No chat, coordinates or inventory.

## Rollout order

1. Server: add checks 1-4 (no client change, works with the current jar), flag-only mode logging for a week.
2. Server: ignore-unknown-fields confirmation, then client adds `since` + deltas + id hash (a follow-up client patch).
3. Turn flags into leaderboard exclusion after reviewing false positives.

## Decisions for Eli

- Is the leaderboard public enough to justify this now? (Card says optional.)
- Flag-only for a week first, or exclude immediately?
- AP growth thresholds (suggest 300/hour, 1500/day).
- OK to download the live worker to a scratch folder and diff before any edit?
- Per-install HMAC: yes or skip?
