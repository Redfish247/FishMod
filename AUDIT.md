# FishModDungeons — `dungeons-26.1.2` deep-dive audit

**Read-only audit. Nothing in the repo was changed to produce this.**
Target: `dungeons-26.1.2` module only, current working tree of branch `claude/section-6-fixes`
(uncommitted + untracked files included). The frozen `dungeons-1.21.11` module was not audited.

---

## How to use this document (for the implementing agent)

1. **Do not batch-apply.** Work top-down by severity. Each item says *what*, *why*, *where*, and a
   *suggested fix*. Treat the fix as a starting point, not gospel.
2. **Verify before deleting.** Every "dead code / unused" claim below was grep-checked across
   `dungeons-26.1.2/src` **and** `common/src`, but re-confirm with your own search before removing a
   symbol — reflection, mixin `@Invoker`/`@Accessor` targets, and `config.practical` annotation
   scanning can reference things that look unused.
3. **Do not change feature behaviour.** Where a fix risks altering what a working feature does
   (rendering, solver output, HUD layout, chat handling), keep the change minimal and preserve the
   existing observable behaviour. If a "fix" would visibly change a feature, leave it and note it.
4. **Comment removal rule (from the requester):** remove `//` comments that are *not*
   credit/attribution. Keep:
   - `/** … */` KDoc / Javadoc,
   - lines naming a source ("ported from Odin", "NoammAddons", "Skytils", "System22", "blade-addons",
     "1:1 with …", author handles/UUIDs used as identity).
   Remove: explanatory "why" prose blocks, `// ===== Section =====` / `// ── divider ──` banners,
   restate-the-code one-liners, and **commented-out code** (highest priority — see LOW-1).
   The per-file comment inventories are large; a practical approach is a scripted pass that strips
   full-line `//` comments and trailing `//` comments, then a human/agent review of the diff to
   re-instate the credit lines listed above.
5. **Build check:** `./gradlew :dungeons-26.1.2:build` from the repo root after each severity tier.

### Known false positive — do NOT "fix" this

`dungeons-26.1.2/src/main/kotlin/fishmod/utils/dungeon/map/MapReader.kt` references the type
`GridPos`, which is **not** in the `dungeons-26.1.2` source tree. It is **not** a compile error:
`GridPos` is defined in `common/src/main/kotlin/fishmod/utils/dungeon/map/GridPos.kt` and
`dungeons-26.1.2/build.gradle` pulls it in via `implementation project(':common')`. The module
compiles. `MapReader` is still *dead* (see MED-19) — never `init()`-ed, no in-module call sites — but
the fix is "delete the dead file / wire it up", not "add a missing class".

---

## CRITICAL

### CRIT-1 — `Events.ON_WORLD_CHANGE` has 28 listeners and **no producer**
- **Where:** declared `utils/events/Events.kt:29`; interface `utils/events/interfaces/WorldEvent.kt`.
- **Confirmed:** 28 `Events.ON_WORLD_CHANGE.register { … }` call sites across the module
  (TerminalSolver, SimonSaysSolver, BloodSolver, SpiritBear, LividSolver, WitherDragons,
  ArrowsDevice, PuzzleSolvers, SecretClicked, SessionStats, Blessings, InvincibilityTracker,
  ExtraStats, MimicAnnounce, AutoRequeue, ArchitectDraft, M7Relics, MelodyMessage, TerracottaTimer,
  PartyFinder, OdinScan, NametagStats, Ragnarock, TacTimer, WarpCooldown, ChatQueue, EntityUtil,
  Location). **Zero** `.invoke` / `onWorldSwap(` call sites anywhere (verified by grep + `git log -S`).
- **Why it matters:** every feature that relies on this event to clear per-run state never gets the
  signal. `TerminalSolver.reset()`, `BloodSolver.resetRun()`, `MimicAnnounce` flags,
  `PartyFinder.kicked`, `EntityUtil.playerMap`, `Location`'s `detectedNewLocation`, etc. Some
  features *also* reset on `ON_LOCATION_CHANGE` or `RUN_END` and are fine; others are not. Net effect:
  intermittent state bleed between dungeon runs (stale solver highlights, stale trackers).
- **Fix:** add exactly one producer. Canonical spot: a mixin on `Minecraft#setLevel` (or
  `ClientPacketListener` login/respawn), firing
  `Events.ON_WORLD_CHANGE.invoke { it.onWorldSwap() }` when the `ClientLevel` instance actually
  changes. Cheaper stopgap: fire it from `Location.changeLocation()` / a
  `ClientPlayConnectionEvents.JOIN` hook on real level change. After adding it, **audit each of the 28
  consumers** — a couple may double-reset and need a guard.
- Was almost certainly meant to come from `blade-addons`, which is now never on the classpath (see
  HIGH-2).

### CRIT-2 — `Events.ON_ENTITY_SPAWNED` has a listener and no producer
- **Where:** declared `Events.kt:43`; sole consumer `features/dungeon/f7/CrystalSpawn.kt:65`.
- **Confirmed:** nothing invokes it. `EntityMixin` only handles glow; `ClientPlayNetworkHandlerMixin`
  handles `handleAddEntity` for RenderOptimizer hiding but does not fire this event.
- **Why it matters:** F7 crystal-spawn detection in `CrystalSpawn` is dead unless it has another path
  (it does not).
- **Fix:** fire `Events.ON_ENTITY_SPAWNED.invoke { it.onEntity(entity, level) }` from
  `ClientPlayNetworkHandlerMixin#handleAddEntity` (TAIL) or a `ClientEntityEvents.ENTITY_LOAD`
  callback — or delete the event + the `CrystalSpawn` listener if superseded.

> `ON_ENTITY_TRACKED` (Events.kt:42) is also fully dead — zero register, zero invoke. `ON_SLOT_CHANGE`
> and `ON_BLOCK_ENTITY` have producers (mixins) but zero listeners (see LOW list).

---

## HIGH

### HIGH-1 — `fishmod.mixins.json`: `ClientPlayNetworkHandlerMixin` registered twice; two client mixins in the common list
- **Where:** `src/main/resources/fishmod.mixins.json`.
- `ClientPlayNetworkHandlerMixin` appears in the root `"mixins"` array **and** in `"client"`.
  It targets `net.minecraft.client.multiplayer.ClientPacketListener` (client-only).
- `StuckArrowsFeatureRendererMixin` is in the root `"mixins"` array but targets a client-only
  renderer class (`ArrowLayer`).
- **Why it matters:** best case, Mixin logs a duplicate-config warning and de-dups. Worst case
  (stricter loader), the `ClientPlayNetworkHandlerMixin` injectors apply twice — `ON_SOUND`,
  `ON_GAME_MESSAGE`, `ON_PARTICLE`, `ON_PLAYER_ENTRY`, entity-hide, `setTitleText`, `handleSystemChat`
  each fire twice per packet, compounding the existing "runs on netty + main" double-dispatch. The
  common-list client classes would fail to classload on a dedicated server.
- **Fix:** remove `"ClientPlayNetworkHandlerMixin"` from the root `"mixins"` array (keep the
  `"client"` entry); move `"StuckArrowsFeatureRendererMixin"` from `"mixins"` to `"client"`.

