package fishmod.features.challenges;

import fishmod.utils.Misc;
import fishmod.utils.config.values.FishSettings;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.EnumMap;
import java.util.Map;

/**
 * Orchestrates challenges: ticks active-time (with AFK pause), polls Hypixel periodically
 * for completion detection, accepts new challenges via GapAnalyzer, awards points,
 * submits scores to the worker.
 *
 * AFK rule: if neither player position nor camera angle changes for {@link FishSettings#challengeAfkMinutes}
 * minutes, activeMs stops accumulating until the next movement.
 */
public class ChallengeManager {

    private static final long POLL_INTERVAL_MS = 5L * 60 * 1000; // 5 minutes
    private static long lastPollMs = 0;

    // AFK tracking
    private static double lastX, lastY, lastZ;
    private static float  lastYaw, lastPitch;
    private static boolean havePos = false;
    private static long lastActivityMs = 0;
    private static boolean afkPaused = false;

    public static void init() {
        ChallengeProgress.get(); // load
        ClientTickEvents.END_CLIENT_TICK.register(ChallengeManager::tick);

        // Detect dungeon run completions immediately — don't wait for the 5-minute
        // Hypixel poll. Bumps DUNGEON_RUNS challenges locally when their floor matches.
        fishmod.utils.events.Events.ON_RUN_END.register(() -> {
            String floor = fishmod.utils.dungeon.Phase.getFloor();
            if (floor == null || floor.isEmpty()) return false;
            ChallengeProgress p = ChallengeProgress.get();
            boolean any = false;
            for (Map.Entry<Tier, Challenge> e : p.active.entrySet()) {
                Challenge c = e.getValue();
                if (c == null || c.isComplete()) continue;
                if (c.template != ChallengeTemplate.DUNGEON_RUNS) continue;
                if (!floor.equalsIgnoreCase(c.paramStr)) continue;
                c.current = Math.min(c.target, c.current + 1);
                any = true;
                if (c.current >= c.target) { complete(c); /* removed below */ }
            }
            // Remove any newly-completed challenges from active map
            p.active.entrySet().removeIf(en -> en.getValue() != null && en.getValue().isComplete());
            if (any) p.save();
            // Trigger an early poll too so Hypixel-side stats (cata XP, etc.) catch up.
            lastPollMs = 0;
            return false;
        });
    }

    private static void tick(Minecraft mc) {
        if (!FishSettings.challengesEnabled) return;
        if (mc.player == null) return;

        long now = System.currentTimeMillis();
        ChallengeProgress p = ChallengeProgress.get();

        // Detect movement/rotation
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        float yaw = mc.player.getYRot(), pitch = mc.player.getXRot();
        if (!havePos) {
            lastX = x; lastY = y; lastZ = z; lastYaw = yaw; lastPitch = pitch;
            havePos = true; lastActivityMs = now;
        } else {
            double posDelta = Math.abs(x - lastX) + Math.abs(y - lastY) + Math.abs(z - lastZ);
            double rotDelta = Math.abs(yaw - lastYaw) + Math.abs(pitch - lastPitch);
            if (posDelta > 0.02 || rotDelta > 0.5) {
                lastX = x; lastY = y; lastZ = z; lastYaw = yaw; lastPitch = pitch;
                lastActivityMs = now;
                if (afkPaused) afkPaused = false;
            }
        }
        long afkMs = Math.max(60_000L, FishSettings.challengeAfkMinutes * 60_000L);
        if (!afkPaused && now - lastActivityMs >= afkMs) afkPaused = true;

        // Accumulate activeMs on every challenge unless AFK paused.
        // Snapshot to a new list — we mutate p.active inside the loop on expiry.
        java.util.List<Map.Entry<Tier, Challenge>> snapshot = new java.util.ArrayList<>(p.active.entrySet());
        for (Map.Entry<Tier, Challenge> e : snapshot) {
            Challenge c = e.getValue();
            if (c == null || c.isComplete()) continue;
            if (!afkPaused) {
                long delta = Math.min(1500, now - c.lastTickMs);
                if (delta > 0) c.activeMs += delta;
            }
            c.lastTickMs = now;
            if (c.isExpired()) {
                // expired without completion — drop into history with 0 points
                c.completedAtMs = now;
                c.awardedPoints = 0;
                p.history.add(c);
                p.active.remove(e.getKey());
                Misc.addChatMessage(Component.literal("§c[Challenge] " + c.tier.label + " expired: " + c.describe()));
            }
        }

        // Periodic Hypixel poll for completion detection
        if (!p.active.isEmpty() && now - lastPollMs > POLL_INTERVAL_MS) {
            lastPollMs = now;
            poll();
        }
    }

    private static void poll() {
        ChallengeApi.fetchLocal(snap -> {
            if (snap == null) return;
            Minecraft.getInstance().execute(() -> applySnapshot(snap));
        });
    }

