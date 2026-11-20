package fishmod.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fishmod.features.mining.MiningForagingMisc;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Gemstone desync fix: lone stained glass panes keep their full shape (idea from NoFrills)
@Mixin(IronBarsBlock.class)
public abstract class GemstonePaneMixin {
    @ModifyReturnValue(method = "updateShape", at = @At("RETURN"))
    private BlockState fishmod$fullPane(BlockState original) {
        return MiningForagingMisc.fullPane(original);
    }
}
