# Board inbox (proposed worker change, NOT deployed)

The mod's `/fm bug`, `/fm suggest` and auto error reports POST to
`https://board.fishmod.dev/board/inbox`. That route does not exist on the live
board yet, so the mod shows "board inbox isn't live yet" until Eli deploys this.

## Apply (Eli only)
```
cd C:\FishMod\worker-board
git apply -p2 --directory=. D:\FishModDungeons\tools\board-inbox\worker-inbox.patch
node _build.cjs
npx wrangler deploy
```
The patch only touches `_preamble.js` (server code). `page.html` is unchanged.

## What it adds
- `POST /board/inbox` (public, no auth: a secret in the jar would be extractable).
  Body: `kind` (bug|suggest|auto), `title`, `text`, `key` (auto only), `mod`, `mc`, `loc`, `log`.
  Max 16 KB, 20 posts/hour per hashed IP (HMAC with SESSION_SECRET, kept only as a 1 h TTL counter; IPs are never stored).
- Server dedup: an `auto` post whose `key` matches an existing entry only bumps `count`, `last` and the `mods` version list.
- `GET /board/inbox` (Discord session): HTML list with dismiss buttons. `?json=1` returns JSON.
- `POST /board/inbox/dismiss {id}` (session) removes an entry.
- `/board/feed` gains `inbox` (entries new or bumped since `?since`).
- Stored in TESTBOARD KV key `inbox`, newest first, capped at 300 entries / 700 KB.
- Nothing is added to the feature cards or the board SPA; link `/board/inbox` from `page.html` if wanted.