### HIGH-2 — `fishmod/Bladeaddons.kt` is entirely dead code
- **Where:** `src/main/kotlin/fishmod/Bladeaddons.kt` (`class Bladeaddons : ModInitializer`).
- **Confirmed:** `fabric.mod.json` registers only `fishmod.FishModInit` (main) and
  `fishmod.utils.config.ModMenu` (modmenu). `Bladeaddons.onInitialize()` is never invoked. Everything
  it would do in "standalone mode" is already done unconditionally by `FishModInit.onInitialize()`
  (`FishModInit.kt:1336-1350`, via `safeInit`). The class is referenced only as a resource anchor
  (`Bladeaddons::class.java.getResourceAsStream` in `RoomData.kt:81`, `JsonUtility.kt:16`).
- **Why it matters:** reads like a live second entrypoint. If anyone re-adds it to `fabric.mod.json`
  the whole framework double-inits (double tick handlers, double event registration). The
  `bladePresent` / `isModLoaded("blade-addons")` branch is unreachable — that mod is never present.
- **Fix:** reduce `Bladeaddons` to a bare anchor class (drop `: ModInitializer` + body), or delete it
  and repoint the two `getResourceAsStream` sites at a stable class (e.g. `FishModInit::class.java`).
  Remove the now-unused imports.

### HIGH-3 — Two `ConfigManager`s own the same four classes; changes silently don't persist
- **Where:** `utils/config/Config.kt:15-21` and `utils/config/FishConfig.kt:24-38`.
- `Config.manager` (file `./config/fishmod.json`) registers `Phase, Section, Split, ExtraOptions,
  Components, Dungeons, PuzzleDisplay`.
  `FishConfig.manager` (file `config/fishmod-settings.json`) registers `Phase, Section, Split,
  Dungeons` (+ `FishSettings`, `Floor7`, `DungeonMapSettings`, …).
- These are `object` singletons, so both managers read/write the *same* static fields. Load order:
  `FishConfig.manager.load()` at `FishModInit.kt:387` (first), then `Config.manager.load()` at
  `:1338` (last) → **`fishmod.json` wins** for `Phase/Section/Split/Dungeons`; their copy in
  `fishmod-settings.json` is written but ignored on load.
- **Why it matters:** every code path that only calls `FishConfig.manager.save()`
  (`FishHudEditor.kt`, `TermSimScreen.kt:74`, `PracticeMode.kt:80`, `InstallHeartbeat.kt:44`,
  `FishModInit.kt` command handlers) does **not** persist any change to `Dungeons.*` across
  restarts. Only `FishModScreen.kt:2343-2344` saves both. Classic "my setting didn't stick" bug, and
  the two JSON files silently diverge.
- **Fix:** one owner per class. Simplest: remove `Phase, Section, Split, Dungeons` from `Config.kt`'s
  list (leaving it `ExtraOptions, Components, PuzzleDisplay`). Consider whether `Phase/Section/Split`
  (runtime run-state) belong in a persisted manager at all. See also LOW-2 (`Components` is an empty
  no-op object).

### HIGH-4 — Uncommitted change adds a duplicate "Dungeon Map" HUD registration (reintroduces a fixed bug)
- **Where:** new `run { … }` block at `FishModInit.kt:504-513` (working-tree addition) vs the
  pre-existing registration at `FishModInit.kt:1293`.
- Both call `FishHudEditor.register("Dungeon Map", …)`. The new block hard-codes a `128 x 132`
  preview size — the comment at `:1298-1300` documents that a hardcoded box here previously made the
  editor preview larger than the real in-game map, and that bug was fixed by matching `MapHud`'s
  actual size.
- **Why it matters:** two editor entries under the same key (duplicate row or one silently shadows
  the other) and the wrong-preview-size bug comes back.
- **Fix:** delete the `FishModInit.kt:504-513` block. The `:1293` registration is the intended one.

### HIGH-5 — `WaterSolver` (odin) chat-spams a red error ~twice per second — regression vs the deleted solver
- **Where:** `features/dungeon/puzzles/odin/WaterSolver.kt:62-66`.
- The `else -> { Misc.addChatMessage("§cFailed to get Water Board pattern. Was the puzzle already
  started?"); return }` branch leaves `patternIdentifier == -1`. `PuzzleSolvers` re-drives `scan()`
  on a 10-tick poll, so this line prints roughly every 0.5 s for as long as you stand in a Water
  Board room whose puzzle a teammate already started. The pre-migration `WaterSolver` (git HEAD) had
  a silent `else -> return` here.
- **Fix:** add `private var failed = false`; set it in the `else`, bail at the top of `scan()` while
  `failed`, clear it in `reset()`. Or drop the chat message entirely (Odin only prints it on an
  explicit trigger, not a poll).

### HIGH-6 — `PingFeature` is entirely dead (never initialized) + 7 dead config fields, one defaulting on
- **Where:** `features/PingFeature.kt` (whole file). `FishModInit.kt:1352` —
  `// safeInit("PingFeature", fishmod.features.PingFeature::init);` is commented out and there is no
  other `PingFeature.init()` call. `init()` is where the keybind, tick handler, world render hook and
  chat listener all register.
- Dead config in `utils/config/values/FishSettings.kt:427-433`: `pingEnabled` (**default true**),
  `pingSound`, `pingAnnounceParty`, `pingShareEnabled`, `pingFromChat`, `pingColor`,
  `pingDurationSeconds`. Not surfaced in `FishModScreen` either.
- **Fix (pick one):** (a) restore `safeInit("PingFeature") { PingFeature.init() }` and add a settings
  section, or (b) delete `PingFeature.kt` and the 7 `ping*` fields. Confirm intent with the requester
  — this looks like a feature that was shelved, not abandoned.

### HIGH-7 — `CooldownOverlay` tuba item IDs are misspelled → those cooldowns never fire
- **Where:** `features/CooldownOverlay.kt:49-50` — `COOLDOWNS["WIERDER_TUBA"]`,
  `COOLDOWNS["WIERD_TUBA"]` ("WIERD", not "WEIRD"). Also `:55` `COOLDOWNS["HOTSPLOT_RADAR"]` (likely
  `HOTSPOT_RADAR`).
- `ItemUtil.getId()` returns Hypixel's real ID (`WEIRD_TUBA` / `WEIRDER_TUBA`), so
  `COOLDOWNS.containsKey(id)` is always false and the overlay never shows for the tubas.
- **Fix:** rename the keys to `WEIRD_TUBA` / `WEIRDER_TUBA`; verify `HOTSPOT_RADAR` against a live
  item before changing it. (Confirm the exact IDs in-game / against a Skyblock item dump before
  editing.)

### HIGH-8 — `CustomEvents` party-message parser drops the first character of unranked usernames
- **Where:** `utils/events/CustomEvents.kt:34-37`.
  ```kotlin
  index = tempUsername.indexOf("]") + 2
  if (index > -1 && index < tempUsername.length) {
      tempUsername = tempUsername.substring(index)
  }
  ```
