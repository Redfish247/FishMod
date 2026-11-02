/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.crimson.page

import fishmod.pv.data.CrimsonSnapshot
import fishmod.pv.data.FormatUtil
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.crimson.CrimsonUi
import fishmod.pv.gui.crimson.CrimsonUi.BARB_COLOR
import fishmod.pv.gui.crimson.CrimsonUi.DOJO_COLOR
import fishmod.pv.gui.crimson.CrimsonUi.ENABLED
import fishmod.pv.gui.crimson.CrimsonUi.GAP
import fishmod.pv.gui.crimson.CrimsonUi.HoverZone
import fishmod.pv.gui.crimson.CrimsonUi.ITEM_SLOT_BG
import fishmod.pv.gui.crimson.CrimsonUi.KUUDRA_COLOR
import fishmod.pv.gui.crimson.CrimsonUi.MAGE_COLOR
import fishmod.pv.gui.crimson.CrimsonUi.PAD
import fishmod.pv.gui.crimson.CrimsonUi.STAT_ROW
import fishmod.pv.gui.crimson.CrimsonUi.drawItemIcon
import fishmod.pv.gui.crimson.CrimsonUi.formatAgo
import fishmod.pv.gui.crimson.CrimsonUi.sectionSeparator
import fishmod.pv.gui.crimson.CrimsonUi.statLine
import java.util.EnumMap
import java.util.Locale
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/** Crimson Overview: factions, essence, with Dojo and the Crimson Essence Shop stacked on the right. */
class CrimsonOverviewPage {
	private val zones = ArrayList<HoverZone>()

	@Suppress("UNUSED_PARAMETER")
	fun mouseClicked(mx: Double, my: Double): Boolean = false

	fun render(
		g: GuiGraphicsExtractor, font: Font, snapshot: CrimsonSnapshot,
		x: Int, y: Int, w: Int, h: Int, mouseX: Int, mouseY: Int,
		@Suppress("UNUSED_PARAMETER") screenW: Int, @Suppress("UNUSED_PARAMETER") screenH: Int,
	) {
		zones.clear()
		val rightW = maxOf(200, w * 52 / 100)
		val leftW = w - rightW - GAP
		PvDraw.innerPanel(g, x, y, leftW, h)
		drawOverviewLeft(g, font, snapshot, x, y, leftW, mouseX, mouseY)
		val rx = x + leftW + GAP
		PvDraw.innerPanel(g, rx, y, rightW, h)
		drawDojoFace(g, font, snapshot, rx, y, rightW, h)
	}

	fun renderTooltip(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int) {
		CrimsonUi.drawHover(g, font, zones, mouseX, mouseY, screenW, screenH)
	}

