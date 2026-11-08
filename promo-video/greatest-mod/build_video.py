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
    ("0_01", "", [(C("C01_settings_menu"), 0, "")]),
    ("0_02a", "", [(C("C03_terminal_solver"), 1, "")]),
    ("0_02b", "", [(C("C17_dungeon_map"), 2, "")]),
    ("0_02c", "", [(C("C20_profile_viewer"), 2, "")]),
    ("0_02d", "", [(C("C21_achievements_menu"), 3, "")]),
    ("0_02e", "", [(C("C07_item_customization"), 2, "")]),
    ("0_02f", "", [(C("C19_garden_visitors"), 12, "")]),
    ("0_02g", "", [(C("B10_necron_splits_full"), 3, "")]),
    ("0_03", "", [(A("A01_code_counter"), 0, "anim")]),
    ("0_04", "", [(A("A02_title_3d"), 0, "anim")]),
    ("1_01", "", [(RUN, 45, "fpszoom")]),
    ("1_02", "", [(A("A03_five_mods"), 0, "anim")]),
    ("1_03", "", [(C("C01_settings_menu"), 3, "")]),
    ("2_01", "", [(C("B05_warp_crypt_wide"), 0, "")]),
    ("2_02", "", [(C("C10_modrinth_page"), 0, "")]),
    ("2_03", "", [(C("C23_storm_death"), 20, "")]),
    ("3_01", "", [(C("C12_claude_sessions"), 0, "")]),
    ("3_02", "", [(A("A06_sessions"), 0, "anim")]),
    ("3_03", "", [(C("C22_hud_editor"), 0, "")]),
    ("3_04", "", [(C("B01_castle_towers_guess"), 0, "")]),
    ("3_05", "", [(A("A07_credits"), 0, "anim")]),
    (None, "", [(A("A08_secrets"), 0, "anim")]),
    ("4_01", "", [(C("C16b_secrets_done"), 0, "")]),
    (None, "", [(A("A08_solvers"), 0, "anim")]),
    ("4_02a", "", [(C("C03_terminal_solver"), 0, "")]),
    ("4_02b", "", [(C("C16_puzzle_tictactoe"), 3, "")]),
    (None, "", [(A("A08_map"), 0, "anim")]),
    ("4_03", "", [(C("C17_dungeon_map"), 0, "")]),
    (None, "", [(A("A08_items"), 0, "anim")]),
    ("4_04", "", [(C("C07_item_customization"), 0, "")]),
    (None, "", [(A("A08_farming"), 0, "anim")]),
    ("4_05a", "", [(C("C19_garden_visitors"), 10, "")]),
    ("4_05b", "", [(C("C19b_garden_pests"), 0, "")]),
    (None, "", [(A("A08_pv"), 0, "anim")]),
    ("4_06", "", [(C("C20_profile_viewer"), 0, "")]),
    (None, "", [(A("A08_ach"), 0, "anim")]),
    ("4_07", "", [(C("C21_achievements_menu"), 0, "")]),
    (None, "", [(A("A08_custom"), 0, "anim")]),
    ("4_08", "", [(C("C22_hud_editor"), 0, "")]),
    ("5_01a", "", [(C("C23_storm_death"), 0, "")]),
    ("5_01b", "", [(A("A09_crash"), 0, "anim")]),
    ("5_02", "", [(A("A10_versions"), 0, "anim")]),
    ("5_03", "", [(A("A11_no_macros"), 0, "anim")]),
    ("6_01", "", [(A("A12_stats"), 0, "anim")]),
    ("6_02", "", [(C("C26_100k_secrets"), 0, "")]),
    ("6_03", "", [(C("C24_roadmap"), 0, "")]),
    ("6_04", "", [(C("B07_pink_sky_islands"), 0, "")]),
    ("7_01", "", [(A("A13_outro"), 0, "anim")]),
    ("7_02", "", [(A("A13_outro"), 2, "anim")]),
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
    return plan

def seg(i, src, start, dur, fx):
    out = os.path.join(TMP, f"s{i:04d}.mp4")
    vf = [f"scale={WH}:force_original_aspect_ratio=increase,crop={WH}", f"fps={FPS}"]
    if fx == "grey": vf.insert(0, "hue=s=0.25,setpts=1.4*PTS")
    if fx == "fpszoom": vf.insert(0, "hue=s=0.3,crop=iw/2.6:ih/2.6:iw-iw/2.6:ih-ih/2.6")  # FPS counter sits bottom-right
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
