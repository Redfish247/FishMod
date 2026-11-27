/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.home

import fishmod.pv.ProfileViewer
import fishmod.pv.data.ProfileSnapshot
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.SkyBlockLevelColors
import fishmod.pv.gui.home.HomePage.SbXpExpandPhase
import fishmod.pv.networth.InventoryDecoder
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

class HomeSbXpOverlay {
	private var phase = SbXpExpandPhase.CLOSED
	private var animStartMs = 0L
	private var hitX = 0
	private var hitY = 0
	private var hitW = 0
	private var hitH = 0
	private var homeBoundsX = 0
	private var homeBoundsY = 0
	private var homeBoundsW = 0
	private var homeBoundsH = 0
	private var emblemScroll = 0
	private var emblemMaxScroll = 0
	private var emblemListX = 0
	private var emblemListY = 0
	private var emblemListW = 0
	private var emblemListH = 0

	fun resetEmblemScroll() {
		emblemScroll = 0
	}

	fun setLayoutBounds(homeX: Int, homeY: Int, homeW: Int, homeH: Int, stripX: Int, stripW: Int) {
		homeBoundsX = homeX
		homeBoundsY = homeY
		homeBoundsW = homeW
		homeBoundsH = homeH
		hitX = stripX
		hitY = homeY
		hitW = stripW
		hitH = homeH
	}

	fun phase(): SbXpExpandPhase {
		tick()
		return phase
	}

	fun isActive(): Boolean {
		tick()
		return phase != SbXpExpandPhase.CLOSED
	}

	fun forceClose() {
		phase = SbXpExpandPhase.CLOSED
		animStartMs = 0L
	}

	fun click(mouseX: Double, mouseY: Double): Boolean {
		tick()
		if (phase == SbXpExpandPhase.EXPANDING_LEFT
			|| phase == SbXpExpandPhase.EXPANDING_RIGHT
			|| phase == SbXpExpandPhase.COLLAPSING_RIGHT
			|| phase == SbXpExpandPhase.COLLAPSING_LEFT
		) {
			return true
		}
		if (phase == SbXpExpandPhase.OPEN) {
			if (mouseX >= homeBoundsX && mouseX < homeBoundsX + homeBoundsW
				&& mouseY >= homeBoundsY && mouseY < homeBoundsY + homeBoundsH
			) {
				startCollapse()
				return true
			}
			return false
		}
		if (mouseX >= hitX && mouseX < hitX + hitW
			&& mouseY >= hitY && mouseY < hitY + hitH
			&& hitW > 0
		) {
			startExpand()
			return true
		}
		return false
	}

	fun requestBack(): Boolean {
		tick()
		if (phase == SbXpExpandPhase.CLOSED
			|| phase == SbXpExpandPhase.COLLAPSING_RIGHT
			|| phase == SbXpExpandPhase.COLLAPSING_LEFT
		) {
			return false
		}
		if (phase == SbXpExpandPhase.EXPANDING_LEFT) {
			val p = stageProgress()
			phase = SbXpExpandPhase.COLLAPSING_LEFT
			animStartMs = System.currentTimeMillis() - Math.round((1F - p) * SB_XP_STAGE_MS)
			return true
		}
		if (phase == SbXpExpandPhase.EXPANDING_RIGHT) {
			val p = stageProgress()
			phase = SbXpExpandPhase.COLLAPSING_RIGHT
			animStartMs = System.currentTimeMillis() - Math.round((1F - p) * SB_XP_STAGE_MS)
			return true
		}
		startCollapse()
		return true
	}

	fun tick() {
		if (phase == SbXpExpandPhase.CLOSED || phase == SbXpExpandPhase.OPEN) {
			return
		}
		if (stageProgress() < 1F) {
			return
		}
		phase = when (phase) {
			SbXpExpandPhase.EXPANDING_LEFT -> SbXpExpandPhase.EXPANDING_RIGHT
			SbXpExpandPhase.EXPANDING_RIGHT -> SbXpExpandPhase.OPEN
			SbXpExpandPhase.COLLAPSING_RIGHT -> SbXpExpandPhase.COLLAPSING_LEFT
			SbXpExpandPhase.COLLAPSING_LEFT -> SbXpExpandPhase.CLOSED
			else -> phase
		}
		if (phase == SbXpExpandPhase.EXPANDING_RIGHT || phase == SbXpExpandPhase.COLLAPSING_LEFT) {
			animStartMs = System.currentTimeMillis()
		} else if (phase == SbXpExpandPhase.OPEN || phase == SbXpExpandPhase.CLOSED) {
			animStartMs = 0L
		}
	}

