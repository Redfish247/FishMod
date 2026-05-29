// ─────────────────────────────────────────────────────────────────────────────
// FishMod Challenges — drop-in snippet for the existing Cloudflare Worker.
//
// SETUP (one-time):
//   1. In the Cloudflare dashboard for the fishmod worker, create a KV namespace
//      named "CHALLENGES" and bind it to the worker as the variable `CHALLENGES`.
//      (Workers → Settings → Bindings → KV Namespace Bindings → Add)
//   2. Paste the two route handlers below into the worker's existing fetch().
//   3. Make sure the existing X-FishMod-Token check covers these routes too.
//
// KV layout:
//   key  "player:<uuid>"  → JSON  { uuid, name, totalPoints, dailyCount,
//                                   weeklyCount, monthlyCount, lastUpdated }
//   key  "lb:index"       → JSON  [ uuid, uuid, ... ]   (all known players)
//
// The index is small (one string per player). For up to ~10k players this is fine.
// If it ever grows beyond that, switch to D1.
// ─────────────────────────────────────────────────────────────────────────────

const MOD_TOKEN = "fishmod123"; // must match HypixelApi.MOD_TOKEN in the mod

// Call this from your worker's fetch(request, env, ctx):
//   const url = new URL(request.url);
//   if (url.pathname.startsWith("/challenges/")) return handleChallenges(request, env, url);
export async function handleChallenges(request, env, url) {
  // Token gate (same pattern your other routes use)
  if (request.headers.get("X-FishMod-Token") !== MOD_TOKEN) {
    return new Response(JSON.stringify({ ok: false, error: "unauthorized" }), {
      status: 401, headers: { "content-type": "application/json" }
    });
  }

  if (url.pathname === "/challenges/leaderboard" && request.method === "GET") {
    const top = Math.max(1, Math.min(100, parseInt(url.searchParams.get("top") || "10", 10)));
    return await getLeaderboard(env, top);
  }
  if (url.pathname === "/challenges/submit" && request.method === "POST") {
    let body;
    try { body = await request.json(); }
    catch { return jsonErr("bad json"); }
    return await submitScore(env, body);
  }
  return new Response("not found", { status: 404 });
}

async function getLeaderboard(env, top) {
  const idxRaw = await env.CHALLENGES.get("lb:index");
  const uuids = idxRaw ? JSON.parse(idxRaw) : [];

  // Pull every player record. With ~thousands of players consider sharding;
  // for now we just fetch them all and sort in-memory.
  const entries = [];
  await Promise.all(uuids.map(async (u) => {
    const raw = await env.CHALLENGES.get("player:" + u);
    if (raw) entries.push(JSON.parse(raw));
  }));
  entries.sort((a, b) => (b.totalPoints || 0) - (a.totalPoints || 0));
  const sliced = entries.slice(0, top);
  return new Response(JSON.stringify({
    entries: sliced,
    generatedAt: Date.now()
  }), { headers: { "content-type": "application/json" } });
}

async function submitScore(env, body) {
  const { uuid, name, challengeId, tier, points, activeMs, completedAt } = body || {};
  if (!uuid || !name || !tier || typeof points !== "number") return jsonErr("missing fields");
  if (points < 0 || points > 1000) return jsonErr("bad points"); // sanity clamp

  // Per-challenge dedupe: refuse double-submit of the same challengeId.
  if (challengeId) {
    const seenKey = "seen:" + uuid + ":" + challengeId;
    if (await env.CHALLENGES.get(seenKey)) {
      return new Response(JSON.stringify({ ok: false, error: "duplicate" }),
        { headers: { "content-type": "application/json" } });
    }
    // Expire dedupe markers after 90 days — keeps KV tidy
    await env.CHALLENGES.put(seenKey, "1", { expirationTtl: 90 * 24 * 60 * 60 });
  }

  const key = "player:" + uuid;
  const existing = await env.CHALLENGES.get(key);
  const rec = existing ? JSON.parse(existing) : {
    uuid, name, totalPoints: 0, dailyCount: 0, weeklyCount: 0, monthlyCount: 0, lastUpdated: 0
  };
  rec.name = name; // keep IGN fresh
  rec.totalPoints = (rec.totalPoints || 0) + points;
  if (tier === "DAILY")   rec.dailyCount   = (rec.dailyCount   || 0) + 1;
  if (tier === "WEEKLY")  rec.weeklyCount  = (rec.weeklyCount  || 0) + 1;
  if (tier === "MONTHLY") rec.monthlyCount = (rec.monthlyCount || 0) + 1;
  rec.lastUpdated = completedAt || Date.now();
  await env.CHALLENGES.put(key, JSON.stringify(rec));

  // Maintain the index
  const idxRaw = await env.CHALLENGES.get("lb:index");
  const idx = idxRaw ? JSON.parse(idxRaw) : [];
  if (!idx.includes(uuid)) {
    idx.push(uuid);
    await env.CHALLENGES.put("lb:index", JSON.stringify(idx));
  }

  // Compute the new rank by scanning everyone (cheap at this scale).
  let rank = 1;
  await Promise.all(idx.map(async (u) => {
    if (u === uuid) return;
    const raw = await env.CHALLENGES.get("player:" + u);
    if (raw && JSON.parse(raw).totalPoints > rec.totalPoints) rank++;
  }));

  return new Response(JSON.stringify({
    ok: true, newTotal: rec.totalPoints, rank
  }), { headers: { "content-type": "application/json" } });
}

function jsonErr(msg) {
  return new Response(JSON.stringify({ ok: false, error: msg }), {
    status: 400, headers: { "content-type": "application/json" }
  });
}
