/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.garden

import fishmod.pv.data.FormatUtil
import fishmod.pv.data.GardenCosts
import fishmod.pv.data.GardenData
import fishmod.pv.data.GardenSnapshot
import fishmod.pv.data.Leveling
import fishmod.pv.gui.PvDraw
import fishmod.pv.gui.PvTooltip
import fishmod.pv.gui.inventories.SkyBlockIconRenderer
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import java.util.Locale
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

class GardenUi {
	@JvmField
	val zones: MutableList<HoverZone> = ArrayList()

	fun clearZones() {
		zones.clear()
	}

	fun drawHover(g: GuiGraphicsExtractor, font: Font, mx: Int, my: Int, screenW: Int, screenH: Int) {
		val zone = zones.firstOrNull { mx >= it.x && mx < it.x + it.w && my >= it.y && my < it.y + it.h } ?: return
		PvTooltip.drawStyled(g, font, zone.lines, mx, my, screenW, screenH)
	}

	fun drawBar(
		g: GuiGraphicsExtractor, font: Font, label: String, value: String?, fill: Float, maxed: Boolean,
		color: Int, hover: String?, x: Int, y: Int, w: Int, mx: Int, my: Int
	): Int {
		val shown = fitValue(font, label, value ?: "", w)
		PvDraw.labeledBar(
			g, font, trim(font, label, maxOf(24, w - font.width(shown) - 8)),
			shown, fill, x, y, w, color, maxed
		)
		val bottom = y + font.lineHeight + BAR_LABEL_GAP + PvDraw.BAR_HEIGHT
		if (!hover.isNullOrBlank()) {
			zones.add(HoverZone(x, y, w, bottom - y, Leveling.hoverTextLines(hover)))
		}
		return bottom
	}

	fun cropMilestoneTip(snapshot: GardenSnapshot, crop: GardenSnapshot.CropRow): List<PvTooltip.Line> {
		val tip = ArrayList<PvTooltip.Line>()
		tip.add(PvTooltip.Line.of(crop.name, PvDraw.COLOR_TEXT))
		tip.add(PvTooltip.Line.of("Milestone " + crop.milestone + (if (crop.milestoneMaxed) " (MAX)" else ""), PvDraw.COLOR_ACCENT))
		val ms = GardenData.cropMilestone(crop.id, crop.collected)
		if (crop.milestoneMaxed) {
			tip.add(PvTooltip.Line.of("Overflow: " + FormatUtil.commas(ms.intoLevel), PvDraw.COLOR_MUTED))
			tip.add(PvTooltip.Line.of("Total: " + FormatUtil.commas(crop.collected), PvDraw.COLOR_MUTED))
		} else {
			tip.add(PvTooltip.Line.of(FormatUtil.commas(ms.intoLevel) + " / " + FormatUtil.commas(ms.needForNext), PvDraw.COLOR_MUTED))
		}
		tip.add(PvTooltip.Line.of("Upgrade: +" + crop.upgradeLevel, PvDraw.COLOR_MUTED))
		val copper = crop.upgradeCopper()
		tip.add(PvTooltip.Line.of(
			"Copper: " + FormatUtil.commas(copper.spent) + " / " + FormatUtil.commas(copper.total) +
				" (" + FormatUtil.percent(copper.fraction()) + ")",
			if (copper.maxed()) COMPLETED_C else COPPER
		))
		if (snapshot.hasUniqueGold(crop.id)) {
			tip.add(PvTooltip.Line.of("Unique gold", GOLD))
		}
		return tip
	}

	@JvmRecord
	data class HoverZone(val x: Int, val y: Int, val w: Int, val h: Int, val lines: List<PvTooltip.Line>)

