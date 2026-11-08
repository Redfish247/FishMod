# Twitch VOD scan (B-roll for "greatest mod" video)

Source: `C:\Users\Eli\Videos\twitch-vods\` (read-only, nothing cut). Both 1920x1080 60fps.
- `2885132351.mp4` (4h13m): Catacombs M7/M5 party runs, Twitch chat overlay top-right. Last ~hour has Twitch dashboard / other screens.
- `2888522472.mp4` (2h07m): Diana burrow hunting in the Hub (clean, scenic, FishMod Diana HUDs everywhere).

Method: one frame every 90s over both files (254 frames, timestamps burned in), then full-res zoom on the picks. **Only the sampled second is verified**; the start-end windows are suggestions around that frame, so scrub 10-20s either side before cutting (clips with the same HUD layout are likely but not checked).

## What is FishMod vs other (checked against the source via where.py)
- FishMod: Diana Loot Tracker + Diana Stats HUDs, `Guess` / `Mob` / `Treasure` waypoint boxes, `Warp Castle/Crypt/Wizard/Hub` titles, run-splits timer (top-right green list), `PB Pace (M7)`, `Est. Total / Lag Lost`, Spirit/Bonzo/Phoenix mask timer (left), `Release Now!` timer.
- Not FishMod / unclear: `[SkyHanni] +Tracker Drop` chat line (VOD2 around 2970s, so SkyHanni is installed), `Soulflow: 1,2xx,xxx` line on the left (VOD2, source unverified, may be SkyHanni, crop or accept), black numbered terminal grids and colour-square popups in VOD1 (no FishMod "terminal solver" found, likely another mod, treat as no), VOD1 room-name dungeon map with Puzzles/Secrets/Score panel at 3150s (could be FishMod map, unverified).
- No `Odin »` chat lines and no IQ HUDs seen in any zoomed frame. Both VODs overlay Twitch chat (viewer names, e.g. top-right, and `[Twitch]` lines in the game chat in some frames), so crop top-right or accept. No webcam, no DMs/emails seen. VOD1 from ~8500s to ~9900s and ~14650s to end is a Twitch/browser UI frame (someone else's screen or dashboard): skip.

## Table (best first)

| # | File | Start-end (s) | What it shows | Usable | Why |
|---|------|---------------|---------------|--------|-----|
| 1 | 2888522472 | 4840-4860 (1:20:40) | Slow look at Hub castle towers on a hillside, `Guess` waypoint beams, Diana Loot + Stats HUDs | **yes** | Cinematic + FishMod Diana HUDs, no other mod text. Soulflow line small at left |
| 2 | 2888522472 | 6830-6850 (1:53:50) | Night castle from above, three `Guess` waypoints with distances, Diana HUDs | **yes** | Best scenic, FishMod waypoints. Night sky good for contrast |
| 3 | 2888522472 | 5930-5950 (1:38:55) | Inside Wizard Tower, big `Warp Wizard` + `Griffin` title, Diana HUDs | **yes** | Shows FishMod warp title clearly. No chat visible |
| 4 | 2888522472 | 800-820 (13:30) | Golden Dragon pet title + `Mob [1/2]` box, damage number, Diana HUDs | **yes** | Clear FishMod Mob waypoint. Fire particles busy but fine |
| 5 | 2888522472 | 80-100 (1:30) | Wide Hub view, giant `Warp Crypt` title, loot HUD full | **yes** | Wide + obvious FishMod title. Twitch chat top-right (crop) |
| 6 | 2888522472 | 2420-2440 (40:30) | Sweeping hub valley, mountain, faint `Guess` marker | **yes** | Clean cinematic. FishMod HUD only at the edges |
| 7 | 2888522472 | 7100-7120 (1:58:30) | Pink-sky floating islands (spooky/Crimson-like hub sky), Diana HUDs | **yes** | Scenic. No chat |
| 8 | 2888522472 | 4760-4780 (1:19:30) | Overgrown tower, `Treasure` + `Guess` waypoint line across islands | **yes** | Good for showing waypoints. Bottom-left chat line has a coords share from a party member (name `Orbie`), blurry-crop if used |
| 9 | 2885132351 | 4760-4780 (1:19:30) | M7 Necron fight, split timer, `PB Pace`, mask timers, red laser lines | **yes** | Dense FishMod dungeon HUDs (splits, PB pace, mask timer). Twitch chat top-right is cropable |
| 10 | 2885132351 | 13850-13870 (3:50:50) | M7 Necron, full splits list + `Est. Total`/`Lag Lost` + PB Pace | **yes** | Best single frame of the FishMod splits panel |
| 11 | 2885132351 | 12860-12880 (3:34:30) | Necron close-up, splits, mask timer | yes | Same HUDs, nice Necron model close-up |
| 12 | 2885132351 | 10610-10630 (2:56:50) | M5 Livid room, red/white floor, Phoenix/Bonzo HUDs, splits | yes | Good colour. Chat box bottom-left shows party chat names (fine, no private info) |
| 13 | 2888522472 | 2960-2980 (49:30) | `Chimera I` drop popup + `RCM Terror` loadout title, Diana HUDs | **no** | `[SkyHanni] +Tracker Drop` in chat, Soulflow, and Twitch chat joke about "fishmod" visible |
| 14 | 2885132351 | 6110-6130 (1:42:00) | Necron with tab list/overlay open (party list, stats) | no | Large tab menu, other mod panels possible, busy |
| 15 | 2885132351 | 3140-3160 (52:30) | `Cleared` room title, dungeon map left, Puzzles/Secrets panel | unsure | Map panel not confirmed FishMod. Great "dungeon" look if Eli confirms it is |
| 16 | 2885132351 | 5750-5770, 6380-6400, 6740-6760 | Black numbered terminal grids (Terminal solver style) | no | Looks like another mod's terminal solver |
| 17 | 2885132351 | 8500-9900 | Twitch dashboard / someone else's screen share | no | Not Eli's game view |
| 18 | 2888522472 | 5300-5320 (1:28:30) | Hub spawn plaza, Twitch chat lines in game chat | no | Viewer usernames in chat plus Hub UI, little cinematic value |

## Other notes
- VOD1 has no calm cinematic footage: it is run after run of M7/M5 with a busy HUD. VOD2 is the clean B-roll source (hub flyover, Diana).
- There is no hub-spawn calm shot, garden or island travel outside Diana burrow hunting in either file.
- `Highest Magic Find` and Diana loot numbers are readable at full res: usable as a "tracker" insert, you may want to blur nothing there.
- Soulflow, TPS/FPS corner text and the FPS/TPS in 7110 and 4770 may come from another mod (TPS exists in FishMod's code, Soulflow unverified).