- For a default-rank player there is no `]`, so `indexOf("]")` returns `-1`, `index` becomes `1`, the
  guard `1 > -1 && 1 < length` passes, and `substring(1)` drops the first character
  (`"Playername"` → `"layername"`). **Confirmed** by reading the file.
- **Why it matters:** `Events.ON_PARTY_MESSAGE` fires with a corrupted username for every unranked
  party member → party-chat `.command` routing / name matching in `PartyCommandHandler` fails for
  those players.
- **Fix:**
  ```kotlin
  val b = tempUsername.indexOf("]")
  if (b >= 0 && b + 2 <= tempUsername.length) tempUsername = tempUsername.substring(b + 2)
  ```

### HIGH-9 — `HypixelApi` blocking HTTP on the shared `ForkJoinPool`, and a class-monitor held across a 20 s GET
- **Where:** `src/main/java/fishmod/utils/HypixelApi.java`.
- ~9 `CompletableFuture.runAsync(() -> { … })` sites with **no executor** → shared
  `ForkJoinPool.commonPool()` (parallelism = CPUs−1); each runs a synchronous `HTTP.send(...)` with
  8–20 s timeouts. A dungeon party lookup (nametag stats ×5 + networth + cata + PB + powder) can
  occupy every common-pool thread for tens of seconds, starving parallel streams elsewhere in the
  game.
- `private static synchronized Map<String,Double> nwPrices()` (`:1124`) holds the `HypixelApi.class`
  monitor across a 20 s HTTP GET. `ensureUuidCacheLoaded()` / `saveUuidCacheNow()` block on the same
  monitor, and the JVM shutdown hook calls `saveUuidCacheNow()` — a game exit during a price refresh
  stalls shutdown up to 20 s.
- **Fix:** route all API calls through a dedicated bounded daemon `Executor`
  (`Executors.newFixedThreadPool(4, …)`). In `nwPrices()`, do the GET outside the lock and only
  `synchronized` the map swap (or use a private lock object). Add a short failure cool-down so a
  GitHub/Hypixel outage doesn't cause a 20 s blocking retry on every request (`:1124-1141`).

### HIGH-10 — `Scan.scanWorldDoors` re-scans the whole world on every chunk load, forever
- **Where:** `features/dungeon/map/Scan.kt:63-72, 102-104, 310-355` (+ `handleWorldDoor` `:149-160`).
- Every `CHUNK_LOAD` while in a dungeon re-arms `shouldScan`; `scan()` calls `scanWorldDoors(world)`
  **even in the `loadedAllRooms` branch**. For each of ~60 door slots without an existing `Door`,
  `handleWorldDoor` does `world.getChunk(...)` + `getTopY` = up to ~150 `getBlockState` calls per
  slot. During terrain streaming that's thousands of blockstate lookups per tick, indefinitely, after
  the map is already fully known (world-scanned doors never change post-discovery; wither unlocks come
  via the map-packet path).
- **Fix:** once `loadedAllRooms` is true, stop re-arming `shouldScan` on `CHUNK_LOAD` (or skip
  `scanWorldDoors` entirely, or throttle to every N seconds). Same theme as MED-16/MED-18.

### HIGH-11 — Large blocks of `@ConfigValue` fields with no implementation (config toggles that do nothing)
- **Where:** `utils/config/values/FishSettings.kt`, `Dungeons.kt`, `ExtraOptions.kt`, `Floor7.kt`.
- Module-wide grep found whole feature families that are config-only — declared, persisted, some
  shown in `/fm` (`FishModScreen.kt`) and `FishHudEditor.kt`, but never read/rendered anywhere:
  - **FishSettings (~80):** fishing timer (`fishingTimer*`, `fishingReminder*`, `fishingMissed*`),
    challenges (`challenge*`), coin/hr trackers (`farmingTracker*`, `miningTracker*`,
    `powderTracker*`, `harvestFeast*`, `skillTracker*` + their `*PriceMode`), slayer
    (`slayerAlerts*`, `slayerDrops*`, `slayerXp*`), `seaCreature*`, `trophyFish*`, `trophyFrog*`,
    plus orphans `soulflowHudColor`, `petHudColor`, `petXpAutoDetect`, `nickPreviewScale`,
    `fmguiScale`, `warpMapHudEnabled`, `warpMapDotColor`, `splitsHudX/Y`, `cooldownShowBar`,
    `invincEquippedMaskColor`, `terminalMelodyRowColor`, `lootTrackerX/Y`, `pcVisitor`,
    `trackerPriceMode` (already comment-marked "not used").
  - **Dungeons.kt (~27):** `highlightItems`, `showProcTitle`, `useSprites`, `hideAfterLeap`,
    `hideOnlyInBoss`, `hideAtSS`, `hideBeforeTermsOnly`, `displayChestCount`, `onlyAfterRunOver`,
    `sendChestWarning`, `chestWarningCount`, `calculateCriticalHit`, `onlyInBoss`,
    `enableSecretSpawnTimer`, `combineScreenNotifications`, `dontProtectHeldItem`, `maskHighlight`,
    `detectPlayerCount`, `highlightTeammates`, `dontHighlightHiddenTeammates`,
    `displayKeyForAllClasses`, `quizTimer`, `quizProgress`, `removeMaskPart`, `alertBloodSpawns`,
    `alertBloodForAllClasses`, `disableDropAnimation`.
  - **ExtraOptions.kt (~34):** listed in the raw audit — many `pet*`, `*Sound`, `luckyButton*`,
    `practiceSSAnywhere`, `stunWaypoint`, `showPbs`, etc.
  - **Floor7.kt (~25):** `assumeCore`, `assumeSplitEE2`, `atLocationSound`,
    `blockIncorrectRelicPlace`, `combineTickTimers`, `displayDistanceToLedge`,
    `displayLocationNotification`, `dontNotifiyForYourself`, `dragSpawnTimers`, `dragonHealth`,
    `dragonTracer`, `enableBossWaypoints`, `enablePositionalMessages`, `enableRelicPlaceTime`,
    `maxorStunDuration`, `nextWaypointColor`, `nextWaypointThroughWall`, `notificationDuration`,
    `notificationRepetitions`, `notifyUsedSpiritMask`, `predevForAll`, `replaceWithProgressBar`,
    `sendSoundOnDragSpawn`, `showAllRelicTimes`, `showDistanceAtYellowOnly`, `useValleyBar`.
- **Why it matters:** a user toggling any of these in `/fm` sees nothing happen; the config screen
  advertises HUDs ("Trophy Frogs", "Sea Creatures", "Powder", "Slayer XP", "Harvest Feast") that
  never render.
- **Fix:** **verify each field individually** (grep the exact name across `dungeons-26.1.2/src` and
  `common/src`), then delete the confirmed-dead field *and* its `FishModScreen` / `FishHudEditor` row
  together. Some (`lootTrackerX/Y`, `pcVisitor`) may be intended-but-unwired — flag those for the
  requester rather than deleting. This is a big, mechanical, error-prone change; do it in its own
  commit, field by field, with a build between batches.

---

## MEDIUM

