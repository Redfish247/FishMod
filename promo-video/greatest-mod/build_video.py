"""Assemble "How I created Hypixel SkyBlock's greatest mod" from VO + clips + anims.
VO lookup per line key (e.g. 0_01): vo/<key>.wav, else a section file vo/<sec>_*.wav split across that section's lines by word count.
Missing VO -> duration estimated from words (placeholder timing). Usage: python build_video.py [--preview]"""
import glob, json, os, re, subprocess, sys, wave
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
C = lambda n: os.path.join(HERE, "clips", n + ".mp4")
A = lambda n: os.path.join(HERE, "anims", n + ".mp4")
TMP = os.path.join(HERE, "_build"); OUT = os.path.join(HERE, "how_i_created_fishmod.mp4")
RUN = r"C:\Users\Eli\Videos\2026-11-07 18-42-05.mp4"
PREVIEW = "--preview" in sys.argv
FPS = 30; WH = "1280:720" if PREVIEW else "1920:1080"

# (key, vo text, [(file, start_s, fx)]) ; fx: "" | "grey" | "fpszoom" | "anim"; a None key = no VO (card only)
L = [
    ("0_01", "Seven months ago, this mod didn't exist.", [(C("C01_settings_menu"), 0, "")]),
    ("0_02", "Today it has terminal solvers, a live dungeon map, a full profile viewer, achievements, cosmetics", [(C(c), s, "") for c, s in
        (("C03_terminal_solver", 1), ("C17_dungeon_map", 2), ("C20_profile_viewer", 2), ("C21_achievements_menu", 3), ("C07_item_customization", 2), ("C19_garden_visitors", 12), ("C22_hud_editor", 2))]),
    ("0_03", "and over a hundred and forty thousand lines of code.", [(A("A01_code_counter"), 0, "anim")]),
    ("0_04", "This is how I built it.", [(A("A02_title_3d"), 0, "anim")]),
    ("1_01", "I was grinding dungeons every day, and with five mods stacked on top of each other, my FPS was tanking.", [(RUN, 45, "fpszoom"), (RUN, 100, "grey")]),
    ("1_02", "I was running five different mods, each with its own menu, its own config, its own bugs.", [(A("A03_five_mods"), 0, "anim")]),
    ("1_03", "So I thought, what if one mod just did all of it?", [(C("C01_settings_menu"), 3, "")]),
    ("2_01", "The first commit was on April 9th. Party Finder stats, a few dungeon features and splits.", [(A("A05_first_commit"), 0, "anim")]),
    ("2_02", "What the first version looked like, how many people used it.", [(C("C10_modrinth_page"), 0, "")]),
    ("2_03", "It was rough.", [(C("C23_storm_death"), 20, "")]),
    ("3_01", "Here's the part people don't expect. I build most of this with AI.", [(C("C12_claude_sessions"), 0, "")]),
    ("3_02", "Not one prompt and done. Dozens of sessions, each working on one feature, all at once.", [(A("A06_sessions"), 0, "anim")]),
    ("3_03", "I test everything in game, report what's broken, and it goes back for another pass.", [(C("C22_hud_editor"), 0, ""), (C("C24_roadmap"), 2, "")]),
    ("3_04", "Your take: what AI is good at, what it's bad at, what you still do yourself.", [(C("C12_claude_sessions"), 8, ""), (C("C16_puzzle_tictactoe"), 2, "")]),
    ("3_05", "And a lot of features stand on the shoulders of other mods, Odin, NoammAddons, SkyHanni, BetterPV, SkyblockOverhaul. Credit where it's due.", [(A("A07_credits"), 0, "anim")]),
    (None, "", [(A("A08_secrets"), 0, "anim")]),
    ("4_01", "Secret tracking that tells you the second a room is done.", [(C("C16b_secrets_done"), 0, "")]),
    (None, "", [(A("A08_solvers"), 0, "anim")]),
    ("4_02", "Every terminal and puzzle, solved on screen.", [(C("C03_terminal_solver"), 0, ""), (C("C16_puzzle_tictactoe"), 3, "")]),
    (None, "", [(A("A08_map"), 0, "anim")]),
    ("4_03", "A full live map with room colours and player heads.", [(C("C17_dungeon_map"), 0, "")]),
    (None, "", [(A("A08_items"), 0, "anim")]),
    ("4_04", "Recolour, rename and remodel your items, and see other FishMod users' items too.", [(C("C07_item_customization"), 0, "")]),
    (None, "", [(A("A08_farming"), 0, "anim")]),
    ("4_05", "Visitors and pests tracked, Jacob's contest on your HUD.", [(C("C19_garden_visitors"), 0, ""), (C("C19b_garden_pests"), 0, "")]),
    (None, "", [(A("A08_pv"), 0, "anim")]),
    ("4_06", "Look up anyone's profile without leaving the game.", [(C("C20_profile_viewer"), 0, ""), (C("C20b_profile_viewer_pages"), 0, "")]),
    (None, "", [(A("A08_ach"), 0, "anim")]),
    ("4_07", "A full achievement system with leaderboards.", [(C("C21_achievements_menu"), 0, ""), (C("C21b_achievement_leaderboard_hub"), 0, "")]),
    (None, "", [(A("A08_custom"), 0, "anim")]),
    ("4_08", "And every single HUD, colour and text is yours to change.", [(C("C22_hud_editor"), 0, "")]),
    ("5_01", "Worst bug you hit. Crashes, a feature that broke on a Hypixel update, a version port.", [(C("C23_storm_death"), 0, ""), (A("A09_crash"), 0, "anim")]),
    ("5_02", "Minecraft updated twice while I was building this. Every feature has to work on both versions.", [(A("A10_versions"), 0, "anim")]),
    ("5_03", "And it all has to be safe. No scripted input. One key press, one action.", [(A("A11_no_macros"), 0, "anim")]),
    ("6_01", "Downloads, Discord members, testers number.", [(A("A12_stats"), 0, "anim")]),
    ("6_02", "Personal milestone line, e.g. 100k secrets.", [(C("C26_100k_secrets"), 0, "")]),
    ("6_03", "New features land every week, and you can see what's coming on the public roadmap.", [(C("C24_roadmap"), 0, "")]),
    ("6_04", "Thank the testers and community.", [(C("C12_claude_sessions"), 4, ""), (C("C01_settings_menu"), 0, "")]),
    ("7_01", "FishMod is free and open source. Link's in the description.", [(A("A13_outro"), 0, "anim")]),
    ("7_02", "Sign-off line.", [(A("A13_outro"), 2, "anim")]),
]

