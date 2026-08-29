package fishmod.mixin;

import fishmod.utils.config.values.Visual;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(GuiGraphicsExtractor.class)
public class DrawContextMixin {

    @Final
    @Shadow
    private Matrix3x2fStack pose;

    @Unique private boolean fishmod$tooltipShifted;

    // Scrollable / scalable tooltips: nudge + scale the pose around the tooltip draw.
    @Inject(method = "tooltip", at = @At("HEAD"))
    private void fishmod$tooltipScrollPush(Font font, List<ClientTooltipComponent> lines, int xo, int yo,
                                          ClientTooltipPositioner positioner, Identifier style, CallbackInfo ci) {
        fishmod$tooltipShifted = fishmod.features.ScrollableTooltip.isEnabled();
        if (!fishmod$tooltipShifted) return;
        pose.pushMatrix();
        pose.translate(xo, yo);
        pose.scale(fishmod.features.ScrollableTooltip.effectiveScale());
        pose.translate(fishmod.features.ScrollableTooltip.offsetX, fishmod.features.ScrollableTooltip.offsetY);
        pose.translate(-xo, -yo);
    }

    @Inject(method = "tooltip", at = @At("TAIL"))
    private void fishmod$tooltipScrollPop(Font font, List<ClientTooltipComponent> lines, int xo, int yo,
                                         ClientTooltipPositioner positioner, Identifier style, CallbackInfo ci) {
        if (fishmod$tooltipShifted) { pose.popMatrix(); fishmod$tooltipShifted = false; }
    }


    @ModifyVariable(method = "itemCooldown", at=@At("STORE"), ordinal = 0)
    private float noCooldown(float f) {
        return Visual.hideCooldown? 0: f;
    }

    @Inject(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V", at=@At("HEAD"))
    private void scaleUp(LivingEntity entity, Level world, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        // Rarity background BEHIND the item. The entity-context drawItem overload is what the hotbar
        // uses (entity = the player); inventory slots delegate with a null entity and are handled by
        // INVENTORY_SLOT_BEFORE instead, so gating on entity != null avoids drawing it twice.
        if (entity != null) {
            fishmod.features.ItemRarityHotbar.drawRarity((GuiGraphicsExtractor) (Object) this, stack, x, y);
        }
        if (Visual.oldPlayerHead && stack.getItem() == Items.PLAYER_HEAD) {
            float scale = 0.875f;
            int offset = (16 - (int)(scale * 16)) / 2;

            pose.pushMatrix();
            pose.translate(x * (1 - scale) + offset, y * (1 - scale) + offset);
            pose.scale(scale);
        }
    }

    @Inject(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V", at=@At("TAIL"))
    private void scaleDown(LivingEntity entity, Level world, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        if (Visual.oldPlayerHead && stack.getItem() == Items.PLAYER_HEAD) {
            pose.popMatrix();
        }
    }

}
