/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.home;

import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.ProfileSnapshot;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.util.LegacyChatFormatting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Skill and slayer bar grids for the Home bars column. */
final class HomeSkillSlayerBars {
	private static final int PAD = 6;
	private static final int BAR_H = 6;
	private static final int BAR_LABEL_GAP = 2;
	private static final int SECTION_GAP = 8;
	private static final int SKILL_ROWS = 5;
	private static final int SLAYER_ROWS = 3;

	private List<CachedBar> cachedSkillBars = List.of();
	private List<CachedBar> cachedExtraSkillBars = List.of();
	private List<CachedBar> cachedSlayerBars = List.of();
	private int cachedBarColW = -1;
	private String cachedActiveSlayerId = "";
	private final List<SlayerNameHit> slayerNameHits = new ArrayList<>();

	void clearCaches() {
		this.cachedBarColW = -1;
		this.cachedSkillBars = List.of();
		this.cachedExtraSkillBars = List.of();
		this.cachedSlayerBars = List.of();
		this.cachedActiveSlayerId = "";
	}

	List<SlayerNameHit> slayerNameHits() {
		return this.slayerNameHits;
	}

	void draw(
		GuiGraphicsExtractor g,
		Font font,
		int x,
		int y,
		int w,
		int h,
		int rowH,
		ProfileSnapshot snapshot,
		List<HomePage.HoverZone> zones
	) {
		PvDraw.innerPanel(g, x, y, w, h);
		int colGap = 8;
		int colW = (w - PAD * 2 - colGap) / 2;
		int leftX = x + PAD;
		int rightX = leftX + colW + colGap;

		int ty = y + PAD;
		drawSkillGrid(g, font, leftX, rightX, ty, colW, rowH, snapshot, zones);
		int extraY = ty + SKILL_ROWS * rowH;
		drawExtraSkillRow(g, font, leftX, rightX, extraY, colW, rowH, snapshot, zones);
		int skillsBarsBottom = extraY + font.lineHeight + BAR_LABEL_GAP + BAR_H;
		int slayersTop = extraY + rowH + SECTION_GAP;
		int lineInset = PAD + 6;
		int lineY = (skillsBarsBottom + slayersTop) / 2;
		int lineW = Math.max(0, w - lineInset * 2);
		if (lineW > 0) {
			PvDraw.fill(g, x + lineInset, lineY, lineW, 1, PvDraw.COLOR_DIVIDER);
		}
		drawSlayerGrid(g, font, leftX, rightX, slayersTop, colW, rowH, snapshot, zones);
	}

	private void ensureBarCaches(Font font, int colW, ProfileSnapshot snapshot) {
		ProfileSnapshot.ActiveSlayerQuest quest = snapshot == null ? null : snapshot.activeSlayer();
		String activeId = quest != null && quest.present() ? quest.typeId() : "";
		if (colW == this.cachedBarColW
			&& activeId.equals(this.cachedActiveSlayerId)
			&& !this.cachedSkillBars.isEmpty()) {
			return;
		}
		this.cachedBarColW = colW;
		this.cachedActiveSlayerId = activeId;
		List<ProfileSnapshot.SkillEntry> skills = snapshot.skills();
		int skillLimit = Math.min(skills.size(), SKILL_ROWS * 2);
		List<CachedBar> skillBars = new ArrayList<>(skillLimit);
		for (int i = 0; i < skillLimit; i++) {
			ProfileSnapshot.SkillEntry skill = skills.get(i);
			skillBars.add(skillBar(skill));
		}
		this.cachedSkillBars = skillBars;

		List<CachedBar> extra = new ArrayList<>(2);
		ProfileSnapshot.SkillEntry rune = snapshot.runecrafting();
		if (rune != null) {
			extra.add(skillBar(rune));
		}
		ProfileSnapshot.SkillEntry social = snapshot.social();
		if (social != null) {
			extra.add(skillBar(social));
		}
		this.cachedExtraSkillBars = extra;

		List<ProfileSnapshot.SlayerEntry> slayers = snapshot.slayers();
		int slayerLimit = Math.min(slayers.size(), SLAYER_ROWS * 2);
		List<CachedBar> slayerBars = new ArrayList<>(slayerLimit);
		for (int i = 0; i < slayerLimit; i++) {
			ProfileSnapshot.SlayerEntry slayer = slayers.get(i);
			boolean active = !activeId.isBlank() && activeId.equalsIgnoreCase(slayer.id());
			int accent = slayerColor(slayer.id());
			slayerBars.add(new CachedBar(
				slayer.name(),
				"T" + slayer.tier(),
				slayer.progress(),
				slayer.maxed(),
				PvDraw.COLOR_BAR_FILL_SLAYER,
				accent,
				active,
				slayerHoverLines(slayer, active ? quest : null, accent),
				slayer.id(),
				font.width(slayer.name())
			));
		}
		this.cachedSlayerBars = slayerBars;
	}