	fun stageProgress(): Float {
		if (animStartMs == 0L) {
			return 1F
		}
		return minOf(1F, (System.currentTimeMillis() - animStartMs) / SB_XP_STAGE_MS.toFloat())
	}

	fun mouseScrolled(mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
		if (phase == SbXpExpandPhase.OPEN && emblemMaxScroll > 0
			&& mouseX >= emblemListX && mouseX < emblemListX + emblemListW
			&& mouseY >= emblemListY && mouseY < emblemListY + emblemListH
		) {
			val next = maxOf(0, minOf(emblemMaxScroll, emblemScroll - Math.round(scrollY * 12).toInt()))
			if (next != emblemScroll) {
				emblemScroll = next
				return true
			}
		}
		return false
	}

	fun draw(
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		snapshot: ProfileSnapshot,
		contentAlpha: Float,
		registerHover: Boolean,
		zones: MutableList<HomePage.HoverZone>
	) {
		PvDraw.innerPanel(g, x, y, w, h)
		val emptyPage = phase == SbXpExpandPhase.OPEN
			|| (phase == SbXpExpandPhase.EXPANDING_RIGHT && contentAlpha < 0.05F)
			|| (phase == SbXpExpandPhase.COLLAPSING_RIGHT && contentAlpha < 0.05F)
		if (emptyPage) {
			drawExpanded(g, font, x, y, w, h, snapshot)
			return
		}
		if (contentAlpha < 0.04F) {
			return
		}

		val level = snapshot.skyBlockLevel
		val xp = snapshot.skyBlockXpIntoLevel
		val levelText = Component.translatable("fishmod.pv.home.sb_level", level).string
		val levelColor = fadeColor(SkyBlockLevelColors.colorFor(level), contentAlpha)
		val muted = fadeColor(PvDraw.COLOR_MUTED, contentAlpha)
		val white = fadeColor(PvDraw.COLOR_WHITE, contentAlpha)
		val barW = minOf(w - 16, 48)
		val cx = x + w / 2

		var blockH = font.lineHeight + 4 + ICON_SIZE + 4 + font.lineHeight + 2 + BAR_H
		val compactEmblems = snapshot.emblems
		val showEmblemCount = compactEmblems != null && compactEmblems.present()
		if (showEmblemCount) {
			blockH += 4 + font.lineHeight
		}
		var ty = y + maxOf(PAD, (h - blockH) / 2)

		PvDraw.textCentered(g, font, levelText, cx, ty, levelColor)
		ty += font.lineHeight + 4
		g.blit(
			RenderPipelines.GUI_TEXTURED,
			SKYBLOCK_XP_ICON,
			cx - ICON_SIZE / 2, ty,
			0f, 0f,
			ICON_SIZE, ICON_SIZE,
			SKYBLOCK_XP_TEX_SIZE, SKYBLOCK_XP_TEX_SIZE,
			SKYBLOCK_XP_TEX_SIZE, SKYBLOCK_XP_TEX_SIZE
		)
		ty += ICON_SIZE + 4

		val xpLine = Component.empty()
			.append(PvDraw.styled(xp.toString(), white, false))
			.append(PvDraw.styled("/", muted, false))
			.append(PvDraw.styled("100", levelColor, false))
		PvDraw.textCentered(g, font, xpLine, cx, ty)
		ty += font.lineHeight + 2

		val barX = cx - barW / 2
		PvDraw.progressBar(g, barX, ty, barW, BAR_H, snapshot.skyBlockProgress(), levelColor)
		if (showEmblemCount) {
			ty += BAR_H + 4
			PvDraw.textCentered(g, font, "Emblems: " + compactEmblems!!.unlocked.size, cx, ty, muted)
		}
		if (registerHover && contentAlpha > 0.85F) {
			val pct = snapshot.skyBlockProgress() * 100.0
			val xpHover = ArrayList<PvTooltip.Line>()
			xpHover.add(PvTooltip.Line.of("SkyBlock Level $level", SkyBlockLevelColors.colorFor(level)))
			xpHover.add(PvTooltip.Line.of(xp.toString() + "/100 (" + Math.round(pct) + "%)", PvDraw.COLOR_GOLD))
			xpHover.addAll(emblemHoverLines(snapshot.emblems, true))
			xpHover.add(PvTooltip.Line.of("Click to open", PvDraw.COLOR_MUTED))
			zones.add(HomePage.HoverZone(x, y, w, h, xpHover))
		}
	}

