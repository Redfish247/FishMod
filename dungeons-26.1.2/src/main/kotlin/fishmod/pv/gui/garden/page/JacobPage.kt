/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.garden.page

import fishmod.pv.data.FormatUtil
import fishmod.pv.data.GardenData
import fishmod.pv.data.GardenSnapshot
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.garden.GardenUi
import fishmod.pv.gui.garden.GardenUi.Companion.BRONZE
import fishmod.pv.gui.garden.GardenUi.Companion.CREDIT_C
import fishmod.pv.gui.garden.GardenUi.Companion.CREDIT_SCALE
import fishmod.pv.gui.garden.GardenUi.Companion.DIAMOND
import fishmod.pv.gui.garden.GardenUi.Companion.FLIP_MS
import fishmod.pv.gui.garden.GardenUi.Companion.GAP
import fishmod.pv.gui.garden.GardenUi.Companion.GHOST
import fishmod.pv.gui.garden.GardenUi.Companion.GOLD
import fishmod.pv.gui.garden.GardenUi.Companion.ICON
import fishmod.pv.gui.garden.GardenUi.Companion.MEDAL_ORB_EMPTY
import fishmod.pv.gui.garden.GardenUi.Companion.ORB
import fishmod.pv.gui.garden.GardenUi.Companion.ORB_GAP
import fishmod.pv.gui.garden.GardenUi.Companion.PAD
import fishmod.pv.gui.garden.GardenUi.Companion.PANEL_HOVER
import fishmod.pv.gui.garden.GardenUi.Companion.PLATINUM
import fishmod.pv.gui.garden.GardenUi.Companion.SILVER
import fishmod.pv.gui.garden.GardenUi.Companion.STAT_ROW
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/** Garden Jacob contests subpage. */
class JacobPage {
	private var scroll = 0
	private var maxScroll = 0
	private var scrollTop = 0
	private var scrollH = 0
	private var leftScroll = 0
	private var leftMaxScroll = 0
	private var leftScrollTop = 0
	private var leftScrollH = 0
	private var leftScrollX = 0
	private var leftScrollW = 0
	private var jacobExtrasFace = false
	private var jacobFlipTarget = false
	private var jacobFlipStartMs = 0L
	private var jacobHitX = 0
	private var jacobHitY = 0
	private var jacobHitW = 0
	private var jacobHitH = 0
	private var jacobHovered = false