Rendering / GL:
- **MED-1** `NvgGlStateGuard.kt:60,107` never records/restores the active texture unit (both hard-set
  `GL_TEXTURE0`). If MC's cached active unit differs at paint time, the next vanilla texture bind
  lands on the wrong unit → "textures wrong/missing after opening a screen". Fix: capture
  `glGetInteger(GL_ACTIVE_TEXTURE)` in `capture()`, restore it last in `restore()`.
- **MED-2** `NvgGlStateGuard.restore()` `:103-106` restores framebuffer/program/buffer/VAO via raw
  `GL30/GL20/GL15` calls, bypassing blaze3d's `RenderSystem` state cache — a later `RenderSystem`
  call may skip a re-bind it actually needs. Route program/framebuffer restore through
  `RenderSystem`/`GlStateManager` where an equivalent exists, or add a one-frame resync after
  `restore()`.
- **MED-3** `FishModScreen.kt:2321,2333` runs a `while (glGetError() != GL_NO_ERROR)` drain every
  frame while the settings screen is open (`glGetError` forces a driver sync). Gate behind a debug
  flag or latch once.
- **MED-4** `EntityRendererMixin.java:38` allocates a `String` per named entity per frame
  (`state.nameTag.getString().contains("Blaze")`) when `Dungeons.hideBlazeNameTag` is on. Hoist to
  one local / cache the decision on the render state. (`:78` `pl.getName().getString()` similar,
  rarer.)

Mixins:
- **MED-5** `ClientConnectionMixin.channelRead0` (`:22-44`) dispatches `ON_SERVER_TICK`,
  `ON_GAME_MESSAGE`, `ON_PACKET` from the **Netty I/O thread** with no `isSameThread()` guard (the
  sibling sound hooks in `ClientPlayNetworkHandlerMixin` do guard). `ON_GAME_MESSAGE` for system
  chat is *also* dispatched from `ClientPlayNetworkHandlerMixin` on the main thread → duplicate +
  cross-thread delivery. Pick one code path; add an `isSameThread()` early-out.
- **MED-6** `ClientConnectionMixin.java:46-51` — `@Inject(method="sendPacket", at=HEAD)` with an
  **empty body** and a comment saying "for some reason this being here fixes player tracking and I
  don't know why". An empty non-cancellable injector can't change behaviour; it only adds a callback
  frame + `CallbackInfo` alloc on every outbound packet. Remove it.
- **MED-7** `SkullBlockEntityRendererMixin.java:24` — `Identifier.fromNamespaceAndPath(NAMESPACE,
  "/textures/entity/essence.png")` has a leading `/` → malformed path, texture won't resolve
  (missing-texture render). Fix to `"textures/entity/essence.png"`. Also `:40`
  `profile.id().toString()` can NPE for a partial profile — guard `profile.id() != null`. Field
  `ESSENCE_TEXTURE` should be `static final` + `@Unique`.
- **MED-8** `StatusEffectsDisplayMixin.java:21` uses `require = 0`, so a wrong mapping name silently
  no-ops and "hide status overlay" leaves the tooltip visible. Verify the target method name on
  1.21.x and drop `require = 0`.
- **MED-9** `InventoryScreenMixin.java:26-29` unconditionally `ci.cancel()`s `extractLabels` with no
  config gate and no comment → the survival-inventory "Crafting" label is permanently removed. If
  intentional, gate it + comment it; if not, it's silently suppressing vanilla UI.
- **MED-10** `HandledScreenMixin.java:76-82` — when `SearchBar.keyPressed` consumes a key it does
  `cir.setReturnValue(false)`, telling vanilla the key was *not* handled, so vanilla still processes
  it (e.g. the inventory-close key closes the screen while typing in the search field). Should be
  `setReturnValue(true)` when consumed.
- **MED-11** `ChatHudMixin.java:59` builds `message.getString().replaceAll("§.", "")` for **every**
  chat line before any feature check. Add a fast early-out when every party/guild/officer/private/
  all/pf/compact feature is off.
- **MED-12** `CosmeticGuiTextMixin.fishmod$swap` (`:53-70`) runs for ~every `Component` drawn each
  frame (scoreboard, tab, HUD numbers, tooltips) and still does `fishmod$inMenu()` +
  `RemoteNicks.apply/applyResolvedOnly` per draw when `NickState` is inactive. Add a top guard:
  `if (!NickState.isActive() && RemoteNicks.isEmpty()) return text;`.
- **MED-13** `GuiScoreboardMixin.java:23-26` and `PlayerListHudMixin.java:29-33` wrap
  `CustomScoreboard.render` / `CompactTab.render` in `catch (Exception ignored) {}` — any bug in
  those renderers is an invisible blank HUD. Log once at minimum. (`HypixelApi.java` has the same
  pattern pervasively for per-field JSON tolerance — acceptable there, but the outer request handlers
  should log.)

Puzzles / F7:
- **MED-14** Ice Fill "Optimized Patterns" and Water Board "optimized" **share one config field**
  (`FishSettings.waterOptimized`): `FishModScreen.kt:399` binds the Ice Fill toggle to
  `waterOptimized`; `PuzzleSolvers.kt:47/64` + `WaterSolver.onTick()` (`:44`) read the same field.
  Toggling one flips the other. Add `iceFillOptimized` and repoint the Ice Fill toggle + the two
  `IceFillSolver` call sites.
- **MED-15** `puzzles/odin/BlazeSolver.kt:19-28, 51-56, 62-66` still carries migration diagnostics
  (`trace()`/`gbTrace()`, `samples`, two extra `entitiesForRendering().count { … }` passes with a
  regex per entity) that run throttled inside a 10-tick poll and every gizmo frame. Delete the
  scaffolding; keep the core `getBlaze()` loop.
- **MED-16** `MapVec2i.index()` (`:27`) combines the two axis tiles as `tx*6 + tz` without checking
  each is in `0..5`, so an out-of-grid position aliases to a *wrong* room instead of null. Feeds
  `DungeonMap.roomPlayerIn()` → `OdinScan` (mis-IDs the puzzle room) and
  `DoorHighlight.facingRoomTile()`. Fix: `return if (tx in 0..5 && tz in 0..5) tx*6+tz else -1`
  (the unused `roomTilePos()` already computes `(tx,tz)`).
- **MED-17** `SimonSaysTracker.kt:132-133` registers two `END_CLIENT_TICK` handlers; `debugTick` scans
  a 13³ cube every tick when `debug` is on. Merge into one handler. Also `scanLitCells`
  (`:305-314`) is a 15³ = 3375-block scan every tick while a player intersects the device box —
  radius 7 could be 2–3.

Map subsystem:
- **MED-18** `DungeonScore.tick` (`:46-67`) runs full tab + sidebar + roster parsing **every client
  tick** (20 Hz): `parseTab` (iterates `onlinePlayers`, ~8 regex ops/line), `parseSidebar`,
  `DungeonPlayers.updateRoster` (iterates `onlinePlayers` again + rescans `level.players()`). Score
  only changes on chat/tab updates. Throttle to ~10–20 ticks; iterate `onlinePlayers` once and
  share.
