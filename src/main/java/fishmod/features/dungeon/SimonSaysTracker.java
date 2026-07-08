package fishmod.features.dungeon;

import fishmod.features.FishHudEditor;
import fishmod.utils.Misc;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.dungeon.Phase;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks Goldor (F7 P3) Simon Says rounds via block scanning.
 *
 * Detection is PLAYER-CENTERED (not hardcoded coordinates): we find the obsidian-backed device
 * near the player, lock the scan onto its centroid so walking around can't miscount stray
 * lanterns, and count demo "flashes" (lit sea-lantern rising edges).
 *
 * Announcing: on the FIRST light of each new demo, the rounds-completed count = the longest
 * sequence shown so far. A failed round re-shows the same/shorter sequence, so the max length
 * doesn't grow and (with the dedupe) nothing extra is sent. 5/5 comes from the in-game
 * "<you> completed a device!" message / completion title.
 */
public final class SimonSaysTracker {

    private SimonSaysTracker() {}

    private static final int SCAN_RADIUS  = 7;   // lit-cell box around the locked device center
    private static final int PROX_RADIUS  = 6;   // obsidian search radius around the player
    private static final int MIN_OBSIDIAN = 8;   // obsidian count that marks "at the device"
    private static final long BURST_GAP_MS = 550L;

    private static int  round         = 0;   // completed-round count shown on the HUD (0..5)
    private static int  maxLen        = 0;   // longest demo sequence length seen this run
    private static int  lastAnnounced = 0;   // highest count already sent (dedupe)
    private static int  burstFlashes  = 0;   // rising-edge flashes in the current demo
    private static long lastFlashMs   = 0L;
    private static long lastAtDeviceMs = 0L; // last tick the player was at the device
    private static boolean primed     = false;
    private static boolean completed  = false; // SS done this run — ignore everything until next run
    private static boolean inP3       = false; // HUD only
    private static boolean atDevice   = false;
    private static net.minecraft.core.BlockPos deviceCenter = null;
    private static long doneAtMs = 0L; // when 5/5 fired — HUD unrenders 2s later
    private static final java.util.HashSet<Long> litPrev = new java.util.HashSet<>();

    // Reads "Simon Says: N/5" out of party chat so the HUD also registers when SOMEONE ELSE
    // does SS (we can't block-scan their device — but their mod announces to party chat).
    private static final Pattern SS_CHAT = Pattern.compile("Simon Says: (\\d)/5");

    // ── debug: log every block transition in a cube around the player (/ssdbg) ──
    public static boolean debug = false;
    private static final int DBG_R = 6;
    private static final java.util.HashMap<Long, net.minecraft.world.level.block.Block> dbgPrev = new java.util.HashMap<>();

    public static void init() {
        // New run (entering/leaving the dungeon) → reset everything.
        fishmod.utils.events.Events.ON_LOCATION_CHANGE.register(loc -> { reset(); return false; });

        // "<you> completed a device! (x/7) (time | time)" → our SS finish (5/5). Use the mod's own
        // game-message event (the same hook party commands use) for reliability.
        fishmod.utils.events.Events.ON_GAME_MESSAGE.register(message -> {
            if (!FishSettings.simonSaysEnabled) return false;
            String s = message.getString().replaceAll("§.", "");

            // Pick up "Simon Says: N/5" from party chat (ours echoed back, or a teammate's) so the
            // HUD shows progress even when WE aren't the one at the device.
            Matcher ss = SS_CHAT.matcher(s);
            if (ss.find()) {
                int n = ss.group(1).charAt(0) - '0';
                if (n >= 1 && n <= 5) {
                    round = n;
                    if (n >= 5) doneAtMs = System.currentTimeMillis();
                }
            }

            if (debug && s.contains("device")) log("msg: \"" + s + "\"");
            if (!s.contains("completed a device")) return false;
            // Must be OUR completion (teammates' device completions also broadcast). Match the name
            // loosely (anywhere in the line) so color-code spacing can't break it.
            Minecraft mc = Minecraft.getInstance();
            String self = (mc.player != null) ? mc.player.getGameProfile().name() : null;
            boolean mine = (self == null) || s.contains(self);
            if (debug) log("completed-a-device match; self=" + self + " mine=" + mine + " completed=" + completed);
            if (mine) tryComplete();
            return false;
        });

        ClientTickEvents.END_CLIENT_TICK.register(SimonSaysTracker::debugTick);
        ClientTickEvents.END_CLIENT_TICK.register(SimonSaysTracker::tick);
        HudRenderCallback.EVENT.register(SimonSaysTracker::renderHud);

        FishHudEditor.register("Simon Says",
                () -> FishSettings.simonSaysHudX, v -> FishSettings.simonSaysHudX = v,
                () -> FishSettings.simonSaysHudY, v -> FishSettings.simonSaysHudY = v,
                110, 14,
                () -> FishSettings.simonSaysHudScale, v -> FishSettings.simonSaysHudScale = v,
                () -> FishSettings.simonSaysHudEnabled && round > 0);
    }

