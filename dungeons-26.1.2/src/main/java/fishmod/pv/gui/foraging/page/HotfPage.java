/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.foraging.page;

import static fishmod.pv.gui.foraging.ForagingUi.DISABLED;
import static fishmod.pv.gui.foraging.ForagingUi.ENABLED;
import static fishmod.pv.gui.foraging.ForagingUi.GAP;
import static fishmod.pv.gui.foraging.ForagingUi.ITEM_SLOT_BG;
import static fishmod.pv.gui.foraging.ForagingUi.ITEM_SLOT_BORDER;
import static fishmod.pv.gui.foraging.ForagingUi.PAD;
import static fishmod.pv.gui.foraging.ForagingUi.STAT_ROW;

import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.ForagingHotfData;
import fishmod.pv.data.ForagingSnapshot;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import fishmod.pv.gui.foraging.ForagingUi;
import fishmod.pv.gui.foraging.ForagingUi.HoverZone;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Heart of the Forest subtab. */
public final class HotfPage {
	private final List<HoverZone> zones = new ArrayList<>();

	public void reset() {
		this.zones.clear();
	}

	public void render(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot,
		int x, int y, int w, int h, int mx, int my
	) {
		this.zones.clear();
		// Wide enough that "Heart of the Forest" isn't truncated.
		int leftW = Math.max(128, Math.min(156, w / 5));
		int centerW = w - leftW - GAP;
		int leftX = x;
		int centerX = x + leftW + GAP;

		PvDraw.innerPanel(g, leftX, y, leftW, h);
		PvDraw.innerPanel(g, centerX, y, centerW, h);

		renderHotfLeft(g, font, snapshot, leftX, y, leftW, h);
		renderHotfCenter(g, font, snapshot, centerX, y, centerW, h, mx, my);
	}

	public void drawHover(GuiGraphicsExtractor g, Font font, int mx, int my, int screenW, int screenH) {
		ForagingUi.drawHover(g, font, this.zones, mx, my, screenW, screenH);
	}