- **MED-19** `DungeonState.isInDungeon()` (`:142-156`) is uncached and called every frame *and* every
  tick from `MapHud`, `MapInfoHud`, `ScoreMessages`, `DungeonScore`, `Mimic`, `Scan`; each call
  iterates `onlinePlayers` running `stripColors` per name. Cache, invalidate on world change / every
  ~20 ticks.
- **MED-20** `ScoreMessages.kt:57-73` fires the "270 Score!" / "300 Score!" **party** messages off
  `DungeonScore.score`, which is `calculateScore()` — a *projected* value that assumes the current
  room + blood complete and adds a flat +100 time component. On a strong run it crosses the
  thresholds before Hypixel shows the score, and it can fire on runs that then lose points. Trigger
  off the real parsed score, or reword as "on pace for".
- **MED-21** `stripColors` / colour-strip compiles a fresh `Regex("(?i)[&§][0-9a-fk-or]")` per call
  in `DungeonState.kt:108`, `DungeonScore.kt:99`, `DungeonPlayers.kt:76`, `ScoreMessages.kt:93` — all
  on per-player / per-line / per-tick paths (`DungeonScore` even keeps *other* patterns precompiled
  right above). Hoist one shared `val`.
- **MED-22** `Scan.calculateCore` (`:205-223`) — the new `resolved` guard correctly skips
  fully-identified cells, but every *unresolved* cell still runs `getTopY` (~150 `getBlockState`) +
  a column hash each scan, and scans re-arm on every chunk load. Throttle full scans (min ~250 ms) or
  only scan cells whose chunk just loaded.
- **MED-19b (`MapReader`)** — dead. `MapReader.init()` never called; `worldToGridPos` /
  `tileWorldOriginX/Z` / `isCalibrated` have zero call sites in the module. Delete
  `utils/dungeon/map/MapReader.kt` (it needs `common`'s `GridPos`, so if the world-grid bridge for
  `DungeonWaypoints` is planned, wire it instead). **Not a compile error — see the false-positive
  note up top.**

Croesus / utils:
- **MED-23** `features/croesus/CroesusPrices.kt:183,205` — `moulberry.codes/auction_averages_lbin` and
  `/lowestbin.json` are long-dead hosts (the code itself documents "HTTP 525" backoff). The profit
  tracker effectively runs on bazaar + slow per-item coflnet fallback only, so `price()` returns 0
  for many armor/weapon rewards until coflnet resolves. Replace with a live bulk LBIN source
  (Coflnet `sky.coflnet.com`, SkyHelper, a maintained mirror) or drop `fetch*Lbin` and lean on
  coflnet bulk.
- **MED-24** `CroesusPrices.kt:110-113` prefers `min` over `median` from the coflnet response
  (comment says the payload is `{min, median, max, mode, volume}`). `min` = lowest historical sale →
  systematic under-valuation. Prefer `median` (or `mode`).
- **MED-25** Widespread `String.replace(Regex("§."))` recompiling a `Regex` per call on hot paths:
  `ArchitectDraft.kt:26`, `ExtraStats.kt:72,114`, `KeyNotifier.kt:26`, `LeapMenu.kt:61`,
  `PuzzleDisplay.kt:47`, `PartyFinderPanel.kt:104`, `PartyFinderStats.kt:29`, `StorageCache.kt:96`,
  `SkyblockItems.kt:39` (in a ~4000-item loop), `GradientNick.kt:27,56,121`,
  `CroesusRewardParser.kt:138`, `ChatFilter.kt:58`, `BridgeBot.kt:37`, `ChatRuleHandler.kt:31,64`,
  `WardrobeHotkeys.kt:62,126`, `PingFeature.kt:59`. Hoist a shared `private val COLOR = Regex("§.")`
  (or a non-regex strip in `utils/data/TextUtil.kt`). Several files already do this right — make it
  consistent.
- **MED-26** `PartyCommandHandler.java:472-482` (`.runs` case) does
  `num = floor.charAt(1) - '0'` then `data.masterTimes[num]` / `data.cataTimes[num]` with **no
  bounds check** (unlike the `.collection` path at `:544-545`). If those arrays are length 7,
  `.runs f7` / `.runs m7` throws AIOOBE inside the async callback → command silently fails. Verify
  `HypixelApi.DungeonData.masterTimes/cataTimes` length; add the same guard if not 8.
- **MED-27** `chat/ChatRuleHandler.kt:78-85` — `Pattern.compile(testFilter)` inside `matches()`,
  which runs once per rule per message from the packet path *and* again from
  `shouldHideAtDisplay()` (ChatHudMixin display path). Cache a compiled `Pattern` on the `ChatRule`
  (invalidate on filter/flags change).

Top-level features:
- **MED-28** `scoreboard/CustomScoreboard.render()` (`:41-112`) reparses the whole scoreboard **every
  frame** (copy+sort entries, `ScoreboardSection.classify()` ≈28 `Pattern.find()` per line,
  `CompactNumbers.apply()` per line, `String.format` in `extraLines()`) — ~30k regex ops/sec for a
  display that changes a few times/sec. Cache the built line list + width; rebuild on
  objective/score change or a ~250 ms throttle.
- **MED-29** `CompactTab` (`:51-58` `shouldRender`, `:61-210` `render`) rebuilds + re-sorts the full
  player list, the `grouped` map, per-entry column widths, and regex-scans the sidebar/footer **every
  frame** while Tab is held. Fine at 12 players, heavy at 80. Cache the column model + server string,
  refresh on tick / player-list revision.
- **MED-30** `NametagStats.kt:40-41, 55-63, 76, 78, 114-117` — leftover `Debug.LOGGER.info(...)`
  ("Temporary: throttled per-name trace so we can see why nothing renders") on the per-nametag render
  path. Spams `latest.log` in any populated lobby. Delete the `lastTrace` map + 5 `info` calls (or
  move to `LOGGER.debug` behind a flag). Minor: `:54/:68` call `System.currentTimeMillis()` twice.
- **MED-31** `features/ScreenTheme.kt` — ~10 unused pre-NanoVG immediate-mode helpers (`st`, `stw`,
  `sst`, `sw`, `pill`, `roundRect`, `disc`, `drawChevron`, `nChevron`, `nDisc`) left after every
  screen migrated to `NvgRecorder`. Delete (keep `panel`, `roundedRect`, `roundedRectRing`,
  `withCoverage`, the `n*` helpers that are still used).
- **MED-32** `CooldownOverlay.kt:282-286` (`parseLevelFromTab`) and `:289-302` (`getLevelFromXp`,
  51-entry XP table) — no callers. Delete both.
- **MED-33** `BossBarFeature.renderHud` (`:21-22`) is an empty `{}` but `FishModInit.kt:1243`
  registers `fishmod:boss_bar_feature` to call it every frame (real work moved to
  `FishBossBarHudMixin`). Remove the `HudElementRegistry` line + the empty method.
- **MED-34** `CooldownOverlay.drawOverlay` (`:380-384`) — comment promises a "200 z-offset to clear
  item shading" but the code does `pushMatrix()` / `translate(0f,0f)` / `popMatrix()` (no-op), so
  cooldown text can render behind item shading in slots. Apply a real Z translate or drop the
  push/pop and fix the comment.