	private fun drawExpanded(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, snapshot: ProfileSnapshot) {
		val level = snapshot.skyBlockLevel
		val xp = snapshot.skyBlockXpIntoLevel
		val levelColor = SkyBlockLevelColors.colorFor(level)
		val pad = PAD + 6
		var ly = y + pad
		val barW = minOf(w - pad * 2, 160)

		PvDraw.textCentered(g, font, Component.translatable("fishmod.pv.home.sb_level", level).string, x + w / 2, ly, levelColor)
		ly += font.lineHeight + 6
		val xpLine = Component.empty()
			.append(PvDraw.styled(xp.toString(), PvDraw.COLOR_WHITE, false))
			.append(PvDraw.styled(" / ", PvDraw.COLOR_MUTED, false))
			.append(PvDraw.styled("100", levelColor, false))
		PvDraw.textCentered(g, font, xpLine, x + w / 2, ly)
		ly += font.lineHeight + 4
		PvDraw.progressBar(g, x + (w - barW) / 2, ly, barW, BAR_H, snapshot.skyBlockProgress(), levelColor)
		ly += BAR_H + 12

		emblemListH = 0
		emblemMaxScroll = 0
		PvDraw.textCentered(g, font, "Coming soon", x + w / 2, ly + 20, PvDraw.COLOR_MUTED)
	}

	private fun startExpand() {
		phase = SbXpExpandPhase.EXPANDING_LEFT
		animStartMs = System.currentTimeMillis()
	}

	private fun startCollapse() {
		phase = SbXpExpandPhase.COLLAPSING_RIGHT
		animStartMs = System.currentTimeMillis()
	}

	companion object {
		private val SKYBLOCK_XP_ICON: Identifier = ProfileViewer.id("textures/gui/skyblock_xp.png")
		private const val SKYBLOCK_XP_TEX_SIZE = 64
		private const val ICON_SIZE = 16
		private const val BAR_H = 6
		private const val PAD = 6
		private const val SB_XP_STAGE_MS = 300
		const val SB_XP_MASK = 0xB0000000.toInt()

		private fun emblemHoverLines(emblems: ProfileSnapshot.EmblemInfo?, includeHeader: Boolean): List<PvTooltip.Line> {
			if (emblems == null || !emblems.present()) {
				return listOf()
			}
			val lines = ArrayList<PvTooltip.Line>()
			if (includeHeader) {
				lines.add(PvTooltip.Line.divider())
			}
			lines.add(PvTooltip.Line.row("Emblems", PvDraw.COLOR_MUTED, emblems.unlocked.size.toString() + " unlocked", PvDraw.COLOR_ACCENT))
			if (emblems.selected.isNotBlank()) {
				lines.add(PvTooltip.Line.row("Selected", PvDraw.COLOR_MUTED, InventoryDecoder.prettyWords(emblems.selected), PvDraw.COLOR_GOLD))
			}
			return lines
		}

		private fun fadeColor(argb: Int, alpha: Float): Int {
			val a = Math.round(((argb ushr 24) and 0xFF) * alpha.coerceIn(0F, 1F)).coerceIn(0, 255)
			return (a shl 24) or (argb and 0x00FFFFFF)
		}
	}
}
