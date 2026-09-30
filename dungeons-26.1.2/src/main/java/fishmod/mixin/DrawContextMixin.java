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
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.resources.Identifier;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import org.joml.Matrix3x2fStack;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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

    // Vanilla sits 12px off the cursor; pull it 6px closer on whichever side it lands.
    private static final int FISHMOD$TOOLTIP_PULL = 6;
    private static final ClientTooltipPositioner FISHMOD$CLOSER = (sw, sh, x, y, w, h) -> {
        Vector2ic p = DefaultTooltipPositioner.INSTANCE.positionTooltip(sw, sh, x, y, w, h);
        int nx = p.x() > x ? p.x() - FISHMOD$TOOLTIP_PULL : Math.max(4, p.x() + FISHMOD$TOOLTIP_PULL);
        return new Vector2i(nx, p.y());
    };

    @ModifyVariable(method = "tooltip", at = @At("HEAD"), argsOnly = true)
    private ClientTooltipPositioner fishmod$tooltipCloser(ClientTooltipPositioner positioner) {
        return positioner == DefaultTooltipPositioner.INSTANCE ? FISHMOD$CLOSER : positioner;
    }

    @Inject(method = "tooltip", at = @At("HEAD"))
    private void fishmod$tooltipScrollPush(Font font, List<ClientTooltipComponent> lines, int xo, int yo,
                                          ClientTooltipPositioner positioner, Identifier style, CallbackInfo ci,
                                          @Share("fishmod$ttShift") LocalBooleanRef shifted) {
        float scale = fishmod.features.ScrollableTooltip.effectiveScale();
        float ox = fishmod.features.ScrollableTooltip.offsetX;
        float oy = fishmod.features.ScrollableTooltip.offsetY;
        boolean on = fishmod.features.ScrollableTooltip.isEnabled() && (scale != 1f || ox != 0f || oy != 0f);
        shifted.set(on);
        if (!on) return;
        pose.pushMatrix();
        pose.translate(xo, yo);
        pose.scale(scale);
        pose.translate(ox, oy);
        pose.translate(-xo, -yo);
    }

    @Inject(method = "tooltip", at = @At("RETURN"))
    private void fishmod$tooltipScrollPop(Font font, List<ClientTooltipComponent> lines, int xo, int yo,
                                          ClientTooltipPositioner positioner, Identifier style, CallbackInfo ci,
                                          @Share("fishmod$ttShift") LocalBooleanRef shifted) {
        if (shifted.get()) pose.popMatrix();
    }

    @ModifyVariable(method = "itemCooldown", at=@At("STORE"), ordinal = 0)
    private float noCooldown(float f) {
        return fishmod.features.CooldownOverlay.shouldHideVanillaCooldown() ? 0 : f;
    }

    @Inject(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V", at=@At("HEAD"))
    private void scaleUp(LivingEntity entity, Level world, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        if (entity != null) {
            try {
                fishmod.features.ItemRarityHotbar.drawRarity((GuiGraphicsExtractor) (Object) this, stack, x, y);
            } catch (Throwable t) {
                fishmod.utils.debug.FishDiag.fail("DrawContextMixin.1", "hotbar rarity draw failed item=" + stack.getItem(), t);
            }
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
