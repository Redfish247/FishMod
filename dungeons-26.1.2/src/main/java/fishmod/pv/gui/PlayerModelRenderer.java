/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.gui;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** 3D player mannequin for the Home PV panel (NEU / VyAddons approach). */
public final class PlayerModelRenderer {
	private static final long SPIN_PERIOD_MS = 4_000L;
	private static final float OFFSET_Y = 0.0625F;
	/** Client-only mannequins are never added to the level; still need a non-zero ID for item rendering. */
	private static int nextGuiEntityId = -1;

	private GuiPlayer mannequin;
	private UUID boundUuid;
	private final ItemStack[] equipped = new ItemStack[] {
		ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
	};

	/**
	 * @param flipScaleX horizontal squash for left-panel flip (1 = normal)
	 * @param flipScaleY vertical squash for left-panel flip (1 = normal)
	 * @param flipPivotX pivot matching the Home panel flip (entity draws ignore GUI pose)
	 * @param flipPivotY pivot matching the Home panel flip
	 * @param openScale PV open expand scale (entity draws ignore GUI pose)
	 * @param openPivotX panel-center pivot for the open expand
	 * @param openPivotY panel-center pivot for the open expand
	 */
	public void draw(
		GuiGraphicsExtractor graphics,
		UUID uuid,
		String name,
		int x0,
		int y0,
		int x1,
		int y1,
		int mouseX,
		int mouseY,
		ItemStack helmet,
		ItemStack chest,
		ItemStack legs,
		ItemStack boots,
		float flipScaleX,
		float flipScaleY,
		float flipPivotX,
		float flipPivotY,
		float openScale,
		float openPivotX,
		float openPivotY,
		float uiScale,
		float uiOffsetX,
		float uiOffsetY
	) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (uuid == null || level == null) {
			return;
		}
		float open = Math.max(0F, Math.min(1F, openScale));
		float sx = Math.max(0F, Math.min(1F, flipScaleX));
		float sy = Math.max(0F, Math.min(1F, flipScaleY));
		if (open < 0.05F || sx < 0.05F) {
			return;
		}
		LivingEntity entity = resolveEntity(mc, level, uuid, name);
		if (entity == null) {
			return;
		}
		equipIfChanged(entity, EquipmentSlot.HEAD, 0, helmet);
		equipIfChanged(entity, EquipmentSlot.CHEST, 1, chest);
		equipIfChanged(entity, EquipmentSlot.LEGS, 2, legs);
		equipIfChanged(entity, EquipmentSlot.FEET, 3, boots);
		applyDinnerboneFlip(entity);

		// Entity path ignores GUI pose matrices - mirror open expand then card flip in screen space.
		float[] box = {x0, y0, x1, y1};
		scaleBox(box, openPivotX, openPivotY, open, open);
		scaleBox(box, flipPivotX, flipPivotY, sx, sy);
		for (int i = 0; i < box.length; i += 2) {
			box[i] = uiOffsetX + box[i] * uiScale;
			box[i + 1] = uiOffsetY + box[i + 1] * uiScale;
		}
		float screenMouseX = uiOffsetX + mouseX * uiScale;
		float screenMouseY = uiOffsetY + mouseY * uiScale;
		int sx0 = Math.round(box[0]);
		int sy0 = Math.round(box[1]);
		int sx1 = Math.round(box[2]);
		int sy1 = Math.round(box[3]);
		if (sx1 - sx0 < 2 || sy1 - sy0 < 2) {
			return;
		}

