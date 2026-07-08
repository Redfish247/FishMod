package fishmod.features.fishing;

import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;

/**
 * Bobber Reminder HUD.
 *
 * <p>Watches the player's fishing bobber. Once it has settled on the water/lava, a sharp downward
 * pull is treated as a bite. From the bite, the HUD:
 * <ol>
 *   <li>counts down for {@link FishSettings#fishingReminderDelay} seconds, then</li>
 *   <li>flashes the customizable {@link FishSettings#fishingReminderText} (+ optional ping), then</li>
 *   <li>if the catch window closes without a reel, shows {@link FishSettings#fishingMissedText}.</li>
 * </ol>
 * Reeling in (the bobber entity disappears) during the window clears the HUD — it only nags when
 * you're slow to react.
 */
public final class FishingTimer {

    private FishingTimer() {}

    // A bite is a downward velocity spike once the bobber has settled. Idle buoyancy bobbing is far
    // gentler than this, and the cast arc is filtered out by the "settled" gate.
    private static final double SETTLE_SPEED = 0.04;   // bobber considered settled below this speed
    private static final double BITE_VY      = -0.12;  // downward pull that counts as a bite
    private static final long   MISS_HOLD_MS = 2000;   // how long "missed it" stays up
    private static final long   GRACE_MS     = 2000;   // unreeled time after the reminder = a miss

    private static int  bobberId   = -1;
    private static boolean settled  = false;
    private static long biteAtMs    = 0;     // 0 = no active bite
    private static boolean soundPlayed = false;
    private static long missedUntilMs = 0;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(FishingTimer::tick);
    }

    private static void reset() {
        bobberId = -1;
        settled = false;
        biteAtMs = 0;
        soundPlayed = false;
    }

    private static void tick(Minecraft mc) {
        if (!FishSettings.fishingTimerEnabled) { reset(); return; }
        if (mc.player == null || mc.level == null) { reset(); return; }

        FishingHook bobber = findBobber(mc);

        if (bobber == null) {
            // Bobber gone. If we were mid-bite and the catch window hadn't elapsed, the player reeled
            // in time — clear silently. Otherwise leave any "missed it" hold as-is.
            reset();
            return;
        }

        if (bobber.getId() != bobberId) {
            // Fresh cast — start clean (also clears a lingering "missed it").
            bobberId = bobber.getId();
            settled = false;
            biteAtMs = 0;
            soundPlayed = false;
            missedUntilMs = 0;
        }

        Vec3 v = bobber.getDeltaMovement();
        double speed = v.length();
        if (!settled) {
            if (speed < SETTLE_SPEED) settled = true;
            return; // still arcing/landing — no bite detection yet
        }

        long now = System.currentTimeMillis();

        if (biteAtMs == 0) {
            if (v.y < BITE_VY) { biteAtMs = now; soundPlayed = false; missedUntilMs = 0; }
            return;
        }

        long elapsed = now - biteAtMs;
        long delayMs = Math.max(0, FishSettings.fishingReminderDelay) * 1000L;

        if (elapsed >= delayMs && !soundPlayed) {
            soundPlayed = true;
            if (FishSettings.fishingReminderSound)
                mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 2.0f);
            if (FishSettings.ttsFishing) fishmod.utils.Tts.speak("Reel");
        }

        // Window fully closed without a reel → it's a miss. Latch the "missed it" message and stop
        // tracking this bite (a new downward spike on the same bobber will start a fresh one).
        if (elapsed >= delayMs + GRACE_MS) {
            missedUntilMs = now + MISS_HOLD_MS;
            biteAtMs = 0;
            soundPlayed = false;
        }
    }

    /** The player's own bobber, or null. */
    private static FishingHook findBobber(Minecraft mc) {
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof FishingHook b && b.getPlayerOwner() == mc.player) return b;
        }
        return null;
    }

    /** Current line to draw, or null when there's nothing to show. */
    private static String currentLine() {
        long now = System.currentTimeMillis();
        if (biteAtMs > 0) {
            long elapsed = now - biteAtMs;
            long delayMs = Math.max(0, FishSettings.fishingReminderDelay) * 1000L;
            if (elapsed < delayMs) {
                double remaining = (delayMs - elapsed) / 1000.0;
                return String.format("§e§lReel in %.1fs", remaining);
            }
            // Past the delay: flash the reminder (blink ~3 Hz so it grabs the eye).
            boolean on = ((now / 160) % 2) == 0;
            return on ? FishSettings.fishingReminderText : "§4" + stripColors(FishSettings.fishingReminderText);
        }
        if (now < missedUntilMs) return FishSettings.fishingMissedText;
        return null;
    }

    private static String stripColors(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }

    public static boolean isVisible() {
        return FishSettings.fishingTimerEnabled && currentLine() != null;
    }

    public static void renderHud(GuiGraphics ctx, DeltaTracker tick) {
        if (!FishSettings.fishingTimerEnabled || !Location.inSkyblock()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) return;

        String line = currentLine();
        if (line == null) return;

        float sc = (float) FishSettings.fishingTimerScale;
        ctx.pose().pushMatrix();
        ctx.pose().translate((float) FishSettings.fishingTimerHudX, (float) FishSettings.fishingTimerHudY);
        ctx.pose().scale(sc, sc);
        ctx.drawString(mc.font, line, 0, 0, 0xFFFFFFFF, true);
        ctx.pose().popMatrix();
    }
}
