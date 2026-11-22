/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.home

import com.google.gson.JsonObject
import fishmod.cosmetic.badge.BadgeManager
import fishmod.pv.data.FormatUtil
import fishmod.pv.data.HypixelRanks
import fishmod.pv.data.PlayerStatsSnapshot
import fishmod.pv.data.PlayerStatus
import fishmod.pv.data.ProfileSnapshot
import fishmod.pv.gui.PlayerModelRenderer
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.SkyBlockLevelColors
import fishmod.pv.gui.SkyBlockStats
import fishmod.pv.networth.NetworthBreakdown
import fishmod.pv.weight.WeightBreakdown
import fishmod.pv.weight.WeightSystem
import java.util.Locale
import java.util.Optional
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.world.item.ItemStack

class HomeLeftColumn {
	private var weightHitX = 0
	private var weightHitY = 0
	private var weightHitW = 0
	private var weightHitH = 0
	private var networthHitX = 0
	private var networthHitY = 0
	private var networthHitW = 0
	private var networthHitH = 0
	private var nameHitX = 0
	private var nameHitY = 0
	private var nameHitW = 0
	private var nameHitH = 0
	private var statusHitX = 0
	private var statusHitY = 0
	private var statusHitW = 0
	private var statusHitH = 0
	private var leftHitX = 0
	private var leftHitY = 0
	private var leftHitW = 0
	private var leftHitH = 0
	private var bankHitX = 0
	private var bankHitY = 0
	private var bankHitW = 0
	private var bankHitH = 0
	private var leftStatsFace = false
	private var leftFlipStartMs = 0L
	private var leftFlipTarget = false
	private val playerModel = PlayerModelRenderer()
	private val modelBoxScratch = IntArray(4)
	private var modelLookSet = false
	private var modelLookX = 0
	private var modelLookY = 0
	private var cachedNwLine: Component? = null
	private var cachedWeightLine: Component? = null
	private var cachedStyledName: Component? = null
	private var cachedStyledNameKey = ""
	private var cachedNameWidth = -1
	private var cachedNwValue = ""
	private var cachedWeightValue = ""
	private var cachedNwValueW = 0
	private var cachedWeightValueW = 0
	private var cachedNwTotal = Double.NaN
	private var cachedWeightTotal = Double.NaN
	private var cachedWeightFormatSystem: WeightSystem? = null
	private var cachedStatusKey = ""
	private var cachedStatusDrawn = ""

	fun clearCaches() {
		cachedStyledName = null
		cachedStyledNameKey = ""
		cachedNameWidth = -1
		cachedNwValue = ""
		cachedWeightValue = ""
		cachedNwTotal = Double.NaN
		cachedWeightTotal = Double.NaN
		cachedStatusKey = ""
		cachedNwLine = null
		cachedWeightLine = null
	}

	fun clearStyledNameCache() {
		cachedStyledName = null
		cachedStyledNameKey = ""
		cachedNameWidth = -1
	}

	fun showingStatsFace(): Boolean {
		if (leftFlipStartMs != 0L) {
			val progress = minOf(1F, (System.currentTimeMillis() - leftFlipStartMs) / FLIP_MS.toFloat())
			val angle = HomeUi.easeInOutCubic(progress) * PI.toFloat()
			return if (cos(angle.toDouble()) < 0.0) leftFlipTarget else leftStatsFace
		}
		return leftStatsFace
	}

	fun hitName(mouseX: Double, mouseY: Double): Boolean =
		!showingStatsFace()
			&& mouseX >= nameHitX && mouseX < nameHitX + nameHitW
			&& mouseY >= nameHitY && mouseY < nameHitY + nameHitH
			&& nameHitW > 0

	fun setModelLook(x: Int, y: Int) {
		modelLookSet = true
		modelLookX = x
		modelLookY = y
	}

	fun modelLookX(hoverX: Int): Int = if (modelLookSet) modelLookX else hoverX

	fun modelLookY(hoverY: Int): Int = if (modelLookSet) modelLookY else hoverY

