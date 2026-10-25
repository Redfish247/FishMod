/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.home;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.HypixelRanks;
import fishmod.pv.data.PlayerStatsSnapshot;
import fishmod.pv.data.PlayerStatus;
import fishmod.pv.data.ProfileSnapshot;
import fishmod.pv.gui.PlayerModelRenderer;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.SkyBlockLevelColors;
import fishmod.pv.gui.SkyBlockStats;
import fishmod.pv.networth.NetworthBreakdown;
import fishmod.pv.weight.WeightBreakdown;
import fishmod.pv.weight.WeightSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.ToIntFunction;

/** Profile face, left-column flip, and stats-face drawing for Home. */
final class HomeLeftColumn {
	private static final int PAD = 6;
	private static final int FLIP_MS = 480;

	private int weightHitX;
	private int weightHitY;
	private int weightHitW;
	private int weightHitH;
	private int networthHitX;
	private int networthHitY;
	private int networthHitW;
	private int networthHitH;
	private int nameHitX;
	private int nameHitY;
	private int nameHitW;
	private int nameHitH;
	private int statusHitX;
	private int statusHitY;
	private int statusHitW;
	private int statusHitH;
	private int leftHitX;
	private int leftHitY;
	private int leftHitW;
	private int leftHitH;
	private int bankHitX;
	private int bankHitY;
	private int bankHitW;
	private int bankHitH;
	private boolean leftStatsFace;
	private long leftFlipStartMs;
	private boolean leftFlipTarget;
	private final PlayerModelRenderer playerModel = new PlayerModelRenderer();
	private final int[] modelBoxScratch = new int[4];
	private boolean modelLookSet;
	private int modelLookX;
	private int modelLookY;
	private Component cachedNwLine;
	private Component cachedWeightLine;
	private Component cachedStyledName;
	private String cachedStyledNameKey = "";
	private int cachedNameWidth = -1;
	private String cachedNwValue = "";
	private String cachedWeightValue = "";
	private int cachedNwValueW;
	private int cachedWeightValueW;
	private double cachedNwTotal = Double.NaN;
	private double cachedWeightTotal = Double.NaN;
	private WeightSystem cachedWeightFormatSystem;
	private String cachedStatusKey = "";
	private String cachedStatusDrawn = "";

	private static String NW_LABEL;
	private static String WEIGHT_LABEL;
	private static String PURSE_LABEL;
	private static String BANK_LABEL;
	private static String SKILL_AVG_LABEL;
	private static int NW_LABEL_W = -1;
	private static int SKILL_AVG_LABEL_W = -1;
	private static int WEIGHT_LABEL_W = -1;
	private static int PURSE_LABEL_W = -1;
	private static int BANK_LABEL_W = -1;

	void clearCaches() {
		this.cachedStyledName = null;
		this.cachedStyledNameKey = "";
		this.cachedNameWidth = -1;
		this.cachedNwValue = "";
		this.cachedWeightValue = "";
		this.cachedNwTotal = Double.NaN;
		this.cachedWeightTotal = Double.NaN;
		this.cachedStatusKey = "";
		this.cachedNwLine = null;
		this.cachedWeightLine = null;
	}

	void clearStyledNameCache() {
		this.cachedStyledName = null;
		this.cachedStyledNameKey = "";
		this.cachedNameWidth = -1;
	}

	boolean showingStatsFace() {
		if (this.leftFlipStartMs != 0L) {
			float progress = Math.min(1F, (System.currentTimeMillis() - this.leftFlipStartMs) / (float) FLIP_MS);
			float angle = HomeUi.easeInOutCubic(progress) * (float) Math.PI;
			return Math.cos(angle) < 0.0 ? this.leftFlipTarget : this.leftStatsFace;
		}
		return this.leftStatsFace;
	}

