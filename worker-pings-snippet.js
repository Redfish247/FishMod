// ─────────────────────────────────────────────────────────────────────────────
// FishMod Shared Pings — drop-in snippet for the existing Cloudflare Worker.
//
// Lets FishMod users see each other's location pings (middle-click waypoints) in
// world. A ping is a tiny, short-lived record (latest one per player) that nearby
// FishMod users fetch by UUID — exactly the way /sync fetches nicks/items, so the
// scope is "players on your current server" (your tab list).
//
// SETUP (one-time):
//   1. In the Cloudflare dashboard for the fishmod worker, create a KV namespace
//      named "PINGS" and bind it to the worker as the variable `PINGS`.
//   2. Paste handlePings() below into the worker and route to it:
//        const url = new URL(request.url);
//        if (url.pathname === "/ping" || url.pathname === "/pings")
//            return handlePings(request, env, url);
//   3. The existing X-FishMod-Token check should cover these routes too.
//
// KV layout:
//   key "ping:<uuid>" → JSON { uuid, name, x, y, z, dim, ts }   (TTL ~30s)
//
// Pings auto-expire via KV TTL, so there's nothing to clean up and stale pings
// never linger. One record per player (a new ping overwrites the old one).
// ─────────────────────────────────────────────────────────────────────────────

const MOD_TOKEN = "fishmod123"; // must match HypixelApi.MOD_TOKEN in the mod

const PING_TTL_SECONDS = 30; // a ping lives at most this long server-side

export async function handlePings(request, env, url) {
  if (request.headers.get("X-FishMod-Token") !== MOD_TOKEN) {
    return json({ success: false, error: "unauthorized" }, 401);
  }

  // POST /ping  — publish (or refresh) the caller's current ping.
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

  // GET /pings?uuids=a,b,c&since=<ms> — return each listed player's live ping newer than `since`.
  if (url.pathname === "/pings" && request.method === "GET") {
    const uuidsParam = url.searchParams.get("uuids") || "";
    const since = parseInt(url.searchParams.get("since") || "0", 10) || 0;
    const uuids = uuidsParam.split(",").map(s => s.trim()).filter(Boolean).slice(0, 100);
    const pings = {};
    await Promise.all(uuids.map(async (u) => {
      const raw = await env.PINGS.get("ping:" + u);
      if (!raw) return;
      try {
        const rec = JSON.parse(raw);
        if (rec && rec.ts > since) pings[u] = rec;
      } catch {}
    }));
    return json({ success: true, pings });
  }

  return new Response("not found", { status: 404 });
}

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status, headers: { "content-type": "application/json" }
  });
}
