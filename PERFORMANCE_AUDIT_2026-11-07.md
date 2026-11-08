# FishModDungeons — Performance & Size Audit 2026-11-07 (Inspection Only, No Edits)

HEAD audited: `43d0aed7` (Ship 1.7.39). Focus requested by Eli: reduce mod size, improve FPS.

## 0. Scope
- `dungeons-26.1.2/src/main`, `dungeons-26.2/src/main` overrides, `fishmod/compat/*`. `dungeons-1.21.11` untouched.
- Seven read-only passes (render/HUD, ticks, mixins/events/packets, tab/scoreboard/chat, network/API/cache, memory, jar size). Done as background subagents because no session-start tool was available.
- Jar inspected: `FishModDungeons-1.7.39-26.1.2.jar` (6.85 MB), parsed with zipfile + class-attribute reader.
- Nothing was built, run or profiled. HologramText and NecronTrace findings were re-read by the lead and confirmed.

## 1. Problems Found

### FPS / runtime
| # | Location | Cause | Impact | Sev | Effort |
|---|---|---|---|---|---|
| 1 | `features/HologramText.kt:63-83`, called from `FishEntityRenderMixin` (`take`) | `nametagsOnTop` defaults TRUE (`VisualSettings.kt:46`). Per named armor stand per frame, `blockedByTranslucent` ray-marches up to 192 steps (0.25 block) with a new `Vec3` + `getBlockState` each | Thousands of block lookups and allocs per frame near many holograms (Hypixel is full of them) | H | M |
| 2 | `utils/dungeon/NecronTrace.kt` (tick from `M7Relics.kt:93`) | Leftover diagnostic runs in production on F7 phase 8: boss-bar join + regex, 2 full `entitiesForRendering()` passes, `String.format` + INFO log every tick for 20 s | Per-tick scans + log spam in M7 | M | S (delete/gate) |
| 3 | `utils/dungeon/NecronDeath.kt:34-52`, `BossPhase.kt:94-103` | After "ARGH" a full entity pass + regex per named stand every tick; `checkNecronBar` regex per bar per tick. Stacks with #2: 3 entity scans/tick | M7 end-fight tick cost | M | M (shared throttled snapshot) |
| 4 | `java/fishmod/utils/events/FishConnectionMixin` + `PacketFilter` | ~17 packet listeners means list never empty, so every whitelisted packet (MoveEntity, SetEntityData…) hops to the client thread with a closure alloc even when no feature is active | Hot netty path churn | M | M (per-type subscriber counts) |
| 5 | `FishScreenMixin.java:~25-56` | ~9 capturing-lambda `MixinGuard.run` per frame on any container screen; ~10 slot AFTER listeners fire for every slot; several lack a setting gate | Per-frame alloc + per-slot virtual calls in every inventory | M | S-M |
| 6 | `FishEntityRenderMixin.fishmod$adjustNameTag` | Per entity per frame: `PlayerSize.scaleFor` (float[3]; UUID string+replace if sharing on), `hideBlazeTags` getString, 4 cull calls before the `renderOptimizer` gate | Per-entity per-frame churn | M | S-M |
| 7 | `cosmetic/RemoteNicks.kt:150-173` via `CosmeticGuiTextMixin` | Every `text()` draw and tooltip line: `text.string`, 2 regex matchers, a LinkedHashSet (when remote nicks on and any known) | All HUD text each frame | M | S-M |
| 8 | `features/item/ItemOverlays.kt:26-60` | Per slot per frame: tag read, pets do `hoverName.string` + `PET_LVL` regex | Per-slot per-frame | M | S |
| 9 | `features/CatacombsOverflowOverlay.kt:92-100` | Slot-after hook for every slot: `hoverName.string`, regex, replaces, lowercase (default off) | Per-slot per-frame when on | M | S |
| 10 | `features/mining/GemstoneLocator.kt:23-41` | Every 5 ticks, ~60k `getBlockState` at r=24 (461k at r=48) + Dijkstra on main thread. Default off, in tunnels only | Stutter when on | M | M |
| 11 | `features/KeyPickupAlert.kt:59` | Full entity scan every tick in dungeons when `keyHighlight` on (default off) | Per-tick scan | M | S |
| 12 | `cosmetic/RemoteItems.kt:126-145` | Every tick, each other player x6 slots: uuid string+replace, registry key string, concat (default off) | Heavy string churn when on | M | S |
| 13 | `features/rift/RiftMobs.kt:80-110`, `RiftHay.kt:56-70` | Bacte scan formats debug strings per entity even when no fight; Hay does ~5.3k block scan every 4 ticks (both default off) | Rift only | M | S / M |
| 14 | 26.2 `RenderHooks.kt:41-140`, `RecordingConsumer.kt`, `compat/WorldText.kt:61-77` | `gizmoOverlays` default true: 3-5 recorders (~10 KB each) + PoseStack + Vec3s per frame; late labels allocate Vector3f/Vec3/Item per label | Tens of KB garbage per frame, 26.2 only | M | M |
| 15 | Chat: `FishEvents.chat` (78 listeners), `ChatHudMixin` | No shared plain string; each listener flattens/strips. ~60 `.string`, ~30 regex strips per chat line | Burst cost per chat line | M | M |
| 16 | `Mining.kt:130-142` (`tabLine`/`tabWidget`) | Full tab scan with trim per call, via `inTunnels()` from many features | Hot if called per frame/tick | M | S (cache by `TabListCache.version`) |
| 17 | `features/RenderOptimizer.kt:80-96`, `CosmeticEntityNameMixin:24-30` | Per armor stand per frame: getString + regex strip before cache lookup (both default off) | Per-frame churn when on | L-M | S |
| 18 | `mining/Mining.kt:170-200` (`MiningHuds.reg`), KuudraHuds, Garden widgets | ~16 HUDs rebuild lines each frame; no `LineCache` | Per-frame alloc | L | M |
| 19 | `PetHud.kt:468,491`, `Ragnarock.kt:114`, `CooldownOverlay.kt:348`, `KuudraHuds.kt:65,117` | Per-frame `String.format` | Small | L | S |
| 20 | `ItemModelBehaviorMixin` (`getUseAnimation`) | Not gated on `ItemCustomizationStore.hasAny()` (unlike `ItemTrimMixin`) | Hot-ish call | L | S |
| 21 | `StorageOverlay.kt:136-143`, `InvincibilityTracker.kt:245`, `SkyBlockItemIconCache.getOrRequest:55` | Per-frame TreeMap/sort, entries().firstOrNull, trim().lowercase() on hit | Small | L | S |
| 22 | Ungated packet listeners: `ArrowsDevice:71`, `SimonSaysSolver:72`, `BloodSolver:78`, `RiftDreadfarm:55`, `MushroomCowPerk`, `PowderChests` | No outer gate; `when` + try/catch per packet | Small | L | S |
| 23 | `RiftDreadfarm:44-52`, `VenoStackCount:64-77`, `RareMobs:148-185`, `NametagCullingCompat:29`, `DungeonRole:46`, `CooldownOverlay:98`, `FishModInit:1436`, `NecronDropPredict:128` | Work runs before area/setting gate or on every tick/line (`hoverName.string`, regex, reflection, String.format) | Small each | L | S each |
| 24 | `DungeonPlayers.updateRosterInner:72-78`, `CompactTab:126,402`, `CustomScoreboard:121-130,265`, `DungeonState:133` | O(n*m) string allocs; re-strip already-plain `TabListCache.Entry.stripped` | Small | L | S |
| 25 | Colour-strip duplication: `FishConstants.STRIP_COLOR_REGEX` (61 uses), `HypixelApi.STRIP_COLOR` (8), ~15 private `Regex("§.")` copies, per-call `Regex("§.")` at `EquipmentDisplay.kt:66` and `Regex("^[^\p{L}\p{N}]+")` at `CustomScoreboard.kt:280` | `TextStrip` exists but is bypassed | Duplication + small per-call cost | L-M | S-M |
| 26 | `M7Relics.kt:119` | `stack.hoverName.string` per armor stand with head item | Small | L | S |