	boolean hitName(double mouseX, double mouseY) {
		return !showingStatsFace()
			&& mouseX >= this.nameHitX && mouseX < this.nameHitX + this.nameHitW
			&& mouseY >= this.nameHitY && mouseY < this.nameHitY + this.nameHitH
			&& this.nameHitW > 0;
	}

	/**
	 * Real cursor for the model's look-at. Popups mask the hover mouse to a far off-screen point,
	 * and feeding that to the follow-mouse entity render snaps the model's head and body.
	 * Only applies to the next draw.
	 */
	void setModelLook(int x, int y) {
		this.modelLookSet = true;
		this.modelLookX = x;
		this.modelLookY = y;
	}

	int modelLookX(int hoverX) {
		return this.modelLookSet ? this.modelLookX : hoverX;
	}

	int modelLookY(int hoverY) {
		return this.modelLookSet ? this.modelLookY : hoverY;
	}

	boolean hitStatus(double mouseX, double mouseY) {
		return !showingStatsFace()
			&& mouseX >= this.statusHitX && mouseX < this.statusHitX + this.statusHitW
			&& mouseY >= this.statusHitY && mouseY < this.statusHitY + this.statusHitH
			&& this.statusHitW > 0;
	}

	boolean hitWeight(double mouseX, double mouseY) {
		return mouseX >= this.weightHitX && mouseX < this.weightHitX + this.weightHitW
			&& mouseY >= this.weightHitY && mouseY < this.weightHitY + this.weightHitH;
	}

	boolean hitNetworth(double mouseX, double mouseY) {
		return mouseX >= this.networthHitX && mouseX < this.networthHitX + this.networthHitW
			&& mouseY >= this.networthHitY && mouseY < this.networthHitY + this.networthHitH;
	}

	boolean hitBank(double mouseX, double mouseY) {
		return mouseX >= this.bankHitX && mouseX < this.bankHitX + this.bankHitW
			&& mouseY >= this.bankHitY && mouseY < this.bankHitY + this.bankHitH;
	}

	boolean contains(double mouseX, double mouseY) {
		return mouseX >= this.leftHitX && mouseX < this.leftHitX + this.leftHitW
			&& mouseY >= this.leftHitY && mouseY < this.leftHitY + this.leftHitH;
	}

	/** Stats face removed from the profile page; the model panel no longer flips. */
	boolean beginFlip() {
		if (true) {
			return false;
		}
		if (this.leftFlipStartMs != 0L) {
			return true;
		}
		this.leftFlipTarget = !this.leftStatsFace;
		this.leftFlipStartMs = System.currentTimeMillis();
		return true;
	}

