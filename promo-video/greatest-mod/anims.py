"""Animated inserts for "How I created Hypixel SkyBlock's greatest mod". Usage: python anims.py [name ...] [--still]"""
import math, os, sys, subprocess, random
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
from PIL import Image, ImageDraw, ImageEnhance
from make_trailer import W, H, FPS, bg, put, rrect, ease, eout, hexc, icon, font, BIG, MID, SML, TINY, TITLE

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "anims")
MONO = font("consola.ttf", 18)
CYAN, PINK, GOLD, GREEN, PURPLE = (0, 200, 255), hexc("#ff4d6d"), hexc("#ffd166"), hexc("#7bd957"), hexc("#b388ff")

# illustrative Kotlin for the code wall
CODE = """@ConfigValue(name = "Wither Dragons", category = "Floor 7")
var witherDragonsEnabled = false
fun onDragonSpawn(color: DragonColor, tick: Int) {
    val prio = DragonPriority.forClass(playerClass, power)
    timers[color] = SPAWN_TICKS - tick
}
@ConfigValue(name = "Terminal Solver", category = "Solvers")
var terminalSolver = false
override fun render(ctx: GuiGraphics, mouseX: Int, mouseY: Int) {
    slots.filter { it.isSolution }.forEach { highlight(ctx, it) }
}
object SecretTracker : FishFeature("Secret Overlay") {
    fun onRoomEnter(room: DungeonRoom) = show(room.secretsLeft)
}
class HudEditorScreen : FishScreen("HUD Editor") {
    override fun mouseDragged(x: Double, y: Double) = drag(selected, x, y)
}""".split("\n")