		// Entity extraction uses screen coordinates. Reset the GUI pose so the
		// manually transformed model bounds and scissor are not transformed twice.
		graphics.pose().pushMatrix();
		graphics.pose().identity();
		graphics.enableScissor(sx0, sy0, sx1, sy1);
		try {
			int baseScale = Math.max(16, Math.round(Math.min(x1 - x0, y1 - y0) / 2f * 0.8f));
			int scale = Math.max(4, Math.round(baseScale * open * sx * uiScale));
			if (false) {
				extractSpinning(graphics, sx0, sy0, sx1, sy1, scale, entity);
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
				);
			}
		} finally {
			graphics.disableScissor();
			graphics.pose().popMatrix();
		}
	}

	public void draw(
		GuiGraphicsExtractor graphics,
		UUID uuid,
		String name,
		int x0,
		int y0,
		int x1,
		int y1,
		int mouseX,
		int mouseY,
		ItemStack helmet,
		ItemStack chest,
		ItemStack legs,
		ItemStack boots,
		float flipScaleX,
		float flipScaleY,
		float flipPivotX,
		float flipPivotY
	) {
		float midX = (x0 + x1) / 2f;
		float midY = (y0 + y1) / 2f;
		draw(
			graphics, uuid, name, x0, y0, x1, y1, mouseX, mouseY, helmet, chest, legs, boots,
			flipScaleX, flipScaleY, flipPivotX, flipPivotY, 1.0F, midX, midY, 1.0F, 0.0F, 0.0F
		);
	}

	public void draw(
		GuiGraphicsExtractor graphics,
		UUID uuid,
		String name,
		int x0,
		int y0,
		int x1,
		int y1,
		int mouseX,
		int mouseY,
		ItemStack helmet,
		ItemStack chest,
		ItemStack legs,
		ItemStack boots
	) {
		float midX = (x0 + x1) / 2f;
		float midY = (y0 + y1) / 2f;
		draw(
			graphics, uuid, name, x0, y0, x1, y1, mouseX, mouseY, helmet, chest, legs, boots,
			1.0F, 1.0F, midX, midY, 1.0F, midX, midY, 1.0F, 0.0F, 0.0F
		);
	}

	private static void scaleBox(float[] box, float pivotX, float pivotY, float scaleX, float scaleY) {
		box[0] = pivotX + (box[0] - pivotX) * scaleX;
		box[1] = pivotY + (box[1] - pivotY) * scaleY;
		box[2] = pivotX + (box[2] - pivotX) * scaleX;
		box[3] = pivotY + (box[3] - pivotY) * scaleY;
	}

	/**
	 * Full 360° yaw spin. {@link LivingEntityRenderState#bodyRot} turns the whole model;
	 * {@link LivingEntityRenderState#yRot} is head-relative-to-body only (keep 0 so head stays locked).
	 */
	private static void extractSpinning(
		GuiGraphicsExtractor graphics,
		int x0,
		int y0,
		int x1,
		int y1,
		int size,
		LivingEntity entity
	) {
		float spin = (System.currentTimeMillis() % SPIN_PERIOD_MS) / (float) SPIN_PERIOD_MS * 360.0F;
		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf camera = new Quaternionf();
		EntityRenderState renderState = createRenderState(entity);
		if (renderState instanceof LivingEntityRenderState living) {
			living.bodyRot = 180.0F + spin;
			living.yRot = 0.0F;
			living.xRot = 0.0F;
			living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
			living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
			living.scale = 1.0F;
			living.isUpsideDown = true;
		}
		Vector3f translation = new Vector3f(0.0F, renderState.boundingBoxHeight / 2.0F + OFFSET_Y, 0.0F);
		graphics.entity(renderState, size, translation, rotation, camera, x0, y0, x1, y1);
	}

	private static EntityRenderState createRenderState(LivingEntity entity) {
		EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		EntityRenderer<? super LivingEntity, ?> renderer = dispatcher.getRenderer(entity);
		EntityRenderState state = renderer.createRenderState(entity, 1.0F);
		state.shadowPieces.clear();
		state.outlineColor = 0;
		return state;
	}

	private LivingEntity resolveEntity(Minecraft mc, ClientLevel level, UUID uuid, String name) {
		if (this.mannequin == null || !uuid.equals(this.boundUuid) || this.mannequin.level() != level) {
			this.mannequin = new GuiPlayer(level, uuid, name);
			this.boundUuid = uuid;
			for (int i = 0; i < this.equipped.length; i++) {
				this.equipped[i] = ItemStack.EMPTY;
			}
		}
		return this.mannequin;
	}

	private void equipIfChanged(LivingEntity entity, EquipmentSlot slot, int index, ItemStack stack) {
		ItemStack next = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack;
		ItemStack current = this.equipped[index];
		if (ItemStack.isSameItemSameComponents(current, next) && current.getCount() == next.getCount()) {
			return;
		}
		this.equipped[index] = next.isEmpty() ? ItemStack.EMPTY : next.copy();
		try {
			entity.setItemSlot(slot, this.equipped[index]);
		} catch (Throwable ignored) {
		}
	}

	/** Dinnerbone / Grumm upside-down render when the Moulberry Konami mode is on. */
	private static void applyDinnerboneFlip(LivingEntity entity) {
		try {
			if (false) {
				entity.setCustomName(Component.literal("Dinnerbone"));
			} else {
				entity.setCustomName(null);
			}
		} catch (Throwable ignored) {
		}
	}

	private static final class GuiPlayer extends ClientMannequin {
		private final Supplier<PlayerSkinRenderCache.RenderInfo> skinLookup;

		private GuiPlayer(ClientLevel level, UUID uuid, String name) {
			super(level, Minecraft.getInstance().playerSkinRenderCache());
			setId(nextGuiEntityId--);
			String safeName = name == null || name.isBlank() ? "Player" : name;
			GameProfile profile = new GameProfile(uuid, safeName);
			ResolvableProfile resolvable = ResolvableProfile.createUnresolved(uuid);
			try {
				setComponent(DataComponents.PROFILE, resolvable);
			} catch (Throwable ignored) {
				try {
					setComponent(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
				} catch (Throwable ignored2) {
				}
			}
			// A bare GameProfile has no textures property, so SkinManager alone only ever gives the
			// default skin, and ClientMannequin applies its own lookup in tick(), which GUI entities
			// never get. The render cache fetches the textured profile off-thread and is shared.
			this.skinLookup = Minecraft.getInstance().playerSkinRenderCache().createLookup(resolvable);
		}

		@Override
		public PlayerSkin getSkin() {
			PlayerSkinRenderCache.RenderInfo info = this.skinLookup.get();
			return info != null ? info.playerSkin() : DefaultPlayerSkin.get(getUUID());
		}

		@Override
		public Component getName() {
			if (false) {
				return Component.literal("Dinnerbone");
			}
			return super.getName();
		}

		@Override
		public boolean isSpectator() {
			return false;
		}

		@Override
		public boolean shouldShowName() {
			return false;
		}
	}
}
