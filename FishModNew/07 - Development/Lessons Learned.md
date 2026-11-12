---
type: development
updated: 2026-09-30
covers: 2026-08-20 → 2026-09-30
sources:
  - "project memory (C:\\Users\\Eli\\.claude\\projects\\D--FishModDungeons\\memory\\*.md)"
  - "[[Session History]], [[Multi-Session Sprint Retro]], [[Technical Gotchas]], [[Known Bugs]]"
  - "git in D:\\FishModDungeons (branch/PR state checked 2026-09-30)"
tags: [process, lessons, mistakes]
---

# Lessons Learned

> [!info] What this page is
> Recurring **process** mistakes across Claude sessions on FishModDungeons and how to avoid them.
> Each entry: **what happened → why → how to avoid**. Code-level traps (rendering, mixins, packets,
> Hypixel strings) live in [[Technical Gotchas]]; how Eli wants things done is in [[Working With Eli]];
> loose ends are in [[Open Threads]].
>
> Rebuilt 2026-09-30 after the original page was lost. Tags: **FACT** = verified in git/memory,
> **INFERENCE** = reasoned, **NEEDS VERIFICATION** = unconfirmed.

> [!tip] The five that cost the most
> 1. Work that exists only on an unmerged branch or in an uncommitted tree **will vanish** from the next build.
> 2. Parallel sessions in one checkout clobber each other; use one worktree per session.
> 3. Never copy a jar over the one a running game has open.
> 4. `mod_version` is per-worktree; bump from the **deployed** jar, not the worktree.
> 5. Don't trust a note (vault or memory) that says "fixed" or "unmerged"; check git.

---

## 1. Lost and vanishing work

### Uncommitted work wiped by a reset (2026-09-06/07)
- **What:** all 1.1.9–1.1.24 work (UI Customization card, curtain animation, SS tracker/solver fixes, P5 Rag) was never committed. `main` was `git reset` onto `slayer-features` and it disappeared. It wasn't in any commit, reflog or dangling object. **FACT**
- **Why:** sessions deployed jars straight from the working tree and never committed. A reset in the shared checkout destroyed the only copy.
- **Avoid:** commit before every deploy. If work is lost, the per-version `build/libs/*-sources.jar` files are the last record. Diff them, and map version → commit via `git log -p -- dungeons-26.1.2/gradle.properties`. That's how `claude/recover-1.1.24-ui` was rebuilt. See [[Main Consolidation 2026-09-09]].

### Features vanish when they live only on unmerged branches (09-09, 09-23)
- **What:** "features keep disappearing". On 09-09 about 14 unmerged branches held shipped features. On 09-23 PB messages, Inventory Search and SkySoft compat dropped out of the jar because they had only been in `local/combined-build`. **FACT**
- **Why:** each session builds its own branch off `main` and deploys it. Anything that exists only elsewhere is silently left out.
- **Avoid:** before building, run `git branch --no-merged main` and diff the class list of the deployed jar against the new one with `unzip -Z1`. Lambda renumbering is noise. Follow the rule of one feature, one PR, merged, then deploy. Never deploy off an unmerged branch. **As of 2026-09-30 those three branches and the route recorder are all on main (FACT).**

### A removal reverted neighbouring code (09-06)
- **What:** removing Slayer also reverted UI Customization, the SS solver/tracker and P5 Rag. **FACT**
- **Why:** the removal was done by resetting to an older tree, not by deleting the feature.
- **Avoid:** remove a feature with a targeted diff and check the jar class diff afterwards. Slayer was re-added on 09-13 and is on main now. See [[Slayers]].

### Stashes are shared across worktrees
- **What:** Disable Front-Facing Camera was lost in a stash on 09-20 and recovered on 09-21. **FACT**
- **Avoid:** don't use a bare `git stash`/`pop`. Use a WIP commit, or a tagged stash that you apply by SHA.

---

## 2. Parallel sessions

### Shared checkout clobbering (08-30 → 09-16)
- **What:** 6 to 8 sessions ran in `D:\FishModDungeons` on one branch. Files were overwritten (SimonSaysSolver was rewritten by another session), `stash`/`reset` wiped other sessions' work, and one committed HEAD didn't compile because it referenced uncommitted symbols. **FACT**
- **Why:** there was one working tree and many writers.
- **Avoid:** give every session its own `git worktree` under `.claude/worktrees/<slice>`. Copy both modules' gitignored `gradle.properties` into it, or configuration fails. See [[Multi-Session Sprint Retro]].

### Deploy clobbering and version collisions (09-22 → 09-26)
- **What:** each session deployed only its own jar, so the last deploy dropped everyone else's work. Two sessions both chose 1.4.40, and one overwrote the other by filename. Another session bumped from the Modrinth profile's 1.5.24 and overwrote a 1.5.26 already in FishTest. **FACT**
- **Avoid:** before deploying, scan `git worktree list`, the newest `build/libs` jars, and **both** mods folders (FishTest and `Skyblock 1.21.8`). Pick a version above all of them. If Eli is testing other sessions' unmerged work, build a combined jar.

