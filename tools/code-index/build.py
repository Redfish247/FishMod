#!/usr/bin/env python3
"""Build .codeindex/index.tsv: one line per symbol -> kind<TAB>name<TAB>path:line<TAB>detail."""
import os, re, sys, collections

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC = os.path.join(ROOT, "dungeons-26.1.2", "src", "main")
OUT = os.path.join(ROOT, ".codeindex")
MOD = ("public|private|protected|internal|data|sealed|abstract|open|final|enum|annotation|inner|"
       "override|inline|suspend|operator|infix|tailrec|external|static|const|lateinit|companion|value")
RE_TYPE = re.compile(r"^\s*(?:(?:%s)\s+)*(class|object|interface)\s+(\w+)" % MOD)
RE_FUN = re.compile(r"^\s*(?:(?:%s)\s+)*fun\s+(?:<[^>]+>\s*)?(?:[\w.<>?,\s]+\.)?(\w+)\s*\(" % MOD)
RE_CFG = re.compile(r"@ConfigValue\b.*?\bvar\s+(\w+)\s*:\s*([\w<>?,. ]+?)\s*(?:=|$)")
RE_JTYPE = re.compile(r"^\s*(?:(?:public|private|protected|abstract|final|static)\s+)*(class|interface|enum|record)\s+(\w+)")
RE_JMETH = re.compile(r"^\s*(?:(?:public|private|protected|static|final|abstract|synchronized)\s+)+[\w<>\[\],.? ]+?\s+(\w+)\s*\(")
RE_MIXIN = re.compile(r"@Mixin\(\s*(?:value\s*=\s*)?\{?\s*([\w.]+)\.class")
RE_MIXIN_T = re.compile(r'targets\s*=\s*"?([\w.$/]+)')
RE_CMD = re.compile(r'(?:ClientCommands|ClientCommandManager|Commands)\.literal\("([^"]+)"\)')
RE_GUI = re.compile(r'\b(\w*Setting|Feature)\(\s*"([^"]+)"[^\n]*?\b(\w+)::(\w+)')
RE_PKG = re.compile(r"^package\s+([\w.]+)")

rows = []
cfg_names = {}
files = []
for dp, _, fns in os.walk(SRC):
    for fn in fns:
        if fn.endswith((".kt", ".java")):
            files.append(os.path.join(dp, fn))
files.sort()

def rel(p): return os.path.relpath(p, ROOT).replace("\\", "/")

texts = {}
for p in files:
    try:
        lines = open(p, encoding="utf-8", errors="replace").read().split("\n")
    except OSError:
        continue
    texts[p] = lines
    kt = p.endswith(".kt")
    pkg = ""
    owner = None
    for i, ln in enumerate(lines, 1):
        loc = "%s:%d" % (rel(p), i)
        m = RE_PKG.match(ln) if not pkg else None
        if m: pkg = m.group(1)
        if kt:
            m = RE_TYPE.match(ln)
            if m:
                rows.append((m.group(1), m.group(2), loc, pkg)); owner = m.group(2) if m.group(1) == "object" else owner
                continue
            m = RE_CFG.search(ln)
            if m:
                rows.append(("setting", m.group(1), loc, "%s.%s: %s" % (owner or "?", m.group(1), m.group(2).strip())))
                cfg_names[m.group(1)] = loc
                continue
            m = RE_FUN.match(ln)
            if m:
                rows.append(("fun", m.group(1), loc, ln.strip()[:110]))
        else:
            m = RE_JTYPE.match(ln)
            if m:
                rows.append((m.group(1), m.group(2), loc, pkg)); continue
            m = RE_MIXIN.search(ln) or RE_MIXIN_T.search(ln)
            if m:
                cls = [x for x in lines[i:i + 8] if "class " in x or "interface " in x]
                name = re.search(r"(?:class|interface)\s+(\w+)", cls[0]).group(1) if cls else "?"
                rows.append(("mixin", name, loc, "targets " + m.group(1)))
                continue
            m = RE_JMETH.match(ln)
            if m and not ln.strip().startswith(("return", "new ", "else")):
                rows.append(("fun", m.group(1), loc, ln.strip()[:110]))
        for c in RE_CMD.findall(ln):
            rows.append(("command", c, loc, ln.strip()[:110]))
        if p.endswith("FishModScreen.kt"):
            g = RE_GUI.search(ln)
            if g:
                rows.append(("gui", g.group(2), loc, "%s -> %s.%s" % (g.group(1), g.group(3), g.group(4))))

# usage files per setting (word match, excluding the defining file)
pat = re.compile(r"\b(%s)\b" % "|".join(sorted(map(re.escape, cfg_names), key=len, reverse=True)))
uses = collections.defaultdict(collections.Counter)
for p, lines in texts.items():
    if "/values/" in p.replace("\\", "/"): continue
    for ln in lines:
        for n in pat.findall(ln):
            uses[n][rel(p)] += 1
for n, loc in cfg_names.items():
    if uses.get(n):
        top = ", ".join("%s(%d)" % (f.split("/")[-1], c) for f, c in uses[n].most_common(4))
        rows.append(("uses", n, loc, top))

# mixins.json + lang keys + resources
for f in ("fishmod.mixins.json",):
    pth = os.path.join(SRC, "resources", f)
    if os.path.exists(pth):
        rows.append(("resource", f, rel(pth), "mixin registry"))
lang = os.path.join(SRC, "resources", "assets", "fishmod", "lang", "en_us.json")
if os.path.exists(lang):
    for i, ln in enumerate(open(lang, encoding="utf-8").read().split("\n"), 1):
        m = re.match(r'\s*"([^"]+)":\s*"(.*?)",?\s*$', ln)
        if m: rows.append(("lang", m.group(1), "%s:%d" % (rel(lang), i), m.group(2)[:80]))

os.makedirs(OUT, exist_ok=True)
with open(os.path.join(OUT, "index.tsv"), "w", encoding="utf-8", newline="\n") as f:
    for r in rows:
        f.write("\t".join(x.replace("\t", " ") for x in r) + "\n")
print("indexed %d files, %d rows -> %s" % (len(files), len(rows), os.path.join(OUT, "index.tsv")))
