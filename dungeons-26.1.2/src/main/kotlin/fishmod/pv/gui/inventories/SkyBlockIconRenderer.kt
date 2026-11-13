/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui.inventories

import java.util.Collections
import java.util.Locale
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

object SkyBlockIconRenderer {
	private val RESOLVES_BY_MODEL = ConcurrentHashMap<String, Boolean>()
	private val PROBING: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }
	private val LAST_MISSING = ThreadLocal<Boolean?>()

	private val DRAWN_BY_STACK: MutableMap<ItemStack, Drawn> = Collections.synchronizedMap(WeakHashMap())
	private val DRAWN_BY_ID = ConcurrentHashMap<String, ItemStack>()

	@JvmStatic
	fun invalidateProbeCache() {
		RESOLVES_BY_MODEL.clear()
		DRAWN_BY_STACK.clear()
		DRAWN_BY_ID.clear()
	}

	@JvmStatic
	fun isProbing(): Boolean = PROBING.get() == true

	@JvmStatic
	fun recordProbeMissing(missing: Boolean) {
		if (isProbing()) {
			LAST_MISSING.set(missing)
		}
	}

	@JvmStatic
	fun draw(g: GuiGraphicsExtractor?, skyblockId: String?, x: Int, y: Int) {
		draw(g, skyblockId, null, x, y, 16)
	}

	@JvmStatic
	fun draw(g: GuiGraphicsExtractor?, skyblockId: String?, x: Int, y: Int, size: Int) {
		draw(g, skyblockId, null, x, y, size)
	}

	@JvmStatic
	fun draw(g: GuiGraphicsExtractor?, skyblockId: String?, itemModelOverride: String?, x: Int, y: Int, size: Int) {
		val stack = if (skyblockId.isNullOrBlank()) ItemStack.EMPTY else SkyBlockItemFactory.iconStack(skyblockId)
		draw(g, stack, skyblockId, itemModelOverride, x, y, size)
	}

	@JvmStatic
	fun draw(g: GuiGraphicsExtractor?, stack: ItemStack?, skyblockId: String?, x: Int, y: Int) {
		draw(g, stack, skyblockId, null, x, y, 16)
	}

	@JvmStatic
	fun draw(g: GuiGraphicsExtractor?, stack: ItemStack?, skyblockId: String?, x: Int, y: Int, size: Int) {
		draw(g, stack, skyblockId, null, x, y, size)
	}

	@JvmStatic
	fun draw(
		g: GuiGraphicsExtractor?,
		stack: ItemStack?,
		skyblockId: String?,
		itemModelOverride: String?,
		x: Int,
		y: Int,
		size: Int
	) {
		if (g == null) {
			return
		}
		val model = resolveModelString(skyblockId, itemModelOverride)
		val usePack = model != null && resolvesWithoutMissing(model, skyblockId, stack)
		val drawn = cachedStack(stack, skyblockId, if (usePack) model else null)
		val paper = drawn.isEmpty || drawn.`is`(Items.PAPER)

		if (!usePack && paper) {
			val png = officialTexture(skyblockId, model)
			if (png != null) {
				blitOfficial(g, png, model, skyblockId, x, y, size)
				return
			}
		}

		drawStack(g, drawn, x, y, size)
	}

	@JvmStatic
	fun hasKnownIcon(skyblockId: String?): Boolean {
		if (skyblockId.isNullOrBlank()) {
			return false
		}
		if (SkyBlockItemFactory.itemModel(skyblockId) != null) {
			return true
		}
		return SkyBlockItemFactory.customIcon(skyblockId) != null
	}

	@JvmStatic
	fun resolvesWithoutMissing(model: String?, skyblockId: String?, template: ItemStack?): Boolean {
		if (model.isNullOrBlank()) {
			return false
		}
		val key = model.trim().lowercase(Locale.ROOT)
		RESOLVES_BY_MODEL[key]?.let { return it }
		val modelId = Identifier.tryParse(model.trim())
		if (modelId == null) {
			RESOLVES_BY_MODEL[key] = false
			return false
		}

		val client: Minecraft? = Minecraft.getInstance()
		val resolver = client?.itemModelResolver ?: return false

		val probe = if (template != null && !template.isEmpty) template.copy() else ItemStack(Items.PAPER)
		applyHypixelModel(probe, modelId, skyblockId)

		PROBING.set(true)
		LAST_MISSING.remove()
		return try {
			val state = ItemStackRenderState()
			resolver.appendItemLayers(state, probe, ItemDisplayContext.GUI, null, null, 0)
			val ok = LAST_MISSING.get() == false
			RESOLVES_BY_MODEL[key] = ok
			ok
		} catch (ignored: Exception) {
			RESOLVES_BY_MODEL[key] = false
			false
		} finally {
			PROBING.set(false)
			LAST_MISSING.remove()
		}
	}

	private fun resolveModelString(skyblockId: String?, itemModelOverride: String?): String? {
		if (!itemModelOverride.isNullOrBlank()) {
			if (!skyblockId.isNullOrBlank()) {
				SkyBlockItemFactory.customIconModel(skyblockId, itemModelOverride)
			}
			return itemModelOverride.trim()
		}
		return SkyBlockItemFactory.itemModel(skyblockId)
	}

	private fun officialTexture(skyblockId: String?, model: String?): Identifier? {
		if (!model.isNullOrBlank()) {
			SkyBlockItemIconCache.getOrRequest(model)?.let { return it }
		}
		return SkyBlockItemFactory.customIcon(skyblockId)
	}

	private fun blitOfficial(
		g: GuiGraphicsExtractor,
		png: Identifier,
		model: String?,
		skyblockId: String?,
		x: Int,
		y: Int,
		size: Int
	) {
		val tex = if (model != null) SkyBlockItemIconCache.textureSize(model) else SkyBlockItemFactory.customIconSize(skyblockId)
		val draw = maxOf(1, size)
		g.blit(RenderPipelines.GUI_TEXTURED, png, x, y, 0f, 0f, draw, draw, tex, tex, tex, tex)
	}

	private data class Drawn(val id: String?, val model: String?, val stack: ItemStack)

	private fun cachedStack(stack: ItemStack?, skyblockId: String?, model: String?): ItemStack {
		if (stack != null && !stack.isEmpty) {
			val hit = DRAWN_BY_STACK[stack]
			if (hit != null && hit.id == skyblockId && hit.model == model) {
				return hit.stack
			}
			val built = stackForPack(stack, skyblockId, model)
			DRAWN_BY_STACK[stack] = Drawn(skyblockId, model, built)
			return built
		}
		val key = "$skyblockId|$model"
		DRAWN_BY_ID[key]?.let { return it }
		val built = stackForPack(stack, skyblockId, model)
		// Paper stand-ins may still upgrade once NEU loads, so only freeze real items.
		if (!built.`is`(Items.PAPER) && DRAWN_BY_ID.size < 4096) {
			DRAWN_BY_ID[key] = built
		}
		return built
	}

	private fun stackForPack(stack: ItemStack?, skyblockId: String?, model: String?): ItemStack {
		var drawn = if (stack != null && !stack.isEmpty) {
			stack.copy()
		} else if (!skyblockId.isNullOrBlank()) {
			SkyBlockItemFactory.iconStack(skyblockId)
		} else {
			ItemStack(Items.PAPER)
		}
		if (drawn.isEmpty) {
			drawn = ItemStack(Items.PAPER)
		}
		val modelId = if (model.isNullOrBlank()) null else Identifier.tryParse(model.trim())
		if (modelId != null) {
			applyHypixelModel(drawn, modelId, skyblockId)
		} else {
			SkyBlockItemFactory.applySkyBlockIdentity(drawn, skyblockId)
		}
		return drawn
	}

	private fun applyHypixelModel(stack: ItemStack, modelId: Identifier, skyblockId: String?) {
		stack.set(DataComponents.ITEM_MODEL, modelId)
		SkyBlockItemFactory.applySkyBlockIdentity(stack, skyblockId)
	}

	private fun drawStack(g: GuiGraphicsExtractor, stack: ItemStack?, x: Int, y: Int, size: Int) {
		if (stack == null || stack.isEmpty) {
			return
		}
		val draw = maxOf(1, size)
		if (draw == 16) {
			g.item(stack, x, y)
			return
		}
		val scale = draw / 16f
		g.pose().pushMatrix()
		g.pose().translate(x.toFloat(), y.toFloat())
		g.pose().scale(scale, scale)
		g.item(stack, 0, 0)
		g.pose().popMatrix()
	}
}