	fun render(snap: GardenSnapshot, ui: GardenUi, g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int) {
		val rightW = maxOf(200, w * 52 / 100)
		val leftW = w - rightW - GAP
		PvDraw.innerPanel(g, x, y, leftW, h)

		val lx = x + PAD
		val ly = y + PAD
		val lw = leftW - PAD * 2

		leftScrollX = lx
		leftScrollW = lw
		leftScrollTop = ly
		leftScrollH = h - PAD * 2

		val contentH = measureJacobLeft(snap, font)
		leftMaxScroll = maxOf(0, contentH - leftScrollH)
		leftScroll = minOf(leftScroll, leftMaxScroll)

		g.enableScissor(lx, leftScrollTop, lx + lw + 3, leftScrollTop + leftScrollH)
		var cy = leftScrollTop - leftScroll

		PvDraw.text(g, font, "Unspent medals", lx, cy, PvDraw.COLOR_MUTED)
		ui.zones.add(GardenUi.HoverZone(
			lx, cy, lw, font.lineHeight, listOf(
				PvTooltip.Line.title("Unspent medals", PvDraw.COLOR_TEXT),
				PvTooltip.Line.divider(),
				PvTooltip.Line.of("Medals held, not yet spent at Anita.", PvDraw.COLOR_TEXT),
				PvTooltip.Line.meta("Not lifetime medal finishes")
			)
		))
		cy += font.lineHeight + 3
		val m = snap.medals
		cy = GardenUi.statLine(g, font, "Bronze", FormatUtil.commas(m.bronze.toLong()), lx, cy, lw, BRONZE) + 1
		cy = GardenUi.statLine(g, font, "Silver", FormatUtil.commas(m.silver.toLong()), lx, cy, lw, SILVER) + 1
		cy = GardenUi.statLine(g, font, "Gold", FormatUtil.commas(m.gold.toLong()), lx, cy, lw, GOLD) + 1
		cy = GardenUi.statLine(g, font, "Total", FormatUtil.commas(m.total().toLong()), lx, cy, lw, PvDraw.COLOR_TEXT) + 4

		if (snap.uniqueBrackets.isNotEmpty()) {
			PvDraw.text(g, font, "Unique brackets", lx, cy, PvDraw.COLOR_MUTED)
			cy += font.lineHeight + 2
			for (b in snap.uniqueBrackets) {
				cy = GardenUi.statLine(g, font, b.bracket, b.crops.toString(), lx, cy, lw, PvDraw.COLOR_TEXT) + 1
			}
			cy += 3
		}

		val cropMedals = snap.cropMedals()
		PvDraw.text(g, font, "Crop medals", lx, cy, PvDraw.COLOR_MUTED)
		cy += font.lineHeight + 3
		if (cropMedals.isEmpty()) {
			PvDraw.text(g, font, "None yet", lx, cy, PvDraw.COLOR_MUTED)
			cy += font.lineHeight + 4
		} else {
			val rowH = maxOf(STAT_ROW, ICON + 2)
			val orbsW = 5 * ORB + 4 * ORB_GAP
			for (medal in cropMedals) {
				GardenUi.drawIcon(g, medal.iconId, lx, cy + (rowH - ICON) / 2, ICON, GardenData.cropPackModel(medal.id))
				val textX = lx + ICON + 4
				val nameMax = maxOf(8, lw - ICON - 8 - orbsW)
				val shown = GardenUi.trim(font, medal.name, nameMax)
				PvDraw.text(g, font, shown, textX, cy + (rowH - font.lineHeight) / 2, PvDraw.COLOR_TEXT)
				drawMedalOrbs(g, lx + lw - orbsW, cy + (rowH - ORB) / 2, medal.filled)
				val tipMedal = when (medal.filled) {
					1 -> "Bronze"
					2 -> "Silver"
					3 -> "Gold"
					4 -> "Platinum"
					5 -> "Diamond"
					else -> "None"
				}
				val tip = ArrayList<PvTooltip.Line>()
				tip.add(PvTooltip.Line.of(medal.name, PvDraw.COLOR_TEXT))
				tip.add(PvTooltip.Line.of(
					"Highest unique: $tipMedal",
					if (medal.filled <= 0) PvDraw.COLOR_MUTED else medalOrbColor(medal.filled - 1)
				))
				addPlacementLines(tip, snap, medal.id)
				ui.zones.add(GardenUi.HoverZone(lx, cy, lw, rowH, tip))
				cy += rowH + 1
			}
			cy += 3
		}

		if (snap.perks.isNotEmpty()) {
			PvDraw.text(g, font, "Perks", lx, cy, PvDraw.COLOR_MUTED)
			cy += font.lineHeight + 2
			for ((key, value) in snap.perks) {
				cy = GardenUi.statLine(g, font, perkName(key), value.toString(), lx, cy, lw, PvDraw.COLOR_ACCENT) + 1
			}
		}
		g.disableScissor()

		drawJacobContestPanel(snap, ui, g, font, x + leftW + GAP, y, rightW, h, mx, my)
	}

	private fun drawJacobContestPanel(snap: GardenSnapshot, ui: GardenUi, g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int) {
		jacobHitX = x
		jacobHitY = y
		jacobHitW = w
		jacobHitH = h

		val hovered = mx >= x && mx < x + w && my >= y && my < y + h
		jacobHovered = hovered
		var flipProgress = 0F
		var animating = jacobFlipStartMs != 0L
		if (animating) {
			flipProgress = minOf(1F, (System.currentTimeMillis() - jacobFlipStartMs) / FLIP_MS.toFloat())
			if (flipProgress >= 1F) {
				jacobExtrasFace = jacobFlipTarget
				jacobFlipStartMs = 0L
				animating = false
				flipProgress = 0F
			}
		}
		val eased = if (animating) easeInOutCubic(flipProgress) else 0F
		val angle = eased * PI.toFloat()
		val showExtras = if (animating) {
			if (cos(angle.toDouble()) < 0.0) jacobFlipTarget else jacobExtrasFace
		} else {
			jacobExtrasFace
		}
		var scaleX = 1F
		var scaleY = 1F
		if (animating) {
			scaleX = maxOf(0.04F, abs(cos(angle.toDouble()).toFloat()))
			scaleY = 1F - (1F - scaleX) * 0.06F
		}

		val cxFlip = x + w / 2F
		val cyFlip = y + h / 2F
		g.pose().pushMatrix()
		g.pose().translate(cxFlip, cyFlip)
		g.pose().scale(scaleX, scaleY)
		g.pose().translate(-cxFlip, -cyFlip)

		PvDraw.innerPanel(g, x, y, w, h)
		if (hovered && !animating) {
			PvDraw.fill(g, x + 1, y + 1, w - 2, h - 2, PANEL_HOVER)
		}

		if (showExtras) {
			drawJacobExtrasFace(snap, ui, g, font, x, y, w, h)
		} else {
			drawJacobContestsFace(snap, ui, g, font, x, y, w, h)
		}

		g.pose().popMatrix()
	}

