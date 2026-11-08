"""FishMod animated trailer: renders frames with PIL, pipes to ffmpeg. Usage: python make_trailer.py [out.mp4] [--test]"""
import math, random, subprocess, sys, wave, os
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

W, H, FPS = 1280, 720, 30
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = next((a for a in sys.argv[1:] if not a.startswith("--")), os.path.join(HERE, "fishmod_trailer.mp4"))
ICON = r"D:\FishModDungeons\dungeons-26.1.2\src\main\resources\assets\fishmod\icon.png"
F = r"C:\Windows\Fonts"
def font(n, s): return ImageFont.truetype(os.path.join(F, n), s)
BIG, MID, SML, TINY = font("seguibl.ttf", 120), font("seguibl.ttf", 64), font("segoeuib.ttf", 34), font("segoeui.ttf", 28)
TITLE = font("seguibl.ttf", 96)

def ease(t): t = max(0, min(1, t)); return t * t * (3 - 2 * t)
def eout(t): t = max(0, min(1, t)); return 1 - (1 - t) ** 3
def lerp(a, b, t): return a + (b - a) * t
def hexc(h): return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))

# scenes: (start s, end s, key, title, tagline, bullets, accent)
FEATURES = [
    ("dungeons", "DUNGEONS", "Built for floor 7", ["Wither dragon timers & priority", "Terminal & puzzle solvers", "Full dungeon map", "Splits & personal-best pace"], "#ff4d6d"),
    ("kuudra", "KUUDRA", "Every phase, handled", ["Phase splits & fresh timers", "Supply & build helpers", "Chest value & profit", "Loot tracker"], "#ff9f1c"),
    ("farming", "FARMING", "Grow smarter", ["Visitors & pests", "Composter & Jacob's contests", "Crop milestones", "Mooshroom Cow perk tracker"], "#7bd957"),
    ("mining", "MINING", "Dig deeper", ["Gemstone & ore helpers", "Powder & event tracking", "Clean mining HUDs"], "#4cc9f0"),
    ("rift", "THE RIFT", "Step through", ["Rift tools & timers", "Everything in one UI"], "#b388ff"),
    ("more", "AND MORE", "Profile Viewer · Achievements · Cosmetics", ["Browse any SkyBlock profile in game", "Achievements with leaderboards", "Name colours, gradients & badges"], "#00e5c3"),
]
T_INTRO, T_LOGO, T_FEAT, T_CUST, T_OUT = 6.0, 7.0, 8.0, 9.0, 10.0
# timeline
t = 0.0
SC = []
SC.append(("intro", t, t + T_INTRO)); t += T_INTRO
SC.append(("logo", t, t + T_LOGO)); t += T_LOGO
for f in FEATURES:
    SC.append((f[0], t, t + 7.0)); t += 7.0
SC.append(("custom", t, t + T_CUST)); t += T_CUST
SC.append(("outro", t, t + T_OUT)); t += T_OUT
TOTAL = t

random.seed(7)
PART = [(random.random(), random.random(), random.uniform(0.3, 1.0), random.uniform(1, 3.5)) for _ in range(90)]

def bg(time, accent):
    y = np.linspace(0, 1, H)[:, None]; x = np.linspace(0, 1, W)[None, :]
    a = np.array(accent, float)
    glow = np.exp(-(((x - 0.5 - 0.15 * math.sin(time * 0.3)) * 1.4) ** 2 + ((y - 0.55) * 1.8) ** 2) * 3.2)
    base = np.array([8, 10, 18], float)
    img = base[None, None, :] + glow[:, :, None] * (a * 0.22)[None, None, :]
    vig = 1 - 0.55 * (((x - 0.5) * 1.6) ** 2 + ((y - 0.5) * 1.6) ** 2)
    img = img * np.clip(vig, 0.3, 1)[:, :, None]
    im = Image.fromarray(np.clip(img, 0, 255).astype("uint8"), "RGB").convert("RGBA")
    d = ImageDraw.Draw(im, "RGBA")
    for px, py, sp, sz in PART:
        yy = (py - time * 0.02 * sp) % 1.0
        xx = (px + 0.02 * math.sin(time * sp + px * 9)) % 1.0
        al = int(70 * sp * (0.6 + 0.4 * math.sin(time * 2 * sp + px * 20)))
        d.ellipse([xx * W - sz, yy * H - sz, xx * W + sz, yy * H + sz], fill=(*accent, max(al, 0)))
    return im