    private static void applySnapshot(ProfileSnapshot snap) {
        ChallengeProgress p = ChallengeProgress.get();
        boolean changed = false;
        for (Tier t : Tier.values()) {
            Challenge c = p.active.get(t);
            if (c == null || c.isComplete()) continue;
            double cur = readCurrent(c, snap);
            c.current = cur;
            if (cur >= c.target) {
                complete(c);
                p.active.remove(t);
                changed = true;
            }
        }
        if (changed) p.save();
    }

    private static double readCurrent(Challenge c, ProfileSnapshot s) {
        return switch (c.template) {
            case SKILL_XP_GAIN    -> s.skillXp.getOrDefault(c.paramStr, (long) c.baseline);
            case SLAYER_XP_GAIN   -> s.slayerXp.getOrDefault(c.paramStr, (long) c.baseline);
            case PET_LEVEL_UP     -> s.petsAt100;
            case SB_MISSION       -> {
                for (SkyblockMissions.Mission m : SkyblockMissions.ALL)
                    if (m.id.equals(c.paramStr)) yield SkyblockMissions.currentValue(m, s);
                yield c.baseline;
            }
            case DUNGEON_RUNS     -> s.floorCompletions.getOrDefault(c.paramStr, (long) c.baseline);
            case CATA_LEVEL       -> fishmod.utils.HypixelApi.calcCataLevel(s.cataXp);
            case COLLECTION_TIER  -> s.collections.getOrDefault(c.paramStr, (long) c.baseline);
        };
    }

    static void complete(Challenge c) {
        long now = System.currentTimeMillis();
        c.completedAtMs = now;
        c.awardedPoints = computePoints(c);
        ChallengeProgress p = ChallengeProgress.get();
        p.totalPoints += c.awardedPoints;
        p.history.add(c);
        p.save();
        Misc.addChatMessage(Component.literal("§a[Challenge] §lCOMPLETE §r" + c.tier.color + c.tier.label
                + " §7— §e+" + c.awardedPoints + " §7points (total §a" + p.totalPoints + "§7)"));
        Misc.addChatMessage(Component.literal("§7→ " + c.describe()));

        // Submit to leaderboard if enabled
        if (FishSettings.challengeLeaderboardEnabled) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                String uuid = mc.player.getUUID().toString().replace("-", "");
                String name = ChallengeApi.displayName();
                ChallengeApi.submitScore(uuid, name, c.id, c.tier, c.awardedPoints, c.activeMs,
                        (ok, total, rank) -> {});
            }
        }
    }

    /** points = base × clamp(2 - activeMs/durationMs, 1, 2). */
    public static int computePoints(Challenge c) {
        double frac = (double) c.activeMs / c.tier.durationMs;
        double mult = Math.max(1.0, Math.min(2.0, 2.0 - frac));
        return (int) Math.round(c.tier.basePoints * mult);
    }

    // ── User-facing operations ──────────────────────────────────────────────

    public static void acceptNew(Tier tier, Runnable onDone) {
        ChallengeApi.fetchLocal(snap -> {
            Minecraft.getInstance().execute(() -> {
                if (snap == null) {
                    Misc.addChatMessage(Component.literal("§c[Challenge] Could not fetch profile — §7"
                            + (ChallengeApi.lastFetchError.isEmpty() ? "unknown" : ChallengeApi.lastFetchError)));
                    if (onDone != null) onDone.run();
                    return;
                }
                Challenge c = GapAnalyzer.pickFor(tier, snap);
                if (c == null) {
                    Misc.addChatMessage(Component.literal("§c[Challenge] No suitable challenge found."));
                    if (onDone != null) onDone.run();
                    return;
                }
                ChallengeProgress p = ChallengeProgress.get();
                p.active.put(tier, c);
                p.save();
                Misc.addChatMessage(Component.literal("§a[Challenge] New " + tier.color + tier.label + " §7challenge: §f" + c.describe()));
                if (onDone != null) onDone.run();
            });
        });
    }

    public static boolean reroll(Tier tier) {
        ChallengeProgress p = ChallengeProgress.get();
        if (!p.canReroll()) {
            Misc.addChatMessage(Component.literal("§c[Challenge] Reroll already used this month."));
            return false;
        }
        p.active.remove(tier);
        p.noteReroll();
        acceptNew(tier, null);
        return true;
    }

    public static void abandon(Tier tier) {
        ChallengeProgress p = ChallengeProgress.get();
        Challenge c = p.active.remove(tier);
        if (c != null) {
            c.completedAtMs = System.currentTimeMillis();
            c.awardedPoints = 0;
            p.history.add(c);
            p.save();
            Misc.addChatMessage(Component.literal("§7[Challenge] Abandoned " + tier.label + "."));
        }
    }

    public static boolean isAfkPaused() { return afkPaused; }
}
