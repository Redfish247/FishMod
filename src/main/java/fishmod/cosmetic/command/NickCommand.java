package fishmod.cosmetic.command;

import fishmod.cosmetic.NickState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

public final class NickCommand {
    private NickCommand() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("nick")
            .then(ClientCommandManager.literal("reset").executes(ctx -> {
                NickState.reset();
                ctx.getSource().sendFeedback(Text.literal("§aNick reset to your real username."));
                return 1;
            }))
            .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                .executes(ctx -> {
                    String name = StringArgumentType.getString(ctx, "name").trim();
                    if (!name.isEmpty() && !name.equalsIgnoreCase("reset")) {
                        NickState.set(name);
                        ctx.getSource().sendFeedback(
                            Text.literal("§aDisplay name set to ").append(NickState.asComponent()));
                    } else {
                        NickState.reset();
                        ctx.getSource().sendFeedback(Text.literal("§aNick reset."));
                    }
                    return 1;
                }))
            .executes(ctx -> {
                ctx.getSource().sendFeedback(Text.literal("§eUsage: §f/nick <name> §7or §f/nick reset"));
                return 1;
            }));
    }
}
