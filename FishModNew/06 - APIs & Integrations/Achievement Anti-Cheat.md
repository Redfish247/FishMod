---
type: design
updated: 2026-11-11
related:
  - "[[fishmod-worker]]"
---

# Achievement anti-cheat (design, not built)

## Why

The achievement leaderboard submit (`AchLeaderboard.submit`) sends only three totals (points, secrets, unlocked count) derived from `config/fishmod/achievements.json`, which the player can edit. The server stores them as given. Auth is the public mod token plus a caller uuid header, so the server also cannot tell who really sent a row.

## Where things are

- Totals built in `AchLeaderboard.submit`; points are the sum over the unlocked set (`AchEngine.points`).
- Counters only ever go up locally (`AchStore.max/inc`); `AchApiSync` raises them from the Hypixel profile, which is the one trustworthy source.
- The live `/api/achievements/*` handler is NOT in the repo copy of the worker (`D:\FishMod-WorkerBoard\worker.js`), so its exact behaviour with unknown JSON fields is unverified.

## Approach

Plausibility checks on the server, flag instead of ban, minimum data. The full list, rollout order and open decisions are in `anticheat-worker-proposal.md` in the repo root (kept out of the vault because it targets the worker).

## Decision: no client change yet

Sync metadata (timestamps, counter deltas) would be extra JSON fields. Because the live worker code could not be read, we cannot confirm it ignores unknown fields, so the client stays unchanged. Server-side checks on the existing three totals work with the current jar.

## Progress is bound to the UUID (2026-10-27)

Copied config folders (Eli's modpack) made other players submit Eli's totals. Progress now lives in `config/fishmod/achievements/<uuid>.json` with an `owner` field, and `AchLeaderboard.submit` / `AchApiSync` do nothing unless `AchStore.ownedBy(uuid)`. A legacy `achievements.json` is claimed once by the first player (then renamed `.legacy`), except Eli's known shipped saves (`KNOWN_LEGACY`, keyed by `since`). Other people's copied legacy files can't be told apart and are still claimed by whoever loads them first. Details: Lessons Learned, 2026-10-27.

## Gotchas

- Public token plus open client means local editing can never be fully stopped; aim for obvious and cheap to hide.
- Download live worker before editing it (stale-deploy incidents).

## Submit clamps must follow the registry (2026-11-11, fishmod.dev #533)

- Symptom: the secrets leaderboard never showed more than 13 secrets.
- Cause: `handleAchSubmit` in the worker clamps totals to hardcoded constants (`ACH_MAX_SECRETS = 13`, `ACH_MAX_POINTS = 8281`) written when the registry was smaller. The registry now has 28 secret achievements and 8706 AP, so every higher submit was silently cut down. The client sent the right value.
- Fix: `AchLeaderboard.submit` also sends `maxPoints` and `maxSecrets` (computed from `AchRegistry.ALL`); the worker should clamp to those, bounded by a generous hard ceiling, instead of fixed constants. Until the worker is deployed the extra fields are ignored (the handler reads named fields only).
- Rule: never hardcode a registry-derived maximum on the server. Adding an achievement must not need a worker deploy.
