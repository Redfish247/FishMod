# FishModDungeons

## Versions and targets
- Active code: `dungeons-26.1.2` (Mojang mappings) AND `dungeons-26.2`, until Eli drops 26.1.2. `dungeons-26.2` compiles the 26.1.2 sources; a same-path file under `dungeons-26.2/src/main` overrides it. Version-specific API calls go in `fishmod/compat/*`. Every change must work on both (`./gradlew :dungeons-26.1.2:build :dungeons-26.2:build`).
- `dungeons-26.3` (port in progress, opt-in) is in the build list via `-Pwith263 :dungeons-26.3:build`; fix 26.3 breakage your change causes.
- `dungeons-1.21.11` is frozen: never edit or mirror to it.
- Version: `mod_version` in `dungeons-26.1.2/gradle.properties` (gitignored, format `X.Y.Z-26.1.2`). Bump from the DEPLOYED jar's version in the Skyblock mods folder, never from a stale file.

## Git
- Work, commit and build on `main` in this checkout. No feature branches or worktrees, even if the app started in one. Before starting: `git pull --ff-only`.
- **Commit every change** on `main`: small commits, one logical change each, so any step can be reverted. Never leave finished work uncommitted.
- After Eli confirms a feature works: `git push origin main:feat/<name>`, then `gh pr create --head feat/<name> --base main`. One PR per feature, never merge. Do not touch the test board.

## Building, running, deploying
- **No session runs gradle builds** (decided by Eli 2026-11-08). Commit with "not built" in the message and report "not built, untested". Build only if Eli explicitly asks in that session.
- `/ship` (Eli runs it) does bump, build and deploy to the Prism `Skyblock` and `FishTest` mods folders. Never copy a jar into a mods folder any other way.
- If Eli asks for a manual copy: first `powershell -NoProfile -File tools/check-game-closed.ps1 <Skyblock|FishTest>` (exit 1 = running, skip), read the installed version and never install a lower one, merge other sessions' work first (past incidents: Lessons Learned section 7).
- **Never open Minecraft instances** (`tools/run262.sh`, `tools/fmtest.ps1`, `runClient`, FishTest/Prism) unless Eli explicitly says so in that session.
- fishmod.dev worker lives in `D:\FishMod-WorkerBoard` (vault: `fishmod-worker`). Never `wrangler deploy` it without diffing against the live code and Eli's explicit OK.

## Parallel sessions
- **At session start** check for other running sessions (`mcp__ccd_session_mgmt__list_sessions` or `ListAgents`) and `git status` / `git log -5` for their work.
- If one is active: note what it touches, stay out of its files, pick a non-overlapping slice, and SendMessage it when your work affects its area. Pull before building; pick a version above any other session's.

## Code rules
- **Find code with the index before reading or grepping:** `python tools/code-index/where.py <terms> [-k gui|setting|uses|command|object|class|mixin|fun|lang] [-n N]` gives exact `path:line`. For a player-visible feature name: `-k gui`, follow to the setting, then `uses` for the feature file. Auto-rebuilds; `python tools/code-index/build.py` forces it. Open only what it points to. If it misses, fall back to Grep, then fix `build.py`. Details: vault `Finding Code`.
- Edit source with the Edit tool, not python/heredoc scripts (backslash loss broke regex strings four times).
- Comments: one short line max. Keep colour/opacity/text customization when slimming a feature.
- Hypixel data (nametags, areas, slime sizes, coordinates): get debug output or Eli's screenshots first, never match guessed strings. Test with a default config and with/without ImmediatelyFast. Say "untested" until Eli has seen it run.
- Never script gameplay or GUI input on Hypixel (ban risk): one keypress = one click in a menu the player opened.

## KVault: write everything down
KVault (`D:\KVault`, `Projects/FishModDungeons/`, `mcp__kvault__*`) is this project's memory. If it is not in KVault, the next session does not know it. The global `CLAUDE.md` rules apply; project specifics:
- **Read first:** before touching an area, read its note. Routing pages: `Codebase Map` (directory to area page), `Technical Gotchas`, `Lessons Learned`, `Working With Eli`, `UI Conventions` (read before any UI/layout/config screen work). Read only what you need.
- **Note everything as it happens:** every decision and its why, feature behaviour, setting names, Hypixel string/data findings (with where they came from), architecture changes, new helpers, version/port quirks (26.1.2 vs 26.2 vs 26.3), build/deploy incidents, Eli's feedback and preferences, open TODOs and ideas. Write it when learned, not at the end.
- **New feature = feature note:** what it does, settings, main files (paths, no code), known limits.
- **Bug fixed = bug written to the vault, same commit.** Symptom, root cause, fix, and which helper or constant prevents a repeat, on the area page (e.g. `UI Conventions`, `Technical Gotchas`). If it can be a shared helper, make it one. Before closing, check (where.py/Grep) whether the same pattern exists elsewhere and fix those too.
- **Mistakes:** log every Claude mistake and how to avoid it in `Lessons Learned`.
- **Session end:** update touched notes, add the session outcome (what changed, what's left, version if any).
- Each note's `source:` names its old FishModNew page; the `FishModNew/` folder here is retired, KVault wins.

## Session wrap-up
- **Claude Changelog:** before finishing a session that changed code, add your work to today's entry on the hidden fishmod.dev page with `tools/claude-changelog.ps1` (merges). Format: vault `Claude Changelog`.
- KVault updated (see above).