	void draw(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		HomePage.Layout layout,
		int mouseX,
		int mouseY,
		ProfileSnapshot snapshot,
		PlayerStatsSnapshot playerStats,
		PlayerStatus playerStatus,
		JsonObject playerRank,
		ItemStack[] armor,
		NetworthBreakdown activeNw,
		WeightBreakdown senither,
		WeightBreakdown lily,
		WeightSystem weightSystem,
		String loadError,
		float openScale,
		float openPivotX,
		float openPivotY,
		float uiScale,
		float uiOffsetX,
		float uiOffsetY,
		List<HomePage.HoverZone> zones
	) {
		this.leftHitX = x;
		this.leftHitY = y;
		this.leftHitW = w;
		this.leftHitH = h;

		boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		float flipProgress = 0F;
		boolean animating = this.leftFlipStartMs != 0L;
		if (animating) {
			flipProgress = Math.min(1F, (System.currentTimeMillis() - this.leftFlipStartMs) / (float) FLIP_MS);
			if (flipProgress >= 1F) {
				this.leftStatsFace = this.leftFlipTarget;
				this.leftFlipStartMs = 0L;
				animating = false;
				flipProgress = 0F;
			}
		}
		float eased = animating ? HomeUi.easeInOutCubic(flipProgress) : 0F;
		float angle = eased * (float) Math.PI;
		boolean showStats = animating
			? (Math.cos(angle) < 0.0 ? this.leftFlipTarget : this.leftStatsFace)
			: this.leftStatsFace;
		float scaleX = 1F;
		float scaleY = 1F;
		if (animating) {
			scaleX = Math.max(0.04F, Math.abs((float) Math.cos(angle)));
			scaleY = 1F - (1F - scaleX) * 0.06F;
		}

		float cxFlip = x + w / 2F;
		float cyFlip = y + h / 2F;
		g.pose().pushMatrix();
		g.pose().translate(cxFlip, cyFlip);
		g.pose().scale(scaleX, scaleY);
		g.pose().translate(-cxFlip, -cyFlip);

		PvDraw.innerPanel(g, x, y, w, h);

		boolean drawModelAfter = false;
		int modelX0 = 0;
		int modelY0 = 0;
		int modelX1 = 0;
		int modelY1 = 0;
		if (showStats) {
			this.networthHitW = 0;
			this.weightHitW = 0;
			this.nameHitW = 0;
			this.statusHitW = 0;
			drawStatsFace(g, font, x, y, w, h, playerStats, zones);
		} else {
			int[] modelBox = drawProfileFace(
				g, font, x, y, w, h, layout, mouseX, mouseY,
				snapshot, playerStatus, playerRank, activeNw, senither, lily, weightSystem, loadError, zones
			);
			if (modelBox != null) {
				drawModelAfter = true;
				modelX0 = modelBox[0];
				modelY0 = modelBox[1];
				modelX1 = modelBox[2];
				modelY1 = modelBox[3];
			}
		}

		g.pose().popMatrix();

		// Entity rendering ignores the GUI pose matrix - apply the same flip in screen space after pop.
		if (drawModelAfter && snapshot.playerUuid() != null) {
			this.playerModel.draw(
				g,
				snapshot.playerUuid(),
				snapshot.playerName(),
				modelX0,
				modelY0,
				modelX1,
				modelY1,
				modelLookX(mouseX),
				modelLookY(mouseY),
				armor.length > 3 ? armor[3] : ItemStack.EMPTY,
				armor.length > 2 ? armor[2] : ItemStack.EMPTY,
				armor.length > 1 ? armor[1] : ItemStack.EMPTY,
				armor.length > 0 ? armor[0] : ItemStack.EMPTY,
				scaleX,
				scaleY,
				cxFlip,
				cyFlip,
				openScale,
				openPivotX,
				openPivotY,
				uiScale,
				uiOffsetX,
				uiOffsetY
			);
		}
		this.modelLookSet = false;
	}

