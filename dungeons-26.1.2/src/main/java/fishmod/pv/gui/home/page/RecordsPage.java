/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.home.page;

import static fishmod.pv.gui.home.HomeUi.GAP;
import static fishmod.pv.gui.home.HomeUi.HEADER_COMMUNITY;
import static fishmod.pv.gui.home.HomeUi.HEADER_DEATHS;
import static fishmod.pv.gui.home.HomeUi.HEADER_HIGHLIGHTS;
import static fishmod.pv.gui.home.HomeUi.HEADER_KILLS;
import static fishmod.pv.gui.home.HomeUi.HEADER_PETS;
import static fishmod.pv.gui.home.HomeUi.HEADER_PROFILE;
import static fishmod.pv.gui.home.HomeUi.PAD;
import static fishmod.pv.gui.home.HomeUi.SEP_GAP;
import static fishmod.pv.gui.home.HomeUi.STAT_ROW;

import fishmod.pv.data.FormatUtil;
import fishmod.pv.data.MiscStatsSnapshot;
import fishmod.pv.gui.PvDraw;
import fishmod.pv.gui.PvTooltip;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Home → Records: dragon fights, race best times, Diana burrows, combat bests, event bests. */
public final class RecordsPage {
	private static final int MIN_PANEL_H = 70;
	private static final int CANDY_GREEN = 0xFF55FF55;
	private static final int CANDY_PURPLE = 0xFFAA00AA;
	private static final int DRAGON_COUNT = 0xFF555555;
	private static final int DRAGON_TIME = 0xFF55FF55;
	private static final int DRAGON_DAMAGE = 0xFFFF5555;

	private MiscStatsSnapshot snapshot = MiscStatsSnapshot.empty();
	private final List<HoverZone> zones = new ArrayList<>();

	public void apply(MiscStatsSnapshot snapshot) {
		this.snapshot = snapshot == null ? MiscStatsSnapshot.empty() : snapshot;
		this.zones.clear();
	}

