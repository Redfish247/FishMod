/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui

import com.mojang.authlib.GameProfile
import fishmod.compat.curScreen
import fishmod.compat.showScreen
import fishmod.pv.ProfileViewer
import fishmod.pv.api.HypixelApiClient
import fishmod.pv.api.ProfileFetcher
import fishmod.pv.data.FormatUtil
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import fishmod.pv.gui.nav.IconButtonBar
import java.util.Locale
import java.util.UUID
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile

/** Profile footer menu, coop flyout, and profile-switch hit testing. */
class ProfileSelectorController(private val screen: ProfileViewerScreen) {
	private var cachedProfileFooter = ""
	private var cachedProfileFooterName = ""
	private var profileChoices: List<ProfileFetcher.ProfileChoice> = listOf()
	private var profileMenuOpen = false
	private var profileMenuHoverChoice: ProfileFetcher.ProfileChoice? = null
	private var profileCoopFlyoutScroll = 0
	private val coopMemberHits = ArrayList<CoopMemberHit>()
	// Per-screen head stacks so the flyout doesn't rebuild one per member every frame.
	private val headCache = HashMap<UUID, ItemStack>()
	private var profileFooterX = 0
	private var profileFooterY = 0
	private var profileFooterW = 0
	private var profileFooterH = 0

	fun choices(): List<ProfileFetcher.ProfileChoice> = profileChoices

	fun menuOpen(): Boolean = profileMenuOpen

	fun footerX(): Int = profileFooterX

	fun footerY(): Int = profileFooterY

	fun footerW(): Int = profileFooterW

	fun footerH(): Int = profileFooterH

	fun clearFooterNameCache() {
		cachedProfileFooterName = ""
	}

	fun applyChoices(choices: List<ProfileFetcher.ProfileChoice>?, viewedUndashed: String?, playerName: String?) {
		if (!choices.isNullOrEmpty()) {
			profileChoices = choices
			headCache.clear()
			ProfileFetcher.warmCoopMemberNames(profileChoices, viewedUndashed, playerName) {
				val client = Minecraft.getInstance()
				client?.execute {
					if (client.curScreen === screen) {
						profileCoopFlyoutScroll = 0
					}
				}
			}
		}
		closeMenu()
	}

	fun closeMenu() {
		profileMenuOpen = false
		profileMenuHoverChoice = null
		profileCoopFlyoutScroll = 0
		coopMemberHits.clear()
	}

	fun renderFooter(graphics: GuiGraphicsExtractor, font: Font, panelX: Int, panelY: Int, panelW: Int, panelH: Int, mouseX: Int, mouseY: Int) {
		val profileName = screen.homePage().profileName()
		val selectedChoice = selectedProfileChoice()
		val footerKey = profileName + "|" + (selectedChoice?.gameMode ?: "") + "|" + profileMenuOpen
		if (footerKey != cachedProfileFooterName) {
			cachedProfileFooterName = footerKey
			cachedProfileFooter = Component.translatable("fishmod.pv.home.profile", profileName).string
		}
		profileFooterX = panelX + 2
		profileFooterY = panelY + panelH + 3
		profileFooterH = font.lineHeight
		val footerBadge = profileModeBadge(selectedChoice)
		val footerArrow = if (profileChoices.size > 1) (if (profileMenuOpen) " ▲" else " ▼") else ""
		val footerTextW = font.width(cachedProfileFooter)
		val footerBadgeW = if (footerBadge.isEmpty) 0 else 2 + PROFILE_BADGE_SIZE
		val footerArrowW = if (footerArrow.isEmpty()) 0 else font.width(footerArrow)
		profileFooterW = footerTextW + footerBadgeW + footerArrowW
		val hovered = footerHover(mouseX, mouseY)
		val footerColor = if (hovered && profileChoices.size > 1) PvDraw.COLOR_ACCENT else PvDraw.COLOR_MUTED
		PvDraw.text(graphics, font, cachedProfileFooter, profileFooterX, profileFooterY, footerColor)
		var footerX = profileFooterX + footerTextW
		if (!footerBadge.isEmpty) {
			footerX += 2
			val badgeY = profileFooterY + maxOf(0, (profileFooterH - PROFILE_BADGE_SIZE) / 2)
			ItemIcons.drawItemIcon(graphics, footerBadge, footerX, badgeY, PROFILE_BADGE_SIZE)
			footerX += PROFILE_BADGE_SIZE
		}
		if (footerArrow.isNotEmpty()) {
			PvDraw.text(graphics, font, footerArrow, footerX, profileFooterY, footerColor)
		}
		if (profileMenuOpen) {
			updateProfileMenuHover(font, mouseX, mouseY)
			drawProfileMenu(graphics, font, mouseX, mouseY)
			drawProfileCoopFlyout(graphics, font, mouseX, mouseY)
		} else {
			profileMenuHoverChoice = null
		}
	}

