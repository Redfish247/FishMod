// ─────────────────────────────────────────────────────────────────────────────
// FishMod Challenges — STANDALONE test worker.
//
// Deploy this as its OWN Cloudflare Worker (e.g. fishmod-lb.redfish2471.workers.dev)
// so you can verify leaderboard submit + fetch in isolation from the main worker.
//
// SETUP:
//   1. Cloudflare dashboard → Workers → Create Worker (any name, e.g. "fishmod-lb").
//   2. Paste this entire file as the worker source.
//   3. Settings → Bindings → KV Namespace Binding → add namespace named CHALLENGES,
//      bind as variable name CHALLENGES.
//   4. Deploy. Note the *.workers.dev URL.
//   5. In Minecraft: run `/fmchworker https://your-worker-url.workers.dev` to point
//      the mod's challenge endpoints at it temporarily. Then `/fmlbtest`.
//   6. Once verified, merge handleChallenges() into the main worker and run
//      `/fmchworker reset` to point the mod back at the default URL.
// ─────────────────────────────────────────────────────────────────────────────

const MOD_TOKEN = "fishmod123";

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    // health check — visit the worker URL in a browser to confirm it's live
    if (url.pathname === "/" || url.pathname === "/health") {
      return new Response(JSON.stringify({ ok: true, service: "fishmod-challenges" }), {
        headers: { "content-type": "application/json" }
      });
    }

    if (request.headers.get("X-FishMod-Token") !== MOD_TOKEN) {
      return j({ ok: false, error: "unauthorized" }, 401);
    }

    if (url.pathname === "/challenges/leaderboard" && request.method === "GET") {
      return getLeaderboard(env, Math.max(1, Math.min(100, parseInt(url.searchParams.get("top") || "10", 10))));
    }
    if (url.pathname === "/challenges/submit" && request.method === "POST") {
      let body;
      try { body = await request.json(); } catch { return j({ ok: false, error: "bad json" }, 400); }
      return submitScore(env, body);
    }
    return new Response("not found", { status: 404 });
  }
};

async function getLeaderboard(env, top) {
  const idxRaw = await env.CHALLENGES.get("lb:index");
  const uuids = idxRaw ? JSON.parse(idxRaw) : [];
  const entries = [];
  await Promise.all(uuids.map(async (u) => {
    const raw = await env.CHALLENGES.get("player:" + u);
    if (raw) entries.push(JSON.parse(raw));
  }));
  entries.sort((a, b) => (b.totalPoints || 0) - (a.totalPoints || 0));
  return j({ entries: entries.slice(0, top), generatedAt: Date.now() });
}

async function submitScore(env, body) {
  const { uuid, name, challengeId, tier, points, activeMs, completedAt } = body || {};
  if (!uuid || !name || !tier || typeof points !== "number") return j({ ok: false, error: "missing fields" }, 400);
  if (points < 0 || points > 1000) return j({ ok: false, error: "bad points" }, 400);

  if (challengeId) {
    const seenKey = "seen:" + uuid + ":" + challengeId;
    if (await env.CHALLENGES.get(seenKey)) return j({ ok: false, error: "duplicate" });
    await env.CHALLENGES.put(seenKey, "1", { expirationTtl: 90 * 24 * 60 * 60 });
  }

  const key = "player:" + uuid;
  const existing = await env.CHALLENGES.get(key);
  const rec = existing ? JSON.parse(existing) : {
    uuid, name, totalPoints: 0, dailyCount: 0, weeklyCount: 0, monthlyCount: 0, lastUpdated: 0
  };
  rec.name = name;
  rec.totalPoints = (rec.totalPoints || 0) + points;
  if (tier === "DAILY")   rec.dailyCount   = (rec.dailyCount   || 0) + 1;
  if (tier === "WEEKLY")  rec.weeklyCount  = (rec.weeklyCount  || 0) + 1;
  if (tier === "MONTHLY") rec.monthlyCount = (rec.monthlyCount || 0) + 1;
  rec.lastUpdated = completedAt || Date.now();
  await env.CHALLENGES.put(key, JSON.stringify(rec));

  const idxRaw = await env.CHALLENGES.get("lb:index");
  const idx = idxRaw ? JSON.parse(idxRaw) : [];
  if (!idx.includes(uuid)) {
    idx.push(uuid);
    await env.CHALLENGES.put("lb:index", JSON.stringify(idx));
  }

  let rank = 1;
  await Promise.all(idx.map(async (u) => {
    if (u === uuid) return;
    const raw = await env.CHALLENGES.get("player:" + u);
    if (raw && JSON.parse(raw).totalPoints > rec.totalPoints) rank++;
  }));

  return j({ ok: true, newTotal: rec.totalPoints, rank });
}

function j(obj, status = 200) {
  return new Response(JSON.stringify(obj), { status, headers: { "content-type": "application/json" } });
}
