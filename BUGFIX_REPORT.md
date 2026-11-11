# Bugfix report 2026-11-11

Commit before start: bf374f5d (Ship 1.7.71). Working tree was clean, nothing to pre-commit.
Fix commits: e44b87a4 (#534, #532), dfe56748 (#537).
Built `:dungeons-26.1.2:build :dungeons-26.2:build`: OK after the fixes and again after the 1.7.72 bump. 26.3 not built (opt-in, files touched have no 26.3 override). Not tested in game.

The prompt arrived cut off at the API token again; the full text was taken from `tools/daily-bugfix/prompt.md`, the token from the `ROADMAP_SECRET` user env var.

## Fixed
- **fishmod.dev #534** `garden/SpeedSignHelper.kt` + `mixin/accessors/AbstractSignEditScreenAccessor.java`: the helper drew over every sign edit screen in the Garden. It now reads the sign's lines (new `messages` accessor, same field name in 26.1.2 and 26.2) and only draws when they contain "crop speed cap" (case-insensitive, lines joined, so it works however Hypixel wraps "Set your crop speed cap!").
- **fishmod.dev #532** `garden/SpeedSignHelper.kt`: helper text moved from top-centre (over the sign) to the bottom-left corner (x 4, bottom margin 4, 10 px lines).
- **fishmod.dev #537** `features/ExperimentProfitTracker.kt`: two causes. (1) The rarity colour was taken from the first text after "+", which for pets is the grey "[Lvl 1]" tag, so pets had no rarity. `[Lvl N]` is now skipped. (2) Pet prices used `LVL_1_<TIER>_<PET>` keys (SkyHelper format), but `CroesusPrices` loads the NEU/Coflnet list that keys pets as `GUARDIAN;3`, so the lookup always missed. Now tries `<PET>;<tierIndex>` first, then the old key. Pets are stored per rarity as "Epic Guardian" etc. so different rarities don't merge. Old saved rows ("Guardian") still price through the colour fallback.

## Already fixed, moved to Testing
- **#538** `[PartyFinderStats.3] PF stats API lookup failed for Devlion`: reported from mod 1.7.18. Current `PartyFinderStats.kt` no longer auto-reports transient lookup failures (logs at info and tells the player). Same as #494.

## Reported bugs not fixed
- **#533 Secret leaderboard capped at 13** (needs decision / worker change): `D:\FishMod-WorkerBoard\worker.js` has `ACH_MAX_SECRETS = 13` and `ACH_MAX_POINTS = 8281`; submissions are clamped to those. The mod registry now has 28 secret achievements and about 8706 points (regex sum over `AchRegistry.kt`, 319 achievements). Fix is to raise both constants (or have the worker derive them), but that needs a `wrangler deploy`, which needs Eli's OK and a live-vs-repo diff first (the WorkerBoard copy is known stale). Not touched. Card left Planned.
- **#459 Players get stuck and glitch**: unchanged from the last two runs, needs a video or `/fm debug` report. Card left In progress.

## Board
Moved to Testing: #532, #534, #537, #538. Nothing set to Done. Tester board not touched.
Note: `POST /roadmap-status` silently returns `updated: 0` when ids are sent as strings (`"532"`); they must be numbers.

## Deploy
`FishModDungeons-1.7.72-26.2.jar` copied into Prism `Skyblock` mods after `check-game-closed.ps1` said closed; 1.7.71 removed. The Modrinth path in the prompt (`ModrinthApp\profiles\Skyblock 1.21.8\mods`) is a 1.21.8 profile with no FishModDungeons jar, so nothing was copied there (same as the last run). FishTest not updated. `prompt.md` should point at the Prism instance.

## Skipped
- Wider source sweep: checked the 33 files changed since the last run (HUD editor refactors, look correct) and re-checked every `player!!`/`level!!`/`connection!!` site; all are behind a null guard or a guarded caller. No new problems found. Did not read every file.

## Test in game
1. Garden: open the crop speed sign (Set your crop speed cap!) holding a crop. The helper should show bottom-left. Open any other sign in the Garden (e.g. a bazaar/AH search sign): no helper.
2. Experimentation Table: claim a Superpairs reward that gives a pet (Guardian). The Experiment Profit HUD should show "Epic Guardian" (or the right rarity) in the rarity colour with a coin value instead of "?".
3. If the pet row still shows grey, the reward line's colour layout differs from the guess; send a chat screenshot.

## Notes for Obsidian
- Speed Sign Helper detection keys on the sign text containing "crop speed cap" (Eli's report #534). Uses `AbstractSignEditScreenAccessor.fishmod$getMessages()`.
- Experiment Profit pet pricing: `CroesusPrices` lbin is the Coflnet NEU list, so pets are `NAME;tier` (COMMON 0 .. MYTHIC 5). `LVL_1_<TIER>_<NAME>` only exists in the PV `ItemPricer` (SkyHelper/Athen). Rows are keyed "Epic Guardian".
- Achievement leaderboard caps live in the worker (`ACH_MAX_SECRETS`, `ACH_MAX_POINTS`); they must be raised whenever achievements are added, or scores are clamped silently.
- Roadmap API: `roadmap-status` needs numeric ids.
