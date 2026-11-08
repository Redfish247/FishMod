# How I Created Hypixel SkyBlock's Greatest Mod

Target length: 8-10 min. Voiceover (VO) is Eli. Lines in [brackets] are for Eli to fill in with his own story.
Clip IDs (C01...) are what Eli records. Animation IDs (A01...) are what Claude builds.

Real numbers used below (check before recording; refresh with `git rev-list --count HEAD` etc.):
- First commit: 9 April 2026 (FishMod), FishModDungeons repo from 11 July 2026
- 1,682 commits (1,640 + 42 in the old repo), ~146,000 lines of code, 1,955 settings
- 30+ feature areas: dungeons, F7, Kuudra, farming, mining, Rift, Diana, slayer, cosmetics, profile viewer

---

## 0. Cold open (0:00-0:30)

| VO | Visual |
|---|---|
| "Seven months ago, this mod didn't exist." | C01: slow pan over the /fm settings menu, fully loaded. Hard cut to black. |
| "Today it has terminal solvers, a live dungeon map, a full profile viewer, achievements, cosmetics..." | Fast montage, 0.5s per clip: C03, C17, C20, C21, C07, C19, C22. Music hits. |
| "...and over a hundred and forty thousand lines of code." | A01: line counter ticking 0 to 146,000 over a scrolling code wall. |
| "This is how I built it." | Title card A02: "HOW I CREATED HYPIXEL SKYBLOCK'S GREATEST MOD". |

## 1. Why I started (0:30-1:30)

| VO | Visual |
|---|---|
| "I was grinding dungeons every day, and with five mods stacked on top of each other, my FPS was tanking." | Background: Eli's dungeon runs (recording 2026-11-07 18-42-05, 0:40-4:00), desaturated and slowed; zoom on the FPS counter. |
| "I was running five different mods, each with its own menu, its own config, its own bugs." | A03: five mod windows stacking up and glitching, then collapsing into one FishMod logo. |
| "So I thought, what if one mod just did all of it?" | A04: logo reveal (reuse trailer logo scene). |

## 2. Day one (1:30-2:30)

| VO | Visual |
|---|---|
| "The first commit was on April 9th. Party Finder stats, a few dungeon features and splits." | A05: GitHub-style commit card animating in: "FishMod initial commit, 9 Apr 2026". |
| "[What the first version looked like, how many people used it.]" | C10: oldest build you still have, or a screenshot of the first Modrinth page. |
| "It was rough." | C11: a funny early bug clip or screenshot if you have one. |

## 3. How it's actually built (2:30-4:00)

| VO | Visual |
|---|---|
| "Here's the part people don't expect. I build most of this with AI." | C12: screen recording of Claude Code sessions running side by side. |
| "Not one prompt and done. Dozens of sessions, each working on one feature, all at once." | A06: animated diagram, one "lead" node fanning out into feat / bug / review sessions. |
| "I test everything in game, report what's broken, and it goes back for another pass." | C13: you testing a feature, then a bug report on the board. |
| "[Your take: what AI is good at, what it's bad at, what you still do yourself.]" | C14: you talking to camera or over gameplay. |
| "And a lot of features stand on the shoulders of other mods, Odin, NoammAddons, SkyHanni, BetterPV, SkyblockOverhaul. Credit where it's due." | A07: credit cards for each upstream mod. |

## 4. The features (4:00-6:30)

One mini section per area: 15-25s each. Format: section title (A08, reuse trailer section cards), then real clip, then one VO line on why it matters.

| Area | VO hook | Clip |
|---|---|---|
| Secrets | "Secret tracking that tells you the second a room is done." | C15 (C16b_secrets_done) |
| Solvers | "Every terminal and puzzle, solved on screen." | C03, C16 |
| Dungeon map | "A full live map with room colours and player heads." | C17 |
| Item customization | "Recolour, rename and remodel your items, and see other FishMod users' items too." | C07 |
| Farming | "Visitors and pests tracked, Jacob's contest on your HUD." | C19, C19b |
| Profile viewer | "Look up anyone's profile without leaving the game." | C20 |
| Achievements | "A full achievement system with leaderboards." | C21 |
| Customization | "And every single HUD, colour and text is yours to change." | C22 (HUD editor) |

