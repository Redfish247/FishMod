/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.home.page

import fishmod.pv.data.FormatUtil
import fishmod.pv.data.MiscStatsSnapshot
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.home.HomeUi.GAP
import fishmod.pv.gui.home.HomeUi.HEADER_COMMUNITY
import fishmod.pv.gui.home.HomeUi.HEADER_DEATHS
import fishmod.pv.gui.home.HomeUi.HEADER_HIGHLIGHTS
import fishmod.pv.gui.home.HomeUi.HEADER_KILLS
import fishmod.pv.gui.home.HomeUi.HEADER_PETS
import fishmod.pv.gui.home.HomeUi.HEADER_PROFILE
import fishmod.pv.gui.home.HomeUi.PAD
import fishmod.pv.gui.home.HomeUi.SEP_GAP
import fishmod.pv.gui.home.HomeUi.STAT_ROW
import java.util.Locale
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

/** Home → Records: dragon fights, race best times, Diana burrows, combat bests, event bests. */
class RecordsPage {
	private var snapshot: MiscStatsSnapshot = MiscStatsSnapshot.empty()
	private val zones = ArrayList<HoverZone>()

	fun apply(snapshot: MiscStatsSnapshot?) {
		this.snapshot = snapshot ?: MiscStatsSnapshot.empty()
		zones.clear()
	}

	fun render(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int) {
		zones.clear()
		val colW = (w - GAP * 2) / 3
		val midX = x + colW + GAP
		val rightX = midX + colW + GAP
		val rightW = w - colW * 2 - GAP * 2
		drawDragons(g, font, x, y, colW, h)

		val racesH = minOf(panelH(font, maxOf(1, snapshot.races.size), 0), h - GAP - MIN_PANEL_H)
		drawRaces(g, font, midX, y, colW, racesH)
		drawDiana(g, font, midX, y + racesH + GAP, colW, h - racesH - GAP)

		val s = snapshot.seasonal
		val eventRows = (if (s.winterPresent()) 3 else 0) + (if (s.spookyPresent()) 3 else 0)
		val eventsH = minOf(
			panelH(font, maxOf(1, eventRows), if (s.winterPresent() && s.spookyPresent()) 1 else 0),
			h - GAP - MIN_PANEL_H
		)
		drawCombat(g, font, rightX, y, rightW, h - eventsH - GAP)
		drawEvents(g, font, rightX, y + h - eventsH, rightW, eventsH)
	}

