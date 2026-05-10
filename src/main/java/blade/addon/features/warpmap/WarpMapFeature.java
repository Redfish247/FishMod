package blade.addon.features.warpmap;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * Warp-map overlay for the Hypixel SkyBlock Hub map wall.
 *
 * Dots are projected from world-space onto the HUD using the
 * INTERPOLATED camera position and rotation (tick-delta lerp).
 *
 * Map wall corners:
 *   Bottom-left: ( 6.6, 69, −5.3)  → u=0, v=1
 *   Top-right:   (−5.7, 76, −5.3)  → u=1, v=0
 */
public final class WarpMapFeature {

    private WarpMapFeature() {}

    // ── Map wall geometry ─────────────────────────────────────────────────────
    private static final double MAP_Z     = -5.3;
    private static final double MAP_X_U0  =  6.6;
    private static final double MAP_X_U1  = -5.7;
    private static final double MAP_Y_TOP = 76.0;
    private static final double MAP_Y_BOT = 69.0;

    private static double uvToWorldX(double u) { return MAP_X_U0 + u * (MAP_X_U1 - MAP_X_U0); }
    private static double uvToWorldY(double v)  { return MAP_Y_TOP - v * (MAP_Y_TOP - MAP_Y_BOT); }

    // ── Activation bounding box ───────────────────────────────────────────────
    private static final double BOX_X0 = -8.0,  BOX_X1 = 10.0;
    private static final double BOX_Y0 = 66.0,  BOX_Y1 = 79.0;
    private static final double BOX_Z0 = -18.0, BOX_Z1 = -5.5;

    // ── Warp points ───────────────────────────────────────────────────────────
    public record WarpPoint(String name, float u, float v) {}

    /**
     * Warps calibrated to the PHYSICAL HUB MAP WALL — used by the HUD overlay.
     */
    public static final WarpPoint[] WALL_WARPS = {
        new WarpPoint("skull",   0.656f, 0.230f),
        new WarpPoint("smold",   0.700f, 0.270f),
        new WarpPoint("drag",    0.182f, 0.425f),
        new WarpPoint("void",    0.316f, 0.410f),
        new WarpPoint("end",     0.312f, 0.498f),
        new WarpPoint("murk",    0.087f, 0.498f),
        new WarpPoint("galatea", 0.179f, 0.539f),
        new WarpPoint("jungle",  0.273f, 0.544f),
        new WarpPoint("isle",    0.455f, 0.480f),
        new WarpPoint("nest",    0.460f, 0.533f),
        new WarpPoint("arachne", 0.360f, 0.550f),
        new WarpPoint("spider",  0.423f, 0.611f),
        new WarpPoint("howl",    0.298f, 0.593f),
        new WarpPoint("park",    0.304f, 0.643f),
        new WarpPoint("castle",  0.285f, 0.686f),
        new WarpPoint("crypt",   0.391f, 0.685f),
        new WarpPoint("hub",     0.416f, 0.712f),
        new WarpPoint("wiz",     0.386f, 0.799f),
        new WarpPoint("da",      0.376f, 0.888f),
        new WarpPoint("gold",    0.533f, 0.645f),
        new WarpPoint("barn",    0.553f, 0.747f),
        new WarpPoint("deep",    0.617f, 0.546f),
        new WarpPoint("glowing", 0.669f, 0.627f),
        new WarpPoint("desert",  0.630f, 0.720f),
        new WarpPoint("trapper", 0.744f, 0.643f),
        new WarpPoint("mines",   0.905f, 0.927f),
        new WarpPoint("ch",      0.905f, 0.955f),
        new WarpPoint("forge",   0.904f, 0.995f),
    };

    /**
     * Extra warps shown only in the GUI map screen (not on the physical wall).
     */
    private static final WarpPoint[] GUI_EXTRAS = {
        new WarpPoint("dragontail", 0.620f, 0.215f),
        new WarpPoint("wasteland",  0.673f, 0.247f),
        new WarpPoint("scarleton",  0.712f, 0.293f),
        new WarpPoint("garden",     0.165f, 0.808f),
        new WarpPoint("dungeon",    0.628f, 0.862f),
        new WarpPoint("rift",       0.752f, 0.855f),
        new WarpPoint("bayou",      0.447f, 0.953f),
        new WarpPoint("basecamp",   0.948f, 0.858f),
    };

    /** Full list used by the GUI map screen (wall warps + extras). */
    public static final WarpPoint[] ALL_WARPS;
    static {
        ALL_WARPS = new WarpPoint[WALL_WARPS.length + GUI_EXTRAS.length];
        System.arraycopy(WALL_WARPS, 0, ALL_WARPS, 0,                WALL_WARPS.length);
        System.arraycopy(GUI_EXTRAS, 0, ALL_WARPS, WALL_WARPS.length, GUI_EXTRAS.length);
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private static WarpPoint hoveredWarp = null;
    private static boolean   inZone      = false;

    // ── HUD render ────────────────────────────────────────────────────────────

    public static void renderHud(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!blade.addon.utils.config.values.FishSettings.warpMapHudEnabled) { hoveredWarp = null; inZone = false; return; }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.currentScreen != null) {
            hoveredWarp = null; inZone = false; return;
        }
        if (!isOnHypixel(mc)) { hoveredWarp = null; inZone = false; return; }
        if (blade.addon.utils.Location.getCurrentLocation() != blade.addon.utils.Location.HUB) { hoveredWarp = null; inZone = false; return; }

        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        inZone = px >= BOX_X0 && px <= BOX_X1
              && py >= BOX_Y0 && py <= BOX_Y1
              && pz >= BOX_Z0 && pz <= BOX_Z1;
        if (!inZone) { hoveredWarp = null; return; }