- **MED-35** `PetHud.kt` — dead `TAB_XP_LINE` (`:26`, compiled `Pattern`), dead `getOverflowLevel()`
  (`:300-301`), unused `import java.util.regex.Matcher` (`:18`). `FishHudEditor.resetAll()`
  (`FishHudEditor.kt:188-198`) is also dead (`FishModScreen` uses `resetAllColumns()`).
- **MED-36** `CooldownOverlay.COOLDOWNS` duplicate keys: `SHADOW_FURY` set at `:37` and `:53`,
  `FIRE_FREEZE_STAFF` at `:42` and `:51` (same value both times). Remove the dupes.
- **MED-37** `Dungeons.hidePlayersInRange` / `hidePlayerRange` (`Dungeons.kt:51-53`) duplicate
  `Visual.hidePlayersInRange` / `hidePlayerRange` (`Visual.kt:30-31`) with different defaults; both
  objects are registered in config managers. `Visual` is the live pair (Render Optimizer); delete the
  `Dungeons` copy.

---

## LOW

- **LOW-1 (do first in the comment sweep)** Commented-out code:
  `FishModInit.kt:423` `// SlayerXpTracker.init();`, `:424` `// fishmod.features.SkillTracker.init();`,
  `:473` `// PowderTracker.init();`, `:1352` `// safeInit("PingFeature", …)`.
  Delete these lines (PingFeature is HIGH-6 — resolve that first).
- **LOW-2** `utils/config/components/Components.kt` — `init()` empty, zero `@ConfigValue` fields, yet
  registered in `Config.manager` + `safeInit`. Remove the class, its registration, the `safeInit`.
- **LOW-3** `utils/config/values/Buttons.kt:58-59` — `Buttons.init()` is an empty class-load forcer;
  rename to something explicit (`fun forceLoad() {}`) or doc it so it doesn't look like missing logic.
- **LOW-4** `features/dungeon/FishEstTotal.kt:43,104-108` — `LocalSplit.tick()` is a documented no-op
  but `ON_SERVER_TICK` iterates `currentSplits` calling it every tick. Remove `tick()` + the handler.
- **LOW-5** `utils/dungeon/RunHistory.kt:124-133` — `saveSplitTimes`/`saveSplits` do a synchronous
  Gson serialize + `FileWriter` under `lock` on the dungeon-tick/network thread at run-end. Debounce
  / move off-thread to avoid a stutter on the "☠ Defeated" line.
- **LOW-6** `utils/networth/ItemsDb.kt:106-109` — `ITEMS.clear(); ITEMS.putAll(next)` is a
  non-atomic swap; a concurrent `get()` sees an empty map. `putAll` then remove stale keys, or swap a
  `@Volatile` reference (`SkyblockItems` does it better under `synchronized`).
- **LOW-7** `utils/data/Misc.kt:33-36` — `getDistance()` returns **squared** distance (no `sqrt`);
  `getDistance(e1,e2)` delegates to it. Rename to `getDistanceSq` (grep callers first — some may
  compare against a non-squared threshold and be silently wrong).
- **LOW-8** `utils/MayorApi.kt:49` — `lastFetch = now` is stamped *before* the async result, so a
  fetch/parse failure persists stale/false values for the full 10-min TTL with no earlier retry.
  Stamp on success only, or use a shorter failure TTL (as `CroesusPrices.failStamp()` does).
- **LOW-9** `features/dungeon/DupeClassDetector.kt:59-63` sends `pc Dupe Class Detected …` via
  `Misc.executeCommand` directly, bypassing `utils/ChatQueue` (whose docs say every event-driven
  auto-announcer routes through it for spam-filter safety). Route through `ChatQueue.enqueue`.
- **LOW-10** `EventHandler.kt` — `listeners` is a plain non-synchronized `ArrayList`; `invoke` runs
  listener bodies on the Netty thread for `ON_SERVER_TICK` / `ON_GAME_MESSAGE` / `ON_PACKET` /
  `ON_SOUND`. Registration is init-time only today so no live CME, but back it with
  `CopyOnWriteArrayList` and document the thread contract. `invoke` short-circuits on the first
  `true` — a cancelling listener suppresses the event for all later listeners (matters for
  `ON_GAME_MESSAGE` / `ON_SOUND` ordering).
- **LOW-11** `ChunkMixin.java:16` (`ON_BLOCK_ENTITY` at `LevelChunk#setBlockEntity` HEAD) and
  `PlayerInventoryMixin.java:16` (`ON_SLOT_CHANGE` at `Inventory#setItem` HEAD) build a capturing
  lambda then `EventHandler.invoke` returns immediately (no listeners). Add `fun isEmpty()` to
  `EventHandler` and guard the mixins, or remove the injections until a consumer exists.
- **LOW-12** `utils/Scheduler.kt` — `scheduleScreen(...)` (`:54`) and `scheduleCommand(...)` (`:77`)
  have zero call sites; the `START_CLIENT_TICK` handler still polls `scheduledScreen != null` /
  `scheduledCommand` every tick. Remove both functions, the three fields, the two dead tick branches,
  the `com.mojang.brigadier.Command` import. Keep the `tasks` list.
- **LOW-13** `utils/Addons.kt` — `Addons.fishModAddonsInstalled` has no reference outside its
  declaration; the `FishModInit.kt:1212-1218` JOIN handler resets `DungeonMapSettings`
  unconditionally and never consults it. Delete `Addons.kt` or wire it into the reset/gating it was
  written for. (Also verify the `isModLoaded("fishmodaddons")` id is current.)
- **LOW-14** `utils/InstallHeartbeat.kt:26,34-37` — `report()` runs on every
  `ClientPlayConnectionEvents.JOIN` (every Hypixel server hop) and does an HTTP `reportSeen(...)` +
  re-resolves the mod version each time. Display is gated; the request is not. Gate to once/session
  or throttle; compute `modVersion` once into a `val`.
- **LOW-15** `FishModInit.kt` unused imports: `:19` `PuzzleDisplay`, `:21` `ChatScreenAccessor`,
  `:44` `ClientTickEvents`, `:48` `ScreenKeyboardEvents`. Also `FishModInit.kt:76-83`
  `looksLikeChannelDot` is a dead private fun.
- **LOW-16** `accessors/RenderLayerAccessor.java` — empty non-`@Mixin` interface, its own comment
  says "kept as an empty file so any stale references compile cleanly"; zero references. Not in
  `fishmod.mixins.json`. Delete it.
- **LOW-17** `EntityRendererMixin.java:94-97` — `if (fishmod$ntTrace++ % 120 == 0)` INFO log inside a
  render hook, runs forever in production. Remove it + the `fishmod$ntTrace` field. (Method
  `hideFire` at `:34` also does far more than hide fire — rename to `fishmod$extractRenderState`.)
- **LOW-18** `LightmapMixin.java:27` sets `state.needsUpdate = true` every frame while Full Bright is
  active (a lightmap rebuild + GPU upload per frame). The white state is constant — apply once on
  enable. The `@ModifyVariable` return is also a no-op (mutates the same object ref in place); a
  plain HEAD `@Inject` would read identically.
