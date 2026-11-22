/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.home

import fishmod.pv.data.FormatUtil
import fishmod.pv.data.ProfileSnapshot
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.util.LegacyChatFormatting
import fishmod.utils.config.values.FishSettings
import java.util.Locale
import kotlin.math.floor
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

class HomeSkillSlayerBars {
	private var cachedSkillBars: List<CachedBar> = listOf()
	private var cachedExtraSkillBars: List<CachedBar> = listOf()
	private var cachedSlayerBars: List<CachedBar> = listOf()
	private var cachedBarColW = -1
	private var cachedActiveSlayerId = ""
	private val slayerNameHits = ArrayList<SlayerNameHit>()

	fun clearCaches() {
		cachedBarColW = -1
		cachedSkillBars = listOf()
		cachedExtraSkillBars = listOf()
		cachedSlayerBars = listOf()
		cachedActiveSlayerId = ""
	}

	fun slayerNameHits(): List<SlayerNameHit> = slayerNameHits

	fun draw(
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		rowH: Int,
		snapshot: ProfileSnapshot,
		zones: MutableList<HomePage.HoverZone>
	) {
		PvDraw.innerPanel(g, x, y, w, h)
		val colGap = 8
		val colW = (w - PAD * 2 - colGap) / 2
		val leftX = x + PAD
		val rightX = leftX + colW + colGap

		val ty = y + PAD
		drawSkillGrid(g, font, leftX, rightX, ty, colW, rowH, snapshot, zones)
		val extraY = ty + SKILL_ROWS * rowH
		drawExtraSkillRow(g, font, leftX, rightX, extraY, colW, rowH, snapshot, zones)
		val skillsBarsBottom = extraY + font.lineHeight + BAR_LABEL_GAP + BAR_H
		val slayersTop = extraY + rowH + SECTION_GAP
		val lineInset = PAD + 6
		val lineY = (skillsBarsBottom + slayersTop) / 2
		val lineW = maxOf(0, w - lineInset * 2)
		if (lineW > 0) {
			PvDraw.fill(g, x + lineInset, lineY, lineW, 1, PvDraw.COLOR_DIVIDER)
		}
		drawSlayerGrid(g, font, leftX, rightX, slayersTop, colW, rowH, snapshot, zones)
	}

	private fun ensureBarCaches(font: Font, colW: Int, snapshot: ProfileSnapshot) {
		val quest = snapshot.activeSlayer
		val activeId = if (quest != null && quest.present()) quest.typeId else ""
		if (colW == cachedBarColW && activeId == cachedActiveSlayerId && cachedSkillBars.isNotEmpty()) {
			return
		}
		cachedBarColW = colW
		cachedActiveSlayerId = activeId
		cachedSkillBars = snapshot.skills.take(SKILL_ROWS * 2).map { skillBar(it) }

		val extra = ArrayList<CachedBar>(2)
		snapshot.runecrafting?.let { extra.add(skillBar(it)) }
		snapshot.social?.let { extra.add(skillBar(it)) }
		cachedExtraSkillBars = extra

		cachedSlayerBars = snapshot.slayers.take(SLAYER_ROWS * 2).map { slayer ->
			val active = activeId.isNotBlank() && activeId.equals(slayer.id, ignoreCase = true)
			val accent = slayerColor(slayer.id)
			CachedBar(
				slayer.name,
				"T" + slayer.tier,
				slayer.progress,
				slayer.maxed,
				PvDraw.COLOR_BAR_FILL_SLAYER,
				accent,
				active,
				slayerHoverLines(slayer, if (active) quest else null, accent),
				slayer.id,
				font.width(slayer.name)
			)
		}
	}

	private fun drawSkillGrid(
		g: GuiGraphicsExtractor, font: Font, leftX: Int, rightX: Int, startY: Int, colW: Int, rowH: Int,
		snapshot: ProfileSnapshot, zones: MutableList<HomePage.HoverZone>
	) {
		ensureBarCaches(font, colW, snapshot)
		drawBarGrid(g, font, cachedSkillBars, leftX, rightX, startY, colW, rowH, zones)
	}

	private fun drawExtraSkillRow(
		g: GuiGraphicsExtractor, font: Font, leftX: Int, rightX: Int, startY: Int, colW: Int, rowH: Int,
		snapshot: ProfileSnapshot, zones: MutableList<HomePage.HoverZone>
	) {
		ensureBarCaches(font, colW, snapshot)
		drawBarGrid(g, font, cachedExtraSkillBars, leftX, rightX, startY, colW, rowH, zones)
	}

	private fun drawSlayerGrid(
		g: GuiGraphicsExtractor, font: Font, leftX: Int, rightX: Int, startY: Int, colW: Int, rowH: Int,
		snapshot: ProfileSnapshot, zones: MutableList<HomePage.HoverZone>
	) {
		ensureBarCaches(font, colW, snapshot)
		slayerNameHits.clear()
		for ((i, bar) in cachedSlayerBars.withIndex()) {
			val bx = if (i % 2 == 0) leftX else rightX
			val by = startY + (i / 2) * rowH
			val zoneH = rowH - 2
			drawCachedBar(g, font, bar, bx, by, colW)
			zones.add(HomePage.HoverZone(bx, by, colW, zoneH, bar.hover))
			if (bar.slayerId.isNotBlank()) {
				slayerNameHits.add(SlayerNameHit(bx, by, colW, zoneH, bar.slayerId, bar.accent))
			}
		}
	}

	@JvmRecord
	data class SlayerNameHit(val x: Int, val y: Int, val w: Int, val h: Int, val slayerId: String, val accent: Int)

