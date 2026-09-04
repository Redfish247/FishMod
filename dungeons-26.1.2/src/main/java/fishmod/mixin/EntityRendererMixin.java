package fishmod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import fishmod.cosmetic.NametagStatsHolder;
import fishmod.utils.Location;
import fishmod.utils.config.values.Dungeons;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.config.values.Visual;
import fishmod.utils.data.EntityUtil;
import fishmod.utils.dungeon.DungeonClass;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
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

import java.util.List;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    public void hideFire(T entity, S state, float tickProgress, CallbackInfo ci) {

        if (Dungeons.hideBlazeNameTag && state.nameTag != null) {
            String nameTagText = state.nameTag.getString();
            if (nameTagText.contains("Blaze")) {
                state.nameTag = null;
            }
        }
        if (entity instanceof RemotePlayer player && Dungeons.renderClassName && Location.inDungeon()) {
            if (DungeonClass.isTeammate(player)) {
                state.nameTag = null;
            }
        }

        // stash render size for PlayerEntityRendererScaleMixin.scale(); raise the nametag when scaled taller (pivot at feet)
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

        // nametag Y offset only (text size is fixed by ImmediatelyFast)
        if (entity instanceof Player p && EntityUtil.isClientPlayer(p)
                && FishSettings.nickPreviewEnabled && FishSettings.nickPreviewYOffset != 0.0
                && state.nameTagAttachment != null) {
            state.nameTagAttachment = state.nameTagAttachment.add(0, FishSettings.nickPreviewYOffset, 0);
        }

        // stat lines drawn under the nametag; fishmod$extraNametagLines reads these back
        List<Component> statLines = null;
        if (FishSettings.nametagStatsEnabled && state.nameTag != null && entity instanceof Player pl) {
            boolean self = EntityUtil.isClientPlayer(pl);
            if (!self || FishSettings.nametagStatsShowSelf) {
                String playerName = pl.getName().getString();
                statLines = fishmod.features.NametagStats.linesFor(playerName);
            }
        }
        ((NametagStatsHolder) state).fishmod$setNametagStats(statLines);
    }

    // AvatarRenderer overrides the 4-arg submitNameDisplay without super, so target the inherited 5-arg variant
    @Inject(
        method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
        at = @At("TAIL")
    )
    private void fishmod$extraNametagLines(S state, PoseStack poseStack, SubmitNodeCollector collector,
                                          CameraRenderState cameraRenderState, int baseOffset, CallbackInfo ci) {
        List<Component> lines = ((NametagStatsHolder) state).fishmod$getNametagStats();
        if (lines == null || lines.isEmpty()) return;
        // negative y = up; stack the stat lines above the nametag
        int y = baseOffset - 10;
        for (Component line : lines) {
            collector.submitNameTag(poseStack, state.nameTagAttachment, y, line,
                !state.isDiscrete, state.lightCoords, state.distanceToCameraSq, cameraRenderState);
            y -= 10;
        }
    }

    // shouldRender is cancellable, so returning false drops the entity from the render pass
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

        // hide mobs in their death animation, and optionally the nametag armor stand riding a dying mob
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