### Network / memory
| # | Location | Cause | Impact | Sev | Effort |
|---|---|---|---|---|---|
| 27 | `pv/neu/NeuRepoCache.kt:504` + `utils/data/NeuRepo.kt:28,51-97` | NEU repo zip downloaded and parsed twice (second copy into a HashMap, plus `readAllBytes` spike); used by `FairySouls`, `RecipeViewer` | Tens of MB extra download + heap | M | M |
| 28 | `HypixelApi.java:1281-1296` (`nwPrices`) vs `pv/price/SkyHelperPriceCache.kt:24` | Same `pricesV2.json` fetched/parsed by both, separate maps | Duplicate download + heap | M | S |
| 29 | `pv/data/MuseumCache.kt:27-73` | `CACHE` never cleared; `clearAll()` has zero callers. One large museum JsonObject per viewed profile stays resident | Heap grows per /pv profile viewed | M-L | S (call from `ProfileViewerScreen.removed()` or LRU) |
| 30 | `AthenPriceCache`, `SkyHelperPriceCache`, `CroesusPrices`, `HypixelCollectionsCache` | Three overlapping price datasets; Athen/SkyHelper/Collections poll from init even if /pv never opened | Idle polling + memory | L-M | S (lazy start) / M |
| 31 | `ItemsDb.kt:96-134` | Parses disk copy then refetch+parses again; keeps every item as JsonObject | Startup churn | L | S |
| 32 | `CatacombsOverflowOverlay.lastDumpAt:39` | Never cleared | Few strings | L | S |
| 33 | Executors/Http: separate fixed pools (`CoflnetApiClient:39`, `EliteBotApiClient:33`, `HypixelApiClient:92,95`, `ProfileFetcher:74`, `HypixelApi.java:193,200`, 4 single-thread), 5 leftover `HttpClient`s, raw `new Thread` (`ItemsDb:70`, `SpotifyHud`, `TerminalTracker`, `UpdateManager`), 200 ms sleep-poll watch loops (`CrosshairImageLoader:89`, `MapImageLoader:89`) | Partial consolidation only | ~23 thread slots, small | L | S |
| 34 | `SkyBlockItemIconCache.kt:29-41`, `UUID_NAME_CACHE`, `HypixelApi.uuidByName` | No cap/LRU; bounded by distinct items/players | Negligible | L | S |

