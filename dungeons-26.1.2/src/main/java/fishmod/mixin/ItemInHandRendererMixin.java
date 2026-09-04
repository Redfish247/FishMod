package fishmod.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.config.values.Visual;
import fishmod.utils.data.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Animations module + Sword Blocking + the Render-Optimizer "No Swing". */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Shadow private ItemStack mainHandItem;
    @Shadow private float oMainHandHeight;
    @Shadow private float mainHandHeight;
    @Shadow private float oOffHandHeight;
    @Shadow private float offHandHeight;

    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER))
    private void fishmod$animPre(AbstractClientPlayer player, float f, float g, InteractionHand hand, float attack,
                                ItemStack itemStack, float inverseArmHeight, PoseStack pose,
                                SubmitNodeCollector col, int light, CallbackInfo ci) {
        if (itemStack.isEmpty()) return;

        if (FishSettings.animEnabled) {
            float sign = hand == InteractionHand.MAIN_HAND ? 1f : -1f;
            pose.translate((float) FishSettings.animX * sign, (float) FishSettings.animY, (float) FishSettings.animZ);
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean fishmod$isSwordBlocking(AbstractClientPlayer player, ItemStack stack, InteractionHand hand) {
        return FishSettings.swordBlockingEnabled
                && hand == InteractionHand.MAIN_HAND
                && !stack.isEmpty()
                && stack.is(ItemTags.SWORDS)
                && Minecraft.getInstance().options.keyUse.isDown()
                && !player.isUsingItem();
    }

    @ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), ordinal = 2, argsOnly = true)
    private float fishmod$noSwing(float attack) {
        if (Visual.renderOptimizer && Visual.noSwingAnimation) {
            if (!Visual.noSwingTerminatorOnly || "TERMINATOR".equals(ItemUtil.getId(mainHandItem))) return 1f;
        }
        return attack;
    }

    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
    private void fishmod$animRotScale(AbstractClientPlayer player, float f, float g, InteractionHand hand, float attack,
                                     ItemStack itemStack, float inverseArmHeight, PoseStack pose,
                                     SubmitNodeCollector col, int light, CallbackInfo ci) {
        // Injected after vanilla positioning so the block pose stacks on the real held-item pose
        if (fishmod$isSwordBlocking(player, itemStack, hand)) {
            // Values from vanilla 1.8 ItemRenderer.transformFirstPersonItem() blocking branch
            pose.translate(-0.14142136f, 0.08f, 0.14142136f);
            pose.mulPose(Axis.XP.rotationDegrees(-102.25f));
            pose.mulPose(Axis.YP.rotationDegrees(13.365f));
            pose.mulPose(Axis.ZP.rotationDegrees(78.05f));
        }

        if (!FishSettings.animEnabled) return;
        pose.mulPose(Axis.XP.rotationDegrees((float) FishSettings.animRotX));
        pose.mulPose(Axis.YP.rotationDegrees((float) FishSettings.animRotY));
        pose.mulPose(Axis.ZP.rotationDegrees((float) FishSettings.animRotZ));
        float s = 1f + (float) FishSettings.animItemScale;
        pose.scale(s, s, s);
    }

    @WrapOperation(method = "swingArm", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void fishmod$animSwing(PoseStack instance, float xo, float yo, float zo, Operation<Void> original) {
        if (!FishSettings.animEnabled) {
            original.call(instance, xo, yo, zo);
            return;
        }
        instance.translate(xo * (float) FishSettings.animSwingX, yo * (float) FishSettings.animSwingY, zo * (float) FishSettings.animSwingZ);
    }

    @Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("HEAD"), cancellable = true)
    private void fishmod$noEquip(ItemStack current, ItemStack expected, CallbackInfoReturnable<Boolean> cir) {
        if (FishSettings.animEnabled && FishSettings.animNoEquip) cir.setReturnValue(true);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void fishmod$noEquipHeights(CallbackInfo ci) {
        if (FishSettings.animEnabled && FishSettings.animNoEquip) {
            oMainHandHeight = 1f;
            mainHandHeight = 1f;
            oOffHandHeight = 1f;
            offHandHeight = 1f;
        }
    }

    @WrapWithCondition(method = "renderHandsWithItems", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V"))
    private boolean fishmod$noHandMove(PoseStack instance, Quaternionfc by) {
        return !(FishSettings.animEnabled && FishSettings.animNoHandMove);
    }
}