	fun hitStatus(mouseX: Double, mouseY: Double): Boolean =
		!showingStatsFace()
			&& mouseX >= statusHitX && mouseX < statusHitX + statusHitW
			&& mouseY >= statusHitY && mouseY < statusHitY + statusHitH
			&& statusHitW > 0

	fun hitWeight(mouseX: Double, mouseY: Double): Boolean =
		mouseX >= weightHitX && mouseX < weightHitX + weightHitW
			&& mouseY >= weightHitY && mouseY < weightHitY + weightHitH

	fun hitNetworth(mouseX: Double, mouseY: Double): Boolean =
		mouseX >= networthHitX && mouseX < networthHitX + networthHitW
			&& mouseY >= networthHitY && mouseY < networthHitY + networthHitH

	fun hitBank(mouseX: Double, mouseY: Double): Boolean =
		mouseX >= bankHitX && mouseX < bankHitX + bankHitW
			&& mouseY >= bankHitY && mouseY < bankHitY + bankHitH

	fun contains(mouseX: Double, mouseY: Double): Boolean =
		mouseX >= leftHitX && mouseX < leftHitX + leftHitW
			&& mouseY >= leftHitY && mouseY < leftHitY + leftHitH

	fun beginFlip(): Boolean {
		if (!FLIP_ENABLED) {
			return false
		}
		if (leftFlipStartMs != 0L) {
			return true
		}
		leftFlipTarget = !leftStatsFace
		leftFlipStartMs = System.currentTimeMillis()
		return true
	}

	fun draw(
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		layout: HomePage.Layout,
		mouseX: Int,
		mouseY: Int,
		snapshot: ProfileSnapshot?,
		playerStats: PlayerStatsSnapshot,
		playerStatus: PlayerStatus,
		playerRank: JsonObject?,
		armor: Array<ItemStack>,
		activeNw: NetworthBreakdown,
		senither: WeightBreakdown,
		lily: WeightBreakdown,
		weightSystem: WeightSystem,
		loadError: String?,
		openScale: Float,
		openPivotX: Float,
		openPivotY: Float,
		uiScale: Float,
		uiOffsetX: Float,
		uiOffsetY: Float,
		zones: MutableList<HomePage.HoverZone>
	) {
		leftHitX = x
		leftHitY = y
		leftHitW = w
		leftHitH = h

		var flipProgress = 0F
		var animating = leftFlipStartMs != 0L
		if (animating) {
			flipProgress = minOf(1F, (System.currentTimeMillis() - leftFlipStartMs) / FLIP_MS.toFloat())
			if (flipProgress >= 1F) {
				leftStatsFace = leftFlipTarget
				leftFlipStartMs = 0L
				animating = false
				flipProgress = 0F
			}
		}
		val eased = if (animating) HomeUi.easeInOutCubic(flipProgress) else 0F
		val angle = eased * PI.toFloat()
		val showStats = if (animating) {
			if (cos(angle.toDouble()) < 0.0) leftFlipTarget else leftStatsFace
		} else {
			leftStatsFace
		}
		var scaleX = 1F
		var scaleY = 1F
		var slideDx = 0F
		if (animating) {
			slideDx = PvDraw.slideOffset(eased, w)
		}

		val cxFlip = x + w / 2F
		val cyFlip = y + h / 2F
		PvDraw.innerPanel(g, x, y, w, h)
		g.enableScissor(x, y, x + w, y + h)
		g.pose().pushMatrix()
		g.pose().translate(slideDx, 0F)

		var modelBox: IntArray? = null
		if (showStats) {
			networthHitW = 0
			weightHitW = 0
			nameHitW = 0
			statusHitW = 0
			drawStatsFace(g, font, x, y, w, h, playerStats, zones)
		} else if (snapshot != null) {
			val faceBox = drawProfileFace(
				g, font, x, y, w, h, layout, mouseX, mouseY,
				snapshot, playerStatus, playerRank, activeNw, senither, lily, weightSystem, loadError, zones
			)
			if (!animating) modelBox = faceBox
		}

		g.pose().popMatrix()
		g.disableScissor()

		val uuid = snapshot?.playerUuid
		if (modelBox != null && uuid != null) {
			playerModel.draw(
				g,
				uuid,
				snapshot.playerName,
				modelBox[0],
				modelBox[1],
				modelBox[2],
				modelBox[3],
				modelLookX(mouseX),
				modelLookY(mouseY),
				armor.getOrElse(3) { ItemStack.EMPTY },
				armor.getOrElse(2) { ItemStack.EMPTY },
				armor.getOrElse(1) { ItemStack.EMPTY },
				armor.getOrElse(0) { ItemStack.EMPTY },
				scaleX,
				scaleY,
				cxFlip,
				cyFlip,
				openScale,
				openPivotX,
				openPivotY,
				uiScale,
				uiOffsetX,
				uiOffsetY
			)
		}
		modelLookSet = false
	}