	private fun drawCombat(g: GuiGraphicsExtractor, font: Font, px: Int, py: Int, pw: Int, ph: Int) {
		PvDraw.innerPanel(g, px, py, pw, ph)
		val x = px + PAD
		val w = pw - PAD * 2
		val bottom = py + ph - PAD
		val s = snapshot
		var cy = header(g, font, "Combat", "", x, py + PAD, w, HEADER_DEATHS)
		cy = stat(
			g, font, "Highest Damage", if (s.highestDamage > 0.0) FormatUtil.shortXp(s.highestDamage) else "-",
			x, cy, w, PvDraw.COLOR_GOLD, listOf(
				PvTooltip.Line.title("Highest Damage", HEADER_DEATHS),
				PvTooltip.Line.divider(),
				PvTooltip.Line.row("Damage", PvDraw.COLOR_MUTED, FormatUtil.commas(s.highestDamage.toLong()), PvDraw.COLOR_GOLD)
			)
		)
		cy = stat(
			g, font, "Highest Crit", if (s.highestCriticalDamage > 0.0) FormatUtil.shortXp(s.highestCriticalDamage) else "-",
			x, cy, w, PvDraw.COLOR_GOLD, listOf(
				PvTooltip.Line.title("Highest Critical Hit", HEADER_DEATHS),
				PvTooltip.Line.divider(),
				PvTooltip.Line.row("Damage", PvDraw.COLOR_MUTED, FormatUtil.commas(s.highestCriticalDamage.toLong()), PvDraw.COLOR_GOLD)
			)
		)
		val kdTip = ArrayList<PvTooltip.Line>()
		kdTip.add(PvTooltip.Line.title("Kills / Deaths", HEADER_DEATHS))
		kdTip.add(PvTooltip.Line.divider())
		kdTip.add(PvTooltip.Line.row("Kills", PvDraw.COLOR_MUTED, FormatUtil.commas(s.killsTotal), HEADER_KILLS))
		kdTip.add(PvTooltip.Line.row("Deaths", PvDraw.COLOR_MUTED, FormatUtil.commas(s.deathsTotal), HEADER_DEATHS))
		if (s.deaths.isNotEmpty()) {
			kdTip.add(PvTooltip.Line.blank())
			kdTip.add(PvTooltip.Line.of("Top death causes", PvDraw.COLOR_MUTED))
			for (death in s.deaths.take(5)) {
				kdTip.add(PvTooltip.Line.row(death.label, PvDraw.COLOR_TEXT, FormatUtil.commas(death.count), HEADER_DEATHS))
			}
		}
		cy = stat(
			g, font, "K/D", if (s.killsTotal > 0L) FormatUtil.twoDecimals(s.killDeathRatio()) else "-",
			x, cy, w, PvDraw.COLOR_TEXT, kdTip
		)
		if (s.kills.isEmpty() || cy + SEP_GAP + STAT_ROW * 2 > bottom) {
			return
		}
		cy = separator(g, px, cy, pw)
		PvDraw.text(g, font, "Most Killed", x, cy, PvDraw.COLOR_MUTED)
		cy += STAT_ROW
		for (kill in s.kills) {
			if (cy + STAT_ROW > bottom) {
				break
			}
			val value = FormatUtil.commas(kill.count)
			cy = stat(g, font, trim(font, kill.label, w - font.width(value) - 6), value, x, cy, w, HEADER_KILLS, null)
		}
	}

