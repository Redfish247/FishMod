package fishmod.features.slayer;

import fishmod.utils.Location;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.events.Events;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.HashSet;
import java.util.Set;

/**
 * Slayer Alerts — title + ping on the key slayer beats:
 * <ul>
 *   <li><b>Boss spawned</b>: detected from the boss armor-stand nametag ("Spawned by: &lt;you&gt;").</li>
 *   <li><b>Boss slain</b>: the "SLAYER QUEST COMPLETE" chat line.</li>
 *   <li><b>Miniboss</b>: tunable spawn lines (see {@link #MINIBOSS_LINES}); capture exact text with
 *       {@code /fmslayerdump} if one isn't firing.</li>
 * </ul>
 */
public final class SlayerAlerts {

    private SlayerAlerts() {}

    // Best-known miniboss spawn lines across the five slayers. Substring match, case-insensitive.
    // Verify/extend with /fmslayerdump — Hypixel's wording varies per slayer/tier.
    private static final String[] MINIBOSS_LINES = {
            "A Revenant Sycophant", "Atoned Champion", "Deformed Revenant",
            "Pack Enforcer", "Sven Follower",
            "Tarantula Vermin", "Tarantula Beast", "Mutant Tarantula",
            "Voidling Devotee", "Voidling Radical", "Voidcrazed Maniac",
            "Flare Demon", "Kindleheart Demon", "Burningsoul Demon",
    };

    public static boolean debugDump = false;

    private static int tickCount = 0;
    private static final Set<Integer> seenSpawnIds = new HashSet<>();
    private static long lastMinibossMs = 0;
    private static String lastMsg = "";
    private static long lastMsgMs = 0;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(SlayerAlerts::tick);

        Events.ON_GAME_MESSAGE.register(text -> {
            if (!FishSettings.slayerAlertsEnabled || text == null) return false;
            onChat(text.getString().replaceAll("§.", "").trim());
            return false;
        });

        Events.ON_WORLD_CHANGE.register(() -> { seenSpawnIds.clear(); return false; });
    }

    private static void tick(MinecraftClient mc) {
        if (!FishSettings.slayerAlertsEnabled || !FishSettings.slayerAlertBossSpawn) return;
        if (mc.player == null || mc.world == null) return;
        if (++tickCount < 10) return;
        tickCount = 0;

        String self = mc.player.getGameProfile().name();
        String marker = "Spawned by: " + self;
        for (Entity e : mc.world.getEntities()) {
            if (!e.hasCustomName()) continue;
            Text name = e.getCustomName();
            if (name == null) continue;
            String s = name.getString().replaceAll("§.", "");
            if (s.contains(marker) && seenSpawnIds.add(e.getId())) {
                alert("§5§lSLAYER BOSS", "§dSpawned — slay it!", SoundEvents.ENTITY_WITHER_SPAWN.value());
            }
        }
        // Keep the seen-set from growing without bound across a long session.
        if (seenSpawnIds.size() > 64) seenSpawnIds.clear();
    }

    private static void onChat(String plain) {
        long now = System.currentTimeMillis();
        if (plain.equals(lastMsg) && now - lastMsgMs < 80) return;
        lastMsg = plain; lastMsgMs = now;

        if (plain.contains("SLAYER QUEST COMPLETE")) {
            if (FishSettings.slayerAlertBossSlain)
                alert("§a§lBOSS SLAIN", "§7Slayer quest complete", SoundEvents.ENTITY_PLAYER_LEVELUP.value());
            return;
        }

        if (FishSettings.slayerAlertMiniboss && now - lastMinibossMs > 1500) {
            for (String line : MINIBOSS_LINES) {
                if (plain.toLowerCase().contains(line.toLowerCase())) {
                    lastMinibossMs = now;
                    alert("§c§lMINIBOSS", "§7" + line, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value());
                    return;
                }
            }
        }

        if (debugDump && Location.inSkyblock() && !plain.isEmpty()) {
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute(() -> {
                if (mc.inGameHud != null)
                    mc.inGameHud.getChatHud().addMessage(Text.literal("§8[slayerdump] §7" + plain));
            });
        }
    }

    private static void alert(String title, String subtitle, net.minecraft.sound.SoundEvent sound) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            InGameHud hud = mc.inGameHud;
            if (hud == null) return;
            hud.setTitleTicks(0, 25, 8);
            hud.setTitle(Text.literal(title));
            hud.setSubtitle(Text.literal(subtitle));
            if (FishSettings.slayerAlertSound && mc.player != null)
                mc.player.playSound(sound, 1.0f, 1.0f);
        });
    }
}