- **LOW-19** `ChannelMixin.java:28-31` — `AL10.alSourcef(source, AL_MAX_GAIN, …)` (a JNI call) on
  every `setVolume` for every channel even with loud/mono features off. Early-out when
  `volume <= 1.0f` and no loud sound is active.
- **LOW-20** `utils/Keybinds.java` — `wardrobeNextPage` / `wardrobePrevPage` (`:153-163`) are
  registered but never added to `TRACKED`, so they're lost if `options.txt` is regenerated while
  siblings restore. `:318` still prints `"(item rarity display removed)"` on the "Copy item lore"
  key — remove. `checkInputs` (`:301-345`) uses `… ; return;` on null player, aborting the whole
  tick's keybind processing for unrelated binds — use per-block guards.
- **LOW-21** `twitchbridge/TwitchIrcClient.java:124` — `text.startsWith("ACTION ") &&
  text.endsWith("")`: `endsWith("")` is always true (dead clause) and Twitch `/me` arrives as
  `ACTION msg`, so `startsWith("ACTION ")` is false → the CTCP markers print literally.
  Fix: `text.startsWith("ACTION ") && text.endsWith("")` then
  `text.substring(8, text.length() - 1)`.
- **LOW-22** `CreditsScreen.kt:39` — the "22yrs" role string is a broken copy/paste:
  `"the dungeon map: shared it so we could build on it. dungeon map: trusted us with it, dungeon
  map: ported with his blessing"`. User-facing. Rewrite to one clean sentence.
- **LOW-23** `FishHudEditor.kt:165-182` (`COLUMN_HUDS`) references HUD names absent from `DEFAULTS`
  (`:128-162`) — "Spirit Bear", "Blessings", "Current Section", "Device Completed", "Melody
  Warning", "Section Completion", "S4 Alert", "S4 Debug", "M7 Relics" — so scoped "Reset positions"
  silently skips them (`DEFAULTS[e.name()] ?: continue`). Add default entries or document
  best-effort.
- **LOW-24** `item/ItemCustomizeScreen.kt:29-31` KDoc says item-model override is "out of scope / no
  render path" but it's fully wired (`modelField` → `applyModel()` →
  `ItemCustomizationStore.setModelId()` → `ItemModelOverrideMixin`). Only *head-skin* is out of
  scope. Fix the KDoc.
- **LOW-25** `item/ItemCustomizationStore.setAnimatedDye` + `item/AnimatedDyeAnimator` — no writer
  path from the current `ItemCustomizeScreen`; legacy JSON entries still animate (read by
  `DyedItemColorMixin`, ticked by `GameRendererNvgMixin`). Either document "legacy-only" or remove if
  old configs don't matter. `AnimatedDyeAnimator.colorFor` (`:40-43`) can index OOB for a stored dye
  with <2 keyframes — early-return when `keyframes.size < 2`.
- **LOW-26** `chat/ChatNotificationsScreen.kt` — `mkField(panelW, chatFor)` (`:125`) both params
  unused; `drawToggle` `:304` `val hov = false` never read; `mouseClicked` (`:357-364`) re-derives
  field Y offsets with a hand-summed literal `editY + 24 + 24 + 20 + 24 + 14` that must track
  `drawEditor()`. Factor layout into shared constants.
- **LOW-27** `SoulflowHud.kt:3` unused `import fishmod.utils.Constants`.
- **LOW-28** `features/TimeChanger.kt:13-14` — 7 `MODES`, 6 `VALUES`; "Real Time" only works because
  `VALUES.getOrElse(idx){ realTimeTicks() }` also catches `indexOf == -1`. Make the fallback
  explicit.
- **LOW-29** `creeperBeamsSolutions.json` has a duplicate row (`[18,81,21,9,69,3]`); `BeamsSolver`
  keys by `BlockPos` so the second silently overwrites the first with a different palette index.
  Remove the dupe.
- **LOW-30** `DungeonState.kt:96-99` — `witherKeys--` on "opened a WITHER door" can go negative when
  joining mid-run. Clamp at 0.
- **LOW-31** `MapImageLoader.kt:100-120` — reloading a PNG whose name is already in `LOADED`
  overwrites the map entry without `textureManager.release()` on the previous `DynamicTexture` (leak
  on hot-reload). The `WatchService` / `watchLoop` thread is never closed.
- **LOW-32** Map dead code: `Room.floorHeight` (write-only), `Room.realCoord()` /
  `Room.rotationDegrees` / `Room.centerBlock` (`Room.kt:305-335`, no callers),
  `Room.Tile.listIndex` getter, `Prince.princeIndicatorShown()` + `indicatorLatched`,
  `RoomData.crypts` (deserialized, never read), `MapVec2i.roomTilePos()`. `Mimic.kt:31-34` — the
  `@Suppress("UNCHECKED_CAST")` + `as? List<BlockPos>` is redundant (already typed).
- **LOW-33** Odin migration scaffolding with no readers: `ORender.withAlpha` / `ORender.outlinedBox`,
  `ORoomShape` enum + `ORoomData.shape` + `OdinScan.mapShapeName()`, `ORoomData.cores/crypts/secrets/
  trappedChests`, `ORoomComponent.vec2/.blockPos/.core` + `OVec2`, per-constant `(x,z)` params on
  `ORotations`. Strip to what the solvers use (`getRealCoords`, `getRelativeCoords`, `centerPos`,
  `rotationDeg`, `rotation`, `data.name`, `data.type`, `clayPos`, `roomComponents`). Stale KDoc link
  `[OdinScan.updateRotation]` in `OdinScan.kt:67` / `OdinDungeon.kt:67`.
- **LOW-34** `RenderPipelines.kt` — empty class, no references, name shadows
  `net.minecraft.client.renderer.RenderPipelines` (which `RenderLayers.kt` imports). Delete the file.
- **LOW-35** Dead render helpers: `RenderLayers.getOutline(width, depthCheck)` (both params
  `@Suppress("UNUSED_PARAMETER")`, body `= LINE_ND`, no callers); `RenderingEvents.NO_DEPTH_OUTLINE_ENTITY`
  (declared + passed to `drawLayer` but never `.register`ed — iterated every frame for nothing);
  `DrawEvents.HUD_SLOT_AFTER` / `HUD_SLOT_BEFORE` (never registered or invoked);
  `RenderUtils.getStatusColor`, `RenderUtils.drawPrefixedTimer` (both overloads),
  `RenderUtils.drawText(context, component, text, color)` — no callers.
- **LOW-36** `f7/ArrowsDevice.kt:61-63,107-113` — `inP3()` falls back to `player.y in 100.0..156.0`,
  so the 9-`getBlockState` seed loop runs every client tick in that Y band in *any* world. Gate on
  `Location.inDungeon()`.
- **LOW-37** `PartyCommandHandler.java:581` — comment says "5 daily-bonus runs at +50%" but code uses
  `1.4` (+40%); the parallel `.crtc` path at `:642` correctly says +40%. Fix the comment.
- **LOW-38** `cosmetic/RemoteSync.kt:97` — `fetchSync(...) { ver, nicks, items, scales -> }` never
  uses `items`.

---

## SPECULATIVE (rewrites — quality, not defects; apply only with care, don't change behaviour)