	fun mouseClicked(mx: Double, my: Double): Boolean = clickProfileFooter(mx, my)

	fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
		val hoverChoice = profileMenuHoverChoice
		if (!profileMenuOpen || hoverChoice == null || !profileCoopFlyoutHover(mouseX.toInt(), mouseY.toInt(), hoverChoice)) {
			return false
		}
		val font = screen.font()
		val contentH = profileCoopFlyoutHeight(font, hoverChoice)
		val layout = profileMenuLayout(font)
		val visibleH = minOf(contentH, visibleScreenH() - layout.menuY - 4)
		val maxScroll = maxOf(0, contentH - visibleH)
		profileCoopFlyoutScroll = maxOf(0, minOf(maxScroll, profileCoopFlyoutScroll - Math.round(scrollY * 12).toInt()))
		return true
	}

	fun tooltip(mouseX: Int, mouseY: Int, footerHover: Boolean): List<PvTooltip.Line>? =
		profileSelectorTooltip(mouseX, mouseY, footerHover)

	fun combinedHover(mouseX: Int, mouseY: Int): Boolean = profileMenuCombinedHover(mouseX, mouseY)

	fun footerHover(mouseX: Int, mouseY: Int): Boolean =
		mouseX >= profileFooterX && mouseX < profileFooterX + profileFooterW &&
			mouseY >= profileFooterY && mouseY < profileFooterY + profileFooterH

	private class ProfileMenuLayout(val menuX: Int, val menuY: Int, val menuW: Int, val menuH: Int, val lineH: Int)

	private fun profileMenuLayout(font: Font): ProfileMenuLayout {
		val lineH = font.lineHeight + 4
		var menuW = PROFILE_MENU_PAD * 2 + PROFILE_MENU_COUNTS_W
		for (choice in profileChoices) {
			menuW = maxOf(menuW, profileChoiceLineWidth(font, choice) + PROFILE_MENU_PAD * 2)
		}
		menuW = maxOf(menuW, 96)
		val menuH = profileChoices.size * lineH + 4
		return ProfileMenuLayout(profileFooterX, profileFooterY + profileFooterH + 2, menuW, menuH, lineH)
	}

	private fun profileMenuChoiceAt(font: Font, mouseX: Int, mouseY: Int): ProfileFetcher.ProfileChoice? {
		val layout = profileMenuLayout(font)
		if (mouseX < layout.menuX || mouseX >= layout.menuX + layout.menuW ||
			mouseY < layout.menuY || mouseY >= layout.menuY + layout.menuH
		) {
			return null
		}
		var cy = layout.menuY + 2
		for (choice in profileChoices) {
			if (mouseY >= cy && mouseY < cy + layout.lineH) {
				return choice
			}
			cy += layout.lineH
		}
		return null
	}

	private fun updateProfileMenuHover(font: Font, mouseX: Int, mouseY: Int) {
		val rowHover = profileMenuChoiceAt(font, mouseX, mouseY)
		if (rowHover != null) {
			if (rowHover !== profileMenuHoverChoice) {
				profileCoopFlyoutScroll = 0
			}
			profileMenuHoverChoice = rowHover
			return
		}
		val current = profileMenuHoverChoice
		if (current != null && profileCoopFlyoutHover(mouseX, mouseY, current)) {
			return
		}
		profileMenuHoverChoice = null
		profileCoopFlyoutScroll = 0
	}

	private fun profileCoopFlyoutX(layout: ProfileMenuLayout): Int =
		layout.menuX + layout.menuW + 2 - PROFILE_COOP_FLYOUT_BRIDGE

	private fun profileCoopFlyoutHeight(font: Font, choice: ProfileFetcher.ProfileChoice?): Int {
		if (choice == null) {
			return 0
		}
		var cy = PROFILE_COOP_FLYOUT_PAD
		cy += font.lineHeight + 3
		if (choice.createdAtMs > 0L) {
			if (FormatUtil.prettyDate(choice.createdAtMs).isNotBlank()) {
				cy += font.lineHeight + 1
			}
			if (FormatUtil.ago(choice.createdAtMs).isNotBlank()) {
				cy += font.lineHeight + 1
			}
		}
		cy += 2 + 1 + 4
		val current = choice.coop.currentMembers
		val former = choice.coop.formerMembers
		if (current.isEmpty() && former.isEmpty()) {
			cy += font.lineHeight
		} else {
			if (current.isNotEmpty()) {
				cy += font.lineHeight + 4
				cy += current.size * PROFILE_COOP_FLYOUT_ROW
				if (former.isNotEmpty()) {
					cy += 2
				}
			}
			if (former.isNotEmpty()) {
				cy += font.lineHeight + 4
				cy += former.size * PROFILE_COOP_FLYOUT_ROW
			}
		}
		return cy + PROFILE_COOP_FLYOUT_PAD
	}

	private fun profileCoopFlyoutHover(mouseX: Int, mouseY: Int, choice: ProfileFetcher.ProfileChoice?): Boolean {
		if (choice == null) {
			return false
		}
		val font = screen.font()
		val layout = profileMenuLayout(font)
		val flyoutX = profileCoopFlyoutX(layout)
		val flyoutY = layout.menuY
		val flyoutH = minOf(profileCoopFlyoutHeight(font, choice), visibleScreenH() - flyoutY - 4)
		return mouseX >= flyoutX && mouseX < flyoutX + PROFILE_COOP_FLYOUT_W + PROFILE_COOP_FLYOUT_BRIDGE &&
			mouseY >= flyoutY && mouseY < flyoutY + flyoutH
	}

	private fun profileMenuCombinedHover(mouseX: Int, mouseY: Int): Boolean {
		val layout = profileMenuLayout(screen.font())
		val inMenu = mouseX >= layout.menuX && mouseX < layout.menuX + layout.menuW &&
			mouseY >= layout.menuY && mouseY < layout.menuY + layout.menuH
		if (inMenu) {
			return true
		}
		val current = profileMenuHoverChoice
		return current != null && profileCoopFlyoutHover(mouseX, mouseY, current)
	}

	private fun drawProfileMenu(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int) {
		if (profileChoices.size <= 1) {
			return
		}
		val layout = profileMenuLayout(font)
		PvDraw.popupSurface(g, layout.menuX, layout.menuY, layout.menuW, layout.menuH)
		var cy = layout.menuY + 2
		for (choice in profileChoices) {
			val hover = choice === profileMenuHoverChoice || profileMenuChoiceAt(font, mouseX, mouseY) === choice
			if (hover || choice.selected) {
				PvDraw.fill(g, layout.menuX + 1, cy, layout.menuW - 2, layout.lineH, if (hover) 0x33FFFFFF else 0x22AA88FF)
			}
			PvDraw.text(
				g, font, choice.cuteName,
				layout.menuX + PROFILE_MENU_PAD, cy + 2,
				if (choice.selected) PvDraw.COLOR_ACCENT else PvDraw.COLOR_TEXT
			)
			val badge = profileModeBadge(choice)
			if (!badge.isEmpty) {
				val badgeX = layout.menuX + PROFILE_MENU_PAD + font.width(choice.cuteName) + 3
				val badgeY = cy + 1 + maxOf(0, (layout.lineH - 2 - PROFILE_BADGE_SIZE) / 2)
				ItemIcons.drawItemIcon(g, badge, badgeX, badgeY, PROFILE_BADGE_SIZE)
			}
			cy += layout.lineH
		}
	}

	/** Screen height in the viewer's scaled coordinates, so popups use the room that is actually there. */
	private fun visibleScreenH(): Int {
		val virtual = IconButtonBar.virtualHeight()
		return if (virtual > 0) virtual else screen.height
	}

	private fun drawProfileCoopFlyout(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int) {
		coopMemberHits.clear()
		val choice = profileMenuHoverChoice ?: return
		val layout = profileMenuLayout(font)
		val flyoutX = profileCoopFlyoutX(layout) + PROFILE_COOP_FLYOUT_BRIDGE
		val flyoutY = layout.menuY
		val flyoutW = PROFILE_COOP_FLYOUT_W
		val contentH = profileCoopFlyoutHeight(font, choice)
		val flyoutH = minOf(contentH, visibleScreenH() - flyoutY - 4)
		PvDraw.popupSurface(g, flyoutX, flyoutY, flyoutW, flyoutH)

		val innerX = flyoutX + PROFILE_COOP_FLYOUT_PAD
		val innerW = flyoutW - PROFILE_COOP_FLYOUT_PAD * 2
		var cy = flyoutY + PROFILE_COOP_FLYOUT_PAD - profileCoopFlyoutScroll

		g.enableScissor(flyoutX + 1, flyoutY + 1, flyoutX + flyoutW - 1, flyoutY + flyoutH - 1)
		PvDraw.text(g, font, choice.cuteName, innerX, cy, PvDraw.COLOR_TEXT)
		val badge = profileModeBadge(choice)
		if (!badge.isEmpty) {
			val badgeX = innerX + font.width(choice.cuteName) + 3
			ItemIcons.drawItemIcon(g, badge, badgeX, cy - 1, PROFILE_BADGE_SIZE)
		}
		cy += font.lineHeight + 3
		if (choice.createdAtMs > 0L) {
			val date = FormatUtil.prettyDate(choice.createdAtMs)
			val age = FormatUtil.ago(choice.createdAtMs)
			if (date.isNotBlank()) {
				drawFlyoutMetaRow(g, font, "Created", date, PvDraw.COLOR_TEXT, innerX, innerW, cy)
				cy += font.lineHeight + 1
			}
			if (age.isNotBlank()) {
				drawFlyoutMetaRow(g, font, "Age", age, PvDraw.COLOR_GOLD, innerX, innerW, cy)
				cy += font.lineHeight + 1
			}
		}
		cy += 2
		PvDraw.fill(g, innerX, cy, innerW, 1, PvDraw.COLOR_DIVIDER)
		cy += 4

		val current = choice.coop.currentMembers
		val former = choice.coop.formerMembers
		if (current.isEmpty() && former.isEmpty()) {
			PvDraw.text(g, font, "Solo profile", innerX, cy, PvDraw.COLOR_MUTED)
			g.disableScissor()
			return
		}
		if (current.isNotEmpty()) {
			PvDraw.text(g, font, "Current members", innerX, cy, COLOR_COOP_CURRENT)
			drawJoinedHeader(g, font, current, innerX, innerW, cy)
			cy += font.lineHeight + 4
			for (member in current) {
				drawCoopFlyoutMember(g, font, member, innerX, innerW, cy, false, mouseX, mouseY, flyoutY, flyoutH)
				cy += PROFILE_COOP_FLYOUT_ROW
			}
			cy += 2
		}
		if (former.isNotEmpty()) {
			PvDraw.text(g, font, "Former members", innerX, cy, COLOR_COOP_FORMER)
			drawJoinedHeader(g, font, former, innerX, innerW, cy)
			cy += font.lineHeight + 4
			for (member in former) {
				drawCoopFlyoutMember(g, font, member, innerX, innerW, cy, true, mouseX, mouseY, flyoutY, flyoutH)
				cy += PROFILE_COOP_FLYOUT_ROW
			}
		}
		g.disableScissor()
	}

	private fun drawFlyoutMetaRow(
		g: GuiGraphicsExtractor,
		font: Font,
		label: String,
		value: String,
		valueColor: Int,
		x: Int,
		w: Int,
		y: Int
	) {
		PvDraw.text(g, font, label, x, y, PvDraw.COLOR_MUTED)
		PvDraw.text(g, font, value, x + w - font.width(value), y, valueColor)
	}

	private fun drawCoopFlyoutMember(
		g: GuiGraphicsExtractor,
		font: Font,
		member: ProfileFetcher.CoopMemberRef,
		x: Int,
		w: Int,
		y: Int,
		former: Boolean,
		mouseX: Int,
		mouseY: Int,
		flyoutY: Int,
		flyoutH: Int
	) {
		// Scissoring hides pixels, but still extracting a head can resolve its profile online.
		if (y + PROFILE_COOP_FLYOUT_ROW <= flyoutY || y >= flyoutY + flyoutH) return
		val resolved = ProfileFetcher.coopNameResolved(member)
		val hover = resolved &&
			mouseX >= x && mouseX < x + w &&
			mouseY >= y && mouseY < y + PROFILE_COOP_FLYOUT_ROW
		if (hover) {
			PvDraw.fill(g, x - 2, y - 1, w + 4, PROFILE_COOP_FLYOUT_ROW, 0x33FFFFFF)
		}
		val uuid = HypixelApiClient.parseUndashedUuid(member.uuid)
		if (uuid != null) {
			val head = headCache.getOrPut(uuid) { coopMemberHead(uuid) }
			ItemIcons.drawItemIcon(g, head, x, y - 1, PROFILE_COOP_ICON)
		}
		val name = ProfileFetcher.coopMemberDisplayName(member)
		val nameColor = when {
			!resolved -> PvDraw.COLOR_MUTED
			former -> COLOR_COOP_FORMER
			else -> COLOR_COOP_CURRENT
		}
		val nameX = x + PROFILE_COOP_ICON + 4
		var maxW = w - PROFILE_COOP_ICON - 4
		if (member.joinedMs > 0L) {
			val joined = FormatUtil.prettySpan(System.currentTimeMillis() - member.joinedMs)
			val joinedW = font.width(joined)
			PvDraw.text(g, font, joined, x + w - joinedW, y, PvDraw.COLOR_MUTED)
			maxW -= joinedW + 4
		}
		PvDraw.text(g, font, font.plainSubstrByWidth(name, maxW), nameX, y, nameColor)
		if (resolved) {
			coopMemberHits.add(CoopMemberHit(x, y, w, PROFILE_COOP_FLYOUT_ROW, name, profileMenuHoverChoice?.profileId))
		}
	}

	private fun profileChoiceLineWidth(font: Font, choice: ProfileFetcher.ProfileChoice): Int {
		var w = font.width(choice.cuteName)
		if (!profileModeBadge(choice).isEmpty) {
			w += 3 + PROFILE_BADGE_SIZE
		}
		return w
	}

	private fun selectedProfileChoice(): ProfileFetcher.ProfileChoice? =
		profileChoices.firstOrNull { it.selected } ?: profileChoices.firstOrNull()

	private fun profileSelectorTooltip(mouseX: Int, mouseY: Int, footerHover: Boolean): List<PvTooltip.Line>? {
		if (profileMenuOpen && profileChoices.size > 1) {
			return profileMenuChoiceAt(screen.font(), mouseX, mouseY)?.let { profileCreatedTooltip(it) }
		}
		if (footerHover) {
			return profileCreatedTooltip(selectedProfileChoice())
		}
		return null
	}

	private fun clickProfileFooter(mx: Double, my: Double): Boolean {
		if (profileChoices.size <= 1) {
			return false
		}
		if (mx >= profileFooterX && mx < profileFooterX + profileFooterW &&
			my >= profileFooterY && my < profileFooterY + profileFooterH
		) {
			profileMenuOpen = !profileMenuOpen
			if (!profileMenuOpen) {
				profileMenuHoverChoice = null
			}
			return true
		}
		if (!profileMenuOpen) {
			return false
		}
		if (!profileMenuCombinedHover(mx.toInt(), my.toInt())) {
			profileMenuOpen = false
			profileMenuHoverChoice = null
			coopMemberHits.clear()
			return true
		}
		val choice = profileMenuChoiceAt(screen.font(), mx.toInt(), my.toInt()) ?: return clickCoopFlyoutMember(mx, my)
		profileMenuOpen = false
		profileMenuHoverChoice = null
		coopMemberHits.clear()
		if (!choice.selected) {
			switchProfile(choice.profileId)
		}
		return true
	}

	private fun clickCoopFlyoutMember(mx: Double, my: Double): Boolean {
		val hit = coopMemberHits.firstOrNull { mx >= it.x && mx < it.x + it.w && my >= it.y && my < it.y + it.h }
		if (hit != null) {
			openCoopMemberProfile(hit.name, hit.profileId)
		}
		return true
	}

	private fun openCoopMemberProfile(name: String?, profileId: String?) {
		if (name.isNullOrBlank()) {
			return
		}
		val current = screen.homePage().playerName()
		if (current != null && current.equals(name, ignoreCase = true)) {
			if (profileId != null && profileId != screen.profileId()) {
				profileMenuOpen = false
				profileMenuHoverChoice = null
				coopMemberHits.clear()
				switchProfile(profileId)
			}
			return
		}
		Minecraft.getInstance().showScreen(screen.forCoopMember(name, profileId))
	}

	private class CoopMemberHit(val x: Int, val y: Int, val w: Int, val h: Int, val name: String, val profileId: String?)

	private fun switchProfile(nextProfileId: String?) {
		val root = screen.profilesRoot()
		val uuid = screen.playerUuid()
		if (nextProfileId.isNullOrBlank() || root == null || uuid == null) {
			return
		}
		if (nextProfileId == screen.profileId()) {
			return
		}
		val name = screen.homePage().playerName()
		val generation = screen.bumpLoadGeneration()
		ProfileFetcher.switchToProfile(name, uuid, root, nextProfileId) { updated ->
			val client = Minecraft.getInstance() ?: return@switchToProfile
			client.execute {
				if (client.curScreen !== screen || !screen.isLoadGeneration(generation)) {
					return@execute
				}
				if (updated == null || !updated.ok()) {
					return@execute
				}
				screen.applyLoadedProfile(updated)
			}
		}.whenComplete { loaded, error ->
			val client = Minecraft.getInstance() ?: return@whenComplete
			client.execute {
				if (client.curScreen !== screen || !screen.isLoadGeneration(generation)) {
					return@execute
				}
				if (error != null || loaded == null || !loaded.ok()) {
					ProfileViewer.LOGGER.warn("Profile switch failed for {} -> {}", name, nextProfileId, error)
					// Keep prior Home visible; do not force loading-egg for a failed switch.
					return@execute
				}
				screen.applyLoadedProfile(loaded)
				screen.setDataReady(true)
			}
		}
	}

	companion object {
		private const val PROFILE_MENU_PAD = 6
		private const val PROFILE_MENU_COUNTS_W = 56
		const val PROFILE_BADGE_SIZE = 10
		private const val PROFILE_COOP_ICON = 8
		private const val PROFILE_COOP_FLYOUT_W = 168
		private const val PROFILE_COOP_FLYOUT_PAD = 6
		private const val PROFILE_COOP_FLYOUT_ROW = 14
		private const val PROFILE_COOP_FLYOUT_BRIDGE = 6
		private const val COLOR_COOP_CURRENT = 0xFF55DD55.toInt()
		private const val COLOR_COOP_FORMER = 0xFFFF6666.toInt()

		@JvmStatic
		fun coopMemberHead(uuid: UUID?): ItemStack {
			val head = ItemStack(Items.PLAYER_HEAD)
			if (uuid == null) {
				return head
			}
			try {
				head.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(uuid))
			} catch (_: Throwable) {
				try {
					head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(GameProfile(uuid, "Player")))
				} catch (_: Throwable) {
				}
			}
			return head
		}

		private fun drawJoinedHeader(
			g: GuiGraphicsExtractor,
			font: Font,
			members: List<ProfileFetcher.CoopMemberRef>,
			x: Int,
			w: Int,
			y: Int
		) {
			if (members.none { it.joinedMs > 0L }) {
				return
			}
			val label = "Joined"
			PvDraw.text(g, font, label, x + w - font.width(label), y, PvDraw.COLOR_MUTED)
		}

		private fun profileModeBadge(choice: ProfileFetcher.ProfileChoice?): ItemStack {
			if (choice == null || choice.gameMode.isBlank()) {
				return ItemStack.EMPTY
			}
			return when (choice.gameMode.lowercase(Locale.ROOT)) {
				"ironman" -> ItemStack(Items.SHIELD)
				"bingo" -> {
					val card = SkyBlockItemFactory.iconStack("BINGO_CARD")
					if (card == null || card.isEmpty) ItemStack(Items.FILLED_MAP) else card
				}
				"stranded", "island" -> ItemStack(Items.OAK_SAPLING)
				else -> ItemStack.EMPTY
			}
		}

		private fun profileCreatedTooltip(choice: ProfileFetcher.ProfileChoice?): List<PvTooltip.Line>? {
			if (choice == null || choice.createdAtMs <= 0L) {
				return null
			}
			val date = FormatUtil.prettyDate(choice.createdAtMs)
			val age = FormatUtil.ago(choice.createdAtMs)
			val lines = ArrayList<PvTooltip.Line>(4)
			lines.add(PvTooltip.Line.title(choice.cuteName, PvDraw.COLOR_TEXT))
			lines.add(PvTooltip.Line.divider())
			if (date.isNotBlank()) {
				lines.add(PvTooltip.Line.row("Created", PvDraw.COLOR_MUTED, date, PvDraw.COLOR_TEXT))
			}
			if (age.isNotBlank()) {
				lines.add(PvTooltip.Line.row("Age", PvDraw.COLOR_MUTED, age, PvDraw.COLOR_GOLD))
			}
			val mode = choice.gameModeLabel()
			if (mode.isNotBlank()) {
				lines.add(PvTooltip.Line.row("Mode", PvDraw.COLOR_MUTED, mode, PvDraw.COLOR_GOLD))
			}
			return lines
		}
	}
}
