package fishmod.features;

import com.mojang.blaze3d.platform.InputConstants;
import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.rendering.RenderUtils;
import fishmod.utils.rendering.RenderingEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Location ping — press the ping key (default middle mouse) to drop a through-walls waypoint where
 * you're looking, like a MOBA ping. The marker (a glowing column + floating "⚑ name • dist") fades
 * out after a few seconds.
 *
 * Two reach levels:
 *   • Local — always on; your own ping renders for you, and (optionally) the coords go to party chat.
 *   • Shared — opt-in. Your ping is published to the worker and other FishMod users on your server
 *     (your tab list, same scope as the cosmetic /sync) see it in their world, labelled with your
 *     name. Their pings show up for you the same way. Needs the /ping worker route deployed
 *     (worker-pings-snippet.js); it silently no-ops until then.
 */
public final class PingFeature {

    private PingFeature() {}

    private static final double REACH = 160.0;   // how far the ping ray travels before landing in air
    private static final int POLL_TICKS = 40;    // ~2s between shared-ping polls

    /** One ping marker — your own or a remote user's. */
    private static final class Ping {
        final Vec3 pos; final long startMs; final String name; final long srcTs;
        Ping(Vec3 pos, long startMs, String name, long srcTs) {
            this.pos = pos; this.startMs = startMs; this.name = name; this.srcTs = srcTs;
        }
    }

    private static KeyMapping pingKey;
    private static Ping self = null;
    private static Ping chatPing = null;         // latest coords parsed out of chat
    private static final Map<String, Ping> remote = new ConcurrentHashMap<>(); // uuid → their ping
    private static int pollTick = 0;
    private static long lastSeenTs = 0;          // newest source ts we've pulled, for the `since` filter

    // "x: 12 y: 34 z: -56", "x12 y34 z-56", "x=12, y=34, z=-56" — labelled so it won't grab random numbers.
    private static final java.util.regex.Pattern COORD_PAT = java.util.regex.Pattern.compile(
            "(?i)x[:=]?\\s*(-?\\d{1,6})[ ,]+y[:=]?\\s*(-?\\d{1,4})[ ,]+z[:=]?\\s*(-?\\d{1,6})");
    private static final java.util.regex.Pattern NAME_PAT = java.util.regex.Pattern.compile("([A-Za-z0-9_]{2,16}):");

