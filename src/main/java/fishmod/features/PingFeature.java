package fishmod.features;

import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.rendering.RenderUtils;
import fishmod.utils.rendering.RenderingEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
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
        final Vec3d pos; final long startMs; final String name; final long srcTs;
        Ping(Vec3d pos, long startMs, String name, long srcTs) {
            this.pos = pos; this.startMs = startMs; this.name = name; this.srcTs = srcTs;
        }
    }

    private static KeyBinding pingKey;
    private static Ping self = null;
    private static final Map<String, Ping> remote = new ConcurrentHashMap<>(); // uuid → their ping
    private static int pollTick = 0;
    private static long lastSeenTs = 0;          // newest source ts we've pulled, for the `since` filter

    public static void init() {
        // Reuse the shared FishMod keybind category (created in Keybinds.init, which runs first) so we
        // don't double-register the category Identifier.
        KeyBinding.Category category = fishmod.utils.Keybinds.category;
        if (category == null) category = KeyBinding.Category.create(Identifier.of(fishmod.utils.Constants.NAMESPACE));
        pingKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "FishMod: Ping location",
                InputUtil.Type.MOUSE,
                GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
                category));

        ClientTickEvents.END_CLIENT_TICK.register(PingFeature::onTick);
        RenderingEvents.NO_DEPTH_FILLED.register((ctx, matrices, vc) -> render(ctx, matrices, vc));
    }

    private static void onTick(MinecraftClient mc) {
        if (pingKey == null) return;

        boolean fired = false;
        while (pingKey.wasPressed()) fired = true; // drain queued presses
        if (fired && FishSettings.pingEnabled && mc.player != null && mc.world != null
                && mc.currentScreen == null && Location.inSkyblock()) {
            placePing(mc);
        }

        // Poll for other users' shared pings.
        if (FishSettings.pingEnabled && FishSettings.pingShareEnabled) {
            if (++pollTick >= POLL_TICKS) { pollTick = 0; pollRemote(mc); }
        } else if (!remote.isEmpty()) {
            remote.clear();
        }
    }

    private static void placePing(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        float delta = mc.getRenderTickCounter().getTickProgress(false);
        Vec3d eye = p.getCameraPosVec(delta);
        Vec3d look = p.getRotationVec(delta);
        Vec3d end = eye.add(look.multiply(REACH));

        BlockHitResult hit = mc.world.raycast(new RaycastContext(
                eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, p));

        Vec3d target;
        if (hit != null && hit.getType() != HitResult.Type.MISS) {
            BlockPos bp = hit.getBlockPos();
            target = new Vec3d(bp.getX() + 0.5, bp.getY(), bp.getZ() + 0.5);
        } else {
            target = end; // landed in air — ping the point you're aiming at
        }

        self = new Ping(target, System.currentTimeMillis(), null, 0);

        if (FishSettings.pingSound) p.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 0.7f, 1.6f);

        if (FishSettings.pingAnnounceParty && mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendChatCommand("pc x: " + (int) Math.floor(target.x)
                    + ", y: " + (int) Math.floor(target.y) + ", z: " + (int) Math.floor(target.z) + " (ping)");
        }

        if (FishSettings.pingShareEnabled) {
            String uuid = p.getUuid().toString().replace("-", "");
            String dim = mc.world.getRegistryKey().getValue().toString();
            fishmod.utils.HypixelApi.uploadPing(uuid, p.getGameProfile().name(), target.x, target.y, target.z, dim);
        }
    }

    private static void pollRemote(MinecraftClient mc) {
        if (mc.getNetworkHandler() == null || mc.player == null) return;
        String selfUuid = mc.player.getUuid().toString().replace("-", "");
        Set<String> uuids = new HashSet<>();
        for (var entry : mc.getNetworkHandler().getPlayerList()) {
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
                remote.put(pd.uuid(), new Ping(new Vec3d(pd.x(), pd.y(), pd.z()), now, pd.name(), pd.ts()));
                if (pd.ts() > lastSeenTs) lastSeenTs = pd.ts();
            }
        }));
    }

    private static double remainingFraction(Ping ping) {
        long dur = Math.max(1, FishSettings.pingDurationSeconds) * 1000L;
        long elapsed = System.currentTimeMillis() - ping.startMs;
        return elapsed >= dur ? 0 : 1.0 - (double) elapsed / dur;
    }

    private static void render(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext ctx,
                              net.minecraft.client.util.math.MatrixStack matrices,
                              net.minecraft.client.render.VertexConsumer vc) {
        if (!FishSettings.pingEnabled) return;

        if (self != null && remainingFraction(self) <= 0) self = null;
        if (self != null) drawPing(ctx, matrices, vc, self);

        if (!remote.isEmpty()) {
            remote.entrySet().removeIf(e -> remainingFraction(e.getValue()) <= 0);
            for (Ping ping : remote.values()) drawPing(ctx, matrices, vc, ping);
        }
    }

    private static void drawPing(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext ctx,
                                 net.minecraft.client.util.math.MatrixStack matrices,
                                 net.minecraft.client.render.VertexConsumer vc, Ping ping) {
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
        RenderUtils.renderFilled(matrices, vc, new Box(x - 0.5, y, z - 0.5, x + 0.5, y + 1, z + 0.5), rgba);
        RenderUtils.renderFilled(matrices, vc, new Box(x - 0.15, y, z - 0.15, x + 0.15, y + 6, z + 0.15), rgba);

        ClientPlayerEntity me = MinecraftClient.getInstance().player;
        if (me != null) {
            int dist = (int) Math.round(Math.sqrt(me.squaredDistanceTo(ping.pos)));
            String who = ping.name == null || ping.name.isBlank() ? "Ping" : ping.name;
            Text label = Text.literal("§b⚑ §f" + who + " §7• §e" + dist + "m");
            RenderUtils.renderText(ctx, matrices, label, x, y + 6.4, z, 1.1f);
        }
    }
}
