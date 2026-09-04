package twitchbridge;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import fishmod.utils.config.values.FishSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Registers the {@code /twitch} client command tree. */
public final class TwitchCommands {

	private TwitchCommands() {}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
			dispatcher.register(ClientCommands.literal("twitch")
				.then(ClientCommands.literal("connect")
					.executes(ctx -> {
						FishSettings.twitchBridgeEnabled = true;
						TwitchBridgeClient.connect(null);
						return 1;
					})
					.then(ClientCommands.argument("channel", StringArgumentType.word())
						.executes(ctx -> {
							FishSettings.twitchBridgeEnabled = true;
							TwitchBridgeClient.connect(StringArgumentType.getString(ctx, "channel"));
							return 1;
						})))
				.then(ClientCommands.literal("disconnect")
					.executes(ctx -> {
						TwitchBridgeClient.setEnabled(false);
						feedback(ctx.getSource(), "Disconnected.");
						return 1;
					}))
				.then(ClientCommands.literal("channel")
					.then(ClientCommands.argument("name", StringArgumentType.word())
						.executes(ctx -> {
							FishSettings.twitchBridgeEnabled = true;
							TwitchBridgeClient.setChannel(StringArgumentType.getString(ctx, "name"));
							return 1;
						})))
				.then(ClientCommands.literal("timestamps")
					.then(ClientCommands.argument("on", StringArgumentType.word())
						.executes(ctx -> {
							boolean on = parseBool(StringArgumentType.getString(ctx, "on"));
							TwitchBridgeClient.config().showTimestamps = on;
							TwitchBridgeClient.config().save();
							feedback(ctx.getSource(), "Timestamps " + (on ? "on" : "off") + ".");
							return 1;
						})))
				.then(ClientCommands.literal("colors")
					.then(ClientCommands.argument("on", StringArgumentType.word())
						.executes(ctx -> {
							boolean on = parseBool(StringArgumentType.getString(ctx, "on"));
							TwitchBridgeClient.config().useTwitchColors = on;
							TwitchBridgeClient.config().save();
							feedback(ctx.getSource(), "Twitch name colours " + (on ? "on" : "off") + ".");
							return 1;
						})))
				.then(ClientCommands.literal("reload")
					.executes(ctx -> {
						TwitchBridgeClient.reload();
						return 1;
					}))
				.then(ClientCommands.literal("status")
					.executes(ctx -> {
						String ch = TwitchBridgeClient.currentChannel();
						String msg = TwitchBridgeClient.isConnected()
								? "Connected to #" + ch
								: "Not connected (configured channel: "
									+ orNone(TwitchBridgeClient.config().channel) + ")";
						feedback(ctx.getSource(), msg);
						return 1;
					}))
				.executes(ctx -> {
					feedback(ctx.getSource(),
							"/twitch connect [channel] | disconnect | channel <name> | "
							+ "colors <on|off> | timestamps <on|off> | reload | status");
					return 1;
				})));
	}

	private static boolean parseBool(String s) {
		return s.equalsIgnoreCase("on") || s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes");
	}

	private static String orNone(String s) {
		return s == null || s.isBlank() ? "none" : s;
	}

	private static void feedback(FabricClientCommandSource source, String text) {
		source.sendFeedback(Component.literal("[Twitch] ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(text).withStyle(ChatFormatting.YELLOW)));
	}
}
