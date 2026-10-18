/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Opens {@code /pv} from chat the same way SkyBlock Profile Viewer does:
 *
 * <ol>
 *   <li>Remap existing Hypixel {@code /socialoptions} / {@code /viewprofile} / social-menu
 *       clicks to {@code /pv &lt;name&gt;} so they work in SkyBlock, including guild chat.</li>
 *   <li>Optionally make whole {@code Party|Guild|Officer|Co-op > Name: ...} lines clickable.</li>
 * </ol>
 *
 * <p>Never invents clicks from arbitrary text (that falsely matched mod banners like
 * {@code [Detexturify] Update available: ...}).
 *
 * <p>Wired via Fabric {@code MODIFY_GAME}.
 */
public final class ChatClickProcessor {
	private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{1,16}");
	private static final Pattern UUID_PATTERN = Pattern.compile(
		"(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"
	);
	/** Same hover Hypixel puts on SocialOptions / viewprofile name spans. */
	private static final Pattern VIEW_PROFILE_HOVER = Pattern.compile(
		"(?i)\\bview\\s+(?:\\[[^\\]]*\\]\\s*)?([A-Za-z0-9_]{1,16})['\u2019\u02BC\u2018]?s\\s+profile\\b"
	);
	/**
	 * SkyBlock PV {@code otherChatRegex}: channel chat only.
	 * {@code Party > [MVP+] Name [Elite]: hello}
	 */
	private static final Pattern CHANNEL_CHAT = Pattern.compile(
		"(?i)^(?:Party|Guild|Officer|Co-op)\\b[^>]{0,8}> (?:[^\\[\\s]{1,3}\\s+)?(?:\\[[^\\]]*\\]\\s*)?([A-Za-z0-9_]{1,16})(?:\\s*\\[[^\\]]*\\])?: .+"
	);

	private ChatClickProcessor() {
	}

	public static Component process(Component component) {
		if (component == null || !fishmod.utils.config.values.FishSettings.profileViewerEnabled) {
			return null;
		}
		String channelName = usernameFromChannelChat(stripLegacy(component.getString()));
		Component remapped = remapTree(component, channelName);
		if (channelName != null && !alreadyHasPvClick(remapped)) {
			MutableComponent copy = remapped.copy();
			copy.setStyle(pvStyle(copy.getStyle(), channelName));
			return copy;
		}
		return remapped;
	}

	private static String usernameFromChannelChat(String plain) {
		if (plain == null || plain.isBlank()) {
			return null;
		}
		Matcher matcher = CHANNEL_CHAT.matcher(plain.trim());
		if (!matcher.matches()) {
			return null;
		}
		String username = matcher.group(1);
		if (username == null || !PLAYER_NAME_PATTERN.matcher(username).matches()) {
			return null;
		}
		return username;
	}

	private static Component remapTree(Component component, String fallbackName) {
		List<Component> siblings = component.getSiblings();
		Style remappedStyle = remapStyle(component.getStyle(), fallbackName, component.plainCopy().getString());
		boolean styleChanged = remappedStyle != component.getStyle();

		MutableComponent output = component.copy();
		output.getSiblings().clear();
		if (styleChanged) {
			output.setStyle(remappedStyle);
		}

		boolean childChanged = false;
		for (Component sibling : siblings) {
			Component next = remapTree(sibling, fallbackName);
			output.append(next);
			if (next != sibling) {
				childChanged = true;
			}
		}

		if (!styleChanged && !childChanged) {
			return component;
		}
		return output;
	}

	private static Style remapStyle(Style style, String fallbackName, String spanText) {
		if (style == null) {
			return style;
		}
		ClickEvent click = style.getClickEvent();
		String name = usernameFromHypixelStyle(style);
		if (name == null) {
			name = fallbackName;
		}
		if (name == null && click != null) {
			name = usernameFromSpanText(spanText);
		}
		if (name == null || !PLAYER_NAME_PATTERN.matcher(name).matches()) {
			return style;
		}
		if (isPvClick(click)) {
			return style;
		}
		if (isSocialOrProfileClick(click) || looksLikeSocialHover(hoverPlain(style))) {
			return pvStyle(style, name);
		}
		if (fallbackName != null && click != null && shouldReplaceChannelClick(click)) {
			return pvStyle(style, name);
		}
		return style;
	}

	private static boolean shouldReplaceChannelClick(ClickEvent click) {
		if (click instanceof ClickEvent.OpenUrl
			|| click instanceof ClickEvent.OpenFile
			|| click instanceof ClickEvent.CopyToClipboard) {
			return false;
		}
		return isSocialOrProfileClick(click);
	}

	private static boolean isSocialOrProfileClick(ClickEvent click) {
		if (click == null) {
			return false;
		}
		if (click instanceof ClickEvent.RunCommand run) {
			return isHypixelProfileCommand(run.command());
		}
		if (click instanceof ClickEvent.SuggestCommand suggest) {
			return isHypixelProfileCommand(suggest.command());
		}
		String text = click.toString().toLowerCase(Locale.ROOT);
		return text.contains("socialoptions")
			|| text.contains("viewprofile")
			|| text.contains("social_menu")
			|| text.contains("socialmenu")
			|| text.contains("social menu");
	}

