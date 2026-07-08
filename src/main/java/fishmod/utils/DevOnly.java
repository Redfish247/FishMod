package fishmod.utils;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.Set;

/**
 * Gate for developer-only debug commands. Only the player whose Minecraft UUID
 * appears in {@link #DEV_UUIDS} can run them; everyone else sees a friendly refusal.
 */
public class DevOnly {

    /** Allowed dev UUIDs (no dashes, lowercase). */
    private static final Set<String> DEV_UUIDS = Set.of(
        "2abb218fada349bea6d181a2872941e2"  // RedFish2471
    );

    public static boolean isDev() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        return DEV_UUIDS.contains(mc.player.getUUID().toString().replace("-", "").toLowerCase());
    }

    /** Returns true if the caller is NOT a dev, after sending a "dev-only" message. */
    public static boolean deny(FabricClientCommandSource source) {
        if (isDev()) return false;
        source.sendFeedback(Component.literal("§cThat command is dev-only."));
        return true;
    }
}
