package fishmod.mixin;

import fishmod.utils.Location;
import fishmod.utils.config.values.Dungeons;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.config.values.Visual;
import fishmod.utils.data.EntityUtil;
import fishmod.utils.dungeon.DungeonClass;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    public void hideFire(T entity, S state, float tickProgress, CallbackInfo ci) {

        if (Dungeons.hideBlazeNameTag && state.nameTag != null) {
            if (state.nameTag.getString().contains("Blaze")) {
                state.nameTag = null;
            }

        }
        if (entity instanceof RemotePlayer player && Dungeons.renderClassName && Location.inDungeon()) {
            if (DungeonClass.isTeammate(player)) {
                state.nameTag = null;
            }
        }

        // Stash render size for PlayerEntityRendererScaleMixin.scale(); raise the nametag when scaled
        // taller so it doesn't clip through a Y-scaled model (pivot is at the feet).
        if (entity instanceof Player sized) {
            float[] sc = fishmod.cosmetic.PlayerSize.scaleFor(sized);
            ((fishmod.cosmetic.ScaleHolder) state).fishmod$setScale(sc[0], sc[1], sc[2]);
            if (sc[1] != 1.0f && state.nameTagAttachment != null) {
                state.nameTagAttachment = state.nameTagAttachment.add(0, sized.getBbHeight() * (sc[1] - 1.0), 0);
            }
        }

        if (Visual.hideEntityFire) {
            state.displayFireAnimation = false;
        } else if (entity instanceof Player player && Visual.hideFireInf5) {
            if (EntityUtil.isClientPlayer(player)) {
                state.displayFireAnimation = false;
            }
        }

        // Own above-head nametag Y offset (position lives in render state; text size is fixed by ImmediatelyFast).
        if (entity instanceof Player p && EntityUtil.isClientPlayer(p)
                && FishSettings.nickPreviewEnabled && FishSettings.nickPreviewYOffset != 0.0
                && state.nameTagAttachment != null) {
            state.nameTagAttachment = state.nameTagAttachment.add(0, FishSettings.nickPreviewYOffset, 0);
        }
    }

    // Visual.kt cull flags: hide nearby other players / dead mobs entirely (blade "hidePlayersInRange",
    // "hideDeadEntities"). shouldRender is cancellable so we can drop the entity from the render pass.
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void fishmod$cullEntities(T entity, Frustum frustum, double camX, double camY, double camZ,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (!Visual.renderOptimizer) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || entity == mc.player) return;

        if (Visual.hidePlayersInRange && entity instanceof Player) {
            double r = Visual.hidePlayerRange;
            if (r > 0 && entity.distanceToSqr(mc.player) <= r * r) {
                cir.setReturnValue(false);
                return;
            }
        }

        if (Visual.hideDeadEntities && entity instanceof LivingEntity le
                && (le.isDeadOrDying() || le.getHealth() <= 0f)) {
            cir.setReturnValue(false);
            return;
        }

        // Odin Render Optimizer: hide mobs playing their death animation, and (optionally) the
        // nametag armor stand riding a dying mob.
        if (fishmod.features.RenderOptimizer.hideDeathAnimation()) {
            if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand) {
                if (fishmod.features.RenderOptimizer.hideDyingArmorStands()
                        && entity.getVehicle() instanceof LivingEntity mount && mount.deathTime > 0) {
                    cir.setReturnValue(false);
                }
            } else if (entity instanceof LivingEntity dying && dying.deathTime > 0) {
                cir.setReturnValue(false);
            }
        }
    }
}