	private class CachedBar(
		val label: String,
		val value: String,
		val progress: Float,
		val maxed: Boolean,
		val fillColor: Int,
		val accent: Int,
		val labelBold: Boolean,
		val hover: List<PvTooltip.Line>,
		val slayerId: String = "",
		val nameW: Int = 0
	) {
		constructor(
			label: String, value: String, progress: Float, maxed: Boolean, fillColor: Int, accent: Int,
			hover: List<PvTooltip.Line>
		) : this(label, value, progress, maxed, fillColor, accent, false, hover, "", 0)
	}

	private companion object {
		const val PAD = 6
		const val BAR_H = 6
		const val BAR_LABEL_GAP = 2
		const val SECTION_GAP = 8
		const val SKILL_ROWS = 5
		const val SLAYER_ROWS = 3

		fun skillBar(skill: ProfileSnapshot.SkillEntry): CachedBar {
			if (FishSettings.pvOverflowSkills && skill.maxed && skill.overflowLevel > skill.level) {
				// Uncapped level; the bar stays gold and fills toward the next overflow level.
				val over = skill.overflowLevel
				return CachedBar(
					skill.name,
					floor(over).toInt().toString(),
					over - floor(over),
					true,
					PvDraw.COLOR_BAR_FILL,
					0,
					skill.hoverLines
				)
			}
			return CachedBar(
				skill.name,
				skill.level.toString(),
				skill.progress,
				skill.maxed,
				PvDraw.COLOR_BAR_FILL,
				0,
				skill.hoverLines
			)
		}

		fun drawBarGrid(
			g: GuiGraphicsExtractor, font: Font, bars: List<CachedBar>, leftX: Int, rightX: Int, startY: Int,
			colW: Int, rowH: Int, zones: MutableList<HomePage.HoverZone>
		) {
			for ((i, bar) in bars.withIndex()) {
				val bx = if (i % 2 == 0) leftX else rightX
				val by = startY + (i / 2) * rowH
				val zoneH = rowH - 2
				drawCachedBar(g, font, bar, bx, by, colW)
				zones.add(HomePage.HoverZone(bx, by, colW, zoneH, bar.hover))
			}
		}

		fun drawCachedBar(g: GuiGraphicsExtractor, font: Font, bar: CachedBar, x: Int, y: Int, w: Int) {
			val labelColor = if (bar.accent != 0) bar.accent else PvDraw.COLOR_TEXT
			PvDraw.labeledBar(
				g, font, bar.label, bar.value, bar.progress, x, y, w, bar.fillColor, bar.maxed,
				labelColor, bar.labelBold
			)
		}

		fun slayerHoverLines(
			slayer: ProfileSnapshot.SlayerEntry,
			quest: ProfileSnapshot.ActiveSlayerQuest?,
			accent: Int
		): List<PvTooltip.Line> {
			val lines = ArrayList<PvTooltip.Line>()
			lines.add(PvTooltip.Line.title(slayer.name + " " + slayer.tier, accent))
			lines.addAll(slayer.hoverLines.drop(1))
			if (quest != null && quest.present()) {
				lines.add(PvTooltip.Line.blank())
				lines.add(PvTooltip.Line.title("Active Quest", accent))
				lines.add(PvTooltip.Line.row("Tier", PvDraw.COLOR_MUTED, roman(quest.tier), PvDraw.COLOR_TEXT))
				lines.add(PvTooltip.Line.row(
					"Boss", PvDraw.COLOR_MUTED, if (quest.spawned) "Spawned" else "Not spawned", PvDraw.COLOR_TEXT
				))
				lines.add(PvTooltip.Line.row(
					"Spawn Progress", PvDraw.COLOR_MUTED,
					FormatUtil.commas(Math.round(quest.combatXp).toLong()) + " Combat XP", PvDraw.COLOR_GOLD
				))
				if (quest.island.isNotBlank()) {
					lines.add(PvTooltip.Line.row("Island", PvDraw.COLOR_MUTED, quest.island, PvDraw.COLOR_TEXT))
				}
				lines.add(PvTooltip.Line.row("Mode", PvDraw.COLOR_MUTED, if (quest.solo) "Solo" else "Group", PvDraw.COLOR_TEXT))
			}
			lines.add(PvTooltip.Line.blank())
			lines.add(PvTooltip.Line.action("Click to view calculator"))
			return lines
		}

		fun slayerColor(id: String?): Int {
			if (id.isNullOrBlank()) {
				return PvDraw.COLOR_ACCENT
			}
			val fmt = when (id.lowercase(Locale.ROOT)) {
				"zombie" -> ChatFormatting.GREEN
				"spider" -> ChatFormatting.RED
				"wolf" -> ChatFormatting.AQUA
				"enderman" -> ChatFormatting.DARK_PURPLE
				"blaze" -> ChatFormatting.GOLD
				"vampire" -> ChatFormatting.LIGHT_PURPLE
				else -> ChatFormatting.BLUE
			}
			val rgb = LegacyChatFormatting.rgb(fmt)
			return if (rgb == null) PvDraw.COLOR_ACCENT else 0xFF000000.toInt() or rgb
		}

		fun roman(value: Int): String = when (value) {
			1 -> "I"
			2 -> "II"
			3 -> "III"
			4 -> "IV"
			5 -> "V"
			6 -> "VI"
			7 -> "VII"
			8 -> "VIII"
			9 -> "IX"
			10 -> "X"
			else -> value.toString()
		}
	}
}