def wav_len(p):
    with wave.open(p) as w: return w.getnframes() / w.getframerate()

def probe_len(p):
    return float(subprocess.check_output(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", p]).decode().strip() or 0)

def vo_plan():
    """key -> (audio file, offset, length) or None."""
    plan = {}
    for key, text, _ in L:
        if not key: continue
        f = os.path.join(HERE, "vo", key + ".wav")
        if os.path.exists(f): plan[key] = (f, 0.0, wav_len(f))
    for sec in sorted({k.split("_")[0] for k, _, _ in L if k}):
        files = sorted(glob.glob(os.path.join(HERE, "vo", f"{sec}_*.wav")))
        files = [f for f in files if not re.search(rf"[\\/]{sec}_\d+\.wav$", f)]
        if not files: continue
        keys = [(k, t) for k, t, _ in L if k and k.startswith(sec + "_") and k not in plan]
        tot = wav_len(files[0]); words = [max(1, len(t.split())) for _, t in keys]; off = 0.0
        for (k, t), w in zip(keys, words):
            ln = tot * w / sum(words); plan[k] = (files[0], off, ln); off += ln
    return plan

def seg(i, src, start, dur, fx):
    out = os.path.join(TMP, f"s{i:04d}.mp4")
    vf = [f"scale={WH}:force_original_aspect_ratio=increase,crop={WH}", f"fps={FPS}"]
    if fx == "grey": vf.insert(0, "hue=s=0.25,setpts=1.4*PTS")
    if fx == "fpszoom": vf.insert(0, "hue=s=0.3,crop=iw/2.2:ih/2.2:iw-iw/2.2:0")  # FPS counter sits top-right
    src_len = probe_len(src)
    if fx == "anim": vf = [f"trim=end={max(0.3, src_len - 0.3):.3f},setpts=PTS-STARTPTS"] + vf + ["tpad=stop_mode=clone:stop_duration=30"]  # hold before the fade-out
    if fx != "anim" and start + dur > src_len: start = max(0, src_len - dur)
    args = ["ffmpeg", "-v", "error", "-y"] + (["-stream_loop", "-1"] if fx != "anim" and src_len < dur else []) + \
           ["-ss", str(start), "-i", src, "-t", f"{dur:.3f}", "-an", "-vf", ",".join(vf),
            "-c:v", "libx264", "-preset", "veryfast" if PREVIEW else "medium", "-crf", "18", "-pix_fmt", "yuv420p", out]
    subprocess.run(args, check=True); return out

def music(total, path):  # soft bed, reused synth from the trailer
    sys.path.insert(0, os.path.join(HERE, "..")); import make_trailer_v2 as m
    m.TOTAL = total; m.INTRO = 0.0; m.make_audio(path)

def main():
    os.makedirs(TMP, exist_ok=True)
    plan = vo_plan(); missing = [k for k, _, _ in L if k and k not in plan]
    print(f"VO found for {len(plan)} lines, estimated timing for {len(missing)}", flush=True)
    segs, timeline, t = [], [], 0.0
    for key, text, vis in L:
        if key is None: dur = 1.2
        elif key in plan: dur = plan[key][2] + 0.45
        else: dur = max(1.6, len(text.split()) / 2.6 + 0.45)
        if key in plan: timeline.append((t + 0.15, plan[key]))
        n = len(vis); part = dur / n
        for j, (src, st, fx) in enumerate(vis):
            segs.append(seg(len(segs), src, st, part, fx))
        t += dur
    lst = os.path.join(TMP, "list.txt")
    with open(lst, "w") as f:
        for s in segs: f.write(f"file '{s}'\n")
    video = os.path.join(TMP, "video.mp4")
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", video], check=True)
    total = probe_len(video); sr = 44100
    mus = os.path.join(TMP, "music.wav"); music(total, mus)
    with wave.open(mus) as w: bed = np.frombuffer(w.readframes(w.getnframes()), "<i2").reshape(-1, 2).astype(np.float32) / 32767
    mix = np.zeros((int(total * sr) + sr, 2), np.float32); mix[:len(bed)] += bed[:len(mix)] * 0.35
    duck = np.ones(len(mix), np.float32)
    for start, (f, off, ln) in timeline:
        r = subprocess.run(["ffmpeg", "-v", "error", "-ss", str(off), "-t", str(ln), "-i", f, "-f", "f32le", "-ac", "2", "-ar", str(sr), "-"], capture_output=True, check=True)
        v = np.frombuffer(r.stdout, np.float32).reshape(-1, 2); a = int(start * sr)
        v = v[:max(0, len(mix) - a)]; mix[a:a + len(v)] += v; duck[a:a + len(v)] = 0.45
    k = sr // 4; duck = np.convolve(duck, np.ones(k) / k, "same")  # smooth ducking
    mix[:, 0] -= (1 - duck) * mix[:, 0] * 0  # VO stays full; bed ducks below
    bedonly = np.zeros_like(mix); bedonly[:len(bed)] = bed[:len(mix)] * 0.35
    mix = mix - bedonly + bedonly * duck[:, None]
    mix = np.clip(mix, -1, 1); aud = os.path.join(TMP, "mix.wav")
    with wave.open(aud, "wb") as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(sr); w.writeframes((mix * 32767).astype("<i2").tobytes())
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", video, "-i", aud, "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-shortest", "-movflags", "+faststart", OUT], check=True)
    json.dump({"missing_vo": missing, "length_s": total}, open(os.path.join(HERE, "build_report.json"), "w"), indent=1)
    print("done", OUT, f"{total:.1f}s", "missing VO:", missing)

if __name__ == "__main__": main()