	private fun drawEvents(g: GuiGraphicsExtractor, font: Font, px: Int, py: Int, pw: Int, ph: Int) {
		PvDraw.innerPanel(g, px, py, pw, ph)
		val x = px + PAD
		val w = pw - PAD * 2
		val s = snapshot.seasonal
		var cy = header(g, font, "Events", "", x, py + PAD, w, HEADER_PROFILE)
		if (!s.winterPresent() && !s.spookyPresent()) {
			PvDraw.text(g, font, "No event stats", x, cy, PvDraw.COLOR_MUTED)
			return
		}
		if (s.winterPresent()) {
			val tip = listOf(
				PvTooltip.Line.title("Jerry's Workshop", HEADER_PROFILE),
				PvTooltip.Line.divider(),
				PvTooltip.Line.row("Snowballs Hit", PvDraw.COLOR_MUTED, FormatUtil.commas(s.snowballsHit), PvDraw.COLOR_GOLD),
				PvTooltip.Line.row("Damage Dealt", PvDraw.COLOR_MUTED, FormatUtil.commas(s.winterDamage), PvDraw.COLOR_GOLD),
				PvTooltip.Line.row("Magma Damage", PvDraw.COLOR_MUTED, FormatUtil.commas(s.magmaDamage), PvDraw.COLOR_GOLD),
				PvTooltip.Line.meta("Best in a single event")
			)
			cy = stat(g, font, "Snowballs Hit", FormatUtil.commas(s.snowballsHit), x, cy, w, PvDraw.COLOR_GOLD, tip)
			cy = stat(g, font, "Winter Damage", FormatUtil.commas(s.winterDamage), x, cy, w, PvDraw.COLOR_GOLD, tip)
			cy = stat(g, font, "Magma Damage", FormatUtil.commas(s.magmaDamage), x, cy, w, PvDraw.COLOR_GOLD, tip)
			if (s.spookyPresent()) {
				cy = separator(g, px, cy, pw)
			}
		}
		if (s.spookyPresent()) {
			cy = stat(
				g, font, "Candy", FormatUtil.commas(s.candyTotal), x, cy, w, PvDraw.COLOR_TEXT, listOf(
					PvTooltip.Line.title("Spooky Festival Candy", HEADER_HIGHLIGHTS),
					PvTooltip.Line.divider(),
					PvTooltip.Line.row("Green", PvDraw.COLOR_MUTED, FormatUtil.commas(s.greenCandy), CANDY_GREEN),
					PvTooltip.Line.row("Purple", PvDraw.COLOR_MUTED, FormatUtil.commas(s.purpleCandy), CANDY_PURPLE),
					PvTooltip.Line.row("Total", PvDraw.COLOR_MUTED, FormatUtil.commas(s.candyTotal), PvDraw.COLOR_TEXT),
					PvTooltip.Line.row("Festivals", PvDraw.COLOR_MUTED, FormatUtil.commas(s.festivals.toLong()), PvDraw.COLOR_TEXT)
				)
			)
			var bestTip: MutableList<PvTooltip.Line>? = null
			if (s.bestFestivalYear > 0) {
				bestTip = mutableListOf(
					PvTooltip.Line.title("Best Spooky Festival", HEADER_HIGHLIGHTS),
					PvTooltip.Line.divider(),
					PvTooltip.Line.row("Year", PvDraw.COLOR_MUTED, s.bestFestivalYear.toString(), PvDraw.COLOR_TEXT),
					PvTooltip.Line.row("Points", PvDraw.COLOR_MUTED, FormatUtil.commas(s.bestFestivalPoints), PvDraw.COLOR_GOLD),
					PvTooltip.Line.row("Green", PvDraw.COLOR_MUTED, FormatUtil.commas(s.bestFestivalGreen), CANDY_GREEN),
					PvTooltip.Line.row("Purple", PvDraw.COLOR_MUTED, FormatUtil.commas(s.bestFestivalPurple), CANDY_PURPLE)
				)
				if (s.festivalHistoryIncomplete()) {
					bestTip.add(PvTooltip.Line.blank())
					bestTip.add(PvTooltip.Line.meta(
						"History covers " + FormatUtil.commas(s.festivalHistoryCandy) + " / " + FormatUtil.commas(s.candyTotal) + " candy"
					))
				}
			}
			cy = stat(
				g, font, "Best Festival", if (s.bestFestivalPoints > 0L) FormatUtil.commas(s.bestFestivalPoints) else "-",
				x, cy, w, PvDraw.COLOR_GOLD, bestTip
			)
			stat(g, font, "Bats Spawned", FormatUtil.commas(s.batsSpawned), x, cy, w, PvDraw.COLOR_TEXT, null)
		}
	}

	fun renderTooltip(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int) {
		val zone = zones.firstOrNull { mouseX >= it.x && mouseX < it.x + it.w && mouseY >= it.y && mouseY < it.y + it.h } ?: return
		PvTooltip.drawStyled(g, font, zone.lines, mouseX, mouseY, screenW, screenH)
	}

