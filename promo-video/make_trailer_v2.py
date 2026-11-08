"""FishMod trailer v2: fast cuts, one specific feature per card, light music. Usage: python make_trailer_v2.py [--test]"""
import math, os, sys, subprocess, wave
import numpy as np
from PIL import Image, ImageDraw
from make_trailer import (W, H, FPS, bg, put, rrect, ease, eout, lerp, hexc, icon, font,
                          BIG, MID, SML, TINY, TITLE)

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "fishmod_trailer_v2.mp4")
HUGE = font("seguibl.ttf", 128)
NAME = font("seguibl.ttf", 78)
CHIP = font("segoeuib.ttf", 26)

# (category, accent, [(name, desc, readout)])  readout: (kind, ...)
CATS = [
    ("DUNGEONS", "#ff4d6d", [
        ("Key & Room Timer", "Live clear time for every room", ("timer", 28.0, "ROOM TIME")),
        ("Leap Menu", "Leap to the right player, fast", ("text", "LEAP")),
        ("Livid Solver", "Find the real Livid instantly", ("text", "LIVID")),
        ("Secret Overlay", "Know what is left in the room", ("count", 7, "", "/9", "SECRETS")),
    ]),
    ("SOLVERS & MAP", "#ff7a59", [
        ("Terminal Solver", "Every F7 terminal, solved on screen", ("grid", "SOLVED")),
        ("Dungeon Map", "Full map, room colours, player heads", ("map",)),
        ("Blood Solver", "Blood room, handled", ("text", "BLOOD")),
    ]),
    ("FLOOR 7", "#c77dff", [
        ("Dragon Timers", "Ranked spawn timers and priority calls", ("timer", 5.0, "#1  ICE DRAGON")),
        ("Relic Times", "Beat your best relic split", ("count", 4.8, "", "s", "RELIC TIME")),
        ("Goldor Splits", "Section progress and gate display", ("timer", 24.0, "GATE 3")),
    ]),
    ("KUUDRA", "#ff9f1c", [
        ("Phase Splits", "Timers for every Kuudra phase", ("timer", 42.0, "PHASE 2")),
        ("Supply Helper", "Waypoints, pile spots, pearl throws", ("text", "SUPPLY")),
        ("Chest Profit", "Value and profit when you open the paid chest", ("count", 4.2, "+", "M", "COINS")),
        ("Auto Requeue", "Requeues the same tier, with a !dt skip", ("text", "NEXT RUN")),
    ]),
    ("FARMING", "#7bd957", [
        ("Visitors", "Timer, alerts, shopping list", ("count", 6, "", "", "WAITING")),
        ("Pest Tracker", "Spawn alerts and a pest finder", ("count", 8, "", "", "PESTS ALIVE")),
        ("Jacob's Contests", "Contest info right on your HUD", ("text", "CONTEST")),
        ("Garden Keybinds", "Use your own keys while farming", ("text", "YOUR KEYS")),
    ]),
    ("MINING", "#4cc9f0", [
        ("Mineshaft Pity", "See your pity before you dive", ("count", 92, "", "%", "PITY")),
        ("Fossil Solver", "Next best slot highlighted", ("grid", "FOUND")),
        ("Mining Profit", "Powder and loot, tracked live", ("count", 1.8, "+", "M", "PER HOUR")),
    ]),
    ("THE RIFT", "#b388ff", [
        ("Vampire Helper", "Vampire slayer made easy", ("text", "VAMPIRE")),
        ("Mirrorverse Parkour", "Parkour route shown as you go", ("map",)),
        ("Crux Helper", "Wyld Woods mobs highlighted", ("text", "CRUX")),
    ]),
    ("DIANA & SLAYER", "#ffd166", [
        ("Burrow Guessing", "Find the next burrow", ("text", "BURROW")),
        ("Slayer Boss Timer", "Kill time on every boss", ("timer", 18.0, "BOSS TIME")),
        ("Carry Tracker", "Track your slayer carries", ("count", 12, "", "", "CARRIES")),
    ]),
    ("EVERYWHERE", "#00e5c3", [
        ("Profile Viewer", "Browse any SkyBlock profile in game", ("text", "/pv")),
        ("Achievements", "Difficulty ratings and leaderboards", ("count", 100, "#", "", "LEADERBOARD")),
        ("Cosmetics", "Name colours, gradients and badges", ("gradient", "Redfish")),
    ]),
]
FEAT_T, CAT_T = 1.6, 0.75
INTRO, LOGO, CUSTOM, OUTRO = 4.4, 3.2, 4.0, 5.0

