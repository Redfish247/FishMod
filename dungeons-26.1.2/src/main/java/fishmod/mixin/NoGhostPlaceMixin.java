package fishmod.mixin;

import fishmod.features.visuals.RenderOptimizer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// from NoFrills (BlockItemMixin)
@Mixin(BlockItem.class)
public abstract class NoGhostPlaceMixin {
    @Inject(method = "placeBlock", at = @At("HEAD"), cancellable = true)
    private void fishmod$noGhostBlock(BlockPlaceContext context, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (context != null && context.getLevel().isClientSide() && RenderOptimizer.noGhostPlace(context.getItemInHand())) cir.setReturnValue(true);
    }

    @Inject(method = "place", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getSoundType()Lnet/minecraft/world/level/block/SoundType;"), cancellable = true)
    private void fishmod$noGhostSound(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (context.getLevel().isClientSide() && RenderOptimizer.noGhostPlace(context.getItemInHand())) cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
