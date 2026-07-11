package fishmod.features;

import fishmod.utils.Misc;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Fires a styled welcome message in chat the first time the user joins a server with FishMod
 * installed. A sentinel file at {@code config/fishmod/welcomed.flag} ensures it never shows again
 * after the first time across launches.
 */
public final class WelcomeMessage {
    private WelcomeMessage() {}

    private static final Path SENTINEL = Paths.get("config/fishmod/welcomed.flag");

    public static void init() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (Files.exists(SENTINEL)) return;
            try {
                Files.createDirectories(SENTINEL.getParent());
                Files.writeString(SENTINEL, "1");
            } catch (Exception ignored) {}
            // Slight delay so the welcome lands after Hypixel's join spam.
            CompletableFuture.delayedExecutor(2000, TimeUnit.MILLISECONDS)
                    .execute(() -> MinecraftClient.getInstance().execute(WelcomeMessage::show));
        });
    }

    private static void show() {
        Misc.addChatMessage(Text.literal(""));
        Misc.addChatMessage(Text.literal("§8§m                                                          "));
        Misc.addChatMessage(Text.literal(""));
        Misc.addChatMessage(Text.literal("              §b§lWelcome To FishMod"));
        Misc.addChatMessage(Text.literal("            §7Do §a§l/fm §r§7To Edit Config"));
        Misc.addChatMessage(Text.literal("    §7Bug reports » DM §bredfish2471 §7on Discord"));
        Misc.addChatMessage(Text.literal(""));
        Misc.addChatMessage(Text.literal("§8§m                                                          "));
        Misc.addChatMessage(Text.literal(""));
    }
}
