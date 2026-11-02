/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui

import com.mojang.authlib.GameProfile
import java.util.UUID
import java.util.function.Supplier
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.ClientMannequin
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.PlayerSkinRenderCache
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.PlayerSkin
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ResolvableProfile
import org.joml.Quaternionf
import org.joml.Vector3f

/** 3D player mannequin for the Home PV panel (NEU / VyAddons approach). */
class PlayerModelRenderer {
	private var mannequin: GuiPlayer? = null
	private var boundUuid: UUID? = null
	private val equipped = Array<ItemStack>(4) { ItemStack.EMPTY }

	/**
	 * @param flipScaleX horizontal squash for left-panel flip (1 = normal)
	 * @param flipScaleY vertical squash for left-panel flip (1 = normal)
	 * @param flipPivotX pivot matching the Home panel flip (entity draws ignore GUI pose)
	 * @param flipPivotY pivot matching the Home panel flip
	 * @param openScale PV open expand scale (entity draws ignore GUI pose)
	 * @param openPivotX panel-center pivot for the open expand
	 * @param openPivotY panel-center pivot for the open expand
	 */
	fun draw(
		graphics: GuiGraphicsExtractor,
		uuid: UUID?,
		name: String?,
		x0: Int,
		y0: Int,
		x1: Int,
		y1: Int,
		mouseX: Int,
		mouseY: Int,
		helmet: ItemStack?,
		chest: ItemStack?,
		legs: ItemStack?,
		boots: ItemStack?,
		flipScaleX: Float,
		flipScaleY: Float,
		flipPivotX: Float,
		flipPivotY: Float,
		openScale: Float,
		openPivotX: Float,
		openPivotY: Float,
		uiScale: Float,
		uiOffsetX: Float,
		uiOffsetY: Float
	) {
		val mc = Minecraft.getInstance()
		val level = mc.level
		if (uuid == null || level == null) {
			return
		}
		val open = maxOf(0f, minOf(1f, openScale))
		val sx = maxOf(0f, minOf(1f, flipScaleX))
		val sy = maxOf(0f, minOf(1f, flipScaleY))
		if (open < 0.05f || sx < 0.05f) {
			return
		}
		val entity = resolveEntity(level, uuid, name)
		equipIfChanged(entity, EquipmentSlot.HEAD, 0, helmet)
		equipIfChanged(entity, EquipmentSlot.CHEST, 1, chest)
		equipIfChanged(entity, EquipmentSlot.LEGS, 2, legs)
		equipIfChanged(entity, EquipmentSlot.FEET, 3, boots)
		applyDinnerboneFlip(entity)

		// Entity path ignores GUI pose matrices - mirror open expand then card flip in screen space.
		val box = floatArrayOf(x0.toFloat(), y0.toFloat(), x1.toFloat(), y1.toFloat())
		scaleBox(box, openPivotX, openPivotY, open, open)
		scaleBox(box, flipPivotX, flipPivotY, sx, sy)
		for (i in box.indices step 2) {
			box[i] = uiOffsetX + box[i] * uiScale
			box[i + 1] = uiOffsetY + box[i + 1] * uiScale
		}
		val screenMouseX = uiOffsetX + mouseX * uiScale
		val screenMouseY = uiOffsetY + mouseY * uiScale
		val sx0 = Math.round(box[0])
		val sy0 = Math.round(box[1])
		val sx1 = Math.round(box[2])
		val sy1 = Math.round(box[3])
		if (sx1 - sx0 < 2 || sy1 - sy0 < 2) {
			return
		}

		// Entity extraction uses screen coordinates. Reset the GUI pose so the
		// manually transformed model bounds and scissor are not transformed twice.
		graphics.pose().pushMatrix()
		graphics.pose().identity()
		graphics.enableScissor(sx0, sy0, sx1, sy1)
		try {
			val baseScale = maxOf(16, Math.round(minOf(x1 - x0, y1 - y0) / 2f * 0.8f))
			val scale = maxOf(4, Math.round(baseScale * open * sx * uiScale))
			if (SPIN) {
				extractSpinning(graphics, sx0, sy0, sx1, sy1, scale, entity)
			} else {
				// Same approach as NEU BasicPage.drawEntityOnScreen / SkyHanni fakePlayer(followMouse):
				// vanilla inventory entity extract with mouse look-at. Cost is small vs Home text.
				InventoryScreen.extractEntityInInventoryFollowsMouse(
					graphics,
					sx0,
					sy0,
					sx1,
					sy1,
					scale,
					OFFSET_Y,
					screenMouseX,
					screenMouseY,
					entity
				)
			}
		} finally {
			graphics.disableScissor()
			graphics.pose().popMatrix()
		}
	}

