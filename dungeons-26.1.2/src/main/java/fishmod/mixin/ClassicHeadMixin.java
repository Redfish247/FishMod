package fishmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fishmod.features.visuals.ClassicSkyblock;
import net.minecraft.client.renderer.special.PlayerHeadSpecialRenderer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerHeadSpecialRenderer.class)
public abstract class ClassicHeadMixin {
    @WrapOperation(
        method = "extractArgument(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/client/renderer/PlayerSkinRenderCache$RenderInfo;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"),
        require = 0
    )
    private Object fishmod$classicHead(ItemStack stack, DataComponentType<?> type, Operation<Object> original) {
        Object result = original.call(stack, type);
        if (type == DataComponents.PROFILE) {
            try {
                ResolvableProfile p = ClassicSkyblock.profileFor(stack);
                if (p != null) return p;
            } catch (Throwable t) {
                fishmod.utils.debug.FishDiag.fail("ClassicHeadMixin.1", "classic head swap failed", t);
            }
        }
        return result;
    }
}