	private fun drawDragons(g: GuiGraphicsExtractor, font: Font, px: Int, py: Int, pw: Int, ph: Int) {
		PvDraw.innerPanel(g, px, py, pw, ph)
		val x = px + PAD
		val w = pw - PAD * 2
		val bottom = py + ph - PAD
		val d = snapshot.dragons
		var cy = header(
			g, font, "Dragons", if (d.summoned > 0L) FormatUtil.commas(d.summoned) + " summoned" else "",
			x, py + PAD, w, HEADER_COMMUNITY
		)
		if (!d.present()) {
			PvDraw.text(g, font, "No dragon fights", x, cy, PvDraw.COLOR_MUTED)
			return
		}
		cy = stat(g, font, "Best Damage", if (d.bestDamage > 0.0) FormatUtil.shortXp(d.bestDamage) else "-", x, cy, w, PvDraw.COLOR_GOLD, null)
		cy = stat(g, font, "Fastest Kill", killTime(d.fastestKillMs), x, cy, w, PvDraw.COLOR_GOLD, null)
		cy = stat(g, font, "Eyes Placed", FormatUtil.commas(d.eyesPlaced), x, cy, w, PvDraw.COLOR_TEXT, null)
		cy = stat(g, font, "Eyes Collected", FormatUtil.commas(d.eyesCollected), x, cy, w, PvDraw.COLOR_TEXT, null)
		cy = stat(g, font, "Special Zealots", FormatUtil.commas(d.specialZealots), x, cy, w, PvDraw.COLOR_TEXT, null)
		if (d.dragons.isEmpty() || cy + SEP_GAP + STAT_ROW > bottom) {
			return
		}
		cy = separator(g, px, cy, pw)
		for (dragon in d.dragons) {
			if (cy + STAT_ROW > bottom) {
				break
			}
			val tip = ArrayList<PvTooltip.Line>()
			tip.add(PvTooltip.Line.title(dragon.label + " Dragon", dragonColor(dragon.id)))
			tip.add(PvTooltip.Line.divider())
			tip.add(PvTooltip.Line.row("Summoned", PvDraw.COLOR_MUTED, FormatUtil.commas(dragon.summoned), PvDraw.COLOR_TEXT))
			tip.add(PvTooltip.Line.row("Eyes Placed", PvDraw.COLOR_MUTED, FormatUtil.commas(dragon.eyes), PvDraw.COLOR_TEXT))
			tip.add(PvTooltip.Line.row(
				"Most Damage", PvDraw.COLOR_MUTED,
				if (dragon.mostDamage > 0.0) FormatUtil.commas(dragon.mostDamage.toLong()) else "-", PvDraw.COLOR_GOLD
			))
			tip.add(PvTooltip.Line.row("Fastest Kill", PvDraw.COLOR_MUTED, killTime(dragon.fastestKillMs), PvDraw.COLOR_GOLD))
			if (dragon.bestRank > 0) {
				tip.add(PvTooltip.Line.row("Best Placement", PvDraw.COLOR_MUTED, "#" + dragon.bestRank, PvDraw.COLOR_TEXT))
			}
			cy = dragonRow(g, font, dragon, x, cy, w, tip)
		}
	}

	private fun dragonRow(
		g: GuiGraphicsExtractor, font: Font, dragon: MiscStatsSnapshot.DragonStat, x: Int, y: Int, w: Int,
		tip: List<PvTooltip.Line>
	): Int {
		val damage = if (dragon.mostDamage > 0.0) FormatUtil.shortXp(dragon.mostDamage) else "-"
		val slash = " / "
		val time = killTime(dragon.fastestKillMs)
		var right = x + w
		PvDraw.textRight(g, font, damage, right, y, DRAGON_DAMAGE)
		right -= font.width(damage)
		PvDraw.textRight(g, font, slash, right, y, PvDraw.COLOR_MUTED)
		right -= font.width(slash)
		PvDraw.textRight(g, font, time, right, y, DRAGON_TIME)
		right -= font.width(time)

		val count = " " + FormatUtil.commas(dragon.summoned) + "x"
		val name = trim(font, dragon.label, right - x - font.width(count) - 6)
		PvDraw.text(g, font, name, x, y, dragonColor(dragon.id))
		PvDraw.text(g, font, count, x + font.width(name), y, DRAGON_COUNT)
		zones.add(HoverZone(x, y, w, STAT_ROW, tip))
		return y + STAT_ROW
	}

	private fun drawRaces(g: GuiGraphicsExtractor, font: Font, px: Int, py: Int, pw: Int, ph: Int) {
		PvDraw.innerPanel(g, px, py, pw, ph)
		val x = px + PAD
		val w = pw - PAD * 2
		val bottom = py + ph - PAD
		val races = snapshot.races
		var cy = header(g, font, "Races", "", x, py + PAD, w, HEADER_PETS)
		if (races.isEmpty()) {
			PvDraw.text(g, font, "No races completed", x, cy, PvDraw.COLOR_MUTED)
			return
		}
		for (race in races) {
			if (cy + STAT_ROW > bottom) {
				break
			}
			val time = raceTime(race.bestMs)
			val tip = ArrayList<PvTooltip.Line>()
			tip.add(PvTooltip.Line.title(race.label, HEADER_PETS))
			tip.add(PvTooltip.Line.divider())
			if (race.modes.size == 1 && race.modes[0].mode == race.label) {
				tip.add(PvTooltip.Line.row("Best Time", PvDraw.COLOR_MUTED, time, PvDraw.COLOR_GOLD))
			} else {
				for (mode in race.modes) {
					tip.add(PvTooltip.Line.row(mode.mode, PvDraw.COLOR_MUTED, raceTime(mode.ms), PvDraw.COLOR_GOLD))
				}
			}
			val label = trim(font, race.label, w - font.width(time) - 6)
			cy = stat(g, font, label, time, x, cy, w, PvDraw.COLOR_GOLD, tip)
		}
	}