def a01(t, dur):  # code wall + line counter
    im = bg(t, CYAN); d = ImageDraw.Draw(im, "RGBA")
    off = t * 60
    for i in range(40):
        line = CODE[(i + int(off // 26)) % len(CODE)]
        y = i * 26 - (off % 26)
        d.text((80, y), line, font=MONO, fill=(120, 200, 255, 70))
    im.alpha_composite(Image.new("RGBA", (W, H), (8, 10, 18, 150)))
    n = int(146199 * eout(t / (dur - 0.8)))
    put(im, f"{n:,}", font("seguibl.ttf", 170), (255, 255, 255), 1, glow=CYAN, pos=(W // 2, H // 2 - 30))
    put(im, "LINES OF CODE", SML, (170, 215, 255), ease(t / 0.5), pos=(W // 2, H // 2 + 90), spacing=6)
    return im

def a02_fallback(t, dur):
    im = bg(t, CYAN)
    put(im, "HOW I CREATED", MID, (200, 225, 255), ease(t / 0.5), pos=(W // 2, 250), spacing=8)
    put(im, "HYPIXEL SKYBLOCK'S", TITLE, (255, 255, 255), ease((t - 0.4) / 0.5), glow=CYAN, pos=(W // 2, 360))
    put(im, "GREATEST MOD", BIG, GOLD, ease((t - 0.9) / 0.5), glow=GOLD, pos=(W // 2, 490), spacing=int(30 - 20 * eout((t - 0.9) / 1.2)))
    return im

MODS = [("Dungeon mod", PINK), ("Kuudra mod", hexc("#ff9f1c")), ("Farming mod", GREEN), ("Mining mod", hexc("#4cc9f0")), ("Rift mod", PURPLE)]
def a03(t, dur):  # five windows stack, glitch, collapse into logo
    im = bg(t, (90, 110, 200))
    col = ease((t - 3.0) / 0.8)
    for i, (name, c) in enumerate(MODS):
        a = eout((t - i * 0.35) / 0.4)
        if a <= 0: continue
        x0, y0 = 220 + i * 150, 140 + i * 70
        jitter = (random.Random(int(t * 20) + i).randint(-6, 6) if 2.0 < t < 3.0 else 0)
        cx, cy = W // 2, H // 2
        x = x0 + (cx - 200 - x0) * col + jitter; y = y0 + (cy - 130 - y0) * col
        sc = 1 - 0.8 * col
        w, h = 400 * sc, 260 * sc
        rrect(im, (x, y, x + w, y + h), 14, (18, 22, 36), a * (1 - col), c)
        if col < 0.5:
            d = ImageDraw.Draw(im); d.rectangle([x, y, x + w, y + 34 * sc], fill=(*c, int(255 * a)))
            put(im, name, TINY, (10, 10, 20), a * (1 - col * 2), anchor="lm", pos=(int(x + 14), int(y + 17)))
            put(im, "Settings", TINY, (180, 190, 210), a * (1 - col * 2), anchor="lm", pos=(int(x + 20), int(y + 70)))
    if 2.0 < t < 3.0:
        put(im, "5 MENUS. 5 CONFIGS. 5x THE LAG.", SML, PINK, ease((t - 2.0) / 0.2), pos=(W // 2, 650))
    if t > 3.6:
        k = eout((t - 3.6) / 0.6); sz = int(80 + 160 * k)
        ic = icon.resize((sz, sz)); lay = Image.new("RGBA", (W, H)); lay.alpha_composite(ic, (W // 2 - sz // 2, H // 2 - sz // 2 - 40))
        im.alpha_composite(lay)
        put(im, "FISHMOD", MID, (255, 255, 255), k, glow=CYAN, pos=(W // 2, H // 2 + 130), spacing=8)
    return im

def card_commit(t, dur, title, sub, date, accent=GREEN):
    im = bg(t, accent); a = eout(t / 0.5)
    y = 260 + (1 - a) * 60
    rrect(im, (300, y, 980, y + 220), 22, (18, 22, 36), 0.95 * a, accent)
    d = ImageDraw.Draw(im); d.ellipse([340, y + 40, 380, y + 80], fill=(*accent, int(255 * a)))
    put(im, "commit", TINY, (150, 160, 185), a, anchor="lm", pos=(400, int(y + 60)))
    put(im, title, SML, (255, 255, 255), a, anchor="lm", pos=(340, int(y + 120)))
    put(im, sub, TINY, (190, 200, 225), eout((t - 0.4) / 0.4), anchor="lm", pos=(340, int(y + 170)))
    put(im, date, MID, accent, eout((t - 0.8) / 0.5), glow=accent, pos=(W // 2, 600))
    return im

def a05(t, dur): return card_commit(t, dur, "FishMod initial commit", "Party Finder stats, dungeon features, splits", "9 APRIL 2026")

def a06(t, dur):  # lead fanning out to sessions
    im = bg(t, PURPLE); d = ImageDraw.Draw(im, "RGBA")
    lead = (W // 2, 160)
    kids = [("feat: dragon timers", PINK), ("feat: profile viewer", CYAN), ("bug: map crash", hexc("#ff9f1c")), ("review: solvers", GREEN), ("research: garden", GOLD)]
    a0 = eout(t / 0.4)
    rrect(im, (lead[0] - 150, lead[1] - 40, lead[0] + 150, lead[1] + 40), 20, (30, 24, 50), a0, PURPLE)
    put(im, "lead session", SML, (255, 255, 255), a0, pos=lead)
    for i, (name, c) in enumerate(kids):
        a = eout((t - 0.6 - i * 0.25) / 0.4)
        if a <= 0: continue
        x = 170 + i * 235; y = 470
        ex, ey = lead[0] + (x - lead[0]) * a, lead[1] + 40 + (y - 40 - lead[1] - 40) * a
        d.line([lead[0], lead[1] + 40, ex, ey], fill=(*c, 200), width=4)
        rrect(im, (x - 105, y - 34, x + 105, y + 34), 16, (18, 22, 36), a, c)
        put(im, name, font("segoeuib.ttf", 20), c, a, pos=(x, y))
        # pulse along line while working
        p = (t * 0.8 + i * 0.2) % 1
        d.ellipse([lead[0] + (x - lead[0]) * p - 5, lead[1] + 40 + (y - 34 - lead[1] - 40) * p - 5,
                   lead[0] + (x - lead[0]) * p + 5, lead[1] + 40 + (y - 34 - lead[1] - 40) * p + 5], fill=(*c, int(255 * a)))
    put(im, "dozens of sessions, one feature each", SML, (190, 200, 225), eout((t - 2.2) / 0.5), pos=(W // 2, 620))
    return im

CREDITS = [("Odin", "puzzle & terminal solvers"), ("NoammAddons", "leap menu, wither dragons"), ("SkyHanni", "Diana arrow guess, Rift data"),
           ("BetterPV", "the profile viewer"), ("SkyblockOverhaul", "Diana features"), ("BladeMasterGabe", "splits"), ("22yrs", "dungeon map")]
def a07(t, dur):
    im = bg(t, GOLD)
    put(im, "BUILT ON THE SHOULDERS OF", SML, GOLD, eout(t / 0.4), pos=(W // 2, 100), spacing=5)
    for i, (n, w) in enumerate(CREDITS):
        a = eout((t - 0.4 - i * 0.3) / 0.4)
        y = 180 + i * 72
        put(im, n, font("seguibl.ttf", 40), (255, 255, 255), a, anchor="rm", pos=(W // 2 - 20, y), dx=-(1 - a) * 40)
        put(im, w, TINY, (190, 200, 225), a, anchor="lm", pos=(W // 2 + 20, y), dx=(1 - a) * 40)
    return im

def a09(t, dur):  # crash glitch overlay card
    im = bg(t, (255, 40, 60)); r = random.Random(int(t * 24))
    for _ in range(int(14 * (1 - min(1, t / dur)) + 4)):
        y = r.randint(0, H); h = r.randint(4, 30)
        band = im.crop((0, y, W, min(H, y + h))); im.paste(band, (r.randint(-60, 60), y))
    put(im, "CRASH", font("seguibl.ttf", 200), (255, 60, 80), 1, glow=(255, 0, 40), pos=(W // 2 + r.randint(-8, 8), H // 2 - 40))
    put(im, "java.util.zip.ZipException: invalid LOC header", MONO, (255, 200, 200), 0.8, pos=(W // 2, H // 2 + 90))
    return im

def a10(t, dur):  # version split
    im = bg(t, CYAN); d = ImageDraw.Draw(im)
    s = eout(t / 0.6); d.rectangle([W // 2 - 2, 120, W // 2 + 2, 120 + int(480 * s)], fill=(255, 255, 255, 120))
    for side, (ver, c) in enumerate([("26.1.2", CYAN), ("26.2", PURPLE)]):
        cx = W // 4 + side * W // 2
        put(im, ver, font("seguibl.ttf", 110), (255, 255, 255), eout((t - 0.3) / 0.5), glow=c, pos=(cx, 230))
        for i, f in enumerate(["Dungeons", "Solvers", "Map", "Farming", "Profile Viewer"]):
            a = eout((t - 1.0 - i * 0.25) / 0.3)
            put(im, "✓", font("seguisym.ttf", 34), GREEN, a, anchor="rm", pos=(cx - 100, 360 + i * 52))
            put(im, f, SML, GREEN, a, anchor="lm", pos=(cx - 85, 360 + i * 52))
    put(im, "every feature, both versions", TINY, (190, 200, 225), eout((t - 2.6) / 0.4), pos=(W // 2, 660))
    return im

def a11(t, dur):  # no macros shield
    im = bg(t, GREEN); d = ImageDraw.Draw(im, "RGBA"); a = eout(t / 0.5); cx, cy = W // 2, 290
    s = 1 + 0.03 * math.sin(t * 3)
    pts = [(cx, cy - 150 * s), (cx + 130 * s, cy - 100 * s), (cx + 115 * s, cy + 40 * s), (cx, cy + 150 * s), (cx - 115 * s, cy + 40 * s), (cx - 130 * s, cy - 100 * s)]
    d.polygon(pts, fill=(*GREEN, int(60 * a)), outline=(*GREEN, int(255 * a)), width=6)
    put(im, "✓", font("seguisym.ttf", 140), (255, 255, 255), a, glow=GREEN, pos=(cx, cy))
    put(im, "NO MACROS", TITLE, (255, 255, 255), eout((t - 0.4) / 0.4), glow=GREEN, pos=(W // 2, 530))
    put(im, "One key press. One action. Nothing plays the game for you.", SML, (190, 220, 200), eout((t - 0.9) / 0.4), pos=(W // 2, 620))
    return im

STATS = [(1682, "", "COMMITS"), (146199, "", "LINES OF CODE"), (1955, "", "SETTINGS"), (7, "", "MONTHS")]
def a12(t, dur):
    im = bg(t, CYAN)
    for i, (v, suf, lab) in enumerate(STATS):
        a = eout((t - i * 0.3) / 0.4); p = eout((t - i * 0.3) / 1.6)
        cx = 200 + i * 293
        rrect(im, (cx - 130, 230, cx + 130, 470), 24, (18, 22, 36), 0.9 * a, CYAN)
        put(im, f"{int(v * p):,}{suf}", font("seguibl.ttf", 54), (255, 255, 255), a, glow=CYAN, pos=(cx, 330))
        put(im, lab, font("segoeuib.ttf", 20), (170, 215, 255), a, pos=(cx, 410), spacing=3)
    return im

def a13(t, dur):
    im = bg(t, CYAN)
    put(im, "FISHMOD", BIG, (255, 255, 255), ease(t / 0.4), glow=CYAN, pos=(W // 2, 200), spacing=10)
    put(im, "Free on Modrinth  ·  fishmod.dev", MID, (255, 255, 255), ease((t - 0.6) / 0.4), glow=CYAN, pos=(W // 2, 330))
    put(im, "discord.gg/3mSuQUB8kk", SML, (130, 160, 255), ease((t - 1.2) / 0.4), pos=(W // 2, 420))
    put(im, "Open source  ·  No scripted input", TINY, (150, 160, 185), ease((t - 1.8) / 0.4), pos=(W // 2, 490))
    return im

SECTIONS = {"secrets": ("SECRETS", PINK), "solvers": ("SOLVERS", hexc("#ff7a59")), "floor7": ("FLOOR 7", hexc("#c77dff")), "map": ("DUNGEON MAP", PINK),
            "items": ("CUSTOM ITEMS", hexc("#00e5c3")), "farming": ("FARMING", GREEN), "pv": ("PROFILE VIEWER", CYAN),
            "ach": ("ACHIEVEMENTS", GOLD), "custom": ("YOURS TO CHANGE", PURPLE)}
def a08(key):
    title, c = SECTIONS[key]
    def f(t, dur):
        im = bg(t, c)
        put(im, title, TITLE, (255, 255, 255), 1, glow=c, pos=(W // 2, H // 2), spacing=int(40 - 30 * eout(t / 0.5)))
        d = ImageDraw.Draw(im); w = int(eout(t / 0.5) * 420)
        d.rectangle([W // 2 - w // 2, H // 2 + 70, W // 2 + w // 2, H // 2 + 76], fill=(*c, 255))
        return im
    return f

ANIMS = {"A01_code_counter": (a01, 4.0), "A02_title_fallback": (a02_fallback, 3.5), "A03_five_mods": (a03, 5.0), "A05_first_commit": (a05, 3.5),
         "A06_sessions": (a06, 4.5), "A07_credits": (a07, 5.0), "A09_crash": (a09, 1.5), "A10_versions": (a10, 4.0), "A11_no_macros": (a11, 3.5),
         "A12_stats": (a12, 4.0), "A13_outro": (a13, 6.0)}
for k in SECTIONS: ANIMS[f"A08_{k}"] = (a08(k), 1.2)

def render(name):
    f, dur = ANIMS[name]; os.makedirs(OUT, exist_ok=True)
    p = subprocess.Popen(["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
                          "-vf", "scale=1920:1080:flags=lanczos", "-c:v", "libx264", "-crf", "16", "-preset", "medium", "-pix_fmt", "yuv420p",
                          "-movflags", "+faststart", os.path.join(OUT, name + ".mp4")], stdin=subprocess.PIPE)
    for i in range(int(dur * FPS)):
        t = i / FPS; im = f(t, dur)
        if t > dur - 0.25: im.alpha_composite(Image.new("RGBA", (W, H), (0, 0, 0, int(255 * (t - (dur - 0.25)) / 0.25))))
        p.stdin.write(ImageEnhance.Brightness(im.convert("RGB")).enhance(0.85).tobytes())
    p.stdin.close(); p.wait()

if __name__ == "__main__":
    names = [a for a in sys.argv[1:] if not a.startswith("--")] or list(ANIMS)
    if "--still" in sys.argv:
        os.makedirs(OUT, exist_ok=True)
        for n in names:
            f, dur = ANIMS[n]; f(dur * 0.75, dur).convert("RGB").save(os.path.join(OUT, n + ".png"))
    else:
        for n in names: render(n); print("rendered", n, flush=True)
