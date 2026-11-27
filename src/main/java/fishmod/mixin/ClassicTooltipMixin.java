package fishmod.mixin;

import fishmod.features.visuals.ClassicSkyblock;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GuiGraphicsExtractor.class)
public abstract class ClassicTooltipMixin {
    @ModifyVariable(method = "tooltip", at = @At("HEAD"), argsOnly = true, require = 0)
    private Identifier fishmod$classicTooltip(Identifier style) {
        return ClassicSkyblock.tooltipStyle(style);
    }
}
