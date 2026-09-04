# FishMod (dungeons-26.1.2) — Full Performance Audit (Inspection Only, No Edits)

**Date:** 2026-09-04
**Branch:** `claude/section-6-fixes`
**Mode:** Read-only reconnaissance per explicit instruction — **no code was changed**. This is Phase 1-17 of the requested audit (recon + hotspot ID); Phases 18-20 (apply/build/test) were NOT run.

---

## 0. Scope Caveat (read first)

The audit ran as 4 parallel research passes (which further split into 7 sub-passes). They did not all see the same tree:

- The assigned project root (`dungeons-26.1.2/src/main/java/fishmod`) is a **stale/partial checkout** — only 65 Java files, and `features/dungeon/` contains just `PartyCommandHandler.java`.
- The **real feature set** (waypoints, puzzle solvers, map HUD, F7 timers, tab-list HUDs, etc. — ~197 files) lives in Kotlin under `src/main/kotlin/fishmod/...`, and was only reachable via a git worktree at `D:\FishModDungeons\.claude\worktrees\agent-af9e9094e22bba9b5\dungeons-26.1.2\src\main\java\fishmod` (same branch, same last commit `63ad5df`).
- `git status` shows dozens of files in `dungeons-26.1.2` as modified-but-uncommitted in the main checkout (from the prior 2026-09-03 AUDIT.md fix pass, per memory). Some mixins referenced by name below (`CustomScoreboard`, `ChatFilter`, `RemoteNicks`, etc.) exist only in the Kotlin tree/worktree and could not be opened by the mixin-focused passes — flagged inline as "not verifiable in this checkout" where relevant.
- **Action item before any fix pass:** reconcile the worktree vs. main checkout (likely just needs the worktree merged/rebased into the main tree) so a future optimization pass works from one consistent source of truth.

---

## 1. Performance Problems Found

### 1.1 Sustained per-tick O(n³) block scan
**Location:** `features/dungeon/SimonSaysTracker.kt` (`scanLitCells()`, worktree, called from `tick()` every client tick while a player is in the Goldor Simon Says device box)
**Cause:** 15×15×15 (3,375) `getBlockState` calls **every tick** for the duration of the minigame, plus a fresh `HashSet<Long>` allocated every tick to hold results.
**Impact:** The only truly *sustained* O(n³) hot loop found (vs. the other scans below, which are periodic/one-shot). Runs for the whole Simon Says round, not just once.

### 1.2 Per-frame O(n·m) entity scan with no caching
**Location:** `features/dungeon/StarredMobHighlight.kt` (`findStarredMobs`/`findNearestMob`, on `RenderingEvents.OUTLINE_ENTITY` — every rendered frame)
**Cause:** Outer loop over all rendering entities; for each matching armor-stand nametag, a second `level.getEntities(stand, searchBox)` AABB query. No frame-to-frame cache even though "which mobs are starred" changes rarely.
**Impact:** O(n·m) per frame, uncached — worse under frame rate than the tick-bound scans above.

### 1.3 Tab-list is independently scanned/parsed by 4-5 separate features
**Location:** `DungeonScore` (10-tick interval), `PuzzleDisplay`/`FishPuzzleDisplay` (20/25-tick, near-duplicate classes), `SoulflowHud` (10-tick), `PetHud` (5-tick + burst window), `CompactTab` (every frame while Tab held)
**Cause:** No shared "tab-list snapshot" cache — each class calls `getOnlinePlayers()` and strips color codes from every entry independently, on its own cadence.
**Impact:** The same tab-list packet gets walked and regex-parsed 4-5 times per update cycle instead of once.

### 1.4 `CompactTab.render()` rebuilds everything from scratch every frame
**Location:** `features/CompactTab.kt` (worktree), invoked every frame via `PlayerListHudMixin.fishmod$compactTab`
**Cause:** Fresh `ArrayList` + full sort (`String.compareToIgnoreCase`) + regex-based re-grouping + per-entry `tr.width(...)` font measurement + two uncompiled `.replaceAll` regex calls per entry (`blank()`) — all re-run at full framerate even though Hypixel's tab data only changes on server packets.
**Impact:** Single biggest per-frame hotspot found in the whole audit.

