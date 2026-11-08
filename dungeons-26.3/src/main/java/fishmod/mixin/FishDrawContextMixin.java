package fishmod.mixin;

import fishmod.utils.config.values.VisualSettings;
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
import fishmod.cosmetic.NameRewriter;
import fishmod.cosmetic.NickState;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;

@Mixin(GuiGraphicsExtractor.class)
public class FishDrawContextMixin {

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
                                          ClientTooltipPositioner positioner, Identifier style, boolean unused, CallbackInfo ci,
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
                                          ClientTooltipPositioner positioner, Identifier style, boolean unused, CallbackInfo ci,
                                          @Share("fishmod$ttShift") LocalBooleanRef shifted) {
        if (shifted.get()) pose.popMatrix();
    }

    @ModifyVariable(method = "itemCooldown", at=@At("STORE"), ordinal = 0)
    private float noCooldown(float f) {
        return fishmod.features.CooldownOverlay.shouldHideVanillaCooldown() ? 0 : f;
    }

    @Inject(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V", at=@At("HEAD"))
    private void fishmod$itemScaleUp(LivingEntity entity, Level world, ItemStack stack, int x, int y, int seed, CallbackInfo ci,
                         @Share("fishmod$headScaled") LocalBooleanRef scaled) {
        if (entity != null) {
            try {
                fishmod.features.ItemRarityHotbar.drawRarity((GuiGraphicsExtractor) (Object) this, stack, x, y);
            } catch (Throwable t) {
                fishmod.utils.debug.FishDiag.fail("FishDrawContextMixin.1", "hotbar rarity draw failed item=" + stack.getItem(), t);
            }
        }
        if (VisualSettings.legacyPlayerHead && stack.getItem() == Items.PLAYER_HEAD) {
            float scale = 0.875f;
            int offset = (16 - (int)(scale * 16)) / 2;
            scaled.set(true);

            pose.pushMatrix();
            pose.translate(x * (1 - scale) + offset, y * (1 - scale) + offset);
            pose.scale(scale);
        }
    }

    @Inject(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V", at=@At("TAIL"))
    private void fishmod$itemScaleDown(LivingEntity entity, Level world, ItemStack stack, int x, int y, int seed, CallbackInfo ci,
                           @Share("fishmod$headScaled") LocalBooleanRef scaled) {
        // Pop only what HEAD pushed, so a mid-frame toggle can't unbalance the pose stack.
        if (scaled.get()) {
            pose.popMatrix();
        }
    }

    @ModifyVariable(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V", at = @At("HEAD"), argsOnly = true)
    private Component fishmod$ds1(Component text) {
        return fishmod$swap(text);
    }

    @ModifyVariable(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V", at = @At("HEAD"), argsOnly = true)
    private Component fishmod$ds2(Component text) {
        return fishmod$swap(text);
    }

    @ModifyVariable(method = "textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V", at = @At("HEAD"), argsOnly = true)
    private Component fishmod$ds3(Component text) {
        return fishmod$swap(text);
    }

    @ModifyVariable(method = "setComponentTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V", at = @At("HEAD"), argsOnly = true)
    private List<Component> fishmod$tt1(List<Component> lines) {
        return fishmod$swapList(lines);
    }

    @ModifyVariable(method = "setComponentTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/resources/Identifier;)V", at = @At("HEAD"), argsOnly = true)
    private List<Component> fishmod$tt2(List<Component> lines) {
        return fishmod$swapList(lines);
    }

    @ModifyVariable(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;II)V", at = @At("HEAD"), argsOnly = true)
    private List<Component> fishmod$tt3(List<Component> lines) {
        return fishmod$swapList(lines);
    }

    @ModifyVariable(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;)V", at = @At("HEAD"), argsOnly = true)
    private List<Component> fishmod$tt4(List<Component> lines) {
        return fishmod$swapList(lines);
    }

    private static Component fishmod$swap(Component text) {
        if (text == null) return text;
        if (!NickState.isActive() && fishmod.cosmetic.RemoteNicks.isEmpty()) return text;
        try {
            Component out = text;
            if (NickState.isActive()) {
                String real = NickState.realName();
                if (!real.isEmpty() && out.getString().contains(real))
                    out = NameRewriter.replaceName(out, real, NickState.asComponent());
            }
            return fishmod.cosmetic.RemoteNicks.applyResolvedOnly(out);
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("FishDrawContextMixin.cosmetic", "gui text nick swap failed", t);
            return text;
        }
    }

    private static List<Component> fishmod$swapList(List<Component> lines) {
        if (lines == null || lines.isEmpty()) return lines;
        List<Component> out = null;
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            if (line == null) continue;
            Component swapped = fishmod$swap(line);
            if (swapped != line) {
                if (out == null) out = new ArrayList<>(lines);
                out.set(i, swapped);
            }
        }
        return out != null ? out : lines;
    }
}
