/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.dungeons

import fishmod.pv.data.DungeonChestHistory
import fishmod.pv.data.DungeonSnapshot
import fishmod.pv.data.FormatUtil
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import java.util.Locale
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.TextColor

/** Dungeons → Chests: recent runs from `dungeons.treasures` with every reward chest. */
class DungeonChestsPage {
	private var history: DungeonChestHistory = DungeonChestHistory.empty()
	private val zones = ArrayList<HoverZone>()
	private var notable: List<Map.Entry<String, Int>> = listOf()
	private val notableRaw = HashMap<String, String>()

	private var listX = 0
	private var listY = 0
	private var listW = 0
	private var listH = 0
	private var scroll = 0
	private var maxScroll = 0

	fun apply(data: DungeonSnapshot?) {
		history = data?.chestHistory ?: DungeonChestHistory.empty()
		scroll = 0
		maxScroll = 0
		val counts = LinkedHashMap<String, Int>()
		notableRaw.clear()
		for (run in history.runs) {
			val opened = run.opened() ?: continue
			for (reward in opened.rewards) {
				if (!DungeonChestHistory.isEssence(reward) && !DungeonChestHistory.isBook(reward)) {
					val name = DungeonChestHistory.prettyReward(reward)
					counts.merge(name, 1) { a, b -> a + b }
					notableRaw.putIfAbsent(name, reward)
				}
			}
		}
		notable = counts.entries.sortedByDescending { it.value }
	}

	fun mouseScrolled(mx: Double, my: Double, scrollY: Double): Boolean {
		if (mx < listX || mx >= listX + listW || my < listY || my >= listY + listH || maxScroll <= 0) {
			return false
		}
		scroll = maxOf(0, minOf(maxScroll, scroll - Math.round(scrollY * ROW).toInt()))
		return true
	}

	fun render(g: GuiGraphicsExtractor, font: Font, x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int) {
		zones.clear()
		val leftW = maxOf(130, w / 3)
		val rightX = x + leftW + GAP
		val rightW = w - leftW - GAP
		drawSummary(g, font, x, y, leftW, h)
		drawRuns(g, font, rightX, y, rightW, h, mx, my)
	}

	fun renderTooltip(g: GuiGraphicsExtractor, font: Font, mouseX: Int, mouseY: Int, screenW: Int, screenH: Int) {
		val zone = zones.firstOrNull { mouseX >= it.x && mouseX < it.x + it.w && mouseY >= it.y && mouseY < it.y + it.h } ?: return
		PvTooltip.drawStyled(g, font, zone.lines, mouseX, mouseY, screenW, screenH)
	}

	private fun drawSummary(g: GuiGraphicsExtractor, font: Font, px: Int, py: Int, pw: Int, ph: Int) {
		PvDraw.innerPanel(g, px, py, pw, ph)
		val x = px + PAD
		val w = pw - PAD * 2
		val bottom = py + ph - PAD
		var cy = py + PAD
		PvDraw.text(g, font, "Chest History", x, cy, HEADER)
		cy += font.lineHeight + 4
		val runs = history.runs
		if (runs.isEmpty()) {
			PvDraw.text(g, font, "No recent runs", x, cy, PvDraw.COLOR_MUTED)
			return
		}
		val openedByType = LinkedHashMap<String, Int>()
		var opened = 0
		var rerolls = 0
		var claimable = 0
		val now = System.currentTimeMillis()
		for (run in runs) {
			val chest = run.opened()
			if (chest != null) {
				opened++
				openedByType.merge(chest.type.lowercase(Locale.ROOT), 1) { a, b -> a + b }
			} else if (!run.expired(now)) {
				claimable++
			}
			rerolls += run.chests.sumOf { it.rerolls }
		}
		cy = stat(g, font, "Runs", FormatUtil.commas(runs.size.toLong()), x, cy, w, PvDraw.COLOR_TEXT)
		cy = stat(g, font, "Chests Opened", FormatUtil.commas(opened.toLong()), x, cy, w, PvDraw.COLOR_TEXT)
		cy = stat(g, font, "Claimable", FormatUtil.commas(claimable.toLong()), x, cy, w, if (claimable > 0) NORMAL else PvDraw.COLOR_MUTED)
		cy = stat(g, font, "Rerolls", FormatUtil.commas(rerolls.toLong()), x, cy, w, PvDraw.COLOR_TEXT)
		val oldest = runs[runs.size - 1].completedMs
		cy = stat(g, font, "Oldest Run", ago(oldest), x, cy, w, PvDraw.COLOR_MUTED)

		if (openedByType.isNotEmpty() && cy + SEP_GAP + ROW <= bottom) {
			cy = separator(g, px, cy, pw)
			for (type in TYPES) {
				val n = openedByType[type]
				if (n == null || cy + ROW > bottom) {
					continue
				}
				PvDraw.text(g, font, typeLabel(type), x, cy, typeColor(type))
				PvDraw.textRight(g, font, FormatUtil.commas(n.toLong()), x + w, cy, PvDraw.COLOR_TEXT)
				cy += ROW
			}
		}

		if (notable.isNotEmpty() && cy + SEP_GAP + ROW * 2 <= bottom) {
			cy = separator(g, px, cy, pw)
			PvDraw.text(g, font, "Drops", x, cy, PvDraw.COLOR_MUTED)
			cy += ROW
			for ((name, n) in notable) {
				if (cy + ROW > bottom) {
					break
				}
				val count = "${n}x"
				PvDraw.text(g, font, trim(font, name, w - font.width(count) - 6), x, cy, rewardColor(notableRaw[name]))
				PvDraw.textRight(g, font, count, x + w, cy, PvDraw.COLOR_TEXT)
				cy += ROW
			}
		}
	}

