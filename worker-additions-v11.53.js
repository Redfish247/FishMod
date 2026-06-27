// ─────────────────────────────────────────────────────────────────────────────
// FishMod worker additions — v11.53 (Shared Pings + Reputation)
//
// ADD these to your EXISTING fishmod worker. Do NOT replace the whole worker —
// your /sync, /nick, /nicks, /items, /skyblock/profiles, /challenges routes must
// stay. This file only adds /ping, /pings and /rep.
//
// SETUP (one-time, in the Cloudflare dashboard):
//   1. Workers & Pages ▸ KV ▸ create two namespaces: "PINGS" and "REP".
//   2. Your worker ▸ Settings ▸ Bindings ▸ add two KV Namespace bindings:
//        variable PINGS → PINGS namespace, variable REP → REP namespace.
//   3. Paste the three functions below into the worker (module format, same file
//      as your other handlers). If your worker already defines MOD_TOKEN and/or a
//      json() helper, delete the duplicates here and keep your existing ones.
//   4. Add these lines inside your fetch(request, env, ctx), next to your other
//      url.pathname checks:
//
//        const url = new URL(request.url);
//        if (url.pathname === "/ping" || url.pathname === "/pings") return handlePings(request, env, url);
//        if (url.pathname === "/rep")  return handleRep(request, env, url);
//
//   5. Save and Deploy.
// ─────────────────────────────────────────────────────────────────────────────

const MOD_TOKEN = "fishmod123"; // must match HypixelApi.MOD_TOKEN in the mod

const PING_TTL_SECONDS = 30; // a ping lives at most this long server-side

// ── Shared Pings ─────────────────────────────────────────────────────────────
// KV "PINGS":  key "ping:<uuid>" → { uuid, name, x, y, z, dim, ts }  (TTL ~30s)
export async function handlePings(request, env, url) {
  if (request.headers.get("X-FishMod-Token") !== MOD_TOKEN) {
    return json({ success: false, error: "unauthorized" }, 401);
  }

  // POST /ping — publish (or refresh) the caller's current ping.
  if (url.pathname === "/ping" && request.method === "POST") {
    let body;
    try { body = await request.json(); } catch { return json({ success: false, error: "bad json" }, 400); }
    const { uuid, name, x, y, z, dim } = body || {};
    if (!uuid || typeof x !== "number" || typeof y !== "number" || typeof z !== "number") {
      return json({ success: false, error: "missing fields" }, 400);
    }
    const rec = { uuid, name: name || "", x, y, z, dim: dim || "", ts: Date.now() };
    await env.PINGS.put("ping:" + uuid, JSON.stringify(rec), { expirationTtl: PING_TTL_SECONDS });
    return json({ success: true });
  }

  // GET /pings?uuids=a,b,c&since=<ms> — each listed player's live ping newer than `since`.
  if (url.pathname === "/pings" && request.method === "GET") {
    const since = parseInt(url.searchParams.get("since") || "0", 10) || 0;
    const uuids = (url.searchParams.get("uuids") || "")
      .split(",").map(s => s.trim()).filter(Boolean).slice(0, 100);
    const pings = {};
    await Promise.all(uuids.map(async (u) => {
      const raw = await env.PINGS.get("ping:" + u);
      if (!raw) return;
      try { const rec = JSON.parse(raw); if (rec && rec.ts > since) pings[u] = rec; } catch {}
    }));
    return json({ success: true, pings });
  }

  return new Response("not found", { status: 404 });
}

// ── Reputation (vouch / shitter list) ────────────────────────────────────────
// KV "REP":  key "rep:<target>" → { name, up, down }      (aggregate)
//            key "vote:<voter>:<target>" → "up" | "down"  (per-voter dedupe)
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

    if (prev === "up")   rec.up   = Math.max(0, (rec.up   || 0) - 1);
    if (prev === "down") rec.down = Math.max(0, (rec.down || 0) - 1);
    if (vote === "up")   rec.up   = (rec.up   || 0) + 1;
    if (vote === "down") rec.down = (rec.down || 0) + 1;

    if (vote === "none") await env.REP.delete(voteKey);
    else                 await env.REP.put(voteKey, vote);
    await env.REP.put(repKey, JSON.stringify(rec));

    return json({ success: true, up: rec.up, down: rec.down });
  }

  return new Response("not found", { status: 404 });
}

// Shared helper — delete this if your worker already defines json().
function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status, headers: { "content-type": "application/json" }
  });
}
