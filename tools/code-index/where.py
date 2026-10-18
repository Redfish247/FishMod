#!/usr/bin/env python3
"""where.py <term> [more terms] [-k kind] [-n limit]: find symbols/settings/commands/GUI labels. Rebuilds the index if stale."""
import os, sys, subprocess, re
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
IDX = os.path.join(ROOT, ".codeindex", "index.tsv")
SRC = os.path.join(ROOT, "dungeons-26.1.2", "src")

def newest():
    m = 0
    for dp, _, fns in os.walk(SRC):
        for fn in fns:
            m = max(m, os.path.getmtime(os.path.join(dp, fn)))
    return m

if not os.path.exists(IDX) or os.path.getmtime(IDX) < newest():
    subprocess.run([sys.executable, os.path.join(HERE, "build.py")], check=True, stdout=subprocess.DEVNULL)

args = sys.argv[1:]; kind = None; limit = 15; terms = []
while args:
    a = args.pop(0)
    if a == "-k": kind = args.pop(0)
    elif a == "-n": limit = int(args.pop(0))
    else: terms.append(a.lower())
if not terms:
    sys.exit(__doc__)
ORDER = ["gui", "setting", "uses", "command", "object", "class", "interface", "enum", "record", "mixin", "fun", "lang", "resource"]
hits = {}
for ln in open(IDX, encoding="utf-8"):
    k, name, loc, det = (ln.rstrip("\n").split("\t") + ["", "", "", ""])[:4]
    if kind and k != kind: continue
    hay = (name + " " + det).lower()
    if all(t in hay for t in terms):
        score = 0 if name.lower() in terms else 1 if name.lower().startswith(terms[0]) else 2
        hits.setdefault(k, []).append((score, name, loc, det))
for k in ORDER + [x for x in hits if x not in ORDER]:
    if k not in hits: continue
    rows = sorted(hits[k])[:limit]
    print("[%s] %d match%s" % (k, len(hits[k]), "" if len(hits[k]) == 1 else "es"))
    for _, name, loc, det in rows:
        print("  %-34s %s  %s" % (name[:34], loc, det[:90]))
if not hits: print("no match")
