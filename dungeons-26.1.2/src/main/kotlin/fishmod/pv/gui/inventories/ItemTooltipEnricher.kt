/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.inventories

import fishmod.pv.data.FormatUtil
import fishmod.pv.data.InventorySnapshot
import fishmod.pv.gui.SkyBlockSymbols
import fishmod.pv.networth.InventoryDecoder
import fishmod.pv.networth.ItemWorth
import fishmod.pv.networth.NbtAttrs
import fishmod.pv.util.LegacyChatFormatting
import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.world.item.ItemStack

/** Tooltip assembly, recombobulator markers, and legacy § lore parsing. */
object ItemTooltipEnricher {
	@JvmStatic
	fun tooltipLines(
		slot: InventorySnapshot.Slot?,
		rendered: ItemStack,
		includeEstimatedValue: Boolean
	): MutableList<Component> {
		val lines = ArrayList<Component>()
		var name: Component? = rendered.get(DataComponents.CUSTOM_NAME)
		if (name == null && slot?.displayName != null) {
			name = legacyText(slot.displayName)
		}
		if (name == null && slot != null) {
			name = Component.literal(ItemTextFormat.prettyId(NeuItemResolver.canonicalId(slot.id)))
		}
		if (name != null) {
			if (slot != null && slot.count > 1) {
				lines.add(name.copy().append(Component.literal(" x" + slot.count).withStyle(ChatFormatting.DARK_GRAY)))
			} else {
				lines.add(name)
			}
		}
		rendered.get(DataComponents.LORE)?.let { lines.addAll(it.lines()) }
		// Re-apply each frame so §k markers and max-enchant rainbow stay live.
		applyRecombobulatorMarkers(lines, slot)
		recolorizeEnchantLines(lines)
		if (includeEstimatedValue) {
			appendEstimatedValueHint(lines, slot)
		}
		appendCreatedDate(lines, slot)
		return lines
	}

	/** Rebuild T7 rainbow enchants from current time (cached lore bakes a static rainbow). */
	@JvmStatic
	fun recolorizeEnchantLines(lines: MutableList<Component>?) {
		if (lines.isNullOrEmpty()) {
			return
		}
		for (i in lines.indices) {
			val line = lines[i]
			val colored = EnchantTooltip.colorize(line, i)
			if (colored !== line) {
				lines[i] = colored
			}
		}
	}