### Stale bumps from a stale worktree (09-25, 09-26)
- **What:** a worktree still at 1.4.56 was bumped to 1.4.57 and swapped in over the deployed 1.5.7, which nearly downgraded Eli's client and dropped PR #58. Separately, 1.5.9–1.5.11 were built from main while Eli was already on 1.5.16. **FACT**
- **Why:** `dungeons-26.1.2/gradle.properties` is gitignored (since PR #22), so each worktree has its own `mod_version`.
- **Avoid:** fast-forward to `origin/main` first. Set the version to the deployed version + 1 and keep the `-26.1.2` suffix. Find the worktree whose jar md5 matches the deployed jar, then use `git cherry` to confirm your base contains every recent branch.

### Main lags the live build
- **What:** a HUD editor mockup was based on `main`, but Eli ran `local/build-1.5.9`, which already had HUD Editor v2. Route Recorder branched off main and would have dropped Eli's `eli/mining-profit` work. **FACT**
- **Avoid:** to describe current behaviour, read the newest `local/build-*` branch (`git branch --sort=-committerdate`). Since 2026-09-30, also check the **main checkout's uncommitted edits**, because Eli edits there directly.

### Cross-session coordination (addon sprint, 09-08)
- **What:** reserve roles ping-ponged, and a Slayer worker went quiet for about 40 minutes. It was holding on a memory note saying "don't re-add slayer". **FACT**
- **Avoid:** assign lanes once, in writing, in the first message. Before calling a quiet session dead, check its memory and transcript. Full playbook in [[Multi-Session Sprint Retro]].

### Vault writes blocked from worktrees
- **What:** the vault (`FishModNew/`) is gitignored and lives only in the main checkout. A hook blocks worktree sessions from writing there, so notes from the 26.2 port, Route Recorder and this rebuild had to be staged. **FACT**
- **Avoid:** stage vault notes in the session scratchpad and give Eli a copy command. Don't try to route around the hook.

---

## 3. Deploying

### Jar hot-swapped under a running game (09-01, again 08-26→30)
- **What:** copying a new jar over the live one produced `ZipException: invalid LOC header` and a hard crash on the next lazy class load. The code was fine. **FACT**
- **Why:** the JVM reads `.class` entries lazily from the jar it opened at startup.
- **Avoid:** confirm the game is closed first. Check `javaw` command lines for "Skyblock 1.21.8" or FishTest; Gradle daemons are java too. If Eli is mid-test, give him the jar path instead. See [[Build & Release]].

### Wrong deploy target (08-22)
- **What:** jars went to the `Fabric 26.1.2` profile for about a day. Eli actually plays `Skyblock 1.21.8`, which is misnamed and really runs 26.1.2. **FACT**
- **Avoid:** deploy to `Skyblock 1.21.8/mods/` **and** FishTest, skipping any client that's open. If you're unsure, compare `logs/latest.log` mtimes. 26.2 builds go only to `FishTest-26.2`.

### A "missing" jar that wasn't lost (09-26)
- **What:** after the game exited, a jar disappeared from mods. The session concluded the installer had lost it and hand-copied a replacement. **FACT**
- **Why:** the Modrinth App moves new jars into `store/content/objects/<sha512>` after exit.
- **Avoid:** search the content store before assuming data loss.

### Stale standalone jars shadow folded-in code
- **What:** a leftover `twitch-minecraft-bridge` jar caused a ClassCastException. A leftover `prestige-colors-0.1.0.jar` also shadowed the folded-in feature. **FACT**
- **Avoid:** when a standalone mod is folded in, remove its jar. Keep exactly one FishModDungeons jar per mods folder.

### Handing over a stale artifact (09-08)
- **What:** Eli was sent pre-QA and pre-rename addon jars. **FACT**
- **Avoid:** rebuild from committed HEAD, and check that the jar mtime is later than the last commit, before handing it over.

### Building against a newer loader than users run (09-27)
- **What:** compiling against loader 0.19.5 encoded `@Redirect.at` as an array, which crashed a 0.19.3 client (MixinExtras ClassCastException). **FACT**
- **Avoid:** keep the compile-time `loader_version` at the lowest one in `depends`. After a toolchain bump, test on the lowest supported loader.

### GL state restored through a cache (09-29/30)
- **What:** the first GUI-blank fix made the UI work without ImmediatelyFast but broke it with IF installed. **FACT**
- **Why:** `GlStateManager` skips a bind it thinks is redundant, and the UI renderer had bound its FBO with raw GL. Details in [[Technical Gotchas]] and [[UI Renderer]].
- **Avoid:** for any render-state change, test with ImmediatelyFast both **on and off**.

---

## 4. Trusting notes over code

### "Already fixed" that wasn't on main (09-05 → 09-08)
- **What:** the vault said EstTotal-1/2/3 and the LagTracker fixes were done. They existed only in uncommitted builds and on other branches. **FACT**
- **Avoid:** `git grep` the target branch before trusting any "fixed" claim, including claims on this vault's pages.

### Memory that goes stale
- **What:** memory still says FishDiag, the ImmediatelyFast FBO fix, PR #59 and PB/inv-search/SkySoft are unmerged or open. On 2026-09-30 all of them are on `origin/main` (**FACT**, see the contradictions list in [[Open Threads]]). The `dual-module-mirroring` memory still tells sessions to mirror into the frozen 1.21.11 module.
- **Avoid:** treat memory and vault status lines as a snapshot. Check `git merge-base --is-ancestor <branch> origin/main` or `git cherry origin/main <branch>` before acting on them.

### Main that doesn't compile (09-26)
- **What:** PR #77, an audit cleanup, left `origin/main` uncompilable (an orphan `SoundManager` line and a `DungeonWaypoints` JVM signature clash). Nobody noticed, because the deployed 1.5.32 had been built before #77 merged. PR #78 fixed it. **FACT**
- **Avoid:** after any large cleanup merge, build `main` before assuming it's green.

---

## 5. Guessing without data

### Guessing Hypixel strings, coords and mappings
- **What:** Kuudra and Mining regexes, arena coords and command names were estimated, and the test plan told Eli to run `/mining config`, a command that doesn't exist. An earlier FishModAddons port guessed `Items.NETHER_STAR` when the right item was `PLAYER_HEAD`. **FACT**
- **Avoid:** for ported features, pull the reference source (Odin / NoammAddons / blade / SBO / SkyHanni) instead of guessing. For mappings, read the actual mapping tables. Check command literals against source before writing any user-facing steps.

### Heuristic fixes flip-flopping (SS, BloodSolver, Goldor splits)
- **What:** Simon Says went through about 10 rewrites (1.0.254–1.0.278). A data-driven BloodSolver fix was reverted by an audit agent that labelled it a "regression". Goldor splits were patched without log data. **FACT**
- **Why:** each session re-guessed instead of collecting evidence. Audit agents also can't tell deliberate behaviour from bugs.
- **Avoid:** get one real log first (a `[Section]` trace, a packet dump) or port 1:1 from the reference. Don't let cleanup agents revert logic that has a data-backed comment or commit behind it. Use [[Diana]]'s singleplayer datapack harness for features you can reproduce offline. For player reports, ask for a `/fm debug` FishDiag report.

### Shipping untested features in bulk
- **What:** the 08-26→30 mass port shipped about 24 features untested, and many were broken in-game. The addon sprint shipped 4 green-but-untested addons, and 3 had significant bugs on first play. **FACT**
- **Avoid:** "build is green" is not "works". Label untested work clearly in the report and in [[Open Threads]]. QA should include runtime checks, such as the Kotlin `object` entrypoint adapter and instantiation.

### Chasing infra that isn't in the repo (09-08)
- **What:** about an hour went into looking for the test board inside the `fishmod` worker. It's actually `fmd-board` at `C:\FishMod\worker-board\`. **FACT**
- **Avoid:** for tooling outside the repo, ask Eli or search transcripts within the first couple of tool calls. See [[Test Board]].

---

## 6. Rule breaches Eli had to correct

- **Pet Keybinds auto-opened `/pets`** (09-24), which Eli called "a cheat". Now a hard rule: no GUI automation.
- **Claude merged PR #22 itself** (09-21), which is against the "Eli merges" rule. **PR #20 was opened from a stale remote branch** and shipped first-draft badges.
- **Verbose AI comments crept back** after the first strip (09-20 recurrence). Default to no comments.
- **Customisation was cut as "slop"** from the Diana plan (09-28). Eli wants it kept.
- **Board edits** kept being offered after Eli said he handles the board himself (09-24).
- **Eli's manual Diana edits were overwritten or ignored** because sessions didn't check the main checkout's uncommitted tree first (09-30; he had reverted arrow fallbacks to SBO-strict).

All of these are now standing rules in [[Working With Eli]].

---

## 7. October pass (mined 2026-10-17 from sessions 09-12 → 10-17)

Full catalogue: [[Issues Log 2026-09-12 to 10-17]]. Worker incidents: [[fishmod-worker]].

### Overwriting a newer artifact with an older one (9 times: jars 09-26, 10-04, 10-11; worker 10-02, 10-10, 10-11, 10-15)
- **What happened:** Claude installed or deployed its own lower or branch-only build over a newer one from another session, so features "vanished". For the worker, the "newer" copy was the live Cloudflare code, and even the git `_live_snapshot` folder was wrong.
- **Why:** version bumped from the worktree's own file; no check of what was installed or live; last deploy wins.
- **How to avoid:** before any deploy list both mods folders (or download the live worker), read the installed version, confirm its commit is an ancestor of what you build, merge the other branch first. Never install a lower version. Live-worker deploys need Eli's explicit OK.

### Guessing Hypixel data instead of reading it (Rift 10-15, mining 10-03, Diana, Necron, profit boxes)
- **What happened:** Rift Bacte/snake/crux/echest needed about 8 rebuilds (1.6.159 → 1.6.183). Real data was chat lines, slime sizes 3/4/5/6/13 and names like `B 1,000/1,000`, not the guessed nametags. Necron timers were fixed offsets; profit-tracker boxes used estimated coordinates.
- **How to avoid:** get the feature's debug output (`/fm rift debug` etc.) or Eli's screenshots first, then write detectors. Derive timers from chat or boss-bar events. Take coordinates from a probe, never estimates.

### "Fixed" claimed before it was seen running
- Water-over-boxes, quiz highlight and others were reported fixed while untested. Diana flicker took 5+ reports and replay videos; 1.5.94 fixed the symptom, 1.5.95 the cause (cleanup deleting guesses outside a height range every tick).
- **How to avoid:** reproduce from Eli's replay or log first, say "built, untested" plainly, and check sibling code paths (arrows vs spade) when fixing one.

### Testing only with your own settings
- The `/d`, `/ping`, `/f7` crash (1.5.43) only hit other players: with toggles off, FishMod forwarded the command and Fabric re-dispatched it in a loop. Eli had every toggle on.
- ImmediatelyFast: fixing it for users without the mod broke it for users with it (1.5.78 hooks `createRenderPass`).
- **How to avoid:** test with a default config and with/without conflicting mods, in singleplayer on the alt account.

### Scripted edits corrupting source
- Kotlin "Unsupported escape sequence" build failures four times (09-30 → 10-16): python/heredoc edits dropped a backslash level in regex strings.
- **How to avoid:** use the Edit tool or raw strings; grep for stray `\d` after any scripted edit.

### Deploy-step reliability
- "Game closed, deploy it" said about 25 times; the inline PowerShell javaw check threw a Substring exception in three sessions; a stale jar was copied after a merge bumped the version.
- **How to avoid:** use `tools/check-game-closed.ps1` (see [[Working With Eli]] and the project CLAUDE.md), re-read `gradle.properties` after every merge, copy the newest jar by glob.

### Fresh worktrees fail to build
- `./gradlew` died in about 15 sessions on `dungeons-1.21.11/build.gradle` line 2 (`loom_version`) or in the wrong cwd. `gradle.properties` is gitignored per module, and the frozen module is still in settings.
- **How to avoid:** build on base main in `D:\FishModDungeons` (the standing rule), and from `dungeons-26.1.2`.

### Things Eli did not want
- Real name in generated strings (use RedFish/RedFish2471), vague redesigns ("revert it"), extra buttons, comment spam, a second brain instead of extending the vault, features placed public vs private without asking.
- **How to avoid:** do the minimum UI requested as pictured, ask about public vs private, keep comments to one line.

### Keybind state lost on restart (2026-10-24)
- A first fix assumed `keybinds.txt` is restored "over options.txt"; it is restored *before* options load. The real gap: KeyMapping names with `:` (Pet/Garden keys) can't round-trip through `options.txt`.
- **How to avoid:** check load order in the mapped jar (`javap` on `Options`) before reasoning about which store wins; never put `:` in a KeyMapping name.

### Rift hedgehog card: no reference implementation (2026-10-24)
- Neither SkyHanni nor Skyblocker has Rift hedgehog code, so no vulnerable/raging strings exist to copy. `gh search code` also hits the shared 5000/hr API limit fast.
- **How to avoid:** `git clone --depth 1 --filter=blob:none` + `git grep` finds reference code without the API; if nothing exists, ask Eli for `/fm rift debug` output per state instead of guessing.

### Achievement progress travelled with a copied config folder (2026-10-27)
- **Symptom:** T1gooby and Gemstone__mining showed ~5400 AP and 7 secrets on the leaderboard after installing Eli's modpack.
- **Cause:** `AchStore` kept all progress in one un-keyed `config/fishmod/achievements.json`, and the modpack shipped Eli's copy. Every submit sends the logged-in UUID with whatever totals that file holds, and the worker overwrites the row.
- **Fix (1.6.384, cbfe44f3):** per-UUID files `config/fishmod/achievements/<uuid>.json` with an `owner` field; `AchStore.bind()` swaps files when the account changes; submit and API sync skip unless `AchStore.ownedBy(uuid)`. The old file is claimed once by the first player, except Eli's known saves (matched by their `since` stamp), which only his UUID may claim. Server rows stay `hidden=1` until those players run the fixed build.
- **How to avoid:** anything personal or uploaded (stats, progress, PBs) must be keyed by the Minecraft UUID, never stored as a single per-install file. Assume config folders get copied between players (modpacks, Prism exports).

### Infra and harness papercuts
- Classifier "no verdict" refusals (12 in a row), `wrangler d1 execute` 7403 auth errors, `gh release create` 422 for a local-only `target_commitish`, "Filename too long" on checkout (`git config core.longpaths true`), subagent reports flooding context (cap fan-out, ask for short reports), usage limit hit mid-deploy (record state before long steps).

- **Expected failures are not FishDiag errors (2026-10-25):** `PartyUtil.4` (Mod API not registered / off Hypixel) and `PartyFinder.15` (PF stats lookup for nicked/unknown/rate-limited names) spammed the test board. Gate on Hypixel server IP, back off exponentially, log once at info; real HTTP/parse faults already report in `HypixelApi.24-26`. Parallel gradle builds from several sessions hit "Not enough memory"; rerun one module at a time with `-Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs=-Xmx3g`.

- WitherDragons.6 / DragonCheck.2: the ping-tick spawn timer can expire before the real dragon entity arrives; treating expiry as a fault then flagged the late spawn as a second fault. Treat a timer as a hint, recover late events from sidebar state, and report only what cannot be explained.

- **RULE: when several sessions fix things in parallel, do not build until all of them are done (2026-10-25).** Eli's call after 8 java processes (3 gradlew clients, 4 Gradle daemons, 1 Kotlin daemon, ~9GB) ran at once. Concurrent builds OOM the shared Kotlin daemon ("GC overhead limit exceeded"), fight over `build/classes/kotlin/main/fishmod` ("Could not delete") and committed fixes without a verified build (dragons `8497c47b`). Parallel sessions edit and commit only; one build of both modules happens once, after every session is idle (the Stop hook auto-build counts as a build, so expect it to collide too). A failure with no `e:` line is not a code error: wait for quiet, then rebuild once. Brief every fan-out session with "no build, report when committed".

- **Shared git index swallows staged deletions (2026-11-01, PV Kotlin port):** a session ran `git rm` on Home*.java and kept writing the .kt files; another session's plain `git commit` in the same checkout took the staged deletions into its own commit (2a070a10) and main failed to compile until the .kt landed. With several sessions in one checkout, never leave anything staged: delete files with plain `rm` and commit with an explicit pathspec (`git commit -m ... -- <paths>`), which also picks up deletions.

## Connections

- [[Working With Eli]] · [[Open Threads]] · [[Technical Gotchas]] · [[Session History]]
- [[Multi-Session Sprint Retro]] · [[Main Consolidation 2026-09-09]] · [[Build & Release]]
- [[Development Setup]] · [[Known Bugs]] · [[Test Board]] · [[UI Renderer]] · [[Diana]]
- [[Maintaining This Vault]]

### 2026-10-17 housekeeping session
- **Moved config out of CLAUDE.md before checking the target existed.** `E:\prism-agent` was missing, so the PrismAgent notes nearly vanished. Check the destination path exists before deleting the source; they now live in `~/.claude/prism-agent-notes.md`.
- **Unquoted filename in a heredoc** (`cat > Session Log.md`) created an empty file named `Session`. Quote paths with spaces.
- **Ambiguous request ("value bugs").** Read it as vault bugs and acted; confirm when a short message could mean code or vault.
- **Don't grep for code first.** Use `tools/code-index/where.py` (see [[Finding Code]]).

### 2026-10-17/18 profile viewer port
- **Read [[UI Renderer]] before styling.** It already said vanilla-rounded corners read as pixelated and overlay shapes cover items. I built a vanilla glass look first and got "I can count the pixels" three times.
- **Remove a feature, then check what else needed it.** Deleting Mining/Museum/Events pages broke essence, networth and the loader; restoring them later cost a second port pass.
- **A bulk colour swap (dark -> translucent) turned opaque popups and tooltips see-through.** Swap constants by role, and keep a separate opaque style for anything floating.
- **Don't reposition overlays with guessed coordinates.** Two wrong tries (top-left, then bottom-right); the original `width/height` was right.
- **Minecraft's TTF font provider only loads TrueType outlines**; a CFF `.otf` fails silently and every glyph becomes a box. Check the log for "Failed to load builder".
- **"Doubled text" came from my own fake bolding (second draw offset).** Fix fonts with a real weight, not a double draw.
- **The proxy, not the client, caused most "request failed".** A cold profile takes up to ~100 s; test the endpoint with curl before touching client timeouts.
- **Close FishTest before deploying**; ask once, then check the process before copying the jar.

### 2026-10-18 leaderboard text hidden by leaves (cost ~10 builds)
- **Find what draws the thing before fixing it.** "Top Secret Hunters" is FishMod's own `AchLeaderboard` world text. I assumed a Hypixel armor stand, and a grep for "hunters|leaderboard" at the start had already listed `AchLeaderboard.kt`. Run `where.py` on the visible text first, and read every file a search lists.
- **If the text is visible in game, grep the exact string before theorising.** It was in the source, split across a title string. I searched only after the fifth failed build.
- **Debug the entity or draw path, not the symptom.** `/fmdev holo <text>` and `/fmdev holotrace` answered it in two runs. Add the probe before the first fix, not after five.
- **Mixing up pass order and depth.** Text hidden by water/glass/leaves is a draw-order problem (queue drains before translucent terrain), not depth testing. Turning depth off changed nothing. See [[Technical Gotchas]] and the gizmo/END_MAIN note in [[Rendering System]].
- **Mixin target mismatch only shows at startup.** The NameTag mixin compiled on 26.2 and crashed the client because `renderTranslucent` no longer exists. Check the target in the 26.2 jar with `javap` first, and treat a clean compile as proof of nothing.
- **Never drive Eli's desktop with SendKeys.** `tools/fmtest.ps1` types into whatever window has focus; it hit Discord. Only run it after confirming the game window is in front, or not at all.
- **Don't take over every entity of a type.** Hijacking all armor-stand nametags broke NPC names. Scope a takeover to the case that is actually broken (here: label blocked by foliage/water).
- **26.2 has no `MultiBufferSource`.** Immediate-mode text is gone; late draws on 26.2 go through the HUD (project to screen) or the submit queue. The 26.2 `WorldText` is the reference.
- **A manual build while the Stop hook or another session builds fails on file locks** (Unable to delete build/classes, Failed to modify jar manifest). Those are not compile errors: wait for the other build to finish and retry once, never loop. (Board Reports, 2026-10-24)
- **Don't `cd` in a PowerShell call** in a shared checkout; it moves the session cwd for every later relative path. Use absolute paths.
- **Fabric's keybinding helper is `KeyMappingHelper` (`client.keymapping.v1`) on 26.x**, not `KeyBindingHelper`. The old name fails to compile; check an existing import first. (AutoSprint, 2026-10-24)
- **Setting `isSprinting` in END_CLIENT_TICK is a tick late.** That tick's movement is already sent, so the server sees sprint start after the move; replay data showed it rubber-banding spring boot launches in M7 P1. To auto-sprint, hold `keySprint` at START_CLIENT_TICK so vanilla starts sprint in the same tick it sends movement. (AutoSprint 1.6.332, 2026-10-24)

- Rift Black Lagoon heads solver (2026-10-24): neither SkyHanni nor Skyblocker implements it (only an RPC area name), so no code was written. Check the reference repos by `git clone --depth 1 --filter=blob:none` + `git grep` first; `gh search code` hits the shared rate limit. Needs Eli's debug data, see side-session-notes rift-black-lagoon-heads.md.
- Rift burger warning (2026-10-24): card said "burgers" with no wiki hit; the real item is McGrubber's Burger. Clone SkyHanni/Skyblocker shallow and `grep -ri` the feature word first. When sibling sessions leave uncommitted hunks in shared files, `git apply --cached` of filtered `-U0` hunks mangles interleaved lines; commit only after they commit, or commit the shared files together.

- 2026-10-24 (Caducous stem): shared Rift files were edited by other sessions at the same time; a plain `git add` would have committed their half-done hunks. Commit only your hunks with `git apply --cached --unidiff-zero` on a hand-written patch. A build failing in another session's new file does not prove or disprove your code.

- 2026-10-24 (Leech Supreme): with several sessions building at once the shared Kotlin daemon throws "GC overhead limit exceeded" and 26.2 hits stale-build-dir NoSuchFileException. That is not a code error; check `e:` lines before debugging, and build when other sessions are idle. Sibling hunks land in the same shared file (RiftMenu/Settings/Rift.kt): stage only your hunks via a filtered `git diff -U0` + `git apply --cached --unidiff-zero`.

### Rift hay session (2026-10-24)
- GitHub search API rate limit is shared across sessions; use web fetch of github tree pages instead of `gh api search`.
- Another session's half-edited file (RiftBurger.kt) failed the shared build; check the first `e:` line is yours before touching anything.

### Rift Guide overlay (2026-10-24)
- Rift Guide overlay (`RiftGuide.kt`, bd3cc2cb): detects a guide sub-page by slot 40 back button lore "To Rift Guide", unfinished task = last lore line "✖ Not completed yet!" (from SkyHanni `HighlightRiftGuide.kt`; Skyblocker has nothing). Menu title unconfirmed; list is in-memory only, pages load when opened. Both-module build could not be verified: shared build dir collided with other sessions' builds and RiftBurger.kt failed (not mine). Staged only my hunks by building the index from `git show HEAD:file` + my lines.

- ArrowsDevice.6 spam: the aim solver ran on a just-MARKED block (hit), so with all pair neighbours marked no pair covered it. A hit block is not a target; recompute for the live target instead and only report when an unmarked target is uncoverable.

- AutoRequeue.3 (floor -1 at requeue): floor read from chat/sidebar at EXTRA STATS time can be gone (sidebar changes at run end). AutoRequeue now caches floor + master mode each tick during the run and falls back to the cache. Cause not confirmed on Hypixel; built, untested.
- AutoRequeue.2 / DungeonRequeue.2 (team count never recorded): `DungeonPlayers` only fills when a map feature is on (all default off), so players=0 is normal there, and a join after boss start leaves the pre-boss count at 0 with players>0. The diag now fires only for an empty roster with a map feature on. Trap: `DungeonPlayers` never removes anyone until level change, so `teamShrank` in `DungeonRequeue` can't currently trigger. Don't feed the roster with the map off without checking LeapMenu, which treats a roster hit as having a map position. Process: when running the one allowed build, keep the full output in a file; a `tail` lost the failure cause.

- Hypixel chat lines can carry double spaces between sentences (Spirit Mask "Second Wind Activated!  Your Spirit Mask..."). Normalize `\s+` to one space before matching exact-text regexes. Parallel sessions building at once OOM the shared Kotlin daemon; rebuild when quiet.

### Overnight audit (2026-10-25)
- 5 parallel slice sessions (dungeon, mixins/render, non-dungeon features, PV/cosmetic/twitch, config/utils+resources) produced 35 fix commits (03db6817..7ddc8886) plus `tools/smoke-test.ps1` (11888748). Jar 7,909,902 -> 7,605,560 B (26.1.2), 7,918,834 -> 7,614,486 B (26.2); most of it from subsetting the Inter fonts.
- Pro plan: 5 Opus sessions burn a 5-hour window in about 1.5 h and roughly 10% of the weekly budget per window. Use a CronCreate watchdog that resumes sessions after the reset; a watchdog turn cannot start NEW sessions (only message existing ones).
- Sessions sharing one build dir collide (file locks, Kotlin daemon OOM); the Stop-hook auto-build then reports false failures. Check `e:` lines first.
- A peer session correctly refused to launch Minecraft on the orchestrator's word; run launches from the session Eli gave permission in.
- Testing: `tools/smoke-test.ps1 26.2 FishTestSurvival 90 [on|off]` passes (join, no FishMod errors, all 879 toggles on and off). `FishTestSurvival` = FishTestFlat copy patched to survival + peaceful via level.dat/playerdata NBT bytes. The FishTest (26.1.2) instance hangs on a white screen after "Sound engine started" even with the pre-audit jar (environment, not the mod), and `:dungeons-26.1.2:runClient` crashes in DevAuth (missing org.apache.http). 26.1.2 is only build-verified.
- dynamic_fps stalls loading while the game window is unfocused; the smoke test refocuses it.

- Network cards (2026-10-28): fan-out features (RemoteNicks looked up every chat word) must cap in-flight requests; the shared HTTP/2 client fails everything with "too many concurrent streams" past the server limit. A fetch failure must not overwrite last good data (BadgeRegistry wiped badges on an empty fetch). Shell slip: `cat > file` with no heredoc waits on stdin and hangs the command.

- FishSim restart (2026-10-29): closing the server terminal tab kills Paper without asking and kicks Eli if he is on it. Check the console for a "joined the game" with no matching "left", and tell Eli before restarting.

- FishModAddons SafariUtils port (2026-10-30): read a third-party jar with `javap -c -p` plus a small constant-pool reader. javap prints non-ASCII string constants as `?`, and Hypixel nametags use private-use glyphs (``), so decode the pool as UTF-8 before matching on those strings. Eli's Skyblock instance runs the **26.2** addon jar, so check the mods folder before assuming 26.1.2 is the one in use. Mirroring to 26.2: `git apply` fails on files where 26.2 already differs (TeamColor instead of ChatFormatting, `mc.gui.hud`, `TeamColor.getSerializedName()`, no `options.hideGui`), so copy new files and insert the changed blocks instead of patching.

- PV de-namespace (2026-11-01): a `static final` field that calls `FabricLoader.getInstance()` breaks the PV unit tests (no loader in tests, ExceptionInInitializerError). Wrap it in try/catch with a fallback path. A bulk rename of `"betterpv pv` to `"pv` duplicated existing `/pv` branches in ProfileViewerOpener; grep the result of every bulk string rename before building.

- PV api Kotlin port (2026-11-01): with several sessions converting packages at once, building in the shared checkout fails on their half-done files (redeclarations, missing files mid-move, locked Kotlin incremental caches, OOM). Verify in an isolated copy instead: `git archive HEAD | tar -x` into the scratchpad, copy your changed files over, build there. Calling Java record accessors from Kotlin with property syntax fails when the record also has a same-named non-component method (`Leveling.Progress.fill()`); call those as methods.

- Blade deconflict (2026-11-01): a Python helper written through a bash heredoc lost its backslashes again (syntax error); write helper scripts with the Write tool too, not only source. Renaming `@ConfigValue` fields changes JSON keys (practical-config stores flat field names, HUD boxes also save their label in `info`, enum settings save the constant name), so every field or enum-constant rename needs a `ConfigKeyMigration` entry or users lose the setting. Reflection strings (`Class.forName("fishmod...")` in tests, `ColorDefaults.of(Cls, "field")`, `Cls.Companion::field`) slip past token renames; grep the old names after every bulk rename.

- PV gui root Kotlin port (2026-11-01): a public Kotlin function cannot take a Java package-private type (`exposes its 'public/*package*/' parameter type`); convert the package-private class in the same commit or keep the caller in Java until then. Mojang's `Font.width` and `Component.literal` are annotated non-null, so Kotlin wrappers can't take `String?` for them, while `GuiGraphicsExtractor.text` accepts null. Kotlin reads Java record components with property syntax (`cat.id`), which keeps working when the record later becomes an `@JvmRecord data class`. Bash heredocs with apostrophes in the content broke `bash -c` again; write source files with the Write tool.

- PV data A-L Kotlin port (2026-11-01): an `internal` function marked `@JvmStatic` gets a mangled JVM name, so the reflection-based `Api2ParsersTest` (looks methods up by name) failed on `EventsSnapshot.parseChocobits`; use `private @JvmStatic` for helpers that tests call by reflection. Once a Java class becomes Kotlin with `@get:JvmName("x") val x`, every Kotlin caller written as `obj.x()` breaks ("cannot be invoked as a function"); fix them from the compiler output (line:col points at the name, drop the `()`), and review the diff after. Writing scratch files with Python on Windows needs `PYTHONUTF8=1`, else cp1252 fails on `→`/`✪`. Java `String.split` drops trailing empty parts and Kotlin `split` keeps them; add `dropLastWhile { it.isEmpty() }` where the part count matters.

- PV gui inventories/nav/pets/bestiary Kotlin port (2026-11-01): the shared checkout has ONE git index, so a plain `git commit` swept another session's staged `git rm` of gui/home/*.java into an unrelated commit (2a070a10) and left main without HomePage until that session committed its .kt. Always commit with an explicit pathspec (`git commit -m ... -- <your paths>`) and check `git show --stat HEAD` after. A Kotlin `data class` with `@JvmOverloads` defaults after a `Runnable` parameter breaks Kotlin trailing-lambda callers (the lambda binds to the last parameter); keep explicit secondary constructors that end in the `Runnable`. Long base64 constants were spliced from the Java source with `sed r <file>` and verified with `grep -F` instead of being retyped.

- PV gui collections/crimson/dungeons/essence Kotlin port (2026-11-01): `Component.translatable(key, vararg args)` takes non-null `Any` in Kotlin, so a record field typed `Any?` fed into it fails to compile; type it `Any` when every caller passes a value. A clean-clone build can fail on another session's half-landed work (here HomePage, then inventories `RARITY_WORD`); kotlinc errors list all Kotlin files at once, so if only foreign files are named your Kotlin compiled, and javac errors mean kotlinc passed.

- **Gradle exit code hidden by `| tail` (2026-11-02):** `./gradlew ... | tail` reported exit 0 on BUILD FAILED and cut off the errors. Read the BUILD line, and do not trust a build while other sessions compile in the same checkout: they fight over `build/classes` ("Unable to delete directory") and see each other's half-edited files.

- **Gradle output cut with a tail (2026-11-02):** with a one-build limit, piping the build through `Select-Object -Last 12` lost the compile error of the only allowed build. Send full output to a scratch file, then read the errors from it.

## 2026-11-02 — check what's deployed and what's in the shared D1 before adding worker features
- I built the leaderboard on `main`, but the live worker runs from `feat/achievement-leaderboard`. The deploy failed on the missing `CosmeticHub` class, which luckily stopped a rollback. Before deploying fishmod-worker, deploy from the branch that's actually live.
- I reused the table name `lb_players`, which a parallel build (`fishmod-worker-lb`) already owned in the shared `fishmod-nicks` D1. `CREATE TABLE IF NOT EXISTS` silently kept the other schema. Before naming tables, check `sqlite_master` and grep sibling `D:\FishMod\fishmod-worker*` folders.
- "Seed if the queue is empty" broke because live traffic filled the queue first. Use an explicit marker instead.

## 2026-11-03: the dashboard session cookie is scoped to Path=/dashboard
- The leaderboard admin bypass (`readUser`) never worked on `/leaderboard/*`, because the browser only sends `fmd_dash_sess` to `/dashboard/*` pages. Eli saw the progress panel fail. Any admin-only endpoint must live under `/dashboard/` (the leaderboard now uses `/dashboard/lb/{data,status,publish}` and `/dashboard/leaderboard`). Check the cookie path before reusing the session outside the dashboard.

## 2026-11-03: the leaderboard crawler read ~15B D1 rows a day (billing risk)
- The cause was `DELETE FROM sbl_stats WHERE uuid = ?` with PK (cat, uuid), so there was no usable index and every player save scanned the whole table. A side session then ran 5 saves in parallel, which tripped "D1 DB is overloaded".
- The fix: index every column used in a WHERE (`sbl_stats(uuid)`, `sbl_players(name NOCASE)`), diff-only writes, keep ORDER BY index-only, cache whole-table COUNT/GROUP BY for 10 min, cap rank counts with `LIMIT 2500` in a subquery, and serialize D1 work while letting Hypixel fetches run in parallel.
- Prevention: after shipping any per-minute D1 job, check `npx wrangler d1 info fishmod-nicks` (rows_read_24h / rows_written_24h) within the hour.

## 2026-11-03: early crawls that saved nothing were stuck at the back of the queue
- The first test runs marked players as crawled even though saving failed, so Eli showed "0 data". Because the queue is ORDER BY last_crawl, those players wouldn't come back until everyone else had been crawled. When a fix changes what a crawl stores, requeue the players with empty or old-format data (here: `last_crawl = 0 WHERE stats = '{}'`).

- **D1 "overloaded" from per-minute full scans (fishmod-worker leaderboard, 2026-11-04):** rebuildOne scanned all ~220k sbl_players rows every minute next to the crawl writes, and the dashboard polled an uncached full-scan COUNT. Fix: skip rebuilds until complete_at (lists are hidden anyway), cache lbReadiness 60s. Rule: never run full-table work on a schedule when its result is hidden; cache anything a page polls.

- **Leaderboard crawler froze all of fishmod.dev (2026-11-05):** sbl_players grew to ~670 MB of JSON in the shared fishmod-nicks D1; D1 runs one query at a time per database, so the crawl/rebuild scans queued every other request (roadmap API timed out for 10+ min, cards looked "gone"). Fix: moved sbl_* to its own D1 fishmod-sbl (binding SBL). Rules: give big background jobs their own D1; `wrangler d1 export` rows over ~100 KB fail import with SQLITE_TOOBIG (skip and rebuild them); verify by pausing the suspect (LB_PAUSED=1) and re-timing.
- **Remote terminal (D:\HTML claude script) broke from my edit:** a Python heredoc turned D:\FishModDungeons into an escape, so node-pty failed with error 267 on every connect; and a server started from Claude shell inherited NO_COLOR. Check the server log after any edit; the server now forces TERM/COLORTERM/FORCE_COLOR.

## 2026-11-05: Gamble Points build (mod + worker built by two separate agents)
- **Mod and worker disagreed on response shapes** (leaderboard bare array vs object, crown `{uuid,name}` vs `crowned`, heartbeat `credited` vs `ok`/`awarded`). The mod agent coded against a prose contract with no exact response JSON and guessed the rest. Fix: give both builders the exact request AND response JSON, or have the later one read the other's code first. Always diff the two before the build.
- **`mc.screen` does not exist in 26.1.2 Kotlin:** use `fishmod.compat.curScreen` (import it). Search `where.py` / grep for how existing code reads a Minecraft field before writing it.
- **Check for an existing system before adding a mixin:** the nametag/tab/chat crown needed no new mixin, because `BadgeManager.badgesFor` already feeds all three surfaces. Reusing it also avoided other sessions' uncommitted mixin files.
- Session tool was unavailable, so helpers ran as background subagents; they cannot be opened and read like sessions.

## 2026-11-06: SkyBlock leaderboard crawl and page (fishmod.dev worker)
- **I deployed without diffing the live worker:** I told Eli the live version "came from this directory" without checking, then wrote and deployed over a page that may have been restyled elsewhere. Every deploy is `Source: Unknown`, so before `wrangler deploy` list deployments, check which tree the last one came from, and ask Eli if another session or folder (`D:\fishmod-redesign`) could have deployed.
- **"Crawl done" does not mean lists are ready:** public lists only show if rebuilt after `complete_at` (99% of Elite players saved). Stale pre-completion lists look like tiny player counts. Missing/stale lists must rebuild first, in page order, or the main boards come last behind the mobkills tail.
- **The rebuild is the D1 read cost, not the crawl:** each scan reads ~750k rows (measured), one category per scan = about 32B reads/month at 1/min. Rebuild several categories per scan (`LB_REBUILD_BATCH`, default 5).
- **Queue order hid the stall:** `ORDER BY last_crawl ASC` over ~587k rows let 774 untried Elite players wait behind discovered co-op players; the ETA counted claims, not saves. Elite players without stats now go first (partial index `sbl_players_unsaved`).
- **Fake ETA / silent stops:** the crawl recorded nothing when it bailed (429, D1 error, time limit). It now writes `sbl_usage` per 5 min plus `last_run`/`run_start` meta; shown on the leaderboard page for admins.
- **Design mockup lives in the Claude canvas, not on disk:** "FishMod Website Redesign" artifact, artboard `Leaderboard.dc.html`. Pages should use the shared kit (`/site.css` + `/site.js`) like Streams/Releases/Installs.
- **Heads:** browsers can block third-party image hosts; serve them same-origin (`/head/<uuid>`, cached a day).
- **Tooling:** wrangler `d1 execute --file` prints no rows (use `--command --json`); the auto-mode classifier blocks my production reads, so hand Eli a script with `!`. Heredoc python with quotes failed once; use the Edit tool.
- **Missing top players were stale saves, not discovery:** bobsteve21 had 23.7B Catacombs XP but no M7/M6/master completions, saved Nov 3 before those stats were added. Check a "missing" player's saved row (and crawl date) before redesigning discovery. Fix is the stalest-first recrawl; add a stats version so a new stat requeues old saves automatically.
- **Pausing was the wrong call:** I committed `LB_CRAWL_PAUSED=1` on Eli's say-so before knowing the cause; recrawl was the fix. Diagnose first, and never leave a pause in `wrangler.toml` that a later deploy could ship.


## 2026-11-07: UI bugs came back
- **Config GUI text-input title detached from its box fixed 11-05 (`c0096372`), regressed by 11-07:** the fix lived only in a commit message. Every fixed bug now gets a rule in the matching vault page (see [[UI Conventions]]) in the same session, plus a grep for the same pattern elsewhere.

## 2026-11-07: git untrack commit silently kept the files
- **`git rm --cached X` then `git commit -- X` re-adds X:** a pathspec commit uses `--only` and reads the working tree, where the file still exists, so only `.gitignore` got committed and the .md junk stayed on GitHub. Untrack with `git rm --cached` and a plain `git commit` (check `git diff --cached --quiet` first so nothing else is staged), then confirm with `git ls-tree --name-only origin/main`.

- 2026-11-08 A6 cleanup: `git add <dir>` swept another live session's fishing files into my commit; always add explicit paths when other sessions write. Mixins with different @Mixin targets (shadow trio, NoRecipeBook pair, ContainerScreen) cannot merge; a 26.3 override copy of a merged mixin must get the same members.

## 2026-11-08: built before /ship
- **Symptom:** I ran a two-module gradle build after a one-line change; it took 10+ minutes, failed twice on daemon stops, and `/ship` builds again anyway. Eli asked why.
- **Root cause:** the old rule "a session builds once when its work is done" in `CLAUDE.md`.
- **Rule now:** no session ever builds. Commit "not built", report "not built, untested", leave it to `/ship`. Only build if Eli asks in that session. Written to global and project `CLAUDE.md`, memory `never-build-before-ship`, and [[Working With Eli]].

- **Tooltips from ScreenEvents.afterExtract (2026-11-08):** setTooltipForNextFrame there is never drawn, the deferred pass already ran; call ctx.extractDeferredElements(mx, my, 0f) right after (see StorageOverlay.extractCarriedLayer). Also dungeons-26.2 carries its OWN copy of GuiScoreboardMixin (Hud, not Gui): any change to a mixin must be mirrored there, check `find dungeons-26.2 -name <Mixin>` first. Skyblock runs the 26.2 jar.

## 2026-11-09: DragonCheck false reports (#492/#493/#522/#528/#529)
- **Symptom:** auto-reports "DragonCheck.1 spawn particle at unknown spot" (26,69; 39,48; 83,75) and "DragonCheck.2 dragon spawned with no spawning dragon (all DEAD)".
- **Root cause:** the extra spawn-animation flame bursts land inside a dragon's pit box but outside its small xRange/zRange spawn spot; and a stray/re-tracked ender dragon entity arriving while every dragon is DEAD is not a tracking fault.
- **Fix:** DragonCheck.1 only fires when the spot is in no pit box; DragonCheck.2 only fires when some dragon is SPAWNING/ALIVE. Rule: a diag report must describe a broken invariant, not an unmodelled-but-harmless packet.

## 2026-11-09: fishing loot leaking into mining/farming profit trackers (#499)
- Symptom: fishing drops caught on a mining island showed in Mining Profit. Cause: trackers gate on island plus sack membership; an item in both a mining and a fishing sack, or any sack item when NEU sack data is not loaded, passed.
- Fix: `ProfitTrackerKit.isFishingLeak(id, now, lastOwnActivityMs)` rejects fishing-sack items (or everything if sack data missing) while a rod cast is recent (`FishingTrackerStore.castRecent`) and the tracker's own activity (mining: last block break) is stale. Any new non-fishing tracker's sack handler must call it.
- Not done: chat-line loot (corpses/nucleus) is activity-specific already; only the sack path was ambiguous.

## 2026-11-09 roadmap #490/#521/#517
- PV attribute shard "Not maxed" list had a Reptiloid-only exclusion; unobtainable/non-syphonable shards (Chameleon) need to be added to that same `skip` check in `AttributeShardsPage.java`.
- SkyItemList search box swallowed every key while focused (`keyPressed` returned true), so Ctrl+V/A/Backspace did nothing. Handle the Ctrl modifier before the swallow.
- Kotlin strings: `"\p{..}"` is an invalid escape; use filter/isISOControl instead of regex.

## 2026-11-09: leaderboards ignored FishMod nicks (#520) and live socket silent report (#498)
- #520 symptom: leaderboards (achievement screen/hologram, /gamble top) showed real names; nick lookup only ran for chat text, so unloaded names never counted. Rule: any UI listing player names goes through `RemoteNicks.styledFor(name)` / `plainFor(name)` / `ensureKnown(name)` (lazy fetch, negative cache, 8 in flight), or `applyResolvedOnly` on a Component. Never read `styledByName` directly or skip the helper. Chat refresh (`ChatNickRefresher`) re-applies once a fetch lands.
- #498 cause: `[CosmeticLive.3]` was an old tick guard report from a client that was suspended or stalled (478s silent vs 75s watchdog); already log-only since 9d079570. Hardened: ping now uses the synchronized `sendText` (a ping racing a FishChat send could throw), and a silent-dead socket retries after 1s with backoff reset instead of the grown backoff.

## 2026-11-09: [Scan] no room data reports (#485/#509)
- Symptom: roadmap bug reports with "[Scan.5] no room data for core hash=H cell=(x,z)". Cause: Scan logged unknown room core hashes at WARN, and the report collector treats WARN lines as bugs. The hashes (655941283, -431747024, 721353045) are not in rooms.json and cannot be derived; they are likely new or variant Hypixel rooms, or chunks not fully loaded.
- Fix: log at DEBUG (the roomCores debug chat flag still shows each hash). To add a room, capture the hash with the roomCores flag in that room and add it to rooms.json; never guess hashes.

## 2026-11-09: One Spotify HUD
- Eli prefers the small pill (Artist - Title plus lyric line, `SpotifyLyricsHud`). The richer media-session card (`extras/SpotifyHud`, `spotify_smtc.ps1`, `spotifyHud*` settings) was removed; the pill is now the only HUD, named "Spotify" everywhere (feature, HUD editor entry). Old `spotifyHud*` config keys are simply ignored on load. Do not re-add a second Spotify overlay; add options to the pill instead.

## 2026-11-09: Nick hover shows real name
- Symptom: "hover over names to see og name doesn't work at all". Cause: `NameRewriter.replaceName` (the single choke point for own and remote nick swaps in chat, GUI text, nametags) never attached a hover event, so there was nothing to show. The only "original name" feature that existed was the item-rename tooltip (`OriginalNameTooltip`), a different thing.
- Fix: `replaceName` wraps each replacement in a root component carrying `HoverEvent.ShowText("Real name: X")`. Any new name-swap site must go through `NameRewriter.replaceName` so it inherits the hover. Hover only shows in chat (tooltips/nametags cannot hover).

## 2026-11-09: HUD editor previews were hand-written text (#517)
- Symptom: HUD editor boxes did not match the real HUD (wrong size, colours, background, missing setting-dependent lines). First attempt only wired Performance's live lines into the preset map, which still left every other HUD as a drifting copy.
- Cause: previews were a second, hand-maintained description of each HUD (`SAMPLES` text lines) instead of the HUD's own render path, so every look or setting change had to be mirrored by hand and never was.
- Fix: `HudPreview` registry; each HUD registers a `HudExample` drawn by its real render code with example data (see UI Conventions, "HUD editor previews"). Rule: never describe a HUD's look twice; one render function, two data sources (live, example).

- **2026-11-10, daily-bugfix skipped because of a leftover version bump.** `tools/deploy.ps1` bumped `mod_version` in all three modules but committed with `--allow-empty` and no `git add`. All `gradle.properties` files are tracked (not gitignored, despite older notes), so `dungeons-26.3/gradle.properties` stayed dirty and the 05:00 runner skipped on its clean-tree check. Fix: deploy.ps1 now stages the bumped files before committing, and `/ship` says to commit every bumped `gradle.properties` and finish with a clean `git status`. Rule: anything that bumps a version must commit it in the same step.

## 2026-11-11: Party Finder counted "Party" as a member (#544)
- `^`-anchored leader regex missed prefixed titles; the fallback picked the word "Party" and the count went to 6/5. Rule: Hypixel titles can carry rank prefixes, so match names unanchored, and a fallback must never return a fixed word from the template. See [[Technical Gotchas]] §3.

## 2026-11-11: Claude Changelog page, and a wrong "rule broken" claim
- Mistake: I told Eli the daily bugfix run broke the no-build rule by building and installing 1.7.72. `tools/daily-bugfix/prompt.md` gives explicit authorization for exactly that. Read the prompt or brief that started a session before judging what it did.
- The daily run wrote `BUGFIX_REPORT.md`. Eli wants the report in `CHANGELOG.txt` (Run notes) and on the hidden fishmod.dev page ([[Claude Changelog]]), never as a separate file.
- Worker deploy from this machine failed with Cloudflare 9109: the `CLOUDFLARE_API_TOKEN` user env var is IP-restricted. Check `wrangler whoami` before promising a deploy.

## 2026-11-11: secrets leaderboard capped at 13 (#533)
- Symptom: secret count on the achievement leaderboard stuck at 13. Cause: the worker clamped submits to a stale `ACH_MAX_SECRETS = 13` (registry now has 28; points cap 8281 vs 8706 is stale too). Rule: when a value tops out at a suspicious round number, check server-side clamps before the client parser. The mod now sends its registry maxima so the worker never needs a constant ([[Achievement Anti-Cheat]]).

## 2026-11-11: MayorApi 403 then MalformedJson (#542/#543)
- Symptom: `MayorApi.1 HTTP 403` followed by `MayorApi.2 MalformedJsonException line 1 column 8`. Cause: a `FishDiag.check` on status only logs, it does not stop execution, so the Cloudflare `error code: 1020` text body was parsed as JSON. The failure stamp also kept `stale()` true right after a term switch, hammering the API. Rule: every HTTP caller returns on non-2xx before parsing, and backoff uses its own `retryAt`, never a faked `lastFetch` ([[Mayor API]]).

## 2026-11-11: session_stats.json "Expected BEGIN_OBJECT but was STRING" every launch (#541)
- Symptom: startup auto error report `SessionStats.5` on every launch. Cause: non-atomic `Files.writeString` on the daemon IO thread left a corrupt (NUL-filled) file after a crash; load reported it but never moved it aside, so it failed forever. Rule: every persisted JSON uses `SafeFiles.writeAtomic` and quarantines unparseable files instead of FishDiag-reporting them ([[Technical Gotchas]] §2).

## 2026-11-11: LagTracker "negative lag -41.35s" (#545)
- Symptom: auto error report `LagTracker.2 negative lag (wall=300.851 ticks=6844)` from 1.6.382; later builds only clamped the final number, hiding it. Cause: server ping ticks arrive in catch-up bursts after server lag, so the tick count can exceed wall time and refund lag that happened before the run started. Rule: cap tick counters at `elapsedMs / 50` per tick instead of clamping only the end result ([[Technical Gotchas]] §2).

## 2026-11-11: remote item customs never cleared, failed upload never retried (#548)
- Symptom: after clearing a trim, other players kept seeing it; a failed `/items` POST left items unshared until the next edit. Cause: `ItemCustomizationStore.setRemote` only adds, and `RemoteItems.uploadIfChanged` marks the payload uploaded before the request completes. Rule: any remote-synced store must replace per player on each update, and "last sent" markers must be reset when the send fails. Two instances on a local vanilla server (rcon `item replace ... custom_data={uuid:"x"}`, `.\drive` style window capture) reproduce this in minutes.