    private static void tick(Minecraft client) {
        if (!FishSettings.simonSaysEnabled || client.player == null || client.level == null) {
            inP3 = false; atDevice = false; deviceCenter = null; reset(); return;
        }
        inP3 = safeInP3();

        // Once SS is done this run, ignore everything until the next run (location change).
        if (completed) { atDevice = false; return; }

        long now = System.currentTimeMillis();

        // Player at an obsidian-backed device? Also get the obsidian centroid to lock the scan.
        long[] agg = new long[4]; // count, sumX, sumY, sumZ
        int nearObsidian = countObsidian(client.level, client.player.blockPosition(), PROX_RADIUS, agg);
        atDevice = nearObsidian >= MIN_OBSIDIAN;
        if (atDevice) lastAtDeviceMs = now;

        if (!atDevice) {
            deviceCenter = null; primed = false; burstFlashes = 0; litPrev.clear();
            return;
        }

        if (deviceCenter == null && agg[0] > 0) {
            deviceCenter = new BlockPos((int) (agg[1] / agg[0]), (int) (agg[2] / agg[0]), (int) (agg[3] / agg[0]));
            primed = false; burstFlashes = 0; litPrev.clear();
            if (debug) log("locked device center " + deviceCenter.toShortString());
        }

        java.util.HashSet<Long> cur = new java.util.HashSet<>();
        scanLitCells(client.level, deviceCenter, cur);

        // Prime on the first scan so the always-lit decorative frame lanterns aren't miscounted.
        if (!primed) { litPrev.clear(); litPrev.addAll(cur); primed = true; return; }

        int newlyLit = 0;
        for (long p : cur) if (!litPrev.contains(p)) newlyLit++;
        litPrev.clear();
        litPrev.addAll(cur);

        if (newlyLit > 0) {
            // A gap before this light = a NEW demo just started. Announce here, on the FIRST light.
            if (now - lastFlashMs > BURST_GAP_MS) {
                if (burstFlashes > maxLen) maxLen = burstFlashes; // finalize previous demo
                burstFlashes = 0;
                int done = Math.min(5, maxLen);
                if (done >= 1 && done <= 4 && done > lastAnnounced) {
                    lastAnnounced = done;
                    round = done;
                    announceRound(done);
                }
            }
            burstFlashes += newlyLit;
            lastFlashMs = now;
            if (debug) log("flash +" + newlyLit + " burst=" + burstFlashes + " maxLen=" + maxLen);
        }
    }

    /**
     * 5/5 finish, triggered by the in-game "<you> completed a device!" message (the reliable
     * signal — block detection of rounds can miss). Fires once per run; the run reset clears it.
     */
    private static void tryComplete() {
        if (debug) log("tryComplete called (completed=" + completed + ")");
        if (completed) return;
        round = 5;
        lastAnnounced = 5;
        doneAtMs = System.currentTimeMillis();
        announceRound(5);
        completed = true;
    }

    /** Center-screen title hook (titles are local-only, so a loose match is safe). */
    public static void onTitle(String title) {
        if (!FishSettings.simonSaysEnabled || title == null) return;
        String s = title.replaceAll("§.", "").toLowerCase();
        if (s.contains("device") && s.contains("complete")) tryComplete();
    }

    private static void announceRound(int r) {
        String label = r + "/5" + (r >= 5 ? " (done)" : "");
        Misc.addChatMessage(Component.literal(fishmod.utils.FishMsg.prefix() + "§bSimon Says: §a" + label));
        if (FishSettings.simonSaysPartyChat) Misc.executeCommand("pc Simon Says: " + label);
    }

