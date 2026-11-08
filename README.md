# FishMod

**The all-in-one Hypixel SkyBlock mod: dungeons, Kuudra, farming, mining, the Rift and more, in one clean settings menu.**

[Download on Modrinth](https://modrinth.com/mod/fishmod) · [Discord](https://discord.gg/3mSuQUB8kk) · [Roadmap & bug reports](https://fishmod.dev/incoming-features)

Type **`/fm`** in game to open the settings. Every feature is off until you turn it on, and every colour, text and HUD position can be changed.

## Why FishMod

- **One mod instead of five.** Dungeons, Kuudra, farming, mining and Rift tools share one UI, one HUD editor and one config.
- **Built for floor 7.** Terminal and puzzle solvers, dragon timers with priority, relic times, splits, a full dungeon map and personal-best pace.
- **Fully customizable.** Move, scale and recolour every HUD, with dark mode and a UI scale per screen.
- **No scripted input.** One key press does one thing; nothing plays the game for you.

## Features

| Area | What's in it |
| --- | --- |
| Dungeons | Splits, PB pace, leap menu, secrets, routes, waypoints, auto requeue, starred mob highlight |
| Solvers | Terminals, puzzles, Simon Says, Arrow Align, blood |
| Dungeon Map | Full map, player heads, room and door colours, score messages |
| Floor / Master 7 | Wither dragons with priority, lever waypoints, tick timers, Goldor splits, relics |
| Kuudra | Phase splits, alerts, supply and build helpers, chest value and profit, loot tracker |
| Garden | Visitors, pests, composter, Jacob's contests, crop milestones, profit tracker, garden keybinds |
| Mining | Nucleus highlights, mineshaft pity, commissions, fossil solver, powder chests |
| Rift | Nametag stats, vampire helper, Mirrorverse parkour, Crux and Bacte helpers |
| Diana | Burrow guessing, warp, rare mob sharing, tracker, Crown of Avarice |
| Slayer | Spawn alerts, carry tracker, profit tracker, boss timer and phases |
| Inventory | Storage overlay, slot binds and locking, inventory search, container value |
| Party & Social | Party commands, party finder, kick list, chat channels |
| Profile Viewer | Browse any player's SkyBlock profile in game |
| Achievements | Achievement menu with difficulty ratings and leaderboards |
| Cosmetics | Name colours and gradients, badges, nametags, player size, item looks |
| General | Dark mode, chat tools, compact tab, zoom, sound manager, custom crosshair and scoreboard |

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api) for Minecraft **26.1.2** or **26.2** (Java 25).
2. Put the FishMod jar in your `mods` folder.
3. Recommended: [ImmediatelyFast](https://modrinth.com/mod/immediatelyfast).
4. Join Hypixel and type `/fm`.

## Building from source

Requires JDK 25.

```bash
./gradlew :dungeons-26.1.2:build :dungeons-26.2:build
```

Jars land in `dungeons-<version>/build/libs/`.

| Module | Purpose |
| --- | --- |
| `dungeons-26.1.2` | Main source (Mojang mappings) |
| `dungeons-26.2` | Builds the 26.1.2 sources for 26.2, with version-specific overrides |
| `dungeons-26.3` | 26.3 port in progress (opt in with `-Pwith263`) |
| `common` | Version-independent shared code |
| `practical-config-relocated` | Repackaged config library so it never clashes with other mods |
| `dungeons-1.21.11` | Legacy 1.21.11 build, no longer updated |

## Bugs & feature requests

Use the [roadmap page](https://fishmod.dev/incoming-features) or the [Discord](https://discord.gg/3mSuQUB8kk). Please include your mod version, Minecraft version and the crash log.

## License

[CC0 1.0](LICENSE). Bundled third-party code keeps its own license (see `dungeons-26.1.2/LICENSE_*`).

*Use at your own risk under Hypixel's rules.*