	private fun drawProfileFace(
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		layout: HomePage.Layout,
		mouseX: Int,
		mouseY: Int,
		snapshot: ProfileSnapshot,
		playerStatus: PlayerStatus,
		playerRank: JsonObject?,
		activeNw: NetworthBreakdown,
		senither: WeightBreakdown,
		lily: WeightBreakdown,
		weightSystem: WeightSystem,
		loadError: String?,
		zones: MutableList<HomePage.HoverZone>
	): IntArray? {
		ensureStaticLabels(font)
		var ty = y + PAD
		val nwTotal = activeNw.total
		val nwLoading = activeNw.note?.lowercase(Locale.ROOT)?.contains("loading") == true
		if (nwTotal.toRawBits() != cachedNwTotal.toRawBits() || cachedNwValue.isEmpty()) {
			cachedNwTotal = nwTotal
			var nwValue = when {
				nwTotal > 0 -> FormatUtil.shortCoins(nwTotal)
				nwLoading -> "..."
				else -> "-"
			}
			if (!loadError.isNullOrBlank() && nwTotal <= 0 && !nwLoading) {
				nwValue = "-"
			}
			cachedNwValue = nwValue
			cachedNwValueW = PvDraw.widthBold(font, nwValue)
			cachedNwLine = labelLine(NW_LABEL, nwValue)
		} else if (cachedNwLine == null) {
			cachedNwLine = labelLine(NW_LABEL, cachedNwValue)
		}

		val weightTotal = if (weightSystem == WeightSystem.SENITHER) senither.total() else lily.total()
		if (cachedWeightFormatSystem != weightSystem
			|| weightTotal.toRawBits() != cachedWeightTotal.toRawBits()
			|| cachedWeightValue.isEmpty()
		) {
			cachedWeightFormatSystem = weightSystem
			cachedWeightTotal = weightTotal
			var weightValue = FormatUtil.weight(weightTotal)
			if (snapshot.weightText == "..." && loadError.isNullOrBlank()) {
				weightValue = "..."
			}
			if (!loadError.isNullOrBlank() && senither.total() <= 0 && lily.total() <= 0) {
				weightValue = "-"
			}
			cachedWeightValue = weightValue
			cachedWeightValueW = PvDraw.widthBold(font, weightValue)
			cachedWeightLine = labelLine(WEIGHT_LABEL, weightValue)
		} else if (cachedWeightLine == null) {
			cachedWeightLine = labelLine(WEIGHT_LABEL, cachedWeightValue)
		}

		val nwLineW = NW_LABEL_W + cachedNwValueW
		val nwX = x + (w - nwLineW) / 2
		g.text(font, cachedNwLine ?: labelLine(NW_LABEL, cachedNwValue), nwX, ty, PvDraw.COLOR_WHITE, false)
		networthHitX = nwX
		networthHitY = ty
		networthHitW = nwLineW
		networthHitH = font.lineHeight

		ty += layout.line
		val weightLineW = WEIGHT_LABEL_W + cachedWeightValueW
		val weightX = x + (w - weightLineW) / 2
		g.text(font, cachedWeightLine ?: labelLine(WEIGHT_LABEL, cachedWeightValue), weightX, ty, PvDraw.COLOR_WHITE, false)
		weightHitX = weightX
		weightHitY = ty
		weightHitW = weightLineW
		weightHitH = font.lineHeight

		ty += layout.line
		drawSkillAverage(g, font, x, ty, w, snapshot, zones)

		ty += layout.line
		val bankValue = FormatUtil.shortCoins(snapshot.bankCoins) + " / " + FormatUtil.shortCoins(snapshot.purseCoins)
		val bankLine = labelLine(BANK_LABEL, bankValue)
		val bankW = BANK_LABEL_W + font.width(PvDraw.styled(bankValue, PvDraw.COLOR_GOLD, true))
		val bankX = x + (w - bankW) / 2
		g.text(font, bankLine, bankX, ty, PvDraw.COLOR_WHITE, false)
		bankHitX = bankX
		bankHitY = ty
		bankHitW = bankW
		bankHitH = font.lineHeight

		val cx = x + w / 2
		val boxW = minOf(w - PAD * 2, maxOf(64, w - 20))
		val boxX = cx - boxW / 2
		val boxTop = y + layout.boxTop
		val boxH = layout.boxH

		val nameComp = styledPlayerName(snapshot, playerRank)
		var nameW = cachedNameWidth
		if (nameW < 0) {
			nameW = font.width(nameComp)
			cachedNameWidth = nameW
		}
		g.text(font, nameComp, cx - nameW / 2, y + layout.nameY, PvDraw.COLOR_WHITE, false)
		nameHitX = cx - nameW / 2
		nameHitY = y + layout.nameY
		nameHitW = nameW
		nameHitH = font.lineHeight

		PvDraw.roundedFill(g, boxX, boxTop, boxW, boxH, 3, 0xFF15151E.toInt())
		PvDraw.roundedRing(g, boxX, boxTop, boxW, boxH, 3, PvDraw.COLOR_BORDER, PvDraw.C_ALL)

		var modelBox: IntArray? = null
		if (snapshot.playerUuid != null) {
			modelBoxScratch[0] = boxX + 2
			modelBoxScratch[1] = boxTop + 2
			modelBoxScratch[2] = boxX + boxW - 2
			modelBoxScratch[3] = boxTop + boxH - 2
			modelBox = modelBoxScratch
		} else {
			PvDraw.textCentered(
				g, font,
				Component.translatable("fishmod.pv.home.player").string,
				cx, boxTop + boxH / 2 - font.lineHeight / 2,
				PvDraw.COLOR_MUTED
			)
		}

		val footerY = y + layout.footerY
		val statusLabel = playerStatus.buttonLabel()
		val statusColor = playerStatus.buttonColor(
			PvDraw.COLOR_ACCENT, HomeTooltips.ENABLED_GREEN, HomeTooltips.OFFLINE_RED, PvDraw.COLOR_MUTED
		)
		val statusW = statusBoxWidth(boxW)
		val statusH = layout.statusH
		val statusX = cx - statusW / 2
		val statusY = footerY
		statusHitX = statusX
		statusHitY = statusY
		statusHitW = statusW
		statusHitH = statusH
		val statusHover = mouseX >= statusX && mouseX < statusX + statusW
			&& mouseY >= statusY && mouseY < statusY + statusH
		PvDraw.fill(g, statusX, statusY, statusW, statusH, PvDraw.CELL)
		g.outline(statusX, statusY, statusW, statusH, if (statusHover) PvDraw.COLOR_ACCENT else PvDraw.COLOR_BORDER)
		val statusKey = "$statusLabel|$statusW"
		if (statusKey != cachedStatusKey) {
			cachedStatusKey = statusKey
			cachedStatusDrawn = statusText({ font.width(it) }, statusLabel, statusW)
		}
		PvDraw.textCentered(
			g, font, cachedStatusDrawn, statusX + statusW / 2,
			statusY + (statusH - font.lineHeight) / 2, statusColor
		)
		return modelBox
	}

