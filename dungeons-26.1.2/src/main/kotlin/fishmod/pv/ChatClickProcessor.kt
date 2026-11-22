/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv

import fishmod.utils.TextStrip
import fishmod.utils.config.values.FishSettings
import java.util.Locale
import java.util.regex.Pattern
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style

/**
 * Opens `/pv` from chat the same way SkyBlock Profile Viewer does:
 *
 * 1. Remap existing Hypixel `/socialoptions` / `/viewprofile` / social-menu
 *    clicks to `/pv <name>` so they work in SkyBlock, including guild chat.
 * 2. Optionally make whole `Party|Guild|Officer|Co-op > Name: ...` lines clickable.
 *
 * Never invents clicks from arbitrary text (that falsely matched mod banners like
 * `[Detexturify] Update available: ...`).
 *
 * Wired via Fabric `MODIFY_GAME`.
 */
object ChatClickProcessor {
	private val PLAYER_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,16}")
	private val UUID_PATTERN = Pattern.compile(
		"(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
	)
	private val VIEW_PROFILE_HOVER = Pattern.compile(
		"(?i)\\bview\\s+(?:\\[[^\\]]*\\]\\s*)?([A-Za-z0-9_]{1,16})['’ʼ‘]?s\\s+profile\\b"
	)
	private val CHANNEL_CHAT = Pattern.compile(
		"(?i)^(?:Party|Guild|Officer|Co-op)\\b[^>]{0,8}> (?:[^\\[\\s]{1,3}\\s+)?(?:\\[[^\\]]*\\]\\s*)?([A-Za-z0-9_]{1,16})(?:\\s*\\[[^\\]]*\\])?: .+"
	)
	private val RANK_TAG = Regex("\\[[^\\]]*\\]")
	private val LINE_BREAK = Regex("\\R")
	private val WHITESPACE = Regex("\\s+")

	@JvmStatic
	fun process(component: Component?): Component? {
		if (component == null || !FishSettings.profileViewerEnabled) {
			return null
		}
		val channelName = usernameFromChannelChat(stripLegacy(component.string))
		val remapped = remapTree(component, channelName)
		if (channelName != null && !alreadyHasPvClick(remapped)) {
			val copy = remapped.copy()
			copy.setStyle(pvStyle(copy.style, channelName))
			return copy
		}
		return remapped
	}

	private fun usernameFromChannelChat(plain: String?): String? {
		if (plain.isNullOrBlank()) {
			return null
		}
		val matcher = CHANNEL_CHAT.matcher(plain.trim())
		if (!matcher.matches()) {
			return null
		}
		val username = matcher.group(1)
		if (username == null || !PLAYER_NAME_PATTERN.matcher(username).matches()) {
			return null
		}
		return username
	}

	private fun remapTree(component: Component, fallbackName: String?): Component {
		val siblings = component.siblings
		val remappedStyle = remapStyle(component.style, fallbackName, component.plainCopy().string)
		val styleChanged = remappedStyle !== component.style

		val output = component.copy()
		output.siblings.clear()
		if (styleChanged) {
			output.setStyle(remappedStyle)
		}

		var childChanged = false
		for (sibling in siblings) {
			val next = remapTree(sibling, fallbackName)
			output.append(next)
			if (next !== sibling) {
				childChanged = true
			}
		}

		if (!styleChanged && !childChanged) {
			return component
		}
		return output
	}

	private fun remapStyle(style: Style, fallbackName: String?, spanText: String?): Style {
		val click = style.clickEvent
		var name = usernameFromHypixelStyle(style) ?: fallbackName
		if (name == null && click != null) {
			name = usernameFromSpanText(spanText)
		}
		if (name == null || !PLAYER_NAME_PATTERN.matcher(name).matches()) {
			return style
		}
		if (isPvClick(click)) {
			return style
		}
		if (isSocialOrProfileClick(click) || looksLikeSocialHover(hoverPlain(style))) {
			return pvStyle(style, name)
		}
		if (fallbackName != null && click != null && shouldReplaceChannelClick(click)) {
			return pvStyle(style, name)
		}
		return style
	}

	private fun shouldReplaceChannelClick(click: ClickEvent): Boolean {
		if (click is ClickEvent.OpenUrl || click is ClickEvent.OpenFile || click is ClickEvent.CopyToClipboard) {
			return false
		}
		return isSocialOrProfileClick(click)
	}

	private fun isSocialOrProfileClick(click: ClickEvent?): Boolean {
		if (click == null) {
			return false
		}
		if (click is ClickEvent.RunCommand) {
			return isHypixelProfileCommand(click.command())
		}
		if (click is ClickEvent.SuggestCommand) {
			return isHypixelProfileCommand(click.command())
		}
		val text = click.toString().lowercase(Locale.ROOT)
		return "socialoptions" in text ||
			"viewprofile" in text ||
			"social_menu" in text ||
			"socialmenu" in text ||
			"social menu" in text
	}

	private fun looksLikeSocialHover(hoverText: String?): Boolean {
		if (hoverText.isNullOrBlank()) {
			return false
		}
		val lower = hoverText.lowercase(Locale.ROOT)
		return "social menu" in lower ||
			"viewprofile" in lower ||
			VIEW_PROFILE_HOVER.matcher(hoverText).find() ||
			("view " in lower && "profile" in lower)
	}

	private fun alreadyHasPvClick(component: Component): Boolean = isPvClick(component.style.clickEvent)

	private fun isPvClick(click: ClickEvent?): Boolean {
		if (click !is ClickEvent.RunCommand) {
			return false
		}
		val cmd = normalizeCommand(click.command())
		return cmd != null && (cmd == "pv" || cmd.startsWith("pv "))
	}

	private fun isHypixelProfileCommand(command: String?): Boolean {
		if (command == null) {
			return false
		}
		val lower = command.lowercase(Locale.ROOT)
		return lower.startsWith("/socialoptions") || lower.startsWith("socialoptions") ||
			lower.startsWith("/viewprofile") || lower.startsWith("viewprofile")
	}

	private fun hoverPlain(style: Style?): String? {
		val hover = style?.hoverEvent
		if (hover is HoverEvent.ShowText) {
			return stripLegacy(hover.value().string)
		}
		return null
	}

	private fun stripLegacy(text: String?): String? = if (text == null) null else TextStrip.plain(text)

	private fun usernameFromSocial(command: String?, hoverText: String?): String? {
		usernameFromCommand(command)?.let { return it }
		if (hoverText.isNullOrBlank()) {
			return null
		}
		for (line in hoverText.split(LINE_BREAK)) {
			val matcher = VIEW_PROFILE_HOVER.matcher(line)
			if (matcher.find()) {
				val name = matcher.group(1)
				if (PLAYER_NAME_PATTERN.matcher(name).matches()) {
					return name
				}
			}
		}
		return null
	}

	@JvmStatic
	fun usernameFromHypixelStyle(style: Style?): String? {
		if (style == null) {
			return null
		}
		val click = style.clickEvent
		if (click is ClickEvent.RunCommand) {
			usernameFromCommand(click.command())?.let { return it }
		}
		if (click is ClickEvent.SuggestCommand) {
			usernameFromCommand(click.command())?.let { return it }
		}
		val hoverText = hoverPlain(style) ?: return null
		return usernameFromSocial(null, hoverText)
	}

	private fun usernameFromSpanText(text: String?): String? {
		if (text == null) {
			return null
		}
		val stripped = TextStrip.plain(text).replace(RANK_TAG, "").trim()
		return if (PLAYER_NAME_PATTERN.matcher(stripped).matches()) stripped else null
	}

	@JvmStatic
	fun usernameFromCommand(command: String?): String? {
		if (command.isNullOrBlank()) {
			return null
		}
		val trimmed = command.trim().removePrefix("/")
		val lower = trimmed.lowercase(Locale.ROOT)
		val rest = when {
			lower.startsWith("socialoptions") -> trimmed.substring("socialoptions".length).trim()
			lower.startsWith("viewprofile") -> trimmed.substring("viewprofile".length).trim()
			else -> return null
		}
		if (rest.isEmpty()) {
			return null
		}
		for (token in rest.split(WHITESPACE)) {
			if (token.isEmpty() || UUID_PATTERN.matcher(token).matches()) {
				continue
			}
			if (PLAYER_NAME_PATTERN.matcher(token).matches()) {
				return token
			}
		}
		return null
	}

	private fun normalizeCommand(command: String?): String? =
		command?.trim()?.removePrefix("/")?.lowercase(Locale.ROOT)

	private fun pvStyle(style: Style?, name: String): Style {
		val tip = Component.translatable("fishmod.pv.chat.click_pv", name)
		return (style ?: Style.EMPTY)
			.withClickEvent(ClickEvent.RunCommand("/pv $name"))
			.withHoverEvent(HoverEvent.ShowText(tip))
	}
}
