import math, os, json, sys
OUT = sys.argv[1]
fn = os.path.join(OUT, "data", "fmtest", "function")
os.makedirs(fn, exist_ok=True)
os.makedirs(os.path.join(OUT, "data", "minecraft", "tags", "function"), exist_ok=True)
json.dump({"pack": {"description": "FishMod Diana test", "min_format": 101, "max_format": 101}}, open(os.path.join(OUT, "pack.mcmeta"), "w"))
json.dump({"values": ["fmtest:load"]}, open(os.path.join(OUT, "data/minecraft/tags/function/load.json"), "w"))
json.dump({"values": ["fmtest:tick"]}, open(os.path.join(OUT, "data/minecraft/tags/function/tick.json"), "w"))

def w(name, lines):
    open(os.path.join(fn, name + ".mcfunction"), "w", encoding="utf-8").write("\n".join(lines) + "\n")

def tell(text):
    return 'tellraw @a ' + json.dumps({"text": text}, ensure_ascii=False)

def marker(s): return tell("[fmtest] " + s)
def f(v): return f"{v:.6f}"

steps = []  # (delay_ticks, [commands])
def at(t, *cmds): steps.append((t, list(cmds)))

w("load", ["scoreboard objectives add fm dummy", "scoreboard players reset #started fm", "gamerule send_command_feedback false", "gamerule command_block_output false"])
w("tick", ["execute if entity @a unless score #started fm matches 1 run function fmtest:start"])
w("reset", ["scoreboard players reset #started fm"])

# ---- setup ----
setup = [
    "scoreboard players set #started fm 1",
    "time set noon", "weather clear",
    "fill -40 70 -40 40 70 40 minecraft:grass_block",
    "fill -40 71 -40 40 74 40 minecraft:air",
    "kill @e[type=armor_stand]", "kill @e[type=zombie]",
    "tp @a 0 71 0 0 20", "clear @a",
    'item replace entity @a weapon.mainhand with minecraft:golden_shovel[custom_data={id:"ANCESTRAL_SPADE"},custom_name="Ancestral Spade"]',
    marker("clear"), marker("enableall"),
]

T = 0
# 1: close burrow particles
def burrow_particles(kind, x, z):
    px, py, pz = x + 0.5, 71.2, z + 0.5
    if kind == "start": return f"particle minecraft:enchanted_hit {px} {py} {pz} 0.5 0.1 0.5 0.01 4 force"
    if kind == "mob": return f"particle minecraft:crit {px} {py} {pz} 0.5 0.1 0.5 0.01 3 force"
    return f"particle minecraft:dripping_lava {px} {py} {pz} 0.35 0.1 0.35 0.01 2 force"
for k in range(3):
    at(60 + k * 10, burrow_particles("start", 5, 5), burrow_particles("mob", -6, 8), burrow_particles("treasure", 10, -7))
at(100, tell("§7[test] burrows placed: start 5,70,5  mob -6,70,8  treasure 10,70,-7"), marker("dump"), "tp @a 2 73 -14 0 25", marker("shot 1_burrows"))

# 2: dig start burrow -> removed, then arrow toward (38,70,5)
at(140, marker("click 5 70 5"), tell("§eYou dug out a Griffin Burrow! §7(1/4)"))
base = (6.0, 73.5, 5.5); target = (30.5, 70.5, 5.5)
o = (base[0], base[1] - 1.5, base[2])
d = [target[i] - o[i] for i in range(3)]; L = math.sqrt(sum(c*c for c in d)); d = [c / L for c in d]
tip = [base[i] + d[i] * 1.9 for i in range(3)]
pts = [[base[i] + d[i] * 0.1 * k for i in range(3)] for k in range(20)]  # shaft base->tip
# perpendicular (horizontal) unit
perp = [-d[2], 0, d[0]]; pl = math.sqrt(sum(c*c for c in perp)) or 1; perp = [c / pl for c in perp]
up = [0, 1, 0]
c_tip = pts[18]; c_base = pts[1]
barbs = []
for s1 in (1, -1):
    barbs.append([c_tip[i] + perp[i] * 0.08 * s1 for i in range(3)])
    barbs.append([c_tip[i] + up[i] * 0.08 * s1 for i in range(3)])
for s1 in (1, -1):
    barbs.append([c_base[i] + perp[i] * 0.08 * s1 for i in range(3)])
dust = []
for p in pts + barbs:
    dust.append(f"particle minecraft:dust{{color:[1.0,1.0,0.0],scale:1.0}} {f(p[0])} {f(p[1])} {f(p[2])} 0 128 0 1 0 force")
at(150, *dust)
at(170, tell("§7[test] arrow shown, expect ARROW guess near 38,70,5"), marker("dump"), "tp @a 3 73 5 -90 15", marker("shot 2_arrow"))