	private fun drawStatsFace(
		g: GuiGraphicsExtractor,
		font: Font,
		x: Int,
		y: Int,
		w: Int,
		h: Int,
		playerStats: PlayerStatsSnapshot,
		zones: MutableList<HomePage.HoverZone>
	) {
		val entries = playerStats.entries
		if (entries.isEmpty()) {
			PvDraw.textCentered(g, font, "No stats", x + w / 2, y + h / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED)
			return
		}
		val cols = 2
		val colGap = 8
		val colW = (w - PAD * 2 - colGap) / cols
		var symbolSlot = 0
		for (entry in entries) {
			val style = SkyBlockStats.stat(entry.id)
			val symSlotW = if (style.boldSymbol) PvDraw.widthBold(font, style.symbol) else font.width(style.symbol)
			symbolSlot = maxOf(symbolSlot, symSlotW)
		}
		symbolSlot = maxOf(symbolSlot, font.width("⚔"))
		val labelGap = 3
		val rows = (entries.size + cols - 1) / cols
		var rowH = font.lineHeight + 1
		var contentH = rows * rowH
		val availH = h - PAD * 2
		if (rows > 0 && contentH < availH) {
			rowH = maxOf(rowH, availH / rows)
			contentH = rows * rowH
		}
		val ty = y + maxOf(PAD, (h - contentH) / 2)
		for ((i, entry) in entries.withIndex()) {
			val col = i % cols
			val row = i / cols
			val bx = x + PAD + col * (colW + colGap)
			val by = ty + row * rowH
			val style = SkyBlockStats.stat(entry.id)
			val symbol = style.symbol
			if (style.boldSymbol) {
				val symW = PvDraw.widthBold(font, symbol)
				PvDraw.textBold(g, font, symbol, bx + (symbolSlot - symW) / 2, by, style.color)
			} else {
				val symW = font.width(symbol)
				PvDraw.text(g, font, symbol, bx + (symbolSlot - symW) / 2, by, style.color)
			}
			PvDraw.text(g, font, entry.label, bx + symbolSlot + labelGap, by, style.color)
			val value = if (entry.present()) formatStat(entry.value.asDouble) else "-"
			PvDraw.textRight(g, font, value, bx + colW, by, PvDraw.COLOR_WHITE)
			val tipValue = if (entry.present()) formatStatFull(entry.value.asDouble) else "-"
			val tip = ArrayList(SkyBlockStats.tooltipLines(entry.id, tipValue))
			tip.add(PvTooltip.Line.blank())
			if (!entry.present()) {
				tip.add(PvTooltip.Line.meta("Not in the API"))
			} else {
				tip.add(PvTooltip.Line.meta("Excludes held item, set bonuses,"))
				tip.add(PvTooltip.Line.meta("temporary buffs and location bonuses."))
			}
			zones.add(HomePage.HoverZone(bx, by, colW, maxOf(font.lineHeight, rowH - 1), tip))
		}
	}