SC = []  # (kind, start, end, payload)
t = 0.0
SC.append(("intro", t, t + INTRO, None)); t += INTRO
SC.append(("logo", t, t + LOGO, None)); t += LOGO
for ci, (cn, cc, feats) in enumerate(CATS):
    SC.append(("cat", t, t + CAT_T, (cn, cc, ci))); t += CAT_T
    for fi, f in enumerate(feats):
        SC.append(("feat", t, t + FEAT_T, (cn, cc, f, fi, len(feats)))); t += FEAT_T
SC.append(("custom", t, t + CUSTOM, None)); t += CUSTOM
SC.append(("outro", t, t + OUTRO, None)); t += OUTRO
TOTAL = t

def readout(im, r, tt, ac, cx, cy):
    k = r[0]; p = eout(tt / 0.9)
    if k == "timer":
        v = max(0.0, r[1] * (1 - tt / (FEAT_T + 0.3)))
        put(im, f"{v:0.1f}s", HUGE, (255, 255, 255), 1, glow=ac, pos=(cx, cy - 10))
        sub = r[2]
    elif k == "count":
        v = r[1] * p
        s = f"{v:.1f}" if isinstance(r[1], float) else f"{int(round(v))}"
        put(im, f"{r[2]}{s}{r[3]}", HUGE, (255, 255, 255), 1, glow=ac, pos=(cx, cy - 10))
        sub = r[4]
    elif k == "text":
        put(im, r[1], NAME, (255, 255, 255), p, glow=ac, pos=(cx, cy - 10)); sub = ""
    elif k == "gradient":
        d = ImageDraw.Draw(im); tot = sum(d.textlength(c, font=NAME) for c in r[1]); x = cx - tot / 2
        for i, c in enumerate(r[1]):
            h = (tt * 0.8 + i * 0.12) % 1
            col = tuple(int(127 + 128 * math.sin(2 * math.pi * (h + o))) for o in (0, 1 / 3, 2 / 3))
            put(im, c, NAME, col, p, anchor="lm", pos=(int(x), cy - 10), glow=col); x += d.textlength(c, font=NAME)
        sub = "NAME GRADIENT"
    elif k == "grid":
        n = 3; s = 66; g = 12; x0 = cx - (n * s + (n - 1) * g) / 2; y0 = cy - (n * s + (n - 1) * g) / 2 - 30
        d = ImageDraw.Draw(im, "RGBA")
        for i in range(n * n):
            on = tt > 0.15 + i * 0.1
            xx, yy = x0 + (i % n) * (s + g), y0 + (i // n) * (s + g)
            d.rounded_rectangle([xx, yy, xx + s, yy + s], 12, fill=(*ac, 255) if on else (255, 255, 255, 28))
        sub = r[1]
    elif k == "map":
        n = 4; s = 62; g = 10; x0 = cx - (n * s + (n - 1) * g) / 2; y0 = cy - (n * s + (n - 1) * g) / 2 - 30
        d = ImageDraw.Draw(im, "RGBA")
        order = [0, 1, 5, 6, 10, 11, 15, 14, 9, 8, 12, 13]
        for j, i in enumerate(order):
            if tt < 0.1 + j * 0.07: continue
            xx, yy = x0 + (i % n) * (s + g), y0 + (i // n) * (s + g)
            col = (*ac, 255) if i in (0, 15) else (255, 255, 255, 190)
            d.rounded_rectangle([xx, yy, xx + s, yy + s], 8, fill=col)
        sub = "LIVE MAP"
    put(im, sub, CHIP, tuple(int(c * 0.6 + 255 * 0.4) for c in ac), p, pos=(cx, cy + 120), spacing=5)

def s_feat(im, tt, pay):
    cn, cc, (name, desc, ro), fi, nf = pay
    ac = hexc(cc)
    a = eout(tt / 0.25)
    # chip + dots
    put(im, cn, CHIP, ac, 1, anchor="lm", pos=(90, 80))
    d = ImageDraw.Draw(im)
    for i in range(nf):
        d.ellipse([90 + i * 26, 118, 104 + i * 26, 132], fill=(*ac, 255) if i == fi else (255, 255, 255, 60))
    nf_ = NAME
    for sz in (78, 66, 56, 48):
        nf_ = font("seguibl.ttf", sz)
        if ImageDraw.Draw(im).textlength(name, font=nf_) <= 650: break
    put(im, name, nf_, (255, 255, 255), a, glow=ac, anchor="lm", pos=(90, 330), dx=-(1 - a) * 90)
    put(im, desc, SML, (200, 210, 230), eout((tt - 0.1) / 0.3), anchor="lm", pos=(94, 420), dx=-(1 - eout((tt - 0.1) / 0.3)) * 60)
    d.rectangle([94, 466, 94 + int(eout(tt / 0.5) * 150), 471], fill=(*ac, 255))
    cx, cy = 1020, 360
    rrect(im, (cx - 240, cy - 190, cx + 240, cy + 190), 30, (16, 20, 34), 0.88 * a, ac)
    readout(im, ro, tt, ac, cx, cy)

def s_cat(im, tt, pay):
    cn, cc, ci = pay; ac = hexc(cc)
    put(im, cn, TITLE, (255, 255, 255), 1, glow=ac, pos=(W // 2, H // 2), spacing=int(40 - 30 * eout(tt / 0.4)))
    d = ImageDraw.Draw(im); w = int(eout(tt / 0.4) * 400)
    d.rectangle([W // 2 - w // 2, H // 2 + 70, W // 2 + w // 2, H // 2 + 76], fill=(*ac, 255))

def s_intro(im, tt):
    beats = [("Dungeons. Kuudra. Farming.", 0.0), ("Mining. The Rift. And more.", 1.4), ("ALL IN ONE MOD.", 2.8)]
    cur = [b for b in beats if tt >= b[1]][-1]
    a = eout((tt - cur[1]) / 0.2)
    big = cur[0] == "ALL IN ONE MOD."
    put(im, cur[0], TITLE if big else MID, (255, 255, 255) if big else (190, 200, 225), a, glow=(120, 160, 255) if big else None, dy=(1 - a) * 30)

def s_logo(im, tt):
    k = eout(tt / 0.7); sz = int(200 + 40 * k)
    ic = icon.resize((sz, sz), Image.LANCZOS); lay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    lay.alpha_composite(ic, (W // 2 - sz // 2, 170 - sz // 2))
    lay.putalpha(lay.getchannel("A").point(lambda v: int(v * ease(tt / 0.3)))); im.alpha_composite(lay)
    put(im, "FISHMOD", BIG, (255, 255, 255), ease((tt - 0.2) / 0.4), glow=(0, 200, 255), pos=(W // 2, 400), spacing=int(26 - 16 * eout((tt - 0.2) / 0.8)))
    put(im, "THE ALL-IN-ONE HYPIXEL SKYBLOCK MOD", SML, (170, 215, 255), ease((tt - 0.9) / 0.4), pos=(W // 2, 500), spacing=4)

def s_custom(im, tt):
    put(im, "YOURS TO CHANGE", TITLE, (255, 255, 255), eout(tt / 0.3), glow=(150, 120, 255), pos=(W // 2, 160))
    put(im, "Every colour. Every text. Every HUD position.", SML, (190, 200, 225), eout((tt - 0.3) / 0.3), pos=(W // 2, 240))
    cols = ["#ff4d6d", "#ff9f1c", "#7bd957", "#4cc9f0", "#b388ff", "#00e5c3"]
    for i in range(6):
        aa = eout((tt - 0.6 - i * 0.1) / 0.3)
        c = hexc(cols[(i + int(tt * 2)) % 6]); x = 240 + i * 160; y = 390 + math.sin(tt * 3 + i) * 8
        rrect(im, (x - 60, y - 60, x + 60, y + 60), 26, c, 0.9 * aa, (255, 255, 255))
    put(im, "Off until you turn it on. Dark mode included.", TINY, (150, 160, 185), eout((tt - 2.0) / 0.4), pos=(W // 2, 540))

def s_outro(im, tt):
    put(im, "FISHMOD", BIG, (255, 255, 255), ease(tt / 0.4), glow=(0, 200, 255), pos=(W // 2, 230), spacing=10)
    put(im, "Free on Modrinth  ·  fishmod.dev", MID, (255, 255, 255), ease((tt - 0.7) / 0.4), pos=(W // 2, 370), glow=(0, 200, 255))
    put(im, "discord.gg/3mSuQUB8kk", SML, (130, 160, 255), ease((tt - 1.5) / 0.4), pos=(W // 2, 460))
    put(im, "Open source  ·  No scripted input", TINY, (150, 160, 185), ease((tt - 2.4) / 0.4), pos=(W // 2, 540))

ACC = {"intro": (90, 110, 200), "logo": (0, 170, 230), "custom": (150, 120, 255), "outro": (0, 170, 230)}

def render(time):
    for i, (kind, a, b, pay) in enumerate(SC):
        if a <= time < b: break
    tt = time - a
    ac = hexc(pay[1]) if kind in ("cat", "feat") else ACC[kind]
    im = bg(time, ac)
    if kind == "intro": s_intro(im, tt)
    elif kind == "logo": s_logo(im, tt)
    elif kind == "cat": s_cat(im, tt, pay)
    elif kind == "feat": s_feat(im, tt, pay)
    elif kind == "custom": s_custom(im, tt)
    else: s_outro(im, tt)
    if kind == "feat" and tt < 0.12:  # quick white flash on cut
        im.alpha_composite(Image.new("RGBA", (W, H), (255, 255, 255, int(22 * (1 - tt / 0.12)))))
    if time < 0.3: im.alpha_composite(Image.new("RGBA", (W, H), (0, 0, 0, int(255 * (1 - time / 0.3)))))
    if time > TOTAL - 0.8: im.alpha_composite(Image.new("RGBA", (W, H), (0, 0, 0, int(255 * min(1, (time - (TOTAL - 0.8)) / 0.8)))))
    from PIL import ImageEnhance
    return ImageEnhance.Brightness(im.convert("RGB")).enhance(0.8)

def note(m): return 440.0 * 2 ** ((m - 69) / 12)

def make_audio(path):
    sr = 44100; n = int(TOTAL * sr); out = np.zeros(n); bpm = 124; beat = 60 / bpm
    prog = [(57, [57, 60, 64]), (53, [53, 57, 60]), (48, [48, 52, 55]), (55, [55, 59, 62])]  # Am F C G
    def pluck(start, m, gain):
        a = int(start * sr); L = int(0.45 * sr)
        if a + L > n or a < 0: return
        x = np.arange(L) / sr; f = note(m)
        w = np.sin(2 * np.pi * f * x) + 0.35 * np.sin(2 * np.pi * 2 * f * x) + 0.1 * np.sin(2 * np.pi * 3 * f * x)
        out[a:a + L] += w * np.exp(-x * 9) * np.minimum(1, x / 0.004) * gain
    bar = beat * 4
    for b in range(int(TOTAL / bar) + 1):
        root, ch = prog[b % 4]; t0 = b * bar
        # soft pad
        a, L = int(t0 * sr), int(min(bar + 0.6, TOTAL - t0) * sr)
        if L > 0:
            x = np.arange(L) / sr; env = np.minimum(1, x / 0.4) * np.minimum(1, (bar + 0.6 - x) / 0.6)
            for m in ch: out[a:a + L] += np.sin(2 * np.pi * note(m + 12) * x) * env * 0.025
            out[a:a + L] += np.sin(2 * np.pi * note(root - 12) * x) * env * 0.05
        # arpeggio, 8ths, picks up from the logo on
        if t0 >= INTRO - 0.5:
            pat = [ch[0] + 12, ch[1] + 12, ch[2] + 12, ch[1] + 24, ch[2] + 12, ch[1] + 12, ch[0] + 24, ch[1] + 12]
            for k, m in enumerate(pat): pluck(t0 + k * beat / 2, m, 0.09 if k % 2 == 0 else 0.06)
        # soft hat on offbeats
        if t0 >= INTRO:
            for k in range(8):
                a = int((t0 + k * beat / 2) * sr); L = int(0.05 * sr)
                if a + L < n:
                    x = np.arange(L) / sr; nz = np.diff(np.random.randn(L + 1))
                    out[a:a + L] += nz * np.exp(-x * 90) * (0.05 if k % 2 else 0.025)
    fade = np.minimum(1, (TOTAL - np.arange(n) / sr) / 1.5) * np.minimum(1, np.arange(n) / sr / 0.4)
    out = np.tanh(out * 1.2) * fade * 0.7
    st = np.stack([out, np.roll(out, 70)], 1)
    with wave.open(path, "wb") as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(sr); w.writeframes((st * 32767).astype("<i2").tobytes())

if __name__ == "__main__":
    if "--test" in sys.argv:
        os.makedirs(os.path.join(HERE, "stills"), exist_ok=True)
        for s in (1, 2.5, 4, 7, 8.5, 12, 16, 22, 28, TOTAL - 4, TOTAL - 1.5): render(min(s, TOTAL - 0.05)).save(os.path.join(HERE, "stills", f"v2_{s:05.1f}.png"))
        print("total", TOTAL); sys.exit()
    wav = os.path.join(HERE, "_music_v2.wav"); make_audio(wav)
    p = subprocess.Popen(["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-", "-i", wav,
                          "-vf", "scale=1920:1080:flags=lanczos", "-c:v", "libx264", "-preset", "medium", "-crf", "17", "-pix_fmt", "yuv420p",
                          "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", "-shortest", OUT], stdin=subprocess.PIPE)
    N = int(TOTAL * FPS)
    for i in range(N): p.stdin.write(render(i / FPS).tobytes())
    p.stdin.close(); p.wait(); print("done", OUT)