## 5. The hard parts (6:30-8:00)

| VO | Visual |
|---|---|
| "[Worst bug you hit. Crashes, a feature that broke on a Hypixel update, a version port.]" | C23: Storm Giga Lightning death for 999,999,999 (funny beat), then A09 red "CRASH" glitch overlay. |
| "Minecraft updated twice while I was building this. Every feature has to work on both versions." | A10: version split screen, 26.1.2 vs 26.2, checkmarks appearing. |
| "And it all has to be safe. No scripted input. One key press, one action." | A11: "NO MACROS" shield graphic. |

## 6. Where it is now (8:00-9:00)

| VO | Visual |
|---|---|
| "[Downloads / Discord members / testers number.]" | A12: animated stat counters (downloads, commits, features). |
| "[Personal milestone line, e.g. 100k secrets.]" | C26: 99,999 to 100k secrets. |
| "New features land every week, and you can see what's coming on the public roadmap." | C24: scroll through fishmod.dev roadmap. |
| "[Thank the testers / community.]" | C25: Discord screenshots (blur usernames). |

## 7. Outro (9:00-9:30)

| VO | Visual |
|---|---|
| "FishMod is free and open source. Link's in the description." | A13: reuse trailer outro (Modrinth, fishmod.dev, Discord). |
| "[Sign-off line.]" | End screen with 2 video slots + subscribe (20s, YouTube end-screen safe layout). |

---

## Shot list for Eli

Record at 1920x1080, 60fps if possible, game GUI scale consistent across clips. Leave 1-2s of padding before and after each action. Name files by clip ID (`C15_dragons.mp4`) and drop them in `promo-video/greatest-mod/clips/`.

| ID | What | Length |
|---|---|---|
| C01 | /fm menu, slow scroll through categories | 10s |
| C02-C08 | Montage: taken from C03, C17, C20, C21, C07, C19, C22 (no new recording) | 3s each |
| C09 | Dropped: uses C17 desaturated | - |
| C10 | Earliest build / first Modrinth page (screenshot is fine) | still |
| C11 | Funny early bug (optional) | any |
| C12 | Claude Code sessions on screen | 15s |
| C13 | Testing a feature + the test board | 15s |
| C14 | Talking head or VO gameplay (optional) | any |
| C15 | Secrets done in a dungeon room (already cut: C16b_secrets_done) | 10s |
| C16 | Terminal + puzzle solvers | 15s |
| C17 | Dungeon map during a run | 10s |
| C18 | Kuudra dropped (no footage) | - |
| C19 | Garden: visitors, pests HUD | 10s |
| C20 | Profile viewer on a player | 10s |
| C21 | Achievements menu | 10s |
| C22 | HUD editor: drag, scale, recolour | 15s |
| C23 | A bug / crash (optional) | any |
| C24 | fishmod.dev roadmap scroll | 10s |
| C25 | Discord screenshots (usernames blurred) | stills |
| VO | Full voiceover as one WAV or per-section files | - |

## How the edit works

- Premiere Pro 2026 is installed (no After Effects or Blender found). Claude builds the animations (A01-A13) as transparent ProRes 4444 `.mov` files plus a Premiere-importable XML timeline with clips and VO laid out per section. Eli opens it in Premiere for final tweaks.
- Blender is optional: install it and Claude can script 3D title shots (spinning logo, camera fly-through of a code wall) with `bpy` and render headless.
- Music: pick a royalty-free track (YouTube Audio Library or similar) and drop it in as `music.mp3`.

Extra clips from old recordings: C26 100k secrets (FishMod chat line visible). C15/C15b/C23/C27 are June M7 runs with Odin running (its chat lines are visible), so use them only as B-roll or the funny death, never as a FishMod feature shot. More candidates in `footage-finds/`.
