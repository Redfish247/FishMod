/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv;

import fishmod.pv.gui.ProfileViewerScreen;
import fishmod.pv.gui.nav.PvSubTab;
import fishmod.pv.gui.nav.PvTab;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;

public final class ProfileViewerOpener {
	private static volatile String pendingName;
	private static volatile PvTab pendingTab;
	private static volatile PvSubTab pendingSub;
	private static volatile int openDelayTicks;

	private ProfileViewerOpener() {
	}

	/**
	 * Chat clicks on a {@link Style}: Hypixel profile hover/click, or our {@code /pv}
	 * {@link ClickEvent.RunCommand}. Handles SuggestCommand / Custom / hover-only names
	 * that never reach {@link #tryHandleChatCommand(String)}.
	 */
	public static boolean tryHandleStyle(Style style) {
		if (style == null || !fishmod.utils.config.values.FishSettings.profileViewerEnabled) {
			return false;
		}
		String hypixelName = ChatClickProcessor.usernameFromHypixelStyle(style);
		if (hypixelName != null) {
			open(hypixelName);
			return true;
		}
		ClickEvent click = style.getClickEvent();
		if (click instanceof ClickEvent.RunCommand run) {
			return tryHandleChatCommand(run.command());
		}
		return false;
	}

	/**
	 * Chat {@code RunCommand} clicks go to the server via {@code sendUnattendedCommand}.
	 * BetterPV's {@code /pv} is a Fabric client command, so intercept here instead.
	 *
	 * @return true if this was a BetterPV pv command and was handled client-side
	 */
	public static boolean tryHandleChatCommand(String command) {
		if (command == null || command.isBlank() || !fishmod.utils.config.values.FishSettings.profileViewerEnabled) {
			return false;
		}
		String trimmed = command.trim();
		if (trimmed.startsWith("/")) {
			trimmed = trimmed.substring(1);
		}
		String lower = trimmed.toLowerCase(Locale.ROOT);
		if (lower.equals("pv")) {
			openSelfOr(null);
			return true;
		}
		if (lower.startsWith("pv ")) {
			handleTypedArg(trimmed.substring(3).trim());
			return true;
		}
		if (lower.equals("betterpv pv")) {
			openSelfOr(null);
			return true;
		}
		if (lower.startsWith("betterpv pv ")) {
			handleTypedArg(trimmed.substring("betterpv pv ".length()).trim());
			return true;
		}
		// Hypixel name clicks stay on the original Component (keeps chat colors).
		// Open BetterPV instead of forwarding SocialOptions / viewprofile to the server.
		String hypixelName = ChatClickProcessor.usernameFromCommand(trimmed);
		if (hypixelName != null) {
			open(hypixelName);
			return true;
		}
		return false;
	}

	/**
	 * Resolves {@code /pv} args:
	 * <ul>
	 *   <li>{@code /pv} or blank -> self, Home</li>
	 *   <li>{@code /pv <name>} -> that player, Home</li>
	 *   <li>{@code /pv <name> <page>} -> that player on that page</li>
	 * </ul>
	 * The first token is always a player name so usernames that match page aliases still work.
	 */
	public static void handleTypedArg(String raw) {
		if (raw == null || raw.isBlank()) {
			openSelfOr(null);
			return;
		}
		String[] parts = raw.trim().split("\\s+");
		if (parts.length == 0 || parts[0].isEmpty()) {
			openSelfOr(null);
			return;
		}
		String name = parts[0];
		if (parts.length == 1) {
			openSelfOr(name);
			return;
		}
		PvTab.Route route = routeFor(parts[1]);
		openSelfOr(name, route.tab(), route.sub());
	}

	/** Unknown page tokens fall back to Home. */
	static PvTab.Route routeFor(String page) {
		PvTab.Route route = PvTab.routeFromCommandArg(page);
		return route == null ? new PvTab.Route(PvTab.HOME, null) : route;
	}

	public static void openSelfOr(String name) {
		openSelfOr(name, null);
	}

	public static void openSelfOr(String name, PvTab tab) {
		openSelfOr(name, tab, null);
	}

	public static void openSelfOr(String name, PvTab tab, PvSubTab sub) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.player == null) {
			return;
		}
		String target = name == null || name.isBlank()
			? client.player.getGameProfile().name()
			: name.trim();
		open(target, tab, sub);
	}

	public static void open(String name) {
		open(name, null);
	}

	public static void open(String name, PvTab tab) {
		open(name, tab, null);
	}

	public static void open(String name, PvTab tab, PvSubTab sub) {
		Minecraft client = Minecraft.getInstance();
		if (client == null || !fishmod.utils.config.values.FishSettings.profileViewerEnabled) {
			return;
		}
		pendingName = name == null || name.isBlank() ? null : name.trim();
		if (pendingName == null && client.player != null) {
			pendingName = client.player.getGameProfile().name();
		}
		pendingTab = tab;
		pendingSub = sub;
		// Defer past chat close so ChatScreen does not immediately replace us.
		openDelayTicks = 2;
	}

	public static void tick(Minecraft client) {
		if (openDelayTicks <= 0 || pendingName == null || client == null) {
			return;
		}
		openDelayTicks--;
		if (openDelayTicks > 0) {
			return;
		}
		String name = pendingName;
		PvTab tab = pendingTab == null ? PvTab.HOME : pendingTab;
		PvSubTab sub = pendingSub;
		pendingName = null;
		pendingTab = null;
		pendingSub = null;
		if (name == null || name.isBlank()) {
			return;
		}
		client.setScreen(new ProfileViewerScreen(name, tab, sub));
	}
}
