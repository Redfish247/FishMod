/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import fishmod.pv.BetterPV;
import fishmod.pv.api.HypixelApiClient;
import fishmod.pv.api.ProfileFetcher;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.gui.inventories.SkyBlockItemFactory;
import fishmod.pv.gui.ItemIcons;
import fishmod.pv.gui.nav.IconButtonBar;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

/** Profile footer menu, coop flyout, and profile-switch hit testing. */
final class ProfileSelectorController {
	private static final int PROFILE_MENU_PAD = 6;
	private static final int PROFILE_MENU_COUNTS_W = 56;
	static final int PROFILE_BADGE_SIZE = 10;
	private static final int PROFILE_COOP_ICON = 8;
	private static final int PROFILE_COOP_FLYOUT_W = 168;
	private static final int PROFILE_COOP_FLYOUT_PAD = 6;
	private static final int PROFILE_COOP_FLYOUT_ROW = 14;
	private static final int PROFILE_COOP_FLYOUT_BRIDGE = 6;
	private static final int COLOR_COOP_CURRENT = 0xFF55DD55;
	private static final int COLOR_COOP_FORMER = 0xFFFF6666;

	private final ProfileViewerScreen screen;

	private String cachedProfileFooter = "";
	private String cachedProfileFooterName = "";
	private List<ProfileFetcher.ProfileChoice> profileChoices = List.of();
	private boolean profileMenuOpen;
	private ProfileFetcher.ProfileChoice profileMenuHoverChoice;
	private int profileCoopFlyoutScroll;
	private final List<CoopMemberHit> coopMemberHits = new ArrayList<>();
	// Per-screen head stacks so the flyout doesn't rebuild one per member every frame.
	private final java.util.Map<UUID, ItemStack> headCache = new java.util.HashMap<>();
	private int profileFooterX;
	private int profileFooterY;
	private int profileFooterW;
	private int profileFooterH;

	ProfileSelectorController(ProfileViewerScreen screen) {
		this.screen = screen;
	}

	List<ProfileFetcher.ProfileChoice> choices() {
		return this.profileChoices;
	}

	boolean menuOpen() {
		return this.profileMenuOpen;
	}

	int footerX() {
		return this.profileFooterX;
	}

	int footerY() {
		return this.profileFooterY;
	}

	int footerW() {
		return this.profileFooterW;
	}

	int footerH() {
		return this.profileFooterH;
	}

	void clearFooterNameCache() {
		this.cachedProfileFooterName = "";
	}

	void applyChoices(List<ProfileFetcher.ProfileChoice> choices, String viewedUndashed, String playerName) {
		if (choices != null && !choices.isEmpty()) {
			this.profileChoices = choices;
			this.headCache.clear();
			ProfileFetcher.warmCoopMemberNames(
				this.profileChoices,
				viewedUndashed,
				playerName,
				() -> {
					Minecraft client = Minecraft.getInstance();
					if (client != null) {
						client.execute(() -> {
							if (fishmod.compat.McCompatKt.getCurScreen(client) == this.screen) {
								this.profileCoopFlyoutScroll = 0;
							}
						});
					}
				}
			);
		}
		closeMenu();
	}

	void closeMenu() {
		this.profileMenuOpen = false;
		this.profileMenuHoverChoice = null;
		this.profileCoopFlyoutScroll = 0;
		this.coopMemberHits.clear();
	}