	private int[] drawProfileFace(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		HomePage.Layout layout,
		int mouseX,
		int mouseY,
		ProfileSnapshot snapshot,
		PlayerStatus playerStatus,
		JsonObject playerRank,
		NetworthBreakdown activeNw,
		WeightBreakdown senither,
		WeightBreakdown lily,
		WeightSystem weightSystem,
		String loadError,
		List<HomePage.HoverZone> zones
	) {
		ensureStaticLabels(font);
		int ty = y + PAD;
		double nwTotal = activeNw.total();
		String nwValue;
		if (Double.doubleToLongBits(nwTotal) != Double.doubleToLongBits(this.cachedNwTotal)
			|| this.cachedNwValue.isEmpty()) {
			this.cachedNwTotal = nwTotal;
			if (nwTotal > 0) {
				nwValue = FormatUtil.shortCoins(nwTotal);
			} else if (activeNw.note() != null && activeNw.note().toLowerCase(java.util.Locale.ROOT).contains("loading")) {
				nwValue = "...";
			} else {
				nwValue = "-";
			}
			if (loadError != null && !loadError.isBlank() && nwTotal <= 0
				&& (activeNw.note() == null || !activeNw.note().toLowerCase(java.util.Locale.ROOT).contains("loading"))) {
				nwValue = "-";
			}
			this.cachedNwValue = nwValue;
			this.cachedNwValueW = PvDraw.widthBold(font, nwValue);
			this.cachedNwLine = Component.empty()
				.append(PvDraw.styled(NW_LABEL, 0xFF55FF55, false))
				.append(PvDraw.styled(nwValue, PvDraw.COLOR_GOLD, true));
		} else {
			nwValue = this.cachedNwValue;
			if (this.cachedNwLine == null) {
				this.cachedNwLine = Component.empty()
					.append(PvDraw.styled(NW_LABEL, 0xFF55FF55, false))
					.append(PvDraw.styled(nwValue, PvDraw.COLOR_GOLD, true));
			}
		}

		double weightTotal = weightSystem == WeightSystem.SENITHER ? senither.total() : lily.total();
		String weightValue;
		if (this.cachedWeightFormatSystem != weightSystem
			|| Double.doubleToLongBits(weightTotal) != Double.doubleToLongBits(this.cachedWeightTotal)
			|| this.cachedWeightValue.isEmpty()) {
			this.cachedWeightFormatSystem = weightSystem;
			this.cachedWeightTotal = weightTotal;
			weightValue = FormatUtil.weight(weightTotal);
			if (snapshot.weightText().equals("...") && (loadError == null || loadError.isBlank())) {
				weightValue = "...";
			}
			if (loadError != null && !loadError.isBlank() && senither.total() <= 0 && lily.total() <= 0) {
				weightValue = "-";
			}
			this.cachedWeightValue = weightValue;
			this.cachedWeightValueW = PvDraw.widthBold(font, weightValue);
			this.cachedWeightLine = Component.empty()
				.append(PvDraw.styled(WEIGHT_LABEL, 0xFF55FF55, false))
				.append(PvDraw.styled(weightValue, PvDraw.COLOR_GOLD, true));
		} else {
			weightValue = this.cachedWeightValue;
			if (this.cachedWeightLine == null) {
				this.cachedWeightLine = Component.empty()
					.append(PvDraw.styled(WEIGHT_LABEL, 0xFF55FF55, false))
					.append(PvDraw.styled(weightValue, PvDraw.COLOR_GOLD, true));
			}
		}

		int nwLineW = NW_LABEL_W + this.cachedNwValueW;
		int nwX = x + (w - nwLineW) / 2;
		g.text(font, this.cachedNwLine, nwX, ty, PvDraw.COLOR_WHITE, false);
		this.networthHitX = nwX;
		this.networthHitY = ty;
		this.networthHitW = nwLineW;
		this.networthHitH = font.lineHeight;

		ty += layout.line;
		int weightLineW = WEIGHT_LABEL_W + this.cachedWeightValueW;
		int weightX = x + (w - weightLineW) / 2;
		g.text(font, this.cachedWeightLine, weightX, ty, PvDraw.COLOR_WHITE, false);
		this.weightHitX = weightX;
		this.weightHitY = ty;
		this.weightHitW = weightLineW;
		this.weightHitH = font.lineHeight;

		ty += layout.line;
		drawSkillAverage(g, font, x, ty, w, snapshot, zones);

		ty += layout.line;
		String bankValue = FormatUtil.shortCoins(snapshot.bankCoins());
		Component bankLine = Component.empty()
			.append(PvDraw.styled(BANK_LABEL, 0xFF55FF55, false))
			.append(PvDraw.styled(bankValue, PvDraw.COLOR_GOLD, true));
		int bankW = BANK_LABEL_W + font.width(PvDraw.styled(bankValue, PvDraw.COLOR_GOLD, true));
		int bankX = x + (w - bankW) / 2;
		g.text(font, bankLine, bankX, ty, PvDraw.COLOR_WHITE, false);
		this.bankHitX = bankX;
		this.bankHitY = ty;
		this.bankHitW = bankW;
		this.bankHitH = font.lineHeight;

		int cx = x + w / 2;
		int boxW = Math.min(w - PAD * 2, Math.max(64, w - 20));
		int boxX = cx - boxW / 2;
		int boxTop = y + layout.boxTop;
		int boxH = layout.boxH;

		Component nameComp = styledPlayerName(snapshot, playerRank);
		int nameW = this.cachedNameWidth;
		if (nameW < 0) {
			nameW = font.width(nameComp);
			this.cachedNameWidth = nameW;
		}
		g.text(font, nameComp, cx - nameW / 2, y + layout.nameY, PvDraw.COLOR_WHITE, false);
		this.nameHitX = cx - nameW / 2;
		this.nameHitY = y + layout.nameY;
		this.nameHitW = nameW;
		this.nameHitH = font.lineHeight;

		PvDraw.roundedFill(g, boxX, boxTop, boxW, boxH, 3, 0xFF15151E);
		PvDraw.roundedRing(g, boxX, boxTop, boxW, boxH, 3, PvDraw.COLOR_BORDER, PvDraw.C_ALL);

		int[] modelBox = null;
		if (snapshot.playerUuid() != null) {
			this.modelBoxScratch[0] = boxX + 2;
			this.modelBoxScratch[1] = boxTop + 2;
			this.modelBoxScratch[2] = boxX + boxW - 2;
			this.modelBoxScratch[3] = boxTop + boxH - 2;
			modelBox = this.modelBoxScratch;
		} else {
			PvDraw.textCentered(
				g, font,
				Component.translatable("betterpv.home.player").getString(),
				cx, boxTop + boxH / 2 - font.lineHeight / 2,
				PvDraw.COLOR_MUTED
			);
		}

		int footerY = y + layout.footerY;
		String statusLabel = playerStatus.buttonLabel();
		int statusColor = playerStatus.buttonColor(
			PvDraw.COLOR_ACCENT, HomeTooltips.ENABLED_GREEN, HomeTooltips.OFFLINE_RED, PvDraw.COLOR_MUTED);
		int statusW = statusBoxWidth(boxW);
		int statusH = layout.statusH;
		int statusX = cx - statusW / 2;
		int statusY = footerY;
		this.statusHitX = statusX;
		this.statusHitY = statusY;
		this.statusHitW = statusW;
		this.statusHitH = statusH;
		boolean statusHover = mouseX >= statusX && mouseX < statusX + statusW
			&& mouseY >= statusY && mouseY < statusY + statusH;
		PvDraw.fill(g, statusX, statusY, statusW, statusH, PvDraw.CELL);
		g.outline(statusX, statusY, statusW, statusH,
			statusHover ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER);
		String statusKey = statusLabel + "|" + statusW;
		if (!statusKey.equals(this.cachedStatusKey)) {
			this.cachedStatusKey = statusKey;
			this.cachedStatusDrawn = statusText(font::width, statusLabel, statusW);
		}
		PvDraw.textCentered(g, font, this.cachedStatusDrawn, statusX + statusW / 2,
			statusY + (statusH - font.lineHeight) / 2, statusColor);
		return modelBox;
	}

