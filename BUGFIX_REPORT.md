# Bugfix report 2026-11-10

Commit before start: 8093dfcc (Ship 1.7.69). Working tree was clean, nothing to pre-commit.
Fix commits: dd989f2d (#530), 8e1524a7 (Spotify), then the 1.7.70 bump (c671a7af).
Built `:dungeons-26.1.2:build :dungeons-26.2:build`: OK after every fix and at the end (only warnings: two unchecked casts in `Api2ParsersTest.kt`, test code). 26.3 not built (opt-in, not touched). Not tested in game.

## Fixed
- **fishmod.dev #530** `DungeonRoomAnchor.kt` + `DungeonWaypoints.kt`: room waypoints were snapped to the block centre and kept world-frame half sizes, so non-full-block boxes drifted and were stretched the wrong way in rooms with another rotation. New `pointToLocal`/`pointToWorld` keep the sub-block offset (rotated around the block centre with the room) and X/Z extents swap on WEST/EAST rotations. Old waypoints (stored at block centres) load unchanged; an old waypoint with unequal X/Z size placed in a WEST/EAST room will now be drawn swapped (it was only right in that one orientation before).
- `SpotifyLyricsHud.kt`: `draw()` called `start()` every frame while `proc` was still null, and `proc` is only set after `ProcessBuilder.start()` returns on the reader thread, so each enable spawned several `powershell` loops. The untracked ones were never destroyed (toggle off, shutdown hook), so they outlived the game. On non-Windows a failing thread was spawned every frame. Now: a `running` flag set before the thread starts, Windows only, 10 s retry backoff, helper destroyed if the toggle went off while starting.

## Roadmap cards moved to Testing (15)
- 530 (this run).
- Already fixed by earlier commits but still "planned" on the board: 499 (277637d5), 498 (652d5913), 492/493/522/528/529 (62438956), 490 (61c369ac), 485/509 (66fff42a), 520 (7888466b), 521 (shipped in 1.7.66), 523 (49e7a290 removed the PlayersLeaped.1 report), 524 (90d52225 restarts SS on a 6th lantern instead of reporting). All shipped in 1.7.66 or earlier.

## Not fixed
- **#459** (in_progress) players far from spawn look stuck/glitched, "also with only FishMod". Checked FishMod's entity/packet mixins (`FishPacketHandlerMixin`, `FishEntityMixin`, render-state mixins): none touches entity position, movement or interpolation. Needs a repro (video + `/fm debug` report, or a test with FishMod removed) before anything can be changed. Left in progress.
- The 23 Bug cards already in Testing were not re-checked one by one (fixed by earlier runs; nothing new reported against them).

## Skipped / decisions
- Deploy target: the prompt's Modrinth path `...\ModrinthApp\profiles\Skyblock 1.21.8\mods` does not exist. Deployed `FishModDungeons-1.7.70-26.2.jar` to the Prism `Skyblock` instance instead (CLAUDE.md deploy target, which ran 1.7.69-26.2), after `check-game-closed.ps1` said closed; removed 1.7.69. FishTest was not updated. The daily-bugfix prompt should get the Prism path.
- Code sweep: checked `!!` on player/level (all guarded by earlier null checks), command arg indexing (all size-checked), sync HTTP calls (all on worker threads), process spawning. No other real bugs found in what was read; the codebase was not read file by file.

## Test in game
- Dungeon waypoints: place a waypoint with "use block size" on a slab/button/lever in a room, then check it in a later run where the same room is rotated differently. Also check an older full-block waypoint still sits right.
- Spotify HUD: toggle on and off a few times; Task Manager should show at most one helper `powershell` and none after the game closes.

## Notes for Obsidian
- Dungeon Waypoints page: the "block-granular on purpose" note is now outdated. Sub-block points go through `DungeonRoomAnchor.pointToLocal/pointToWorld` (block via the lattice formula, offset from the block centre rotated by the same rotation), and box X/Z half sizes are stored in the canonical north frame and swapped with `swapsAxes()` on WEST/EAST rooms.
- Lessons: a per-frame "start if handle is null" with the handle set on another thread starts the job many times. Set a flag synchronously before handing off, and never leave a spawned OS process untracked.
- Board: `roadmap-status` needs numeric `id`s; string ids return `updated: 0` with `success: true`.

---

# Bugfix report 2026-11-09 (run 2)

Commit before start: 71393b82. Fix commit: eb7c1ea3. Built (26.1.2 + 26.2): OK. Not tested in game.

## Fixed
- SoundManager.kt: sound names case/space tolerant (card 501).
- LootIcons.kt: SIGN -> oak_sign (516).
- CroesusRewardParser.kt: upper-snake id fallback when name map misses (491).
- FishDiag.kt: connection reset / "not processed by peer" / localized WinSock IOExceptions no longer reported (495, 496, 497, 504, 506, 507, 508; 470 disk-full was already filtered).
- PvClient.kt: reload-listener registration wrapped so PV init continues (494).
- Pests.kt: remembered plots only drawn while the sidebar shows live pests (502).
- ExoticTooltip.kt: Seymour pieces skipped (510).
- Already fixed earlier, verified in code: 503 (Lotus Atoll in enum), 505 (null JSON handled), 511/512/514/515 (Spotify, commit 52d9d64b).

## Not fixed
- 492, 493, 522 dragon diag (needs Hypixel coords/logs); 523 PlayersLeaped; 524 SS 6 lanterns; 485, 509 rooms.json missing cores (data); 498 informational.
- 490 chameleon shard (needs decision on which shards are non-siphonable); 513 button outline (design call, border is deliberate); 520, 521 (feature-like); 499 (behavior change); 459 stuck players (cannot reproduce, happens with only FishMod; likely entity interpolation, needs a repro).
- Suggestions 519, 500, 486, 468, 91, 375, 517 not touched.

## Skipped
- Jar copy: `Skyblock 1.21.8\mods` in ModrinthApp does not exist (profile is 1.21.8, wrong version for 26.x jars). No javaw running. Nothing copied.

## Test in game
- Garden: kill all pests, plot boxes disappear. Sound setting typed "Ping". Seymour armor tooltip. /pv opens.

## Notes for Obsidian
- FishDiag.isTransientNetwork now matches stack frames (sun.nio.ch.Socket*, jdk.internal.net.http) because localized WinSock messages can't be matched by text.
- Pests tab list is authoritative; chat memory only valid while sidebar pest count > 0.
- Roadmap API ids are ints; status POST needs ints, strings update 0 cards.
- ModrinthApp Skyblock 1.21.8 mods folder no longer exists; update prompt.md deploy target.