	private static CachedBar skillBar(ProfileSnapshot.SkillEntry skill) {
		if (fishmod.utils.config.values.FishSettings.pvOverflowSkills && skill.overflowLevel() > skill.level()) {
			// Uncapped level; the bar stays gold and fills toward the next overflow level.
			float over = skill.overflowLevel();
			return new CachedBar(
				skill.name(),
				String.valueOf((int) Math.floor(over)),
				over - (float) Math.floor(over),
				true,
				PvDraw.COLOR_BAR_FILL,
				0,
				skill.hoverLines()
			);
		}
		return new CachedBar(
			skill.name(),
			String.valueOf(skill.level()),
			skill.progress(),
			skill.maxed(),
			PvDraw.COLOR_BAR_FILL,
			0,
			skill.hoverLines()
		);
	}

	private void drawSkillGrid(
		GuiGraphicsExtractor g,
		Font font,
		int leftX,
		int rightX,
		int startY,
		int colW,
		int rowH,
		ProfileSnapshot snapshot,
		List<HomePage.HoverZone> zones
	) {
		ensureBarCaches(font, colW, snapshot);
		drawBarGrid(g, font, this.cachedSkillBars, leftX, rightX, startY, colW, rowH, zones);
	}

	private void drawExtraSkillRow(
		GuiGraphicsExtractor g,
		Font font,
		int leftX,
		int rightX,
		int startY,
		int colW,
		int rowH,
		ProfileSnapshot snapshot,
		List<HomePage.HoverZone> zones
	) {
		ensureBarCaches(font, colW, snapshot);
		drawBarGrid(g, font, this.cachedExtraSkillBars, leftX, rightX, startY, colW, rowH, zones);
	}

	private void drawSlayerGrid(
		GuiGraphicsExtractor g,
		Font font,
		int leftX,
		int rightX,
		int startY,
		int colW,
		int rowH,
		ProfileSnapshot snapshot,
		List<HomePage.HoverZone> zones
	) {
		ensureBarCaches(font, colW, snapshot);
		this.slayerNameHits.clear();
		for (int i = 0; i < this.cachedSlayerBars.size(); i++) {
			CachedBar bar = this.cachedSlayerBars.get(i);
			boolean left = (i % 2) == 0;
			int row = i / 2;
			int bx = left ? leftX : rightX;
			int by = startY + row * rowH;
			int zoneH = rowH - 2;
			drawCachedBar(g, font, bar, bx, by, colW);
			zones.add(new HomePage.HoverZone(bx, by, colW, zoneH, bar.hover()));
			if (!bar.slayerId().isBlank()) {
				this.slayerNameHits.add(new SlayerNameHit(
					bx, by, colW, zoneH, bar.slayerId(), bar.accent()
				));
			}
		}
	}

	private static void drawBarGrid(
		GuiGraphicsExtractor g,
		Font font,
		List<CachedBar> bars,
		int leftX,
		int rightX,
		int startY,
		int colW,
		int rowH,
		List<HomePage.HoverZone> zones
	) {
		for (int i = 0; i < bars.size(); i++) {
			CachedBar bar = bars.get(i);
			boolean left = (i % 2) == 0;
			int row = i / 2;
			int bx = left ? leftX : rightX;
			int by = startY + row * rowH;
			int zoneH = rowH - 2;
			drawCachedBar(g, font, bar, bx, by, colW);
			zones.add(new HomePage.HoverZone(bx, by, colW, zoneH, bar.hover()));
		}
	}