	companion object {
		const val PAD = 6
		const val GAP = 6
		const val BAR_LABEL_GAP = 2
		const val BAR_AFTER = 4
		const val STAT_ROW = 12
		const val ICON = 16
		const val SLOT = 20
		const val SLOT_GAP = 4
		const val ITEM_SLOT_BG = 0x1CFFFFFF
		const val ITEM_SLOT_BORDER = 0x00FFFFFF
		const val ITEM_SLOT_HOVER = 0xFF4A4A5A.toInt()
		const val VISITOR_CELL_W = 22
		const val VISITOR_CELL_H = 22
		const val CHIP_CELL = 22
		const val CHIP_GAP = 4
		const val GH_CELL_W = 24
		const val GH_CELL_H = 24
		const val CELL_BORDER = 0xFF3A3A48.toInt()
		const val CREDIT_C = 0xFF4A4A58.toInt()
		const val BAR_CROP = 0xFF6BAA3D.toInt()
		const val BAR_GARDEN = 0xFF4CAF50.toInt()
		const val BAR_FARM = 0xFF8BC34A.toInt()
		const val BAR_VISITOR = 0xFF9C6B4A.toInt()
		const val BAR_COMPOST = 0xFF7CB342.toInt()
		const val BRONZE = 0xFFCD7F32.toInt()
		const val SILVER = 0xFFC0C0C0.toInt()
		const val GOLD = 0xFFFFD700.toInt()
		const val PLATINUM = 0xFF55FFFF.toInt()
		const val DIAMOND = 0xFFAAFFFF.toInt()
		const val MEDAL_ORB_EMPTY = 0x30FFFFFF
		const val GHOST = 0xFF9A9AAC.toInt()
		const val COPPER = 0xFFE07A3D.toInt()
		const val VISITS_C = 0xFFE8E8F0.toInt()
		const val COMPLETED_C = 0xFF55FF55.toInt()
		const val REJECTED_C = 0xFFFF5555.toInt()
		const val CREDIT_SCALE = 0.75F
		const val FLIP_MS = 480
		const val PANEL_HOVER = 0x0AFFFFFF
		const val ORB = 7
		const val ORB_GAP = 3

		private val COST_ICONS = HashMap<String, ItemStack>()

		@JvmStatic
		fun barRowH(font: Font): Int = font.lineHeight + BAR_LABEL_GAP + PvDraw.BAR_HEIGHT + BAR_AFTER

		@JvmStatic
		fun statLine(g: GuiGraphicsExtractor, font: Font, label: String?, value: String?, x: Int, y: Int, w: Int, valueColor: Int): Int {
			drawPair(g, font, label, value, x, y, w, PvDraw.COLOR_MUTED, valueColor)
			return y + STAT_ROW
		}

		@JvmStatic
		fun drawPair(
			g: GuiGraphicsExtractor, font: Font, left: String?, right: String?, x: Int, y: Int, w: Int, leftColor: Int, rightColor: Int
		) {
			val r = right ?: ""
			val leftMax = maxOf(8, w - font.width(r) - 6)
			PvDraw.text(g, font, trim(font, left, leftMax), x, y, leftColor)
			PvDraw.textRight(g, font, r, x + w, y, rightColor)
		}

		@JvmStatic
		fun drawCellBorder(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
			g.outline(x, y, w, h, CELL_BORDER)
		}

		@JvmStatic
		fun drawIcon(g: GuiGraphicsExtractor, id: String?, x: Int, y: Int, size: Int, packModel: String?) {
			val icon: ItemStack = if (id.isNullOrBlank()) ItemStack.EMPTY else SkyBlockItemFactory.iconStack(id)
			if (!id.isNullOrBlank() && (!packModel.isNullOrBlank() || SkyBlockIconRenderer.hasKnownIcon(id))) {
				SkyBlockIconRenderer.draw(g, icon, id, packModel, x, y, size)
				return
			}
			val vanilla = vanillaFallback(id)
			if (!vanilla.isEmpty) {
				drawStack(g, vanilla, x, y, size)
				return
			}
			if (isTexturedHead(icon) || (!icon.isEmpty && !icon.`is`(Items.PAPER) && !icon.`is`(Items.PLAYER_HEAD))) {
				SkyBlockIconRenderer.draw(g, icon, id, x, y, size)
				return
			}
			val upper = id?.uppercase(Locale.ROOT) ?: ""
			if (upper.endsWith("_NPC") || upper.contains("MAYOR") || upper.endsWith("_MONSTER")) {
				drawStack(g, ItemStack(Items.VILLAGER_SPAWN_EGG), x, y, size)
				return
			}
			drawStack(g, ItemStack(Items.PAPER), x, y, size)
		}

		@JvmStatic
		fun isTexturedHead(icon: ItemStack?): Boolean {
			if (icon == null || icon.isEmpty || !icon.`is`(Items.PLAYER_HEAD)) {
				return false
			}
			val profile = icon.get(DataComponents.PROFILE) ?: return false
			try {
				val props = profile.partialProfile().properties().get("textures")
				if (props == null || props.isEmpty()) {
					return false
				}
				return props.any { it != null && !it.value().isNullOrBlank() }
			} catch (_: Exception) {
			}
			return false
		}

		@JvmStatic
		fun drawStack(g: GuiGraphicsExtractor, icon: ItemStack, x: Int, y: Int, size: Int) {
			if (size == ICON) {
				g.item(icon, x, y)
				return
			}
			val scale = size / ICON.toFloat()
			g.pose().pushMatrix()
			g.pose().translate(x.toFloat(), y.toFloat())
			g.pose().scale(scale, scale)
			g.item(icon, 0, 0)
			g.pose().popMatrix()
		}

		@JvmStatic
		fun vanillaFallback(id: String?): ItemStack {
			if (id.isNullOrBlank()) {
				return ItemStack.EMPTY
			}
			return when (id.uppercase(Locale.ROOT)) {
				"CLOCK" -> ItemStack(Items.CLOCK)
				"GOLDEN_HOE" -> ItemStack(Items.GOLDEN_HOE)
				"COAL_BLOCK" -> ItemStack(Items.COAL_BLOCK)
				"GOLD_INGOT" -> ItemStack(Items.GOLD_INGOT)
				"COMPOSTER" -> ItemStack(Items.COMPOSTER)
				"WHEAT" -> ItemStack(Items.WHEAT)
				"CARROT_ITEM", "CARROT" -> ItemStack(Items.CARROT)
				"POTATO_ITEM", "POTATO" -> ItemStack(Items.POTATO)
				"PUMPKIN" -> ItemStack(Items.PUMPKIN)
				"SUGAR_CANE" -> ItemStack(Items.SUGAR_CANE)
				"MELON" -> ItemStack(Items.MELON_SLICE)
				"CACTUS" -> ItemStack(Items.CACTUS)
				"INK_SACK:3", "INK_SACK-3", "COCOA_BEANS" -> ItemStack(Items.COCOA_BEANS)
				"RED_MUSHROOM", "MUSHROOM" -> ItemStack(Items.RED_MUSHROOM)
				"NETHER_STALK", "NETHER_WART" -> ItemStack(Items.NETHER_WART)
				"DOUBLE_PLANT", "SUNFLOWER" -> ItemStack(Items.SUNFLOWER)
				"MOONFLOWER" -> ItemStack(Items.BLUE_ORCHID)
				"WILD_ROSE" -> ItemStack(Items.ROSE_BUSH)
				"SEEDS", "WHEAT_SEEDS" -> ItemStack(Items.WHEAT_SEEDS)
				else -> ItemStack.EMPTY
			}
		}

		@JvmStatic
		fun fitValue(font: Font, label: String, value: String?, w: Int): String {
			if (value.isNullOrBlank()) {
				return ""
			}
			val maxValue = w - font.width(label) - 10
			if (maxValue < 12) {
				return ""
			}
			return if (font.width(value) <= maxValue) value else trim(font, value, maxValue)
		}

		@JvmStatic
		fun trim(font: Font, text: String?, maxW: Int): String {
			if (text == null) {
				return ""
			}
			if (font.width(text) <= maxW) {
				return text
			}
			val ell = "..."
			val ellW = font.width(ell)
			if (maxW <= ellW) {
				return ""
			}
			val sb = StringBuilder(text)
			while (sb.isNotEmpty() && font.width(sb.toString()) + ellW > maxW) {
				sb.setLength(sb.length - 1)
			}
			return sb.toString() + ell
		}

		@JvmStatic
		fun title(raw: String?): String {
			if (raw.isNullOrBlank()) {
				return "?"
			}
			return raw.lowercase(Locale.ROOT).split(Regex("[\\s_]+"))
				.filter { it.isNotEmpty() }
				.joinToString(" ") { it[0].uppercaseChar() + it.substring(1) }
		}

		@JvmStatic
		fun addComposterRemaining(tip: MutableList<PvTooltip.Line>, remaining: GardenCosts.ComposterRemaining) {
			if (remaining.none()) {
				tip.add(PvTooltip.Line.of("Nothing left to buy", COMPLETED_C))
				return
			}
			tip.add(PvTooltip.Line.row("Copper", PvDraw.COLOR_MUTED, FormatUtil.commas(remaining.copper), COPPER))
			if (remaining.crops.isNotEmpty()) {
				tip.add(PvTooltip.Line.of("Crops", PvDraw.COLOR_ACCENT))
				for ((id, amount) in remaining.crops) {
					tip.add(costRow(id, amount, PvDraw.COLOR_TEXT))
				}
			}
			if (remaining.rare.isNotEmpty()) {
				tip.add(PvTooltip.Line.of("Rare crops", PvDraw.COLOR_GOLD))
				for ((id, amount) in remaining.rare) {
					tip.add(costRow(id, amount, PvDraw.COLOR_GOLD))
				}
			}
			if (remaining.crops.isNotEmpty() || remaining.rare.isNotEmpty()) {
				val coins = remaining.coins()
				tip.add(PvTooltip.Line.divider())
				tip.add(PvTooltip.Line.row(
					"Coin cost", PvDraw.COLOR_MUTED,
					if (coins > 0.0) FormatUtil.shortCoins(coins) else "Unknown",
					if (coins > 0.0) PvDraw.COLOR_GOLD else PvDraw.COLOR_MUTED
				))
				if (coins > 0.0 && !remaining.fullyPriced()) {
					tip.add(PvTooltip.Line.meta("Some items have no bazaar price"))
				}
			}
		}

		private fun costRow(itemId: String?, amount: Long, amountColor: Int): PvTooltip.Line {
			val coins = GardenCosts.itemCoins(itemId, amount)
			return PvTooltip.Line.row(
				listOf(PvTooltip.Span.of(costItemName(itemId), PvDraw.COLOR_MUTED)),
				listOf(PvTooltip.Span.of(FormatUtil.commas(amount), amountColor)),
				listOf(PvTooltip.Span.of(
					if (coins > 0.0) FormatUtil.shortCoins(coins) else "-",
					if (coins > 0.0) PvDraw.COLOR_GOLD else PvDraw.COLOR_MUTED
				))
			).withIcon(costIcon(itemId))
		}

		private fun costIcon(itemId: String?): ItemStack {
			val key = itemId?.uppercase(Locale.ROOT) ?: ""
			COST_ICONS[key]?.let { return it }
			val icon: ItemStack = SkyBlockItemFactory.iconStack(key)
			// Don't pin the paper placeholder while NEU is still loading.
			if (!icon.isEmpty && !icon.`is`(Items.PAPER)) {
				COST_ICONS[key] = icon
			}
			return icon
		}

		@JvmStatic
		fun costItemName(id: String?): String {
			if (id.isNullOrBlank()) {
				return "?"
			}
			return when (id.uppercase(Locale.ROOT)) {
				"ENCHANTED_HUGE_MUSHROOM_1" -> "Enchanted Brown Mushroom Block"
				"ENCHANTED_HUGE_MUSHROOM_2" -> "Enchanted Red Mushroom Block"
				"MUTANT_NETHER_STALK" -> "Mutant Nether Wart"
				else -> title(id)
			}
		}
	}
}