	private void renderHotfLeft(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot, int x, int y, int w, int h
	) {
		int rx = x + PAD;
		int ry = y + PAD;
		int rw = w - PAD * 2;
		int bottom = y + h - PAD;

		PvDraw.text(g, font, "Heart of the Forest", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;
		ry = ForagingUi.statLine(g, font, "HOTF XP", FormatUtil.shortXp(snapshot.hotfXp()),
			rx, ry, rw, PvDraw.COLOR_ACCENT) + 1;
		ry = ForagingUi.statLine(g, font, "Tokens spent", String.valueOf(snapshot.forestTokensSpent()),
			rx, ry, rw, PvDraw.COLOR_TEXT) + 2;
		// skill_tree.last_reset.foraging unit/reliability unverified; hide until confirmed.

		ry = ForagingUi.sectionSeparator(g, font, x, ry, w);
		PvDraw.text(g, font, "Whispers", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;
		List<ForagingSnapshot.WhisperPool> pools = snapshot.whisperPools();
		if (pools.isEmpty()) {
			ry = ForagingUi.statLine(g, font, "Total", "-", rx, ry, rw, PvDraw.COLOR_MUTED) + 3;
		} else {
			for (ForagingSnapshot.WhisperPool pool : pools) {
				String value = whisperValue(pool);
				boolean stacked = !fitsOnOneLine(font.width(pool.label()), font.width(value), rw);
				int rowH = stacked ? STAT_ROW * 2 : STAT_ROW;
				if (ry + rowH > bottom) {
					break;
				}
				int poolColor = whisperPoolColor(pool.id());
				this.zones.add(HoverZone.of(rx, ry, rw, rowH, whisperTip(pool, poolColor)));
				if (stacked) {
					PvDraw.text(g, font, pool.label(), rx, ry, PvDraw.COLOR_MUTED);
					PvDraw.textRight(g, font, value, rx + rw, ry + STAT_ROW, poolColor);
					ry += rowH + 1;
				} else {
					ry = ForagingUi.statLine(g, font, pool.label(), value, rx, ry, rw, poolColor) + 1;
				}
			}
			ry += 2;
		}

		ry = ForagingUi.sectionSeparator(g, font, x, ry, w);
		PvDraw.text(g, font, "Dailies", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;
		ry = ForagingUi.statLine(g, font, "Trees cut", FormatUtil.commas(snapshot.currentDailyTreesCut()),
			rx, ry, rw, PvDraw.COLOR_TEXT) + 1;
		ry = ForagingUi.statLine(g, font, "Tree gifts", FormatUtil.commas(snapshot.currentDailyGifts()),
			rx, ry, rw, PvDraw.COLOR_TEXT) + 2;

		List<String> dailyLogs = snapshot.currentDailyLogs();
		if (!dailyLogs.isEmpty() && ry + font.lineHeight < bottom) {
			PvDraw.text(g, font, "Logs cut", rx, ry, PvDraw.COLOR_MUTED);
			ry += font.lineHeight + 2;
			for (String log : dailyLogs) {
				if (ry + STAT_ROW > bottom) {
					break;
				}
				PvDraw.text(g, font, ForagingUi.trim(font, ForagingUi.pretty(log), rw), rx, ry, PvDraw.COLOR_TEXT);
				ry += STAT_ROW;
			}
			ry += 2;
		}

		if (ry + STAT_ROW * 2 <= bottom) {
			ry = ForagingUi.sectionSeparator(g, font, x, ry, w);
			PvDraw.text(g, font, "Daily effect", rx, ry, PvDraw.COLOR_MUTED);
			ry += font.lineHeight + 2;
			String effect = snapshot.dailyEffect().isBlank() ? "-" : ForagingUi.pretty(snapshot.dailyEffect());
			ForagingUi.wrapText(g, font, effect, rx, ry, rw, PvDraw.COLOR_GOLD);
			ry += font.lineHeight + 4;
			// current_daily_effect_last_changed is unmapped (value scale != daily_*_day). Hide raw until verified.
		}

		List<ForagingSnapshot.HotfNode> unplaced = snapshot.unplacedHotfNodes();
		if (unplaced.isEmpty() || ry + STAT_ROW * 2 > bottom) {
			return;
		}
		ry = ForagingUi.sectionSeparator(g, font, x, ry, w);
		PvDraw.text(g, font, "Other perks", rx, ry, PvDraw.COLOR_MUTED);
		ry += font.lineHeight + 3;
		for (ForagingSnapshot.HotfNode node : unplaced) {
			if (ry + STAT_ROW > bottom) {
				break;
			}
			int color = node.enabled() ? PvDraw.COLOR_TEXT : DISABLED;
			ForagingUi.statLine(g, font, node.name(), "Lv " + node.level(), rx, ry, rw, color);
			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.of(node.name(), PvDraw.COLOR_TEXT));
			tip.add(PvTooltip.Line.of("Level " + node.level(), PvDraw.COLOR_ACCENT));
			tip.add(PvTooltip.Line.of(node.enabled() ? "Enabled" : "Disabled", node.enabled() ? ENABLED : DISABLED));
			tip.add(PvTooltip.Line.meta("Max level unknown"));
			this.zones.add(HoverZone.of(rx, ry, rw, STAT_ROW, tip));
			ry += STAT_ROW;
		}
	}

	private void renderHotfCenter(
		GuiGraphicsExtractor g, Font font, ForagingSnapshot snapshot,
		int x, int y, int w, int h, int mx, int my
	) {
		List<ForagingHotfData.PerkDef> perks = ForagingHotfData.perks();
		if (perks.isEmpty()) {
			PvDraw.textCentered(g, font, "HOTF layout unavailable",
				x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED);
			return;
		}

		int cols = ForagingHotfData.maxX() + 1;
		int rows = ForagingHotfData.maxY() + 1;
		int innerW = w - PAD * 2;
		int innerH = h - PAD * 2;
		int baseIcon = 16;
		int basePad = 2;
		int baseCell = baseIcon + basePad * 2;
		int baseGap = 2;
		int baseGridW = cols * baseCell + Math.max(0, cols - 1) * baseGap;
		int baseGridH = rows * baseCell + Math.max(0, rows - 1) * baseGap;
		// Integer zoom only - fractional scale softens item textures.
		int zoom = 1;
		if (baseGridW > 0 && baseGridH > 0) {
			zoom = Math.max(1, Math.min(innerW / baseGridW, innerH / baseGridH));
			zoom = Math.min(3, zoom);
		}
		int cell = baseCell * zoom;
		int cellGap = baseGap * zoom;
		int iconDraw = baseIcon * zoom;
		int gridW = cols * cell + Math.max(0, cols - 1) * cellGap;
		int gridH = rows * cell + Math.max(0, rows - 1) * cellGap;
		int gridX = x + PAD + Math.max(0, (innerW - gridW) / 2);
		int gridY = y + PAD + Math.max(0, (innerH - gridH) / 2);

		g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
		for (ForagingHotfData.PerkDef perk : perks) {
			int cx = gridX + perk.x() * (cell + cellGap);
			int cy = gridY + perk.y() * (cell + cellGap);
			int level = snapshot.hotfNodeLevel(perk.id());
			PvDraw.roundedFill(g, cx, cy, cell, cell, 3, ITEM_SLOT_BG);
			PvDraw.roundedRing(g, cx, cy, cell, cell, 3, ITEM_SLOT_BORDER, PvDraw.C_ALL);
			ItemStack stack = hotfPerkIcon(perk, level);
			int ix = cx + Math.max(0, (cell - iconDraw) / 2);
			int iy = cy + Math.max(0, (cell - iconDraw) / 2);
			if (zoom == 1) {
				g.item(stack, ix, iy);
			} else {
				g.pose().pushMatrix();
				g.pose().translate(ix, iy);
				g.pose().scale(zoom, zoom);
				g.item(stack, 0, 0);
				g.pose().popMatrix();
			}
			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.of(perk.name(), PvDraw.COLOR_TEXT));
			tip.add(PvTooltip.Line.of("Level " + level + " / " + perk.maxLevel(), PvDraw.COLOR_ACCENT));
			boolean enabled = snapshot.hotfNodeEnabled(perk.id());
			if (level > 0) {
				tip.add(PvTooltip.Line.of(enabled ? "Enabled" : "Disabled",
					enabled ? ENABLED : DISABLED));
			}
			if (perk.ability()) {
				tip.add(PvTooltip.Line.of("Ability", PvDraw.COLOR_GOLD));
			}
			this.zones.add(HoverZone.of(cx, cy, cell, cell, tip));
		}
		g.disableScissor();
	}

	/**
	 * Locked: pale oak button · unlocked: stripped oak · maxed: oak log.
	 * Locked ability: dead bush · unlocked/maxed ability: stripped/oak log.
	 */
	private static ItemStack hotfPerkIcon(ForagingHotfData.PerkDef perk, int level) {
		if (perk.ability()) {
			if (level <= 0) {
				return new ItemStack(Items.DEAD_BUSH);
			}
			return new ItemStack(level >= perk.maxLevel() ? Items.OAK_LOG : Items.STRIPPED_OAK_LOG);
		}
		if (level <= 0) {
			return new ItemStack(Items.PALE_OAK_BUTTON);
		}
		if (level >= perk.maxLevel()) {
			return new ItemStack(Items.OAK_LOG);
		}
		return new ItemStack(Items.STRIPPED_OAK_LOG);
	}

	/** Three significant figures (10.4M) so the pool name fits beside it; exact numbers stay in the tooltip. */
	static String whisperValue(ForagingSnapshot.WhisperPool pool) {
		return compact(pool.balance()) + " / " + compact(pool.total());
	}

	private static String compact(long value) {
		return FormatUtil.compactCoins(value).toUpperCase(java.util.Locale.ROOT);
	}

	static boolean fitsOnOneLine(int labelW, int valueW, int rowW) {
		return labelW + 6 + valueW <= rowW;
	}

	static List<PvTooltip.Line> whisperTip(ForagingSnapshot.WhisperPool pool, int poolColor) {
		List<PvTooltip.Line> tip = new ArrayList<>();
		tip.add(PvTooltip.Line.title(pool.label() + " whispers", poolColor));
		tip.add(PvTooltip.Line.divider());
		tip.add(PvTooltip.Line.row("Left", PvDraw.COLOR_MUTED, FormatUtil.commas(pool.balance()), poolColor));
		tip.add(PvTooltip.Line.row("Spent", PvDraw.COLOR_MUTED, FormatUtil.commas(pool.spent()), PvDraw.COLOR_TEXT));
		tip.add(PvTooltip.Line.row("Total earned", PvDraw.COLOR_MUTED, FormatUtil.commas(pool.total()), PvDraw.COLOR_TEXT));
		if (pool.spentByLoadout().size() > 1) {
			tip.add(PvTooltip.Line.blank());
			tip.add(PvTooltip.Line.meta("Spent in loadout " + pool.loadout() + " (selected)"));
		}
		return tip;
	}

	private static int whisperPoolColor(String id) {
		if (id == null) {
			return PvDraw.COLOR_ACCENT;
		}
		return switch (id.toLowerCase(java.util.Locale.ROOT)) {
			case "forest" -> 0xFF55C878;
			case "desert" -> 0xFFE8A838;
			default -> PvDraw.COLOR_ACCENT;
		};
	}
}
