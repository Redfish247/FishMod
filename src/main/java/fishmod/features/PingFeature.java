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

/**
 * Location ping — press the ping key (default middle mouse) to drop a through-walls waypoint where
 * you're looking, like a MOBA ping. The marker (a glowing column + floating "⚑ name • dist") fades
 * out after a few seconds; an optional toggle also drops the coordinates into party chat so the rest
 * of the group sees them even without the mod.
 *
 * This is the local half of the feature. Sharing the live in-world marker between FishMod users runs
 * through the worker and is layered on top of this without changing the rendering path.
 */
public final class PingFeature {

    private PingFeature() {}

    private static final double REACH = 160.0;   // how far the ping ray travels before landing in air

    private static KeyBinding pingKey;

    // A single active self-ping. (Remote pings, when sharing lands, render through the same draw path.)
    private static Vec3d pingPos = null;
    private static long  pingStartMs = 0;

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
        if (!fired) return;
        if (!FishSettings.pingEnabled) return;
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return;        // don't ping while a menu is open
        if (!Location.inSkyblock()) return;          // SkyBlock-only, like the rest of the mod
        placePing(mc);
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

        pingPos = target;
        pingStartMs = System.currentTimeMillis();

        if (FishSettings.pingSound) p.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.6f);

        if (FishSettings.pingAnnounceParty && mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendChatCommand("pc x: " + (int) Math.floor(target.x)
                    + ", y: " + (int) Math.floor(target.y) + ", z: " + (int) Math.floor(target.z) + " (ping)");
        }
    }

    private static double remainingFraction() {
        long dur = Math.max(1, FishSettings.pingDurationSeconds) * 1000L;
        long elapsed = System.currentTimeMillis() - pingStartMs;
        return elapsed >= dur ? 0 : 1.0 - (double) elapsed / dur;
    }

    private static void render(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext ctx,
                              net.minecraft.client.util.math.MatrixStack matrices,
                              net.minecraft.client.render.VertexConsumer vc) {
        if (!FishSettings.pingEnabled || pingPos == null) return;
        double frac = remainingFraction();
        if (frac <= 0) { pingPos = null; return; }

        int base = FishSettings.pingColor;
        float r = (base >> 16 & 0xFF) / 255f;
        float g = (base >> 8  & 0xFF) / 255f;
        float b = (base       & 0xFF) / 255f;
        float a = (float) (Math.min(1.0, frac) * 0.55);   // fade out
        float[] rgba = { r, g, b, a };

        double x = pingPos.x, y = pingPos.y, z = pingPos.z;
        // Base cube on the block + a tall thin beam so it's spottable from across the room.
        RenderUtils.renderFilled(matrices, vc, new Box(x - 0.5, y, z - 0.5, x + 0.5, y + 1, z + 0.5), rgba);
        RenderUtils.renderFilled(matrices, vc, new Box(x - 0.15, y, z - 0.15, x + 0.15, y + 6, z + 0.15), rgba);

        // Floating label above the beam with the live distance.
        ClientPlayerEntity self = MinecraftClient.getInstance().player;
        if (self != null) {
            int dist = (int) Math.round(self.getPos().distanceTo(pingPos));
            Text label = Text.literal("§b⚑ §fPing §7• §e" + dist + "m");
            RenderUtils.renderText(ctx, matrices, label, x, y + 6.4, z, 1.1f);
        }
    }
}