	private fun drawOverviewLeft(
		g: GuiGraphicsExtractor, font: Font, snapshot: CrimsonSnapshot,
		x: Int, y: Int, w: Int, @Suppress("UNUSED_PARAMETER") mx: Int, @Suppress("UNUSED_PARAMETER") my: Int,
	) {
		val lx = x + PAD
		var ly = y + PAD
		val lw = w - PAD * 2

		ly = statLine(
			g, font, "Selected faction", snapshot.factionLabel(),
			lx, ly, lw, snapshot.factionColor(MAGE_COLOR, BARB_COLOR, PvDraw.COLOR_MUTED),
		) + 2

		ly = drawFactionCards(g, font, snapshot, lx, ly, lw) + 4

		ly = sectionSeparator(g, font, x, ly, w)
		ly = statLine(g, font, "Matriarch pearls", FormatUtil.commas(snapshot.matriarchPearls.toLong()), lx, ly, lw, PvDraw.COLOR_ACCENT)
		if (snapshot.matriarchLastAttemptMs > 0L) {
			ly = statLine(g, font, "Last pearl attempt", formatAgo(snapshot.matriarchLastAttemptMs), lx, ly, lw, PvDraw.COLOR_MUTED)
		}

		ly = sectionSeparator(g, font, x, ly, w)
		val bosses = snapshot.minibosses
		val killed = snapshot.minibossesKilled()
		val total = maxOf(1, bosses.size)
		val bossY = ly
		ly = statLine(g, font, "Minibosses", "$killed / $total", lx, ly, lw, if (killed >= total) ENABLED else PvDraw.COLOR_TEXT)
		val bossTip = ArrayList<PvTooltip.Line>()
		bossTip.add(PvTooltip.Line.title("Crimson minibosses", PvDraw.COLOR_TEXT))
		bossTip.add(PvTooltip.Line.divider())
		for ((id, done) in bosses) {
			bossTip.add(PvTooltip.Line.row(
				prettyMiniboss(id), PvDraw.COLOR_MUTED,
				if (done) "Killed" else "Not killed",
				if (done) ENABLED else PvDraw.COLOR_MUTED,
			))
		}
		if (snapshot.lastMinibossesKilled.isNotEmpty()) {
			bossTip.add(PvTooltip.Line.blank())
			bossTip.add(PvTooltip.Line.meta("Recent"))
			for (id in snapshot.lastMinibossesKilled) {
				bossTip.add(PvTooltip.Line.row("Recent", PvDraw.COLOR_MUTED, prettyMiniboss(id), PvDraw.COLOR_ACCENT))
			}
		}
		zones.add(HoverZone.of(lx, bossY, lw, STAT_ROW, bossTip))

		ly = sectionSeparator(g, font, x, ly, w)
		ly = statLine(g, font, "Kuudra clears", FormatUtil.commas(snapshot.kuudraTotalCompletions().toLong()), lx, ly, lw, KUUDRA_COLOR)
		val wave = snapshot.kuudraHighestClearedWave()
		ly = statLine(g, font, "Highest wave", if (wave > 0) wave.toString() else "-", lx, ly, lw, PvDraw.COLOR_TEXT)
		if (snapshot.dailyQuests.isNotEmpty()) {
			val dailyTip = ArrayList<PvTooltip.Line>()
			dailyTip.add(PvTooltip.Line.title("Daily quests", PvDraw.COLOR_TEXT))
			dailyTip.add(PvTooltip.Line.divider())
			for ((i, q) in snapshot.dailyQuests.withIndex()) {
				dailyTip.add(PvTooltip.Line.row("#" + (i + 1), PvDraw.COLOR_MUTED, prettyDailyQuest(q), PvDraw.COLOR_ACCENT))
			}
			val dailyY = ly
			ly = statLine(g, font, "Daily quests", snapshot.dailyQuests.size.toString(), lx, ly, lw, PvDraw.COLOR_ACCENT)
			zones.add(HoverZone.of(lx, dailyY, lw, STAT_ROW, dailyTip))
		}
		if (snapshot.cavityNpcs > 0) {
			ly = statLine(g, font, "Cavity NPCs", snapshot.cavityNpcs.toString(), lx, ly, lw, PvDraw.COLOR_TEXT)
		}
		val follower = snapshot.kuudraFollower
		val loreY = ly
		statLine(
			g, font, "Kuudra Loremaster",
			if (follower.loremaster) "Completed" else "Not completed",
			lx, ly, lw, if (follower.loremaster) ENABLED else PvDraw.COLOR_MUTED,
		)
		val loreTip = ArrayList<PvTooltip.Line>()
		loreTip.add(PvTooltip.Line.title("Kuudra Follower Armor quests", KUUDRA_COLOR))
		loreTip.add(PvTooltip.Line.divider())
		loreTip.add(followerRow("Loremaster (Boots)", follower.loremaster, "Completed", "Not completed"))
		loreTip.add(followerRow("Cathedral book", follower.cathedralBook, "Found", "Not found"))
		loreTip.add(followerRow("Kuudra Relic", follower.relicClaimedMs > 0L, "Claimed", "Not claimed"))
		loreTip.add(followerRow("Follower Leggings", follower.leggings, "Claimed", "Not claimed"))
		if (follower.relicClaimedMs > 0L) {
			loreTip.add(PvTooltip.Line.blank())
			loreTip.add(PvTooltip.Line.meta("Relic claimed " + formatAgo(follower.relicClaimedMs)))
		}
		zones.add(HoverZone.of(lx, loreY, lw, STAT_ROW, loreTip))
	}

	/** Side-by-side bordered faction cards. */
	private fun drawFactionCards(g: GuiGraphicsExtractor, font: Font, snapshot: CrimsonSnapshot, x: Int, y: Int, w: Int): Int {
		val gap = 8
		val colW = (w - gap) / 2
		val cardPad = 4
		val iconSize = 16
		val cardH = cardPad * 2 + iconSize + 2 + STAT_ROW

		drawFactionCard(
			g, font, "MAGE", MAGE_ICON,
			snapshot.magesReputation, snapshot.magesReputationHighest, MAGE_COLOR,
			x, y, colW, cardH, cardPad, iconSize,
		)
		drawFactionCard(
			g, font, "BARBARIAN", BARB_ICON,
			snapshot.barbariansReputation, snapshot.barbariansReputationHighest, BARB_COLOR,
			x + colW + gap, y, colW, cardH, cardPad, iconSize,
		)
		return y + cardH
	}