# 3: spade guess, trail toward +z landing on the platform
def spade_final(start, dvec):
    dx, dy, dz = dvec
    ln = math.sqrt(dx*dx + dy*dy + dz*dz)
    pitch0 = -math.atan2(dy, math.sqrt(dx*dx + dz*dz))
    lo, hi = -math.pi/2, math.pi/2
    for _ in range(100):
        pitch = (lo + hi) / 2
        r = math.atan2(math.sin(pitch) - 0.75, math.cos(pitch))
        if r < pitch0: lo = pitch
        else: hi = pitch
    cpd = math.sqrt(24 * math.sin(pitch - math.pi) + 25)
    t = 3 * cpd / ln
    return [start[i] + dvec[i] * t for i in range(3)]
start = (-30.5, 72.6, -36.5)
best = None
for dyi in range(-400, 400):
    dy = dyi / 1000
    dv = (0.0, dy, 0.5)
    fin = spade_final(start, dv)
    if 70.9 <= fin[1] <= 71.1 and -38 < fin[2] < 38:
        best = (dv, fin); break
dv, fin = best
trail = [f"particle minecraft:dripping_lava {f(start[0] + dv[0]*k)} {f(start[1] + dv[1]*k)} {f(start[2] + dv[2]*k)} 0 0 0 0 2 force" for k in range(8)]
at(200, marker("spade"), *trail)
exp_spade = (math.floor(fin[0]), math.floor(fin[1] - 0.5), math.floor(fin[2]))
at(215, tell(f"§7[test] spade trail sent, expect GUESS at {exp_spade}"), marker("dump"), "tp @a -31 73 -44 0 15", marker("shot 3_spade"))

# 4: mob burrow -> inquisitor
at(240, marker("click -6 70 8"), tell("§c§lUh oh! §eYou dug out §2Minos Inquisitor§e!"))
at(245, 'summon armor_stand -5.5 72.5 8.5 {CustomName:"§8[§7Lv750§8] §2✿ §2Minos Inquisitor §a40M§f/§a40M§c❤",CustomNameVisible:1b,NoGravity:1b,Invisible:1b}',
        'summon zombie -5.5 71 8.5 {CustomName:"Minos Inquisitor",NoAI:1b,PersistenceRequired:1b,Silent:1b}')
at(250, tell("§eFollow the arrows to find the §6treasure§e!"), tell("§cThis ability is on cooldown for 2s."))
at(290, marker("dump"), "tp @a -6 73 -4 0 15", marker("shot 4_inquisitor"))
at(300, 'data merge entity @e[type=armor_stand,limit=1,sort=nearest] {CustomName:"§8[§7Lv750§8] §2✿ §2Minos Inquisitor §a0§f/§a40M§c❤"}')
at(310, tell("§c ☠ §7You were killed by §2Minos Inquisitor§7§7."), marker("dump"),
   'give @a minecraft:iron_sword[custom_data={id:"HILT_OF_REVELATIONS",uuid:"' + str(__import__("uuid").uuid4()) + '"},custom_name="Hilt of Revelations"]')
at(320, tell("§a§lCAUGHT! §7You cocooned a §2Minos Inquisitor§7!"))
at(325, 'summon armor_stand 20.5 72.5 20.5 {CustomName:"§8[§7Lv750§8] §2✿ §2Exalted Manticore §a30M§f/§a40M§c❤",CustomNameVisible:1b,NoGravity:1b,Invisible:1b}',
        'summon zombie 20.5 71 20.5 {CustomName:"Manticore",NoAI:1b,PersistenceRequired:1b,Silent:1b}',
        'summon armor_stand 22.5 72.5 22.5 {CustomName:"§8[§7Lv280§8] §2✿ §2Minos Champion §a2M§f/§a2.5M§c❤ §b✯",CustomNameVisible:1b,NoGravity:1b,Invisible:1b}')
at(330, "tp @a 14 73 12 45 15", marker("shot 4b_manticore_glow_hp_shuriken"))

# 5: treasure burrow dug twice
at(340, marker("click 10 70 -7"), tell("§6§lWow! §eYou dug out §650,000 coins§e!"))
at(350, tell("§eYou dug out a Griffin Burrow! §7(2/4)"), 'particle minecraft:large_smoke 10.5 71 -6.5 0 0 0 0.01 1 force')
at(360, marker("dump"))

# 6: party share receive
at(380, tell("§9Party §8> §b[MVP§c+§b] Tester§f: x: 150, y: 71, z: -150 | Minos Inquisitor"))
at(390, tell("§9Party §8> §a[VIP] Other§f: x: -20, y: 71, z: 20"))
at(400, marker("dump"), "tp @a 120 73 -120 135 10", marker("shot 5_party"))

# 7: sphinx
q = "Which of these is NOT a pet?"
at(430, tell("§e[NPC] §bSphinx§f: " + q))
def ans(letter, text):
    return 'tellraw @a ' + json.dumps({"text": f"   {letter}) ", "color": "gray", "extra": [{"text": text, "color": "white"}], "click_event": {"action": "run_command", "command": f"/say picked {letter}"}})
