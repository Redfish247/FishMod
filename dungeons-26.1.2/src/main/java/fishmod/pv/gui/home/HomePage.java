/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.home;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import fishmod.pv.data.PlayerStatsSnapshot;
import fishmod.pv.data.PlayerStatus;
import fishmod.pv.data.ProfileSnapshot;
import fishmod.pv.data.UsernameHistory;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.networth.NetworthBreakdown;
import fishmod.pv.networth.NetworthMode;
import fishmod.pv.slayer.SlayerCalcOverlay;
import fishmod.pv.weight.WeightBreakdown;
import fishmod.pv.weight.WeightSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class HomePage {
	private static final int BAR_H = 6;
	private static final int BAR_LABEL_GAP = 2;
	private static final int BAR_AFTER_GAP = 6;
	private static final int SECTION_GAP = 8;
	private static final int PAD = 6;
	private static final int SKILL_ROWS = 5;
	private static final int SLAYER_ROWS = 3;

	/** Two-stage SkyBlock Level panel expand/collapse within Home bounds. */
	public enum SbXpExpandPhase {
		CLOSED,
		EXPANDING_LEFT,
		EXPANDING_RIGHT,
		OPEN,
		COLLAPSING_RIGHT,
		COLLAPSING_LEFT
	}

	private ProfileSnapshot snapshot;
	private PlayerStatsSnapshot playerStats = PlayerStatsSnapshot.empty();
	private WeightBreakdown senither = WeightBreakdown.empty(WeightSystem.SENITHER);
	private WeightBreakdown lily = WeightBreakdown.empty(WeightSystem.LILY);
	private NetworthBreakdown networthNormal = NetworthBreakdown.empty("");
	private NetworthBreakdown networthNonCosmetic = NetworthBreakdown.empty("");
	private NetworthBreakdown networthUnsoulbound = NetworthBreakdown.empty("");
	private NetworthBreakdown networthUnsoulboundNonCosmetic = NetworthBreakdown.empty("");
	private WeightSystem weightSystem = WeightSystem.SENITHER;
	private boolean networthIncludeCosmetics = true;
	private boolean networthUnsoulboundOnly = false;
	private String loadError;
	private final SlayerCalcOverlay slayerOverlay = new SlayerCalcOverlay();
	private final HomeSbXpOverlay sbXpOverlay = new HomeSbXpOverlay();
	private final HomeLeftColumn leftColumn = new HomeLeftColumn();
	private final HomeSkillSlayerBars bars = new HomeSkillSlayerBars();

	private UsernameHistory usernameHistory = UsernameHistory.idle();
	private int historyScroll;
	private int historyMaxScroll;
	private PlayerStatus playerStatus = PlayerStatus.idle();
	private JsonObject playerRank;
	private ItemStack[] armor = new ItemStack[] {
		ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
	};
	private final List<HoverZone> zones = new ArrayList<>();
	private Layout cachedLayout;
	private int layoutCacheW = Integer.MIN_VALUE;
	private int layoutCacheLineH = -1;
	private float openScale = 1.0F;
	private float openPivotX;
	private float openPivotY;
	private float uiScale = 1.0F;
	private float uiOffsetX;
	private float uiOffsetY;

	public HomePage(ProfileSnapshot snapshot) {
		this.snapshot = snapshot;
	}

	public void applyLoaded(
		ProfileSnapshot snapshot,
		WeightBreakdown senither,
		WeightBreakdown lily,
		NetworthBreakdown networthNormal,
		NetworthBreakdown networthNonCosmetic,
		NetworthBreakdown networthUnsoulbound,
		NetworthBreakdown networthUnsoulboundNonCosmetic,
		ItemStack[] armor,
		PlayerStatsSnapshot playerStats,
		String error
	) {
		UUID prevUuid = this.snapshot == null ? null : this.snapshot.playerUuid();
		UUID nextUuid = snapshot == null ? null : snapshot.playerUuid();
		this.snapshot = snapshot;
		this.leftColumn.clearCaches();
		this.bars.clearCaches();
		this.sbXpOverlay.resetEmblemScroll();
		this.senither = senither == null ? WeightBreakdown.empty(WeightSystem.SENITHER) : senither;
		this.lily = lily == null ? WeightBreakdown.empty(WeightSystem.LILY) : lily;
		this.networthNormal = networthNormal == null ? NetworthBreakdown.empty("") : networthNormal;
		this.networthNonCosmetic = networthNonCosmetic == null ? NetworthBreakdown.empty("") : networthNonCosmetic;
		this.networthUnsoulbound = networthUnsoulbound == null ? NetworthBreakdown.empty("") : networthUnsoulbound;
		this.networthUnsoulboundNonCosmetic = networthUnsoulboundNonCosmetic == null ? NetworthBreakdown.empty("") : networthUnsoulboundNonCosmetic;
		this.armor = armor == null
			? new ItemStack[] { ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY }
			: armor;
		this.playerStats = playerStats == null ? PlayerStatsSnapshot.empty() : playerStats;
		this.loadError = error;
		this.slayerOverlay.close();
		this.usernameHistory = UsernameHistory.idle();
		this.playerStatus = PlayerStatus.idle();
		if (nextUuid == null || prevUuid == null || !nextUuid.equals(prevUuid)) {
			this.playerRank = null;
		}
		invalidateLayoutCache();
	}

	public void applyPlayerRank(JsonObject player) {
		this.playerRank = player;
		this.leftColumn.clearStyledNameCache();
	}

	public void applyUsernameHistory(UsernameHistory history) {
		this.usernameHistory = history == null ? UsernameHistory.idle() : history;
		this.historyScroll = 0;
		this.historyMaxScroll = 0;
	}

	/** Real cursor for the 3D model when the hover mouse passed to {@link #render} is masked. */
	public void setModelLook(int x, int y) {
		this.leftColumn.setModelLook(x, y);
	}

	public void applyPlayerStatus(PlayerStatus status) {
		this.playerStatus = status == null ? PlayerStatus.idle() : status;
	}

	public UsernameHistory usernameHistory() {
		return this.usernameHistory;
	}

	public PlayerStatus playerStatus() {
		return this.playerStatus;
	}

	public String profileName() {
		return this.snapshot == null ? "" : this.snapshot.profileName();
	}

	public String playerName() {
		return this.snapshot == null ? "" : this.snapshot.playerName();
	}

	public void applyNetworth(
		NetworthBreakdown normal,
		NetworthBreakdown nonCosmetic,
		NetworthBreakdown unsoulbound,
		NetworthBreakdown unsoulboundNonCosmetic
	) {
		if (normal != null) {
			this.networthNormal = normal;
		}
		if (nonCosmetic != null) {
			this.networthNonCosmetic = nonCosmetic;
		}
		if (unsoulbound != null) {
			this.networthUnsoulbound = unsoulbound;
		}
		if (unsoulboundNonCosmetic != null) {
			this.networthUnsoulboundNonCosmetic = unsoulboundNonCosmetic;
		}
		// NW label is cached by total only; a failed load keeps total 0 and would stay "...".
		this.leftColumn.clearCaches();
		invalidateLayoutCache();
	}

	public boolean clickWeight(double mouseX, double mouseY) {
		if (this.leftColumn.showingStatsFace()) {
			return false;
		}
		if (this.leftColumn.hitWeight(mouseX, mouseY)) {
			this.weightSystem = this.weightSystem.other();
			return true;
		}
		return false;
	}

	public boolean slayerMouseClicked(double mouseX, double mouseY) {
		if (this.slayerOverlay.isOpen()) {
			return this.slayerOverlay.mouseClicked(mouseX, mouseY);
		}
		return clickSlayerName(mouseX, mouseY);
	}

	public boolean slayerKeyPressed(int key) {
		return this.slayerOverlay.keyPressed(key);
	}

	public boolean slayerCharTyped(char ch) {
		return this.slayerOverlay.charTyped(ch);
	}

	public boolean slayerOverlayOpen() {
		return this.slayerOverlay.isOpen();
	}

	public void renderSlayerOverlay(GuiGraphicsExtractor g, Font font, int screenW, int screenH, int mouseX, int mouseY) {
		this.slayerOverlay.render(g, font, screenW, screenH, mouseX, mouseY);
	}

	private boolean clickSlayerName(double mouseX, double mouseY) {
		if (this.sbXpOverlay.phase() != SbXpExpandPhase.CLOSED) {
			return false;
		}
		for (HomeSkillSlayerBars.SlayerNameHit hit : this.bars.slayerNameHits()) {
			if (mouseX < hit.x() || mouseX >= hit.x() + hit.w()
				|| mouseY < hit.y() || mouseY >= hit.y() + hit.h()) {
				continue;
			}
			ProfileSnapshot.SlayerEntry slayer = slayerById(hit.slayerId());
			if (slayer == null) {
				return false;
			}
			this.slayerOverlay.open(
				slayer,
				this.snapshot == null ? null : this.snapshot.slayerMods(),
				hit.accent()
			);
			return true;
		}
		return false;
	}

	private ProfileSnapshot.SlayerEntry slayerById(String id) {
		if (this.snapshot == null || id == null || id.isBlank()) {
			return null;
		}
		for (ProfileSnapshot.SlayerEntry slayer : this.snapshot.slayers()) {
			if (id.equalsIgnoreCase(slayer.id())) {
				return slayer;
			}
		}
		return null;
	}

	public boolean clickLeftPanel(double mouseX, double mouseY) {
		if (this.slayerOverlay.isOpen()) {
			return true;
		}
		if (clickSlayerName(mouseX, mouseY)) {
			return true;
		}
		if (this.sbXpOverlay.phase() != SbXpExpandPhase.CLOSED) {
			return false;
		}
		if (!this.leftColumn.contains(mouseX, mouseY)) {
			return false;
		}
		if (hitName(mouseX, mouseY) || hitStatus(mouseX, mouseY)) {
			return false;
		}
		return this.leftColumn.beginFlip();
	}

	/** Click the SkyBlock Level strip (closed → expand) or the open page (→ collapse). */
	public boolean clickSbLevelPanel(double mouseX, double mouseY) {
		return this.sbXpOverlay.click(mouseX, mouseY);
	}

	/** ESC / back: collapse expanded SB Level view. */
	public boolean requestSbLevelBack() {
		return this.sbXpOverlay.requestBack();
	}

	public boolean isSbLevelOverlayActive() {
		return this.sbXpOverlay.isActive();
	}

	/** Snap closed when leaving Home overview (tab switch, etc.). */
	public void forceCloseSbLevelOverlay() {
		this.sbXpOverlay.forceClose();
	}

	public SbXpExpandPhase sbXpExpandPhase() {
		return this.sbXpOverlay.phase();
	}

	public boolean hitName(double mouseX, double mouseY) {
		return this.leftColumn.hitName(mouseX, mouseY);
	}

	public boolean hitStatus(double mouseX, double mouseY) {
		return this.leftColumn.hitStatus(mouseX, mouseY);
	}

	public boolean clickNetworth(double mouseX, double mouseY, int button) {
		if (this.leftColumn.showingStatsFace()) {
			return false;
		}
		if (!this.leftColumn.hitNetworth(mouseX, mouseY)) {
			return false;
		}
		if (button == 0) {
			this.networthIncludeCosmetics = !this.networthIncludeCosmetics;
			return true;
		}
		if (button == 1) {
			this.networthUnsoulboundOnly = !this.networthUnsoulboundOnly;
			return true;
		}
		return false;
	}

	private NetworthMode networthMode() {
		if (this.networthUnsoulboundOnly) {
			return this.networthIncludeCosmetics
				? NetworthMode.UNSOULBOUND
				: NetworthMode.UNSOULBOUND_NON_COSMETIC;
		}
		return this.networthIncludeCosmetics ? NetworthMode.NORMAL : NetworthMode.NON_COSMETIC;
	}

	private NetworthBreakdown activeNetworth() {
		return switch (networthMode()) {
			case NORMAL -> this.networthNormal;
			case NON_COSMETIC -> this.networthNonCosmetic;
			case UNSOULBOUND -> this.networthUnsoulbound;
			case UNSOULBOUND_NON_COSMETIC -> this.networthUnsoulboundNonCosmetic;
		};
	}

	public int preferredHeight(Font font, int width) {
		return layoutFor(font, width).contentH;
	}

	private void invalidateLayoutCache() {
		this.layoutCacheW = Integer.MIN_VALUE;
		this.layoutCacheLineH = -1;
		this.cachedLayout = null;
	}

	private Layout layoutFor(Font font, int width) {
		if (this.cachedLayout != null
			&& width == this.layoutCacheW
			&& font.lineHeight == this.layoutCacheLineH) {
			return this.cachedLayout;
		}
		this.layoutCacheW = width;
		this.layoutCacheLineH = font.lineHeight;
		this.cachedLayout = measure(font, width);
		return this.cachedLayout;
	}

	public void render(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		int mouseX,
		int mouseY,
		int screenW,
		int screenH,
		float openScale,
		float openPivotX,
		float openPivotY,
		float uiScale,
		float uiOffsetX,
		float uiOffsetY
	) {
		this.openScale = openScale;
		this.openPivotX = openPivotX;
		this.openPivotY = openPivotY;
		this.uiScale = uiScale;
		this.uiOffsetX = uiOffsetX;
		this.uiOffsetY = uiOffsetY;
		this.zones.clear();
		this.sbXpOverlay.tick();
		Layout layout = layoutFor(font, w);
		int contentH = Math.min(h, layout.contentH);
		int leftX = x;
		int levelX = x + layout.leftW + layout.gap;
		int barsX = levelX + layout.levelW + (layout.levelW > 0 ? layout.gap : 0);

		this.sbXpOverlay.setLayoutBounds(x, y, w, contentH, levelX, layout.levelW);

		SbXpExpandPhase phase = this.sbXpOverlay.phase();
		boolean overlay = phase != SbXpExpandPhase.CLOSED;
		float stageP = HomeUi.easeInOutCubic(this.sbXpOverlay.stageProgress());
		int animLeft = levelX;
		int animRight = levelX + layout.levelW;
		float leftMask = 0F;
		float barsMask = 0F;
		float cardFade = 1F;

		if (phase == SbXpExpandPhase.EXPANDING_LEFT) {
			animLeft = Math.round(lerp(levelX, x, stageP));
			animRight = levelX + layout.levelW;
			leftMask = stageP;
			cardFade = 1F - stageP;
		} else if (phase == SbXpExpandPhase.EXPANDING_RIGHT) {
			animLeft = x;
			animRight = Math.round(lerp(levelX + layout.levelW, x + w, stageP));
			leftMask = 1F;
			barsMask = stageP;
			cardFade = 0F;
		} else if (phase == SbXpExpandPhase.OPEN) {
			animLeft = x;
			animRight = x + w;
			leftMask = 1F;
			barsMask = 1F;
			cardFade = 0F;
		} else if (phase == SbXpExpandPhase.COLLAPSING_RIGHT) {
			animLeft = x;
			animRight = Math.round(lerp(x + w, levelX + layout.levelW, stageP));
			leftMask = 1F;
			barsMask = 1F - stageP;
			cardFade = 0F;
		} else if (phase == SbXpExpandPhase.COLLAPSING_LEFT) {
			animLeft = Math.round(lerp(x, levelX, stageP));
			animRight = levelX + layout.levelW;
			leftMask = 1F - stageP;
			barsMask = 0F;
			cardFade = stageP;
		}

		if (phase != SbXpExpandPhase.OPEN) {
			this.leftColumn.draw(
				g, font, leftX, y, layout.leftW, contentH, layout, mouseX, mouseY,
				this.snapshot, this.playerStats, this.playerStatus, this.playerRank, this.armor,
				activeNetworth(), this.senither, this.lily, this.weightSystem, this.loadError,
				this.openScale, this.openPivotX, this.openPivotY,
				this.uiScale, this.uiOffsetX, this.uiOffsetY, this.zones
			);
			if (leftMask > 0.01F) {
				PvDraw.fill(g, leftX, y, layout.leftW, contentH, withAlpha(HomeSbXpOverlay.SB_XP_MASK, leftMask));
			}
			// SkyBlock level card removed; the skill bars take its width.
			this.bars.draw(g, font, barsX, y, layout.barsW, contentH, layout.rowH, this.snapshot, this.zones);
			if (barsMask > 0.01F) {
				PvDraw.fill(g, barsX, y, layout.barsW, contentH, withAlpha(HomeSbXpOverlay.SB_XP_MASK, barsMask));
			}
		}

		if (overlay) {
			int animW = Math.max(1, animRight - animLeft);
			this.sbXpOverlay.draw(g, font, animLeft, y, animW, contentH, this.snapshot, cardFade, false, this.zones);
		}
	}

	public void renderTooltip(
		GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH
	) {
		List<PvTooltip.Line> styledTip = null;
		if (this.slayerOverlay.isOpen()) {
			return;
		}
		SbXpExpandPhase phase = this.sbXpOverlay.phase();
		boolean onProfileFace = !this.leftColumn.showingStatsFace() && phase == SbXpExpandPhase.CLOSED;
		if (onProfileFace && hitName(mouseX, mouseY)) {
			int[] scrollOut = new int[2];
			HomeTooltips.drawUsernameHistory(
				g, font, this.usernameHistory, this.historyScroll,
				mouseX, mouseY, screenW, screenH, scrollOut
			);
			this.historyScroll = scrollOut[0];
			this.historyMaxScroll = scrollOut[1];
			return;
		}
		if (onProfileFace && hitStatus(mouseX, mouseY)) {
			styledTip = HomeTooltips.status(this.playerStatus);
		} else if (onProfileFace && this.leftColumn.hitWeight(mouseX, mouseY)) {
			if (this.loadError != null && !this.loadError.isBlank()) {
				styledTip = List.of(
					PvTooltip.Line.of("Weight unavailable", PvDraw.COLOR_TEXT),
					PvTooltip.Line.of(this.loadError, PvDraw.COLOR_MUTED)
				);
			} else {
				styledTip = activeWeight().tooltipStyledLines();
			}
		} else if (onProfileFace && this.leftColumn.hitNetworth(mouseX, mouseY)) {
			styledTip = activeNetworth().tooltipStyledLines(
				networthMode(),
				leftShiftDown()
			);
		} else if (onProfileFace && this.leftColumn.hitBank(mouseX, mouseY)) {
			styledTip = HomeTooltips.bank(this.snapshot);
		} else if (phase == SbXpExpandPhase.CLOSED || phase == SbXpExpandPhase.OPEN
			|| this.leftColumn.showingStatsFace()) {
			for (HoverZone zone : this.zones) {
				if (mouseX >= zone.x && mouseX < zone.x + zone.w && mouseY >= zone.y && mouseY < zone.y + zone.h) {
					styledTip = zone.lines;
					break;
				}
			}
		}
		if (styledTip != null) {
			PvTooltip.drawStyled(g, font, styledTip, mouseX, mouseY, screenW, screenH);
		}
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private static int withAlpha(int argb, float alpha) {
		int a = Math.max(0, Math.min(255, Math.round(((argb >>> 24) & 0xFF) * alpha)));
		return (a << 24) | (argb & 0x00FFFFFF);
	}

	public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int mouseX, int mouseY, int screenW, int screenH) {
		render(
			g, font, x, y, w, h, mouseX, mouseY, screenW, screenH,
			1.0F, x + w / 2F, y + h / 2F, 1.0F, 0.0F, 0.0F
		);
	}

	private WeightBreakdown activeWeight() {
		return this.weightSystem == WeightSystem.SENITHER ? this.senither : this.lily;
	}

	/** GUI screens do not keep {@code options.keyShift} updated; poll GLFW instead. */
	private static boolean leftShiftDown() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.getWindow() == null) {
			return false;
		}
		return InputConstants.isKeyDown(mc.getWindow(), InputConstants.KEY_LSHIFT);
	}

	private Layout measure(Font font, int w) {
		Layout layout = new Layout();
		layout.gap = 6;
		layout.levelW = 0;
		layout.leftW = Math.max(100, (w - layout.gap) * 30 / 100);
		layout.barsW = w - layout.leftW - layout.gap;
		layout.line = font.lineHeight + 2;
		layout.rowH = font.lineHeight + BAR_LABEL_GAP + BAR_H + BAR_AFTER_GAP;
		int extraSkillH = layout.rowH;
		layout.barsInnerH = SKILL_ROWS * layout.rowH + extraSkillH + SECTION_GAP
			+ SLAYER_ROWS * layout.rowH - BAR_AFTER_GAP;
		layout.barsH = PAD * 2 + layout.barsInnerH;
		layout.lastSlayerNameY = PAD + SKILL_ROWS * layout.rowH + extraSkillH + SECTION_GAP
			+ (SLAYER_ROWS - 1) * layout.rowH;
		layout.statsH = PAD + layout.line * 4 + 6;
		layout.profileLineH = font.lineHeight;
		layout.nameLineH = font.lineHeight;
		layout.nameGap = 2;
		layout.boxToFooterGap = 8;
		layout.statusH = font.lineHeight + 4;
		layout.footerH = layout.statusH;
		layout.footerY = 0;
		layout.boxBottom = 0;
		layout.nameY = layout.statsH;
		layout.boxTop = layout.nameY + layout.nameLineH + layout.nameGap;
		layout.contentH = layout.barsH;
		layout.levelH = layout.contentH;
		layout.leftH = layout.contentH;
		layout.footerY = layout.leftH - PAD - layout.statusH;
		layout.boxBottom = layout.footerY - layout.boxToFooterGap;
		layout.boxH = Math.max(48, layout.boxBottom - layout.boxTop);
		return layout;
	}

	public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
		if (this.sbXpOverlay.mouseScrolled(mouseX, mouseY, scrollY)) {
			return true;
		}
		if (!this.leftColumn.showingStatsFace() && hitName(mouseX, mouseY)
			&& this.usernameHistory.state() == UsernameHistory.State.READY
			&& this.historyMaxScroll > 0) {
			int step = 12;
			int delta = scrollY > 0 ? -step : step;
			int next = Math.max(0, Math.min(this.historyMaxScroll, this.historyScroll + delta));
			if (next != this.historyScroll) {
				this.historyScroll = next;
				return true;
			}
		}
		return false;
	}

	record HoverZone(int x, int y, int w, int h, List<PvTooltip.Line> lines) {
	}

	static final class Layout {
		int gap;
		int leftW;
		int levelW;
		int barsW;
		int line;
		int rowH;
		int barsInnerH;
		int barsH;
		int lastSlayerNameY;
		int statsH;
		int profileLineH;
		int statusH;
		int nameLineH;
		int nameGap;
		int boxToFooterGap;
		int footerH;
		int footerY;
		int nameY;
		int boxTop;
		int boxBottom;
		int boxH;
		int leftH;
		int levelH;
		int contentH;
	}
}
