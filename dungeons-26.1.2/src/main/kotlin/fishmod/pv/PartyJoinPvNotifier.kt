/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv

import fishmod.compat.hud
import fishmod.utils.config.values.FishSettings
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style

/**
 * Builds the coloured `[BPV] Click to open <name>'s pv.` chat line,
 * posted ~1s after a live party-join message so it does not stack instantly.
 *
 * Scheduling is tied to [ClientReceiveMessageEvents] only. Chat history
 * restore (Chat Patches / More Chat History) also calls `addMessage`, and
 * must not spawn BPV lines for old joins.
 */
object PartyJoinPvNotifier {
	private val PLAYER_NAME = Regex("[A-Za-z0-9_]{3,16}")
	private val PARTY_JOIN = Regex(
		"(?i)^(?:\\[[^\\]]+]\\s*)?([A-Za-z0-9_]{3,16})\\s+joined the party\\.?\\s*$"
	)
	private val YOU_JOINED_PARTY = Regex(
		"(?i)^You have joined\\s+(?:\\[[^\\]]+]\\s*)?([A-Za-z0-9_]{3,16})'s?\\s+party!?\\s*$"
	)
	private val PARTY_FINDER_JOIN = Regex(
		"(?i)^Party Finder\\s*>\\s*(?:\\[[^\\]]+]\\s*)?([A-Za-z0-9_]{3,16})\\s+joined the (?:dungeon )?group!"
	)
	private const val DELAY_MS = 1000L
	/** Ignore duplicate live joins for the same name within this window. */
	private const val DEDUPE_MS = 2500L
	private val PENDING = ConcurrentLinkedQueue<Pending>()
	private val RECENT = ConcurrentHashMap<String, Long>()

	private class Pending(val name: String, val atMs: Long)

	@JvmStatic
	fun register() {
		ClientTickEvents.END_CLIENT_TICK.register { tick(it) }
		ClientReceiveMessageEvents.GAME.register { message, _ -> onLiveMessage(message) }
		ClientReceiveMessageEvents.CHAT.register { message, _, _, _, _ -> onLiveMessage(message) }
		ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> clear() }
	}

	/** Queue a delayed BPV line for a party-join player name. */
	@JvmStatic
	fun schedule(name: String?) {
		val cleaned = valid(name)
		if (cleaned == null || !FishSettings.profileViewerEnabled) {
			return
		}
		val now = System.currentTimeMillis()
		val key = cleaned.lowercase(Locale.ROOT)
		// Only the dedupe window matters; drop older names so the map stays small.
		RECENT.values.removeIf { now - it >= DEDUPE_MS }
		val last = RECENT.put(key, now)
		if (last != null && now - last < DEDUPE_MS) {
			return
		}
		PENDING.add(Pending(cleaned, now + DELAY_MS))
	}

	private fun onLiveMessage(message: Component?) {
		val joinName = extractJoinName(message?.string ?: return)
		if (joinName != null) {
			schedule(joinName)
		}
	}

	private fun clear() {
		PENDING.clear()
		RECENT.clear()
	}

	private fun tick(client: Minecraft?) {
		if (PENDING.isEmpty()) {
			return
		}
		val now = System.currentTimeMillis()
		val it = PENDING.iterator()
		while (it.hasNext()) {
			val next = it.next()
			if (next.atMs > now) {
				continue
			}
			it.remove()
			if (client == null || client.gui == null) {
				continue
			}
			client.hud.chat.addClientSystemMessage(bpvLine(next.name))
		}
	}

	@JvmStatic
	fun extractJoinName(text: String?): String? {
		if (text.isNullOrBlank()) {
			return null
		}
		val trimmed = text.trim()
		for (pattern in arrayOf(PARTY_JOIN, YOU_JOINED_PARTY, PARTY_FINDER_JOIN)) {
			val m = pattern.find(trimmed)
			if (m != null) {
				return valid(m.groupValues[1])
			}
		}
		return null
	}

	private fun valid(name: String?): String? {
		if (name == null || !PLAYER_NAME.matches(name)) {
			return null
		}
		if (name.equals("npc", ignoreCase = true) || name.lowercase(Locale.ROOT).startsWith("cit-")) {
			return null
		}
		return name
	}

	@JvmStatic
	fun bpvLine(name: String): Component {
		val cmd = "/pv $name"
		val click = Style.EMPTY
			.withClickEvent(ClickEvent.RunCommand(cmd))
			.withHoverEvent(HoverEvent.ShowText(Component.literal("Open $name's profile").withStyle(ChatFormatting.YELLOW)))
		val line = Component.empty()
		line.append(Component.literal("[BPV] ").withStyle(Style.EMPTY.withColor(0x5B8CFF).withBold(true)))
		line.append(Component.literal("Click to open ").withStyle(Style.EMPTY.withColor(0xAAAAAA)))
		line.append(Component.literal(name).withStyle(Style.EMPTY.withColor(0xFFD36A).withUnderlined(true)))
		line.append(Component.literal("'s pv.").withStyle(Style.EMPTY.withColor(0xAAAAAA)))
		return line.withStyle(click)
	}
}
