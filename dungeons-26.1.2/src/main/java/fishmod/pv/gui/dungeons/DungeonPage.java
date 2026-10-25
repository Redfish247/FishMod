/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.dungeons;

import fishmod.pv.data.DungeonSnapshot;
import fishmod.pv.data.FormatUtil;
import fishmod.pv.dungeons.CataXpCalculator;
import fishmod.pv.dungeons.CataXpMath;
import fishmod.pv.dungeons.ClassLevelQuery;
import fishmod.pv.dungeons.ClassXpCalculator;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.essence.EssenceShopColumn;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public final class DungeonPage {
	private enum FieldFocus {
		NONE,
		CATA,
		CLASS
	}

	private static final int PAD = 6;
	private static final int BAR_AFTER = 4;
	private static final int GAP = 6;
	private static final int CALC_H = 78;
	private static final int FLOOR_GAP_MAX = 6;
	/** Height of the personal-best line under each floor name. */
	private static final int PB_H = 8;
	private static final float PB_SCALE = 0.75F;
	private static final int FIELD_H = 14;
	private static final int BTN_W = 36;
	private static final int HELP_GAP = 3;
	private static final int HELP_TIP_MAX_W = 180;
	/** Enough for {@code cata99} / {@code C55}. */
	private static final int CATA_MAX_LEN = 6;
	private static final int CLASS_MAX_LEN = 32;
	private static final int HINT_COLOR = 0xFF5A5A68;
	private static final int HELP_ICON_COLOR = 0xFF9A9AAC;
	private static final int HELP_ICON_HOVER = 0xFF5B8CFF;
	private static final String HELP_MARK = "(?)";
	private static final int FLIP_MS = 480;
	/** Wither essence: light gray so the header reads at the same weight as Ice/Spider/Dragon. */
	private static final int WITHER_COLOR = 0xFFAAAAAA;
	/** Undead essence - pink. */
	private static final int UNDEAD_COLOR = 0xFFFF8EC8;
	private static final int ICE_COLOR = 0xFF55FFFF;
	private static final int SPIDER_COLOR = 0xFF55FF55;
	private static final int DRAGON_COLOR = 0xFFFF5555;
	private static final int NORMAL_FLOOR_COLOR = 0xFF7CB87C;
	private static final int MASTER_FLOOR_COLOR = 0xFFB85A5A;

	private DungeonSnapshot data = DungeonSnapshot.empty();
	private final List<HoverZone> zones = new ArrayList<>();
	private final DungeonCalcOverlay overlay = new DungeonCalcOverlay();

	private final CalcRow cataRow = new CalcRow();
	private final CalcRow classRow = new CalcRow();

	private String cataLevelText = "";
	private String classLevelText = "";
	private FieldFocus focus = FieldFocus.NONE;
	private boolean replaceOnType;

	private boolean runsEssenceFace;
	private long runsFlipStartMs;
	private boolean runsFlipTarget;
	private int runsHitX;
	private int runsHitY;
	private int runsHitW;
	private int runsHitH;

	private boolean classEssenceFace;
	private long classFlipStartMs;
	private boolean classFlipTarget;
	private int classHitX;
	private int classHitY;
	private int classHitW;
	private int classHitH;

	public void apply(DungeonSnapshot data) {
		this.data = data == null ? DungeonSnapshot.empty() : data;
	}

	public DungeonCalcOverlay overlay() {
		return this.overlay;
	}

	public void blurField() {
		this.focus = FieldFocus.NONE;
		this.replaceOnType = false;
	}

	public void runCataCalc() {
		blurField();
		int target = parseTarget(this.cataLevelText, this.data.cataLevel() + 1);
		CataXpCalculator.Result result = CataXpCalculator.calculate(this.data, target);
		this.overlay.open(toView(result));
	}

	public void runClassCalc() {
		blurField();
		ClassLevelQuery.Parsed query = ClassLevelQuery.parse(this.classLevelText);
		if (query == null) {
			this.overlay.open(DungeonCalcOverlay.View.error(
				"betterpv.dungeons.calc.class",
				Component.translatable("betterpv.dungeons.calc.class_invalid").getString()
			));
			return;
		}
		if (query.classAverage()) {
			ClassXpCalculator.AverageResult result = ClassXpCalculator.calculateAverage(this.data, query.targetLevel());
			this.overlay.open(toView(result));
			return;
		}
		ClassXpCalculator.Result result = ClassXpCalculator.calculate(this.data, query);
		this.overlay.open(toView(result));
	}

	public boolean mouseScrolled(double delta) {
		return this.overlay.mouseScrolled(delta);
	}

	public boolean mouseClicked(double mx, double my) {
		if (this.overlay.isOpen()) {
			return this.overlay.mouseClicked(mx, my);
		}
		if (hitButton(this.cataRow, mx, my)) {
			runCataCalc();
			return true;
		}
		if (hitButton(this.classRow, mx, my)) {
			runClassCalc();
			return true;
		}
		if (hitField(this.cataRow, mx, my)) {
			this.focus = FieldFocus.CATA;
			this.replaceOnType = !this.cataLevelText.isEmpty();
			return true;
		}
		if (hitField(this.classRow, mx, my)) {
			this.focus = FieldFocus.CLASS;
			this.replaceOnType = !this.classLevelText.isEmpty();
			return true;
		}
		if (this.focus != FieldFocus.NONE) {
			blurField();
			return true;
		}
		if (clickRunsPanel(mx, my)) {
			return true;
		}
		if (clickClassPanel(mx, my)) {
			return true;
		}
		return false;
	}

	private boolean clickRunsPanel(double mouseX, double mouseY) {
		if (this.runsHitW <= 0 || this.runsHitH <= 0) {
			return false;
		}
		if (mouseX < this.runsHitX || mouseX >= this.runsHitX + this.runsHitW
			|| mouseY < this.runsHitY || mouseY >= this.runsHitY + this.runsHitH) {
			return false;
		}
		if (true) {
			return false;
		}
		if (this.runsFlipStartMs != 0L) {
			return true;
		}
		this.runsFlipTarget = !this.runsEssenceFace;
		this.runsFlipStartMs = System.currentTimeMillis();
		return true;
	}

	private boolean clickClassPanel(double mouseX, double mouseY) {
		if (this.classHitW <= 0 || this.classHitH <= 0) {
			return false;
		}
		if (mouseX < this.classHitX || mouseX >= this.classHitX + this.classHitW
			|| mouseY < this.classHitY || mouseY >= this.classHitY + this.classHitH) {
			return false;
		}
		if (true) {
			return false;
		}
		if (this.classFlipStartMs != 0L) {
			return true;
		}
		this.classFlipTarget = !this.classEssenceFace;
		this.classFlipStartMs = System.currentTimeMillis();
		return true;
	}

	public boolean isCalcButton(double mx, double my) {
		return !this.overlay.isOpen()
			&& (hitButton(this.cataRow, mx, my) || hitButton(this.classRow, mx, my));
	}

	public boolean charTyped(char codepoint) {
		if (this.focus == FieldFocus.NONE || this.overlay.isOpen()) {
			return false;
		}
		if (this.focus == FieldFocus.CATA) {
			if (!isCataChar(codepoint)) {
				return false;
			}
			if (this.replaceOnType) {
				this.cataLevelText = "";
				this.replaceOnType = false;
			}
			if (this.cataLevelText.length() >= CATA_MAX_LEN) {
				return true;
			}
			this.cataLevelText += codepoint;
			return true;
		}
		if (this.focus == FieldFocus.CLASS) {
			if (!isClassChar(codepoint)) {
				return false;
			}
			if (this.replaceOnType) {
				this.classLevelText = "";
				this.replaceOnType = false;
			}
			if (this.classLevelText.length() >= CLASS_MAX_LEN) {
				return true;
			}
			this.classLevelText += codepoint;
			return true;
		}
		return false;
	}

	public boolean keyPressed(int key) {
		if (this.overlay.isOpen()) {
			if (key == 256) {
				this.overlay.close();
				return true;
			}
			return false;
		}
		if (this.focus == FieldFocus.NONE) {
			return false;
		}
		if (key == 256) { // Esc
			blurField();
			return true;
		}
		if (key == 257 || key == 335) { // Enter
			if (this.focus == FieldFocus.CLASS) {
				runClassCalc();
			} else {
				runCataCalc();
			}
			return true;
		}
		if (key == 259) { // Backspace
			if (this.replaceOnType) {
				if (this.focus == FieldFocus.CLASS) {
					this.classLevelText = "";
				} else {
					this.cataLevelText = "";
				}
				this.replaceOnType = false;
				return true;
			}
			if (this.focus == FieldFocus.CLASS) {
				if (!this.classLevelText.isEmpty()) {
					this.classLevelText = this.classLevelText.substring(0, this.classLevelText.length() - 1);
				}
			} else if (!this.cataLevelText.isEmpty()) {
				this.cataLevelText = this.cataLevelText.substring(0, this.cataLevelText.length() - 1);
			}
			return true;
		}
		return false;
	}

	private static boolean isClassChar(char c) {
		return (c >= '0' && c <= '9')
			|| (c >= 'a' && c <= 'z')
			|| (c >= 'A' && c <= 'Z')
			|| c == ' ';
	}

	/** Digits plus optional {@code C}/{@code cata} prefix for overflow targets like {@code C55}. */
	private static boolean isCataChar(char c) {
		return (c >= '0' && c <= '9')
			|| (c >= 'a' && c <= 'z')
			|| (c >= 'A' && c <= 'Z');
	}

	private static boolean hitButton(CalcRow row, double mx, double my) {
		return mx >= row.btnX && mx < row.btnX + row.btnW
			&& my >= row.btnY && my < row.btnY + row.btnH;
	}

	private static boolean hitField(CalcRow row, double mx, double my) {
		return mx >= row.fieldX && mx < row.fieldX + row.fieldW
			&& my >= row.fieldY && my < row.fieldY + row.fieldH;
	}

	private static boolean hitHelp(CalcRow row, double mx, double my) {
		return mx >= row.helpX && mx < row.helpX + row.helpW
			&& my >= row.helpY && my < row.helpY + row.helpH;
	}

	private static DungeonCalcOverlay.View toView(CataXpCalculator.Result result) {
		List<DungeonCalcOverlay.FloorLine> floors = new ArrayList<>();
		for (CataXpCalculator.FloorEstimate floor : result.floors()) {
			floors.add(new DungeonCalcOverlay.FloorLine(
				floor.label(), floor.runsNeeded(), floor.xpPerRun(), floor.master()
			));
		}
		return DungeonCalcOverlay.View.single(
			"betterpv.dungeons.calc.result_title",
			result.targetLevel(),
			result.xpNeeded(),
			result.mayorLabel() == null || result.mayorLabel().isBlank()
				? ""
				: "Mayor: " + result.mayorLabel(),
			floors,
			null
		);
	}

	private static DungeonCalcOverlay.View toView(ClassXpCalculator.Result result) {
		List<DungeonCalcOverlay.FloorLine> floors = new ArrayList<>();
		for (ClassXpCalculator.FloorEstimate floor : result.floors()) {
			floors.add(new DungeonCalcOverlay.FloorLine(
				floor.label(), floor.runsNeeded(), floor.xpPerRun(), floor.master()
			));
		}
		return DungeonCalcOverlay.View.single(
			"betterpv.dungeons.calc.class_result_title",
			result.className() + " " + result.targetLevel(),
			result.xpNeeded(),
			result.modsLabel() == null ? "" : result.modsLabel(),
			floors,
			result.classId()
		);
	}

	private static DungeonCalcOverlay.View toView(ClassXpCalculator.AverageResult result) {
		List<DungeonCalcOverlay.ClassBlock> blocks = new ArrayList<>();
		for (ClassXpCalculator.ClassRuns clazz : result.classes()) {
			blocks.add(new DungeonCalcOverlay.ClassBlock(
				clazz.classId(),
				clazz.className(),
				clazz.currentLevel(),
				clazz.selectedRuns()
			));
		}
		String titleArg = result.targetLevel() + " · " + result.floorLabel();
		return DungeonCalcOverlay.View.average(
			"betterpv.dungeons.calc.ca_result_title",
			titleArg,
			result.modsLabel() == null ? "" : result.modsLabel(),
			blocks,
			result.totalRuns(),
			result.floorLabel(),
			result.timedOut()
		);
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
		int screenH
	) {
		this.zones.clear();

		// Catacombs / class column on the left, calculators and floor runs on the right.
		int rightW = Math.max(120, w * 38 / 100);
		int leftW = w - rightW - GAP;
		int leftX = x + rightW + GAP;
		int rightX = x;

		// Calculators removed (commands cover them); floors take the whole panel.
		int calcH = 0;
		int headerH = font.lineHeight + 4;
		int lineH = font.lineHeight;
		int runsY = y;
		int runsH = h;

		int firstFloorY = runsY + PAD + headerH;
		int totalY = runsY + runsH - PAD - lineH;
		int floorBlock = 7 * (lineH + PB_H);
		int stretch = totalY - firstFloorY - floorBlock;
		int gap;
		int totalGap;
		if (stretch < 14) {
			gap = 2;
			totalGap = 2;
			totalY = firstFloorY + floorBlock + 6 * gap + totalGap;
		} else {
			gap = Math.max(2, Math.min(FLOOR_GAP_MAX, stretch / 7));
			int usedGaps = gap * 6;
			totalGap = Math.max(gap, stretch - usedGaps);
			totalY = firstFloorY + floorBlock + usedGaps + totalGap;
		}
		FloorLayout floors = new FloorLayout(firstFloorY, lineH, gap, totalY, PB_H);
		drawRunsPanel(g, font, leftX, runsY, leftW, runsH, floors, mouseX, mouseY);
		drawRightColumn(g, font, rightX, y, rightW, h, floors.totalY(), mouseX, mouseY);
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		if (this.runsFlipStartMs != 0L || this.classFlipStartMs != 0L) {
			return;
		}
		for (HoverZone zone : this.zones) {
			if (mouseX >= zone.x && mouseX < zone.x + zone.w && mouseY >= zone.y && mouseY < zone.y + zone.h) {
				if (zone.lines != null && !zone.lines.isEmpty()) {
					PvTooltip.drawStyled(g, font, zone.lines, mouseX, mouseY, screenW, screenH);
				}
				break;
			}
		}
	}

	public void renderOverlay(GuiGraphicsExtractor g, Font font, int screenW, int screenH, int mouseX, int mouseY) {
		this.overlay.render(g, font, screenW, screenH, mouseX, mouseY);
	}

	private void drawCalcPanel(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		int mouseX,
		int mouseY
	) {
		PvDraw.innerPanel(g, x, y, w, h);
		int cx = x + PAD;
		int cy = y + PAD;

		String header = Component.translatable("betterpv.dungeons.calc.header").getString();
		PvDraw.text(g, font, header, cx, cy, PvDraw.COLOR_MUTED);

		String mods = buildModsHint();
		if (!mods.isBlank()) {
			int maxMods = (x + w - PAD) - (cx + font.width(header) + 8);
			if (maxMods > 16) {
				PvDraw.textRight(g, font, trimToWidth(font, mods, maxMods), x + w - PAD, cy, PvDraw.COLOR_BORDER);
			}
		}

		cy += font.lineHeight + 6;
		String cataLabel = Component.translatable("betterpv.dungeons.calc.level_wanted").getString();
		String classLabel = Component.translatable("betterpv.dungeons.calc.class_wanted").getString();
		int labelW = Math.max(font.width(cataLabel), font.width(classLabel)) + 4;

		layoutRow(this.cataRow, font, x, w, cx, cy, labelW);
		drawCalcRow(
			g, font, this.cataRow, cx, cataLabel, this.cataLevelText,
			"betterpv.dungeons.calc.level_hint",
			"betterpv.dungeons.calc.level_help",
			this.focus == FieldFocus.CATA,
			mouseX, mouseY
		);

		cy += FIELD_H + 6;
		layoutRow(this.classRow, font, x, w, cx, cy, labelW);
		drawCalcRow(
			g, font, this.classRow, cx, classLabel, this.classLevelText,
			"betterpv.dungeons.calc.class_hint",
			"betterpv.dungeons.calc.class_help",
			this.focus == FieldFocus.CLASS,
			mouseX, mouseY
		);
	}

	private static void layoutRow(CalcRow row, Font font, int panelX, int panelW, int cx, int cy, int labelW) {
		row.fieldH = FIELD_H;
		row.fieldX = cx + labelW;
		row.fieldY = cy;
		row.btnW = BTN_W;
		row.btnH = FIELD_H;
		row.btnX = panelX + panelW - PAD - row.btnW;
		row.btnY = cy;
		row.helpW = font.width(HELP_MARK);
		row.helpH = font.lineHeight;
		row.helpX = row.btnX - HELP_GAP - row.helpW;
		row.helpY = cy + Math.max(0, (FIELD_H - row.helpH) / 2);
		row.fieldW = Math.max(28, row.helpX - 4 - row.fieldX);
	}

	private void drawCalcRow(
		GuiGraphicsExtractor g,
		Font font,
		CalcRow row,
		int labelX,
		String label,
		String text,
		String hintKey,
		String helpKey,
		boolean focused,
		int mouseX,
		int mouseY
	) {
		PvDraw.text(g, font, label, labelX, row.fieldY + 2, PvDraw.COLOR_TEXT);

		boolean fieldHover = hitField(row, mouseX, mouseY);
		int fieldBorder = focused ? PvDraw.COLOR_ACCENT : fieldHover ? 0xFF4A4A5A : PvDraw.COLOR_BORDER;
		PvDraw.roundedFill(g, row.fieldX, row.fieldY, row.fieldW, row.fieldH, 3, PvDraw.CELL);
		PvDraw.roundedRing(g, row.fieldX, row.fieldY, row.fieldW, row.fieldH, 3, fieldBorder, PvDraw.C_ALL);

		int textX = row.fieldX + 3;
		int textY = row.fieldY + Math.max(0, (row.fieldH - font.lineHeight) / 2);
		if (text.isEmpty()) {
			String hint = Component.translatable(hintKey).getString();
			PvDraw.text(g, font, trimToWidth(font, hint, row.fieldW - 6), textX, textY, HINT_COLOR);
		} else {
			int textColor = this.replaceOnType && focused ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_TEXT;
			PvDraw.text(g, font, trimToWidth(font, text, row.fieldW - 6), textX, textY, textColor);
			if (focused && !this.replaceOnType && (System.currentTimeMillis() / 500L) % 2L == 0L) {
				int cursorX = textX + font.width(trimToWidth(font, text, row.fieldW - 6));
				PvDraw.fill(g, cursorX, textY, 1, font.lineHeight, PvDraw.COLOR_TEXT);
			}
		}

		boolean helpHover = hitHelp(row, mouseX, mouseY);
		PvDraw.text(g, font, HELP_MARK, row.helpX, row.helpY, helpHover ? HELP_ICON_HOVER : HELP_ICON_COLOR);
		this.zones.add(new HoverZone(
			row.helpX - 1, row.helpY - 1, row.helpW + 2, row.helpH + 2,
			wrapTip(font, Component.translatable(helpKey).getString(), HELP_TIP_MAX_W)
		));

		boolean btnHover = hitButton(row, mouseX, mouseY);
		PvDraw.roundedFill(g, row.btnX, row.btnY, row.btnW, row.btnH, 3, btnHover ? 0x50386CC8 : PvDraw.CELL);
		PvDraw.roundedRing(g, row.btnX, row.btnY, row.btnW, row.btnH, 3, btnHover ? PvDraw.COLOR_ACCENT : PvDraw.COLOR_BORDER, PvDraw.C_ALL);
		String calc = Component.translatable("betterpv.dungeons.calc.button").getString();
		PvDraw.textCentered(
			g, font, calc,
			row.btnX + row.btnW / 2,
			row.btnY + (row.btnH - font.lineHeight) / 2,
			PvDraw.COLOR_TEXT
		);
	}

	private static List<PvTooltip.Line> wrapTip(Font font, String text, int maxW) {
		List<PvTooltip.Line> lines = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return lines;
		}
		String[] words = text.trim().split("\\s+");
		StringBuilder current = new StringBuilder();
		for (String word : words) {
			String candidate = current.isEmpty() ? word : current + " " + word;
			if (!current.isEmpty() && font.width(candidate) > maxW) {
				lines.add(PvTooltip.Line.plain(current.toString()));
				current.setLength(0);
				current.append(word);
			} else {
				current.setLength(0);
				current.append(candidate);
			}
		}
		if (!current.isEmpty()) {
			lines.add(PvTooltip.Line.plain(current.toString()));
		}
		return lines;
	}

	private static String trimToWidth(Font font, String text, int maxW) {
		if (font.width(text) <= maxW) {
			return text;
		}
		String ellipsis = "...";
		int budget = maxW - font.width(ellipsis);
		if (budget <= 0) {
			return ellipsis;
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (font.width(out.toString() + c) > budget) {
				break;
			}
			out.append(c);
		}
		return out + ellipsis;
	}

	private static String trimToWidthBold(Font font, String text, int maxW) {
		if (text == null) {
			return "";
		}
		if (PvDraw.widthBold(font, text) <= maxW) {
			return text;
		}
		String ellipsis = "...";
		int budget = maxW - PvDraw.widthBold(font, ellipsis);
		if (budget <= 0) {
			return ellipsis;
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (PvDraw.widthBold(font, out.toString() + c) > budget) {
				break;
			}
			out.append(c);
		}
		return out + ellipsis;
	}

	private String buildModsHint() {
		List<String> bits = new ArrayList<>();
		if (this.data.expertRing()) {
			bits.add("Ring");
		}
		if (this.data.hecatombLevel() > 0) {
			bits.add("Hec " + roman(this.data.hecatombLevel()));
		}
		if (this.data.scarfBonus() > 0) {
			bits.add("Scarf +" + Math.round(this.data.scarfBonus() * 100) + "%");
		}
		if (this.data.catacombsGraduateBonus() > 0) {
			bits.add("Grad +" + Math.round(this.data.catacombsGraduateBonus() * 100) + "%");
		}
		if (this.data.mayorFactor() > 1.0 && this.data.mayorName() != null && !this.data.mayorName().isBlank()) {
			bits.add(this.data.mayorName());
		}
		return String.join(" · ", bits);
	}

	private static String roman(int n) {
		return switch (n) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			case 6 -> "VI";
			case 7 -> "VII";
			case 8 -> "VIII";
			case 9 -> "IX";
			case 10 -> "X";
			default -> String.valueOf(n);
		};
	}

	private static int parseTarget(String text, int fallback) {
		int max = CataXpMath.maxLevel();
		if (text == null || text.isBlank()) {
			return Math.max(1, Math.min(max, fallback));
		}
		String cleaned = text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
		if (cleaned.startsWith("cata")) {
			cleaned = cleaned.substring(4);
		} else if (cleaned.startsWith("c") && cleaned.length() > 1 && Character.isDigit(cleaned.charAt(1))) {
			cleaned = cleaned.substring(1);
		}
		try {
			return Math.max(1, Math.min(max, Integer.parseInt(cleaned)));
		} catch (NumberFormatException exception) {
			return Math.max(1, Math.min(max, fallback));
		}
	}

	private void drawRightColumn(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int totalY, int mouseX, int mouseY
	) {
		this.classHitX = x;
		this.classHitY = y;
		this.classHitW = w;
		this.classHitH = h;

		boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		float flipProgress = 0F;
		boolean animating = this.classFlipStartMs != 0L;
		if (animating) {
			flipProgress = Math.min(1F, (System.currentTimeMillis() - this.classFlipStartMs) / (float) FLIP_MS);
			if (flipProgress >= 1F) {
				this.classEssenceFace = this.classFlipTarget;
				this.classFlipStartMs = 0L;
				animating = false;
				flipProgress = 0F;
			}
		}
		float eased = animating ? easeInOutCubic(flipProgress) : 0F;
		float angle = eased * (float) Math.PI;
		boolean showEssence = animating
			? (Math.cos(angle) < 0.0 ? this.classFlipTarget : this.classEssenceFace)
			: this.classEssenceFace;
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

		this.classHitH = h;
		PvDraw.innerPanel(g, x, y, w, h);

		if (showEssence) {
			drawWitherUndeadEssenceFace(g, font, x + PAD, y + PAD, w - PAD * 2, h - PAD * 2, !animating);
		} else {
			drawClassStatsFace(g, font, x, y, w, h, totalY);
		}

		g.pose().popMatrix();
	}

	private void drawClassStatsFace(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int totalY) {
		int cx = x + PAD;
		int barW = w - PAD * 2;
		int labeledH = font.lineHeight + 2 + PvDraw.BAR_HEIGHT;
		int defaultRowH = labeledH + BAR_AFTER;

		int cataY = y + PAD + 4;
		PvDraw.labeledBar(
			g, font,
			"Catacombs", String.valueOf(this.data.cataLevel()),
			this.data.cataProgress(),
			cx, cataY, barW,
			PvDraw.COLOR_BAR_FILL,
			this.data.cataLevel() >= 50 || this.data.cataMaxed()
		);
		this.zones.add(new HoverZone(cx, cataY, barW, labeledH, cataTip()));

		int secretsY = cataY + labeledH + 6;
		PvDraw.text(g, font, "Secrets", cx, secretsY, PvDraw.COLOR_MUTED);
		String secretsValue = FormatUtil.commas(this.data.secrets())
			+ " (avg "
			+ FormatUtil.oneDecimal(this.data.secretsPerRun())
			+ "/run)";
		int secretsMax = Math.max(12, barW - font.width("Secrets") - 8);
		PvDraw.textRight(g, font, trimToWidth(font, secretsValue, secretsMax), cx + barW, secretsY, PvDraw.COLOR_TEXT);

		int extraY = secretsY + font.lineHeight + 4;
		if (this.data.dailyRuns() > 0) {
			PvDraw.text(g, font, "Daily runs", cx, extraY, PvDraw.COLOR_MUTED);
			PvDraw.textRight(g, font, FormatUtil.commas(this.data.dailyRuns()), cx + barW, extraY, PvDraw.COLOR_ACCENT);
			extraY += font.lineHeight + 2;
		}
		if (this.data.journalsUnlocked() > 0) {
			PvDraw.text(g, font, "Unique Journals", cx, extraY, PvDraw.COLOR_MUTED);
			PvDraw.textRight(g, font, FormatUtil.commas(this.data.journalsUnlocked()),
				cx + barW, extraY, PvDraw.COLOR_GOLD);
			extraY += font.lineHeight + 2;
		}

		List<DungeonSnapshot.ClassEntry> classes = this.data.classes();
		if (classes.isEmpty()) {
			return;
		}
		int n = classes.size();

		int extraBottom = extraY;
		int avgGap = 8;
		int tankY = totalY + font.lineHeight - labeledH;
		int rowH = defaultRowH;
		int firstY = n <= 1 ? tankY : tankY - (n - 1) * rowH;
		int avgY = firstY - avgGap - labeledH;
		int minAvgY = extraBottom + 8;
		if (avgY < minAvgY) {
			avgY = minAvgY;
			int afterAvg = avgY + labeledH + avgGap;
			if (n <= 1) {
				if (tankY < afterAvg) {
					tankY = afterAvg;
					firstY = tankY;
				}
			} else {
				int span = tankY - afterAvg;
				if (span >= (n - 1) * (labeledH + 2)) {
					rowH = Math.max(labeledH + 2, span / (n - 1));
					firstY = tankY - (n - 1) * rowH;
				} else {
					firstY = afterAvg;
					rowH = labeledH + 2;
					tankY = firstY + (n - 1) * rowH;
				}
			}
		}

		PvDraw.labeledBar(
			g, font,
			"Class Average", FormatUtil.oneDecimal(this.data.classAverage()),
			this.data.classAverageProgress(),
			cx, avgY, barW,
			PvDraw.COLOR_BAR_FILL,
			this.data.classAverage() >= 50 || this.data.classAverageMaxed()
		);
		this.zones.add(new HoverZone(cx, avgY, barW, labeledH, plainTip(this.data.classAverageHover())));

		for (int i = 0; i < n; i++) {
			DungeonSnapshot.ClassEntry clazz = classes.get(i);
			int cy = i == n - 1 ? tankY : firstY + i * rowH;
			String label = clazz.selected() ? "> " + clazz.name() : clazz.name();
			int labelColor = clazz.selected() ? 0xFF6DFF8A : PvDraw.COLOR_TEXT;
			PvDraw.text(g, font, label, cx, cy, labelColor);
			PvDraw.textRight(g, font, String.valueOf(clazz.level()), cx + barW, cy, PvDraw.COLOR_MUTED);
			int barY = cy + font.lineHeight + 2;
			PvDraw.progressBar(g, cx, barY, barW, PvDraw.BAR_HEIGHT, clazz.progress(), PvDraw.COLOR_BAR_FILL, clazz.level() >= 50 || clazz.maxed());
			this.zones.add(new HoverZone(cx, cy, barW, labeledH, classTip(clazz)));
		}
	}

	private void drawWitherUndeadEssenceFace(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, boolean registerHovers
	) {
		int gap = 20;
		int colW = Math.max(40, (w - gap) / 2);
		int sepX = x + colW + gap / 2;
		drawEssenceColumnFit(g, font, this.data.witherShop(), x, y, colW, h, WITHER_COLOR, 3, registerHovers);
		PvDraw.fill(g, sepX, y, 1, h, PvDraw.COLOR_DIVIDER);
		drawEssenceColumnFit(
			g, font, this.data.undeadShop(), x + colW + gap, y, colW, h, UNDEAD_COLOR, 3, registerHovers
		);
	}

	private void drawIceSpiderDragonEssenceFace(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, boolean registerHovers
	) {
		int gap = 8;
		int colW = Math.max(40, (w - gap * 2) / 3);
		int c0 = x;
		int c1 = x + colW + gap;
		int c2 = c1 + colW + gap;
		drawEssenceColumnFit(g, font, this.data.iceShop(), c0, y, colW, h, ICE_COLOR, 0, registerHovers);
		PvDraw.fill(g, c0 + colW + gap / 2, y, 1, h, PvDraw.COLOR_DIVIDER);
		drawEssenceColumnFit(g, font, this.data.spiderShop(), c1, y, colW, h, SPIDER_COLOR, 0, registerHovers);
		PvDraw.fill(g, c1 + colW + gap / 2, y, 1, h, PvDraw.COLOR_DIVIDER);
		drawEssenceColumnFit(g, font, this.data.dragonShop(), c2, y, colW, h, DRAGON_COLOR, 0, registerHovers);
	}

	private void drawEssenceColumnFit(
		GuiGraphicsExtractor g,
		Font font,
		DungeonSnapshot.EssenceShop shop,
		int x,
		int y,
		int w,
		int h,
		int headerColor,
		int rowGap,
		boolean registerHovers
	) {
		EssenceShopColumn.draw(
			g, font, shop, x, y, w, h, headerColor, rowGap,
			registerHovers ? (zx, zy, zw, zh, lines) -> this.zones.add(new HoverZone(zx, zy, zw, zh, lines)) : null
		);
	}

	private void drawRunsPanel(
		GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, FloorLayout floors, int mouseX, int mouseY
	) {
		this.runsHitX = x;
		this.runsHitY = y;
		this.runsHitW = w;
		this.runsHitH = h;

		boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		float flipProgress = 0F;
		boolean animating = this.runsFlipStartMs != 0L;
		if (animating) {
			flipProgress = Math.min(1F, (System.currentTimeMillis() - this.runsFlipStartMs) / (float) FLIP_MS);
			if (flipProgress >= 1F) {
				this.runsEssenceFace = this.runsFlipTarget;
				this.runsFlipStartMs = 0L;
				animating = false;
				flipProgress = 0F;
			}
		}
		float eased = animating ? easeInOutCubic(flipProgress) : 0F;
		float angle = eased * (float) Math.PI;
		boolean showEssence = animating
			? (Math.cos(angle) < 0.0 ? this.runsFlipTarget : this.runsEssenceFace)
			: this.runsEssenceFace;
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

		this.runsHitH = h;
		PvDraw.innerPanel(g, x, y, w, h);

		if (showEssence) {
			drawIceSpiderDragonEssenceFace(g, font, x + PAD, y + PAD, w - PAD * 2, h - PAD * 2, !animating);
		} else {
			drawRunsFace(g, font, x, y, w, floors);
		}

		g.pose().popMatrix();
	}

	private void drawRunsFace(GuiGraphicsExtractor g, Font font, int x, int y, int w, FloorLayout floors) {
		int cx = x + PAD;
		int innerW = w - PAD * 2;
		int colGap = 20;
		int colW = (innerW - colGap) / 2;
		int leftCol = cx;
		int rightCol = cx + colW + colGap;

		String normalHeader = Component.translatable("betterpv.dungeons.normal").getString();
		String masterHeader = Component.translatable("betterpv.dungeons.master").getString();
		PvDraw.text(g, font, normalHeader, leftCol, y + PAD, PvDraw.COLOR_MUTED);
		PvDraw.text(g, font, masterHeader, rightCol, y + PAD, PvDraw.COLOR_MUTED);

		int cy = floors.firstFloorY();
		long normalTotal = 0L;
		long masterTotal = 0L;
		for (int floor = 1; floor <= 7; floor++) {
			DungeonSnapshot.FloorEntry normal = floorById(this.data.normal(), floor);
			DungeonSnapshot.FloorEntry master = floorById(this.data.master(), floor);
			normalTotal += normal.completions();
			masterTotal += master.completions();

			String floorLabel = Component.translatable("betterpv.dungeons.floor", floor).getString();
			String masterLabel = Component.translatable("betterpv.dungeons.master_floor", floor).getString();

			drawFloorCell(g, font, leftCol, cy, colW, floors.lineH() + floors.pbH(), floors.gap(), floorLabel, normal, NORMAL_FLOOR_COLOR);
			drawFloorCell(g, font, rightCol, cy, colW, floors.lineH() + floors.pbH(), floors.gap(), masterLabel, master, MASTER_FLOOR_COLOR);
			cy += floors.lineH() + floors.pbH() + floors.gap();
		}

		drawTotalLine(g, font, leftCol, floors.totalY(), colW, normalTotal);
		drawTotalLine(g, font, rightCol, floors.totalY(), colW, masterTotal);
	}

	private static float easeInOutCubic(float t) {
		return t < 0.5F
			? 4F * t * t * t
			: 1F - (float) Math.pow(-2F * t + 2F, 3) / 2F;
	}

	private void drawTotalLine(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		long total
	) {
		String label = Component.translatable("betterpv.dungeons.total").getString();
		PvDraw.text(g, font, label, x, y, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, FormatUtil.commas(total), x + w, y, PvDraw.COLOR_TEXT);
	}

	private void drawFloorCell(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int lineH,
		int gap,
		String label,
		DungeonSnapshot.FloorEntry floor,
		int nameColor
	) {
		String right = FormatUtil.commas(floor.completions());
		int rightW = font.width(right);
		String shown = trimToWidth(font, label, Math.max(8, w - rightW - 4));
		PvDraw.text(g, font, shown, x, y, nameColor);
		PvDraw.textRight(g, font, right, x + w, y, PvDraw.COLOR_TEXT);
		drawPbLine(g, font, x, y + font.lineHeight, floor);
		this.zones.add(new HoverZone(
			x, y - 1, w, lineH + Math.max(2, gap / 2),
			floorStatsTip(label, floor)
		));
	}

	/** Small personal-best line under a floor name: S+, S and any-rank clear times. */
	private static void drawPbLine(GuiGraphicsExtractor g, Font font, int x, int y, DungeonSnapshot.FloorEntry floor) {
		long[] times = {floor.fastestSPlusMs(), floor.fastestSMs(), floor.fastestMs()};
		String[] tags = {"S+", "S", "Any"};
		int[] colors = {COLOR_TIME_S_PLUS, COLOR_TIME_S, COLOR_TIME};
		int cx = x;
		boolean any = false;
		for (int i = 0; i < times.length; i++) {
			if (times[i] <= 0L) {
				continue;
			}
			any = true;
			String tag = tags[i] + " ";
			String time = FormatUtil.prettyTime(times[i]);
			PvDraw.textScaled(g, font, tag, cx, y + 1, PvDraw.COLOR_MUTED, PB_SCALE);
			cx += Math.round(font.width(tag) * PB_SCALE);
			PvDraw.textScaled(g, font, time, cx, y + 1, colors[i], PB_SCALE);
			cx += Math.round(font.width(time + "  ") * PB_SCALE);
		}
		if (!any) {
			PvDraw.textScaled(g, font, "no clears", x, y + 1, 0xFF6A6A7A, PB_SCALE);
		}
	}

	private List<PvTooltip.Line> cataTip() {
		List<PvTooltip.Line> lines = new ArrayList<>(plainTip(this.data.cataHover()));
		DungeonSnapshot.HubRace race = this.data.hubRace();
		if (race != null && race.present()) {
			lines.add(PvTooltip.Line.divider());
			lines.add(PvTooltip.Line.title("Dungeon Hub Race", PvDraw.COLOR_ACCENT));
			if (!race.selectedRace().isBlank()) {
				lines.add(PvTooltip.Line.row(
					"Race", PvDraw.COLOR_MUTED, race.selectedRace(), PvDraw.COLOR_TEXT
				));
			}
			if (!race.selectedSetting().isBlank()) {
				lines.add(PvTooltip.Line.row(
					"Setting", PvDraw.COLOR_MUTED, race.selectedSetting(), PvDraw.COLOR_TEXT
				));
			}
			if (race.runback() != null) {
				lines.add(PvTooltip.Line.row(
					"Runback", PvDraw.COLOR_MUTED, race.runback() ? "On" : "Off", PvDraw.COLOR_TEXT
				));
			}
		}
		return lines;
	}

	private static List<PvTooltip.Line> plainTip(String text) {
		return List.of(PvTooltip.Line.plain(text));
	}

	private List<PvTooltip.Line> classTip(DungeonSnapshot.ClassEntry clazz) {
		List<PvTooltip.Line> lines = new ArrayList<>();
		int color = classColor(clazz.id());
		lines.add(PvTooltip.Line.of(clazz.name() + " Lvl " + clazz.level(), color));
		for (String part : clazz.xpHover().split("\n")) {
			if (!part.isBlank()) {
				lines.add(PvTooltip.Line.plain(part));
			}
		}
		return lines;
	}

	private static final int COLOR_STAT = 0xFFE8E8F0;
	private static final int COLOR_COMPLETIONS = 0xFF7CFF9A;
	private static final int COLOR_MOBS = 0xFFFFB86C;
	private static final int COLOR_SCORE_S_PLUS = 0xFF6DFF8A;
	private static final int COLOR_SCORE = 0xFFFFD36A;
	private static final int COLOR_TIME = 0xFF7EC8FF;
	private static final int COLOR_TIME_S = 0xFFFFCC66;
	private static final int COLOR_TIME_S_PLUS = 0xFF6DFF8A;
	private static final int COLOR_HEALING = 0xFFFF8EC8;
	private static final int COLOR_MAGE = 0xFF6EB5FF;
	private static final int COLOR_BERSERK = 0xFFFF6B6B;
	private static final int COLOR_ARCHER = 0xFF7CFF9A;
	private static final int COLOR_HEALER = 0xFFFF8EC8;
	private static final int COLOR_TANK = 0xFFC0C0D0;

	static List<PvTooltip.Line> floorStatsTip(String title, DungeonSnapshot.FloorEntry floor) {
		List<PvTooltip.Line> lines = new ArrayList<>();
		lines.add(PvTooltip.Line.title(title, PvDraw.COLOR_ACCENT));
		lines.add(PvTooltip.Line.row("Clears", PvDraw.COLOR_MUTED, FormatUtil.commas(floor.completions()), COLOR_COMPLETIONS));
		if (floor.milestoneCompletions() > 0L) {
			lines.add(PvTooltip.Line.row("Milestone", PvDraw.COLOR_MUTED, FormatUtil.commas(floor.milestoneCompletions()), COLOR_COMPLETIONS));
		}
		lines.add(PvTooltip.Line.divider());
		lines.add(PvTooltip.Line.row("S+ record", PvDraw.COLOR_MUTED, timeText(floor.fastestSPlusMs()), COLOR_TIME_S_PLUS));
		lines.add(PvTooltip.Line.row("S record", PvDraw.COLOR_MUTED, timeText(floor.fastestSMs()), COLOR_TIME_S));
		lines.add(PvTooltip.Line.row("Fastest clear", PvDraw.COLOR_MUTED, timeText(floor.fastestMs()), COLOR_TIME));
		lines.add(PvTooltip.Line.divider());
		lines.add(PvTooltip.Line.row(
			"Top score",
			PvDraw.COLOR_MUTED,
			FormatUtil.commas(floor.bestScore()),
			floor.bestScore() >= 300L ? COLOR_SCORE_S_PLUS : COLOR_SCORE
		));
		lines.add(PvTooltip.Line.row("Kills (total)", PvDraw.COLOR_MUTED, FormatUtil.shortXp(floor.mobsKilled()), COLOR_MOBS));
		lines.add(PvTooltip.Line.row("Kills (best run)", PvDraw.COLOR_MUTED, FormatUtil.commas(floor.mostMobsKilled()), COLOR_MOBS));
		if (floor.mostHealing() > 0D) {
			lines.add(PvTooltip.Line.row("Healing (best run)", PvDraw.COLOR_MUTED, FormatUtil.shortXp(floor.mostHealing()), COLOR_HEALING));
		}
		if (floor.mostDamage() > 0D && floor.mostDamageClass() != null) {
			String className = ClassLevelQuery.displayName(floor.mostDamageClass());
			lines.add(PvTooltip.Line.row(
				"Damage (best run)",
				PvDraw.COLOR_MUTED,
				FormatUtil.shortXp(floor.mostDamage()) + " " + className,
				classColor(floor.mostDamageClass())
			));
		}
		return lines;
	}

	private static String timeText(long ms) {
		return ms <= 0L ? "none" : FormatUtil.prettyTime(ms);
	}

	private static PvTooltip.Line statLine(String label, String value, int valueColor) {
		return new PvTooltip.Line(List.of(
			PvTooltip.Span.of(label + ": ", PvDraw.COLOR_MUTED),
			PvTooltip.Span.of(value, valueColor)
		));
	}

	private static PvTooltip.Line timeLine(String label, long ms, int color) {
		if (ms <= 0L) {
			return new PvTooltip.Line(List.of(
				PvTooltip.Span.bold(label + ": ", color),
				PvTooltip.Span.of("N/A", color)
			));
		}
		return new PvTooltip.Line(List.of(
			PvTooltip.Span.bold(label + ": ", color),
			PvTooltip.Span.bold(FormatUtil.prettyTime(ms), color)
		));
	}

	private static int classColor(String classId) {
		if (classId == null) {
			return COLOR_STAT;
		}
		return switch (classId.toLowerCase(Locale.ROOT)) {
			case "mage" -> COLOR_MAGE;
			case "berserk" -> COLOR_BERSERK;
			case "archer" -> COLOR_ARCHER;
			case "healer" -> COLOR_HEALER;
			case "tank" -> COLOR_TANK;
			default -> COLOR_STAT;
		};
	}

	private static DungeonSnapshot.FloorEntry floorById(DungeonSnapshot.ModeStats mode, int floor) {
		String key = String.valueOf(floor);
		for (DungeonSnapshot.FloorEntry entry : mode.floors()) {
			if (key.equals(entry.id())) {
				return entry;
			}
		}
		return DungeonSnapshot.FloorEntry.empty(key, "F" + floor);
	}

	private record FloorLayout(int firstFloorY, int lineH, int gap, int totalY, int pbH) {
	}

	private record HoverZone(int x, int y, int w, int h, List<PvTooltip.Line> lines) {
	}

	private static final class CalcRow {
		int fieldX;
		int fieldY;
		int fieldW;
		int fieldH;
		int helpX;
		int helpY;
		int helpW;
		int helpH;
		int btnX;
		int btnY;
		int btnW;
		int btnH;
	}
}