	private fun drawFactionCard(
		g: GuiGraphicsExtractor, font: Font, name: String, icon: ItemStack,
		reputation: Double, peak: Double, color: Int, x: Int, y: Int, w: Int, h: Int, pad: Int, iconSize: Int,
	) {
		PvDraw.roundedFill(g, x, y, w, h, 3, ITEM_SLOT_BG)
		PvDraw.roundedRing(g, x, y, w, h, 3, PvDraw.COLOR_BORDER, PvDraw.C_ALL)

		val ix = x + pad
		val iy = y + pad
		drawItemIcon(g, icon, ix, iy, iconSize)
		val textY = iy + maxOf(0, (iconSize - font.lineHeight) / 2)
		PvDraw.text(g, font, name, ix + iconSize + 3, textY, color)

		val statY = iy + iconSize + 2
		PvDraw.text(g, font, "Reputation:", ix, statY, PvDraw.COLOR_MUTED)
		PvDraw.textRight(g, font, FormatUtil.commas(Math.round(reputation)), x + w - pad, statY, color)
		zones.add(HoverZone.of(x, y, w, h, listOf(
			PvTooltip.Line.title(name, color),
			PvTooltip.Line.divider(),
			PvTooltip.Line.row("Reputation", PvDraw.COLOR_MUTED, FormatUtil.commas(Math.round(reputation)), color),
			PvTooltip.Line.row("Peak", PvDraw.COLOR_MUTED, FormatUtil.commas(Math.round(peak)), PvDraw.COLOR_GOLD),
		)))
	}

	private fun drawDojoFace(g: GuiGraphicsExtractor, font: Font, snapshot: CrimsonSnapshot, x: Int, y: Int, w: Int, h: Int) {
		val lx = x + PAD
		var ly = y + PAD
		val lw = w - PAD * 2

		PvDraw.text(g, font, "Dojo", lx, ly, PvDraw.COLOR_MUTED)
		PvDraw.textRight(g, font, "Total Points: " + FormatUtil.commas(snapshot.dojoTotalPoints().toLong()), lx + lw, ly, DOJO_COLOR)
		ly += font.lineHeight + 2

		val colGap = 6
		val colW = (lw - colGap) / 2
		val leftCol = lx
		val rightCol = lx + colW + colGap
		val challenges = CrimsonSnapshot.DojoChallenge.entries
		val rows = (challenges.size + 1) / 2
		val bottom = y + h - PAD
		val available = maxOf(rows * 22, bottom - ly)
		val entryGap = 1
		// Icon + Points + Rank (no time).
		val entryH = minOf(maxOf(16 + STAT_ROW, (available - (rows - 1) * entryGap) / rows), 16 + STAT_ROW * 3)

		for ((i, challenge) in challenges.withIndex()) {
			val col = i % 2
			val row = i / 2
			val cx = if (col == 0) leftCol else rightCol
			val cy = ly + row * (entryH + entryGap)
			if (cy + 16 + STAT_ROW > bottom) {
				break
			}
			drawDojoEntry(g, font, snapshot, challenge, cx, cy, colW, entryH)
		}
	}

	private fun drawDojoEntry(
		g: GuiGraphicsExtractor, font: Font, snapshot: CrimsonSnapshot, challenge: CrimsonSnapshot.DojoChallenge,
		x: Int, y: Int, w: Int, entryH: Int,
	) {
		val score = snapshot.dojo(challenge)
		val icon = 16
		drawItemIcon(g, dojoIcon(challenge), x, y, icon)
		val textY = y + maxOf(0, (icon - font.lineHeight) / 2)
		PvDraw.text(g, font, challenge.label, x + icon + 3, textY, PvDraw.COLOR_TEXT)

		var ly = y + icon + 1
		ly = statLine(g, font, "Points", FormatUtil.commas(score.points.toLong()), x, ly, w, DOJO_COLOR)
		if (ly + font.lineHeight <= y + entryH) {
			val rank = CrimsonSnapshot.dojoRank(score.points)
			statLine(g, font, "Rank", rank, x, ly, w, CrimsonSnapshot.dojoRankColor(rank))
		}
		if (score.timeMs > 0L) {
			zones.add(HoverZone.of(x, y, w, entryH, listOf(
				PvTooltip.Line.title(challenge.label, DOJO_COLOR),
				PvTooltip.Line.divider(),
				PvTooltip.Line.row("Points", PvDraw.COLOR_MUTED, FormatUtil.commas(score.points.toLong()), DOJO_COLOR),
				PvTooltip.Line.row("Time", PvDraw.COLOR_MUTED, FormatUtil.prettyTime(score.timeMs), PvDraw.COLOR_TEXT),
			)))
		}
	}