    // ── block scanning ──────────────────────────────────────────────────────────

    /** Counts obsidian within {@code radius} of center; accumulates centroid into agg[count,x,y,z]. */
    private static int countObsidian(Level world, BlockPos center, int radius, long[] agg) {
        int obsidian = 0;
        int cx = center.getX(), cy = center.getY(), cz = center.getZ();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++)
            for (int dy = -radius; dy <= radius; dy++)
                for (int dz = -radius; dz <= radius; dz++) {
                    m.set(cx + dx, cy + dy, cz + dz);
                    if (world.getBlockState(m).getBlock() == Blocks.OBSIDIAN) {
                        obsidian++;
                        agg[0]++; agg[1] += m.getX(); agg[2] += m.getY(); agg[3] += m.getZ();
                    }
                }
        return obsidian;
    }

    /** Fills {@code litOut} with lit sea-lantern positions in the box around the locked device center. */
    private static void scanLitCells(Level world, BlockPos center, java.util.HashSet<Long> litOut) {
        int cx = center.getX(), cy = center.getY(), cz = center.getZ();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++)
            for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++)
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    m.set(cx + dx, cy + dy, cz + dz);
                    if (world.getBlockState(m).getBlock() == Blocks.SEA_LANTERN) litOut.add(m.asLong());
                }
    }

    /** Phase.inP3() but never throws — blade-addons' Phase may differ; treat errors as false. */
    private static boolean safeInP3() {
        try { return Phase.inP3(); } catch (Throwable t) { return false; }
    }

    private static void reset() {
        round = 0;
        maxLen = 0;
        lastAnnounced = 0;
        burstFlashes = 0;
        primed = false;
        completed = false;
        doneAtMs = 0L;
        litPrev.clear();
        deviceCenter = null;
    }

    public static int getStage() { return round; }

    public static void renderHud(GuiGraphics ctx, DeltaTracker tc) {
        if (!FishSettings.simonSaysEnabled || !FishSettings.simonSaysHudEnabled) return;
        if (round <= 0) return;
        // Auto-hide 2 seconds after completion.
        if (round >= 5 && doneAtMs > 0 && System.currentTimeMillis() - doneAtMs > 2000) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // Don't render when standing right at the device (~3 blocks) — you can see it yourself.
        if (deviceCenter != null && mc.player.blockPosition().distSqr(deviceCenter) <= 12) return;

        String label = round == 0
                ? "§bSimon Says: §7—"
                : "§bSimon Says: §a" + round + "§7/5" + (round >= 5 ? " §7(done)" : "");

        float sc = (float) FishSettings.simonSaysHudScale;
        ctx.pose().pushMatrix();
        ctx.pose().translate((float) FishSettings.simonSaysHudX, (float) FishSettings.simonSaysHudY);
        ctx.pose().scale(sc, sc);
        ctx.drawString(mc.font, label, 0, 0, -1, true);
        ctx.pose().popMatrix();
    }

    private static void log(String line) {
        fishmod.utils.debug.Debug.LOGGER.info("[SS] {}", line);
        Misc.addChatMessage(Component.literal("§e[SS] " + line));
    }

    /** /ssdbg: logs every block transition in a cube around the player — for locating the device. */
    private static void debugTick(Minecraft client) {
        if (!debug || client.player == null || client.level == null) { dbgPrev.clear(); return; }
        Level world = client.level;
        BlockPos c = client.player.blockPosition();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        boolean first = dbgPrev.isEmpty();
        for (int dx = -DBG_R; dx <= DBG_R; dx++)
            for (int dy = -DBG_R; dy <= DBG_R; dy++)
                for (int dz = -DBG_R; dz <= DBG_R; dz++) {
                    m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    net.minecraft.world.level.block.Block b = world.getBlockState(m).getBlock();
                    long key = m.asLong();
                    net.minecraft.world.level.block.Block old = dbgPrev.put(key, b);
                    if (first || old == b) continue;
                    String oldId = old == null ? "none" : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(old).getPath();
                    String newId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath();
                    log("(" + dx + "," + dy + "," + dz + ") " + oldId + " -> " + newId);
                }
    }
}