### Jar size (6.85 MB: classes 5.9 MB compressed, 2006 classes; resources ~250 KB; bundled jars 174 KB)
| # | Item | Est. saving | Effort |
|---|---|---|---|
| S1 | Strip `SourceDebugExtension` (Kotlin SMAP, 266 KB raw) and `@kotlin.Metadata` (keep on `FishModAddonApi`) in the existing ASM pass in `gradle/strip-debug.gradle`. Grep `kotlin.reflect` first | ~150-190 KB | S |
| S2 | Strip `LineNumberTable` (645 KB raw). Trade-off: crash traces lose line numbers. Middle option: strip only `fishmod/pv` and data classes | ~200-250 KB | S |
| S3 | Subset the two Inter TTFs to Latin/Latin-1 (216 KB + 132 KB raw), keep symbols drawn via Inter (☆ ✔ ✖ ♛) | ~100-120 KB | S |
| S4 | 206 `data class` in `kotlin/fishmod/pv` generating unused copy/componentN/equals/hashCode/toString (snapshot classes). Not measured. Equality used by caches, so some regression risk | ~40-80 KB (estimate) | M |
| S5 | Optimise PNGs (`icon.png` 31 KB, `map/prince_crown_ziyno.png`) | ~20-30 KB | S |
| S6 | Collapse HTTP/executor/getJson duplication (items 33, 27, 28) | ~15-30 KB | M |
| S7 | Merge mixins: `FishDrawContextMixin`+`CosmeticGuiTextMixin`; shadow trio (`WorldTextShadow`/`SubmitTextShadow`/`SignRendererShadow`); `ContainerScreenMixin`→`FishScreenMixin`; `NoRecipeBook*`; `ItemModelBehaviorMixin`+`FishItemStackMixin`; `FishEntityRenderMixin`+`CosmeticEntityNameMixin`; fold `PlayerNameLabelMixin`. ~8 fewer mixin classes and startup transforms | ~10-20 KB + startup | S-M |
| S8 | Dead code: classes `features/scoreboard/ElectionInfo.kt`, `features/gamble/DropEvents.kt`, `features/garden/MoneyPerHour.kt`; ~78 dead Kotlin funs (list in raw notes: AuctionSnapshot `with*`, `CoflnetApiClient.playerAuctions/playerBids`, `HypixelApiClient.skyblockAuction`, `ProfileViewerScale` helpers, `PvDraw` layeredPanel/chromaRgb/paddedLabelValue, `FarmingProfitTracker` sessionItems/sessionTimeMs, `NameRewriter.dumpColors`, `PvTab` helpers, `HomePage` applyNetworth/slayerOverlayOpen/sbXpExpandPhase, `FishModScreen.makeButtonInput`). Keep `FishModAddonApi` public API. Single-occurrence count only, verify each with `where.py -k uses` before deleting | ~5-10 KB | S |
| S9 | Table-driven `FishModScreen` (107 KB) / `FishSettings` (66 KB) | unknown | L, skip |