	private fun drawRuns(g: GuiGraphicsExtractor, font: Font, px: Int, py: Int, pw: Int, ph: Int, mx: Int, my: Int) {
		PvDraw.innerPanel(g, px, py, pw, ph)
		val x = px + PAD
		val w = pw - PAD * 2
		var cy = py + PAD
		val runs = history.runs
		PvDraw.text(g, font, "Recent Runs", x, cy, HEADER)
		PvDraw.textRight(g, font, "Newest first", x + w, cy, PvDraw.COLOR_MUTED)
		cy += font.lineHeight + 4

		listX = x
		listY = cy
		listW = w
		listH = maxOf(ROW, py + ph - PAD - cy)
		maxScroll = maxOf(0, runs.size * ROW - listH)
		scroll = minOf(scroll, maxScroll)
		if (runs.isEmpty()) {
			PvDraw.textCentered(g, font, "No recent runs", px + pw / 2, listY + listH / 2 - font.lineHeight / 2, PvDraw.COLOR_MUTED)
			return
		}

		val now = System.currentTimeMillis()
		val floorW = maxOf(font.width("M7"), runs.maxOf { font.width(it.floor) }) + 6
		val agoW = font.width("00d 00h ago") + 6
		val typeW = font.width("Obsidian") + 6
		g.enableScissor(listX, listY, listX + listW, listY + listH)
		var rowY = listY - scroll
		for (run in runs) {
			if (rowY + ROW < listY) {
				rowY += ROW
				continue
			}
			if (rowY > listY + listH) {
				break
			}
			val hover = mx >= x && mx < x + w && my >= maxOf(rowY, listY) && my < minOf(rowY + ROW, listY + listH)
			if (hover) {
				PvDraw.fill(g, x - 2, rowY - 1, w + 4, ROW, 0x14FFFFFF)
			}
			PvDraw.text(g, font, run.floor, x, rowY, floorColor(run.floor))
			if (run.completedMs > 0L && !run.expired(now)) {
				PvDraw.text(g, font, FormatUtil.prettySpan(run.expiresMs() - now) + " left", x + floorW, rowY, NORMAL)
			} else {
				PvDraw.text(g, font, ago(run.completedMs), x + floorW, rowY, PvDraw.COLOR_MUTED)
			}
			val opened = run.opened()
			val cx = x + floorW + agoW
			if (opened == null) {
				PvDraw.text(g, font, "Not opened", cx, rowY, PvDraw.COLOR_MUTED)
			} else {
				PvDraw.text(g, font, typeLabel(opened.type), cx, rowY, typeColor(opened.type))
				drawLoot(g, font, opened, cx + typeW, rowY, x + w - cx - typeW)
			}
			if (hover) {
				zones.add(HoverZone(x, maxOf(rowY, listY), w, ROW, runTooltip(run)))
			}
			rowY += ROW
		}
		g.disableScissor()
	}