	fun draw(
		graphics: GuiGraphicsExtractor,
		uuid: UUID?,
		name: String?,
		x0: Int,
		y0: Int,
		x1: Int,
		y1: Int,
		mouseX: Int,
		mouseY: Int,
		helmet: ItemStack?,
		chest: ItemStack?,
		legs: ItemStack?,
		boots: ItemStack?,
		flipScaleX: Float,
		flipScaleY: Float,
		flipPivotX: Float,
		flipPivotY: Float
	) {
		val midX = (x0 + x1) / 2f
		val midY = (y0 + y1) / 2f
		draw(
			graphics, uuid, name, x0, y0, x1, y1, mouseX, mouseY, helmet, chest, legs, boots,
			flipScaleX, flipScaleY, flipPivotX, flipPivotY, 1.0f, midX, midY, 1.0f, 0.0f, 0.0f
		)
	}

	fun draw(
		graphics: GuiGraphicsExtractor,
		uuid: UUID?,
		name: String?,
		x0: Int,
		y0: Int,
		x1: Int,
		y1: Int,
		mouseX: Int,
		mouseY: Int,
		helmet: ItemStack?,
		chest: ItemStack?,
		legs: ItemStack?,
		boots: ItemStack?
	) {
		val midX = (x0 + x1) / 2f
		val midY = (y0 + y1) / 2f
		draw(
			graphics, uuid, name, x0, y0, x1, y1, mouseX, mouseY, helmet, chest, legs, boots,
			1.0f, 1.0f, midX, midY, 1.0f, midX, midY, 1.0f, 0.0f, 0.0f
		)
	}

	private fun resolveEntity(level: ClientLevel, uuid: UUID, name: String?): LivingEntity {
		val current = mannequin
		if (current != null && uuid == boundUuid && current.level() === level) {
			return current
		}
		boundUuid = uuid
		equipped.fill(ItemStack.EMPTY)
		return GuiPlayer(level, uuid, name).also { mannequin = it }
	}

	private fun equipIfChanged(entity: LivingEntity, slot: EquipmentSlot, index: Int, stack: ItemStack?) {
		val next = if (stack == null || stack.isEmpty) ItemStack.EMPTY else stack
		val current = equipped[index]
		if (ItemStack.isSameItemSameComponents(current, next) && current.count == next.count) {
			return
		}
		equipped[index] = if (next.isEmpty) ItemStack.EMPTY else next.copy()
		try {
			entity.setItemSlot(slot, equipped[index])
		} catch (_: Throwable) {
		}
	}