Realistic jar total: S1+S3+S5 ≈ 300 KB (4-5%); +S2 ≈ 550 KB (8%); everything else ≈ +100 KB. The mod is code-heavy, so big reductions mean removing features. Comments, colour-strip helpers and `ON_WORLD_CHANGE` boilerplate do not affect jar size. Resources are tiny and all referenced; lazy-fetching JSON would save under 30 KB. Jar is already deflate-9, no shading/minimize applies.

### Possible correctness bug (not perf)
- `utils/events/EventDispatch.kt:11`: `PARTY_PREFIX = "§9Party §8>"` tested against `message.string`. If `Component.string` has no § codes the party hook never fires. Unverified in game; needs a check.

## 2. Prior-audit cross-reference

| Item (10-27 / 09-04) | Status now |
|---|---|
| 10-27 #1 comments | OPEN (no jar effect, slimming only) |
| #2 colour-strip duplication | STILL OPEN, partial (`TextStrip` exists, bypassed). Now item 25 |
| #3 HandledScreenMixin fan-out | Class gone; PARTLY FIXED. `MixinGuard` replaced most try/catch but 57 `FishDiag.fail` / 56 `catch Throwable` remain; fan-out moved to `FishScreenMixin` (items 5) |
| #4 Sidebar read in 11 places | FIXED (`SidebarCache`) |
| #5 HttpClient duplication | MOSTLY FIXED (`utils/Http.kt`); 5 clients remain (item 33) |
| #6 items JSON double fetch | FIXED (`HypixelItemsCache` delegates to `ItemsDb`); residual item 31 |
| #7 M7Relics 3 loops | MOSTLY FIXED (single pass); `hoverName.string` leftover (26). Neighbouring NecronTrace/NecronDeath are new (2, 3) |
| #8 BossDamage | FIXED (file gone) |
| #9 executor pools | PARTLY FIXED (`PvExecutors`, `IoExecutor`); item 33 |
| #10 profit trackers | FIXED (`ProfitTrackerKit.LineCache`) |
| #11 chat strip per listener | STILL OPEN (item 15) |
| #12 raw tab loops | FIXED (`ScoreboardUtil` deleted) |
| #13 IO executors | FIXED |
| #14 entities-near idiom | PARTIAL (StarredMobHighlight fixed; duplicate named-stand scans remain: KeyPickupAlert, Corpses, MiningBoxes, SlayerBossDetector) |
| #15 26.2 mixin copies | STILL OPEN, 10 copies (Crosshair, ExperienceBarRenderer, GameRendererUi, GlCommandEncoderUiTarget, Gui, GuiScoreboard, ItemInHandRenderer, ScreenEffectRenderer, WorldTextShadow, GuiAccessor), 4-21 changed lines each, mostly renamed targets. Affects 26.2 jar only |
| #16 regex in lambdas | MOSTLY FIXED; leftovers in item 25 |
| #17 per-frame String.format | MOSTLY FIXED; leftovers item 19 |
| #18 SkyBlockItemIconCache | PARTLY FIXED (clear on NEU reload only); item 34 |
| #19 ON_WORLD_CHANGE boilerplate | OPEN (cosmetic) |
| #20 sloppy bits | ON_PACKET gating FIXED (`PacketFilter`); empty `mixins` list FIXED; `AnimatedDyeAnimator.clear` FIXED; `lastDumpAt` OPEN (32) |
| 09-04 SimonSays O(n³), StarredMobHighlight, TabList x4-5, CompactTab, CustomScoreboard sig, getLocalMember, DataComponent mixins | all remain FIXED |
| 09-04 SPEC-4 / moulberry hosts | FIXED |