    public static void init() {
        // Reuse the shared FishMod keybind category (created in Keybinds.init, which runs first) so we
        // don't double-register the category Identifier.
        KeyMapping.Category category = fishmod.utils.Keybinds.category;
        if (category == null) category = KeyMapping.Category.register(Identifier.parse(fishmod.utils.Constants.NAMESPACE));
        pingKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "FishMod: Ping location",
                InputConstants.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
                category));

        ClientTickEvents.END_CLIENT_TICK.register(PingFeature::onTick);
        RenderingEvents.NO_DEPTH_FILLED.register((ctx, matrices, vc) -> render(ctx, matrices, vc));
        fishmod.utils.events.Events.ON_GAME_MESSAGE.register(text -> {
            if (FishSettings.pingEnabled && FishSettings.pingFromChat && text != null)
                parseChatCoords(text.getString().replaceAll("§.", ""));
            return false;
        });
    }

    /** Detect "x: N y: N z: N" in a chat line and drop a waypoint there, labelled with the speaker. */
    private static void parseChatCoords(String plain) {
        if (plain == null || plain.isEmpty()) return;
        java.util.regex.Matcher m = COORD_PAT.matcher(plain);
        if (!m.find()) return;
        int x, y, z;
        try {
            x = Integer.parseInt(m.group(1));
            y = Integer.parseInt(m.group(2));
            z = Integer.parseInt(m.group(3));
        } catch (NumberFormatException e) { return; }
        if (y < -64 || y > 320) return; // implausible Y → almost certainly not a location
        // Best-effort speaker name: the last "Name:" token before the message body.
        String label = "ping";
        java.util.regex.Matcher nm = NAME_PAT.matcher(plain.substring(0, m.start()));
        while (nm.find()) label = nm.group(1);
        chatPing = new Ping(new Vec3(x + 0.5, y, z + 0.5), System.currentTimeMillis(), label, 0);
    }

    private static void onTick(Minecraft mc) {
        if (pingKey == null) return;

        boolean fired = false;
        while (pingKey.consumeClick()) fired = true; // drain queued presses
        if (fired && FishSettings.pingEnabled && mc.player != null && mc.level != null
                && mc.screen == null && Location.inSkyblock()) {
            placePing(mc);
        }

        // Poll for other users' shared pings.
        if (FishSettings.pingEnabled && FishSettings.pingShareEnabled) {
            if (++pollTick >= POLL_TICKS) { pollTick = 0; pollRemote(mc); }
        } else if (!remote.isEmpty()) {
            remote.clear();
        }
    }

    private static void placePing(Minecraft mc) {
        LocalPlayer p = mc.player;
        float delta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 eye = p.getEyePosition(delta);
        Vec3 look = p.getViewVector(delta);
        Vec3 end = eye.add(look.scale(REACH));

        BlockHitResult hit = mc.level.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));

        Vec3 target;
        if (hit != null && hit.getType() != HitResult.Type.MISS) {
            BlockPos bp = hit.getBlockPos();
            target = new Vec3(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
        } else {
            target = end; // landed in air — ping the point you're aiming at
        }

        self = new Ping(target, System.currentTimeMillis(), null, 0);

        if (FishSettings.pingSound) p.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.7f, 1.6f);

        if (FishSettings.pingAnnounceParty && mc.getConnection() != null) {
            mc.getConnection().sendCommand("pc x: " + (int) Math.floor(target.x)
                    + ", y: " + (int) Math.floor(target.y) + ", z: " + (int) Math.floor(target.z) + " (ping)");
        }

        if (FishSettings.pingShareEnabled) {
            String uuid = p.getUUID().toString().replace("-", "");
            String dim = mc.level.dimension().identifier().toString();
            fishmod.utils.HypixelApi.uploadPing(uuid, p.getGameProfile().name(), target.x, target.y, target.z, dim);
        }
    }

    private static void pollRemote(Minecraft mc) {
        if (mc.getConnection() == null || mc.player == null) return;
        String selfUuid = mc.player.getUUID().toString().replace("-", "");
        Set<String> uuids = new HashSet<>();
        for (var entry : mc.getConnection().getOnlinePlayers()) {
            var gp = entry.getProfile();
            if (gp == null || gp.id() == null) continue;
            String u = gp.id().toString().replace("-", "");
            if (!u.equals(selfUuid)) uuids.add(u);
        }
        if (uuids.isEmpty()) return;

        fishmod.utils.HypixelApi.fetchPings(uuids, lastSeenTs, list -> mc.execute(() -> {
            long now = System.currentTimeMillis();
            for (var pd : list) {
                Ping existing = remote.get(pd.uuid());
                if (existing != null && existing.srcTs == pd.ts()) continue; // same ping, keep its local clock
                remote.put(pd.uuid(), new Ping(new Vec3(pd.x(), pd.y(), pd.z()), now, pd.name(), pd.ts()));
                if (pd.ts() > lastSeenTs) lastSeenTs = pd.ts();
            }
        }));
    }

    private static double remainingFraction(Ping ping) {
        long dur = Math.max(1, FishSettings.pingDurationSeconds) * 1000L;
        long elapsed = System.currentTimeMillis() - ping.startMs;
        return elapsed >= dur ? 0 : 1.0 - (double) elapsed / dur;
    }

    private static void render(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext ctx,
                              com.mojang.blaze3d.vertex.PoseStack matrices,
                              com.mojang.blaze3d.vertex.VertexConsumer vc) {
        if (!FishSettings.pingEnabled) return;

        if (self != null && remainingFraction(self) <= 0) self = null;
        if (self != null) drawPing(ctx, matrices, vc, self);

        if (chatPing != null && remainingFraction(chatPing) <= 0) chatPing = null;
        if (chatPing != null) drawPing(ctx, matrices, vc, chatPing);

        if (!remote.isEmpty()) {
            remote.entrySet().removeIf(e -> remainingFraction(e.getValue()) <= 0);
            for (Ping ping : remote.values()) drawPing(ctx, matrices, vc, ping);
        }
    }

    private static void drawPing(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext ctx,
                                 com.mojang.blaze3d.vertex.PoseStack matrices,
                                 com.mojang.blaze3d.vertex.VertexConsumer vc, Ping ping) {
        double frac = remainingFraction(ping);
        if (frac <= 0) return;

        int base = FishSettings.pingColor;
        float r = (base >> 16 & 0xFF) / 255f;
        float g = (base >> 8  & 0xFF) / 255f;
        float b = (base       & 0xFF) / 255f;
        float a = (float) (Math.min(1.0, frac) * 0.55);   // fade out
        float[] rgba = { r, g, b, a };

        double x = ping.pos.x, y = ping.pos.y, z = ping.pos.z;
        // Base cube on the block + a tall thin beam so it's spottable from across the room.
        RenderUtils.renderFilled(matrices, vc, new AABB(x - 0.5, y, z - 0.5, x + 0.5, y + 1, z + 0.5), rgba);
        RenderUtils.renderFilled(matrices, vc, new AABB(x - 0.15, y, z - 0.15, x + 0.15, y + 6, z + 0.15), rgba);

        LocalPlayer me = Minecraft.getInstance().player;
        if (me != null) {
            int dist = (int) Math.round(Math.sqrt(me.distanceToSqr(ping.pos)));
            String who = ping.name == null || ping.name.isBlank() ? "Ping" : ping.name;
            Component label = Component.literal("§b⚑ §f" + who + " §7• §e" + dist + "m");
            RenderUtils.renderText(ctx, matrices, label, x, y + 6.4, z, 1.1f);
        }
    }
}
