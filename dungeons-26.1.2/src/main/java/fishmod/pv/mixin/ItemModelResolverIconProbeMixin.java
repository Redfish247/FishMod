/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from BetterPV by Vyriv (https://github.com/Vyriv/BetterPV).
 */
package fishmod.pv.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import fishmod.pv.gui.inventories.SkyBlockIconRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.MissingItemModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the model id actually used by {@link ItemModelResolver#appendItemLayers}
 * (after remapper mixins) so Auto icon mode can detect {@link MissingItemModel}
 * without bypassing remappers via raw {@code ModelManager.getItemModel(hypixelId)}.
 */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverIconProbeMixin {
	@Inject(
		method = "appendItemLayers",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/item/ItemModelResolver;getItemModel(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/item/ItemModel;"
		)
	)
	private void betterpv$captureResolvedModel(
		ItemStackRenderState output,
		ItemStack item,
		ItemDisplayContext displayContext,
		Level level,
		ItemOwner owner,
		int seed,
		CallbackInfo ci,
		@Local Identifier modelId
	) {
		if (!SkyBlockIconRenderer.isProbing() || modelId == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.getModelManager() == null) {
			return;
		}
		ItemModel model = client.getModelManager().getItemModel(modelId);
		SkyBlockIconRenderer.recordProbeMissing(model instanceof MissingItemModel);
	}
}