def text_layer(s, fnt, fill, glow=None, anchor="mm", pos=(W // 2, H // 2), spacing=0):
    lay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    if spacing:
        widths = [d.textlength(c, font=fnt) for c in s]
        total = sum(widths) + spacing * (len(s) - 1)
        x = pos[0] - total / 2
        for c, w in zip(s, widths):
            d.text((x, pos[1]), c, font=fnt, fill=fill, anchor="lm"); x += w + spacing
    else:
        d.text(pos, s, font=fnt, fill=fill, anchor=anchor)
    if glow:
        g = lay.resize((W // 4, H // 4)).filter(ImageFilter.GaussianBlur(6)).resize((W, H))
        g = Image.merge("RGBA", (*[Image.new("L", (W, H), c) for c in glow], g.getchannel("A").point(lambda v: min(255, v * 2))))
        return g, lay
    return None, lay

def put(im, s, fnt, fill, alpha=1.0, glow=None, anchor="mm", pos=(W // 2, H // 2), dy=0, spacing=0, dx=0):
    if alpha <= 0: return
    g, lay = text_layer(s, fnt, fill, glow, anchor, (pos[0] + dx, pos[1] + dy), spacing)
    for l, m in ((g, 0.7), (lay, 1.0)):
        if l is None: continue
        a = l.getchannel("A").point(lambda v: int(v * alpha * m))
        l.putalpha(a); im.alpha_composite(l)

def rrect(im, box, r, fill, alpha=1.0, outline=None):
    lay = Image.new("RGBA", (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(lay)
    d.rounded_rectangle(box, r, fill=(*fill, int(255 * alpha)), outline=(*outline, int(255 * alpha)) if outline else None, width=2)
    im.alpha_composite(lay)

icon = Image.open(ICON).convert("RGBA")

def scene_intro(im, p, tt):
    lines = ["Dungeons.   Kuudra.   Farming.", "Mining.   The Rift.", "Five mods. Five menus."]
    # beats
    for i, (s, a, b) in enumerate([(lines[0], 0.3, 2.3), (lines[1], 1.7, 3.5), ("What if one mod did it all?", 3.6, 6.0)]):
        al = ease((tt - a) / 0.5) * (1 - ease((tt - b + 0.4) / 0.4)) if i < 2 else ease((tt - a) / 0.6)
        big = i == 2
        put(im, s, MID if not big else MID, (255, 255, 255) if big else (190, 200, 220), al, glow=(120, 160, 255) if big else None, dy=(i * 0 + (1 - eout((tt - a) / 0.6)) * 25), pos=(W // 2, H // 2 - (0 if big else 30)))
    fade = 1 - ease((tt - 5.5) / 0.5)

def scene_logo(im, p, tt):
    k = eout(tt / 1.3)
    sz = int(200 + 40 * k)
    ic = icon.resize((sz, sz), Image.LANCZOS)
    lay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    lay.alpha_composite(ic, (W // 2 - sz // 2, 150 - sz // 2 + 40 - int(20 * k)))
    lay.putalpha(lay.getchannel("A").point(lambda v: int(v * ease(tt / 0.6))))
    # glow behind icon
    gl = lay.resize((W // 4, H // 4)).filter(ImageFilter.GaussianBlur(8)).resize((W, H))
    gl.putalpha(gl.getchannel("A").point(lambda v: int(v * 0.8)))
    im.alpha_composite(gl); im.alpha_composite(lay)
    put(im, "FISHMOD", BIG, (255, 255, 255), ease((tt - 0.5) / 0.7), glow=(0, 200, 255), pos=(W // 2, 400), spacing=int(30 - 20 * eout((tt - 0.5) / 1.5)))
    put(im, "THE ALL-IN-ONE HYPIXEL SKYBLOCK MOD", SML, (170, 215, 255), ease((tt - 1.8) / 0.7), pos=(W // 2, 500), spacing=4)
    put(im, "Free  ·  Open source  ·  Updated every week", TINY, (150, 160, 185), ease((tt - 3.5) / 0.7), pos=(W // 2, 570))

def scene_feature(im, tt, f, idx):
    key, title, tag, bullets, hx = f
    ac = hexc(hx)
    # big ghost number
    put(im, f"0{idx + 1}", font("seguibl.ttf", 380), ac, 0.07 * ease(tt / 0.5), pos=(W - 260, H // 2 + 20))
    # accent bar sweep
    bw = int(eout(tt / 0.7) * 120)
    d = ImageDraw.Draw(im); d.rectangle([110, 168, 110 + bw, 174], fill=(*ac, 255))
    put(im, title, TITLE, (255, 255, 255), ease(tt / 0.5), glow=ac, anchor="lm", pos=(110, 240), dx=-(1 - eout(tt / 0.7)) * 80)
    put(im, tag, SML, tuple(int(c * 0.55 + 255 * 0.45) for c in ac), ease((tt - 0.4) / 0.5), anchor="lm", pos=(112, 320))
    for i, b in enumerate(bullets):
        a = (tt - 1.0 - i * 0.45) / 0.5
        if a <= 0: continue
        y = 410 + i * 62
        e = eout(a)
        rrect(im, (110, y - 26, 110 + 560, y + 26), 14, (255, 255, 255), 0.06 * e, None)
        d = ImageDraw.Draw(im); d.ellipse([130, y - 7, 144, y + 7], fill=(*ac, int(255 * e)))
        put(im, b, TINY, (235, 240, 250), e, anchor="lm", pos=(162, y), dx=(1 - e) * 40)
    # right panel: mock HUD card with animated bars
    cx, cy = 1030, 360
    pe = eout((tt - 0.3) / 0.8)
    rrect(im, (cx - 200, cy - 200 + (1 - pe) * 40, cx + 200, cy + 200 + (1 - pe) * 40), 28, (16, 20, 34), 0.92 * pe, ac)
    put(im, title.title() + " HUD", SML, ac, pe, pos=(cx, cy - 150), dy=(1 - pe) * 40)
    for i in range(4):
        v = (0.35 + 0.6 * abs(math.sin(tt * 0.9 + i * 1.3 + idx))) * eout((tt - 0.8 - i * 0.2) / 0.8)
        y = cy - 85 + i * 62 + (1 - pe) * 40
        d = ImageDraw.Draw(im)
        d.rounded_rectangle([cx - 165, y, cx + 165, y + 22], 11, fill=(255, 255, 255, int(30 * pe)))
        if v > 0.02: d.rounded_rectangle([cx - 165, y, cx - 165 + 330 * v, y + 22], 11, fill=(*ac, int(255 * pe)))

def scene_custom(im, tt):
    put(im, "YOURS TO CHANGE", TITLE, (255, 255, 255), ease(tt / 0.5), glow=(150, 120, 255), pos=(W // 2, 170))
    put(im, "Every colour. Every text. Every HUD position.", SML, (190, 200, 225), ease((tt - 0.5) / 0.5), pos=(W // 2, 250))
    cols = ["#ff4d6d", "#ff9f1c", "#7bd957", "#4cc9f0", "#b388ff", "#00e5c3"]
    for i in range(6):
        a = eout((tt - 1.0 - i * 0.2) / 0.5)
        x = 240 + i * 160 + 0
        c = hexc(cols[(i + int(tt * 1.5)) % 6])
        y = 380 + math.sin(tt * 2 + i) * 8
        rrect(im, (x - 60, y - 60, x + 60, y + 60), 26, c, 0.9 * a, (255, 255, 255))
    # gradient name
    name = "Redfish"
    d = ImageDraw.Draw(im)
    total = sum(d.textlength(ch, font=MID) for ch in name); x = W / 2 - total / 2
    for i, ch in enumerate(name):
        h = (tt * 0.4 + i * 0.12) % 1
        r, g, b = [int(127 + 128 * math.sin(2 * math.pi * (h + o))) for o in (0, 1 / 3, 2 / 3)]
        put(im, ch, MID, (r, g, b), ease((tt - 3.0) / 0.5), anchor="lm", pos=(int(x), 540), glow=(r, g, b))
        x += d.textlength(ch, font=MID)
    put(im, "Cosmetics, name gradients & dark mode", TINY, (150, 160, 185), ease((tt - 3.5) / 0.5), pos=(W // 2, 620))

def scene_outro(im, tt):
    put(im, "FISHMOD", BIG, (255, 255, 255), ease(tt / 0.6), glow=(0, 200, 255), pos=(W // 2, 220), spacing=10)
    put(im, "One mod. Everything SkyBlock.", MID, (200, 225, 255), ease((tt - 0.8) / 0.6), pos=(W // 2, 340))
    put(im, "Free on Modrinth  ·  fishmod.dev", SML, (255, 255, 255), ease((tt - 2.2) / 0.6), pos=(W // 2, 460), glow=(0, 200, 255))
    put(im, "discord.gg/3mSuQUB8kk", SML, (130, 160, 255), ease((tt - 3.2) / 0.6), pos=(W // 2, 520))
    put(im, "Open source  ·  No scripted input  ·  Off until you turn it on", TINY, (150, 160, 185), ease((tt - 4.5) / 0.6), pos=(W // 2, 600))

ACC = {"intro": (90, 110, 200), "logo": (0, 170, 230), "custom": (150, 120, 255), "outro": (0, 170, 230)}
for f in FEATURES: ACC[f[0]] = hexc(f[4])

def render(time):
    for name, a, b in SC:
        if a <= time < b + 1e-9: break
    tt = time - a
    # accent crossfade
    ac = ACC[name]
    if tt < 0.6:
        prev = SC[SC.index((name, a, b)) - 1][0] if SC.index((name, a, b)) > 0 else name
        ac = tuple(int(lerp(ACC[prev][i], ac[i], ease(tt / 0.6))) for i in range(3))
    im = bg(time, ac)
    if name == "intro": scene_intro(im, 0, tt)
    elif name == "logo": scene_logo(im, 0, tt)
    elif name == "custom": scene_custom(im, tt)
    elif name == "outro": scene_outro(im, tt)
    else:
        i = [f[0] for f in FEATURES].index(name); scene_feature(im, tt, FEATURES[i], i)
    # fades
    dur = b - a
    fo = 0.0
    if tt < 0.35: fo = 1 - tt / 0.35
    if time > TOTAL - 1.0: fo = max(fo, (time - (TOTAL - 1.0)))
    if fo > 0:
        blk = Image.new("RGBA", (W, H), (0, 0, 0, int(255 * min(fo, 1)))); im.alpha_composite(blk)
    return im.convert("RGB")

def make_audio(path):
    sr = 44100; n = int(TOTAL * sr); tt = np.arange(n) / sr
    out = np.zeros(n)
    chords = [(55, 65.41, 82.41), (43.65, 65.41, 87.31), (51.91, 77.78, 103.83), (49.0, 73.42, 98.0)]
    seg = 7.0
    for i, c in enumerate(chords * 20):
        a, b = int(i * seg * sr), int(min((i + 1) * seg * sr, n))
        if a >= n: break
        x = tt[a:b] - tt[a]; env = np.minimum(1, x / 1.5) * np.minimum(1, (seg - x) / 1.5)
        for f in c:
            for h, g in ((1, 1.0), (2, 0.35), (3, 0.15)):
                out[a:b] += env * g * np.sin(2 * np.pi * f * h * x + np.sin(x * 0.5)) * 0.07
    # pulse from logo reveal onward
    start = T_INTRO
    for k in range(int((TOTAL - start) * 2)):
        bt = start + k * 0.5; a = int(bt * sr); L = int(0.25 * sr)
        if a + L > n: break
        x = np.arange(L) / sr
        out[a:a + L] += np.sin(2 * np.pi * (60 - 20 * x * 4) * x) * np.exp(-x * 14) * 0.45 * (1.0 if k % 2 == 0 else 0.6)
        if k % 2 == 1: out[a:a + L] += np.random.randn(L) * np.exp(-x * 40) * 0.05
    # riser into logo
    r0, r1 = int((T_INTRO - 2.5) * sr), int(T_INTRO * sr)
    x = np.linspace(0, 1, r1 - r0); out[r0:r1] += np.sin(2 * np.pi * (200 + 1200 * x ** 2) * np.linspace(0, 2.5, r1 - r0)) * x ** 2 * 0.08
    fade = np.minimum(1, (TOTAL - tt) / 2.0) * np.minimum(1, tt / 0.5)
    out = np.tanh(out * 1.4) * fade * 0.8
    st = np.stack([out, np.roll(out, 90)], 1)
    with wave.open(path, "wb") as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(sr); w.writeframes((st * 32767).astype("<i2").tobytes())

if __name__ == "__main__":
    wav = os.path.join(HERE, "_music.wav")
    if "--test" in sys.argv:
        os.makedirs(os.path.join(HERE, "stills"), exist_ok=True)
        pts = [3, 5, 8, 14, 22, 29, 36, 43, 52, 60, 66, 72]
        for s in pts: render(min(s, TOTAL - 0.1)).save(os.path.join(HERE, "stills", f"t{s:02d}.png"))
        print("total", TOTAL); sys.exit()
    make_audio(wav)
    p = subprocess.Popen(["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
                          "-i", wav, "-vf", "scale=1920:1080:flags=lanczos", "-c:v", "libx264", "-preset", "medium", "-crf", "17", "-pix_fmt", "yuv420p",
                          "-c:a", "aac", "-b:a", "192k", "-shortest", OUT], stdin=subprocess.PIPE)
    N = int(TOTAL * FPS)
    for i in range(N):
        p.stdin.write(render(i / FPS).tobytes())
        if i % 150 == 0: print(i, "/", N, flush=True)
    p.stdin.close(); p.wait(); print("done", OUT)
