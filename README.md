# FishMod

A Hypixel SkyBlock quality-of-life and dungeons mod for Fabric. FishMod bundles
dungeon timers, profit/XP trackers, HUDs, party command tools, item cosmetics,
and a stack of convenience commands into one client-side mod.

## Current Version

- Minecraft **1.21.11** (Fabric, Java 21)

## Dependencies

- [Fabric API](https://modrinth.com/mod/fabric-api) — required
- [Hypixel Mod API](https://modrinth.com/mod/hypixel-mod-api) — required
- [MCEF](https://modrinth.com/mod/mcef) — optional, only needed for `/wiki`

## Features

### Dungeons
- **Floor 7 boss timers** — Maxor / Storm / Goldor tick timers, crystal spawn
  time + reminder, term start timer, section progress, storm death/crushed.
- **Splits & estimated total** — live phase splits with an estimated run total.
- **Dungeon score** and **secrets** tracking.
- **Puzzle solvers** — Simon Says tracker, M7 lever waypoints, puzzle display.
- **Death messages** and **session stats** (runs, time, profit).
- **Class detection** with optional **class-colored boots**.

### Trackers & HUDs
- **Powder tracker** — Mithril / Gemstone / Glacite powder rates.
- **Mining loot/sack profit** — corpse, sack, and key-cost aware profit tracking.
- **Farming tracker** — farming weight and crop profit.
- **Slayer XP** and **Skill XP** HUDs.
- **Pet HUD**, **Soulflow HUD**, **Cooldown overlay**.
- **Croesus loot tracker** with bazaar/lbin pricing (`/fmloot`).
- **Trophy frog** progress tracker.
- All HUD elements are draggable/scalable via the in-game **HUD editor**.

### Fishing
- **Bobber reminder** — after a bite, a HUD counts down; once your reminder delay passes it flashes
  a customizable `!!!` alert (with optional ping), and shows **"missed it"** if the catch window
  closes unreeled. Delay, reminder text, missed text, sound, position & scale all configurable.
- **Sea creature tracker** — per-creature session counts + creatures/hour HUD, with an optional
  **rare-catch** title + sound when a rare one surfaces.
- **Trophy fish tracker** — Bronze/Silver/Gold/Diamond progress per trophy fish on Crimson Isle,
  seeded from the Trophy Fishing menu and kept live from catch messages.

### Slayer
- **Slayer alerts** — title + ping on **boss spawn** (your "Spawned by" boss), **boss slain**, and
  **miniboss** spawns. Each event individually toggleable.
- **Slayer drop tracker** — session HUD counting Rare / Very Rare / Crazy Rare / Insane drops and
  "Praise RNGesus" pulls while a quest is active.

### Cosmetics (opt-in)
- **Item customizer** (`/fm customize`) — recolor/rename your items and armor, swap an item's
  model to another item (a model swap also adopts that item's hold/draw pose, so e.g. a crossbow
  model actually behaves like a crossbow, not a retextured bow), and apply a head **skin** to pet /
  player-head items from a texture hash, URL, or value.
- **Custom nicknames** (`/nick`), shared with other FishMod users.
- Remote item cosmetics and player sizes synced between FishMod users.

### Other
- **Profile Optimizer** (`/po`) — net worth, skill roadmap, and "what to do next".
- **Twitch streams** browser (`/streams`) and **Wiki** browser (`/wiki`).
- **Warp map**, **chat filter**, **inventory command buttons**, item rarity
  backgrounds, welcome message, lag/ping tracking.

## Commands

Args in `<>` are required, `[]` optional. Stats commands default to **you** if
no name is given, and also work in party chat as `.cmd` (each toggleable in
`/fm` → Party Commands).

### Stats lookups
| Command | Description |
| --- | --- |
| `/cata [player]` | Catacombs level |
| `/rtc [player] [level]` | Runs to a Cata level (default 50) |
| `/rtca [player]` | Runs to class 50 for all 5 classes |
| `/crtc [player] <class> [level]` | XP + runs for one class (class = healer\|mage\|berserk\|archer\|tank) |
| `/secrets`, `/sa [player]` | Total secrets / secret average |
| `/runs [player] [floor]` | Floor run count (default m7) |
| `/totalruns [player]` | Total dungeon runs |
| `/pb [player] [floor]` | Floor personal best (default m7) |
| `/mp [player]` | Magical Power |
| `/nw [player]` | Networth |
| `/level [player]` | SkyBlock level |
| `/farming [player]` | Farming weight |
| `/nuc [player]` | Crystal Nucleus runs |
| `/worm`, `/scatha [player]` | Worm + Scatha bestiary |
| `/bank [player]` | Bank + purse |
| `/powder [player]` | Mithril / Gemstone / Glacite powder |
| `/corpse [player]` | Glacite corpses |

Floors: `e`, `f1`-`f7`, `m1`-`m7`.

### Your stats
`/fps` · `/tps` · `/ping` · `/dprofit` — FPS, server TPS, ping, Croesus profit/run.

### Party chat joins
`.e` · `.f1`-`.f7` · `.m1`-`.m7` — join a Catacombs floor.
`.t1`-`.t5` — join a Kuudra tier.

### Party actions
`/pk <player>` kick · `/pw` warp · `/pt <player>` transfer · `/pp <player>` promote.
In party chat: `.ai` (allinvite), `.d` (disband), `.kick/.warp/.transfer/.promote`.

### Screens & misc
| Command | Description |
| --- | --- |
| `/fm` | Config GUI |
| `/fm customize` | Item customizer |
| `/fmloot` | Croesus loot tracker |
| `/po`, `/fm optimize` | Profile Optimizer |
| `/streams` | Twitch streams |
| `/wiki <query>` | Wiki browser (needs MCEF) |
| `/nick <name>\|reset` | Set/reset cosmetic nickname |
| `/fm commandhelp` | Print the full command list in chat |

In party chat, `.help` lists your enabled party commands.

## Configuration

Open the config GUI with `/fm`, or via Mod Menu. HUD positions and scales are
edited from the config's HUD editor — drag to move, scroll to resize, and use
**Reset positions** to restore every HUD to its default layout.

`/fm commandhelp` (and party-chat `.help`) print the command reference; single
commands in that list are click-to-suggest.

## Building

```bash
./gradlew build
```

The built jar is produced under `build/libs/`.
