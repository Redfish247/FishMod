# Footage finds: replay buffers + long recordings

Scanned: all 95 `Replay 2026-*.mp4` (coarse 60s sampling) and 9 long `2026-*.mp4` files >15 min dated 2026-07-01+ (3 min sampling). Two long files skipped: `2026-08-10 17-57-35.mp4` (0 s) and `2026-09-02 21-15-58.mp4` (corrupt, no moov atom).
Source folder: `C:\Users\Eli\Videos\`. All timestamps are `h:mm:ss` or `m:ss` inside that file. Sampling is coarse, so open the file and scrub +/- 1 min around each pick. Script sections follow `SCRIPT.md` (4 = features, 5 = hard parts, 6 = where it is now, 0 = cold open).

Low-res contact sheets only; "what it shows" is what was visible at the sample point, not frame-perfect.

## Best picks first

| # | File | Start-end | What it shows | Fits script section |
|---|------|-----------|---------------|---------------------|
| ~~1~~ | NOT FISHMOD (IQ mod HUDs, per Eli), do not use: `2026-08-02 13-00-24.mp4` (long, 1h57) | 1:15:00-1:37:00 (best 1:25-1:32) | **Kuudra run** with FishMod Kuudra Splits HUD (Supplies/Build/Eaten/Stun/DPS), Supply Times + Fresh Times lists, "SUPPLIES HOOK CLOSER" supply helper, "DT requested" auto-requeue msg, boss fight start (Kuudra Health). Confirmed at full res 1:16:00 and 1:18:00 | 4 (Kuudra) |
| 2 | `2026-08-25 15-47-18.mp4` (long, 5h52) | 0:03-0:12 (Treasure [1/2], Mob [0/2]), 1:06 (INQUISITOR spawn), 1:33-1:39 (Treasure + "Warp Castle [113m]" label), 3:24, 4:33-4:36, 5:06-5:09 | **Diana**: burrow guess boxes (Start / Guess / Mob / Treasure highlights), distance labels, warp suggestion labels (Warp Castle/Wizard/Hub/Crypt), Diana Stats HUD top right, damage numbers on Empyrean mobs. Hours of it, easy to pick the cleanest 30 s | 4 (Diana) |
| 3 | `Replay 2026-08-25 23-49-17.mp4` / `23-56-09` / `23-57-27` (10 s each) | whole clips | Ready-made short **Diana** clips: guess marker + distance, red Mob box with big damage numbers, green Start box, Diana Loot/Stats HUD at left. `23-49-17` @0:00-0:08 shows "Guess [98m]" purple line to the burrow | 4 (Diana), B-roll |
| 4 | `2026-10-22 17-31-43.mp4` (long, 2h31) | 0:00-0:45 (best 0:30-0:42) | **Diana** newest build: "Guess" purple box at 0:33, Mob / Treasure [1/2] boxes, Diana Stats + loot HUD. Rest of file is idle/server list, ignore after 0:48 | 4 (Diana), 6 (current state) |
| 5 | `Replay 2026-07-20 20-20-41.mp4` (10 min) | 0:00-9:00; dragons 0:00-1:00 and 8:00, terminals 5:00-6:00, chest 9:00 | **Full M7 run**: P5 dragon timers ("2200 Orange"/"1700"), "Terminal (4/7)", lever + device solvers, profit/reward chest screen at 9:00 (Croesus-style "TREASURES CLICK" + loot list) | 4 (F7/M7), chest profit screen |
| 6 | `Replay 2026-10-20 22-28-37.mp4` (414 s) | 0:00-6:50; dragon "ORANGE 1400ms" at 6:00, "Terminal (0/8)" + "[309] Golden Dragon" at 5:00 | **M7 run** with PB Pace splits HUD (Blood Open, Portal, Maxor, Storm, Goldor, Necron vs PB), terminal counter, dragon spawn timer. Clean recent footage (late Oct build) | 4 (M7), 6 |
| 7 | `Replay 2026-09-02 00-11-30.mp4` .. `Replay 2026-09-06 17-32-26.mp4` (5 min each; also `09-02 21-15-48`, `09-02 22-06-14`, `09-03 00-31-00`, `09-03 19-53-12`) | e.g. `09-02 00-11-30` @2:00 ("Lever (1/7)" + terminal GUI solver), `09-02 21-15-48` @2:00 "Gate Destroyed (3/7)" and @3:00 dragon, `09-02 22-06-14` @4:00 "Casted Ragnarock" at Necron | M7 5-min highlight clips: PB-Pace split timers every shot, terminal solver GUIs (number/rubix/select), ghost/revive. Good for "terminals" montage | 4 (terminals) |
| 8 | `Replay 2026-10-07 01-02-58.mp4` (313 s) | 0:00-4:30 (static) | P5 dragon with huge "BLUE" title and **dragon timer "3950ms"**, PB Pace "Necron +x vs PB" HUD, split list. Frame barely moves, so it works as a locked-off hero shot | 4 (dragon timers) |
| 9 | `2026-10-24 19-40-34.mp4` (long, 4h26) | 0:09-0:30, 0:51-0:57 (Zombie slayer: Spawned by RedFish2471, Crypt Ghoul, 4.7M-5.0M damage numbers); 2:27 and 2:45-2:48 in the same file (Revenant Horror "SLAYER BOSS" + Boss Time panel) | **Slayer bosses with timer/Boss Time HUD**, slayer profit/RNG panel, "Spawned by" nametag. Also 0:03 M7 Blood Open split, 0:42-0:48 terminal GUI | 4 (slayer) |
| 10 | `Replay 2026-07-26 18-02-58.mp4` (10 min) and `Replay 2026-07-28 17-50-25.mp4` (10 min) | `07-26` @0:00 boss damage, 1:00-2:00 map + "270 Score!", 4:00 "Terminal (5/7)"; `07-28` @0:00-3:00 "Terminal (4/7)", "Gate Destroyed", terminal GUI overlays | F7 runs with dungeon map HUD, score popup, terminal GUIs | 4 (F7, terminals) |

## More by topic

### Kuudra
- Only Kuudra found: item 1 (`2026-08-02 13-00-24.mp4` @1:15:00-1:37:00). Contact sheet showed "Sweaty Times"/splits panel from 1:15, "SUPPLIES CLICK TO PICK UP" @1:27:00, Kuudra fight @1:30-1:36. No chest profit screen seen in that window; check 1:37-1:42 (after the fight) for the profit/reward chest GUI. A second Kuudra run may be in the same file after 1:57 (file is 1h57 long, but 1:57 shows a different map).
- No Kuudra in any replay buffer.

### F7 / M7 (dragons, timers, terminals, PB Pace)
| File | Timestamp | Shows |
|------|-----------|-------|
| `Replay 2026-08-02 01-17-10.mp4` | 0:00 "Terminal (8/8)", 2:00 dragon 4650ms, 7:00 "Terminal (2/8)" | M7 run, dragon timer + terminal GUI |
| `Replay 2026-08-02 01-24-48.mp4` | 0:00 Lever (5/7) + terminal; 6:00 "Core entrance is opening"; 7:00 dragon "4150" | M7 P3 to P5 |
| `Replay 2026-08-02 01-30-32.mp4` | 0:00 "Device (4/7)"; 6:00 "Device (7/7)" | M7 devices |
| `Replay 2026-08-07 00-17-55.mp4` | 0:00-9:00 | Dungeon run, secrets map, spirit/bonzo/phoenix HUD |
| `Replay 2026-09-25 23-38-53.mp4` | 0:00 dragon "2300ms" (Necron pace), 4:00 "activated a lever! (?/8)" | M7 P5 + P3 |
| `Replay 2026-10-06 17-22-45.mp4` | 0:00 and 4:00 terminal solver overlays (spirit/bonzo/phoenix times), 3:00 Maxor split | M7 clip |
| `Replay 2026-10-09 21-57-05.mp4` | 5:00 "[309] Golden Dragon" + terminal (0/8), 6:00 "ORANGE 1400ms" | M7 dragons, same run as item 6 neighbour |
| `Replay 2026-10-20 22-36-02.mp4` | 5:00 "Terminal (3/7)", 6:00 boss health "30021" | M7 |
| `Replay 2026-10-20 23-12-21.mp4` | 1:00 "SHOOT LB!" Maxor, 5:00 Goldor | M7 |
| `Replay 2026-04-24 00-24-48.mp4` | 0:00-9:00 | Older (April) Catacombs run, early HUDs (map, terminals list) |
| `Replay 2026-04-23 16-58-52.mp4`, `04-23 23-01-52` | spirit/bonzo, Section Done!, "BEING REVIVED" | April dungeon, pre-PB-pace HUD (shows how far the mod has come) |
| `Replay 2026-10-08 19-50-56.mp4` (long, 52 min) | 0:03-0:45; dragon 1100ms @0:42, terminal solvers @0:27 (Lever 5/7), class select tiles @0:21 | M7 full run, good clean HUD |
| `2026-10-26 21-21-02.mp4` (long, 59 min) | 0:00 "GREEN 2550ms" dragon timer, 0:42 "SKYBLOCK S rank score screen (Skill 93, Explor 93, Speed 100, Bonus 10, 296)", 0:45 terminal | M7 end-of-run score screen |

### Terminals only (GUI solvers)
`Replay 2026-09-02 00-11-30` @2:00 (number solver 1/7), `Replay 2026-09-02 22-06-14` @2:00 "Terminal (3/7)", `Replay 2026-09-03 19-53-12` @3:00 (select-all terminal), `Replay 2026-10-07 23-28-59` (n/a), `Replay 2026-09-28 20-12-57` @0:00 and @4:00 (colored number terminals), `Replay 2026-07-28 17-50-25` @1:00 / 2:00 (number terminal 12/13/14), `Replay 2026-10-06 20-12-36` @0:00-0:10 ("Leven (0/7)" practice-mode P3 terminals).

### Diana (more)
`Replay 2026-09-30 00-29-38` (Treasure box, Diana loot panel), `Replay 2026-09-30 01-00-59` ("Griffin" label), `Replay 2026-10-01 16-52-06`..`18-59-00` (Inquisitor kill + Chimera drop @`10-01 18-59-00` 0:04, ender chest GUI), `Replay 2026-10-04 14-06-45`/`14-39-54`/`19-45-35` (Inquisitor fights, "Loadout equipped"), `Replay 2026-10-02 *`, `10-03 *` are black/empty. `2026-10-01 15-41-13.mp4` (long, 1h01): 0:27 "Warp Castle", 0:30-0:51 "Mob"/"Guess" labels, 0:36 Empyrean Sphinx, 0:48 "Warp Crypt". `2026-08-26 15-39-54.mp4` (long, 1h24): Diana with guesses 0:03-0:21, "Warp Wizard" 0:51, INQUISITOR 0:57, Chimera I drop 0:30.

### Slayers
`Replay 2026-07-23 00-37-09` @1:00-9:00: Spider/Tarantula slayer with **slayer profit HUD** (Flaming Spider Shard, session profit, profit per hour), 7881 damage numbers. `2026-10-24 19-40-34` see item 9. `Replay 2026-05-04 20-31-43` @0:00-5:00: older Tarantula slayer in Spider's Den.

### Garden
`Replay 2026-05-26 01-29-20` (farming, 10 min): **Pest HUD** "Last pest spawned / Pest Cooldown", "6th Visitor Ready", "6 Pests Spawned in 23!", Jacob's contest, farming profit panel. `Replay 2026-07-26 15-17-10` .. `15-38-18` (6 clips): **Composter preview GUI** ("Preview Nothing / Display Hour / Time per compost"), FishMod config screen (composter settings) at `07-26 15-17-10` @7:00, bazaar price overlay on the composter menu. `Replay 2026-07-29 17-01-37` @0:00 Farming tracker HUD. `2026-09-23 21-22-16.mp4` (long): garden visitors/AFK area around 0:12.

### Mining
`Replay 2026-05-24 13-04-06`/`16-26-07`/`21-34-54`: Glacite Mineshaft with **Mining Profit / Powder Tracker / Pity counter** HUDs. `Replay 2026-07-22 18-50-52` and `19-56-51`/`21-37-13`: **Mineshaft Corpse Profit Tracker** (loot list at top right), "MINESHAFT! portal spawned" title @`07-22 21-37-13` 7:00, Pity Counter. Clean mining HUD footage.

### GUIs / quality-of-life (good for "features" B-roll)
- `Replay 2026-09-25 21-48-57.mp4` @0:00 and @2:00: **Item Customize** GUI (color picker, custom name, "Apply / Done"). `Replay 2026-09-28 20-12-57` @2:00 same GUI.
- `Replay 2026-09-26 00-49-13.mp4` @0:00-3:00: item browser / profile viewer GUI (stats, skills, essence).
- `Replay 2026-10-07 23-28-59.mp4` @1:00-2:00: **Container value** overlay on ender chests/backpacks. `Replay 2026-08-02 01-58-09` @9:00: same on ender chest.
- `Replay 2026-08-02 01-40-14` @0:00: chest loot values.
- `2026-10-24 19-40-34.mp4` @4:00-4:03 (long file, "q01" sample), `2026-10-22 17-31-43` @1:48: **config GUI** (dark category list on the left, search box at bottom). `2026-11-07 18-42-05.mp4` (long, 20 min) @0:09-0:12: newest config GUI sliders/categories. `2026-10-26 21-21-02.mp4` @0:57: "Commands" key-binding style GUI page.
- `Replay 2026-07-26 18-02-58` .. `07-29 21-48-09` @various: private island dummy ("Dummy 10M") damage testing, Loadout 9 popup (loadouts feature).

### Cinematic / B-roll (hub, islands)
Not much clean scenery: Eli's footage is HUD-heavy.
- `Replay 2026-07-25 01-02-24.mp4` @8:00-9:00: hub **Dungeon Hub** entrance plaza with players (HUD visible, no tabs).
- `Replay 2026-07-29 21-48-09.mp4` @0:00-4:00: Crimson Isle / Burning Desert flyover (mod hitboxes visible).
- `Replay 2026-10-07 01-02-58`/`2026-10-26 21-21-02` @2:00-4:00: guest island garden (pink/colorful greenhouse), SwimClass guest island.
- `2026-09-23 21-22-16.mp4`, `Replay 2026-05-24 13-04-06`: Glacite Mineshaft tunnels, crystals.
- Title-screen panorama loop: `Replay 2026-07-25 01-02-24` 0:00-4:00 (MC 1.21.11 title screen over a night ocean): usable only as a "launching the game" shot.

## Files with nothing usable
Black/empty: `Replay 2026-10-02 16-36-09`, `10-02 16-36-23` (black). `2026-10-22 17-31-43.mp4` 0:51-1:45 is the Minecraft multiplayer menu. `2026-11-07 18-42-05` ends on the Windows lock screen at 0:15 (sheet label).
Not reviewed in detail (sheets generated, skimmed or skipped for time): `Replay 2026-08-06 23-48-48`, `08-07 00-41-30`, `08-07 00-53-02`, `09-06 17-49-46`, `09-08 02-55-15`, `09-20 22-43-13`, `09-21 00-06-58`, `09-22 *`, `09-24 21-59-30`, `09-25 21-48-58`, `09-25 23-38-54`, `09-28 19-58-39`, `10-21 17-33-38`, `Replay 2026-10-02/03/04` shorts beyond the ones listed. Most are likely more M7/Diana of the same kind.

## Caveats
- HUD-readability: sheets were 320-480 px per tile, so exact on-screen text for HUDs was not always legible. Items 1 and 5 were the most checked (item 1 at full res).
- Item 2 file is 5h52, timestamps are approximate to +/- 3 min (3 min sampling).
- Several Diana clips are in the `Replay 2026-09-30`..`10-04` range; most have the Diana Stats HUD but the guess markers are only clear in the listed ones.
- Nothing launched, nothing committed, `C:\Users\Eli\Videos` untouched. Contact sheets live in the scratchpad (`...\scratchpad\sh\`).