at(432, ans("A", "Slime"), ans("B", "Bat"), ans("C", "Rabbit"))

# 8: tracker chat (drops)
from_tracker = os.environ.get("TRACKER_LINES", "")
drops = [
    "§6§lRARE DROP! §eYou dug out a §9Griffin Feather§e!",
    "§6§lRARE DROP! §5Enchanted Book §7(§d§lChimera I§7) §b(+§b320% §b✯ Magic Find§b)",
    "§6§lRARE DROP! §6Daedalus Stick §b(+§b250% §b✯ Magic Find§b)",
    "§e§lLOOT SHARE §fYou received loot for assisting §bTester§f!",
    "§6§lRARE DROP! §5Enchanted Book §7(§d§lChimera I§7) §b(+§b300% §b✯ Magic Find§b)",
]
for i, dmsg in enumerate(drops):
    at(470 + i * 5, tell(dmsg))
extra = [l for l in from_tracker.split("||") if l]
for i, l in enumerate(extra):
    at(500 + i * 5, tell(l))

# 9: party commands
for i, c in enumerate(["!inq", "!chim", "!since chim", "!burrow", "!mob", "!stick", "!mf", "!profit"]):
    at(560 + i * 4, tell("§9Party §8> §b[MVP§c+§b] Tester§f: " + c))
at(440, marker("shot 6_sphinx"))
# 8: tracker chat (drops)
from_tracker = os.environ.get("TRACKER_LINES", "")
drops = [
    "§6§lRARE DROP! §eYou dug out a §9Griffin Feather§e!",
    "§6§lRARE DROP! §5Enchanted Book §7(§d§lChimera I§7) §b(+§b320% §b✯ Magic Find§b)",
    "§6§lRARE DROP! §6Daedalus Stick §b(+§b250% §b✯ Magic Find§b)",
    "§e§lLOOT SHARE §fYou received loot for assisting §bTester§f!",
    "§6§lRARE DROP! §5Enchanted Book §7(§d§lChimera I§7) §b(+§b300% §b✯ Magic Find§b)",
]
for i, dmsg in enumerate(drops):
    at(470 + i * 5, tell(dmsg))
extra = [l for l in from_tracker.split("||") if l]
for i, l in enumerate(extra):
    at(500 + i * 5, tell(l))

# 9: party commands
for i, c in enumerate(["!inq", "!chim", "!since chim", "!burrow", "!mob", "!stick", "!mf", "!profit"]):
    at(560 + i * 4, tell("§9Party §8> §b[MVP§c+§b] Tester§f: " + c))
at(440, marker("shot 6_sphinx"))
# warp title + hint: burrow near museum warp, player far away
at(450, burrow_particles("start", 30, 0), "tp @a 170 90 -200 -35 5")
at(460, burrow_particles("start", 30, 0), marker("dump"), marker("shot 8_warp_title"))
at(470, "tp @a 0 72 0 0 10")
at(480, marker("click 30 70 0"), tell("§eYou dug out a Griffin Burrow! §7(3/4)"), tell("§eYou finished the Griffin burrow chain! §7(4/4)"))
at(485, marker("shot 9_chain_end"))
at(600, marker("dump"), "tp @a 0 72 0 0 10", marker("shot 7_huds"))
at(620, marker("pastevents"))
at(630, marker("shot 10_past_events"))
at(640, marker("closescreen"))
at(700, marker("clear"), "kill @e[type=armor_stand]", "kill @e[type=zombie]", burrow_particles("start", 30, 0), "tp @a 170 90 -200 -35 5")
at(710, burrow_particles("start", 30, 0))
at(725, marker("dump"), marker("warp"), marker("shot 8_warp_title"))
at(760, "tp @a 0 72 0 90 10", marker("click 30 70 0"), tell("§eYou dug out a Griffin Burrow! §7(3/4)"), tell("§eYou finished the Griffin burrow chain! §7(4/4)"))
at(765, marker("shot 9_chain_end"))
at(800, marker("openmenu"))
at(820, marker("shot 11_menu"))
at(830, marker("closescreen"))
at(860, marker("clear"), "tp @a -20 74 -30 0 20", marker("subguess -20 70 -10 -20 70 25 -20 70 38"))
at(870, marker("dump"), marker("shot 12_subguesses"))
at(880, "tp @a -20 71 -14 0 20")
at(930, marker("dump"), marker("shot 13_subguess_advanced"))
at(960, tell("§a[test] scenario done"))

# schedule chain
w("start", setup + [f"schedule function fmtest:s{i} {t + 200}t append" for i, (t, _) in enumerate(steps)])
for i, (t, cmds) in enumerate(steps):
    w(f"s{i}", cmds)
print("spade expect", exp_spade, "dv", dv, "fin", fin)
