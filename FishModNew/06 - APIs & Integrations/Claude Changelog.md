---
type: integration
updated: 2026-11-11
related:
  - "[[fishmod-worker]]"
  - "[[fishmod.dev Dashboard]]"
---

# Claude Changelog

A hidden, owner-only page on fishmod.dev where Claude logs what it did each day: fixes, cards still open, and what Eli should test in game. Every Claude session that finishes real work adds to today's entry, not just the daily bugfix run.

## Where it is

- Page: `https://fishmod.dev/dashboard/claude-changelog`. It only works for Eli's dashboard login. Everyone else gets a plain 404 (no login prompt, no greyed tile).
- Hub tile: the hub gets it from `/dashboard/api/me` `ownerLinks`, which only lists it for the owner, so the path never appears in public page source.
- Code (worker repo `D:\FishMod-WorkerBoard`): `claude-changelog.html` (page), `dashboard.js` (`saveClaudeReport`, `mergeClaudeReport`, `listClaudeReports`), `hub.html` (ownerLinks tiles).
- Storage: D1 `fishmod-nicks`, table `claude_reports(date PK, body JSON, updated_at)`, created on first use. One row per day; the page shows the newest 90.

## How to add to it (any session)

1. Write a JSON file with only what you are adding (format below). Put it in your scratchpad, not the repo.
2. Run `powershell -NoProfile -File tools/claude-changelog.ps1 <file>` from `D:\FishModDungeons`. It merges into today's entry, reading `ROADMAP_SECRET` from the user env var. Never print the secret.
3. Expect `{"success":true,...}`. If it fails, say so in your final message; don't retry in a loop.

`-Replace` overwrites the whole day and wipes other sessions' items, so only use it to repair a broken entry. The daily bugfix run merges too (`tools/daily-bugfix/prompt.md`, file `tools/daily-bugfix/last-report.json`).

Merge rules: lists are appended; a card `id` posted again replaces its old entry; a card that is now fixed or already fixed drops out of "open"; `summary` and `alert` are joined; `version` takes the newest. Post each item once, because `tests`, `facts` and `notes` are appended without deduping.

## Report format

```json
{
  "date": "YYYY-MM-DD",
  "version": "1.7.72",
  "summary": "one line: what was built, where it went, tested or not",
  "alert": "optional: one line Eli must see first",
  "fixed":   [{"id": 534, "title": "short title", "files": ["garden/SpeedSignHelper.kt"], "detail": "sentence, or an array of bullets"}],
  "already": [{"id": 538, "title": "...", "detail": "..."}],
  "open":    [{"id": 533, "title": "...", "status": "Needs your OK", "detail": "why"}],
  "tests":   ["in-game test step"],
  "facts":   [["Commits", "`abc1234` what"], ["Build", "not built"]],
  "notes":   ["side notes"]
}
```

- All fields are optional except `date` (the script fills in today).
- `id` is a fishmod.dev card number. Leave it out for work that has no card (features, refactors).
- Plain text only. Wrap code, file names and commands in backticks; the page shows them as code. No HTML or markdown.
- A feature session puts its work in `fixed` with a feature title, plus test steps in `tests`. Be honest: say "not built, untested" in `summary` or `facts`.

## Gotchas

- Wrangler deploys from this machine failed on 2026-11-11 with Cloudflare error 9109 ("Cannot use the access token from location"): the `CLOUDFLARE_API_TOKEN` user env var is IP-restricted and the current IPv6 address isn't allowed. Eli has to allow the address, or deploy himself.
- The worker deploy rule still applies: diff against live code from Cloudflare first, and Eli must OK it ([[fishmod-worker]]).
- The POST uses the bearer `ROADMAP_SECRET`. A wrong or missing token also returns 404, so a 404 from the script means auth or "not deployed yet".
