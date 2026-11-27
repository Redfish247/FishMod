package fishmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fishmod.features.visuals.ClassicSkyblock;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemModelResolver.class)
public abstract class ClassicItemModelMixin {
    @WrapOperation(
        method = "appendItemLayers",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"),
        require = 0
    )
    private Object fishmod$classicModel(ItemStack stack, DataComponentType<?> type, Operation<Object> original) {
        Object result = original.call(stack, type);
        if (type == DataComponents.ITEM_MODEL && result instanceof Identifier id) {
            try {
                Identifier replacement = ClassicSkyblock.modelFor(stack, id);
                if (replacement != null) return replacement;
            } catch (Throwable t) {
                fishmod.utils.debug.FishDiag.fail("ClassicItemModelMixin.1", "classic model swap failed", t);
            }
        }
        return result;
    }
}