	private fun drawDiana(g: GuiGraphicsExtractor, font: Font, px: Int, py: Int, pw: Int, ph: Int) {
		PvDraw.innerPanel(g, px, py, pw, ph)
		val x = px + PAD
		val w = pw - PAD * 2
		val bottom = py + ph - PAD
		val m = snapshot.mythos
		var cy = header(g, font, "Diana", "", x, py + PAD, w, HEADER_HIGHLIGHTS)
		if (!m.present()) {
			PvDraw.text(g, font, "No Diana stats", x, cy, PvDraw.COLOR_MUTED)
			return
		}
		cy = stat(
			g, font, "Mythological Kills", FormatUtil.commas(m.kills), x, cy, w, PvDraw.COLOR_GOLD, listOf(
				PvTooltip.Line.title("Mythological Kills", HEADER_HIGHLIGHTS),
				PvTooltip.Line.divider(),
				PvTooltip.Line.of("Mythological creatures killed.", PvDraw.COLOR_TEXT),
				PvTooltip.Line.meta(DIANA_LIFETIME)
			)
		)
		if (m.burrows.isEmpty() || cy + SEP_GAP + STAT_ROW > bottom) {
			return
		}
		cy = separator(g, px, cy, pw)
		PvDraw.text(g, font, "Burrows Dug", x, cy, PvDraw.COLOR_MUTED)
		cy += STAT_ROW
		for (burrow in m.burrows) {
			if (cy + STAT_ROW > bottom) {
				break
			}
			val value = FormatUtil.commas(burrow.total)
			val label = trim(font, burrow.label, w - font.width(value) - 6)
			cy = stat(g, font, label, value, x, cy, w, PvDraw.COLOR_TEXT, burrowTip(burrow))
		}
	}

	private fun stat(
		g: GuiGraphicsExtractor, font: Font, label: String, value: String?,
		x: Int, y: Int, w: Int, valueColor: Int, tip: List<PvTooltip.Line>?
	): Int {
		PvDraw.text(g, font, label, x, y, PvDraw.COLOR_MUTED)
		PvDraw.textRight(g, font, if (value.isNullOrBlank()) "-" else value, x + w, y, valueColor)
		if (!tip.isNullOrEmpty()) {
			zones.add(HoverZone(x, y, w, STAT_ROW, tip))
		}
		return y + STAT_ROW
	}

	private class HoverZone(val x: Int, val y: Int, val w: Int, val h: Int, val lines: List<PvTooltip.Line>)

