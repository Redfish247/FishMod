package fishmod.features.dungeon.f7;

import config.practical.hud.HUDComponent;
import fishmod.mixin.accessors.BossBarHudAccessor;
import fishmod.utils.Constants;
import fishmod.utils.Location;
import fishmod.utils.Misc;
import fishmod.utils.Scheduler;
import fishmod.utils.config.values.Floor7;
import fishmod.utils.dungeon.Phase;
import fishmod.utils.events.Events;
import fishmod.utils.rendering.RenderUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.Map;
import java.util.UUID;

/** Counts down 3.45s from Goldor's health hitting 0, then tells you to leap. */
public class GoldorLeapTimer {

    private static final int TOTAL_TICKS = 75; // 3.75s / 0.05s per tick
    private static int tick = 0;
    private static boolean wasAlive = false;

    public static void init() {
        Events.ON_SERVER_TICK.register(() -> {
            if (!Location.inDungeon() || !Phase.inGoldorTunnel()) return false;

            float progress = getGoldorProgress();
            if (progress > 0f) {
                wasAlive = true;
            } else if (wasAlive) {
                wasAlive = false;
                tick = TOTAL_TICKS;
            }

            if (tick > 0) {
                tick--;
                if (tick == 0 && Floor7.leapNotifications) {
                    Misc.forceTitle(Component.literal("LEAP!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), Component.empty());
                    Scheduler.scheduleSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1, 1);
                }
            }
            return false;
        });

        Events.ON_LOCATION_CHANGE.register(newLocation -> {
            tick = 0;
            wasAlive = false;
            return false;
        });
    }

    private static float getGoldorProgress() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return -1f;

        BossBarHudAccessor accessor = (BossBarHudAccessor) mc.gui.getBossOverlay();
        Map<UUID, LerpingBossEvent> bossBars = accessor.getBossBars();
        if (bossBars == null || bossBars.isEmpty()) return -1f;

        for (LerpingBossEvent bar : bossBars.values()) {
            String name = bar.getName().getString().replaceAll("§.", "").trim();
            if (name.contains("Goldor")) return bar.getProgress();
        }
        return -1f;
    }

    public static boolean display() {
        return Floor7.leapNotifications && Location.inDungeon() && Phase.inGoldorTunnel() && tick > 0;
    }

    public static void render(HUDComponent component, GuiGraphicsExtractor context) {
        RenderUtils.drawTimer(component, context, tick, tick < 20 ? Constants.GREEN : Constants.GOLD);
    }
}