	private fun styledPlayerName(snapshot: ProfileSnapshot, playerRank: JsonObject?): Component {
		val name = snapshot.playerName
		val uuid = snapshot.playerUuid
		val rankKey = if (playerRank == null) "" else Integer.toHexString(System.identityHashCode(playerRank))
		val badgeCount = if (uuid == null) 0 else BadgeManager.badgesFor(uuid.toString().replace("-", "")).size
		val key = (uuid?.toString() ?: "") + "|" + name + "|" + rankKey +
			"|" + snapshot.skyBlockLevel +
			"|" + badgeCount +
			"|" + (if (uuid == null) "" else tabEmblem(uuid).toString())
		val cached = cachedStyledName
		if (key == cachedStyledNameKey && cached != null) {
			return cached
		}
		var namePart: Component = Component.literal(name)
		val styled = Component.empty()
		val nameAlreadyHasBracket = namePart.string.startsWith("[")
		if (!nameAlreadyHasBracket) {
			val level = snapshot.skyBlockLevel
			styled.append(PvTooltip.Span.of("[", 0xFF555555.toInt()).toComponent())
			styled.append(PvTooltip.Span.of(level.toString(), SkyBlockLevelColors.colorFor(level)).toComponent())
			styled.append(PvTooltip.Span.of("] ", 0xFF555555.toInt()).toComponent())
		}
		if (playerRank != null && !nameAlreadyHasBracket) {
			namePart = PvTooltip.Span.of(name, HypixelRanks.nameColor(playerRank)).toComponent()
		}
		if (uuid != null) {
			for (badge in BadgeManager.badgesFor(uuid.toString().replace("-", ""))) {
				styled.append(badge.component(" "))
			}
		}
		styled.append(namePart)
		val emblem = if (uuid == null) null else tabEmblem(uuid)
		if (emblem != null) {
			styled.append(Component.literal(" "))
			styled.append(emblem)
		}
		cachedStyledNameKey = key
		cachedStyledName = styled
		cachedNameWidth = -1
		return styled
	}