	public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h) {
		this.zones.clear();
		int colW = (w - GAP * 2) / 3;
		int midX = x + colW + GAP;
		int rightX = midX + colW + GAP;
		int rightW = w - colW * 2 - GAP * 2;
		drawDragons(g, font, x, y, colW, h);

		int racesH = Math.min(panelH(font, Math.max(1, this.snapshot.races().size()), 0), h - GAP - MIN_PANEL_H);
		drawRaces(g, font, midX, y, colW, racesH);
		drawDiana(g, font, midX, y + racesH + GAP, colW, h - racesH - GAP);

		MiscStatsSnapshot.SeasonalStats s = this.snapshot.seasonal();
		int eventRows = (s.winterPresent() ? 3 : 0) + (s.spookyPresent() ? 3 : 0);
		int eventsH = Math.min(
			panelH(font, Math.max(1, eventRows), s.winterPresent() && s.spookyPresent() ? 1 : 0),
			h - GAP - MIN_PANEL_H);
		drawCombat(g, font, rightX, y, rightW, h - eventsH - GAP);
		drawEvents(g, font, rightX, y + h - eventsH, rightW, eventsH);
	}

	private static int panelH(Font font, int rows, int separators) {
		return PAD * 2 + font.lineHeight + 4 + rows * STAT_ROW + separators * SEP_GAP;
	}

	private void drawCombat(GuiGraphicsExtractor g, Font font, int px, int py, int pw, int ph) {
		PvDraw.innerPanel(g, px, py, pw, ph);
		int x = px + PAD;
		int w = pw - PAD * 2;
		int bottom = py + ph - PAD;
		MiscStatsSnapshot s = this.snapshot;
		int cy = header(g, font, "Combat", "", x, py + PAD, w, HEADER_DEATHS);
		cy = stat(g, font, "Highest Damage", s.highestDamage() > 0D ? FormatUtil.shortXp(s.highestDamage()) : "-",
			x, cy, w, PvDraw.COLOR_GOLD, List.of(
				PvTooltip.Line.title("Highest Damage", HEADER_DEATHS),
				PvTooltip.Line.divider(),
				PvTooltip.Line.row("Damage", PvDraw.COLOR_MUTED, FormatUtil.commas((long) s.highestDamage()), PvDraw.COLOR_GOLD)
			));
		cy = stat(g, font, "Highest Crit", s.highestCriticalDamage() > 0D ? FormatUtil.shortXp(s.highestCriticalDamage()) : "-",
			x, cy, w, PvDraw.COLOR_GOLD, List.of(
				PvTooltip.Line.title("Highest Critical Hit", HEADER_DEATHS),
				PvTooltip.Line.divider(),
				PvTooltip.Line.row("Damage", PvDraw.COLOR_MUTED, FormatUtil.commas((long) s.highestCriticalDamage()), PvDraw.COLOR_GOLD)
			));
		List<PvTooltip.Line> kdTip = new ArrayList<>();
		kdTip.add(PvTooltip.Line.title("Kills / Deaths", HEADER_DEATHS));
		kdTip.add(PvTooltip.Line.divider());
		kdTip.add(PvTooltip.Line.row("Kills", PvDraw.COLOR_MUTED, FormatUtil.commas(s.killsTotal()), HEADER_KILLS));
		kdTip.add(PvTooltip.Line.row("Deaths", PvDraw.COLOR_MUTED, FormatUtil.commas(s.deathsTotal()), HEADER_DEATHS));
		if (!s.deaths().isEmpty()) {
			kdTip.add(PvTooltip.Line.blank());
			kdTip.add(PvTooltip.Line.of("Top death causes", PvDraw.COLOR_MUTED));
			for (MiscStatsSnapshot.CountEntry death : s.deaths().subList(0, Math.min(5, s.deaths().size()))) {
				kdTip.add(PvTooltip.Line.row(death.label(), PvDraw.COLOR_TEXT, FormatUtil.commas(death.count()), HEADER_DEATHS));
			}
		}
		cy = stat(g, font, "K/D", s.killsTotal() > 0L ? FormatUtil.twoDecimals(s.killDeathRatio()) : "-",
			x, cy, w, PvDraw.COLOR_TEXT, kdTip);
		if (s.kills().isEmpty() || cy + SEP_GAP + STAT_ROW * 2 > bottom) {
			return;
		}
		cy = separator(g, px, cy, pw);
		PvDraw.text(g, font, "Most Killed", x, cy, PvDraw.COLOR_MUTED);
		cy += STAT_ROW;
		for (MiscStatsSnapshot.CountEntry kill : s.kills()) {
			if (cy + STAT_ROW > bottom) {
				break;
			}
			String value = FormatUtil.commas(kill.count());
			cy = stat(g, font, trim(font, kill.label(), w - font.width(value) - 6), value, x, cy, w, HEADER_KILLS, null);
		}
	}

	private void drawEvents(GuiGraphicsExtractor g, Font font, int px, int py, int pw, int ph) {
		PvDraw.innerPanel(g, px, py, pw, ph);
		int x = px + PAD;
		int w = pw - PAD * 2;
		MiscStatsSnapshot.SeasonalStats s = this.snapshot.seasonal();
		int cy = header(g, font, "Events", "", x, py + PAD, w, HEADER_PROFILE);
		if (!s.winterPresent() && !s.spookyPresent()) {
			PvDraw.text(g, font, "No event stats", x, cy, PvDraw.COLOR_MUTED);
			return;
		}
		if (s.winterPresent()) {
			List<PvTooltip.Line> tip = List.of(
				PvTooltip.Line.title("Jerry's Workshop", HEADER_PROFILE),
				PvTooltip.Line.divider(),
				PvTooltip.Line.row("Snowballs Hit", PvDraw.COLOR_MUTED, FormatUtil.commas(s.snowballsHit()), PvDraw.COLOR_GOLD),
				PvTooltip.Line.row("Damage Dealt", PvDraw.COLOR_MUTED, FormatUtil.commas(s.winterDamage()), PvDraw.COLOR_GOLD),
				PvTooltip.Line.row("Magma Damage", PvDraw.COLOR_MUTED, FormatUtil.commas(s.magmaDamage()), PvDraw.COLOR_GOLD),
				PvTooltip.Line.meta("Best in a single event")
			);
			cy = stat(g, font, "Snowballs Hit", FormatUtil.commas(s.snowballsHit()), x, cy, w, PvDraw.COLOR_GOLD, tip);
			cy = stat(g, font, "Winter Damage", FormatUtil.commas(s.winterDamage()), x, cy, w, PvDraw.COLOR_GOLD, tip);
			cy = stat(g, font, "Magma Damage", FormatUtil.commas(s.magmaDamage()), x, cy, w, PvDraw.COLOR_GOLD, tip);
			if (s.spookyPresent()) {
				cy = separator(g, px, cy, pw);
			}
		}
		if (s.spookyPresent()) {
			cy = stat(g, font, "Candy", FormatUtil.commas(s.candyTotal()), x, cy, w, PvDraw.COLOR_TEXT, List.of(
				PvTooltip.Line.title("Spooky Festival Candy", HEADER_HIGHLIGHTS),
				PvTooltip.Line.divider(),
				PvTooltip.Line.row("Green", PvDraw.COLOR_MUTED, FormatUtil.commas(s.greenCandy()), CANDY_GREEN),
				PvTooltip.Line.row("Purple", PvDraw.COLOR_MUTED, FormatUtil.commas(s.purpleCandy()), CANDY_PURPLE),
				PvTooltip.Line.row("Total", PvDraw.COLOR_MUTED, FormatUtil.commas(s.candyTotal()), PvDraw.COLOR_TEXT),
				PvTooltip.Line.row("Festivals", PvDraw.COLOR_MUTED, FormatUtil.commas(s.festivals()), PvDraw.COLOR_TEXT)
			));
			List<PvTooltip.Line> bestTip = null;
			if (s.bestFestivalYear() > 0) {
				bestTip = new ArrayList<>(List.of(
					PvTooltip.Line.title(
						"Best Spooky Festival",
						HEADER_HIGHLIGHTS),
					PvTooltip.Line.divider(),
					PvTooltip.Line.row("Year", PvDraw.COLOR_MUTED, String.valueOf(s.bestFestivalYear()), PvDraw.COLOR_TEXT),
					PvTooltip.Line.row("Points", PvDraw.COLOR_MUTED, FormatUtil.commas(s.bestFestivalPoints()), PvDraw.COLOR_GOLD),
					PvTooltip.Line.row("Green", PvDraw.COLOR_MUTED, FormatUtil.commas(s.bestFestivalGreen()), CANDY_GREEN),
					PvTooltip.Line.row("Purple", PvDraw.COLOR_MUTED, FormatUtil.commas(s.bestFestivalPurple()), CANDY_PURPLE)
				));
				if (s.festivalHistoryIncomplete()) {
					bestTip.add(PvTooltip.Line.blank());
					bestTip.add(PvTooltip.Line.meta("History covers " + FormatUtil.commas(s.festivalHistoryCandy())
						+ " / " + FormatUtil.commas(s.candyTotal()) + " candy"));
				}
			}
			cy = stat(g, font, "Best Festival", s.bestFestivalPoints() > 0L ? FormatUtil.commas(s.bestFestivalPoints()) : "-",
				x, cy, w, PvDraw.COLOR_GOLD, bestTip);
			stat(g, font, "Bats Spawned", FormatUtil.commas(s.batsSpawned()), x, cy, w, PvDraw.COLOR_TEXT, null);
		}
	}

	public void renderTooltip(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int screenW, int screenH) {
		for (HoverZone zone : this.zones) {
			if (mouseX >= zone.x && mouseX < zone.x + zone.w && mouseY >= zone.y && mouseY < zone.y + zone.h) {
				PvTooltip.drawStyled(g, font, zone.lines, mouseX, mouseY, screenW, screenH);
				break;
			}
		}
	}

	private void drawDragons(GuiGraphicsExtractor g, Font font, int px, int py, int pw, int ph) {
		PvDraw.innerPanel(g, px, py, pw, ph);
		int x = px + PAD;
		int w = pw - PAD * 2;
		int bottom = py + ph - PAD;
		MiscStatsSnapshot.DragonStats d = this.snapshot.dragons();
		int cy = header(g, font, "Dragons", d.summoned() > 0L ? FormatUtil.commas(d.summoned()) + " summoned" : "",
			x, py + PAD, w, HEADER_COMMUNITY);
		if (!d.present()) {
			PvDraw.text(g, font, "No dragon fights", x, cy, PvDraw.COLOR_MUTED);
			return;
		}
		cy = stat(g, font, "Best Damage", d.bestDamage() > 0D ? FormatUtil.shortXp(d.bestDamage()) : "-",
			x, cy, w, PvDraw.COLOR_GOLD, null);
		cy = stat(g, font, "Fastest Kill", killTime(d.fastestKillMs()), x, cy, w, PvDraw.COLOR_GOLD, null);
		cy = stat(g, font, "Eyes Placed", FormatUtil.commas(d.eyesPlaced()), x, cy, w, PvDraw.COLOR_TEXT, null);
		cy = stat(g, font, "Eyes Collected", FormatUtil.commas(d.eyesCollected()), x, cy, w, PvDraw.COLOR_TEXT, null);
		cy = stat(g, font, "Special Zealots", FormatUtil.commas(d.specialZealots()), x, cy, w, PvDraw.COLOR_TEXT, null);
		if (d.dragons().isEmpty() || cy + SEP_GAP + STAT_ROW > bottom) {
			return;
		}
		cy = separator(g, px, cy, pw);
		for (MiscStatsSnapshot.DragonStat dragon : d.dragons()) {
			if (cy + STAT_ROW > bottom) {
				break;
			}
			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.title(dragon.label() + " Dragon", dragonColor(dragon.id())));
			tip.add(PvTooltip.Line.divider());
			tip.add(PvTooltip.Line.row("Summoned", PvDraw.COLOR_MUTED, FormatUtil.commas(dragon.summoned()), PvDraw.COLOR_TEXT));
			tip.add(PvTooltip.Line.row("Eyes Placed", PvDraw.COLOR_MUTED, FormatUtil.commas(dragon.eyes()), PvDraw.COLOR_TEXT));
			tip.add(PvTooltip.Line.row("Most Damage", PvDraw.COLOR_MUTED,
				dragon.mostDamage() > 0D ? FormatUtil.commas((long) dragon.mostDamage()) : "-", PvDraw.COLOR_GOLD));
			tip.add(PvTooltip.Line.row("Fastest Kill", PvDraw.COLOR_MUTED, killTime(dragon.fastestKillMs()), PvDraw.COLOR_GOLD));
			if (dragon.bestRank() > 0) {
				tip.add(PvTooltip.Line.row("Best Placement", PvDraw.COLOR_MUTED, "#" + dragon.bestRank(), PvDraw.COLOR_TEXT));
			}
			cy = dragonRow(g, font, dragon, x, cy, w, tip);
		}
	}

	private int dragonRow(GuiGraphicsExtractor g, Font font, MiscStatsSnapshot.DragonStat dragon, int x, int y, int w,
		List<PvTooltip.Line> tip) {
		String damage = dragon.mostDamage() > 0D ? FormatUtil.shortXp(dragon.mostDamage()) : "-";
		String slash = " / ";
		String time = killTime(dragon.fastestKillMs());
		int right = x + w;
		PvDraw.textRight(g, font, damage, right, y, DRAGON_DAMAGE);
		right -= font.width(damage);
		PvDraw.textRight(g, font, slash, right, y, PvDraw.COLOR_MUTED);
		right -= font.width(slash);
		PvDraw.textRight(g, font, time, right, y, DRAGON_TIME);
		right -= font.width(time);

		String count = " " + FormatUtil.commas(dragon.summoned()) + "x";
		String name = trim(font, dragon.label(), right - x - font.width(count) - 6);
		PvDraw.text(g, font, name, x, y, dragonColor(dragon.id()));
		PvDraw.text(g, font, count, x + font.width(name), y, DRAGON_COUNT);
		this.zones.add(new HoverZone(x, y, w, STAT_ROW, tip));
		return y + STAT_ROW;
	}

	// Leather colours of each dragon's armour set (NEU repo).
	private static int dragonColor(String id) {
		return switch (id == null ? "" : id.toLowerCase(Locale.ROOT)) {
			case "protector" -> 0xFF99978B;
			case "old" -> 0xFFF0E6AA;
			case "wise" -> 0xFF29F0E9;
			case "unstable" -> 0xFFB212E3;
			case "young" -> 0xFFDDE4F0;
			case "strong" -> 0xFFD91E41;
			case "superior" -> 0xFFF2DF11;
			case "holy" -> 0xFF47D147;
			default -> PvDraw.COLOR_TEXT;
		};
	}

	private void drawRaces(GuiGraphicsExtractor g, Font font, int px, int py, int pw, int ph) {
		PvDraw.innerPanel(g, px, py, pw, ph);
		int x = px + PAD;
		int w = pw - PAD * 2;
		int bottom = py + ph - PAD;
		List<MiscStatsSnapshot.RaceGroup> races = this.snapshot.races();
		int cy = header(g, font, "Races", "", x, py + PAD, w, HEADER_PETS);
		if (races.isEmpty()) {
			PvDraw.text(g, font, "No races completed", x, cy, PvDraw.COLOR_MUTED);
			return;
		}
		for (MiscStatsSnapshot.RaceGroup race : races) {
			if (cy + STAT_ROW > bottom) {
				break;
			}
			String time = raceTime(race.bestMs());
			List<PvTooltip.Line> tip = new ArrayList<>();
			tip.add(PvTooltip.Line.title(race.label(), HEADER_PETS));
			tip.add(PvTooltip.Line.divider());
			if (race.modes().size() == 1 && race.modes().get(0).mode().equals(race.label())) {
				tip.add(PvTooltip.Line.row("Best Time", PvDraw.COLOR_MUTED, time, PvDraw.COLOR_GOLD));
			} else {
				for (MiscStatsSnapshot.RaceTime mode : race.modes()) {
					tip.add(PvTooltip.Line.row(mode.mode(), PvDraw.COLOR_MUTED, raceTime(mode.ms()), PvDraw.COLOR_GOLD));
				}
			}
			String label = trim(font, race.label(), w - font.width(time) - 6);
			cy = stat(g, font, label, time, x, cy, w, PvDraw.COLOR_GOLD, tip);
		}
	}

	private void drawDiana(GuiGraphicsExtractor g, Font font, int px, int py, int pw, int ph) {
		PvDraw.innerPanel(g, px, py, pw, ph);
		int x = px + PAD;
		int w = pw - PAD * 2;
		int bottom = py + ph - PAD;
		MiscStatsSnapshot.MythosStats m = this.snapshot.mythos();
		int cy = header(g, font, "Diana", "", x, py + PAD, w, HEADER_HIGHLIGHTS);
		if (!m.present()) {
			PvDraw.text(g, font, "No Diana stats", x, cy, PvDraw.COLOR_MUTED);
			return;
		}
		cy = stat(g, font, "Mythological Kills", FormatUtil.commas(m.kills()), x, cy, w, PvDraw.COLOR_GOLD, List.of(
			PvTooltip.Line.title("Mythological Kills", HEADER_HIGHLIGHTS),
			PvTooltip.Line.divider(),
			PvTooltip.Line.of("Mythological creatures killed.", PvDraw.COLOR_TEXT),
			PvTooltip.Line.meta(DIANA_LIFETIME)
		));
		if (m.burrows().isEmpty() || cy + SEP_GAP + STAT_ROW > bottom) {
			return;
		}
		cy = separator(g, px, cy, pw);
		PvDraw.text(g, font, "Burrows Dug", x, cy, PvDraw.COLOR_MUTED);
		cy += STAT_ROW;
		for (MiscStatsSnapshot.BurrowCount burrow : m.burrows()) {
			if (cy + STAT_ROW > bottom) {
				break;
			}
			String value = FormatUtil.commas(burrow.total());
			String label = trim(font, burrow.label(), w - font.width(value) - 6);
			cy = stat(g, font, label, value, x, cy, w, PvDraw.COLOR_TEXT, burrowTip(burrow));
		}
	}

	private static final String DIANA_LIFETIME = "Lifetime, all Diana events on this profile";

	static List<PvTooltip.Line> burrowTip(MiscStatsSnapshot.BurrowCount burrow) {
		List<PvTooltip.Line> tip = new ArrayList<>();
		boolean chains = "burrows_chains_complete".equals(burrow.key());
		tip.add(PvTooltip.Line.title(chains ? "Chains Completed" : burrow.label() + " Burrows", HEADER_HIGHLIGHTS));
		tip.add(PvTooltip.Line.divider());
		String about = switch (burrow.key()) {
			case "burrows_dug_next" -> "First burrow of each chain. Points to the next one.";
			case "burrows_dug_combat" -> "Burrows that spawned a Mythological creature.";
			case "burrows_dug_treasure" -> "Burrows that dropped treasure.";
			case "burrows_chains_complete" -> "Griffin burrow chains finished.";
			default -> null;
		};
		if (about != null) {
			tip.add(PvTooltip.Line.of(about, PvDraw.COLOR_TEXT));
		}
		if (!burrow.byRarity().isEmpty()) {
			tip.add(PvTooltip.Line.blank());
			tip.add(PvTooltip.Line.of("By Griffin rarity used", PvDraw.COLOR_MUTED));
			for (Map.Entry<String, Long> e : burrow.byRarity().entrySet()) {
				tip.add(PvTooltip.Line.row(e.getKey(), PvDraw.COLOR_MUTED, FormatUtil.commas(e.getValue()), PvDraw.COLOR_TEXT));
			}
		}
		tip.add(PvTooltip.Line.row("Total", PvDraw.COLOR_MUTED, FormatUtil.commas(burrow.total()), PvDraw.COLOR_GOLD));
		tip.add(PvTooltip.Line.blank());
		tip.add(PvTooltip.Line.meta(DIANA_LIFETIME));
		return tip;
	}

	private static int header(GuiGraphicsExtractor g, Font font, String title, String right, int x, int y, int w, int color) {
		PvDraw.text(g, font, title, x, y, color);
		if (right != null && !right.isBlank()) {
			PvDraw.textRight(g, font, right, x + w, y, PvDraw.COLOR_MUTED);
		}
		return y + font.lineHeight + 4;
	}

	private int stat(
		GuiGraphicsExtractor g, Font font, String label, String value,
		int x, int y, int w, int valueColor, List<PvTooltip.Line> tip
	) {
		PvDraw.text(g, font, label, x, y, PvDraw.COLOR_MUTED);
		PvDraw.textRight(g, font, value == null || value.isBlank() ? "-" : value, x + w, y, valueColor);
		if (tip != null && !tip.isEmpty()) {
			this.zones.add(new HoverZone(x, y, w, STAT_ROW, tip));
		}
		return y + STAT_ROW;
	}

	private static int separator(GuiGraphicsExtractor g, int panelX, int y, int panelW) {
		int lineInset = PAD + 4;
		int lineW = Math.max(0, panelW - lineInset * 2);
		int lineY = y + (SEP_GAP - 1) / 2;
		if (lineW > 0) {
			PvDraw.fill(g, panelX + lineInset, lineY, lineW, 1, 0x33FFFFFF);
		}
		return y + SEP_GAP;
	}

	private static String killTime(long ms) {
		return ms <= 0L ? "-" : String.format(Locale.ROOT, "%.2fs", ms / 1000D);
	}

	private static String raceTime(long ms) {
		if (ms <= 0L) {
			return "-";
		}
		long minutes = ms / 60_000L;
		double seconds = (ms % 60_000L) / 1000D;
		return minutes > 0L
			? String.format(Locale.ROOT, "%d:%06.3f", minutes, seconds)
			: String.format(Locale.ROOT, "%.3fs", seconds);
	}

	private static String trim(Font font, String text, int maxW) {
		if (text == null) {
			return "";
		}
		if (font.width(text) <= maxW) {
			return text;
		}
		String ellipsis = "...";
		int budget = Math.max(0, maxW - font.width(ellipsis));
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (font.width(sb.toString() + c) > budget) {
				break;
			}
			sb.append(c);
		}
		return sb + ellipsis;
	}

	private record HoverZone(int x, int y, int w, int h, List<PvTooltip.Line> lines) {
	}
}