	private fun drawJacobContestsFace(snap: GardenSnapshot, ui: GardenUi, g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int) {
		val rx = x + PAD
		var ry = y + PAD
		val rw = w - PAD * 2
		PvDraw.text(g, font, "Contest history", rx, ry, PvDraw.COLOR_MUTED)
		drawFlipHint(g, font, "Click for bests", rx, ry, rw)
		ry += font.lineHeight + 3

		val credit = Component.translatable("fishmod.pv.garden.elite_credit").string
		val creditH = Math.round(font.lineHeight * CREDIT_SCALE) + 4
		PvDraw.textScaled(g, font, credit, rx, y + h - PAD - Math.round(font.lineHeight * CREDIT_SCALE), CREDIT_C, CREDIT_SCALE)

		if (snap.contestsLoading) {
			PvDraw.text(g, font, "Loading contests...", rx, ry, PvDraw.COLOR_MUTED)
			maxScroll = 0
			return
		}
		if (snap.contestsError.isNotBlank() && snap.contests.isEmpty()) {
			PvDraw.text(g, font, GardenUi.trim(font, snap.contestsError, rw), rx, ry, PvDraw.COLOR_MUTED)
			maxScroll = 0
			return
		}

		val contests = snap.contests
		val rowH = maxOf(STAT_ROW, ICON + 2)
		val listTop = ry
		val listH = y + h - PAD - creditH - listTop
		scrollTop = listTop
		scrollH = maxOf(0, listH)
		maxScroll = maxOf(0, contests.size * rowH - listH)
		scroll = minOf(scroll, maxScroll)

		g.enableScissor(rx, listTop, rx + rw + 3, listTop + listH)
		var yy = listTop - scroll
		for (c in contests) {
			if (yy + rowH >= listTop && yy < listTop + listH) {
				GardenUi.drawIcon(g, c.iconId, rx, yy + (rowH - ICON) / 2, ICON, GardenData.cropPackModel(c.crop))
				val textX = rx + ICON + 4
				val textW = rw - ICON - 4
				val medalLabel = medalDisplay(c.medal)
				var right = FormatUtil.shortXp(c.collected.toDouble())
				if (medalLabel.isNotBlank()) {
					right = "$medalLabel  $right"
				}
				GardenUi.drawPair(
					g, font, c.cropName, right, textX, yy + (rowH - font.lineHeight) / 2, textW,
					PvDraw.COLOR_TEXT, medalColor(c.medal)
				)
				val tip = ArrayList<PvTooltip.Line>()
				tip.add(PvTooltip.Line.of(c.cropName, PvDraw.COLOR_TEXT))
				tip.add(PvTooltip.Line.of("Collected: " + FormatUtil.commas(c.collected), PvDraw.COLOR_MUTED))
				if (hasMedalLabel(c.medal)) {
					tip.add(PvTooltip.Line.of("Medal: " + GardenUi.title(c.medal), medalColor(c.medal)))
				} else if ("unclaimable".equals(c.medal, ignoreCase = true)) {
					tip.add(PvTooltip.Line.of("Medal: Unclaimable", PvDraw.COLOR_MUTED))
				}
				if (c.placement() > 0) {
					tip.add(PvTooltip.Line.of(
						"#" + c.placement() + (if (c.participants > 0) " / " + c.participants else ""),
						PvDraw.COLOR_MUTED
					))
				}
				if (c.timestampSeconds > 0L) {
					tip.add(PvTooltip.Line.of(contestWhen(c.timestampSeconds), PvDraw.COLOR_MUTED))
				}
				ui.zones.add(GardenUi.HoverZone(rx, yy, rw, rowH, tip))
			}
			yy += rowH
		}
		g.disableScissor()
	}

