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
