"""
Local Bestiary Rebalance tracker.

Fetches live Hypixel Skyblock bestiary kills for any IGN, cross-references
the public Bestiary Rebalancing sheet + community bestiary family map, and
serves a small dashboard on localhost that auto-refreshes every 5 minutes.

Run:  python server.py
Then open http://localhost:8787?ign=YourIGN
"""

import csv
import io
import json
import re
import threading
import time
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# ---- Config -----------------------------------------------------------

PROXY_URL = "https://fishmod.dev"
MOD_TOKEN = "fishmod123"
DEFAULT_IGN = "RedFish2471"

SHEET_CSV_URL = (
    "https://docs.google.com/spreadsheets/d/e/"
    "2PACX-1vRmPmClR56gantvT1c4SF9eKW-FoftHtBUAcfJiDFLRHyRActqlXk5cabFMpYXIvwVTojMbaQ8sFf70/"
    "pub?output=csv&gid=0"
)
BESTIARY_CONST_URL = (
    "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/"
    "master/constants/bestiary.json"
)

REFRESH_SECONDS = 300  # 5 minutes
PORT = 8787

NAME_FIX = {"Millenia-Aged Blaze": "Millennia-Aged Blaze"}

# ---- State (keyed by lowercase IGN) --------------------------------------

_lock = threading.Lock()
_players = {}  # lowercase ign -> {rows, last_updated, last_error, refreshing, ign, profile_name}
_bestiary_def_cache = {"data": None, "fetched_at": 0}


def _fetch_json(url, headers=None):
    req_headers = {"User-Agent": "bestiary-tracker/1.0"}
    if headers:
        req_headers.update(headers)
    req = urllib.request.Request(url, headers=req_headers)
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode("utf-8"))


def _fetch_text(url):
    req = urllib.request.Request(url, headers={"User-Agent": "bestiary-tracker/1.0"})
    with urllib.request.urlopen(req, timeout=20) as resp:
        return resp.read().decode("utf-8")


def _clean(name):
    return re.sub(r"Â?§[0-9a-fk-or]", "", name).strip()


def _get_bestiary_def():
    # The community bracket/family map rarely changes — cache it for an hour
    # across all players instead of refetching per-player every 5 minutes.
    now = time.time()
    if _bestiary_def_cache["data"] is None or now - _bestiary_def_cache["fetched_at"] > 3600:
        _bestiary_def_cache["data"] = _fetch_json(BESTIARY_CONST_URL)
        _bestiary_def_cache["fetched_at"] = now
    return _bestiary_def_cache["data"]


def _extract_families(bestiary_def, kills):
    families = {}

    def walk(node):
        if not isinstance(node, dict):
            return
        if "mobs" in node and isinstance(node["mobs"], list):
            for item in node["mobs"]:
                if isinstance(item, dict) and "name" in item:
                    name = _clean(item["name"])
                    keys = item.get("mobs", [])
                    total = sum(kills.get(k, 0) for k in keys)
                    families[name] = {"total_kills": total}
        for k, v in node.items():
            if k == "mobs":
                continue
            if isinstance(v, dict):
                walk(v)

    for cat in bestiary_def.values():
        if isinstance(cat, dict):
            walk(cat)
    return families


def _resolve_uuid(ign):
    data = _fetch_json(f"https://api.mojang.com/users/profiles/minecraft/{urllib.parse.quote(ign)}")
    if not data or "id" not in data:
        raise RuntimeError(f"No Minecraft account found for '{ign}'")
    return data["id"], data.get("name", ign)


def refresh_player(ign):
    key = ign.lower()
    with _lock:
        _players.setdefault(
            key,
            {
                "ign": ign,
                "rows": [],
                "last_updated": None,
                "last_error": None,
                "refreshing": False,
            },
        )
        _players[key]["refreshing"] = True

    try:
        uuid, real_name = _resolve_uuid(ign)

        profiles = _fetch_json(
            f"{PROXY_URL}/skyblock/profiles?uuid={uuid}",
            headers={"X-FishMod-Token": MOD_TOKEN},
        )
        if not profiles.get("success"):
            raise RuntimeError(profiles.get("cause", "Hypixel API request failed"))
        if not profiles.get("profiles"):
            raise RuntimeError(f"'{real_name}' has no SkyBlock profiles")

        profile = next(
            (p for p in profiles["profiles"] if p.get("selected")),
            profiles["profiles"][0],
        )
        member = profile["members"][uuid]
        kills = member.get("bestiary", {}).get("kills", {})

        bestiary_def = _get_bestiary_def()
        families = _extract_families(bestiary_def, kills)
        brackets = bestiary_def.get("brackets", {})

        csv_text = _fetch_text(SHEET_CSV_URL)
        reader = csv.DictReader(io.StringIO(csv_text))

        rows = []
        for r in reader:
            name = (r.get("Mob") or "").strip()
            if not name:
                continue
            cap_str = (r.get("New Kills to Max") or "").replace(",", "")
            try:
                new_cap = int(cap_str)
            except ValueError:
                continue
            lookup = NAME_FIX.get(name, name)
            fam = families.get(lookup)
            if fam is None:
                continue
            current = fam["total_kills"]
            remaining = max(0, new_cap - current)

            # Bestiary XP toward the milestone bar is earned +1 per tier gained,
            # regardless of how many kills that tier costs — so "XP missing" is
            # tier-based, not kill-based.
            new_tiers_str = (r.get("New Tiers") or "").strip()
            new_bracket_str = (r.get("New Bracket") or "").strip()
            tiers_remaining = None
            current_tier = None
            new_tiers = None
            try:
                new_tiers = int(new_tiers_str)
                bracket_thresholds = brackets.get(new_bracket_str, [])
                if len(bracket_thresholds) >= new_tiers:
                    # The community bracket curve covers every tier the sheet
                    # says this mob now has — use its real per-tier thresholds.
                    relevant = bracket_thresholds[:new_tiers]
                else:
                    # This bracket's published curve is shorter than the new
                    # tier count (the rebalance introduced tier counts the
                    # existing bracket data doesn't cover yet). Fall back to
                    # an even linear split of the new cap across the new tier
                    # count so tier progress always reflects the NEW tiers,
                    # never gets truncated to the old, shorter curve.
                    relevant = [round(new_cap * (i + 1) / new_tiers) for i in range(new_tiers)]
                current_tier = sum(1 for t in relevant if current >= t)
                tiers_remaining = max(0, new_tiers - current_tier)
            except (ValueError, TypeError):
                pass

            rows.append(
                {
                    "Mob": name,
                    "Category": (r.get("Category") or "").strip(),
                    "Current Kills": current,
                    "New Cap": new_cap,
                    "Remaining": remaining,
                    "CurrentTier": current_tier,
                    "NewTiers": new_tiers,
                    "TiersRemaining": tiers_remaining,
                    "XpMissing": tiers_remaining,
                }
            )

        with _lock:
            _players[key].update(
                {
                    "ign": real_name,
                    "rows": rows,
                    "last_updated": int(time.time() * 1000),
                    "last_error": None,
                }
            )
    except Exception as e:  # noqa: BLE001
        with _lock:
            _players[key]["last_error"] = str(e)
    finally:
        with _lock:
            _players[key]["refreshing"] = False