	private static void drawCachedBar(
		GuiGraphicsExtractor g, Font font, CachedBar bar, int x, int y, int w
	) {
		int labelColor = bar.accent() != 0 ? bar.accent() : PvDraw.COLOR_TEXT;
		PvDraw.labeledBar(
			g, font, bar.label(), bar.value(), bar.progress(), x, y, w, bar.fillColor(), bar.maxed(),
			labelColor, bar.labelBold()
		);
	}

	private static List<PvTooltip.Line> slayerHoverLines(
		ProfileSnapshot.SlayerEntry slayer,
		ProfileSnapshot.ActiveSlayerQuest quest,
		int accent
	) {
		List<PvTooltip.Line> lines = new ArrayList<>();
		lines.add(PvTooltip.Line.title(slayer.name() + " " + slayer.tier(), accent));
		List<PvTooltip.Line> base = slayer.hoverLines();
		for (int i = 0; i < base.size(); i++) {
			if (i == 0) {
				continue;
			}
			lines.add(base.get(i));
		}
		if (quest != null && quest.present()) {
			lines.add(PvTooltip.Line.blank());
			lines.add(PvTooltip.Line.title("Active Quest", accent));
			lines.add(PvTooltip.Line.row("Tier", PvDraw.COLOR_MUTED, roman(quest.tier()), PvDraw.COLOR_TEXT));
			lines.add(PvTooltip.Line.row(
				"Boss",
				PvDraw.COLOR_MUTED,
				quest.spawned() ? "Spawned" : "Not spawned",
				PvDraw.COLOR_TEXT
			));
			lines.add(PvTooltip.Line.row(
				"Spawn Progress",
				PvDraw.COLOR_MUTED,
				FormatUtil.commas(Math.round(quest.combatXp())) + " Combat XP",
				PvDraw.COLOR_GOLD
			));
			if (!quest.island().isBlank()) {
				lines.add(PvTooltip.Line.row("Island", PvDraw.COLOR_MUTED, quest.island(), PvDraw.COLOR_TEXT));
			}
			lines.add(PvTooltip.Line.row(
				"Mode", PvDraw.COLOR_MUTED, quest.solo() ? "Solo" : "Group", PvDraw.COLOR_TEXT
			));
		}
		lines.add(PvTooltip.Line.blank());
		lines.add(PvTooltip.Line.action("Click to view calculator"));
		return lines;
	}

	private static int slayerColor(String id) {
		if (id == null || id.isBlank()) {
			return PvDraw.COLOR_ACCENT;
		}
		ChatFormatting fmt = switch (id.toLowerCase(java.util.Locale.ROOT)) {
			case "zombie" -> ChatFormatting.GREEN;
			case "spider" -> ChatFormatting.RED;
			case "wolf" -> ChatFormatting.AQUA;
			case "enderman" -> ChatFormatting.DARK_PURPLE;
			case "blaze" -> ChatFormatting.GOLD;
			case "vampire" -> ChatFormatting.LIGHT_PURPLE;
			default -> ChatFormatting.BLUE;
		};
		Integer rgb = LegacyChatFormatting.rgb(fmt);
		return rgb == null ? PvDraw.COLOR_ACCENT : 0xFF000000 | rgb;
	}

	private static String roman(int value) {
		return switch (value) {
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
			default -> String.valueOf(value);
		};
	}

	record SlayerNameHit(int x, int y, int w, int h, String slayerId, int accent) {
	}

	private record CachedBar(
		String label,
		String value,
		float progress,
		boolean maxed,
		int fillColor,
		int accent,
		boolean labelBold,
		List<PvTooltip.Line> hover,
		String slayerId,
		int nameW
	) {
		private CachedBar(
			String label,
			String value,
			float progress,
			boolean maxed,
			int fillColor,
			int accent,
			List<PvTooltip.Line> hover
		) {
			this(label, value, progress, maxed, fillColor, accent, false, hover, "", 0);
		}
	}
}