	/** Hypixel `timestamp` on extraAttributes (item craft / obtain time). */
	@JvmStatic
	fun appendCreatedDate(lines: MutableList<Component>?, slot: InventorySnapshot.Slot?) {
		if (slot == null || slot.isEmpty() || lines == null) {
			return
		}
		val ea = slot.extraAttributes ?: return
		var ms = NbtAttrs.longValue(ea, "timestamp", 0L)
		if (ms <= 0L) {
			ms = NbtAttrs.longValue(ea, "TIMESTAMP", 0L)
		}
		if (ms <= 0L) {
			return
		}
		// Some payloads store seconds.
		if (ms < 10_000_000_000L) {
			ms *= 1000L
		}
		val date = FormatUtil.prettyDate(ms)
		if (date.isBlank()) {
			return
		}
		lines.add(
			Component.literal("Created $date")
				.withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY).withItalic(false))
		)
	}

	/** Inserts estimated value + click hint under the rarity line when the item has a price. */
	@JvmStatic
	fun appendEstimatedValueHint(lines: MutableList<Component>?, slot: InventorySnapshot.Slot?) {
		if (slot == null || slot.isEmpty() || lines == null) {
			return
		}
		val id = slot.id ?: return
		val worth = InventoryDecoder.Stack(
			id,
			slot.count,
			slot.extraAttributes ?: CompoundTag(),
			slot.lore ?: listOf(),
			slot.soulbound,
			slot.displayName,
			slot.dyeColor,
			slot.skullValue,
			slot.skullSignature
		)
		val value = ItemWorth.value(worth)
		if (value <= 0) {
			return
		}
		val coins = FormatUtil.shortCoins(Math.round(value).toDouble())
		// Fixed colours so this line does not steal the rarity / name colour.
		val estimated = Component.literal("Estimated value: ")
			.withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(false))
			.append(Component.literal(coins).withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(false)))
		val hint = Component.literal("Click to view value breakdown")
			.withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY).withItalic(true))

		val rarityIndex = findRarityLineIndex(lines)
		val insertAt = if (rarityIndex >= 0) rarityIndex + 1 else lines.size
		lines.add(insertAt, estimated)
		lines.add(insertAt + 1, hint)
	}

	/** Sack contents value in place of the sack item's own price; nothing when no contents are priced. */
	@JvmStatic
	fun appendSackValue(lines: MutableList<Component>?, value: SackValue?) {
		if (lines == null || value == null || !value.known()) {
			return
		}
		val rarityIndex = findRarityLineIndex(lines)
		var insertAt = if (rarityIndex >= 0) rarityIndex + 1 else lines.size
		for (line in sackValueLines(value, false)) {
			lines.add(insertAt++, line)
		}
	}

	@JvmStatic
	fun sackValueLines(value: SackValue?, clickHint: Boolean): MutableList<Component> {
		val out = ArrayList<Component>()
		if (value == null || !value.known()) {
			return out
		}
		out.add(
			Component.literal("Sack value: ")
				.withStyle(Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(false))
				.append(
					Component.literal(FormatUtil.shortCoins(Math.round(value.total).toDouble()))
						.withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withItalic(false))
				)
		)
		if (value.unpriced.isNotEmpty()) {
			val n = value.unpriced.size
			out.add(
				Component.literal(n.toString() + (if (n == 1) " item has" else " items have") + " no price")
					.withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY).withItalic(false))
			)
		}
		if (clickHint) {
			out.add(
				Component.literal("Click to view value breakdown")
					.withStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GRAY).withItalic(true))
			)
		}
		return out
	}

	/** Rarity / item-name colour for recombobulated rarity lines. */
	@JvmStatic
	fun estimatedHeaderColor(lines: List<Component>, slot: InventorySnapshot.Slot?): Int {
		val rarityIndex = findRarityLineIndex(lines)
		if (rarityIndex >= 0) {
			firstNonWhiteColor(lines[rarityIndex])?.let { return it }
			val match = NeuItemResolver.RARITY_WORD.find(lines[rarityIndex].string ?: "")
			if (match != null) {
				return SkyBlockItemFactory.tierArgb(match.groupValues[1])
			}
		}
		if (slot?.id != null) {
			return SkyBlockItemFactory.tierArgb(SkyBlockItemFactory.resolveTier(slot.id))
		}
		return 0xFF55FF55.toInt()
	}

	private fun firstNonWhiteColor(component: Component?): Int? {
		if (component == null) {
			return null
		}
		val color = component.style.color
		if (color != null) {
			val rgb = color.value and 0xFFFFFF
			if (rgb != 0xFFFFFF && rgb != 0xE8E8F0) {
				return 0xFF000000.toInt() or rgb
			}
		}
		for (sibling in component.siblings) {
			firstNonWhiteColor(sibling)?.let { return it }
		}
		return null
	}

	@JvmStatic
	fun findRarityLineIndex(lines: List<Component>): Int {
		for (i in lines.indices.reversed()) {
			val plain: String? = lines[i].string
			if (plain != null && NeuItemResolver.RARITY_WORD.containsMatchIn(plain)) {
				return i
			}
		}
		return -1
	}

	/**
	 * Hypixel API lore often keeps the recomb "a" glyphs but drops `obfuscated:true`.
	 * Rebuild the rarity line from `rarity_upgrades` (or placeholder a's) with real obfuscation.
	 */
	@JvmStatic
	fun applyRecombobulatorMarkers(loreLines: MutableList<Component>?, slot: InventorySnapshot.Slot?) {
		if (loreLines.isNullOrEmpty() || slot == null) {
			return
		}
		val rarityIndex = findRarityLineIndex(loreLines)
		if (rarityIndex < 0) {
			return
		}
		val recombed = NbtAttrs.intValue(slot.extraAttributes, "rarity_upgrades", 0) > 0 ||
			looksLikeRecombPlaceholders(loreLines[rarityIndex].string)
		if (!recombed) {
			return
		}
		loreLines[rarityIndex] = recombobulatedRarityLine(loreLines[rarityIndex])
	}

	private fun isRecombA(c: Char): Boolean = c == 'a' || c == 'A'

	private fun looksLikeRecombPlaceholders(plain: String?): Boolean {
		if (plain == null) {
			return false
		}
		val trimmed = plain.trim()
		return trimmed.length >= 5 &&
			isRecombA(trimmed[0]) &&
			trimmed[1].let { Character.isWhitespace(it) } &&
			isRecombA(trimmed[trimmed.length - 1]) &&
			trimmed[trimmed.length - 2].let { Character.isWhitespace(it) } &&
			NeuItemResolver.RARITY_WORD.containsMatchIn(trimmed)
	}

	private fun recombobulatedRarityLine(line: Component?): Component {
		if (line == null) {
			return Component.empty()
		}
		val plain: String? = line.string
		if (plain.isNullOrBlank()) {
			return line
		}
		val core = stripRecombPlaceholders(plain.trim())
		if (core.isBlank()) {
			return line
		}
		val colorRgb = estimatedHeaderColor(listOf(line), null)
		val body = Style.EMPTY
			.withItalic(false)
			.withBold(true)
			.withColor(colorRgb)
			.withObfuscated(false)
		val marker = body.withObfuscated(true)
		return Component.empty()
			.append(Component.literal("a").withStyle(marker))
			.append(Component.literal(" $core ").withStyle(body))
			.append(Component.literal("a").withStyle(marker))
	}

	/** Removes static leading/trailing recomb "a" placeholders left by API lore. */
	private fun stripRecombPlaceholders(plain: String): String {
		var core = plain
		if (core.length >= 2 && isRecombA(core[0]) && core[1].let { Character.isWhitespace(it) }) {
			core = core.substring(2).trimStart { Character.isWhitespace(it) }
		}
		if (core.length >= 2 && isRecombA(core[core.length - 1]) && core[core.length - 2].let { Character.isWhitespace(it) }) {
			core = core.substring(0, core.length - 1).trimEnd { Character.isWhitespace(it) }
		}
		return core
	}

	@JvmStatic
	fun legacyLine(text: String?): Component = EnchantTooltip.colorize(legacyText(text))

	@JvmStatic
	fun legacyText(text: String?): Component {
		if (text.isNullOrEmpty()) {
			return Component.empty()
		}
		val replaced = SkyBlockSymbols.replace(text)
		val root = Component.empty()
		var style = Style.EMPTY.withItalic(false)
		val buf = StringBuilder()
		var i = 0
		while (i < replaced.length) {
			val c = replaced[i]
			if ((c == '§' || c == '&') && i + 1 < replaced.length) {
				if (buf.isNotEmpty()) {
					root.append(Component.literal(buf.toString()).withStyle(style))
					buf.setLength(0)
				}
				val code = replaced[++i].lowercaseChar()
				i++
				if (code == 'k') {
					style = style.withObfuscated(true)
					continue
				}
				val formatting = ChatFormatting.getByCode(code)
				if (formatting != null) {
					if (LegacyChatFormatting.isColor(formatting) || formatting == ChatFormatting.RESET) {
						style = Style.EMPTY.withItalic(false)
					}
					style = style.applyFormat(formatting)
				}
			} else {
				buf.append(c)
				i++
			}
		}
		if (buf.isNotEmpty()) {
			root.append(Component.literal(buf.toString()).withStyle(style))
		}
		return root
	}

	@JvmStatic
	fun cleanLoreLine(raw: String?): String = InventoryDecoder.cleanJsonText(raw)
}
