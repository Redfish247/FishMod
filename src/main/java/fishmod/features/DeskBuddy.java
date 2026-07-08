package fishmod.features;

import fishmod.utils.config.values.FishSettings;
import fishmod.utils.events.Events;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;

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
    private static long reactUntilMs   = 0;   // reacting while now < this
    private static int  reactType      = 0;    // R_DANCE
    private static long lastReactMs    = 0;    // de-dupe burst of drop lines into one reaction
    private static long lastActivityMs = System.currentTimeMillis(); // for AFK
    private static long lastBlinkMs    = 0;
    private static boolean blinking    = false;

    // Last-known pose, to detect activity (any move/look = not AFK).
    private static double pX, pY, pZ;
    private static float  pYaw, pPitch;
    private static boolean poseInit = false;

    // Reaction kinds.
    private static final int R_DANCE = 0, R_FAINT = 1, R_LOVE = 2;
    private static final long DANCE_MS       = 4200;
    private static final long FAINT_MS       = 5000;
    private static final long LOVE_MS        = 3500;
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

    /** Make the buddy cheer (dance) right now (used by chat triggers and the debug command). */
    public static void cheer() { react(R_DANCE, DANCE_MS); }

    private static void react(int type, long ms) {
        long now = System.currentTimeMillis();
        if (now - lastReactMs < REACT_DEBOUNCE && now < reactUntilMs && reactType == type) return; // mid-burst
        lastReactMs = now;
        reactType = type;
        reactUntilMs = now + ms;
    }

    private static void onChat(String plain) {
        String up = plain.toUpperCase();
        if (up.contains("RARE DROP")          // covers RARE / VERY RARE / CRAZY RARE
                || up.contains("INSANE DROP")
                || up.contains("PRAISE RNGESUS")
                || up.contains("PET DROP")
                || up.contains("GREAT CATCH")) {
            react(R_DANCE, DANCE_MS);
        } else if (up.contains("LEVELED UP TO LEVEL") || up.contains("LEVELLED UP TO LEVEL")) {
            react(R_LOVE, LOVE_MS);   // pet level-up
        } else if (up.contains("YOU DIED") || up.contains("BECAME A GHOST")
                || up.contains("AND BECAME A GHOST") || up.equals("☠ YOU DIED")) {
            react(R_FAINT, FAINT_MS); // you died
        }
    }

    private static void tick(Minecraft mc) {
        if (!FishSettings.deskBuddyEnabled) return;
        LocalPlayer p = mc.player;
        if (p == null) return;
        long now = System.currentTimeMillis();

        // Activity = any movement or camera change since last tick.
        if (!poseInit) {
            pX = p.getX(); pY = p.getY(); pZ = p.getZ(); pYaw = p.getYRot(); pPitch = p.getXRot();
            poseInit = true; lastActivityMs = now;
        } else {
            boolean moved = p.getX() != pX || p.getY() != pY || p.getZ() != pZ
                    || p.getYRot() != pYaw || p.getXRot() != pPitch;
            if (moved) lastActivityMs = now;
            pX = p.getX(); pY = p.getY(); pZ = p.getZ(); pYaw = p.getYRot(); pPitch = p.getXRot();
        }
    }

    private static boolean isAfk() {
        long afkMs = Math.max(10, FishSettings.deskBuddyAfkSeconds) * 1000L;
        return System.currentTimeMillis() - lastActivityMs > afkMs;
    }

    private static boolean isReacting() { return System.currentTimeMillis() < reactUntilMs; }
    private static boolean isDancing()  { return isReacting() && reactType == R_DANCE; }

    // ── faces ──────────────────────────────────────────────────────────────────
    private static final String[] DANCE = { "§a\\(^o^)/", "§a/(^o^)\\", "§e♪\\(•o•)/♪", "§a<(^-^<)", "§a(>^-^)>" };
    private static final String[] LOVE  = { "§d(♥‿♥)", "§d\\(♥▽♥)/", "§d(♥o♥)" };

    private static String face() {
        if (isReacting()) {
            long t = System.currentTimeMillis();
            switch (reactType) {
                case R_DANCE: return DANCE[(int) ((t / 140) % DANCE.length)];
                case R_LOVE:  return LOVE[(int) ((t / 250) % LOVE.length)];
                case R_FAINT: return "§7(x_x)";
            }
        }
        if (isAfk()) return "§7(-.-) zzz";
        return blinking ? "§f(-‿-)" : "§f(•‿•)";
    }

    private static String mood() {
        if (isReacting()) {
            switch (reactType) {
                case R_DANCE: return "§a✦ POG ✦";
                case R_LOVE:  return "§d♥ nice ♥";
                case R_FAINT: return "§8oof…";
            }
        }
        if (isAfk()) return "§8sleeping…";
        return "§7idle";
    }

    public static void renderHud(GuiGraphics ctx, DeltaTracker tickCounter) {
        if (!FishSettings.deskBuddyEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen != null && !(mc.screen instanceof ChatScreen)) return;

        long now = System.currentTimeMillis();
        // Blink scheduler (only while awake & not mid-reaction).
        if (!isAfk() && !isReacting()) {
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
        } else if (isReacting() && reactType == R_LOVE) {
            bob = (float) (-Math.abs(Math.sin(t * 6)) * 2.5);  // gentle happy bounce
        } else if (isReacting() && reactType == R_FAINT) {
            bob = 3f;                                          // slumped down, lying still
        } else if (isAfk()) {
            bob = (float) (Math.sin(t * 1.2) * 0.8);           // slow breathing
        } else {
            bob = (float) (Math.sin(t * 2.2) * 1.6);
        }

        int lh = fishmod.utils.Constants.TEXT_HEIGHT + 1;
        ctx.pose().pushMatrix();
        ctx.pose().translate((float) x, (float) y);
        ctx.pose().scale(sc, sc);
        ctx.drawString(mc.font, "§6" + name, 0, 0, 0xFFFFFFFF, true);
        ctx.drawString(mc.font, faceStr, Math.round(wiggle), lh + Math.round(bob), 0xFFFFFFFF, true);
        ctx.drawString(mc.font, moodStr, 0, lh * 2 + 2, 0xFFFFFFFF, true);
        ctx.pose().popMatrix();
    }
}