## 3. Recommended fixes, ranked (impact ÷ effort)
1. **#1 HologramText ray-march** (H): cap/step/skip beyond ~16-24 blocks, reuse a mutable vec, cache per entity for a few ticks. Only H item; on by default.
2. **#2 NecronTrace**: delete or put behind FishDebug. Then #3 share one throttled Necron snapshot.
3. **S1+S3+S5 jar trims** (~300 KB, trivial). Then decide on S2 (LineNumberTable, ~200 KB, costs trace line numbers).
4. **#29 MuseumCache.clearAll** hook + **#28 nwPrices to SkyHelperPriceCache**: S each, real heap/bandwidth wins.
5. **#5 + #6 FishScreenMixin / entity render gating**: one "any overlay active" check, gate before lambdas, merge `ContainerScreenMixin`.
6. **#4 per-type packet subscriber counts**: avoids the main-thread hop for MoveEntity/SetEntityData when unused.
7. **#7/#8/#9 per-frame string caches** (RemoteNicks fast pre-check, ItemOverlays WeakHashMap, CatacombsOverflow screen-title gate): S each.
8. **#27 NEU repo downloaded twice**: M, big bandwidth and heap.
9. **#15 plain chat string** shared through the event and mixin (M, touches 78 sites) plus **#16 Mining.tabLine cache** (S).
10. **S7 mixin merges + S8 dead code + S4 data-class trim**: moderate size win, low risk each.
Quick wins batch (all S): #11, #12, #13, #17, #19, #20, #21, #22, #23, #24, #30 lazy start, #32. Bigger refactors (later): #14, #18, #25, #33, S6, #15 of 10-27 (26.2 mixin copies).

## 4. Honest assessment
- **Inspected, not measured.** No profiler, no in-game test. Severity is by code shape (work per frame × default-on?), not frame time. Several "M" items are default-off settings and only matter if Eli or users enable them.
- Only #1 is a likely real FPS cost on default settings (default-on, per-frame, scales with holograms in view). #5, #6, #4 are default-on but cheap per call; their gain is mostly fewer allocations (less GC pressure), not a large FPS jump.
- Most remaining runtime items are default-off or area-gated. Realistic FPS gain from this list is modest; the big per-frame hot spots from 09-04 stayed fixed.
- Jar: ~300-550 KB (5-8%) reachable cheaply; beyond that requires removing features. Code is 86% of the jar.
- Coverage gaps: 26.2 overrides were not diffed semantically beyond mixins; dead-code sweep covered Kotlin funs/classes only (no properties, no Java); `Mining.tabLine` call frequency not traced; `EventDispatch` PARTY_PREFIX unverified; `data class` saving unmeasured; each agent greped for growth risks rather than reading every collection field (~760 grepped).
- Every fix must build on both `:dungeons-26.1.2` and `:dungeons-26.2`.