	static int statusBoxWidth(int boxW) {
		return Math.min(boxW, Math.max(48, boxW - 8));
	}

	// Full label stays in the hover tooltip; the box itself never grows.
	static String statusText(ToIntFunction<String> width, String label, int statusW) {
		return trimToWidth(width, label, Math.max(8, statusW - 8));
	}

	private static void drawSkillAverage(
		GuiGraphicsExtractor g, Font font, int x, int ty, int w, ProfileSnapshot snapshot, List<HomePage.HoverZone> zones
	) {
		boolean loaded = snapshot.skillsLoaded();
		double average = snapshot.skillAverage(true);
		String value = loaded ? FormatUtil.twoDecimals(average) : "...";
		Component line = Component.empty()
			.append(PvDraw.styled(SKILL_AVG_LABEL, 0xFF55FF55, false))
			.append(PvDraw.styled(value, PvDraw.COLOR_GOLD, true));
		int lineW = SKILL_AVG_LABEL_W + PvDraw.widthBold(font, value);
		int lineX = x + (w - lineW) / 2;
		g.text(font, line, lineX, ty, PvDraw.COLOR_WHITE, false);
		if (!loaded) {
			return;
		}
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.title("Skill Average", 0xFF55FF55));
		tip.add(PvTooltip.Line.divider());
		tip.add(PvTooltip.Line.row("Skill Avg", PvDraw.COLOR_MUTED, FormatUtil.twoDecimals(average), PvDraw.COLOR_GOLD));
		tip.add(PvTooltip.Line.row("Overflow Skill Avg", PvDraw.COLOR_MUTED,
			FormatUtil.twoDecimals(snapshot.overflowSkillAverage()), PvDraw.COLOR_GOLD));
		tip.add(PvTooltip.Line.blank());
		for (ProfileSnapshot.SkillEntry skill : snapshot.skills()) {
			int overflow = (int) Math.floor(skill.overflowLevel());
			String level = skill.maxed() && overflow > skill.level()
				? skill.level() + " (" + overflow + ")"
				: String.valueOf(skill.level());
			tip.add(PvTooltip.Line.row(skill.name(), PvDraw.COLOR_MUTED, level,
				skill.maxed() ? PvDraw.COLOR_GOLD : PvDraw.COLOR_TEXT));
		}
		zones.add(new HomePage.HoverZone(lineX, ty, lineW, font.lineHeight, tip));
	}

	private void drawStatsFace(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		PlayerStatsSnapshot playerStats,
		List<HomePage.HoverZone> zones
	) {
		List<PlayerStatsSnapshot.Entry> entries = playerStats.entries();
		if (entries.isEmpty()) {
			PvDraw.textCentered(g, font, "No stats", x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			return;
		}
		int cols = 2;
		int colGap = 8;
		int colW = (w - PAD * 2 - colGap) / cols;
		int symbolSlot = 0;
		for (PlayerStatsSnapshot.Entry entry : entries) {
			SkyBlockStats.StatStyle style = SkyBlockStats.stat(entry.id());
			int symSlotW = style.boldSymbol()
				? PvDraw.widthBold(font, style.symbol())
				: font.width(style.symbol());
			symbolSlot = Math.max(symbolSlot, symSlotW);
		}
		symbolSlot = Math.max(symbolSlot, font.width("⚔"));
		int labelGap = 3;
		int rows = (entries.size() + cols - 1) / cols;
		int rowH = font.lineHeight + 1;
		int contentH = rows * rowH;
		int availH = h - PAD * 2;
		if (rows > 0 && contentH < availH) {
			rowH = Math.max(rowH, availH / rows);
			contentH = rows * rowH;
		}
		int ty = y + Math.max(PAD, (h - contentH) / 2);
		for (int i = 0; i < entries.size(); i++) {
			PlayerStatsSnapshot.Entry entry = entries.get(i);
			int col = i % cols;
			int row = i / cols;
			int bx = x + PAD + col * (colW + colGap);
			int by = ty + row * rowH;
			SkyBlockStats.StatStyle style = SkyBlockStats.stat(entry.id());
			String symbol = style.symbol();
			int symW = font.width(symbol);
			if (style.boldSymbol()) {
				symW = PvDraw.widthBold(font, symbol);
				PvDraw.textBold(g, font, symbol, bx + (symbolSlot - symW) / 2, by, style.color());
			} else {
				PvDraw.text(g, font, symbol, bx + (symbolSlot - symW) / 2, by, style.color());
			}
			PvDraw.text(g, font, entry.label(), bx + symbolSlot + labelGap, by, style.color());
			String value = entry.present() ? formatStat(entry.value().getAsDouble()) : "-";
			PvDraw.textRight(g, font, value, bx + colW, by, PvDraw.COLOR_WHITE);
			String tipValue = entry.present() ? formatStatFull(entry.value().getAsDouble()) : "-";
			List<PvTooltip.Line> tip = new ArrayList<>(SkyBlockStats.tooltipLines(entry.id(), tipValue));
			tip.add(PvTooltip.Line.blank());
			if (!entry.present()) {
				tip.add(PvTooltip.Line.meta("Not in the API"));
			} else {
				tip.add(PvTooltip.Line.meta("Excludes held item, set bonuses,"));
				tip.add(PvTooltip.Line.meta("temporary buffs and location bonuses."));
			}
			zones.add(new HomePage.HoverZone(
				bx,
				by,
				colW,
				Math.max(font.lineHeight, rowH - 1),
				tip
			));
		}
	}

	private Component styledPlayerName(ProfileSnapshot snapshot, JsonObject playerRank) {
		String name = snapshot.playerName();
		UUID uuid = snapshot.playerUuid();
		String rankKey = playerRank == null ? "" : Integer.toHexString(System.identityHashCode(playerRank));
		String key = (uuid == null ? "" : uuid.toString()) + "|" + (name == null ? "" : name) + "|" + rankKey
			+ "|" + snapshot.skyBlockLevel()
			+ "|" + (uuid == null ? 0 : fishmod.cosmetic.badge.BadgeManager.INSTANCE
				.badgesFor(uuid.toString().replace("-", "")).size())
			+ "|" + (uuid == null ? "" : String.valueOf(tabEmblem(uuid)));
		if (key.equals(this.cachedStyledNameKey) && this.cachedStyledName != null) {
			return this.cachedStyledName;
		}
		Component base = Component.literal(name == null ? "" : name);
		Component namePart = base;
		MutableComponent styled = Component.empty();
		// SkyBlock level bracket in place of the Hypixel rank tag, e.g. [603].
		boolean nameAlreadyHasBracket = namePart.getString().startsWith("[");
		if (!nameAlreadyHasBracket) {
			int level = snapshot.skyBlockLevel();
			styled.append(PvTooltip.Span.of("[", 0xFF555555).toComponent());
			styled.append(PvTooltip.Span.of(String.valueOf(level), SkyBlockLevelColors.colorFor(level)).toComponent());
			styled.append(PvTooltip.Span.of("] ", 0xFF555555).toComponent());
		}
		if (namePart == base && playerRank != null && !nameAlreadyHasBracket) {
			// Cosmetics did nothing special, so use the HypixelRanks name colour (grey with no rank).
			namePart = PvTooltip.Span.of(name == null ? "" : name, HypixelRanks.nameColor(playerRank)).toComponent();
		}
		if (uuid != null) {
			// FishMod badges, then the name, then any emblem the player wears in the tab list.
			for (fishmod.cosmetic.badge.BadgeDef badge
				: fishmod.cosmetic.badge.BadgeManager.INSTANCE.badgesFor(uuid.toString().replace("-", ""))) {
				styled.append(Component.literal(badge.getSymbol() + " ").withColor(0xFF000000 | badge.getColorRgb()));
			}
		}
		styled.append(namePart);
		Component emblem = uuid == null ? null : tabEmblem(uuid);
		if (emblem != null) {
			styled.append(Component.literal(" "));
			styled.append(emblem);
		}
		this.cachedStyledNameKey = key;
		this.cachedStyledName = styled;
		this.cachedNameWidth = -1;
		return styled;
	}

	/** Trailing symbol after the name in the tab list (Hypixel emblem), with its colour. */
	private static Component tabEmblem(UUID uuid) {
		var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
		if (connection == null) {
			return null;
		}
		for (var info : connection.getOnlinePlayers()) {
			if (!uuid.equals(info.getProfile().id())) {
				continue;
			}
			Component display = info.getTabListDisplayName();
			if (display == null) {
				return null;
			}
			String plain = display.getString().stripTrailing();
			int end = plain.length();
			int start = end;
			while (start > 0) {
				char c = plain.charAt(start - 1);
				if (Character.isLetterOrDigit(c) || Character.isWhitespace(c) || c == '_' || c == ']' || c == ')') {
					break;
				}
				start--;
			}
			if (start == end) {
				return null;
			}
			String symbol = plain.substring(start, end);
			int[] color = {0xFFFFFFFF};
			display.visit((style, text) -> {
				if (text != null && !text.isBlank() && symbol.indexOf(text.strip().charAt(0)) >= 0
					&& style != null && style.getColor() != null) {
					color[0] = 0xFF000000 | style.getColor().getValue();
				}
				return java.util.Optional.empty();
			}, net.minecraft.network.chat.Style.EMPTY);
			return Component.literal(symbol).withColor(color[0]);
		}
		return null;
	}

	private static void ensureStaticLabels(Font font) {
		if (NW_LABEL == null) {
			NW_LABEL = Component.translatable("betterpv.home.networth_label").getString();
			WEIGHT_LABEL = Component.translatable("betterpv.home.weight_label").getString();
			PURSE_LABEL = Component.translatable("betterpv.home.purse_label").getString();
			BANK_LABEL = Component.translatable("betterpv.home.bank_label").getString();
			SKILL_AVG_LABEL = Component.translatable("betterpv.home.skill_avg_label").getString();
		}
		if (NW_LABEL_W < 0) {
			NW_LABEL_W = font.width(NW_LABEL);
			SKILL_AVG_LABEL_W = font.width(SKILL_AVG_LABEL);
			WEIGHT_LABEL_W = font.width(WEIGHT_LABEL);
			PURSE_LABEL_W = font.width(PURSE_LABEL);
			BANK_LABEL_W = font.width(BANK_LABEL);
		}
	}

	private static String trimToWidth(ToIntFunction<String> width, String text, int maxW) {
		if (text == null) {
			return "";
		}
		if (width.applyAsInt(text) <= maxW) {
			return text;
		}
		String ellipsis = "...";
		int ellipsisW = width.applyAsInt(ellipsis);
		if (maxW <= ellipsisW) {
			return ellipsis;
		}
		int lo = 0;
		int hi = text.length();
		while (lo < hi) {
			int mid = (lo + hi + 1) >>> 1;
			if (width.applyAsInt(text.substring(0, mid)) + ellipsisW <= maxW) {
				lo = mid;
			} else {
				hi = mid - 1;
			}
		}
		return text.substring(0, lo).stripTrailing() + ellipsis;
	}

	/** Compact rounded stats: {@code 3908.7 → 3.9k}, {@code 469.5 → 470}, {@code 0.0 → 0}. */
	private static String formatStat(double value) {
		double abs = Math.abs(value);
		if (abs >= 1_000_000_000L) {
			return formatCompact(value / 1_000_000_000L, "b");
		}
		if (abs >= 1_000_000L) {
			return formatCompact(value / 1_000_000L, "m");
		}
		if (abs >= 1_000L) {
			return formatCompact(value / 1_000L, "k");
		}
		return String.valueOf(Math.round(value));
	}

	/** Full tooltip number with commas (and one decimal when meaningful). */
	private static String formatStatFull(double value) {
		double rounded = Math.round(value * 10.0) / 10.0;
		if (Math.abs(rounded - Math.rint(rounded)) < 0.05) {
			return FormatUtil.commas(Math.round(rounded));
		}
		long whole = (long) Math.floor(Math.abs(rounded));
		String dec = String.format(java.util.Locale.US, "%.1f", Math.abs(rounded) - whole).substring(1);
		String sign = rounded < 0 ? "-" : "";
		return sign + FormatUtil.commas(whole) + dec;
	}

	private static String formatCompact(double scaled, String suffix) {
		double one = Math.round(scaled * 10.0) / 10.0;
		if (Math.abs(one - Math.rint(one)) < 0.05) {
			return ((long) Math.rint(one)) + suffix;
		}
		return String.format(java.util.Locale.US, "%.1f%s", one, suffix);
	}
}