### 1.5 `CustomScoreboard.buildSig()` runs full stream+sort every frame just to fingerprint change
**Location:** `features/scoreboard/CustomScoreboard.kt` (worktree; delegate of `GuiScoreboardMixin`)
**Cause:** `sb.listPlayerScores(obj).filter{}.sortedWith{}.take(20)` plus per-entry `StringBuilder` concatenation, run **every frame unconditionally**, purely to decide whether the (correctly TTL/signature-cached) `buildLines()` rebuild is needed.
**Impact:** Defeats much of the point of the surrounding cache design — the "cheap fingerprint" is itself not cheap.

### 1.6 `getLocalMember` (Hypixel profile fetch) has zero caching, polled independently 4x
**Location:** `utils/HypixelApi.java:2336-2356` (no TTL/cache); polled by `CatacombsOverflowOverlay.kt`, `BestiaryProgress.kt`, `CollectionsProgress.kt`, `SkillLevels.kt`, each on its own ~60s `END_CLIENT_TICK` timer
**Cause:** Confirmed still-open from the 2026-09-03 AUDIT.md (tracked there as SPEC-5) — no shared fetch/fan-out implemented since.
**Impact:** Up to 4 full-profile HTTP fetches + JSON parses per ~60s instead of 1.

### 1.7 Two `DataComponentHolder.get()` mixins stacked on the hottest item-data read path
**Location:** `mixin/ItemTrimMixin.java`, `mixin/ItemModelOverrideMixin.java` — both `@ModifyReturnValue` on the same generic `get()` method
**Cause:** Every single `DataComponents.get(...)` call anywhere in the client (any component, any item) now pays two extra identity comparisons + two `instanceof` checks, even for components neither mixin cares about.
**Impact:** Small per-call cost, but amplified by call volume — `get()` fires constantly for render/tooltip/comparison/network paths.

### 1.8 Unconditional per-packet event dispatch, on two threads, possibly double-firing
**Location:** `mixin/ClientConnectionMixin.java:33` (`Events.ON_PACKET.invoke`, netty thread, every packet) and `mixin/ClientPlayNetworkHandlerMixin.java:120` (`Events.ON_PACKET.invoke` again, main thread, every packet inside a bundle)
**Cause:** No guard before either dispatch; a code comment nearby shows awareness of one dedup (`ON_GAME_MESSAGE`) but not this one.
**Impact:** Every packet may pay for `ON_PACKET` listener dispatch twice; needs verification against listener count/cost.

### 1.9 `HandledScreenMixin.extractRenderState` fans out to 8+ feature calls every frame per open container
**Location:** `mixin/HandledScreenMixin.java:29-44`
**Cause:** No shared top-level guard; `ScrollableTooltip`, `SearchBar`, `LeapMenu`, `PartyFinderPanel`, `StorageOverlay`, `CroesusProfit`, `ContainerValue`, `TermCustomGui` are all invoked unconditionally every frame a container screen is open, relying on each callee to self-guard.
**Impact:** 8 method dispatches/frame minimum per open chest, plus 2 more calls per rendered slot (`extractSlot`, ~108/frame for a large chest).

### 1.10 Regex recompiled per call (uncached `String.replaceAll`/`.matches`) in several hot-ish paths
**Location:** `ChatHudMixin.java:61`, `ClientPlayNetworkHandlerMixin.java:66` (per scoreboard-team packet, can burst on dungeon start), `FishCopyChatMixin.java:58/60/74/75`, `GoldorLeapTimer.kt:68` (**every server tick** during Goldor tunnel phase), `CompactTab.blank()` (per tab entry per frame), `PuzzleDisplay.kt:49` (its near-duplicate `FishPuzzleDisplay` does this correctly as a static `Pattern` — inconsistent).
**Cause:** `String.replaceAll`/`.matches` compile a throwaway `Pattern` internally on every call instead of reusing a `static final Pattern`.
**Impact:** Mostly low-frequency (chat/team packets), but `GoldorLeapTimer` and `CompactTab` cases are genuinely hot (per-tick / per-frame).

