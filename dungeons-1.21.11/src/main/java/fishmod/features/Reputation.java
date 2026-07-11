package fishmod.features;

import fishmod.utils.HypixelApi;
import fishmod.utils.Misc;
import fishmod.utils.config.values.FishSettings;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Crowd-sourced player reputation — a community "vouch / shitter list". Any player can be tagged by
 * UUID (they don't need the mod): {@code /vouch} for a good carry, {@code /shitter} to flag a
 * ditcher/scammer, {@code /unrep} to clear your tag. {@code /rep <player>} shows the aggregate, and
 * {@code /rep} with no name scans your current lobby and lists anyone who's been flagged.
 *
 * One vote per voter per target (re-voting overwrites), so counts can't be stuffed. Backed by the
 * worker /rep route (worker-reputation-snippet.js); commands report cleanly if it isn't deployed.
 */
public final class Reputation {

    private Reputation() {}

    // Lowercased IGNs of flagged (net-negative) players currently in the lobby, refreshed by a poll.
    private static final Set<String> flaggedIgns = ConcurrentHashMap.newKeySet();
    private static final int POLL_TICKS = 20 * 30; // ~30s between flag refreshes
    private static int pollTick = 0;

    /** Registers the background poll that keeps the in-lobby flagged set fresh (for the tab ✗ marker). */
    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (!FishSettings.repFlagsEnabled) { if (!flaggedIgns.isEmpty()) flaggedIgns.clear(); return; }
            if (++pollTick < POLL_TICKS) return;
            pollTick = 0;
            pollFlags(mc);
        });
    }

    private static void pollFlags(MinecraftClient mc) {
        if (mc.getNetworkHandler() == null) return;
        Map<String, String> uuidToName = new HashMap<>();
        for (var e : mc.getNetworkHandler().getPlayerList()) {
            var gp = e.getProfile();
            if (gp == null || gp.id() == null || gp.name() == null || gp.name().isBlank()) continue;
            uuidToName.put(gp.id().toString().replace("-", ""), gp.name());
        }
        if (uuidToName.isEmpty()) return;
        HypixelApi.fetchReps(uuidToName.keySet(), reps -> mc.execute(() -> {
            Set<String> next = new HashSet<>();
            for (var entry : reps.entrySet()) {
                HypixelApi.RepData rd = entry.getValue();
                if (rd.down() > rd.up()) {
                    String name = uuidToName.get(entry.getKey());
                    if (name != null) next.add(name.toLowerCase());
                }
            }
            flaggedIgns.clear();
            flaggedIgns.addAll(next);
        }));
    }

    /**
     * Appends a red ✘ to any flagged player's name found in an on-screen text (tab list / scoreboard).
     * Cheap: the flagged set is usually empty and only ever holds players in your current lobby.
     */
    public static Text decorateTab(Text text) {
        if (!FishSettings.repFlagsEnabled || text == null || flaggedIgns.isEmpty()) return text;
        String s = text.getString();
        if (s.isEmpty() || s.endsWith("✘")) return text;
        String lower = s.toLowerCase();
        for (String name : flaggedIgns) {
            if (lower.contains(name)) {
                MutableText out = text.copy();
                out.append(Text.literal(" §c✘"));
                return out;
            }
        }
        return text;
    }

    /** Resolve an IGN to a dashless UUID (cache first, then Mojang), then run {@code cb} (null on miss). */
    private static void withUuid(String name, Consumer<String> cb) {
        String cached = HypixelApi.getCachedUuid(name);
        if (cached != null) { cb.accept(cached.replace("-", "")); return; }
        HypixelApi.resolveUuidAsync(name, uuid -> cb.accept(uuid == null ? null : uuid.replace("-", "")));
    }

    /** Cast (or clear) the local player's vote on {@code name}. dir ∈ "up" | "down" | "none". */
    public static void vote(String name, String dir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        String voter = mc.player.getUuid().toString().replace("-", "");
        Misc.addChatMessage(Text.literal("§7[Rep] resolving §f" + name + "§7…"));
        withUuid(name, uuid -> {
            if (uuid == null) { mc.execute(() -> Misc.addChatMessage(Text.literal("§c[Rep] couldn't find player §f" + name))); return; }
            HypixelApi.voteRep(voter, uuid, name, dir, (up, down) -> mc.execute(() -> {
                if (up < 0) { Misc.addChatMessage(Text.literal("§c[Rep] vote failed — worker route not reachable.")); return; }
                String v = dir.equals("up") ? "§avouched" : dir.equals("down") ? "§cflagged" : "§7cleared vote on";
                Misc.addChatMessage(Text.literal("§7[Rep] " + v + " §f" + name + " §8— " + counts(up, down)));
            }));
        });
    }

    /** Print the aggregate reputation for a single player. */
    public static void lookup(String name) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Misc.addChatMessage(Text.literal("§7[Rep] looking up §f" + name + "§7…"));
        withUuid(name, uuid -> {
            if (uuid == null) { mc.execute(() -> Misc.addChatMessage(Text.literal("§c[Rep] couldn't find player §f" + name))); return; }
            HypixelApi.fetchReps(Set.of(uuid), reps -> mc.execute(() -> {
                HypixelApi.RepData rd = reps.get(uuid);
                if (rd == null || (rd.up() == 0 && rd.down() == 0)) {
                    Misc.addChatMessage(Text.literal("§7[Rep] §f" + name + " §7has no reputation yet."));
                } else {
                    Misc.addChatMessage(Text.literal("§7[Rep] §f" + name + " §8— " + counts(rd.up(), rd.down()) + " " + verdict(rd)));
                }
            }));
        });
    }

    /** Scan the current lobby's tab list and list any flagged (net-negative) players. */
    public static void listNearby() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) { Misc.addChatMessage(Text.literal("§c[Rep] Not in a world.")); return; }
        Map<String, String> uuidToName = new HashMap<>();
        for (var e : mc.getNetworkHandler().getPlayerList()) {
            var gp = e.getProfile();
            if (gp == null || gp.id() == null || gp.name() == null || gp.name().isBlank()) continue;
            uuidToName.put(gp.id().toString().replace("-", ""), gp.name());
        }
        if (uuidToName.isEmpty()) { Misc.addChatMessage(Text.literal("§7[Rep] No players found in tab.")); return; }
        Misc.addChatMessage(Text.literal("§7[Rep] scanning §f" + uuidToName.size() + " §7players…"));
        HypixelApi.fetchReps(uuidToName.keySet(), reps -> mc.execute(() -> {
            List<String> flagged = new ArrayList<>();
            for (var entry : reps.entrySet()) {
                HypixelApi.RepData rd = entry.getValue();
                if (rd.down() > rd.up()) {
                    String who = uuidToName.getOrDefault(entry.getKey(), rd.name());
                    flagged.add("§f" + who + " §8(" + counts(rd.up(), rd.down()) + "§8)" + (isShitter(rd) ? " §4§lSHITTER" : ""));
                }
            }
            if (flagged.isEmpty()) { Misc.addChatMessage(Text.literal("§a[Rep] No flagged players in this lobby.")); return; }
            Misc.addChatMessage(Text.literal("§c[Rep] §l" + flagged.size() + " flagged player(s) here:"));
            for (String line : flagged) Misc.addChatMessage(Text.literal("  " + line));
        }));
    }

    private static String counts(int up, int down) { return "§a+" + up + " §c-" + down; }

    private static boolean isShitter(HypixelApi.RepData rd) {
        return rd.down() >= 3 && rd.down() > rd.up() * 2;
    }

    private static String verdict(HypixelApi.RepData rd) {
        if (isShitter(rd)) return "§4§lSHITTER";
        if (rd.up() - rd.down() >= 3) return "§2§ltrusted";
        return "";
    }
}