def refresh_loop():
    while True:
        time.sleep(REFRESH_SECONDS)
        with _lock:
            keys = list(_players.keys())
        for key in keys:
            refresh_player(key)


def load_index_html():
    with open(__file__.replace("server.py", "index.html"), encoding="utf-8") as f:
        return f.read()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        pass

    def _send_json(self, payload, status=200):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        qs = urllib.parse.parse_qs(parsed.query)

        if parsed.path == "/" or parsed.path == "/index.html":
            body = load_index_html().encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        elif parsed.path == "/api/data":
            ign = (qs.get("ign", [DEFAULT_IGN])[0] or DEFAULT_IGN).strip()
            key = ign.lower()
            with _lock:
                player = _players.get(key)
            if player is None:
                # First time seeing this name — fetch synchronously so the
                # caller gets real data on the first request.
                refresh_player(ign)
                with _lock:
                    player = _players.get(key)
            self._send_json(
                {
                    "rows": player["rows"],
                    "lastUpdated": player["last_updated"],
                    "lastError": player["last_error"],
                    "refreshing": player["refreshing"],
                    "refreshIntervalSeconds": REFRESH_SECONDS,
                    "ign": player["ign"],
                }
            )
        elif parsed.path == "/api/refresh":
            ign = (qs.get("ign", [DEFAULT_IGN])[0] or DEFAULT_IGN).strip()
            threading.Thread(target=refresh_player, args=(ign,), daemon=True).start()
            self._send_json({"started": True})
        elif parsed.path == "/api/export.csv":
            ign = (qs.get("ign", [DEFAULT_IGN])[0] or DEFAULT_IGN).strip()
            key = ign.lower()
            with _lock:
                player = _players.get(key)
            if player is None:
                refresh_player(ign)
                with _lock:
                    player = _players.get(key)

            include_maxed = (qs.get("maxed", ["0"])[0] or "0") == "1"
            rows = player["rows"]
            if not include_maxed:
                rows = [r for r in rows if r["Remaining"] > 0 or (r["TiersRemaining"] or 0) > 0]

            buf = io.StringIO()
            writer = csv.writer(buf)
            writer.writerow(
                [
                    "Mob",
                    "Category",
                    "Current Kills",
                    "New Cap",
                    "Kills Left",
                    "Current Tier",
                    "New Tiers",
                    "Bestiary XP Missing",
                ]
            )
            for r in rows:
                writer.writerow(
                    [
                        r["Mob"],
                        r["Category"],
                        r["Current Kills"],
                        r["New Cap"],
                        r["Remaining"],
                        r["CurrentTier"] if r["CurrentTier"] is not None else "",
                        r["NewTiers"] if r["NewTiers"] is not None else "",
                        r["XpMissing"] if r["XpMissing"] is not None else "",
                    ]
                )

            body = buf.getvalue().encode("utf-8")
            safe_ign = re.sub(r"[^A-Za-z0-9_-]", "_", player["ign"] or ign)
            self.send_response(200)
            self.send_header("Content-Type", "text/csv; charset=utf-8")
            self.send_header(
                "Content-Disposition", f'attachment; filename="bestiary-{safe_ign}.csv"'
            )
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        qs = urllib.parse.parse_qs(parsed.query)
        if parsed.path == "/api/refresh":
            ign = (qs.get("ign", [DEFAULT_IGN])[0] or DEFAULT_IGN).strip()
            threading.Thread(target=refresh_player, args=(ign,), daemon=True).start()
            self._send_json({"started": True})
        else:
            self.send_response(404)
            self.end_headers()


def main():
    refresh_player(DEFAULT_IGN)  # populate before first request
    threading.Thread(target=refresh_loop, daemon=True).start()
    server = ThreadingHTTPServer(("localhost", PORT), Handler)
    print(f"Bestiary tracker running at http://localhost:{PORT}?ign={DEFAULT_IGN}")
    server.serve_forever()


if __name__ == "__main__":
    main()
