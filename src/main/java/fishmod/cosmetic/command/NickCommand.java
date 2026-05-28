package fishmod.cosmetic.command;

import fishmod.cosmetic.GradientNick;
import fishmod.cosmetic.NickState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Colors your real username with a gradient. Custom names are not allowed — color only. */
public final class NickCommand {
    private NickCommand() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("nick")
            .then(ClientCommandManager.literal("reset").executes(ctx -> {
                NickState.reset();
                ctx.getSource().sendFeedback(Text.literal("§aName color reset to your real username."));
                return 1;
            }))
            .then(ClientCommandManager.literal("rainbow").executes(ctx -> {
                NickState.setGradient(GradientNick.rainbow());
                ctx.getSource().sendFeedback(Text.literal("§aName color set to ").append(NickState.asComponent()));
                return 1;
            }))
            .then(ClientCommandManager.argument("colors", StringArgumentType.greedyString())
                .executes(ctx -> {
                    String arg = StringArgumentType.getString(ctx, "colors").trim();
                    if (arg.equalsIgnoreCase("reset")) {
                        NickState.reset();
                        ctx.getSource().sendFeedback(Text.literal("§aName color reset."));
                        return 1;
                    }
                    List<int[]> stops = new ArrayList<>();
                    for (String tok : arg.split("[\\s,]+")) {
                        if (tok.isEmpty()) continue;
                        int[] rgb = GradientNick.parseColor(tok);
                        if (rgb == null) {
                            ctx.getSource().sendError(Text.literal("§cUnknown color: §f" + tok));
                            return 0;
                        }
                        stops.add(rgb);
                    }
                    if (stops.isEmpty()) { usage(ctx.getSource()); return 0; }
                    NickState.setGradient(stops.toArray(new int[0][]));
                    ctx.getSource().sendFeedback(Text.literal("§aName color set to ").append(NickState.asComponent()));
                    return 1;
                }))
            .executes(ctx -> { usage(ctx.getSource()); return 1; }));
    }

    private static void usage(FabricClientCommandSource src) {
        src.sendFeedback(Text.literal(
            "§eUsage: §f/nick <color> [color2 ...] §7| §f/nick rainbow §7| §f/nick reset"));
        src.sendFeedback(Text.literal("§7Colors: names (§fred§7, §fdark_blue§7), codes (§fa§7), or hex (§f#ff8800§7)."));
    }
}
