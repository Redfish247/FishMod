// ─────────────────────────────────────────────────────────────────────────────
// FishMod Reputation ("shitter list") — drop-in snippet for the Cloudflare Worker.
//
// Crowd-sourced, opt-in player reputation. Any player can be tagged by UUID (they
// do NOT need the mod), so you can flag a ditcher/scammer or vouch a good carry and
// other FishMod users see the aggregate. One vote per voter per target (changing
// your vote just overwrites the old one), so counts can't be stuffed by spamming.
//
// SETUP (one-time):
//   1. Create a KV namespace "REP" and bind it to the worker as `REP`.
//   2. Paste handleRep() below and route to it:
//        const url = new URL(request.url);
//        if (url.pathname === "/rep") return handleRep(request, env, url);
//   3. The existing X-FishMod-Token check should cover this route too.
//
// KV layout:
//   key "rep:<targetUuid>"           → JSON { name, up, down }   (aggregate)
//   key "vote:<voterUuid>:<target>"  → "up" | "down"            (per-voter, for dedupe)
// ─────────────────────────────────────────────────────────────────────────────

const MOD_TOKEN = "fishmod123"; // must match HypixelApi.MOD_TOKEN in the mod

export async function handleRep(request, env, url) {
  if (request.headers.get("X-FishMod-Token") !== MOD_TOKEN) {
    return json({ success: false, error: "unauthorized" }, 401);
  }

  // GET /rep?uuids=a,b,c — aggregate reputation for the listed targets.
  if (request.method === "GET") {
    const uuids = (url.searchParams.get("uuids") || "")
      .split(",").map(s => s.trim()).filter(Boolean).slice(0, 100);
    const reps = {};
    await Promise.all(uuids.map(async (u) => {
      const raw = await env.REP.get("rep:" + u);
      if (raw) { try { reps[u] = JSON.parse(raw); } catch {} }
    }));
    return json({ success: true, reps });
  }

  // POST /rep {voter, target, name, vote}  vote ∈ "up" | "down" | "none"
  if (request.method === "POST") {
    let body;
    try { body = await request.json(); } catch { return json({ success: false, error: "bad json" }, 400); }
    const { voter, target, name, vote } = body || {};
    if (!voter || !target || !["up", "down", "none"].includes(vote)) {
      return json({ success: false, error: "missing/invalid fields" }, 400);
    }
    if (voter === target) return json({ success: false, error: "cannot vote yourself" }, 400);

    const voteKey = "vote:" + voter + ":" + target;
    const prev = await env.REP.get(voteKey); // "up" | "down" | null

    const repKey = "rep:" + target;
    const raw = await env.REP.get(repKey);
    const rec = raw ? JSON.parse(raw) : { name: name || "", up: 0, down: 0 };
    if (name) rec.name = name;

    // Undo the voter's previous contribution, then apply the new one.
    if (prev === "up") rec.up = Math.max(0, (rec.up || 0) - 1);
    if (prev === "down") rec.down = Math.max(0, (rec.down || 0) - 1);
    if (vote === "up") rec.up = (rec.up || 0) + 1;
    if (vote === "down") rec.down = (rec.down || 0) + 1;

    if (vote === "none") await env.REP.delete(voteKey);
    else await env.REP.put(voteKey, vote);
    await env.REP.put(repKey, JSON.stringify(rec));

    return json({ success: true, up: rec.up, down: rec.down });
  }

  return new Response("not found", { status: 404 });
}

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status, headers: { "content-type": "application/json" }
  });
}
