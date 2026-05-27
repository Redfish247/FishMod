const ALLOWED_PATHS = ['/player', '/skyblock/profiles', '/skyblock/profile', '/skyblock/garden'];
const RATE_LIMIT_PER_MIN = 10; // requests per IP per minute

const MINECRAFT_GAME_ID = '27471';
let twitchToken = null;
let twitchTokenExp = 0;

export default {
  async fetch(request, env) {
    // Check shared token
    const token = request.headers.get('X-FishMod-Token');
    if (token !== env.MOD_TOKEN) {
      return json({ success: false, cause: 'Unauthorized' }, 401);
    }

    const url = new URL(request.url);

    // Shared cosmetic nicknames (so other mod users can see each other's nicks).
    if (url.pathname === '/nick') {
      return handleNickUpload(request, env);
    }
    if (url.pathname === '/nicks') {
      return handleNickFetch(url, env);
    }

    // Twitch: live Hypixel SkyBlock streams (keys held server-side as secrets)
    if (url.pathname === '/twitch/streams') {
      return handleTwitch(env);
    }

    // Networth — computed from the Hypixel profile (your key) via skyhelper-networth.
    if (url.pathname === '/networth') {
      return handleNetworth(url, env);
    }

    if (!ALLOWED_PATHS.some(p => url.pathname.startsWith(p))) {
      return json({ success: false, cause: 'Not found' }, 404);
    }

    // Proxy to Hypixel
    const hypixelUrl = 'https://api.hypixel.net/v2' + url.pathname + url.search;
    const resp = await fetch(hypixelUrl, {
      headers: { 'API-Key': env.HYPIXEL_API_KEY }
    });

    const data = await resp.json();
    return json(data, resp.status);
  }
};

async function getTwitchToken(env) {
  const now = Date.now();
  if (twitchToken && now < twitchTokenExp - 60000) return twitchToken;
  const body = `client_id=${env.TWITCH_CLIENT_ID}&client_secret=${env.TWITCH_CLIENT_SECRET}&grant_type=client_credentials`;
  const r = await fetch('https://id.twitch.tv/oauth2/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body
  });
  if (!r.ok) throw new Error('auth ' + r.status);
  const d = await r.json();
  twitchToken = d.access_token;
  twitchTokenExp = now + (d.expires_in || 3600) * 1000;
  return twitchToken;
}

function isHypixelSkyblock(title) {
  if (!title) return false;
  const t = title.toLowerCase();
  return t.includes('hypixel') && (t.includes('skyblock') || t.includes('sky block'));
}

function addStream(merged, s) {
  const prev = merged.get(s.user_login);
  const viewers = s.viewer_count || 0;
  if (prev && prev.viewers >= viewers) return;
  merged.set(s.user_login, {
    login: s.user_login,
    name: s.user_name,
    title: s.title,
    viewers,
    game: s.game_name || '',
    preview: (s.thumbnail_url || '').replace('{width}', '320').replace('{height}', '180')
  });
}

async function handleTwitch(env) {
  try {
    const tok = await getTwitchToken(env);
    const headers = { 'Client-Id': env.TWITCH_CLIENT_ID, 'Authorization': 'Bearer ' + tok };
    const merged = new Map();

    // 1) Crawl the entire Minecraft directory; keep SkyBlock titles.
    let cursor = null;
    for (let page = 0; page < 40; page++) {
      let u = `https://api.twitch.tv/helix/streams?game_id=${MINECRAFT_GAME_ID}&first=100`;
      if (cursor) u += '&after=' + cursor;
      const r = await fetch(u, { headers });
      if (!r.ok) return json({ success: false, cause: 'helix ' + r.status }, 502);
      const d = await r.json();
      if (!d.data || d.data.length === 0) break;
      for (const s of d.data) if (isHypixelSkyblock(s.title)) addStream(merged, s);
      cursor = d.pagination && d.pagination.cursor;
      if (!cursor) break;
    }

    // 2) Catch streamers in OTHER categories (Just Chatting, etc.) via channel search.
    const candidates = new Set();
    for (const q of ['hypixel skyblock', 'hypixel sky block']) {
      const sr = await fetch(
        `https://api.twitch.tv/helix/search/channels?query=${encodeURIComponent(q)}&first=100&live_only=true`,
        { headers }
      );
      if (!sr.ok) continue;
      const sd = await sr.json();
      for (const c of (sd.data || [])) {
        if (c.is_live && c.broadcaster_login && !merged.has(c.broadcaster_login)) {
          candidates.add(c.broadcaster_login);
        }
      }
    }
    const logins = [...candidates].slice(0, 100);
    if (logins.length) {
      const qs = logins.map(l => 'user_login=' + encodeURIComponent(l)).join('&');
      const br = await fetch(`https://api.twitch.tv/helix/streams?first=100&${qs}`, { headers });
      if (br.ok) {
        const bd = await br.json();
        for (const s of (bd.data || [])) if (isHypixelSkyblock(s.title)) addStream(merged, s);
      }
    }

    const streams = [...merged.values()].sort((a, b) => b.viewers - a.viewers);
    return json({ success: true, streams });
  } catch (e) {
    return json({ success: false, cause: String(e) }, 502);
  }
}