	private fun drawJacobExtrasFace(snap: GardenSnapshot, ui: GardenUi, g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int) {
		val rx = x + PAD
		var ry = y + PAD
		val rw = w - PAD * 2
		val bottom = y + h - PAD

		val uniqueGolds = snap.uniqueGoldCrops()
		val goldHeader = if (uniqueGolds.isEmpty()) "Unique golds" else "Unique golds (" + uniqueGolds.size + ")"
		PvDraw.text(g, font, goldHeader, rx, ry, PvDraw.COLOR_MUTED)
		ui.zones.add(GardenUi.HoverZone(
			rx, ry, font.width(goldHeader), font.lineHeight, listOf(
				PvTooltip.Line.title("Unique golds", GOLD),
				PvTooltip.Line.divider(),
				PvTooltip.Line.of("Crops with a gold medal earned at least once.", PvDraw.COLOR_TEXT),
				PvTooltip.Line.meta("Not total gold finishes or unspent medals")
			)
		))
		drawFlipHint(g, font, "Click for history", rx, ry, rw)
		ry += font.lineHeight + 3

		val goldBlockH: Int
		if (uniqueGolds.isEmpty()) {
			PvDraw.text(g, font, "None yet", rx, ry, PvDraw.COLOR_MUTED)
			goldBlockH = font.lineHeight + 4
		} else {
			val cols = maxOf(4, minOf(8, (rw + 2) / (ICON + 2)))
			val cell = ICON + 2
			for ((i, cropId) in uniqueGolds.withIndex()) {
				val bx = rx + (i % cols) * cell
				val by = ry + (i / cols) * cell
				val iconId = GardenData.cropIconId(cropId)
				GardenUi.drawIcon(g, iconId, bx, by, ICON, GardenData.cropPackModel(cropId))
				ui.zones.add(GardenUi.HoverZone(
					bx, by, ICON, ICON, listOf(
						PvTooltip.Line.of(GardenData.prettyCrop(cropId), GOLD),
						PvTooltip.Line.of("Unique gold medal", PvDraw.COLOR_MUTED)
					)
				))
			}
			val rows = (uniqueGolds.size + cols - 1) / cols
			goldBlockH = rows * cell + 4
		}
		ry += goldBlockH

		PvDraw.text(g, font, "Personal bests", rx, ry, PvDraw.COLOR_MUTED)
		ry += font.lineHeight + 3

		val pbs = snap.personalBests
		val rowH = maxOf(STAT_ROW, ICON + 2)
		scrollTop = ry
		scrollH = maxOf(0, bottom - ry)
		maxScroll = maxOf(0, pbs.size * rowH - scrollH)
		scroll = minOf(scroll, maxScroll)

		g.enableScissor(rx, scrollTop, rx + rw + 3, scrollTop + scrollH)
		var yy = scrollTop - scroll
		for (pb in pbs) {
			if (yy + rowH >= scrollTop && yy < scrollTop + scrollH) {
				val iconId = GardenData.cropIconId(pb.id)
				GardenUi.drawIcon(g, iconId, rx, yy + (rowH - ICON) / 2, ICON, GardenData.cropPackModel(pb.id))
				val textX = rx + ICON + 4
				val textW = rw - ICON - 4
				GardenUi.drawPair(
					g, font, pb.name, FormatUtil.commas(pb.amount), textX,
					yy + (rowH - font.lineHeight) / 2, textW, PvDraw.COLOR_TEXT, PvDraw.COLOR_ACCENT
				)
				val tip = ArrayList<PvTooltip.Line>()
				tip.add(PvTooltip.Line.of(pb.name, PvDraw.COLOR_TEXT))
				tip.add(PvTooltip.Line.of(FormatUtil.commas(pb.amount) + " collected", PvDraw.COLOR_MUTED))
				addPlacementLines(tip, snap, pb.id)
				ui.zones.add(GardenUi.HoverZone(rx, yy, rw, rowH, tip))
			}
			yy += rowH
		}
		g.disableScissor()
	}