	private class HoverZone(val x: Int, val y: Int, val w: Int, val h: Int, val lines: List<PvTooltip.Line>)

	companion object {
		private const val PAD = 6
		private const val GAP = 6
		private const val ROW = 12
		private const val SEP_GAP = 10
		private const val HEADER = 0xFFFFAA55.toInt()
		private const val MASTER = 0xFFFF5555.toInt()
		private const val NORMAL = 0xFF55FF55.toInt()
		private const val KUUDRA = 0xFFFFAA00.toInt()
		private const val ESSENCE = 0xFFFF55FF.toInt()
		private val TYPES = arrayOf("bedrock", "obsidian", "emerald", "diamond", "gold", "wood", "paid", "free")

		private fun runTooltip(run: DungeonChestHistory.Run): List<PvTooltip.Line> {
			val tip = ArrayList<PvTooltip.Line>()
			tip.add(PvTooltip.Line.title(run.floor + " Run", floorColor(run.floor)))
			tip.add(PvTooltip.Line.meta("Completed " + ago(run.completedMs)))
			val now = System.currentTimeMillis()
			if (run.completedMs > 0L) {
				tip.add(
					if (run.expired(now)) {
						PvTooltip.Line.meta("Expired from Croesus " + ago(run.expiresMs()))
					} else {
						PvTooltip.Line.of("Croesus expiry in " + FormatUtil.prettySpan(run.expiresMs() - now), NORMAL)
					},
				)
			}
			if (run.party.isNotEmpty()) {
				tip.add(PvTooltip.Line.divider())
				for (member in run.party) {
					tip.add(PvTooltip.Line.text(legacySpans(member)))
				}
			}
			if (run.chests.isNotEmpty()) {
				tip.add(PvTooltip.Line.divider())
			}
			for (chest in run.chests) {
				val title = typeLabel(chest.type) + if (chest.paid) " (opened)" else ""
				tip.add(PvTooltip.Line.of(title, typeColor(chest.type)))
				for (reward in chest.rewards) {
					tip.add(rewardLine(DungeonChestHistory.prettyReward(reward), DungeonChestHistory.rewardTier(reward)))
				}
			}
			return tip
		}

		private fun drawLoot(g: GuiGraphicsExtractor, font: Font, chest: DungeonChestHistory.Chest, x: Int, y: Int, maxW: Int) {
			if (chest.rewards.isEmpty()) {
				return
			}
			val items = chest.rewards.filter { !DungeonChestHistory.isEssence(it) }
			if (items.isEmpty()) {
				PvDraw.text(g, font, trim(font, "Essence only", maxW), x, y, ESSENCE)
				return
			}
			var cx = x
			val end = x + maxW
			for ((i, item) in items.withIndex()) {
				val text = DungeonChestHistory.prettyReward(item) + if (i < items.size - 1) "," else ""
				val color = rewardColor(item)
				val remaining = end - cx
				if (isBold(DungeonChestHistory.rewardTier(item))) {
					if (PvDraw.widthBold(font, text) > remaining) {
						PvDraw.text(g, font, trim(font, text, remaining), cx, y, color)
						return
					}
					PvDraw.textBold(g, font, text, cx, y, color)
					cx += PvDraw.widthBold(font, text) + font.width(" ")
					continue
				}
				if (font.width(text) > remaining) {
					PvDraw.text(g, font, trim(font, text, remaining), cx, y, color)
					return
				}
				PvDraw.text(g, font, text, cx, y, color)
				cx += font.width("$text ")
			}
		}

		// Hypixel lore draws ultimate enchants bold (§d§l); regular books are not.
		@JvmStatic
		fun isBold(tier: String?): Boolean = tier == "ULTIMATE"

		@JvmStatic
		fun rewardLine(name: String, tier: String): PvTooltip.Line {
			val color = tierColor(tier)
			return PvTooltip.Line(listOf(
				PvTooltip.Span.of("  ", color),
				if (isBold(tier)) PvTooltip.Span.bold(name, color) else PvTooltip.Span.of(name, color),
			))
		}

		private fun rewardColor(raw: String?): Int = tierColor(DungeonChestHistory.rewardTier(raw))

		private fun tierColor(tier: String): Int = when (tier) {
			"ESSENCE", "ULTIMATE", "MYTHIC" -> ESSENCE
			"BOOK", "RARE" -> 0xFF5555FF.toInt()
			"COMMON" -> 0xFFFFFFFF.toInt()
			"UNCOMMON" -> 0xFF55FF55.toInt()
			"EPIC" -> 0xFFAA00AA.toInt()
			"LEGENDARY" -> 0xFFFFAA00.toInt()
			"DIVINE" -> 0xFF55FFFF.toInt()
			"SPECIAL", "VERY_SPECIAL" -> 0xFFFF5555.toInt()
			"SUPREME" -> 0xFFAA0000.toInt()
			else -> PvDraw.COLOR_TEXT
		}

		private fun floorColor(floor: String?): Int {
			if (DungeonChestHistory.isKuudra(floor)) {
				return KUUDRA
			}
			return if (floor != null && floor.startsWith("M")) MASTER else NORMAL
		}

		private fun typeLabel(type: String?): String {
			if (type.isNullOrBlank()) {
				return "Chest"
			}
			return type.lowercase(Locale.ROOT).replaceFirstChar { it.uppercaseChar() }
		}

		private fun typeColor(type: String?): Int = when (type?.lowercase(Locale.ROOT) ?: "") {
			"wood" -> 0xFFC08A4A.toInt()
			"gold" -> 0xFFFFAA00.toInt()
			"diamond" -> 0xFF55FFFF.toInt()
			"emerald" -> 0xFF55FF55.toInt()
			"obsidian" -> 0xFFAA55FF.toInt()
			"bedrock" -> 0xFFAAAAAA.toInt()
			"paid" -> 0xFFFFAA00.toInt()
			else -> PvDraw.COLOR_TEXT
		}

		private fun stat(g: GuiGraphicsExtractor, font: Font, label: String, value: String, x: Int, y: Int, w: Int, color: Int): Int {
			PvDraw.text(g, font, label, x, y, PvDraw.COLOR_MUTED)
			PvDraw.textRight(g, font, value, x + w, y, color)
			return y + ROW
		}

		private fun separator(g: GuiGraphicsExtractor, panelX: Int, y: Int, panelW: Int): Int {
			val lineInset = PAD + 4
			val lineW = maxOf(0, panelW - lineInset * 2)
			if (lineW > 0) {
				PvDraw.fill(g, panelX + lineInset, y + (SEP_GAP - 1) / 2, lineW, 1, 0x33FFFFFF)
			}
			return y + SEP_GAP
		}

		private fun legacySpans(text: String): List<PvTooltip.Span> {
			val spans = ArrayList<PvTooltip.Span>()
			var color = PvDraw.COLOR_TEXT
			val buf = StringBuilder()
			var i = 0
			while (i < text.length) {
				val c = text[i]
				if (c == '§' && i + 1 < text.length) {
					if (buf.isNotEmpty()) {
						spans.add(PvTooltip.Span.of(buf.toString(), color))
						buf.setLength(0)
					}
					val f = ChatFormatting.getByCode(text[++i])
					val tc = if (f == null) null else TextColor.fromLegacyFormat(f)
					if (tc != null) {
						color = 0xFF000000.toInt() or tc.value
					} else if (f == ChatFormatting.RESET) {
						color = PvDraw.COLOR_TEXT
					}
				} else {
					buf.append(c)
				}
				i++
			}
			if (buf.isNotEmpty()) {
				spans.add(PvTooltip.Span.of(buf.toString(), color))
			}
			return spans
		}

		private fun ago(ms: Long): String {
			if (ms <= 0L) {
				return "-"
			}
			return FormatUtil.prettySpan(maxOf(0L, System.currentTimeMillis() - ms)) + " ago"
		}

		private fun trim(font: Font, text: String?, maxW: Int): String {
			if (text == null || maxW <= 0) {
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
			return "$sb$ellipsis"
		}
	}
}
