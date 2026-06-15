"""
One-shot backfill for FishMod's Croesus loot tracker.

Adds a single "Manual Adjustment" entry with RNG drops the tracker missed,
then pads the file with empty (0-value) entries so the run count (= number of
entries) reaches TARGET_RUNS. RNG prices are left at 0 on purpose: the mod's
CroesusStore.backfillPrices() fills them from bazaar / lowest-BIN on next launch.

REFUSES TO RUN while Minecraft (javaw.exe) is open, because the mod holds the
data in memory and rewrites the file on every claim / on exit.
"""
import json
import subprocess
import sys
import time
from pathlib import Path

FILE = Path(
    r"C:\Users\Eli\AppData\Roaming\ModrinthApp\profiles\Skyblock 1.21.8"
    r"\config\fishmod\croesus_loot.json"
)
TARGET_RUNS = 430

# RNG drops to add: (id, display name, count). price left at 0 -> mod backfills.
RNG = [
    ("NECRON_HANDLE",        "Necron's Handle",     3),
    ("SHADOW_WARP_SCROLL",   "Shadow Warp Scroll",  5),
    ("WITHER_SHIELD_SCROLL", "Wither Shield Scroll", 2),
    ("IMPLOSION_SCROLL",     "Implosion Scroll",    1),
    ("RECOMBOBULATOR_3000",  "Recombobulator 3000", 57),
    ("SECOND_MASTER_STAR",   "Second Master Star",  8),
    ("FIFTH_MASTER_STAR",    "Fifth Master Star",   2),
]


def game_running() -> bool:
    try:
        out = subprocess.run(["tasklist"], capture_output=True, text=True).stdout.lower()
        return "javaw.exe" in out
    except Exception:
        return False


def main() -> int:
    if game_running():
        print("ABORT: Minecraft (javaw.exe) is running. Close the game fully, then re-run.")
        return 1
    if not FILE.exists():
        print(f"ABORT: file not found: {FILE}")
        return 1

    data = json.loads(FILE.read_text(encoding="utf-8"))
    if not isinstance(data, list):
        print("ABORT: unexpected file format (expected a JSON array).")
        return 1

    start = len(data)
    print(f"Existing entries (runs): {start}")

    now = int(time.time() * 1000)

    # 1) One real RNG adjustment entry.
    rng_entry = {
        "timestamp": now,
        "floor": "?",
        "chestType": "Manual Adjustment",
        "runCompletedAgoSec": -1,
        "claimCost": 0,
        "items": [
            {"id": iid, "name": name, "count": cnt, "priceAtClaim": 0.0}
            for (iid, name, cnt) in RNG
        ],
    }
    data.append(rng_entry)

    # 2) Empty filler entries until we hit TARGET_RUNS total.
    need = TARGET_RUNS - len(data)
    if need < 0:
        print(f"NOTE: already at {len(data)} entries (>= {TARGET_RUNS}); no filler added.")
        need = 0
    for i in range(need):
        data.append({
            "timestamp": now - (i + 1) * 1000,
            "floor": "?",
            "chestType": "",
            "runCompletedAgoSec": -1,
            "claimCost": 0,
            "items": [],
        })

    FILE.write_text(json.dumps(data, indent=2), encoding="utf-8")
    print(f"RNG entry added (1) + empty filler added ({need}).")
    print(f"Total entries (runs) now: {len(data)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
