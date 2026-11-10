/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.rift.page

import fishmod.pv.data.FormatUtil
import fishmod.pv.data.InventorySnapshot
import fishmod.pv.data.RiftSnapshot
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import fishmod.pv.gui.rift.RiftUi
import fishmod.pv.gui.rift.RiftUi.Companion.BAR_AFTER
import fishmod.pv.gui.rift.RiftUi.Companion.BURGER_COLOR
import fishmod.pv.gui.rift.RiftUi.Companion.CAT_COLOR
import fishmod.pv.gui.rift.RiftUi.Companion.CHARM_ICON
import fishmod.pv.gui.rift.RiftUi.Companion.CHARM_ROW
import fishmod.pv.gui.rift.RiftUi.Companion.ENIGMA_COLOR
import fishmod.pv.gui.rift.RiftUi.Companion.EYE_COLOR
import fishmod.pv.gui.rift.RiftUi.Companion.FLIP_MS
import fishmod.pv.gui.rift.RiftUi.Companion.GAP
import fishmod.pv.gui.rift.RiftUi.Companion.MOTES_COLOR
import fishmod.pv.gui.rift.RiftUi.Companion.NOT_OBTAINED
import fishmod.pv.gui.rift.RiftUi.Companion.OBTAINED
import fishmod.pv.gui.rift.RiftUi.Companion.PAD
import fishmod.pv.gui.rift.RiftUi.Companion.PANEL_HOVER
import fishmod.pv.gui.rift.RiftUi.Companion.SEP_GAP
import fishmod.pv.gui.rift.RiftUi.Companion.STAT_ROW
import fishmod.pv.gui.rift.RiftUi.Companion.TIMECHARM_COLOR
import fishmod.pv.gui.rift.RiftUi.Companion.ZONE_COLOR
import fishmod.pv.networth.InventoryDecoder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor

/** Rift overview: progress bars + timecharms / zone unlocks. */
class RiftOverviewPage {
	private var questScroll = 0
	private var questMaxScroll = 0
	private var questViewX = 0
	private var questViewY = 0
	private var questViewW = 0
	private var questViewH = 0

	private var leftHitX = 0
	private var leftHitY = 0
	private var leftHitW = 0
	private var leftHitH = 0
	private var leftQuestFace = false
	private var leftFlipTarget = false
	private var leftFlipStartMs = 0L

	fun resetScroll() {
		questScroll = 0
		leftQuestFace = false
		leftFlipTarget = false
		leftFlipStartMs = 0L
	}

	fun mouseClicked(mx: Double, my: Double): Boolean {
		if (mx < leftHitX || mx >= leftHitX + leftHitW || my < leftHitY || my >= leftHitY + leftHitH) {
			return false
		}
		if (leftFlipStartMs != 0L) {
			return true
		}
		leftFlipTarget = !leftQuestFace
		leftFlipStartMs = System.currentTimeMillis()
		return true
	}

	fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
		if (!showingQuestFace() || questMaxScroll <= 0) {
			return false
		}
		if (mouseX < questViewX || mouseX >= questViewX + questViewW || mouseY < questViewY || mouseY >= questViewY + questViewH) {
			return false
		}
		val next = maxOf(0, minOf(questMaxScroll, questScroll - Math.round(scrollY * 12).toInt()))
		if (next == questScroll) {
			return false
		}
		questScroll = next
		return true
	}

	fun render(
		snapshot: RiftSnapshot, ui: RiftUi,
		g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int,
	) {
		val rightW = maxOf(200, w * 48 / 100)
		val leftW = w - rightW - GAP
		drawOverviewLeftFlip(snapshot, ui, g, font, x, y, leftW, h, mx, my)
		PvDraw.innerPanel(g, x + leftW + GAP, y, rightW, h)
		drawOverviewRight(snapshot, ui, g, font, x + leftW + GAP, y, rightW, h, mx, my)
	}

	private fun showingQuestFace(): Boolean {
		if (leftFlipStartMs == 0L) {
			return leftQuestFace
		}
		val progress = minOf(1f, (System.currentTimeMillis() - leftFlipStartMs) / FLIP_MS.toFloat())
		val angle = easeInOutCubic(progress) * Math.PI.toFloat()
		return if (cos(angle) < 0.0) leftFlipTarget else leftQuestFace
	}

	private fun drawOverviewLeftFlip(
		snapshot: RiftSnapshot, ui: RiftUi,
		g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int,
	) {
		leftHitX = x
		leftHitY = y
		leftHitW = w
		leftHitH = h

		val hovered = mx >= x && mx < x + w && my >= y && my < y + h
		var flipProgress = 0f
		var animating = leftFlipStartMs != 0L
		if (animating) {
			flipProgress = minOf(1f, (System.currentTimeMillis() - leftFlipStartMs) / FLIP_MS.toFloat())
			if (flipProgress >= 1f) {
				leftQuestFace = leftFlipTarget
				leftFlipStartMs = 0L
				animating = false
				flipProgress = 0f
			}
		}
		val angle = (if (animating) easeInOutCubic(flipProgress) else 0f) * Math.PI.toFloat()
		val showQuests = if (animating && cos(angle) < 0.0) leftFlipTarget else leftQuestFace
		var scaleX = 1f
		var scaleY = 1f
		if (animating) {
			scaleY = maxOf(0.04f, abs(cos(angle)))
			scaleX = 1f - (1f - scaleY) * 0.06f
		}

		val cxFlip = x + w / 2f
		val cyFlip = y + h / 2f
		g.pose().pushMatrix()
		g.pose().translate(cxFlip, cyFlip)
		g.pose().scale(scaleX, scaleY)
		g.pose().translate(-cxFlip, -cyFlip)

		PvDraw.innerPanel(g, x, y, w, h)
		if (hovered && !animating) {
			PvDraw.fill(g, x + 1, y + 1, w - 2, h - 2, PANEL_HOVER)
		}

		if (showQuests) {
			drawQuestFace(snapshot, g, font, x, y, w, h)
		} else {
			questMaxScroll = 0
			questViewH = 0
			drawStatsFace(snapshot, ui, g, font, x, y, w, mx, my)
		}

		g.pose().popMatrix()
	}

	private fun drawStatsFace(
		snapshot: RiftSnapshot, ui: RiftUi,
		g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, mx: Int, my: Int,
	) {
		val lx = x + PAD
		var ly = y + PAD
		val lw = w - PAD * 2

		ly = RiftUi.statLine(g, font, "Motes", FormatUtil.commas(snapshot.motesPurse), lx, ly, lw, MOTES_COLOR) + 2
		ly = RiftUi.statLine(g, font, "Lifetime motes", FormatUtil.commas(snapshot.lifetimeMotes), lx, ly, lw, PvDraw.COLOR_MUTED)
		if (snapshot.visits > 0) {
			ly = RiftUi.statLine(g, font, "Visits", FormatUtil.commas(snapshot.visits), lx, ly, lw, PvDraw.COLOR_MUTED)
		}
		if (snapshot.lastFreeAccessMs > 0L) {
			ly = RiftUi.statLine(g, font, "Last free entry", FormatUtil.ago(snapshot.lastFreeAccessMs), lx, ly, lw, PvDraw.COLOR_ACCENT)
		}

		ly = RiftUi.sectionSeparator(g, font, x, ly, w)

		val vamp = snapshot.vampire
		ly = ui.drawLabeledBar(
			g, font, "Vampire", "T" + vamp.level, vamp.fill, vamp.maxed, PvDraw.COLOR_BAR_FILL_SLAYER, vamp.hover,
			lx, ly, lw, mx, my,
		) + BAR_AFTER

		ly = RiftUi.sectionSeparator(g, font, x, ly, w)

		val enigmaTip = "Found " + snapshot.enigmaFound + " / " + RiftSnapshot.ENIGMA_MAX +
			if (snapshot.enigmaCloakBought) " · Cloak owned" else " · Cloak not bought"
		ly = ui.drawLabeledBar(
			g, font, "Enigma souls", "${snapshot.enigmaFound}/${RiftSnapshot.ENIGMA_MAX}",
			snapshot.enigmaFill(), snapshot.enigmaFound >= RiftSnapshot.ENIGMA_MAX,
			ENIGMA_COLOR, enigmaTip, lx, ly, lw, mx, my,
		) + BAR_AFTER

		val charmTip = "${snapshot.timecharmsSecured} / ${RiftSnapshot.TIMECHARM_MAX} secured in the gallery"
		ly = ui.drawLabeledBar(
			g, font, "Timecharms", "${snapshot.timecharmsSecured}/${RiftSnapshot.TIMECHARM_MAX}",
			snapshot.timecharmFill(), snapshot.timecharmsSecured >= RiftSnapshot.TIMECHARM_MAX,
			TIMECHARM_COLOR, charmTip, lx, ly, lw, mx, my,
		) + BAR_AFTER

		ly = ui.drawLabeledBar(
			g, font, "McGrubber's burgers", "${snapshot.burgers}/${RiftSnapshot.BURGER_MAX}",
			snapshot.burgerFill(), snapshot.burgers >= RiftSnapshot.BURGER_MAX,
			BURGER_COLOR, "Grubber stacks from Castle burgers", lx, ly, lw, mx, my,
		) + BAR_AFTER

		val catTip = if (snapshot.montezumaUnlocked) {
			"Montezuma unlocked" + if (snapshot.montezumaTier.isBlank()) "" else " · " + RiftUi.prettyTier(snapshot.montezumaTier)
		} else {
			"Montezuma not unlocked"
		}
		ly = ui.drawLabeledBar(
			g, font, "Montezuma's Souls", "${snapshot.catsFound}/${RiftSnapshot.MONTEZUMA_CATS_MAX}",
			snapshot.catsFill(), snapshot.catsFound >= RiftSnapshot.MONTEZUMA_CATS_MAX,
			CAT_COLOR, catTip, lx, ly, lw, mx, my,
		) + BAR_AFTER

		ui.drawLabeledBar(
			g, font, "Porhtal eyes", "${snapshot.eyesKilled}/${RiftSnapshot.EYES_MAX}",
			snapshot.eyesFill(), snapshot.eyesKilled >= RiftSnapshot.EYES_MAX,
			EYE_COLOR, "Rogue eyes calmed for Porhtal", lx, ly, lw, mx, my,
		)
	}

	private fun drawQuestFace(snapshot: RiftSnapshot, g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int) {
		val lx = x + PAD
		var ly = y + PAD
		val lw = w - PAD * 2
		val bottom = y + h - PAD

		PvDraw.text(g, font, "Quest progress", lx, ly, ZONE_COLOR)
		ly += STAT_ROW + 2

		val quests = snapshot.questRegions
		if (quests.isEmpty()) {
			questMaxScroll = 0
			questViewH = 0
			PvDraw.text(g, font, "No tracked quests", lx, ly, PvDraw.COLOR_MUTED)
			return
		}

		val contentH = questContentHeight(quests)
		questViewX = lx
		questViewY = ly
		questViewW = lw
		questViewH = maxOf(STAT_ROW, bottom - ly)
		questMaxScroll = maxOf(0, contentH - questViewH)
		questScroll = maxOf(0, minOf(questScroll, questMaxScroll))

		g.enableScissor(lx, ly, lx + lw + 3, ly + questViewH)
		var qy = ly - questScroll
		var firstRegion = true
		for (region in quests) {
			if (!region.present()) {
				continue
			}
			if (!firstRegion) {
				qy += REGION_GAP
			}
			firstRegion = false
			if (qy + STAT_ROW > ly && qy < ly + questViewH) {
				PvDraw.text(g, font, RiftUi.trim(font, region.title, lw), lx, qy, PvDraw.COLOR_ACCENT)
			}
			qy += STAT_ROW
			for (line in region.lines) {
				if (qy + font.lineHeight > ly && qy < ly + questViewH) {
					RiftUi.questRow(g, font, line.label, line.value, lx, qy, lw)
				}
				qy += STAT_ROW
			}
		}
		g.disableScissor()
	}

	private fun drawOverviewRight(
		snapshot: RiftSnapshot, ui: RiftUi,
		g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int,
	) {
		val lx = x + PAD
		var ly = y + PAD
		val lw = w - PAD * 2
		val bottom = y + h - PAD

		PvDraw.text(g, font, "Timecharms", lx, ly, TIMECHARM_COLOR)
		ly += STAT_ROW + 2
		for (charm in snapshot.timecharms) {
			if (ly + CHARM_ROW > bottom) {
				break
			}
			ly = drawTimecharmRow(ui, g, font, charm, lx, ly, lw)
		}

		ly = RiftUi.sectionSeparator(g, font, x, ly, w)
		if (ly + STAT_ROW > bottom) {
			return
		}

		PvDraw.text(g, font, "Zone unlocks", lx, ly, ZONE_COLOR)
		ly += STAT_ROW
		val zones = snapshot.purchasedBoundaries
		if (zones.isEmpty()) {
			PvDraw.text(g, font, "None purchased", lx, ly, PvDraw.COLOR_MUTED)
			return
		}
		PvDraw.text(g, font, zones.size.toString() + " boundaries", lx, ly, PvDraw.COLOR_MUTED)
		ly += STAT_ROW + 2
		for (zone in zones) {
			if (ly + STAT_ROW > bottom) {
				break
			}
			val label = InventoryDecoder.prettyWords(zone)
			PvDraw.text(g, font, RiftUi.trim(font, label, lw), lx, ly, PvDraw.COLOR_TEXT)
			ui.zones.add(RiftUi.HoverZone(
				lx, ly, lw, STAT_ROW,
				listOf(PvTooltip.Line.title(label, PvDraw.COLOR_TEXT), PvTooltip.Line.divider(), PvTooltip.Line.meta(zone)),
			))
			ly += STAT_ROW
		}

		ly = drawNameList(g, font, x, ly, w, lx, lw, bottom, "Montezuma's Souls", CAT_COLOR, snapshot.foundCats)
		drawNameList(g, font, x, ly, w, lx, lw, bottom, "Killed eyes", EYE_COLOR, snapshot.killedEyes)
	}

	private fun drawNameList(
		g: GuiGraphicsExtractor, font: Font, x: Int, startY: Int, w: Int, lx: Int, lw: Int, bottom: Int,
		title: String, color: Int, ids: List<String>,
	): Int {
		var ly = startY
		if (ids.isEmpty() || ly + SEP_GAP + STAT_ROW * 2 >= bottom) {
			return ly
		}
		ly = RiftUi.sectionSeparator(g, font, x, ly, w)
		PvDraw.text(g, font, title, lx, ly, color)
		ly += STAT_ROW
		for (id in ids) {
			if (ly + STAT_ROW > bottom) {
				break
			}
			PvDraw.text(g, font, RiftUi.trim(font, InventoryDecoder.prettyWords(id), lw), lx, ly, PvDraw.COLOR_TEXT)
			ly += STAT_ROW
		}
		return ly
	}

	private fun drawTimecharmRow(
		ui: RiftUi, g: GuiGraphicsExtractor, font: Font, charm: RiftSnapshot.Timecharm, x: Int, y: Int, w: Int,
	): Int {
		val icon = SkyBlockItemFactory.toStack(InventorySnapshot.Slot(charm.itemId, 1, listOf(), charm.name, null, null, null))

		val rowAlign = PvDraw.IconTextAlign.of(y, CHARM_ROW, CHARM_ICON, font.lineHeight)
		RiftUi.drawItemIcon(g, icon, charm.itemId, x, rowAlign.iconY, CHARM_ICON)

		val textX = x + CHARM_ICON + 4
		val status = if (charm.secured) "Obtained" else "Not obtained"
		val statusColor = if (charm.secured) OBTAINED else NOT_OBTAINED
		val statusW = font.width(status)
		val nameMax = maxOf(20, w - CHARM_ICON - 4 - statusW - 6)
		PvDraw.text(g, font, RiftUi.trim(font, charm.name, nameMax), textX, rowAlign.textY, charm.color)
		PvDraw.textRight(g, font, status, x + w, rowAlign.textY, statusColor)

		val tip = ArrayList<PvTooltip.Line>()
		tip.add(PvTooltip.Line.title(charm.name, charm.color))
		tip.add(PvTooltip.Line.divider())
		tip.add(PvTooltip.Line.row("Status", PvDraw.COLOR_MUTED, status, statusColor))
		if (charm.visitsToGet > 0) {
			tip.add(PvTooltip.Line.row("Visits to get", PvDraw.COLOR_MUTED, charm.visitsToGet.toString(), PvDraw.COLOR_TEXT))
		}
		if (charm.secured && charm.securedAtMs > 0L) {
			tip.add(PvTooltip.Line.meta("Secured " + RiftUi.formatAgo(charm.securedAtMs)))
		}
		ui.zones.add(RiftUi.HoverZone(x, y, w, CHARM_ROW, tip))
		return y + CHARM_ROW + 1
	}

	private companion object {
		const val REGION_GAP = 6

		fun questContentHeight(quests: List<RiftSnapshot.QuestRegion>): Int {
			val present = quests.filter { it.present() }
			if (present.isEmpty()) {
				return 0
			}
			return (present.size - 1) * REGION_GAP + present.sumOf { STAT_ROW + it.lines.size * STAT_ROW }
		}

		fun easeInOutCubic(t: Float): Float {
			val x = t.coerceIn(0f, 1f)
			return if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
		}
	}
}
