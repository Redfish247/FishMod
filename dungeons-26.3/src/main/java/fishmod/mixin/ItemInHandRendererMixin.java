package fishmod.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.config.values.VisualSettings;
import fishmod.utils.data.ItemTools;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Inject(method = "submitArmWithItem", require = 0, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER))
    private void fishmod$animPre(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float f, float g, InteractionHand hand, float attack,
                                ItemStack itemStack, float inverseArmHeight, PoseStack pose,
                                SubmitNodeCollector col, int light, CallbackInfo ci) {
        if (itemStack.isEmpty()) return;

        if (FishSettings.animEnabled) {
            float sign = hand == InteractionHand.MAIN_HAND ? 1f : -1f;
            pose.translate((float) FishSettings.animX * sign, (float) FishSettings.animY, (float) FishSettings.animZ);
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean fishmod$isSwordBlocking(ItemStack stack, InteractionHand hand) {
        var player = Minecraft.getInstance().player;
        return FishSettings.swordBlockingEnabled
                && player != null
                && hand == InteractionHand.MAIN_HAND
                && !stack.isEmpty()
                && stack.is(ItemTags.SWORDS)
                && Minecraft.getInstance().options.keyUse.isDown()
                && !player.isUsingItem();
    }

    @ModifyVariable(method = "submitArmWithItem", require = 0, at = @At("HEAD"), ordinal = 2, argsOnly = true)
    private float fishmod$noSwing(float attack) {
        try {
            if (VisualSettings.renderOptimizer && VisualSettings.noSwingAnimation) {
                var player = Minecraft.getInstance().player;
                if (!VisualSettings.noSwingTerminatorOnly || (player != null && "TERMINATOR".equals(ItemTools.getId(player.getMainHandItem())))) return 1f;
            }
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("ItemInHandRendererMixin.1", "no-swing terminator check failed", t);
        }
        return attack;
    }

    @Inject(method = "submitArmWithItem", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void fishmod$animRotScale(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float f, float g, InteractionHand hand, float attack,
                                     ItemStack itemStack, float inverseArmHeight, PoseStack pose,
                                     SubmitNodeCollector col, int light, CallbackInfo ci) {
        if (fishmod$isSwordBlocking(itemStack, hand)) {
            pose.translate(-0.14142136f, 0.08f, 0.14142136f);
            pose.rotateDegrees(Axis.XP, -102.25f);
            pose.rotateDegrees(Axis.YP, 13.365f);
            pose.rotateDegrees(Axis.ZP, 78.05f);
        }

        if (!FishSettings.animEnabled) return;
        pose.rotateDegrees(Axis.XP, (float) FishSettings.animRotX);
        pose.rotateDegrees(Axis.YP, (float) FishSettings.animRotY);
        pose.rotateDegrees(Axis.ZP, (float) FishSettings.animRotZ);
        float s = 1f + (float) FishSettings.animItemScale;
        pose.scale(s, s, s);
    }

    @WrapOperation(method = "swingArm", require = 0, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void fishmod$animSwing(PoseStack instance, float xo, float yo, float zo, Operation<Void> original) {
        if (!FishSettings.animEnabled) {
            original.call(instance, xo, yo, zo);
            return;
        }
        instance.translate(xo * (float) FishSettings.animSwingX, yo * (float) FishSettings.animSwingY, zo * (float) FishSettings.animSwingZ);
    }

    @WrapWithCondition(method = "submitHandsWithItems", require = 0, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotateDegrees(Lcom/mojang/math/Axis;F)V"))
    private boolean fishmod$noHandMove(PoseStack instance, Axis axis, float degrees) {
        return !(FishSettings.animEnabled && FishSettings.animNoHandMove);
    }
}
