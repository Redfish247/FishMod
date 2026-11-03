/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.collections

import fishmod.pv.data.BossCollections
import fishmod.pv.data.CollectionIds
import fishmod.pv.gui.inventories.SkyBlockIconRenderer
import fishmod.pv.gui.inventories.SkyBlockItemFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.function.IntConsumer
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/** Shared Collections / Minions UI helpers. */
object CollectionsUi {
	const val GAP = 8
	const val PAD = 6
	const val SLOT = 20
	const val SLOT_GAP = 3
	const val ITEM_ICON = 16

	/**
	 * Collections: pick the largest crisp icon that fits without scrolling.
	 * Discrete sizes only - fractional scales look muddy.
	 */
	@JvmField
	val COLL_ICON_STEPS = intArrayOf(16, 14, 12, 10)
	const val MINION_UNLOCKED = 0xFF98E898.toInt()
	const val MINION_LOCKED = 0xFFFF9999.toInt()

	/** Hypixel SkyBlock heavy marks (not the thin ✓/✗). */
	const val MINION_TICK = "✔"
	const val MINION_CROSS = "✖"

	// Only resolved hits are cached so a not-yet-loaded icon can still upgrade later.
	private val RESOLVED = ConcurrentHashMap<String, String>()

	@JvmStatic
	fun resolveIconId(collectionId: String?): String {
		if (collectionId == null) {
			return CollectionIds.iconId(collectionId)
		}
		RESOLVED[collectionId]?.let { return it }
		val resolved = resolveIconIdUncached(collectionId) ?: return CollectionIds.iconId(collectionId)
		RESOLVED[collectionId] = resolved
		return resolved
	}

	private fun resolveIconIdUncached(collectionId: String): String? {
		if (BossCollections.isBossId(collectionId)) {
			for (boss in BossCollections.bosses()) {
				if (boss.id.equals(collectionId, ignoreCase = true)) {
					return boss.iconId
				}
			}
		}
		val keys = ArrayList<String?>()
		keys.add(CollectionIds.iconId(collectionId))
		keys.addAll(CollectionIds.lookupKeys(collectionId))
		for (key in keys) {
			if (key.isNullOrBlank()) {
				continue
			}
			if (SkyBlockIconRenderer.hasKnownIcon(key)) {
				return key
			}
			val stack: ItemStack = SkyBlockItemFactory.iconStack(key)
			if (!stack.isEmpty && !stack.`is`(Items.PAPER)) {
				return key
			}
		}
		return null
	}

	@JvmStatic
	fun drawVanillaIcon(g: GuiGraphicsExtractor, icon: ItemStack?, x: Int, y: Int, size: Int) {
		if (icon == null || icon.isEmpty) {
			return
		}
		val draw = maxOf(1, minOf(ITEM_ICON, size))
		if (draw == ITEM_ICON) {
			g.item(icon, x, y)
			return
		}
		val scale = draw / ITEM_ICON.toFloat()
		g.pose().pushMatrix()
		g.pose().translate(x.toFloat(), y.toFloat())
		g.pose().scale(scale, scale)
		g.item(icon, 0, 0)
		g.pose().popMatrix()
	}

	@JvmStatic
	@JvmOverloads
	fun drawIcon(g: GuiGraphicsExtractor, id: String?, x: Int, y: Int, size: Int = ITEM_ICON) {
		val draw = minOf(ITEM_ICON, maxOf(1, size))
		SkyBlockIconRenderer.draw(g, id, x, y, draw)
	}

	@JvmStatic
	fun scrollBy(scrollY: Double, scroll: Int, maxScroll: Int, step: Int, setter: IntConsumer): Boolean {
		if (maxScroll <= 0) {
			return false
		}
		val next = when {
			scrollY > 0 -> maxOf(0, scroll - step)
			scrollY < 0 -> minOf(maxScroll, scroll + step)
			else -> scroll
		}
		setter.accept(next)
		return next != scroll
	}

	@JvmStatic
	fun trim(font: Font, value: String?, maxW: Int): String {
		if (value == null) {
			return ""
		}
		if (maxW <= 0 || font.width(value) <= maxW) {
			return value
		}
		val ellipsis = "..."
		val ellipsisW = font.width(ellipsis)
		if (maxW <= ellipsisW) {
			return ellipsis
		}
		val sb = StringBuilder(value)
		while (sb.isNotEmpty() && font.width(sb.toString()) + ellipsisW > maxW) {
			sb.setLength(sb.length - 1)
		}
		return "$sb$ellipsis"
	}
}
