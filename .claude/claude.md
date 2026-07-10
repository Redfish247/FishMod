# Working Principles

- **Obsidian vault as project brain:** Use the Obsidian vault as a persistent knowledge layer for project context — architecture notes, design decisions, conventions, gotchas, mental models, and "why" reasoning. Do NOT dump literal data into it (no raw code, no large file contents, no API responses, no database rows). It is a brain, not a database. Treat it as the place that captures understanding *about* the project, while the codebase remains the source of truth for the project itself.
- **Always spawn agents when working:** Default to delegating real work to subagents (via the Agent tool) rather than doing it inline. Use `Explore` for codebase searches, `Plan` for implementation strategy, and `general-purpose` for multi-step research/execution. Parallelize independent agents in a single message. Keep the main thread focused on orchestration and synthesis, not on doing the grunt work itself.

# FishMod (Minecraft mod)
- Project path: `C:/FishMod`
- Mods folder: `C:/Users/Eli/AppData/Roaming/ModrinthApp/profiles/Skyblock 1.21.8/mods/`
- Build tool: Gradle (`./gradlew build`)
- Jar name: `FishMod-<version>.jar` (archives_base_name=FishMod)
- Version tracked in `gradle.properties` (mod_version)
- After every change: bump version, build, copy new jar, remove old jar from mods folder

# Future FishMod Features

## Dungeons QoL ideas (gaps vs. current feature set)
Current dungeon coverage is already deep on F7 (boss timers, terminals, relics, sections)
and general run tracking (score, splits, session stats, class detection, PB pace). These
are the notable gaps against that baseline — not yet implemented anywhere in the codebase:

- **Livid finder (F5)** — highlight/glow the real Livid among the fake color-block
  Livids using its unique name-tag offset, like Skytils/DSM do.
- **Terminal answer solver (F7)** — `TerminalEvent` only reports completion; add
  overlay hints for "click in order", "same color", "what starts with" terminal types
  (highlight blocks/items, not auto-click — stay outside macro territory).
- **Trivia (Devil's Riddle, F5) answer highlight** — glow the correct chat option
  based on a bundled answer table, mirroring the terminal-solver approach.
- **Boulder/Creeper-beam puzzle helper (F6)** — path/beam highlight for the
  push-block room.
- **Higher/Lower puzzle helper (F1)** — no assist exists yet for the sign puzzle.
- **Blaze solve-order highlight (F7 P3)** — visually mark the correct blaze
  click order instead of relying on memorized patterns.
- **Necron P3 "Handles of Krul" progress tracker** — dedicated HUD element
  distinct from the general boss health/tick timers.
- **Teammate stat overlay** — HP/mana/absorption above teammates' heads
  (useful for healers/tanks), separate from the existing class-color/name render.
- **Live score-target projection** — "need N more secrets for S+/S++" using the
  data `DungeonScore` already tracks (secretsPercent, deaths, completed rooms),
  surfaced as a HUD line instead of only the final score.
- **Run summary export** — on run completion, auto-copy or post to party chat a
  one-line recap (score, secrets, splits vs PB, deaths, class comp) built from
  `SessionStats`/`RunHistory`/`DungeonScore`, instead of only logging locally.
- **Pre-run ready check** — HUD/chat check before `/warp` that flags missing
  class levels, no active potion/god pot, or a duplicate class in party.
- **Wither-door essence cost tracker** — running total of essence spent opening
  wither doors this run/session.
- **AFK/leech teammate flag** — surface a teammate who hasn't moved/dealt
  damage in N seconds (builds on the movement tracking already in `SessionStats`).

# PrismAgent (Electron App)
- **Project path:** `E:\prism-agent`
- **Stack:** React + Vite + Electron, Firebase Realtime Database, localStorage for CRM data
- **Build:** `npm run electron:build` (local installer), `GH_TOKEN=$(gh auth token) npm run electron:publish` (GitHub release)
- **Version:** bump both `package.json` AND `src/pages/Settings.jsx` (`const VERSION`) every release
- **Notify:** `curl -d "msg" ntfy.sh/prism-notifications` when done

## Layout
- No left sidebar — full-width content
- Permanent **right sidebar** (340px) with Team Chat + AI Chat tabs, collapse toggle strip (28px)
- Grid: `1fr 340px` / `1fr 28px` collapsed (`.app-layout.right-collapsed`)
- Header: logo→dashboard, page title, search, chat toggle, settings, avatar

## Auth & Users
- Whitelist: `src/auth/whitelist.js` → `ALLOWED_EMAILS` array
- DevMode/roles: `src/auth/roles.js` (localStorage + Firebase)
- Dev password: `VITE_DEV_PASSWORD` env or `prism-dev-2024`
- Dev codes (Settings → single input → password popup): `ChatClear`, `ClearTenants`, `ClearProspects`, `ClearCRM`, `DevMode`

## Data
- `prism_tenants`, `prism_spaces` → LeasingCRM localStorage
- `prism_prospects` → ProspectPipeline localStorage
- Clear fix: use `setItem(key, '[]')` not `removeItem` to avoid INIT fallback
- Chat/presence: Firebase `messages`, `typing`, `users`, `roles`

## Key Files
- `src/App.jsx` — root state (activePage, rightCollapsed, unread)
- `src/components/layout/RightSidebar.jsx` — permanent right chat panel
- `src/components/layout/Header.jsx` — NAV_PAGES (no "chat"), chat toggle button
- `src/pages/TeamChat.jsx` — Team + AI tabs, Firebase presence
- `src/pages/Settings.jsx` — theme, password, account, dev tools
- `src/components/ui/CRMAtoms.jsx` — FSelect, FInput, Badge (shared CRM UI)
- `src/auth/whitelist.js` — allowed emails
- `public/icon.ico` — full Prism logo (app icon), `public/logo.webp` — header logo
- `build/make-installer-assets.cjs` — generates BMP/PNG assets

## CSS Variables
- `--bg-base #0a0b0f`, `--bg-surface #12141a`, `--bg-elevated #1a1d27`
- `--prism-2 #CC1F08` (brand red), `--border-subtle #252836`
- Light mode via `[data-theme="light"]` in `src/styles/global.css`

## Prism Context
- Prism Real Estate Services, LLC — KC metro commercial real estate brokerage
- Internal tooling for the leasing team (CRM, pipeline, commissions, comp db, chat, AI assistant)
# graphify
- **graphify** (`~/.claude/skills/graphify/SKILL.md`) - any input to knowledge graph. Trigger: `/graphify`
When the user types `/graphify`, invoke the Skill tool with `skill: "graphify"` before doing anything else.


ALWAYS REFRENCE THIS AFTER EACH PROMPT!