async function handleNetworth(url, env) {
  const uuid = url.searchParams.get('uuid');
  if (!uuid) return json({ success: false, cause: 'no uuid' }, 400);
  try {
    // SkyCrypt runs skyhelper-networth correctly server-side (it can't run here — it's a Node
    // library that reads item data from disk via __dirname/fs, which the Workers runtime lacks).
    const r = await fetch('https://sky.shiiyu.moe/api/v2/profile/' + uuid, {
      headers: { 'User-Agent': 'FishMod/1.0', 'Accept': 'application/json' }
    });
    if (!r.ok) return json({ success: false, cause: 'skycrypt ' + r.status }, 502);
    const d = await r.json();
    const profiles = (d && d.profiles) ? Object.values(d.profiles) : [];
    if (!profiles.length) return json({ success: false, cause: 'no profiles' }, 404);

    const chosen = profiles.find(p => p.current) || profiles.find(p => p.selected) || profiles[0];
    const nwObj = chosen && chosen.data && chosen.data.networth;
    if (!nwObj || typeof nwObj.networth !== 'number') {
      return json({ success: false, cause: 'no networth field' }, 502);
    }
    return json({
      success: true,
      profile: chosen.cute_name || null,
      networth: nwObj.networth,
      unsoulbound: typeof nwObj.unsoulboundNetworth === 'number' ? nwObj.unsoulboundNetworth : null
    });
  } catch (e) {
    return json({ success: false, cause: String(e && e.message ? e.message : e) }, 502);
  }
}

// ── Cosmetic nicknames (KV: binding NICKS, key = uuid without dashes, value = &-coded nick) ──
const NICK_MAX_LEN = 256; // hex-gradient nicks use ~9 chars per letter, so allow long strings
const cleanUuid = u => (u || '').replace(/-/g, '').toLowerCase();
const isUuid = u => /^[0-9a-f]{32}$/.test(u);

// All nicks live in ONE KV key (a uuid→nick map) so a fetch costs a single KV read instead of one
// per player — the per-key approach blew the free tier's daily read limit in busy lobbies.
// Nicks are stored in D1 (SQLite). The whole table is small (only players who set a nick), so we
// load it all into an in-memory map cached per isolate (~1 query/minute/isolate). D1's free read
// limit (~5M rows/day) is ~50x KV's, and this caching keeps actual queries negligible.
let nickCache = null;
let nickCacheAt = 0;
const NICK_CACHE_MS = 60000;

async function readAllNicks(env) {
  const now = Date.now();
  if (nickCache && now - nickCacheAt < NICK_CACHE_MS) return nickCache;
  try {
    const { results } = await env.DB.prepare('SELECT uuid, nick FROM nicks').all();
    const map = {};
    for (const row of results) map[row.uuid] = row.nick;
    nickCache = map;
    nickCacheAt = now;
  } catch (e) {
    if (!nickCache) nickCache = {}; // serve empty rather than erroring
  }
  return nickCache;
}

async function handleNickUpload(request, env) {
  if (request.method !== 'POST') return json({ success: false, cause: 'POST only' }, 405);
  if (!env.DB) return json({ success: false, cause: 'D1 not configured' }, 500);
  let body;
  try { body = await request.json(); } catch { return json({ success: false, cause: 'bad json' }, 400); }
  const uuid = cleanUuid(body.uuid);
  if (!isUuid(uuid)) return json({ success: false, cause: 'bad uuid' }, 400);
  let nick = typeof body.nick === 'string' ? body.nick : '';
  if (nick.length > NICK_MAX_LEN) nick = nick.slice(0, NICK_MAX_LEN);

  try {
    if (nick === '') {
      await env.DB.prepare('DELETE FROM nicks WHERE uuid = ?').bind(uuid).run();
    } else {
      await env.DB.prepare(
        'INSERT INTO nicks (uuid, nick) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET nick = excluded.nick'
      ).bind(uuid, nick).run();
    }
  } catch (e) {
    return json({ success: false, cause: 'db write failed: ' + (e && e.message) }, 503);
  }
  // Update the isolate cache so the change is visible immediately on this isolate.
  if (nickCache) { if (nick === '') delete nickCache[uuid]; else nickCache[uuid] = nick; }
  return json({ success: true });
}

async function handleNickFetch(url, env) {
  if (!env.DB) return json({ success: false, cause: 'D1 not configured' }, 500);
  const raw = url.searchParams.get('uuids') || '';
  const wanted = new Set(raw.split(',').map(cleanUuid).filter(isUuid));
  const all = await readAllNicks(env);
  const nicks = {};
  for (const u of wanted) if (all[u]) nicks[u] = all[u];
  return json({ success: true, nicks });
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' }
  });
}