### 1.11 Duplicate detection logic across features
- **Floor/dungeon-state detection** independently re-derived from raw chat/sidebar text by both `DungeonScore` (own `FLOOR_PAT`) and `FishEstTotal` (own `FLOOR_PATTERN`) instead of reading one shared field.
- **Scoreboard sidebar text extraction** (strip team prefix/suffix + color codes) duplicated verbatim in `DungeonScore.scanSidebar()` and `CompactTab.findServer()`.
- **Color-code stripping** (`"§."` regex) implemented independently in ≥5 places: `HypixelApi.java:292` (the only cached one), `ChatHudMixin.java:61`, `ClientPlayNetworkHandlerMixin.java:66`, `FishCopyChatMixin.java:58`, plus a likely Kotlin-side `VanillaColors.kt` equivalent (not opened).
- **`petRarity`/`petRarityOf`** in `HypixelApi.java` (lines 1835-1838 and 2517-2520) are byte-for-byte identical methods, both live, never consolidated.
- **Two near-duplicate puzzle-status HUD classes** (`PuzzleDisplay` and `FishPuzzleDisplay`) scan the tab list independently on different tick intervals for what looks like the same data.

### 1.12 Potential memory growth without eviction
- **`AnimatedDyeAnimator.states`** (`features/item/AnimatedDyeAnimator.kt:13`, `HashMap<String,State>` keyed by item UUID) — populated via `getOrPut`, only cleared via an explicit `clear()` call whose caller was not confirmed as wired to any world-change/inventory event. No TTL, no bound.
- **`MapImageLoader`** (`.kt:44-63`) starts a daemon directory-watch thread in `init()`; its own `close()` method exists but has no caller (confirmed via grep) — thread runs for the entire client lifetime with no way to stop it short of process exit. (Matches LOW-31 from the 2026-09-03 AUDIT.md, still unaddressed.)
- **`CroesusPrices` per-item coflnet cache** (`ConcurrentHashMap<String,Double>`) is unbounded but naturally capped by total SkyBlock item count — low severity.
- **`ChatHistoryMixin`**: `FishSettings.infiniteChatHistoryLimit` directly sets vanilla chat-history caps with no further bound — by design, but worth flagging since it's user-configurable to arbitrarily large values.

### 1.13 Dead/vestigial code (verified via repo-wide grep, not just "looks unused")
- **`Keybinds.java:44` `openItemWiki`** — registered and tracked for backup, but `consumeClick()` is never called anywhere. The keybind shows in the controls menu and does nothing when pressed.
- **`HypixelApi.checkKey()`** always returns `true` with a comment "API key no longer needed — requests go through proxy," still has 3 call sites — not dead, but vestigial; flagged for awareness only.

---

## 2. Optimizations Applied

**None.** Per explicit instruction, this pass was inspection-only. Below is a *recommended* fix list (not yet implemented), in priority order:

| # | Target | Recommended change | Expected benefit | Risk |
|---|--------|---------------------|-------------------|------|
| 1 | `CompactTab.render()` (§1.4) | Cache sorted/grouped/measured column data keyed on a tab-list version counter; only rebuild on actual tab-list change | Eliminates a full sort+regex+font-measure pass every frame while Tab is held → likely the single biggest FPS win available | Low — feature is purely visual, cache invalidation is simple (tab-list update events already exist) |
| 2 | `CustomScoreboard.buildSig()` (§1.5) | Replace the stream+sort fingerprint with a cheaper signature (e.g. hash of raw score entries without full sort/filter chain) | Removes a guaranteed-every-frame allocation/sort chain | Low — purely an internal fingerprint, doesn't change output |
| 3 | `SimonSaysTracker.scanLitCells()` (§1.1) | Throttle to every 2-4 ticks instead of every tick, or switch to incremental diffing of the 15³ box | Cuts sustained per-tick block-scan cost by 50-75% | Medium — must confirm minigame timing tolerance isn't sensitive to a few-tick delay |
| 4 | `StarredMobHighlight` (§1.2) | Cache "which armor stands are starred" per-tick (event-driven or on a short tick interval) instead of rescanning + re-querying every render frame | Removes O(n·m) work from the render hot path entirely | Low-medium — needs a tick hook to refresh the cache |
| 5 | Shared tab-list snapshot (§1.3) | One `TabListCache` that scans/strips-color once per interval; `DungeonScore`/`PuzzleDisplay`/`FishPuzzleDisplay`/`SoulflowHud`/`PetHud`/`CompactTab` consume it | Collapses 4-5 independent scans into 1 | Medium — requires touching several features' data-access pattern; also a chance to delete the `PuzzleDisplay`/`FishPuzzleDisplay` duplication (§1.11) |
| 6 | `getLocalMember` (§1.6) | Add the TTL cache from SPEC-5 (2026-09-03 audit) with a shared 60s fan-out across the 4 pollers | Cuts profile fetches from up to 4x/60s to 1x/60s | Low — TTL caching pattern already proven elsewhere in `HypixelApi.java` (nwPrices) |
| 7 | Color-strip duplication (§1.11) | Replace the 4 independent `.replaceAll("§.", "")` call sites with `HypixelApi.STRIP_COLOR` (or a shared `TextUtil` if that's more appropriate architecturally) | Removes repeated regex-compile cost, one source of truth | Low |
| 8 | `GoldorLeapTimer` regex (§1.10) | Hoist `"§."` to a `static final Pattern` | Removes regex recompile from a per-server-tick hot path during Goldor phase | Trivial, no risk |
| 9 | `ItemTrimMixin`/`ItemModelOverrideMixin` stacking (§1.7) | Consider merging both `@ModifyReturnValue` bodies into one mixin method with an if/else on `type`, if Mixin ordering allows | Halves the fixed per-`get()`-call overhead | Medium — needs care with Mixin injection ordering/priority |
| 10 | `Events.ON_PACKET` double-dispatch (§1.8) | Verify with the `Events` bus owner whether both firings are intentional; if not, remove one | Could halve packet-handling event overhead if truly duplicated | Needs investigation before any change — don't remove blind |
| 11 | `petRarity`/`petRarityOf` (§1.11) | Delete one, redirect callers | Code cleanliness only, negligible perf | Trivial |
| 12 | `openItemWiki` dead keybind (§1.13) | Either wire it up or remove it | Cleanliness / avoids a confusing no-op control | Trivial |
| 13 | `MapImageLoader` leaked thread (§1.12) | Wire `close()` to a client-stop/world-unload hook | Fixes a real (if minor) resource leak | Low |

---

## 3. Architecture Improvements (recommended, not built)

- **One shared tab-list/scoreboard snapshot service** consumed by all HUD/score features instead of 4-5 independent parsers (see §1.3, §1.11).
- **One shared "current floor / dungeon state" accessor** instead of `DungeonScore` and `FishEstTotal` each re-deriving it from raw text.
- **Consolidate `PuzzleDisplay` and `FishPuzzleDisplay`** — audit didn't find a clear reason both need to independently exist; worth a decision from Eli on which is canonical (possibly blade-addons-compat related, per a comment on `FishEstTotal`).

## 4. Memory Improvements (recommended)

See §1.12 — `AnimatedDyeAnimator.states` needs a confirmed eviction path, `MapImageLoader`'s watch thread needs a shutdown hook.

## 5. Rendering Improvements (recommended)

See §1.4, §1.5, plus smaller instances of the same "rebuild every frame" pattern in `DungeonMapHud`, `DungeonWaypoints`, `BossBarFeature`, `FishEstTotal` — all rebuild `Component.literal(...)` and call `font.width(...)` every frame even though underlying data updates only every N ticks. Lower priority than #1/#2 above since bar/waypoint counts are small (1-4 items), but same fix pattern (cache text + width, invalidate on data change) applies to all.

## 6. Tick Improvements (recommended)

See §1.1, §1.2. No other sustained per-tick O(n²+) loops found outside these two. `Keybinds.checkInputs()` (every tick, ~17-entry map diff) is negligible and not worth touching.

## 7. Network/API Improvements (recommended)

- §1.6 (`getLocalMember`) is the clearest win.
- **MED-23 from prior audit still open**: dead `moulberry.codes` LBIN hosts in `CroesusPrices.kt` — no replacement bulk-LBIN source has been chosen. This needs Eli's decision (Coflnet bulk endpoint vs. full Hypixel auction scan vs. drop bulk-LBIN entirely), not something to guess at.
- **SPEC-4 from prior audit still open**: `SkyblockItems.kt` and `ItemsDb.kt` both independently fetch `/v2/resources/skyblock/items` — not re-verified line-by-line this pass, no evidence of consolidation.
- Executors are already well-consolidated (`HypixelApi.API_EXECUTOR`, single fixed pool of 4, verified as the sole executor for all `runAsync` calls in that file) — no action needed there.
- `HypixelApi.nwPrices()` TTL caching is correctly implemented — used as the reference pattern for fixing `getLocalMember`.

## 8. Dead Code Removed

**None removed** (inspection-only). Candidates: `Keybinds.openItemWiki` (§1.13), duplicate `petRarity`/`petRarityOf` (§1.11).

## 9. Remaining Bottlenecks / Honest Assessment

- This audit did **not** cover the full ~197-file Kotlin feature tree exhaustively — the dungeon-features pass covered the main HUD/waypoint/puzzle/tab classes but a full mod this size likely has more of the same "rebuild every frame" pattern in less-trafficked HUDs (e.g. `PetHud`, `SoulflowHud`, `PbPaceHud`, `CatacombsOverflowOverlay`) that weren't individually line-audited.
- Whether `Events.ON_PACKET` double-dispatch (§1.8) is a real bug or intentional needs someone who knows the `Events` bus design — flagged, not resolved.
- No profiling was run (no JFR/async-profiler capture) — every "hotspot" here is identified by code inspection (call frequency + allocation pattern), not measured. Before doing the actual optimization pass, it would be worth a short profiling session (even just Minecraft's built-in F3 debug + a spark/JFR capture during a dungeon run) to confirm §1.4 and §1.5 are actually where frame time goes, since inspection can mis-rank relative cost.
- The stale-checkout issue (§0) needs resolving before any of this is safely acted on — fixing code in the wrong tree wastes the work.

## 10. Final Performance Assessment (subjective, from inspection only — not measured)

```
CPU efficiency:       6/10   (a few real hot loops, but most of the mod is event-driven and cheap)
Memory efficiency:    7/10   (one leaked thread, one unbounded cache, otherwise well-managed TTL caches)
Tick efficiency:      6/10   (SimonSaysTracker + StarredMobHighlight are the real cost; rest is negligible)
Rendering efficiency: 5/10   (CompactTab + CustomScoreboard buildSig are genuine every-frame waste)
Network efficiency:   7/10   (executor consolidation already done well; getLocalMember dedup is the gap)
Architecture:         6/10   (good TTL-cache patterns exist as a template; duplicate tab/floor/color-strip logic across features is the main debt)
Overall:              6/10
```

---

## Appendix: Prior audit cross-reference (2026-09-03, AUDIT.md)

Confirmed still-open from that pass (re-verified this session):
- **MED-23** — moulberry.codes dead hosts in `CroesusPrices.kt`, no replacement chosen.
- **SPEC-5** — `HypixelApi.getLocalMember` TTL-cache, not implemented; 4 independent pollers confirmed unchanged.
- **LOW-31** — `MapImageLoader` leaked watch thread, `close()` still has no caller.
- **SPEC-4** — duplicate `/v2/resources/skyblock/items` fetchers (`SkyblockItems.kt`/`ItemsDb.kt`), not re-verified in depth this pass but no evidence of consolidation.

Confirmed fixed and holding up well:
- Executor consolidation (`HypixelApi.API_EXECUTOR`) — verified as the sole executor for all async HTTP calls.
- `nwPrices()` TTL cache + failure backoff — correctly implemented, used above as the reference pattern to copy for `getLocalMember`.
- Shutdown-hook lock contention (previously could stall up to 20s) — resolved, no longer shares a lock with blocking HTTP.