- **SPEC-1** Collapse `RenderHandler` / `GizmoHandler` / `DrawHandler<T>` (three near-identical
  `ArrayList`+`register`+`invoke(Consumer<T>)` classes) into one generic `DrawHandler<T>` with
  `size()`; alias the other two. Swap `java.util.function.Consumer` params for Kotlin function types
  to drop a SAM allocation per call site.
- **SPEC-2** `NvgRecorder` is a global `object` with one shared `commands` list — works only because
  exactly one `HasNvgOverlay` screen is open at a time. It also rebuilds a per-shape/per-text
  `Runnable` list every frame (hundreds for a full settings page) and `NVGPaint.calloc()/free()`s per
  gradient/shadow op during replay. A retained-mode typed command buffer + a reusable `NVGPaint` pool
  would cut per-frame churn (more code).
- **SPEC-3** `RenderUtils.renderText` allocates a `Vector3f` + `Quaternionf` per world-text label per
  frame (`:261,264`); `RenderUtils.toFloats` returns a fresh `FloatArray(4)` per call inside
  per-frame NO_DEPTH handlers. Hoist scratch instances / add write-into-caller-array overloads.
- **SPEC-4** Unify the item-DB layer: `SkyblockItems.initAsync()` and `networth/ItemsDb.fetch()` both
  GET the full `/v2/resources/skyblock/items` payload with separate `HttpClient`s and caches
  (`SkyblockItems` never persists → re-fetches every launch). Build `SkyblockItems`' index from
  `ItemsDb`'s already-parsed, disk-cached array. One client, one cache, one refresh.
- **SPEC-5** Four scoreboard-adjacent features (`CatacombsOverflowOverlay`, `SkillLevels`,
  `BestiaryProgress`, `CollectionsProgress`) each register their own `END_CLIENT_TICK` handler
  polling `HypixelApi.getLocalMember` on a 60 s timer — four polls of the same endpoint. Share one
  60 s member fetch and fan out.
- **SPEC-6** `ExplosiveShot` and `CritTracker` register separate `ON_GAME_MESSAGE` handlers with an
  identical "Your Explosive Shot hit …" regex + parse. Parse once, hand both the numbers.
- **SPEC-7** `FishModInit.onInitialize()` is ~1350 lines (whole Brigadier tree + all HUD
  registrations + framework bootstrap inline). Split into `registerCommands(dispatcher)`,
  `registerHuds()`, `bootstrapFramework()`. The `for (name in arrayOf(...))` command loops
  (`:1120-1192`) are already the right pattern.
- **SPEC-8** `utils/MathParser.kt` (233-line hand-rolled shunting-yard, stringly-typed tokens, used
  only by the search bar) could be a ~40-line recursive-descent parser. Low stakes; current code
  works — leave unless touching it anyway.
- **SPEC-9** `EntityRendererMixin.java:34` `hideFire` does nametag hide + per-player scale stash +
  class-name hide + fire hide + nick Y-offset + stat resolution in one method. Split for readability
  (behaviour-equivalent).
- **SPEC-10** `RecipeBookWidgetMixin` has three handlers all named `render` (`:16,23,31`) — rename
  (`hideRender` / `hideIsVisible` / `hideSetVisible`).
- **SPEC-11** `ItemRarityHotbar.getRarity` (`:68-75`) uses `ItemRarity.valueOf(word)` in try/catch
  per lore token (result is cached per stack, so runs once) — a `when`/map lookup avoids the throw.
- **SPEC-12** `TicTacToeSolver.kt:90` / `TPMazeSolver.kt:30` rely on exact `Double` `% 0.5 == 0.0`
  (ported verbatim; works because coords are grid-aligned). An epsilon check
  (`abs(x % 0.5) < 1e-4`) is more robust.

---

## Comment-removal — high-value targets

The full per-file `//` inventories run to several hundred lines (produced during the audit, not
reproduced here). Priorities:

1. **Commented-out code** — `FishModInit.kt:423,424,473,1352` (see LOW-1). Also
   `accessors/RenderLayerAccessor.java:3-4`, `RenderPipelines.kt:3-4`.
2. **`// ===== Section =====` / `// ── divider ──` banners** — heavy in `FishModScreen.kt`
   (`:101,353,618,693,803,961,1152,1238,1260,1273`, etc.), `FishModInit.kt`
   (`:683,740,1031,1238,1260,1273`), the F7/terminal files (`ArrowsDevice`, `WitherDragons`,
   `TermSimScreen`, `TerminalHandlers`, `TerminalSolver`, `GateDisplay`, `TermCustomGui`,
   `SimonSaysSolver`), `NvgRecorder.kt:48,196,221`, `RenderUtils.kt:36-39,315-340`, screen files'
   `// ── NanoVG overlay ──`.
3. **Explanatory "why" prose blocks** — the bulk. Present throughout `RenderUtils.kt`,
   `NvgGlStateGuard.kt`, `DrawContextMixin.java`, `EntityRendererMixin.java`, `Scan.kt`,
   `DungeonMap.kt`, `DoorHighlight.kt`, `SecretClicked.kt`, `CooldownOverlay.kt`, `PetHud.kt`,
   `CompactTab.kt`, `FishModScreen.kt`, the `f7/` package, `HypixelApi.java`, etc.
4. **Restate-the-code one-liners** — `MathParser.kt:81`, `Split.kt:162`, `RunHistory.kt:42,62,64`,
   `OverflowPetLevels.kt:40` (`// safety`), `MayorApi.kt:17,49`, many in `PetHud.kt` /
   `CooldownOverlay.kt`.

**Keep (credit / identity):** anything naming Odin / NoammAddons / Skytils / System22 /
blade-addons / "ported from" / "1:1 with" / author handles / the `DevOnly.kt:11` UUID label /
`NwConstants.kt` `// ---- <source>.js ----` provenance banners (requester's call on those last ones).

Recommended approach: scripted strip of full-line and trailing `//` comments in the module, then
review the diff and restore the credit lines above. Do it as its own commit, separate from the
behavioural fixes, so a regression is easy to bisect.

---

## Sanity checks that passed (no action needed)

- Puzzle-solver migration (flat `puzzles/*` → `puzzles/odin/*`) is clean: no dangling refs to the 9
  deleted classes, no duplicate event registration, all config toggles wired both directions, all 6
  solver JSONs (`iceFillFloors`, `odin_rooms`, `boulderSolutions`, `creeperBeamsSolutions`,
  `quizAnswers`, `waterSolutions`) load and match their readers.
- `rooms.json` loads correctly via `RoomData.loadRoomData()`; `type`/`shape` values all map to
  `Room.Type`/`Room.Shape` (incl. `RARE`).
- Mixin ↔ `fishmod.mixins.json`: every listed class exists on disk; every mixin file is listed
  (except the dead `RenderLayerAccessor`, LOW-16); the duplicate/placement issues are HIGH-1.
- `MapReader` compiles (`GridPos` comes from `:common`) — it's dead, not broken (MED-19b).
- `safeInit(...)` in `FishModInit` is used (framework bootstrap, `:1336-1350`) — not dead.