	private static boolean looksLikeSocialHover(String hoverText) {
		if (hoverText == null || hoverText.isBlank()) {
			return false;
		}
		String lower = hoverText.toLowerCase(Locale.ROOT);
		return lower.contains("social menu")
			|| lower.contains("viewprofile")
			|| VIEW_PROFILE_HOVER.matcher(hoverText).find()
			|| (lower.contains("view ") && lower.contains("profile"));
	}

	private static boolean alreadyHasPvClick(Component component) {
		return isPvClick(component.getStyle().getClickEvent());
	}

	private static boolean isPvClick(ClickEvent click) {
		if (!(click instanceof ClickEvent.RunCommand run)) {
			return false;
		}
		String cmd = normalizeCommand(run.command());
		return cmd != null && (cmd.equals("pv") || cmd.startsWith("pv ") || cmd.startsWith("betterpv pv"));
	}

	private static boolean isHypixelProfileCommand(String command) {
		if (command == null) {
			return false;
		}
		String lower = command.toLowerCase(Locale.ROOT);
		return lower.startsWith("/socialoptions") || lower.startsWith("socialoptions")
			|| lower.startsWith("/viewprofile") || lower.startsWith("viewprofile");
	}

	private static String hoverPlain(Style style) {
		if (style == null) {
			return null;
		}
		HoverEvent hover = style.getHoverEvent();
		if (hover instanceof HoverEvent.ShowText show) {
			return stripLegacy(show.value().getString());
		}
		return null;
	}

	// Hypixel often embeds § codes inside literal text, which getString() keeps.
	private static String stripLegacy(String text) {
		return text == null ? null : text.replaceAll("§.", "");
	}

	/**
	 * SkyBlock PV {@code getUsername}: socialoptions arg, else hover profile line.
	 */
	private static String usernameFromSocial(String command, String hoverText) {
		String fromCommand = usernameFromCommand(command);
		if (fromCommand != null) {
			return fromCommand;
		}
		if (hoverText == null || hoverText.isBlank()) {
			return null;
		}
		for (String line : hoverText.split("\\R")) {
			Matcher matcher = VIEW_PROFILE_HOVER.matcher(line);
			if (matcher.find()) {
				String name = matcher.group(1);
				if (PLAYER_NAME_PATTERN.matcher(name).matches()) {
					return name;
				}
			}
		}
		return null;
	}

	/** Used by {@link ProfileViewerOpener} when clicking an unmapped Hypixel style. */
	static String usernameFromHypixelStyle(Style style) {
		if (style == null) {
			return null;
		}
		ClickEvent click = style.getClickEvent();
		if (click instanceof ClickEvent.RunCommand run) {
			String name = usernameFromCommand(run.command());
			if (name != null) {
				return name;
			}
		}
		if (click instanceof ClickEvent.SuggestCommand suggest) {
			String name = usernameFromCommand(suggest.command());
			if (name != null) {
				return name;
			}
		}

		String hoverText = hoverPlain(style);
		if (hoverText != null) {
			String fromHover = usernameFromSocial(null, hoverText);
			if (fromHover != null) {
				return fromHover;
			}
		}
		return null;
	}

	// The clicked span is usually just the name, maybe with rank tags: "[MVP+] naplass".
	private static String usernameFromSpanText(String text) {
		if (text == null) {
			return null;
		}
		String stripped = stripLegacy(text).replaceAll("\\[[^\\]]*\\]", "").trim();
		return PLAYER_NAME_PATTERN.matcher(stripped).matches() ? stripped : null;
	}

	static String usernameFromCommand(String command) {
		if (command == null || command.isBlank()) {
			return null;
		}
		String trimmed = command.trim();
		if (trimmed.startsWith("/")) {
			trimmed = trimmed.substring(1);
		}
		String lower = trimmed.toLowerCase(Locale.ROOT);
		String rest;
		if (lower.startsWith("socialoptions")) {
			rest = trimmed.substring("socialoptions".length()).trim();
		} else if (lower.startsWith("viewprofile")) {
			rest = trimmed.substring("viewprofile".length()).trim();
		} else {
			return null;
		}
		if (rest.isEmpty()) {
			return null;
		}
		// Prefer the first non-UUID token (Hypixel sometimes sends uuid, or uuid + name).
		for (String token : rest.split("\\s+")) {
			if (token.isEmpty() || UUID_PATTERN.matcher(token).matches()) {
				continue;
			}
			if (PLAYER_NAME_PATTERN.matcher(token).matches()) {
				return token;
			}
		}
		return null;
	}

	private static String normalizeCommand(String command) {
		if (command == null) {
			return null;
		}
		String t = command.trim();
		if (t.startsWith("/")) {
			t = t.substring(1);
		}
		return t.toLowerCase(Locale.ROOT);
	}

	private static Style pvStyle(Style style, String name) {
		Component tip = Component.translatable("betterpv.chat.click_pv", name);
		Style base = style == null ? Style.EMPTY : style;
		return base
			.withClickEvent(new ClickEvent.RunCommand("/pv " + name))
			.withHoverEvent(new HoverEvent.ShowText(tip));
	}
}