	companion object {
		private const val MIN_PANEL_H = 70
		private const val CANDY_GREEN = 0xFF55FF55.toInt()
		private const val CANDY_PURPLE = 0xFFAA00AA.toInt()
		private const val DRAGON_COUNT = 0xFF555555.toInt()
		private const val DRAGON_TIME = 0xFF55FF55.toInt()
		private const val DRAGON_DAMAGE = 0xFFFF5555.toInt()
		private const val DIANA_LIFETIME = "Lifetime, all Diana events on this profile"

		private fun panelH(font: Font, rows: Int, separators: Int): Int =
			PAD * 2 + font.lineHeight + 4 + rows * STAT_ROW + separators * SEP_GAP

		private fun dragonColor(id: String?): Int = when (id?.lowercase(Locale.ROOT) ?: "") {
			"protector" -> 0xFF99978B.toInt()
			"old" -> 0xFFF0E6AA.toInt()
			"wise" -> 0xFF29F0E9.toInt()
			"unstable" -> 0xFFB212E3.toInt()
			"young" -> 0xFFDDE4F0.toInt()
			"strong" -> 0xFFD91E41.toInt()
			"superior" -> 0xFFF2DF11.toInt()
			"holy" -> 0xFF47D147.toInt()
			else -> PvDraw.COLOR_TEXT
		}

		@JvmStatic
		fun burrowTip(burrow: MiscStatsSnapshot.BurrowCount): List<PvTooltip.Line> {
			val tip = ArrayList<PvTooltip.Line>()
			val chains = burrow.key == "burrows_chains_complete"
			tip.add(PvTooltip.Line.title(if (chains) "Chains Completed" else burrow.label + " Burrows", HEADER_HIGHLIGHTS))
			tip.add(PvTooltip.Line.divider())
			val about = when (burrow.key) {
				"burrows_dug_next" -> "First burrow of each chain. Points to the next one."
				"burrows_dug_combat" -> "Burrows that spawned a Mythological creature."
				"burrows_dug_treasure" -> "Burrows that dropped treasure."
				"burrows_chains_complete" -> "Griffin burrow chains finished."
				else -> null
			}
			if (about != null) {
				tip.add(PvTooltip.Line.of(about, PvDraw.COLOR_TEXT))
			}
			if (burrow.byRarity.isNotEmpty()) {
				tip.add(PvTooltip.Line.blank())
				tip.add(PvTooltip.Line.of("By Griffin rarity used", PvDraw.COLOR_MUTED))
				for ((key, value) in burrow.byRarity) {
					tip.add(PvTooltip.Line.row(key, PvDraw.COLOR_MUTED, FormatUtil.commas(value), PvDraw.COLOR_TEXT))
				}
			}
			tip.add(PvTooltip.Line.row("Total", PvDraw.COLOR_MUTED, FormatUtil.commas(burrow.total), PvDraw.COLOR_GOLD))
			tip.add(PvTooltip.Line.blank())
			tip.add(PvTooltip.Line.meta(DIANA_LIFETIME))
			return tip
		}

		private fun header(g: GuiGraphicsExtractor, font: Font, title: String, right: String?, x: Int, y: Int, w: Int, color: Int): Int {
			PvDraw.text(g, font, title, x, y, color)
			if (!right.isNullOrBlank()) {
				PvDraw.textRight(g, font, right, x + w, y, PvDraw.COLOR_MUTED)
			}
			return y + font.lineHeight + 4
		}

		private fun separator(g: GuiGraphicsExtractor, panelX: Int, y: Int, panelW: Int): Int {
			val lineInset = PAD + 4
			val lineW = maxOf(0, panelW - lineInset * 2)
			val lineY = y + (SEP_GAP - 1) / 2
			if (lineW > 0) {
				PvDraw.fill(g, panelX + lineInset, lineY, lineW, 1, 0x33FFFFFF)
			}
			return y + SEP_GAP
		}

		private fun killTime(ms: Long): String = if (ms <= 0L) "-" else String.format(Locale.ROOT, "%.2fs", ms / 1000.0)

		private fun raceTime(ms: Long): String {
			if (ms <= 0L) {
				return "-"
			}
			val minutes = ms / 60_000L
			val seconds = (ms % 60_000L) / 1000.0
			return if (minutes > 0L) {
				String.format(Locale.ROOT, "%d:%06.3f", minutes, seconds)
			} else {
				String.format(Locale.ROOT, "%.3fs", seconds)
			}
		}

		private fun trim(font: Font, text: String?, maxW: Int): String {
			if (text == null) {
				return ""
			}
			if (font.width(text) <= maxW) {
				return text
			}
			val ellipsis = "..."
			val budget = maxOf(0, maxW - font.width(ellipsis))
			val sb = StringBuilder()
			for (c in text) {
				if (font.width(sb.toString() + c) > budget) {
					break
				}
				sb.append(c)
			}
			return sb.toString() + ellipsis
		}
	}
}
