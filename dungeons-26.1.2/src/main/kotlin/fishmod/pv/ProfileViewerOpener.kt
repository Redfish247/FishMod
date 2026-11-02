/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv

import fishmod.compat.showScreen
import fishmod.pv.gui.ProfileViewerScreen
import fishmod.pv.gui.nav.PvSubTab
import fishmod.pv.gui.nav.PvTab
import fishmod.utils.config.values.FishSettings
import java.util.Locale
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Style

object ProfileViewerOpener {
	@Volatile
	private var pendingName: String? = null
	@Volatile
	private var pendingTab: PvTab? = null
	@Volatile
	private var pendingSub: PvSubTab? = null
	@Volatile
	private var openDelayTicks = 0

	/**
	 * Chat clicks on a [Style]: Hypixel profile hover/click, or our `/pv`
	 * [ClickEvent.RunCommand]. Handles SuggestCommand / Custom / hover-only names
	 * that never reach [tryHandleChatCommand].
	 */
	@JvmStatic
	fun tryHandleStyle(style: Style?): Boolean {
		if (style == null || !FishSettings.profileViewerEnabled) {
			return false
		}
		val hypixelName = ChatClickProcessor.usernameFromHypixelStyle(style)
		if (hypixelName != null) {
			open(hypixelName)
			return true
		}
		val click = style.clickEvent
		if (click is ClickEvent.RunCommand) {
			return tryHandleChatCommand(click.command())
		}
		return false
	}

	/**
	 * Chat `RunCommand` clicks go to the server via `sendUnattendedCommand`.
	 * Our `/pv` is a Fabric client command, so intercept here instead.
	 *
	 * @return true if this was a pv command and was handled client-side
	 */
	@JvmStatic
	fun tryHandleChatCommand(command: String?): Boolean {
		if (command.isNullOrBlank() || !FishSettings.profileViewerEnabled) {
			return false
		}
		val trimmed = command.trim().removePrefix("/")
		val lower = trimmed.lowercase(Locale.ROOT)
		if (lower == "pv") {
			openSelfOr(null)
			return true
		}
		if (lower.startsWith("pv ")) {
			handleTypedArg(trimmed.substring(3).trim())
			return true
		}
		// Hypixel name clicks stay on the original Component (keeps chat colors).
		// Open the profile viewer instead of forwarding SocialOptions / viewprofile to the server.
		val hypixelName = ChatClickProcessor.usernameFromCommand(trimmed)
		if (hypixelName != null) {
			open(hypixelName)
			return true
		}
		return false
	}

	/**
	 * Resolves `/pv` args:
	 * - `/pv` or blank -> self, Home
	 * - `/pv <name>` -> that player, Home
	 * - `/pv <name> <page>` -> that player on that page
	 *
	 * The first token is always a player name so usernames that match page aliases still work.
	 */
	@JvmStatic
	fun handleTypedArg(raw: String?) {
		if (raw.isNullOrBlank()) {
			openSelfOr(null)
			return
		}
		val parts = raw.trim().split(Regex("\\s+"))
		if (parts.isEmpty() || parts[0].isEmpty()) {
			openSelfOr(null)
			return
		}
		val name = parts[0]
		if (parts.size == 1) {
			openSelfOr(name)
			return
		}
		val route = routeFor(parts[1])
		openSelfOr(name, route.tab, route.sub)
	}

	/** Unknown page tokens fall back to Home. */
	@JvmStatic
	fun routeFor(page: String?): PvTab.Route = PvTab.routeFromCommandArg(page) ?: PvTab.Route(PvTab.HOME, null)

	@JvmStatic
	@JvmOverloads
	fun openSelfOr(name: String?, tab: PvTab? = null, sub: PvSubTab? = null) {
		val client = Minecraft.getInstance() ?: return
		val player = client.player ?: return
		val target = if (name.isNullOrBlank()) player.gameProfile.name() else name.trim()
		open(target, tab, sub)
	}

	@JvmStatic
	@JvmOverloads
	fun open(name: String?, tab: PvTab? = null, sub: PvSubTab? = null) {
		val client = Minecraft.getInstance()
		if (client == null || !FishSettings.profileViewerEnabled) {
			return
		}
		pendingName = if (name.isNullOrBlank()) client.player?.gameProfile?.name() else name.trim()
		pendingTab = tab
		pendingSub = sub
		// Defer past chat close so ChatScreen does not immediately replace us.
		openDelayTicks = 2
	}

	@JvmStatic
	fun tick(client: Minecraft?) {
		if (openDelayTicks <= 0 || pendingName == null || client == null) {
			return
		}
		openDelayTicks--
		if (openDelayTicks > 0) {
			return
		}
		val name = pendingName
		val tab = pendingTab ?: PvTab.HOME
		val sub = pendingSub
		pendingName = null
		pendingTab = null
		pendingSub = null
		if (name.isNullOrBlank()) {
			return
		}
		client.showScreen(ProfileViewerScreen(name, tab, sub))
	}
}
