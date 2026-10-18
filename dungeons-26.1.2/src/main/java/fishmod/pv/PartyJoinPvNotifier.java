/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv;

import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Builds the coloured {@code [BPV] Click to open <name>'s pv.} chat line,
 * posted ~1s after a live party-join message so it does not stack instantly.
 *
 * <p>Scheduling is tied to {@link ClientReceiveMessageEvents} only. Chat history
 * restore (Chat Patches / More Chat History) also calls {@code addMessage}, and
 * must not spawn BPV lines for old joins.
 */
public final class PartyJoinPvNotifier {
	private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
	private static final Pattern PARTY_JOIN = Pattern.compile(
		"(?i)^(?:\\[[^\\]]+]\\s*)?([A-Za-z0-9_]{3,16})\\s+joined the party\\.?\\s*$"
	);
	private static final Pattern YOU_JOINED_PARTY = Pattern.compile(
		"(?i)^You have joined\\s+(?:\\[[^\\]]+]\\s*)?([A-Za-z0-9_]{3,16})'s?\\s+party!?\\s*$"
	);
	private static final Pattern PARTY_FINDER_JOIN = Pattern.compile(
		"(?i)^Party Finder\\s*>\\s*(?:\\[[^\\]]+]\\s*)?([A-Za-z0-9_]{3,16})\\s+joined the (?:dungeon )?group!"
	);
	private static final long DELAY_MS = 1000L;
	/** Ignore duplicate live joins for the same name within this window. */
	private static final long DEDUPE_MS = 2500L;
	private static final ConcurrentLinkedQueue<Pending> PENDING = new ConcurrentLinkedQueue<>();
	private static final ConcurrentHashMap<String, Long> RECENT = new ConcurrentHashMap<>();

	private PartyJoinPvNotifier() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(PartyJoinPvNotifier::tick);
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> onLiveMessage(message));
		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
			onLiveMessage(message)
		);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
	}

	/** Queue a delayed BPV line for a party-join player name. */
	public static void schedule(String name) {
		String cleaned = valid(name);
		if (cleaned == null || !fishmod.utils.config.values.FishSettings.profileViewerEnabled) {
			return;
		}
		long now = System.currentTimeMillis();
		String key = cleaned.toLowerCase(Locale.ROOT);
		Long last = RECENT.put(key, now);
		if (last != null && now - last < DEDUPE_MS) {
			return;
		}
		PENDING.add(new Pending(cleaned, now + DELAY_MS));
	}

	private static void onLiveMessage(Component message) {
		if (message == null) {
			return;
		}
		String joinName = extractJoinName(message.getString());
		if (joinName != null) {
			schedule(joinName);
		}
	}

	private static void clear() {
		PENDING.clear();
		RECENT.clear();
	}

	private static void tick(Minecraft client) {
		if (PENDING.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		Iterator<Pending> it = PENDING.iterator();
		while (it.hasNext()) {
			Pending next = it.next();
			if (next.atMs > now) {
				continue;
			}
			it.remove();
			if (client == null || client.gui == null) {
				continue;
			}
			fishmod.compat.McCompatKt.getHud(client).getChat().addClientSystemMessage(bpvLine(next.name));
		}
	}

	static String extractJoinName(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String trimmed = text.trim();
		Matcher m = PARTY_JOIN.matcher(trimmed);
		if (m.find()) {
			return valid(m.group(1));
		}
		m = YOU_JOINED_PARTY.matcher(trimmed);
		if (m.find()) {
			return valid(m.group(1));
		}
		m = PARTY_FINDER_JOIN.matcher(trimmed);
		if (m.find()) {
			return valid(m.group(1));
		}
		return null;
	}

	private static String valid(String name) {
		if (name == null || !PLAYER_NAME.matcher(name).matches()) {
			return null;
		}
		if ("npc".equalsIgnoreCase(name) || name.toLowerCase(Locale.ROOT).startsWith("cit-")) {
			return null;
		}
		return name;
	}

	static Component bpvLine(String name) {
		String cmd = "/pv " + name;
		Style click = Style.EMPTY
			.withClickEvent(new ClickEvent.RunCommand(cmd))
			.withHoverEvent(new HoverEvent.ShowText(
				Component.literal("Open " + name + "'s BetterPV").withStyle(ChatFormatting.YELLOW)
			));
		MutableComponent line = Component.empty();
		line.append(Component.literal("[BPV] ").withStyle(Style.EMPTY.withColor(0x5B8CFF).withBold(true)));
		line.append(Component.literal("Click to open ").withStyle(Style.EMPTY.withColor(0xAAAAAA)));
		line.append(Component.literal(name).withStyle(Style.EMPTY.withColor(0xFFD36A).withUnderlined(true)));
		line.append(Component.literal("'s pv.").withStyle(Style.EMPTY.withColor(0xAAAAAA)));
		return line.withStyle(click);
	}

	private record Pending(String name, long atMs) {
	}
}
