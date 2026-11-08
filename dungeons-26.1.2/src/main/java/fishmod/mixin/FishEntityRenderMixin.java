package fishmod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import fishmod.cosmetic.NametagStatsHolder;
import fishmod.utils.SkyblockLocation;
import fishmod.utils.config.values.DungeonSettings;
import fishmod.utils.config.values.FishSettings;
import fishmod.utils.config.values.VisualSettings;
import fishmod.utils.data.EntityTools;
import fishmod.utils.dungeon.DungeonRole;
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
public class FishEntityRenderMixin<T extends Entity, S extends EntityRenderState> {

    @org.spongepowered.asm.mixin.Unique
    private static final float[] IDENTITY_SCALE = {1.0f, 1.0f, 1.0f};

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    public void fishmod$adjustNameTag(T entity, S state, float tickProgress, CallbackInfo ci) {
        if (fishmod.utils.debug.FishDebug.skipExtract) return;
        try {
            if (DungeonSettings.hideBlazeTags && state.nameTag != null && SkyblockLocation.inDungeon()
                    && !(entity instanceof net.minecraft.world.entity.player.Player)) {
                String nameTagText = state.nameTag.getString();
                if (nameTagText.contains("Blaze")) {
                    state.nameTag = null;
                }
            }
            if (entity instanceof RemotePlayer player && DungeonSettings.roleNameTags && SkyblockLocation.inDungeon()) {
                if (DungeonRole.isTeammate(player)) {
                    state.nameTag = null;
                }
            }

            if (entity instanceof Player sized) {
                float[] sc = (FishSettings.playerSizeEnabled || FishSettings.forceOthersSizeEnabled || FishSettings.playerSizeShared)
                        ? fishmod.cosmetic.PlayerSize.scaleFor(sized) : IDENTITY_SCALE;
                ((fishmod.cosmetic.ScaleHolder) state).fishmod$setScale(sc[0], sc[1], sc[2]);
                if (sc[1] != 1.0f && state.nameTagAttachment != null) {
                    state.nameTagAttachment = state.nameTagAttachment.add(0, sized.getBbHeight() * (sc[1] - 1.0), 0);
                }
            }

            if (entity instanceof RemotePlayer teammate) {
                int outline = fishmod.features.dungeon.PlayerHighlight.outlineColor(teammate);
                if (outline != EntityRenderState.NO_OUTLINE) state.outlineColor = outline;
            }

            if (fishmod.features.fishing.FishingWorld.anyOn) {
                int fishGlow = fishmod.features.fishing.RareMobHighlight.glowColor(entity);
                if (fishGlow != EntityRenderState.NO_OUTLINE) state.outlineColor = fishGlow;
            }

            int dianaGlow = fishmod.features.diana.RareMobs.glowColor(entity);
            if (dianaGlow != EntityRenderState.NO_OUTLINE) state.outlineColor = dianaGlow;

            if (VisualSettings.hideMobFire) {
                state.displayFireAnimation = false;
            } else if (entity instanceof Player player && VisualSettings.hideBurningInf5) {
                if (EntityTools.isClientPlayer(player)) {
                    state.displayFireAnimation = false;
                }
            }

            if (entity instanceof Player p && EntityTools.isClientPlayer(p)
                    && FishSettings.nickPreviewEnabled && FishSettings.nickPreviewYOffset != 0.0
                    && state.nameTagAttachment != null) {
                state.nameTagAttachment = state.nameTagAttachment.add(0, FishSettings.nickPreviewYOffset, 0);
            }

            List<Component> statLines = null;
            if (FishSettings.nametagStatsEnabled && state.nameTag != null && entity instanceof Player pl) {
                // self only: other players cost an API call each
                if (EntityTools.isClientPlayer(pl)) {
                    String playerName = pl.getName().getString();
                    statLines = fishmod.features.NametagStats.linesFor(playerName);
                }
            }
            if (statLines != null && !statLines.isEmpty() && !FishSettings.nametagStatsAbove && state.nameTagAttachment != null) {
                state.nameTagAttachment = state.nameTagAttachment.add(0, statLines.size() * 10 * 0.025, 0);
            }
            ((NametagStatsHolder) state).fishmod$setNametagStats(statLines);
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("FishEntityRenderMixin.1", "nametag render-state adjust failed entity=" + entity.getType(), t);
        }
    }

    @Inject(
        method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
        at = @At("TAIL")
    )
    private void fishmod$extraNametagLines(S state, PoseStack poseStack, SubmitNodeCollector collector,
                                          CameraRenderState cameraRenderState, int baseOffset, CallbackInfo ci) {
        List<Component> lines = ((NametagStatsHolder) state).fishmod$getNametagStats();
        if (lines == null || lines.isEmpty()) return;
        boolean above = FishSettings.nametagStatsAbove;
        // Vanilla lifts the name one row when a score line sits under it, so go one row higher too.
        int y = above ? baseOffset - (state.scoreText != null ? 20 : 10) : baseOffset + 10;
        try {
            for (Component line : lines) {
                fishmod.compat.NameTagCompat.submit(collector, poseStack, state, y, line, cameraRenderState);
                y += above ? -10 : 10;
            }
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("FishEntityRenderMixin.2", "extra nametag lines submit failed lines=" + lines.size(), t);
        }
    }

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void fishmod$cullEntities(T entity, Frustum frustum, double camX, double camY, double camZ,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (fishmod.utils.debug.FishDebug.skipCull) return;
        try {
            if (fishmod.features.fishing.FishingWorld.anyOn && fishmod.features.fishing.HideHooks.shouldHide(entity)) {
                cir.setReturnValue(false);
                return;
            }
            if (fishmod.features.rift.RiftMobs.INSTANCE.shouldHideSlime(entity)) {
                cir.setReturnValue(false);
                return;
            }
            if (fishmod.features.mining.MiningBoxes.hideGhost(entity)) {
                cir.setReturnValue(false);
                return;
            }
            if (fishmod.features.dungeon.puzzles.odin.BlazeSolver.INSTANCE.shouldHideMob(entity)) {
                cir.setReturnValue(false);
                return;
            }
            if (entity instanceof Player && fishmod.features.dungeon.LeapAnnounce.hidePlayersNow()
                    && entity != Minecraft.getInstance().player) {
                cir.setReturnValue(false);
                return;
            }
            if (!VisualSettings.renderOptimizer) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || entity == mc.player) return;

            if (VisualSettings.hideNearbyPlayers && entity instanceof Player) {
                double r = VisualSettings.hidePlayersRadius;
                if (r > 0 && entity.distanceToSqr(mc.player) <= r * r) {
                    cir.setReturnValue(false);
                    return;
                }
            }

            if (VisualSettings.hideDeadMobs && entity instanceof LivingEntity le
                    && (le.isDeadOrDying() || le.getHealth() <= 0f)) {
                cir.setReturnValue(false);
                return;
            }

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
        } catch (Throwable t) {
            fishmod.utils.debug.FishDiag.fail("FishEntityRenderMixin.3", "entity cull check failed entity=" + entity.getType(), t);
        }
    }
}
