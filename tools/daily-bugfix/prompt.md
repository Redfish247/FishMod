Find and fix every bug you can in this project. Work on it until there's nothing left you can fix, without stopping to ask me unless something is truly destructive. (Unattended run: never wait for an answer; skip anything that needs a decision and list it in the report.)

Setup:
- Stay on main. Do NOT create any branches. All commits go directly to main.
- Before changing anything, run `git status` and commit any uncommitted changes first so the bugfix work starts clean.
- Read CLAUDE.md and look over the project structure.
- Read my Obsidian brain vault at D:\FishModDungeons\FishModNew before you start. Go through the notes about this project: known bugs, past fixes, decisions, conventions, and any TODOs. Don't re-break anything a note says was fixed on purpose, and don't "fix" anything a note says is intentional.
- Fetch and read these pages:
  - https://fishmod.dev/incoming-features/bugs — reported bugs. These are TOP PRIORITY. Fix every one you can reproduce or find in the code. (If the page returns 401, note that in the report and continue.)
  - https://fishmod.dev/incoming-features — suggestions and planned features. Do NOT build these. Only read them so you don't break or block anything that's planned, and note any suggestion that's really a bug in disguise.
- While you work, don't edit the vault unless I say to. Put anything worth saving into BUGFIX_REPORT.md under a "Notes for Obsidian" section.

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
  (delete the old jar for this mod there first). If a javaw/Minecraft process is running, do NOT copy (ZipException risk); say so in the report.
- Write BUGFIX_REPORT.md with:
  - every bug fixed (file + one-line explanation), marking which ones came from fishmod.dev
  - every reported bug from fishmod.dev that you couldn't fix, and why
  - the commit hash from before you started
  - anything you skipped and why
  - anything I should test in-game
  - a "Notes for Obsidian" section

Roadmap board (fishmod.dev):
- After the fixes are committed, update the board. List cards with `GET https://fishmod.dev/dashboard/api/roadmap`, then for every card matching a bug you fixed, `POST https://fishmod.dev/dashboard/api/roadmap-status` with body `{"updates":[{"id":"<card id>","status":"testing"}]}` and header `Authorization: Bearer $env:ROADMAP_SECRET`.
- Move cards to `testing` ONLY. NEVER `done` (only Eli sets Done). Do not touch the tester board (board.fishmod.dev, C:\FishMod\worker-board). If ROADMAP_SECRET is unset or the API fails, skip and say so in BUGFIX_REPORT.md. Never print the secret.

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

  Write it so a player can understand it, not as dev notes.
- Commit BUGFIX_REPORT.md and CHANGELOG.txt to main.

Last step: run `powershell -NoProfile -ExecutionPolicy Bypass -File D:\fishmoddungeons\tools\daily-bugfix\open-changelog.ps1` (opens CHANGELOG.txt in VS Code on Display 1). If it prints "Couldn't find the VS Code window", wait a few seconds and run it again once.
