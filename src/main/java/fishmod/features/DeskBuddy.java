package fishmod.features;

import fishmod.utils.config.values.FishSettings;
import fishmod.utils.events.Events;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;

/**
 * Desk-Buddy — a tiny kaomoji companion that lives in the corner of your HUD. It idles with a gentle
 * bob and the odd blink, curls up to sleep when you go AFK, and breaks into a little dance whenever
 * RNG smiles on you (rare drops, "PRAISE RNGESUS", etc.). Purely cosmetic; rendered from text glyphs
 * so it needs no texture assets.
 *
 * Reaction triggers are detected from chat in {@link #init()} so the feature is fully self-contained —
 * other trackers don't need to know it exists.
 */
public final class DeskBuddy {

    private DeskBuddy() {}

    // ── reaction state ─────────────────────────────────────────────────────────
    private static long danceUntilMs   = 0;   // dancing while now < this
    private static long lastReactMs    = 0;    // de-dupe burst of drop lines into one dance
    private static long lastActivityMs = System.currentTimeMillis(); // for AFK
    private static long lastBlinkMs    = 0;
    private static boolean blinking    = false;

    // Last-known pose, to detect activity (any move/look = not AFK).
    private static double pX, pY, pZ;
    private static float  pYaw, pPitch;
    private static boolean poseInit = false;

    private static final long DANCE_MS       = 4200;
    private static final long REACT_DEBOUNCE = 600;   // collapse multi-line drop spam into one cheer
    private static final long BLINK_EVERY_MS = 3800;
    private static final long BLINK_HOLD_MS  = 160;

    public static void init() {
        Events.ON_GAME_MESSAGE.register(text -> {
            if (text == null) return false;
            if (FishSettings.deskBuddyEnabled && FishSettings.deskBuddyReactToRng)
                onChat(text.getString().replaceAll("§.", "").trim());
            return false;
        });
        ClientTickEvents.END_CLIENT_TICK.register(DeskBuddy::tick);
    }

    /** Make the buddy cheer right now (used by chat triggers and the debug command). */
    public static void cheer() {
        long now = System.currentTimeMillis();
        if (now - lastReactMs < REACT_DEBOUNCE && now < danceUntilMs) return; // already mid-cheer burst
        lastReactMs = now;
        danceUntilMs = now + DANCE_MS;
    }

    private static void onChat(String plain) {
        String up = plain.toUpperCase();
        if (up.contains("RARE DROP")          // covers RARE / VERY RARE / CRAZY RARE
                || up.contains("INSANE DROP")
                || up.contains("PRAISE RNGESUS")
                || up.contains("PET DROP")
                || up.contains("GREAT CATCH")) {
            cheer();
        }
    }

    private static void tick(MinecraftClient mc) {
        if (!FishSettings.deskBuddyEnabled) return;
        ClientPlayerEntity p = mc.player;
        if (p == null) return;
        long now = System.currentTimeMillis();

        // Activity = any movement or camera change since last tick.
        if (!poseInit) {
            pX = p.getX(); pY = p.getY(); pZ = p.getZ(); pYaw = p.getYaw(); pPitch = p.getPitch();
            poseInit = true; lastActivityMs = now;
        } else {
            boolean moved = p.getX() != pX || p.getY() != pY || p.getZ() != pZ
                    || p.getYaw() != pYaw || p.getPitch() != pPitch;
            if (moved) lastActivityMs = now;
            pX = p.getX(); pY = p.getY(); pZ = p.getZ(); pYaw = p.getYaw(); pPitch = p.getPitch();
        }
    }

    private static boolean isAfk() {
        long afkMs = Math.max(10, FishSettings.deskBuddyAfkSeconds) * 1000L;
        return System.currentTimeMillis() - lastActivityMs > afkMs;
    }

    private static boolean isDancing() { return System.currentTimeMillis() < danceUntilMs; }

    // ── faces ──────────────────────────────────────────────────────────────────
    private static final String[] DANCE = { "§a\\(^o^)/", "§a/(^o^)\\", "§e♪\\(•o•)/♪", "§a<(^-^<)", "§a(>^-^)>" };

    private static String face() {
        if (isDancing()) {
            int frame = (int) ((System.currentTimeMillis() / 140) % DANCE.length);
            return DANCE[frame];
        }
        if (isAfk()) return "§7(-.-) zzz";
        return blinking ? "§f(-‿-)" : "§f(•‿•)";
    }

    private static String mood() {
        if (isDancing()) return "§a✦ POG ✦";
        if (isAfk())     return "§8sleeping…";
        return "§7idle";
    }

    public static void renderHud(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!FishSettings.deskBuddyEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof ChatScreen)) return;

        long now = System.currentTimeMillis();
        // Blink scheduler (only while awake & not dancing).
        if (!isAfk() && !isDancing()) {
            if (!blinking && now - lastBlinkMs > BLINK_EVERY_MS) { blinking = true; lastBlinkMs = now; }
            else if (blinking && now - lastBlinkMs > BLINK_HOLD_MS) { blinking = false; }
        } else {
            blinking = false;
        }

        String name = FishSettings.deskBuddyName == null || FishSettings.deskBuddyName.isBlank()
                ? "Rocky" : FishSettings.deskBuddyName;
        String faceStr = face();
        String moodStr = mood();

        int x = FishSettings.deskBuddyHudX;
        int y = FishSettings.deskBuddyHudY;
        float sc = (float) FishSettings.deskBuddyScale;

        // Bob: slow gentle float while idle, fast bouncy hop while dancing.
        double t = now / 1000.0;
        float bob;
        float wiggle = 0f;
        if (isDancing()) {
            bob = (float) (-Math.abs(Math.sin(t * 10)) * 4.0); // hop up off the "floor"
            wiggle = (float) (Math.sin(t * 18) * 2.0);
        } else if (isAfk()) {
            bob = (float) (Math.sin(t * 1.2) * 0.8);           // slow breathing
        } else {
            bob = (float) (Math.sin(t * 2.2) * 1.6);
        }

        int lh = fishmod.utils.Constants.TEXT_HEIGHT + 1;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) x, (float) y);
        ctx.getMatrices().scale(sc, sc);
        ctx.drawText(mc.textRenderer, "§6" + name, 0, 0, 0xFFFFFFFF, true);
        ctx.drawText(mc.textRenderer, faceStr, Math.round(wiggle), lh + Math.round(bob), 0xFFFFFFFF, true);
        ctx.drawText(mc.textRenderer, moodStr, 0, lh * 2 + 2, 0xFFFFFFFF, true);
        ctx.getMatrices().popMatrix();
    }
}