	companion object {
		private const val PAD = 6
		private const val FLIP_MS = 480
		private const val FLIP_ENABLED = false

		private var NW_LABEL = ""
		private var WEIGHT_LABEL = ""
		private var PURSE_LABEL = ""
		private var BANK_LABEL = ""
		private var SKILL_AVG_LABEL = ""
		private var labelsLoaded = false
		private var NW_LABEL_W = -1
		private var SKILL_AVG_LABEL_W = -1
		private var WEIGHT_LABEL_W = -1
		private var PURSE_LABEL_W = -1
		private var BANK_LABEL_W = -1

		@JvmStatic
		fun statusBoxWidth(boxW: Int): Int = minOf(boxW, maxOf(48, boxW - 8))

		// Full label stays in the hover tooltip; the box itself never grows.
		@JvmStatic
		fun statusText(width: (String) -> Int, label: String?, statusW: Int): String =
			trimToWidth(width, label, maxOf(8, statusW - 8))

		private fun labelLine(label: String, value: String): Component =
			Component.empty()
				.append(PvDraw.styled(label, 0xFF55FF55.toInt(), false))
				.append(PvDraw.styled(value, PvDraw.COLOR_GOLD, true))

		private fun drawSkillAverage(
			g: GuiGraphicsExtractor, font: Font, x: Int, ty: Int, w: Int, snapshot: ProfileSnapshot,
			zones: MutableList<HomePage.HoverZone>
		) {
			val loaded = snapshot.skillsLoaded()
			val average = snapshot.skillAverage(true)
			val value = if (loaded) FormatUtil.twoDecimals(average) else "..."
			val line = labelLine(SKILL_AVG_LABEL, value)
			val lineW = SKILL_AVG_LABEL_W + PvDraw.widthBold(font, value)
			val lineX = x + (w - lineW) / 2
			g.text(font, line, lineX, ty, PvDraw.COLOR_WHITE, false)
			if (!loaded) {
				return
			}
			val tip = ArrayList<PvTooltip.Line>()
			tip.add(PvTooltip.Line.title("Skill Average", 0xFF55FF55.toInt()))
			tip.add(PvTooltip.Line.divider())
			tip.add(PvTooltip.Line.row("Skill Avg", PvDraw.COLOR_MUTED, FormatUtil.twoDecimals(average), PvDraw.COLOR_GOLD))
			tip.add(PvTooltip.Line.row(
				"Overflow Skill Avg", PvDraw.COLOR_MUTED,
				FormatUtil.twoDecimals(snapshot.overflowSkillAverage()), PvDraw.COLOR_GOLD
			))
			tip.add(PvTooltip.Line.blank())
			for (skill in snapshot.skills) {
				val overflow = floor(skill.overflowLevel.toDouble()).toInt()
				val level = if (skill.maxed && overflow > skill.level) "${skill.level} ($overflow)" else skill.level.toString()
				tip.add(PvTooltip.Line.row(
					skill.name, PvDraw.COLOR_MUTED, level,
					if (skill.maxed) PvDraw.COLOR_GOLD else PvDraw.COLOR_TEXT
				))
			}
			zones.add(HomePage.HoverZone(lineX, ty, lineW, font.lineHeight, tip))
		}

		private fun tabEmblem(uuid: UUID): Component? {
			val connection = Minecraft.getInstance().connection ?: return null
			for (info in connection.onlinePlayers) {
				if (uuid != info.profile.id()) {
					continue
				}
				val display = info.tabListDisplayName ?: return null
				val plain = display.string.trimEnd()
				val end = plain.length
				var start = end
				while (start > 0) {
					val c = plain[start - 1]
					if (c.isLetterOrDigit() || c.isWhitespace() || c == '_' || c == ']' || c == ')') {
						break
					}
					start--
				}
				if (start == end) {
					return null
				}
				val symbol = plain.substring(start, end)
				var color = 0xFFFFFFFF.toInt()
				display.visit(FormattedText.StyledContentConsumer<Unit> { style, text ->
					val textColor = style?.color
					if (text != null && text.isNotBlank() && symbol.indexOf(text.trim()[0]) >= 0 && textColor != null) {
						color = 0xFF000000.toInt() or textColor.value
					}
					Optional.empty()
				}, Style.EMPTY)
				return Component.literal(symbol).withColor(color)
			}
			return null
		}

		private fun ensureStaticLabels(font: Font) {
			if (!labelsLoaded) {
				NW_LABEL = Component.translatable("fishmod.pv.home.networth_label").string
				WEIGHT_LABEL = Component.translatable("fishmod.pv.home.weight_label").string
				PURSE_LABEL = Component.translatable("fishmod.pv.home.purse_label").string
				BANK_LABEL = Component.translatable("fishmod.pv.home.bank_label").string
				SKILL_AVG_LABEL = Component.translatable("fishmod.pv.home.skill_avg_label").string
				labelsLoaded = true
			}
			if (NW_LABEL_W < 0) {
				NW_LABEL_W = font.width(NW_LABEL)
				SKILL_AVG_LABEL_W = font.width(SKILL_AVG_LABEL)
				WEIGHT_LABEL_W = font.width(WEIGHT_LABEL)
				PURSE_LABEL_W = font.width(PURSE_LABEL)
				BANK_LABEL_W = font.width(BANK_LABEL)
			}
		}

		private fun trimToWidth(width: (String) -> Int, text: String?, maxW: Int): String {
			if (text == null) {
				return ""
			}
			if (width(text) <= maxW) {
				return text
			}
			val ellipsis = "..."
			val ellipsisW = width(ellipsis)
			if (maxW <= ellipsisW) {
				return ellipsis
			}
			var lo = 0
			var hi = text.length
			while (lo < hi) {
				val mid = (lo + hi + 1) ushr 1
				if (width(text.substring(0, mid)) + ellipsisW <= maxW) {
					lo = mid
				} else {
					hi = mid - 1
				}
			}
			return text.substring(0, lo).trimEnd() + ellipsis
		}

		private fun formatStat(value: Double): String {
			val abs = abs(value)
			if (abs >= 1_000_000_000L) {
				return formatCompact(value / 1_000_000_000L, "b")
			}
			if (abs >= 1_000_000L) {
				return formatCompact(value / 1_000_000L, "m")
			}
			if (abs >= 1_000L) {
				return formatCompact(value / 1_000L, "k")
			}
			return Math.round(value).toString()
		}

		private fun formatStatFull(value: Double): String {
			val rounded = Math.round(value * 10.0) / 10.0
			if (abs(rounded - Math.rint(rounded)) < 0.05) {
				return FormatUtil.commas(Math.round(rounded))
			}
			val whole = floor(abs(rounded)).toLong()
			val dec = String.format(Locale.US, "%.1f", abs(rounded) - whole).substring(1)
			val sign = if (rounded < 0) "-" else ""
			return sign + FormatUtil.commas(whole) + dec
		}

		private fun formatCompact(scaled: Double, suffix: String): String {
			val one = Math.round(scaled * 10.0) / 10.0
			if (abs(one - Math.rint(one)) < 0.05) {
				return Math.rint(one).toLong().toString() + suffix
			}
			return String.format(Locale.US, "%.1f%s", one, suffix)
		}
	}
}
