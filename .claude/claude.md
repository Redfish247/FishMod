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
-

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