package twitchbridge;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Builds coloured chat lines and pushes them onto the Minecraft chat HUD on the render thread. */
public final class ChatOutput {

	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

	/** Fallback palette (classic Twitch default name colours) when a chatter has no colour set. */
	private static final int[] PALETTE = {
			0xFF0000, 0x0000FF, 0x008000, 0xB22222, 0xFF7F50, 0x9ACD32, 0xFF4500,
			0x2E8B57, 0xDAA520, 0xD2691E, 0x5F9EA0, 0x1E90FF, 0xFF69B4, 0x8A2BE2, 0x00FF7F
	};

	private ChatOutput() {}

	/** A normal chat message: {@code [Twitch] Name: message} */
	public static void chat(TwitchBridgeConfig cfg, String name, String colorHex, String message, boolean action) {
		int rgb = resolveColor(cfg, name, colorHex);
		MutableComponent line = base(cfg);
		line.append(Component.literal(name).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb))));
		if (action) {
			line.append(Component.literal(" " + message)
					.setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withItalic(true)));
		} else {
			line.append(Component.literal(": ").withStyle(ChatFormatting.GRAY));
			line.append(Component.literal(message).withStyle(ChatFormatting.WHITE));
		}
		push(line);
	}

	/** A Twitch event notice (sub, raid, announcement, …). */
	public static void event(TwitchBridgeConfig cfg, String text) {
		push(base(cfg).append(Component.literal(text).withStyle(ChatFormatting.LIGHT_PURPLE)));
	}

	/** A status/info line from the bridge itself. */
	public static void info(TwitchBridgeConfig cfg, String text) {
		push(base(cfg).append(Component.literal(text).withStyle(ChatFormatting.YELLOW)));
	}

	private static MutableComponent base(TwitchBridgeConfig cfg) {
		MutableComponent c = Component.empty();
		if (cfg.showTimestamps) {
			c.append(Component.literal(LocalTime.now().format(CLOCK) + " ").withStyle(ChatFormatting.DARK_GRAY));
		}
		if (cfg.prefix != null && !cfg.prefix.isEmpty()) {
			c.append(Component.literal(cfg.prefix).withStyle(ChatFormatting.GRAY));
		}
		return c;
	}

	private static int resolveColor(TwitchBridgeConfig cfg, String name, String colorHex) {
		if (cfg.useTwitchColors && colorHex != null && colorHex.matches("#[0-9a-fA-F]{6}")) {
			return Integer.parseInt(colorHex.substring(1), 16);
		}
		if (!cfg.useTwitchColors) {
			return 0xD69BF5; // light purple
		}
		int idx = Math.floorMod(name.toLowerCase().hashCode(), PALETTE.length);
		return PALETTE[idx];
	}

	private static void push(Component component) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) return;
		mc.execute(() -> {
			if (mc.gui != null) {
				mc.gui.getChat().addClientSystemMessage(component);
			}
		});
	}
}
