# FishModDungeons — Performance & Slimming Audit 2026-10-27 (Inspection Only, No Edits)

HEAD audited: `e7f45622` (Ship 1.6.381). Focus requested by Eli: sloppy code, all unnecessary comments, duplicate snippets, slimming.

## 0. Scope
- `dungeons-26.1.2/src/main`, `dungeons-26.2/src/main` overrides, `fishmod/compat/*`. `dungeons-1.21.11` untouched.
- Six read-only area passes (render/HUD, ticks, mixins/events/packets, tab/scoreboard/chat, network/API, memory). Raw notes: `audit-work/*.md` (uncommitted).
- **Coverage caveat:** every pass was partial (grep + targeted reads). Comment counts are file totals, not per-line ranges. No repo-wide unused-symbol sweep was done. Nothing was measured.

## 1. Problems Found

| # | Location | Cause | Impact | Sev | Effort | Lines removable |
|---|---|---|---|---|---|---|
| 1 | Comments repo-wide (pv/* dominates: `InventoryPage.java` 43, `HypixelApiClient` 49, `Leveling.java` 53, `PlayerStatsCalculator` 47, `PvDraw` 36…) | ~4300 comment lines (1179 `//`, ~1082 block openers); 125 are history/provenance ("was", "Odin", "NoammAddons") | Breaks the one-short-line rule; noise | L perf / H slim | M (mechanical) | 2500-3500 (needs a per-file pass to keep "why" lines) |
| 2 | Colour-strip duplication: `HypixelApi.STRIP_COLOR`, `Constants.STRIP_COLOR_REGEX`, `MapColors.MAP_COLOR_CODES`, 38 Kotlin `private val COLOR` aliases, 3 identical `stripColors` (DungeonPlayers:85, DungeonScore:110, DungeonState:78), ~11 Java strip helpers, ~14 uncached `replaceAll("§.")` (NetworthCalculator:196, ItemWorth:252 run per item) | Same helper reimplemented everywhere | Regex compile per call in networth/tooltip loops | M | S-M | ~90-110 |
| 3 | `HandledScreenMixin.java:30-110` (+ `ClientPlayNetworkHandlerMixin`, ~76 `FishDiag.fail` in mixins, ~100 Kotlin tick try/catch wrappers) | 8x copy-paste try/catch + `fishmod$logged*` once-flags; two idioms vs existing `FishDiag.guard` | Prior §1.9 still open; pure boilerplate | M | S | ~150-250 |
| 4 | Sidebar read in 11 places: `CompactTab:456`, `CrownOfAvarice:217`, `DragonCheck:120`, `DungeonScore:162`, `DungeonState:170`, `Mining:125`, `RiftParkour:87`, `CustomScoreboard:56/111/123`, `SlayerManager:398`, `FishModInit:1016` | No shared `SidebarCache` (TabListCache equivalent) | ~10 independent scoreboard scans + team-format allocs per tick | M | M | ~110 |
| 5 | HTTP: 8 extra `HttpClient`s (Coflnet:39, EliteBot:37, Athen:30, SkyHelper:35 identical), 70 `HttpRequest.newBuilder` sites, 4 `getJson` overloads in HypixelApiClient, ~15 inline proxy sends in `HypixelApi.java` repeating token headers | No shared `Http.getJson` / `proxyGet` | Extra selector threads, big duplicate surface | M | M | ~250-400 |
| 6 | `/v2/resources/skyblock/items` fetched by both `pv/price/HypixelItemsCache.java:30` and `utils/networth/ItemsDb.kt:19` | SPEC-4 still open | 2 downloads + 2 parsed copies of a large JSON | M | M | ~60-100 |
| 7 | `dungeon/f7/M7Relics.kt:84-140` | 3 full `entitiesForRendering()` loops per tick + hoverName string alloc; checkPlaced/checkAllPlaced duplicate test | Per-tick churn P5→end | M | S | ~25 |
| 8 | `dungeon/f7/BossDamage.kt:48-65` | 96-block AABB query + per-boss stand query every tick | 20 Hz scans whole M7 boss | M | S | 0 |
| 9 | ~13 executor pools in `pv/api/*`, `pv/neu/*`, `pv/price/*`, `HypixelApi:152,159` | Thread-factory lambda copy-pasted 12x; ~20 idle threads | Threads + duplication | M | S | ~60 |
| 10 | `garden/FarmingProfitTracker.kt` vs `mining/MiningProfitTracker.kt` | Near-duplicate files; `lines()` rebuilt every frame with `String.format` | Per-frame alloc + duplication | M | M | ~60 |
| 11 | Chat: ~40 listeners each strip the same message (`PartyCommandHandler`, `ChatHudMixin`, ChatFilter, CompactChat, CooldownOverlay…) | No strip-once chat bus | 10-20 strips per chat line | M | M | ~50 |
| 12 | Remaining raw tab loops: `DungeonPlayers:48`, `DungeonState:133`, `LeapMenu:39`, `CooldownOverlay:206`, `FishModInit:952,1002,1156`, `ScoreboardUtil:22` (fold + delete file), `AchHooks:80`, `RemoteSync:60,89` | TabListCache migration unfinished | Re-scans tab | M | S-M | ~60 (+53 file) |
| 13 | IO: 3 private `ioExecutor` copies (PbMessages, RoomTimer, SessionStats) duplicate `IoExecutor`; ~9 raw `Files.writeString` stores | Inconsistent; torn-file risk | L | S | ~25 |
| 14 | Shared "entities near" idiom 5x (RiftVampire, Rift:49,56, SlayerBossDetector, SlayerCarryTracker, StarredMobHighlight:78) | No util | Duplication | L | S | ~25 |
| 15 | 26.2 mixin full copies (8 files, ~600 lines; Crosshair/ExperienceBar/ScreenEffect differ only slightly) | Copy instead of compat helper | Double maintenance | L | M | ~100 |
| 16 | Regex in lambdas: `CroesusProfit.kt:97,100`, `AchUsage.kt:23`, `ChatClickProcessor:222,268,293`, `UpdateScreen.kt:251-259` | Per-call compile | Small | L | S | ~3 |
| 17 | Per-frame `String.format`: TerracottaTimer:53, BloodSolver:195, WitherDragons:147,243, GateDisplay:69, TacTimer:82, KuudraExtras:153 | Format every frame | Small alloc churn | L | S | 0 |
| 18 | `SkyBlockItemIconCache.java:104-135` | DynamicTextures never released | Grows with models viewed | L-M | M | 0 |
| 19 | 83 one-line `ON_WORLD_CHANGE` reset registrations | Boilerplate | Cosmetic | L | M | ~80-120 |
| 20 | Sloppy bits: FQNs instead of imports (M7Relics:83, Rift:88, SlayerCarryTracker:85, HypixelApi FishDiag calls), `!!` x3 in SimonSaysTracker, empty `"mixins": []` in fishmod.mixins.json:5, duplicated uuid lookup ItemTrimMixin:25-45, `AnimatedDyeAnimator.clear()` dead, `CatacombsOverflowOverlay.lastDumpAt` never cleared, bundled ON_PACKET ungated (`ClientPlayNetworkHandlerMixin:145`) | — | L | S | ~20 |

## 2. Prior-audit cross-reference (2026-09-04)

| Item | Status |
|---|---|
| 1.1 SimonSays O(n³) scan | FIXED |
| 1.2 StarredMobHighlight per-frame scan | FIXED (tick cache) |
| 1.3 Tab list scanned 4-5x | MOSTLY FIXED (TabListCache); raw loops remain (#12) |
| 1.4 CompactTab rebuild per frame | FIXED (cached model); residual strip in comparator, L |
| 1.5 CustomScoreboard.buildSig | FIXED (250 ms gate) |
| 1.6 getLocalMember no cache | FIXED (60 s TTL + in-flight dedupe) |
| 1.7 Stacked DataComponentHolder.get mixins | FIXED (single mixin, early exit) |
| 1.8 ON_PACKET double fire | NOT a double fire; bundled path ungated (#20) |
| 1.9 HandledScreenMixin fan-out | STILL OPEN (#3) |
| 1.10 Uncached regex / GoldorLeapTimer | MOSTLY FIXED; leftovers (#2, #16) |
| 1.11 Duplicates (petRarity, PuzzleDisplay, colour strip) | petRarity + PuzzleDisplay FIXED; colour strip STILL OPEN (#2) |
| 1.12 MapImageLoader thread / dye states | FIXED / mitigated |
| 1.13 openItemWiki, checkKey | REMOVED |
| §7 SPEC-4 items double fetch | STILL OPEN (#6) |
| §7 moulberry hosts | FIXED |

## 3. Recommended fixes, ranked (impact ÷ effort)
1. #3 Mixin/tick guard helper: one `safe(id, Runnable)`, ~150-250 lines, S.
2. #2 Single `TextStrip.plain()`: kills 38 aliases + ~25 helpers, fixes networth regex compile, S-M.
3. #1 Comment strip, pv/* first: biggest line win, mechanical; keep non-obvious "why" one-liners.
4. #7 + #8 M7Relics single pass + BossDamage throttle: real per-tick wins, S.
5. #5 Shared `Http` client + `getJson`/`proxyGet`: ~250-400 lines, M.
6. #4 `SidebarCache`: 11 scans → 1, M.
7. #6 Merge ItemsDb onto HypixelItemsCache: 1 download, less memory, M.
8. #9 Shared executors: ~60 lines, fewer threads, S.
9. #12 Finish TabListCache migration, delete ScoreboardUtil, S-M.
10. #10 Profit-tracker shared base + cached lines, M.
Then #11, #13, #14, #20 (quick wins), #15-#19.

## 4. Honest assessment
- Inspected, not measured. No profiler, no in-game test.
- The 09-04 per-frame hot spots are gone; nothing H-severity for FPS remains. The real gains now are **slimming**: ~3000+ comment lines and ~1000-1500 lines of duplicate code/boilerplate.
- Coverage gaps: no unused-symbol sweep (use `where.py -k uses` per symbol in the fix pass), ~45 mixins skimmed only, `Events` bus class unread, pv/gui per-area `*Ui` helpers likely duplicated but unverified, 26.2 copies not diffed semantically.
- Every fix must build on both `:dungeons-26.1.2` and `:dungeons-26.2`.