        // Camera values — handled differently in first-person vs third-person.
        // In first-person the player-lerp X/Z + camera Y is proven stable.
        // In third-person (F5 / F5-front) the camera is offset from the player,
        // so we use the Camera object for all three axes and its own yaw/pitch.
        float delta  = tickCounter.getTickProgress(true);
        Camera camera = mc.gameRenderer.getCamera();
        boolean thirdPerson = mc.options.getPerspective() != Perspective.FIRST_PERSON;

        double ex, ey, ez;
        float  yaw, pitch;
        if (thirdPerson) {
            ex    = camera.getCameraPos().x;
            ey    = camera.getCameraPos().y;
            ez    = camera.getCameraPos().z;
            yaw   = camera.getYaw();
            pitch = camera.getPitch();
        } else {
            ex    = MathHelper.lerp((double)delta, mc.player.lastX, mc.player.getX());
            ey    = camera.getCameraPos().y;
            ez    = MathHelper.lerp((double)delta, mc.player.lastZ, mc.player.getZ());
            yaw   = mc.player.getYaw(delta);
            pitch = mc.player.getPitch(delta);
        }

        double yr   = Math.toRadians(yaw),   pr = Math.toRadians(pitch);
        double cosY = Math.cos(yr), sinY = Math.sin(yr);
        double cosP = Math.cos(pr), sinP = Math.sin(pr);

        double rdx = -sinY * cosP, rdy = -sinP, rdz = cosY * cosP;

        // Crosshair hover detection
        hoveredWarp = null;
        if (rdz > 0.05) {
            double t = (MAP_Z - ez) / rdz;
            if (t > 0) {
                double hitX = ex + t * rdx, hitY = ey + t * rdy;
                double u = (hitX - MAP_X_U0) / (MAP_X_U1 - MAP_X_U0);
                double v = (MAP_Y_TOP - hitY) / (MAP_Y_TOP - MAP_Y_BOT);
                if (u >= -0.05 && u <= 1.05 && v >= -0.05 && v <= 1.05) {
                    double best = Double.MAX_VALUE;
                    for (WarpPoint wp : WALL_WARPS) {
                        double du = wp.u() - u, dv = wp.v() - v;
                        double d  = du * du + dv * dv;
                        if (d < best) { best = d; hoveredWarp = wp; }
                    }
                    if (best > 0.07 * 0.07) hoveredWarp = null;
                }
            }
        }

        // Project and draw
        Window win = mc.getWindow();
        int fov = mc.options.getFov().getValue();
        double f   = 1.0 / Math.tan(Math.toRadians(fov / 2.0));
        double asp = (double) win.getScaledWidth() / win.getScaledHeight();

        for (WarpPoint wp : WALL_WARPS) {
            double dx = uvToWorldX(wp.u()) - ex;
            double dy = uvToWorldY(wp.v()) - ey;
            double dz = MAP_Z              - ez;

            double camX = dx * (-cosY)              + dz * (-sinY);
            double camY = dx * (-sinY * sinP) + dy * cosP + dz * (cosY * sinP);
            double camZ = dx * (-sinY * cosP) + dy * (-sinP) + dz * (cosY * cosP);

            if (camZ <= 0.01) continue;

            double ndcX = f / asp * camX / camZ;
            double ndcY = f          * camY / camZ;

            int sx = (int)((1.0 + ndcX) * 0.5 * win.getScaledWidth()) - 2;
            int sy = (int)((1.0 - ndcY) * 0.5 * win.getScaledHeight());

            drawDot(ctx, sx, sy, wp == hoveredWarp);
        }
    }

    // ── Click handler ─────────────────────────────────────────────────────────

    public static boolean onLeftClick() {
        if (!inZone || hoveredWarp == null) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.currentScreen != null) return false;
        mc.player.networkHandler.sendChatCommand("warp " + hoveredWarp.name());
        mc.player.sendMessage(
            Text.literal("§a[WarpMap] Warping to §l" + hoveredWarp.name() + "§r§a..."), true);
        return true;
    }

    // ── Zone check ────────────────────────────────────────────────────────────

    public static boolean isInZone(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return false;
        if (!isOnHypixel(mc)) return false;
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        return x >= BOX_X0 && x <= BOX_X1
            && y >= BOX_Y0 && y <= BOX_Y1
            && z >= BOX_Z0 && z <= BOX_Z1;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean isOnHypixel(MinecraftClient mc) {
        var s = mc.getCurrentServerEntry();
        return s != null && s.address.toLowerCase().contains("hypixel.net");
    }

    private static void drawDot(DrawContext ctx, int sx, int sy, boolean hov) {
        if (hov) {
            ctx.fill(sx - 6, sy - 6, sx + 6, sy + 6, 0x1A44ee88);
            ctx.fill(sx - 4, sy - 4, sx + 4, sy + 4, 0x5044ee88);
            ctx.fill(sx - 2, sy - 2, sx + 2, sy + 2, 0xFF4cef80);
            ctx.fill(sx - 1, sy - 1, sx + 1, sy + 1, 0xFFDDFFDD);
        } else {
            int c = blade.addon.utils.config.values.FishSettings.warpMapDotColor;
            ctx.fill(sx - 2, sy - 1, sx + 3, sy + 3, c);
            ctx.fill(sx - 2, sy - 2, sx + 2, sy + 2, c);
            ctx.fill(sx,     sy,     sx + 1, sy + 1, 0xCCFFFFFF);
        }
    }
}