	private class GuiPlayer(level: ClientLevel, uuid: UUID, name: String?) :
		ClientMannequin(level, Minecraft.getInstance().playerSkinRenderCache()) {
		private val skinLookup: Supplier<PlayerSkinRenderCache.RenderInfo>

		init {
			setId(nextGuiEntityId--)
			val safeName = if (name.isNullOrBlank()) "Player" else name
			val profile = GameProfile(uuid, safeName)
			val resolvable = ResolvableProfile.createUnresolved(uuid)
			try {
				setComponent(DataComponents.PROFILE, resolvable)
			} catch (_: Throwable) {
				try {
					setComponent(DataComponents.PROFILE, ResolvableProfile.createResolved(profile))
				} catch (_: Throwable) {
				}
			}
			// A bare GameProfile has no textures property, so SkinManager alone only ever gives the
			// default skin, and ClientMannequin applies its own lookup in tick(), which GUI entities
			// never get. The render cache fetches the textured profile off-thread and is shared.
			skinLookup = Minecraft.getInstance().playerSkinRenderCache().createLookup(resolvable)
		}

		override fun getSkin(): PlayerSkin =
			(skinLookup.get() as PlayerSkinRenderCache.RenderInfo?)?.playerSkin() ?: DefaultPlayerSkin.get(getUUID())

		override fun getName(): Component {
			if (DINNERBONE) {
				return Component.literal("Dinnerbone")
			}
			return super.getName()
		}

		override fun isSpectator(): Boolean = false

		override fun shouldShowName(): Boolean = false
	}

	private companion object {
		const val SPIN_PERIOD_MS = 4_000L
		const val OFFSET_Y = 0.0625f
		// Spinning model and Dinnerbone mode are switched off, as in BetterPV.
		const val SPIN = false
		const val DINNERBONE = false

		/** Client-only mannequins are never added to the level; still need a non-zero ID for item rendering. */
		var nextGuiEntityId = -1

		fun scaleBox(box: FloatArray, pivotX: Float, pivotY: Float, scaleX: Float, scaleY: Float) {
			box[0] = pivotX + (box[0] - pivotX) * scaleX
			box[1] = pivotY + (box[1] - pivotY) * scaleY
			box[2] = pivotX + (box[2] - pivotX) * scaleX
			box[3] = pivotY + (box[3] - pivotY) * scaleY
		}

		/**
		 * Full 360° yaw spin. [LivingEntityRenderState.bodyRot] turns the whole model;
		 * [LivingEntityRenderState.yRot] is head-relative-to-body only (keep 0 so head stays locked).
		 */
		fun extractSpinning(
			graphics: GuiGraphicsExtractor,
			x0: Int,
			y0: Int,
			x1: Int,
			y1: Int,
			size: Int,
			entity: LivingEntity
		) {
			val spin = (System.currentTimeMillis() % SPIN_PERIOD_MS) / SPIN_PERIOD_MS.toFloat() * 360.0f
			val rotation = Quaternionf().rotateZ(Math.PI.toFloat())
			val camera = Quaternionf()
			val renderState = createRenderState(entity)
			if (renderState is LivingEntityRenderState) {
				renderState.bodyRot = 180.0f + spin
				renderState.yRot = 0.0f
				renderState.xRot = 0.0f
				renderState.boundingBoxWidth = renderState.boundingBoxWidth / renderState.scale
				renderState.boundingBoxHeight = renderState.boundingBoxHeight / renderState.scale
				renderState.scale = 1.0f
				renderState.isUpsideDown = true
			}
			val translation = Vector3f(0.0f, renderState.boundingBoxHeight / 2.0f + OFFSET_Y, 0.0f)
			graphics.entity(renderState, size.toFloat(), translation, rotation, camera, x0, y0, x1, y1)
		}

		fun createRenderState(entity: LivingEntity): EntityRenderState {
			val dispatcher = Minecraft.getInstance().entityRenderDispatcher
			val state = dispatcher.getRenderer(entity).createRenderState(entity, 1.0f)
			state.shadowPieces.clear()
			state.outlineColor = 0
			return state
		}

		/** Dinnerbone / Grumm upside-down render when the Moulberry Konami mode is on. */
		fun applyDinnerboneFlip(entity: LivingEntity) {
			try {
				entity.customName = if (DINNERBONE) Component.literal("Dinnerbone") else null
			} catch (_: Throwable) {
			}
		}
	}
}