	private companion object {
		val DOJO_ICONS = EnumMap<CrimsonSnapshot.DojoChallenge, ItemStack>(CrimsonSnapshot.DojoChallenge::class.java)
		val MAGE_ICON = ItemStack(Items.BLAZE_POWDER)
		val BARB_ICON = ItemStack(Items.IRON_AXE)

		fun followerRow(label: String, done: Boolean, yes: String, no: String): PvTooltip.Line =
			PvTooltip.Line.row(label, PvDraw.COLOR_MUTED, if (done) yes else no, if (done) ENABLED else PvDraw.COLOR_MUTED)

		fun capitalizeWords(text: String): String {
			val out = StringBuilder(text.length)
			var cap = true
			for (c in text) {
				if (c == ' ') {
					out.append(c)
					cap = true
				} else if (cap) {
					out.append(c.uppercaseChar())
					cap = false
				} else {
					out.append(c)
				}
			}
			return out.toString()
		}

		fun prettyDailyQuest(id: String?): String {
			if (id.isNullOrBlank()) {
				return "?"
			}
			var raw = id.trim().lowercase(Locale.ROOT).removePrefix("crimson_isle_")
			raw = raw.replace('_', ' ').replace(Regex("\\s+"), " ").trim()
			// Drop trailing letter grades like " a" / " b" / " c" when they look like tier tags.
			if (raw.length > 2 && raw[raw.length - 2] == ' ' && raw[raw.length - 1].isLetter()) {
				val grade = raw[raw.length - 1].lowercaseChar()
				if (grade in 'a'..'d') {
					raw = raw.substring(0, raw.length - 2)
				}
			}
			val pretty = capitalizeWords(raw)
				.replace("Kuudra Hot Tier", "Kuudra (Hot)")
				.replace("Kuudra Burning Tier", "Kuudra (Burning)")
				.replace("Kuudra Fiery Tier", "Kuudra (Fiery)")
				.replace("Kuudra Infernal Tier", "Kuudra (Infernal)")
				.replace("Dojo Test Of", "Dojo:")
				.replace("Kb Drating", "Knockback")
				.replace("Fetch Magmag", "Fetch Magmaggies")
			return pretty.ifBlank { "?" }
		}

		fun prettyMiniboss(id: String?): String {
			if (id.isNullOrBlank()) {
				return "?"
			}
			return when (id.uppercase(Locale.ROOT)) {
				"BLADESOUL" -> "Bladesoul"
				"MAGE_OUTLAW" -> "Mage Outlaw"
				"BARBARIAN_DUKE_X" -> "Barbarian Duke X"
				"ASHFANG" -> "Ashfang"
				"MAGMA_BOSS" -> "Magma Boss"
				else -> capitalizeWords(id.lowercase(Locale.ROOT).replace('_', ' '))
			}
		}

		fun dojoIcon(challenge: CrimsonSnapshot.DojoChallenge): ItemStack = DOJO_ICONS.getOrPut(challenge) {
			when (challenge) {
				CrimsonSnapshot.DojoChallenge.MOB_KB -> ItemStack(Items.BLAZE_ROD)
				CrimsonSnapshot.DojoChallenge.WALL_JUMP -> ItemStack(Items.RABBIT_FOOT)
				CrimsonSnapshot.DojoChallenge.ARCHER -> ItemStack(Items.LEAD)
				CrimsonSnapshot.DojoChallenge.SWORD_SWAP -> ItemStack(Items.DIAMOND_SWORD)
				CrimsonSnapshot.DojoChallenge.SNAKE -> ItemStack(Items.BOW)
				CrimsonSnapshot.DojoChallenge.FIREBALL -> ItemStack(Items.FIRE_CHARGE)
				CrimsonSnapshot.DojoChallenge.LOCK_HEAD -> ItemStack(Items.ENDER_EYE)
			}
		}
	}
}