	void renderFooter(GuiGraphicsExtractor graphics, Font font, int panelX, int panelY, int panelW, int panelH, int mouseX, int mouseY) {
		String profileName = this.screen.homePage().profileName();
		ProfileFetcher.ProfileChoice selectedChoice = selectedProfileChoice();
		String footerKey = profileName + "|" + (selectedChoice == null ? "" : selectedChoice.gameMode())
			+ "|" + this.profileMenuOpen;
		if (!footerKey.equals(this.cachedProfileFooterName)) {
			this.cachedProfileFooterName = footerKey;
			this.cachedProfileFooter = Component.translatable("betterpv.home.profile", profileName).getString();
		}
		this.profileFooterX = panelX + 2;
		this.profileFooterY = panelY + panelH + 3;
		this.profileFooterH = font.lineHeight;
		ItemStack footerBadge = profileModeBadge(selectedChoice);
		String footerArrow = this.profileChoices.size() > 1
			? (this.profileMenuOpen ? " ▲" : " ▼")
			: "";
		int footerTextW = font.width(this.cachedProfileFooter);
		int footerBadgeW = footerBadge.isEmpty() ? 0 : 2 + PROFILE_BADGE_SIZE;
		int footerArrowW = footerArrow.isEmpty() ? 0 : font.width(footerArrow);
		this.profileFooterW = footerTextW + footerBadgeW + footerArrowW;
		boolean footerHover = mouseX >= this.profileFooterX && mouseX < this.profileFooterX + this.profileFooterW
			&& mouseY >= this.profileFooterY && mouseY < this.profileFooterY + this.profileFooterH;
		int footerColor = footerHover && this.profileChoices.size() > 1 ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_MUTED;
		PvDraw.text(
			graphics, font, this.cachedProfileFooter,
			this.profileFooterX, this.profileFooterY,
			footerColor
		);
		int footerX = this.profileFooterX + footerTextW;
		if (!footerBadge.isEmpty()) {
			footerX += 2;
			int badgeY = this.profileFooterY + Math.max(0, (this.profileFooterH - PROFILE_BADGE_SIZE) / 2);
			ItemIcons.drawItemIcon(graphics, footerBadge, footerX, badgeY, PROFILE_BADGE_SIZE);
			footerX += PROFILE_BADGE_SIZE;
		}
		if (!footerArrow.isEmpty()) {
			PvDraw.text(graphics, font, footerArrow, footerX, this.profileFooterY, footerColor);
		}
		if (this.profileMenuOpen) {
			updateProfileMenuHover(font, mouseX, mouseY);
			drawProfileMenu(graphics, font, mouseX, mouseY);
			drawProfileCoopFlyout(graphics, font, mouseX, mouseY);
		} else {
			this.profileMenuHoverChoice = null;
		}
	}

	boolean mouseClicked(double mx, double my) {
		return clickProfileFooter(mx, my);
	}

	boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (!this.profileMenuOpen
			|| this.profileMenuHoverChoice == null
			|| !profileCoopFlyoutHover((int) mouseX, (int) mouseY, this.profileMenuHoverChoice)) {
			return false;
		}
		Font font = this.screen.font();
		int contentH = profileCoopFlyoutHeight(font, this.profileMenuHoverChoice);
		ProfileMenuLayout layout = profileMenuLayout(font);
		int visibleH = Math.min(contentH, visibleScreenH() - layout.menuY() - 4);
		int maxScroll = Math.max(0, contentH - visibleH);
		this.profileCoopFlyoutScroll = Math.max(
			0,
			Math.min(maxScroll, this.profileCoopFlyoutScroll - (int) Math.round(scrollY * 12))
		);
		return true;
	}

	List<PvTooltip.Line> tooltip(int mouseX, int mouseY, boolean footerHover) {
		return profileSelectorTooltip(mouseX, mouseY, footerHover);
	}

	boolean combinedHover(int mouseX, int mouseY) {
		return profileMenuCombinedHover(mouseX, mouseY);
	}

	boolean footerHover(int mouseX, int mouseY) {
		return mouseX >= this.profileFooterX && mouseX < this.profileFooterX + this.profileFooterW
			&& mouseY >= this.profileFooterY && mouseY < this.profileFooterY + this.profileFooterH;
	}

	private record ProfileMenuLayout(int menuX, int menuY, int menuW, int menuH, int lineH) {
	}

	private ProfileMenuLayout profileMenuLayout(Font font) {
		int lineH = font.lineHeight + 4;
		int menuW = PROFILE_MENU_PAD * 2 + PROFILE_MENU_COUNTS_W;
		for (ProfileFetcher.ProfileChoice choice : this.profileChoices) {
			menuW = Math.max(menuW, profileChoiceLineWidth(font, choice) + PROFILE_MENU_PAD * 2);
		}
		menuW = Math.max(menuW, 96);
		int menuH = this.profileChoices.size() * lineH + 4;
		int menuX = this.profileFooterX;
		int menuY = this.profileFooterY + this.profileFooterH + 2;
		return new ProfileMenuLayout(menuX, menuY, menuW, menuH, lineH);
	}

	private ProfileFetcher.ProfileChoice profileMenuChoiceAt(Font font, int mouseX, int mouseY) {
		ProfileMenuLayout layout = profileMenuLayout(font);
		if (mouseX < layout.menuX() || mouseX >= layout.menuX() + layout.menuW()
			|| mouseY < layout.menuY() || mouseY >= layout.menuY() + layout.menuH()) {
			return null;
		}
		int cy = layout.menuY() + 2;
		for (ProfileFetcher.ProfileChoice choice : this.profileChoices) {
			if (mouseY >= cy && mouseY < cy + layout.lineH()) {
				return choice;
			}
			cy += layout.lineH();
		}
		return null;
	}

	private void updateProfileMenuHover(Font font, int mouseX, int mouseY) {
		ProfileFetcher.ProfileChoice rowHover = profileMenuChoiceAt(font, mouseX, mouseY);
		if (rowHover != null) {
			if (rowHover != this.profileMenuHoverChoice) {
				this.profileCoopFlyoutScroll = 0;
			}
			this.profileMenuHoverChoice = rowHover;
			return;
		}
		if (this.profileMenuHoverChoice != null && profileCoopFlyoutHover(mouseX, mouseY, this.profileMenuHoverChoice)) {
			return;
		}
		this.profileMenuHoverChoice = null;
		this.profileCoopFlyoutScroll = 0;
	}

	private int profileCoopFlyoutX(ProfileMenuLayout layout) {
		return layout.menuX() + layout.menuW() + 2 - PROFILE_COOP_FLYOUT_BRIDGE;
	}

	private int profileCoopFlyoutHeight(Font font, ProfileFetcher.ProfileChoice choice) {
		return profileCoopFlyoutContentBottom(font, choice);
	}

	private int profileCoopFlyoutContentBottom(Font font, ProfileFetcher.ProfileChoice choice) {
		if (choice == null) {
			return 0;
		}
		int cy = PROFILE_COOP_FLYOUT_PAD;
		cy += font.lineHeight + 3;
		if (choice.createdAtMs() > 0L) {
			if (!FormatUtil.prettyDate(choice.createdAtMs()).isBlank()) {
				cy += font.lineHeight + 1;
			}
			if (!FormatUtil.ago(choice.createdAtMs()).isBlank()) {
				cy += font.lineHeight + 1;
			}
		}
		cy += 2 + 1 + 4;
		if (choice.coop().currentMembers().isEmpty() && choice.coop().formerMembers().isEmpty()) {
			cy += font.lineHeight;
		} else {
			if (!choice.coop().currentMembers().isEmpty()) {
				cy += font.lineHeight + 4;
				cy += choice.coop().currentMembers().size() * PROFILE_COOP_FLYOUT_ROW;
				if (!choice.coop().formerMembers().isEmpty()) {
					cy += 2;
				}
			}
			if (!choice.coop().formerMembers().isEmpty()) {
				cy += font.lineHeight + 4;
				cy += choice.coop().formerMembers().size() * PROFILE_COOP_FLYOUT_ROW;
			}
		}
		return cy + PROFILE_COOP_FLYOUT_PAD;
	}

	private boolean profileCoopFlyoutHover(int mouseX, int mouseY, ProfileFetcher.ProfileChoice choice) {
		if (choice == null) {
			return false;
		}
		Font font = this.screen.font();
		ProfileMenuLayout layout = profileMenuLayout(font);
		int flyoutX = profileCoopFlyoutX(layout);
		int flyoutY = layout.menuY();
		int flyoutW = PROFILE_COOP_FLYOUT_W;
		int flyoutH = Math.min(profileCoopFlyoutHeight(font, choice), visibleScreenH() - flyoutY - 4);
		return mouseX >= flyoutX && mouseX < flyoutX + flyoutW + PROFILE_COOP_FLYOUT_BRIDGE
			&& mouseY >= flyoutY && mouseY < flyoutY + flyoutH;
	}

	private boolean profileMenuCombinedHover(int mouseX, int mouseY) {
		Font font = this.screen.font();
		ProfileMenuLayout layout = profileMenuLayout(font);
		boolean inMenu = mouseX >= layout.menuX() && mouseX < layout.menuX() + layout.menuW()
			&& mouseY >= layout.menuY() && mouseY < layout.menuY() + layout.menuH();
		if (inMenu) {
			return true;
		}
		return this.profileMenuHoverChoice != null
			&& profileCoopFlyoutHover(mouseX, mouseY, this.profileMenuHoverChoice);
	}

	private void drawProfileMenu(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
		if (this.profileChoices.size() <= 1) {
			return;
		}
		ProfileMenuLayout layout = profileMenuLayout(font);
		PvDraw.popupSurface(g, layout.menuX(), layout.menuY(), layout.menuW(), layout.menuH());
		int cy = layout.menuY() + 2;
		for (ProfileFetcher.ProfileChoice choice : this.profileChoices) {
			boolean hover = choice == this.profileMenuHoverChoice
				|| profileMenuChoiceAt(font, mouseX, mouseY) == choice;
			if (hover || choice.selected()) {
				PvDraw.fill(
					g,
					layout.menuX() + 1,
					cy,
					layout.menuW() - 2,
					layout.lineH(),
					hover ? 0x33FFFFFF : 0x22AA88FF
				);
			}
			PvDraw.text(
				g, font, choice.cuteName(),
				layout.menuX() + PROFILE_MENU_PAD, cy + 2,
				choice.selected() ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_TEXT
			);
			ItemStack badge = profileModeBadge(choice);
			if (!badge.isEmpty()) {
				int badgeX = layout.menuX() + PROFILE_MENU_PAD + font.width(choice.cuteName()) + 3;
				int badgeY = cy + 1 + Math.max(0, (layout.lineH() - 2 - PROFILE_BADGE_SIZE) / 2);
				ItemIcons.drawItemIcon(g, badge, badgeX, badgeY, PROFILE_BADGE_SIZE);
			}
			cy += layout.lineH();
		}
	}

	/** Screen height in the viewer's scaled coordinates, so popups use the room that is actually there. */
	private int visibleScreenH() {
		int virtual = IconButtonBar.virtualHeight();
		return virtual > 0 ? virtual : this.screen.height;
	}

	private void drawProfileCoopCounts(
		GuiGraphicsExtractor g,
		Font font,
		ProfileFetcher.ProfileChoice choice,
		int menuX,
		int menuW,
		int textY
	) {
		int x = menuX + menuW - PROFILE_MENU_PAD;
		if (choice.coop().soloProfile()) {
			String solo = "Solo";
			PvDraw.text(g, font, solo, x - font.width(solo), textY, PvDraw.COLOR_MUTED);
			return;
		}
		String formerText = String.valueOf(choice.coop().formerCount());
		x -= font.width(formerText);
		PvDraw.text(g, font, formerText, x, textY, COLOR_COOP_FORMER);
		x -= 2;
		x -= PROFILE_COOP_ICON;
		drawCoopMemberIcon(g, x, textY - 1, COLOR_COOP_FORMER);
		x -= 8;
		String currentText = String.valueOf(choice.coop().currentOthers());
		x -= font.width(currentText);
		PvDraw.text(g, font, currentText, x, textY, COLOR_COOP_CURRENT);
		x -= 2;
		x -= PROFILE_COOP_ICON;
		drawCoopMemberIcon(g, x, textY - 1, COLOR_COOP_CURRENT);
	}

	private static void drawCoopMemberIcon(GuiGraphicsExtractor g, int x, int y, int tint) {
		int body = tint & 0x00FFFFFF;
		PvDraw.fill(g, x + 2, y, 4, 4, 0xFF000000 | body);
		PvDraw.fill(g, x + 1, y + 4, 6, 5, 0xFF000000 | body);
	}

	private void drawProfileCoopFlyout(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
		this.coopMemberHits.clear();
		ProfileFetcher.ProfileChoice choice = this.profileMenuHoverChoice;
		if (choice == null) {
			return;
		}
		ProfileMenuLayout layout = profileMenuLayout(font);
		int flyoutX = profileCoopFlyoutX(layout) + PROFILE_COOP_FLYOUT_BRIDGE;
		int flyoutY = layout.menuY();
		int flyoutW = PROFILE_COOP_FLYOUT_W;
		int contentH = profileCoopFlyoutHeight(font, choice);
		int flyoutH = Math.min(contentH, visibleScreenH() - flyoutY - 4);
		PvDraw.popupSurface(g, flyoutX, flyoutY, flyoutW, flyoutH);

		int innerX = flyoutX + PROFILE_COOP_FLYOUT_PAD;
		int innerW = flyoutW - PROFILE_COOP_FLYOUT_PAD * 2;
		int cy = flyoutY + PROFILE_COOP_FLYOUT_PAD - this.profileCoopFlyoutScroll;

		g.enableScissor(flyoutX + 1, flyoutY + 1, flyoutX + flyoutW - 1, flyoutY + flyoutH - 1);
		PvDraw.text(g, font, choice.cuteName(), innerX, cy, PvDraw.COLOR_TEXT);
		ItemStack badge = profileModeBadge(choice);
		if (!badge.isEmpty()) {
			int badgeX = innerX + font.width(choice.cuteName()) + 3;
			ItemIcons.drawItemIcon(g, badge, badgeX, cy - 1, PROFILE_BADGE_SIZE);
		}
		cy += font.lineHeight + 3;
		if (choice.createdAtMs() > 0L) {
			String date = FormatUtil.prettyDate(choice.createdAtMs());
			String age = FormatUtil.ago(choice.createdAtMs());
			if (!date.isBlank()) {
				drawFlyoutMetaRow(g, font, "Created", date, PvDraw.COLOR_TEXT, innerX, innerW, cy);
				cy += font.lineHeight + 1;
			}
			if (!age.isBlank()) {
				drawFlyoutMetaRow(g, font, "Age", age, PvDraw.COLOR_GOLD, innerX, innerW, cy);
				cy += font.lineHeight + 1;
			}
		}
		cy += 2;
		PvDraw.fill(g, innerX, cy, innerW, 1, PvDraw.COLOR_DIVIDER);
		cy += 4;

		if (choice.coop().currentMembers().isEmpty() && choice.coop().formerMembers().isEmpty()) {
			PvDraw.text(g, font, "Solo profile", innerX, cy, PvDraw.COLOR_MUTED);
			g.disableScissor();
			return;
		}
		if (!choice.coop().currentMembers().isEmpty()) {
			PvDraw.text(g, font, "Current members", innerX, cy, COLOR_COOP_CURRENT);
			drawJoinedHeader(g, font, choice.coop().currentMembers(), innerX, innerW, cy);
			cy += font.lineHeight + 4;
			for (ProfileFetcher.CoopMemberRef member : choice.coop().currentMembers()) {
				drawCoopFlyoutMember(g, font, member, innerX, innerW, cy, false, mouseX, mouseY, flyoutY, flyoutH);
				cy += PROFILE_COOP_FLYOUT_ROW;
			}
			cy += 2;
		}
		if (!choice.coop().formerMembers().isEmpty()) {
			PvDraw.text(g, font, "Former members", innerX, cy, COLOR_COOP_FORMER);
			drawJoinedHeader(g, font, choice.coop().formerMembers(), innerX, innerW, cy);
			cy += font.lineHeight + 4;
			for (ProfileFetcher.CoopMemberRef member : choice.coop().formerMembers()) {
				drawCoopFlyoutMember(g, font, member, innerX, innerW, cy, true, mouseX, mouseY, flyoutY, flyoutH);
				cy += PROFILE_COOP_FLYOUT_ROW;
			}
		}
		g.disableScissor();
	}

	private static void drawJoinedHeader(
		GuiGraphicsExtractor g,
		Font font,
		List<ProfileFetcher.CoopMemberRef> members,
		int x,
		int w,
		int y
	) {
		if (members.stream().noneMatch(m -> m.joinedMs() > 0L)) {
			return;
		}
		String label = "Joined";
		PvDraw.text(g, font, label, x + w - font.width(label), y, PvDraw.COLOR_MUTED);
	}

	private void drawFlyoutMetaRow(
		GuiGraphicsExtractor g,
		Font font,
		String label,
		String value,
		int valueColor,
		int x,
		int w,
		int y
	) {
		PvDraw.text(g, font, label, x, y, PvDraw.COLOR_MUTED);
		int valueW = font.width(value);
		PvDraw.text(g, font, value, x + w - valueW, y, valueColor);
	}

	private void drawCoopFlyoutMember(
		GuiGraphicsExtractor g,
		Font font,
		ProfileFetcher.CoopMemberRef member,
		int x,
		int w,
		int y,
		boolean former,
		int mouseX,
		int mouseY,
		int flyoutY,
		int flyoutH
	) {
		boolean visible = y + PROFILE_COOP_FLYOUT_ROW > flyoutY && y < flyoutY + flyoutH;
		// Scissoring hides pixels, but still extracting a head can resolve its profile online.
		if (!visible) return;
		boolean resolved = ProfileFetcher.coopNameResolved(member);
		boolean hover = visible && resolved
			&& mouseX >= x && mouseX < x + w
			&& mouseY >= y && mouseY < y + PROFILE_COOP_FLYOUT_ROW;
		if (hover) {
			PvDraw.fill(g, x - 2, y - 1, w + 4, PROFILE_COOP_FLYOUT_ROW, 0x33FFFFFF);
		}
		UUID uuid = HypixelApiClient.parseUndashedUuid(member.uuid());
		if (uuid != null) {
			ItemStack head = this.headCache.computeIfAbsent(uuid, ProfileSelectorController::coopMemberHead);
			ItemIcons.drawItemIcon(g, head, x, y - 1, PROFILE_COOP_ICON);
		}
		String name = ProfileFetcher.coopMemberDisplayName(member);
		int nameColor = former ? COLOR_COOP_FORMER : COLOR_COOP_CURRENT;
		if (!resolved) {
			nameColor = PvDraw.COLOR_MUTED;
		}
		int nameX = x + PROFILE_COOP_ICON + 4;
		int maxW = w - PROFILE_COOP_ICON - 4;
		if (member.joinedMs() > 0L) {
			String joined = FormatUtil.prettySpan(System.currentTimeMillis() - member.joinedMs());
			int joinedW = font.width(joined);
			PvDraw.text(g, font, joined, x + w - joinedW, y, PvDraw.COLOR_MUTED);
			maxW -= joinedW + 4;
		}
		String clipped = font.plainSubstrByWidth(name, maxW);
		PvDraw.text(g, font, clipped, nameX, y, nameColor);
		if (visible && resolved) {
			ProfileFetcher.ProfileChoice choice = this.profileMenuHoverChoice;
			String profileId = choice == null ? null : choice.profileId();
			this.coopMemberHits.add(new CoopMemberHit(x, y, w, PROFILE_COOP_FLYOUT_ROW, member.uuid(), name, profileId));
		}
	}

	static ItemStack coopMemberHead(UUID uuid) {
		ItemStack head = new ItemStack(Items.PLAYER_HEAD);
		if (uuid == null) {
			return head;
		}
		try {
			head.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(uuid));
		} catch (Throwable ignored) {
			try {
				head.set(
					DataComponents.PROFILE,
					ResolvableProfile.createResolved(new GameProfile(uuid, "Player"))
				);
			} catch (Throwable ignored2) {
			}
		}
		return head;
	}

	private int profileChoiceLineWidth(Font font, ProfileFetcher.ProfileChoice choice) {
		int w = font.width(choice.cuteName());
		if (!profileModeBadge(choice).isEmpty()) {
			w += 3 + PROFILE_BADGE_SIZE;
		}
		return w;
	}

	private static ItemStack profileModeBadge(ProfileFetcher.ProfileChoice choice) {
		if (choice == null || choice.gameMode() == null || choice.gameMode().isBlank()) {
			return ItemStack.EMPTY;
		}
		return switch (choice.gameMode().toLowerCase(Locale.ROOT)) {
			case "ironman" -> new ItemStack(Items.SHIELD);
			case "bingo" -> {
				ItemStack card = SkyBlockItemFactory.iconStack("BINGO_CARD");
				yield card == null || card.isEmpty() ? new ItemStack(Items.FILLED_MAP) : card;
			}
			case "stranded", "island" -> new ItemStack(Items.OAK_SAPLING);
			default -> ItemStack.EMPTY;
		};
	}

	private ProfileFetcher.ProfileChoice selectedProfileChoice() {
		for (ProfileFetcher.ProfileChoice choice : this.profileChoices) {
			if (choice.selected()) {
				return choice;
			}
		}
		return this.profileChoices.isEmpty() ? null : this.profileChoices.get(0);
	}

	private List<PvTooltip.Line> profileSelectorTooltip(int mouseX, int mouseY, boolean footerHover) {
		Font font = this.screen.font();
		if (this.profileMenuOpen && this.profileChoices.size() > 1) {
			ProfileFetcher.ProfileChoice hover = profileMenuChoiceAt(font, mouseX, mouseY);
			if (hover != null) {
				return profileCreatedTooltip(hover);
			}
			return null;
		}
		if (footerHover) {
			return profileCreatedTooltip(selectedProfileChoice());
		}
		return null;
	}

	private static List<PvTooltip.Line> profileCreatedTooltip(ProfileFetcher.ProfileChoice choice) {
		if (choice == null || choice.createdAtMs() <= 0L) {
			return null;
		}
		String date = FormatUtil.prettyDate(choice.createdAtMs());
		String age = FormatUtil.ago(choice.createdAtMs());
		List<PvTooltip.Line> lines = new ArrayList<>(4);
		lines.add(PvTooltip.Line.title(choice.cuteName(), PvDraw.COLOR_TEXT));
		lines.add(PvTooltip.Line.divider());
		if (!date.isBlank()) {
			lines.add(PvTooltip.Line.row("Created", PvDraw.COLOR_MUTED, date, PvDraw.COLOR_TEXT));
		}
		if (!age.isBlank()) {
			lines.add(PvTooltip.Line.row("Age", PvDraw.COLOR_MUTED, age, PvDraw.COLOR_GOLD));
		}
		String mode = choice.gameModeLabel();
		if (!mode.isBlank()) {
			lines.add(PvTooltip.Line.row("Mode", PvDraw.COLOR_MUTED, mode, PvDraw.COLOR_GOLD));
		}
		return lines;
	}

	private boolean clickProfileFooter(double mx, double my) {
		if (this.profileChoices.size() <= 1) {
			return false;
		}
		if (mx >= this.profileFooterX && mx < this.profileFooterX + this.profileFooterW
			&& my >= this.profileFooterY && my < this.profileFooterY + this.profileFooterH) {
			this.profileMenuOpen = !this.profileMenuOpen;
			if (!this.profileMenuOpen) {
				this.profileMenuHoverChoice = null;
			}
			return true;
		}
		if (!this.profileMenuOpen) {
			return false;
		}
		if (!profileMenuCombinedHover((int) mx, (int) my)) {
			this.profileMenuOpen = false;
			this.profileMenuHoverChoice = null;
			this.coopMemberHits.clear();
			return true;
		}
		ProfileFetcher.ProfileChoice choice = profileMenuChoiceAt(this.screen.font(), (int) mx, (int) my);
		if (choice == null) {
			return clickCoopFlyoutMember(mx, my);
		}
		this.profileMenuOpen = false;
		this.profileMenuHoverChoice = null;
		this.coopMemberHits.clear();
		if (!choice.selected()) {
			switchProfile(choice.profileId());
		}
		return true;
	}

	private boolean clickCoopFlyoutMember(double mx, double my) {
		for (CoopMemberHit hit : this.coopMemberHits) {
			if (mx >= hit.x && mx < hit.x + hit.w && my >= hit.y && my < hit.y + hit.h) {
				openCoopMemberProfile(hit.name, hit.profileId);
				return true;
			}
		}
		return true;
	}

	private void openCoopMemberProfile(String name, String profileId) {
		if (name == null || name.isBlank()) {
			return;
		}
		String current = this.screen.homePage().playerName();
		if (current != null && current.equalsIgnoreCase(name)) {
			if (profileId != null && !profileId.equals(this.screen.profileId())) {
				this.profileMenuOpen = false;
				this.profileMenuHoverChoice = null;
				this.coopMemberHits.clear();
				switchProfile(profileId);
			}
			return;
		}
		fishmod.compat.McCompatKt.showScreen(Minecraft.getInstance(), this.screen.forCoopMember(name, profileId));
	}

	private record CoopMemberHit(int x, int y, int w, int h, String uuid, String name, String profileId) {
	}

	private void switchProfile(String nextProfileId) {
		JsonObject profilesRoot = this.screen.profilesRoot();
		UUID playerUuid = this.screen.playerUuid();
		String profileId = this.screen.profileId();
		if (nextProfileId == null || nextProfileId.isBlank() || profilesRoot == null || playerUuid == null) {
			return;
		}
		if (nextProfileId.equals(profileId)) {
			return;
		}
		String name = this.screen.homePage().playerName();
		UUID uuid = playerUuid;
		JsonObject root = profilesRoot;
		int generation = this.screen.bumpLoadGeneration();
		ProfileFetcher.switchToProfile(name, uuid, root, nextProfileId, updated -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (fishmod.compat.McCompatKt.getCurScreen(client) != this.screen || !this.screen.isLoadGeneration(generation)) {
					return;
				}
				if (updated == null || !updated.ok()) {
					return;
				}
				this.screen.applyLoadedProfile(updated);
			});
		}).whenComplete((loaded, error) -> {
			Minecraft client = Minecraft.getInstance();
			if (client == null) {
				return;
			}
			client.execute(() -> {
				if (fishmod.compat.McCompatKt.getCurScreen(client) != this.screen || !this.screen.isLoadGeneration(generation)) {
					return;
				}
				if (error != null || loaded == null || !loaded.ok()) {
					BetterPV.LOGGER.warn(
						"Profile switch failed for {} -> {}",
						name,
						nextProfileId,
						error
					);
					// Keep prior Home visible; do not force loading-egg for a failed switch.
					return;
				}
				this.screen.applyLoadedProfile(loaded);
				this.screen.setDataReady(true);
			});
		});
	}
}