	private fun drawFlipHint(g: GuiGraphicsExtractor, font: Font, hint: String, rx: Int, ry: Int, rw: Int) {
		val hintW = font.width(hint)
		PvDraw.text(g, font, hint, rx + rw - hintW, ry, if (jacobHovered) PvDraw.COLOR_ACCENT else PvDraw.COLOR_MUTED)
	}

	private fun measureJacobLeft(snap: GardenSnapshot, font: Font): Int {
		var h = font.lineHeight + 3 + STAT_ROW * 4 + 4
		if (snap.uniqueBrackets.isNotEmpty()) {
			h += font.lineHeight + 2 + snap.uniqueBrackets.size * (STAT_ROW + 1) + 3
		}
		h += font.lineHeight + 3
		val medals = snap.cropMedals()
		h += if (medals.isEmpty()) {
			font.lineHeight + 4
		} else {
			medals.size * (maxOf(STAT_ROW, ICON + 2) + 1) + 3
		}
		if (snap.perks.isNotEmpty()) {
			h += font.lineHeight + 2 + snap.perks.size * (STAT_ROW + 1)
		}
		return h
	}

	fun resetScroll() {
		scroll = 0
		leftScroll = 0
	}

	fun clearScrollExtents() {
		maxScroll = 0
		leftMaxScroll = 0
	}

	fun resetFlip() {
		jacobExtrasFace = false
		jacobFlipTarget = false
		jacobFlipStartMs = 0L
	}

	fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
		val overLeft = leftMaxScroll > 0
			&& mouseX >= leftScrollX && mouseX < leftScrollX + leftScrollW
			&& mouseY >= leftScrollTop && mouseY < leftScrollTop + leftScrollH
		if (overLeft) {
			val next = maxOf(0, minOf(leftMaxScroll, leftScroll + (if (scrollY > 0) -14 else 14)))
			if (next != leftScroll) {
				leftScroll = next
				return true
			}
			return false
		}
		if (maxScroll <= 0) {
			return false
		}
		if (mouseY < scrollTop || mouseY >= scrollTop + scrollH) {
			return false
		}
		val next = maxOf(0, minOf(maxScroll, scroll + (if (scrollY > 0) -14 else 14)))
		if (next == scroll) {
			return false
		}
		scroll = next
		return true
	}

	fun mouseClicked(mx: Double, my: Double): Boolean {
		if (mx < jacobHitX || mx >= jacobHitX + jacobHitW || my < jacobHitY || my >= jacobHitY + jacobHitH) {
			return false
		}
		if (jacobFlipStartMs != 0L) {
			return true
		}
		jacobFlipTarget = !jacobExtrasFace
		jacobFlipStartMs = System.currentTimeMillis()
		return true
	}

	private companion object {
		fun addPlacementLines(tip: MutableList<PvTooltip.Line>, snap: GardenSnapshot, cropId: String) {
			if (snap.contestsLoading) {
				tip.add(PvTooltip.Line.meta("Loading contests..."))
				return
			}
			val p = snap.placement(cropId)
			if (p == null) {
				tip.add(PvTooltip.Line.meta("No contests for this crop"))
				return
			}
			tip.add(PvTooltip.Line.blank())
			if (p.best > 0) {
				val value = "#" + FormatUtil.commas(p.best.toLong()) +
					(if (p.participants > 0) " / " + FormatUtil.commas(p.participants.toLong()) else "")
				tip.add(PvTooltip.Line.row("Best placement", PvDraw.COLOR_MUTED, value, PvDraw.COLOR_ACCENT))
			} else {
				tip.add(PvTooltip.Line.row("Best placement", PvDraw.COLOR_MUTED, "None", PvDraw.COLOR_MUTED))
			}
			tip.add(PvTooltip.Line.row("Contests", PvDraw.COLOR_MUTED, FormatUtil.commas(p.contests.toLong()), PvDraw.COLOR_TEXT))
			if (p.unplaced > 0) {
				tip.add(PvTooltip.Line.meta(FormatUtil.commas(p.unplaced.toLong()) + " unclaimed contests"))
			}
		}

		fun drawMedalOrbs(g: GuiGraphicsExtractor, x: Int, y: Int, filled: Int) {
			for (i in 0 until 5) {
				val ox = x + i * (ORB + ORB_GAP)
				val color = if (i < filled) medalOrbColor(i) else MEDAL_ORB_EMPTY
				drawOrb(g, ox, y, ORB, color)
			}
		}

		fun medalOrbColor(index: Int): Int = when (index) {
			0 -> BRONZE
			1 -> SILVER
			2 -> GOLD
			3 -> PLATINUM
			4 -> DIAMOND
			else -> MEDAL_ORB_EMPTY
		}

		fun drawOrb(g: GuiGraphicsExtractor, x: Int, y: Int, size: Int, argb: Int) {
			// Odd sizes center cleanly; even sizes read as knobbly squares.
			val r = size / 2
			val mid = (size - 1) * 0.5F
			val r2 = r * r + 0.25F
			val baseR = (argb ushr 16) and 0xFF
			val baseG = (argb ushr 8) and 0xFF
			val baseB = argb and 0xFF
			val baseA = (argb ushr 24) and 0xFF
			for (dy in 0 until size) {
				for (dx in 0 until size) {
					val cx = dx - mid
					val cy = dy - mid
					val dist2 = cx * cx + cy * cy
					if (dist2 > r2) {
						continue
					}
					// Soft sphere shade so medals read as circles, not flat squares.
					var shade = 1.0F - (cy / maxOf(1F, r.toFloat())) * 0.22F
					shade = shade.coerceIn(0.72F, 1.18F)
					if (cx * cx + (cy + r * 0.35F) * (cy + r * 0.35F) < r2 * 0.22F) {
						shade = minOf(1.28F, shade + 0.18F)
					}
					val pr = clampByte(Math.round(baseR * shade))
					val pg = clampByte(Math.round(baseG * shade))
					val pb = clampByte(Math.round(baseB * shade))
					PvDraw.fill(g, x + dx, y + dy, 1, 1, (baseA shl 24) or (pr shl 16) or (pg shl 8) or pb)
				}
			}
		}

		fun clampByte(value: Int): Int = value.coerceIn(0, 255)

		fun easeInOutCubic(t: Float): Float =
			if (t < 0.5F) 4F * t * t * t else 1F - (-2F * t + 2F).toDouble().pow(3).toFloat() / 2F

		fun contestWhen(timestampSeconds: Long): String {
			var sec = timestampSeconds
			if (sec > 10_000_000_000L) {
				sec /= 1000L
			}
			val ago = maxOf(0L, (System.currentTimeMillis() / 1000L) - sec)
			return when {
				ago < 60 -> ago.toString() + "s ago"
				ago < 3600 -> (ago / 60).toString() + "m ago"
				ago < 86_400 -> (ago / 3600).toString() + "h ago"
				ago < 86_400L * 45L -> (ago / 86_400L).toString() + "d ago"
				else -> (ago / (86_400L * 30L)).toString() + "mo ago"
			}
		}

		fun perkName(id: String?): String {
			if (id == null) {
				return "?"
			}
			return when (id.lowercase(Locale.ROOT)) {
				"double_drops" -> "Double Drops"
				"farming_level_cap" -> "Farming Cap"
				"personal_bests" -> "Personal Bests"
				else -> GardenUi.title(id.replace('_', ' '))
			}
		}

		fun hasMedalLabel(medal: String?): Boolean {
			if (medal.isNullOrBlank()) {
				return false
			}
			val m = medal.lowercase(Locale.ROOT)
			return m != "none" && m != "unclaimable"
		}

		fun medalDisplay(medal: String?): String = if (!hasMedalLabel(medal)) "" else GardenUi.title(medal)

		fun medalColor(medal: String?): Int {
			if (medal == null) {
				return PvDraw.COLOR_MUTED
			}
			return when (medal.lowercase(Locale.ROOT)) {
				"bronze" -> BRONZE
				"silver" -> SILVER
				"gold" -> GOLD
				"platinum" -> PLATINUM
				"diamond" -> DIAMOND
				"ghost" -> GHOST
				else -> PvDraw.COLOR_MUTED
			}
		}
	}
}
