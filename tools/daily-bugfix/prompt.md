Find and fix every bug you can in this project. Work on it until there's nothing left you can fix, without stopping to ask me unless something is truly destructive. (Unattended run: never wait for an answer; skip anything that needs a decision and list it in the report.)

EXPLICIT AUTHORIZATION FROM ELI (overrides CLAUDE.md and memory rules for THIS run only): you MAY and MUST run `.\gradlew build` (use `:dungeons-26.1.2:build :dungeons-26.2:build`), copy the jar to the Modrinth folder below, write and commit CHANGELOG.txt (use `git add -f` if ignored; do NOT create BUGFIX_REPORT.md or any other report file, everything goes in CHANGELOG.txt), update the roadmap board (Testing only), and run the open-changelog script. These are the whole point of this run; do not skip them. Do NOT launch Minecraft, do NOT touch the tester board, do NOT edit the vault.

Setup:
- Stay on main. Do NOT create any branches. All commits go directly to main.
- Before changing anything, run `git status` and commit any uncommitted changes first so the bugfix work starts clean.
- Read CLAUDE.md and look over the project structure.
- Read my Obsidian brain vault at D:\FishModDungeons\FishModNew before you start. Go through the notes about this project: known bugs, past fixes, decisions, conventions, and any TODOs. Don't re-break anything a note says was fixed on purpose, and don't "fix" anything a note says is intentional.
- Fetch and read these pages:
  - Reported bugs. These are TOP PRIORITY. The web page https://fishmod.dev/incoming-features/bugs returns 401 to fetchers, so DO NOT use it. Read them from the API instead: `Invoke-RestMethod https://fishmod.dev/dashboard/api/roadmap -Headers @{Authorization="Bearer $env:ROADMAP_SECRET"}` (the User env var; if `$env:ROADMAP_SECRET` is empty use `[Environment]::GetEnvironmentVariable('ROADMAP_SECRET','User')`). Every item with `tag` = "Bug" and `status` not "done" is a reported bug (about 34: many are auto error reports with a stack trace in `description`; others are tester reports). Work through ALL of them one by one: find the code (`python tools/code-index/where.py <terms>`), fix it if you can, and record each card id with its outcome (fixed / can't reproduce / needs decision / not FishMod) in the "Run notes" part of the CHANGELOG.txt entry. Cards you cannot fix go under "Known issues" in CHANGELOG.txt.
  - Suggestions (the non-Bug cards in the same API response; the page https://fishmod.dev/incoming-features is client-rendered and shows nothing to fetchers) — suggestions and planned features. Do NOT build these. Only read them so you don't break or block anything that's planned, and note any suggestion that's really a bug in disguise.
- While you work, don't edit the vault unless I say to. Put anything worth saving under "Notes for Obsidian" in the Run notes of the CHANGELOG.txt entry.

How to find bugs:
1. Start with the reported bugs from fishmod.dev/incoming-features/bugs and the Obsidian vault.
2. Run `.\gradlew build` and fix every compile error and warning that points at a real problem.
3. Read through every source file in src/ looking for:
   - null pointer risks (player/world/client being null, especially on world join/leave)
   - code running off the render thread or blocking it (network calls, heavy loops in render/tick)
   - mixins that could crash or conflict with other mods
   - toggles/config options that don't save, load, or actually do anything
   - HUD elements that break on resize, different GUI scales, or when dragged offscreen
   - chat-message detection that breaks on color codes or rank prefixes
   - commands with missing args crashing instead of showing usage
   - leftover debug code, unused imports, dead code
4. Check that everything registered (commands, keybinds, events, screens) is actually wired up.

How to fix:
- Fix one bug at a time. After each fix (or small group of related fixes), run `.\gradlew build` and make sure it still compiles.
- Commit to main after each working fix with a message starting with "fix:" saying what was wrong. If it's a reported bug from the site, mention that in the message.
- Never commit code that doesn't build.
- Don't add new features or redesign things. Only fix stuff that's broken or risky.
- If a fix needs a decision from me or could change how a feature behaves, skip it and add it to the list below.

When you're done:
- Run a final `.\gradlew build`.
- Copy the built jar from build/libs (not the -sources jar) to:
  C:\Users\Eli\AppData\Roaming\ModrinthApp\profiles\Skyblock 1.21.8\mods\
  (delete the old jar for this mod there first). If a javaw/Minecraft process is running, do NOT copy (ZipException risk); say so in the Run notes.
- Do NOT write BUGFIX_REPORT.md. The report goes in the "Run notes" part of today's CHANGELOG.txt entry (see Changelog below).

Roadmap board (fishmod.dev):
- After the fixes are committed, update the board. List cards with `GET https://fishmod.dev/dashboard/api/roadmap`, then for every card matching a bug you fixed, `POST https://fishmod.dev/dashboard/api/roadmap-status` with body `{"updates":[{"id":"<card id>","status":"testing"}]}` and header `Authorization: Bearer $env:ROADMAP_SECRET`.
- Move cards to `testing` ONLY. NEVER `done` (only Eli sets Done). Do not touch the tester board (board.fishmod.dev, C:\FishMod\worker-board). If ROADMAP_SECRET is unset or the API fails, skip and say so in the Run notes. Never print the secret.

Changelog:
- Write CHANGELOG.txt in the project root (plain text, no markdown). If it already exists, add the new entry at the TOP and keep the old entries. Format:

  FishMod Changelog - <today's date>
  ==================================
  Fixed:
  - <short player-friendly description of each fix>
  Reported bugs fixed (from fishmod.dev):
  - <each one>
  Known issues (not fixed yet):
  - <each one>
  Test in game:
  1. <each step>
  Run notes:
  - Start commit <hash>, fix commits <hashes>, build result, where the jar went
  - Each fix: file + one-line dev explanation, with the fishmod.dev card id
  - Each reported bug not fixed and why (needs decision / can't reproduce / not FishMod)
  - Skipped and why, board updates
  - Notes for Obsidian: <each one>

  Write everything above "Run notes" so a player can understand it; Run notes are short dev notes for Eli.
- Commit CHANGELOG.txt to main.

Claude Changelog page (fishmod.dev, hidden owner page):
- After the changelog, write the same report as JSON to `tools/daily-bugfix/last-report.json` (UTF-8, overwrite) and post it with `powershell -NoProfile -File tools/claude-changelog.ps1 tools/daily-bugfix/last-report.json`. Expect `{"success":true,...}`. It merges into today's entry, so other sessions' items stay; post once per run. If it fails, say so in the Run notes. Never print the secret.
- Format (all strings plain text; wrap code, file names and commands in backticks):
  {
    "date": "YYYY-MM-DD", "version": "1.7.72",
    "summary": "one line: what was built, where it went, tested or not",
    "alert": "optional one line Eli must see first, else omit",
    "fixed":   [{"id": 534, "title": "short player-facing title", "files": ["garden/SpeedSignHelper.kt"], "detail": "one or two sentences, or an array of bullets"}],
    "already": [{"id": 538, "title": "...", "detail": "..."}],
    "open":    [{"id": 533, "title": "...", "status": "Needs your OK | Needs a report | Can't reproduce | Not FishMod", "detail": "why"}],
    "tests":   ["each in-game test step"],
    "facts":   [["Commits", "..."], ["Build", "..."], ["Board", "..."], ["Sweep", "..."]],
    "notes":   ["side notes and Notes for Obsidian"]
  }
  Use `id` only for fishmod.dev card numbers (omit it for bugs you found yourself).

Last step: run `powershell -NoProfile -ExecutionPolicy Bypass -File D:\fishmoddungeons\tools\daily-bugfix\open-changelog.ps1` (opens CHANGELOG.txt in VS Code on Display 1). If it prints "Couldn't find the VS Code window", wait a few seconds and run it again once.
