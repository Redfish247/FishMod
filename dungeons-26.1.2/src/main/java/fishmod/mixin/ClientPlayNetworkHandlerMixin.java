package fishmod.mixin;

import fishmod.utils.Misc;
import fishmod.utils.config.values.ExtraOptions;
import fishmod.utils.debug.Debug;
import fishmod.utils.events.Events;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPlayNetworkHandlerMixin {


    @Shadow
    private ClientLevel level;

    @Inject(method = "applyPlayerInfoUpdate", at = @At(value = "TAIL"))
    private void onPlayerList(ClientboundPlayerInfoUpdatePacket.Action action, ClientboundPlayerInfoUpdatePacket.Entry receivedEntry, PlayerInfo currentEntry, CallbackInfo ci) {
        Events.ON_PLAYER_ENTRY.invoke(playerListEvent -> playerListEvent.onNewPlayerEntry(receivedEntry));
    }

    @Inject(method = "handleAddEntity", at = @At("HEAD"), cancellable = true)
    private void fishmod$renderOptimizerHideEntities(net.minecraft.network.protocol.game.ClientboundAddEntityPacket packet, CallbackInfo ci) {
        if (!fishmod.utils.config.values.Visual.renderOptimizer) return;
        net.minecraft.world.entity.EntityType<?> t = packet.getType();
        if ((fishmod.utils.config.values.Visual.roHideFallingBlocks && t == net.minecraft.world.entity.EntityType.FALLING_BLOCK)
                || (fishmod.utils.config.values.Visual.roHideLightning && t == net.minecraft.world.entity.EntityType.LIGHTNING_BOLT)
                || (fishmod.utils.config.values.Visual.roHideExperienceOrbs && t == net.minecraft.world.entity.EntityType.EXPERIENCE_ORB)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleAddEntity", at = @At("TAIL"))
    private void fishmod$onEntitySpawned(net.minecraft.network.protocol.game.ClientboundAddEntityPacket packet, CallbackInfo ci, @Local Entity entity) {
        if (entity == null) return;
        Events.ON_ENTITY_SPAWNED.invoke(e -> e.onEntity(entity, this.level));
    }

    @Inject(method = "handleSetPlayerTeamPacket", at = @At(value = "TAIL"))
    private void onTeam(ClientboundSetPlayerTeamPacket packet, CallbackInfo ci, @Local PlayerTeam team) {
        if (team == null) return;
        String teamStr = fishmod.utils.HypixelApi.STRIP_COLOR.matcher(
                team.getPlayerPrefix().getString() + team.getPlayerSuffix().getString()).replaceAll("");
        Events.ON_TEAM.invoke(scoreBoardEvent -> scoreBoardEvent.onTeam(teamStr));
    }

    @Inject(method = "handleSoundEvent", at = @At(value = "HEAD"), cancellable = true)
    private void onSound(ClientboundSoundPacket packet, CallbackInfo ci) {
        // handleSoundEvent fires twice — netty thread then main thread; only dispatch on the main pass
        if (!Minecraft.getInstance().isSameThread()) return;
        float volume = packet.getVolume();
        float pitch = packet.getPitch();
        SoundEvent event = packet.getSound().value();

        if (ExtraOptions.disableAbilityCooldownSound && pitch == 0.0 && volume == 8.0 && event == SoundEvents.ENDERMAN_TELEPORT) {
            ci.cancel();
        }

        if (Debug.sendSound) {
            Misc.addChatMessage(Component.literal("Sound: " + event.location() + " Volume: " + volume + " Pitch: " + pitch));
        }

        if (Events.ON_SOUND.invoke(soundEvent -> soundEvent.onSound(event, volume, pitch))) {
            ci.cancel();
        }
    }

    @Inject(method = "handleSoundEntityEvent", at = @At(value = "HEAD"), cancellable = true)
    private void onLocationSound(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) return;
        float volume = packet.getVolume();
        float pitch = packet.getPitch();
        SoundEvent event = packet.getSound().value();

        if (Debug.sendSound) {
            Misc.addChatMessage(Component.literal("Sound: " + event.location() + " Volume: " + volume + " Pitch: " + pitch + "entitySeed: " + packet.getId()));
        }

        if (Events.ON_SOUND.invoke(soundEvent -> soundEvent.onSound(event, volume, pitch))) {
            ci.cancel();
        }
    }

    @Inject(method = "handleParticleEvent", at = @At("HEAD"))
    private void onParticle(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        Events.ON_PARTICLE.invoke(particleEvent -> particleEvent.onParticle(packet));
    }

    @WrapOperation(method = "handleBundlePacket", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"))
    private void apply(Packet<?> packet, PacketListener listener, Operation<Void> original) {
        if (packet instanceof ClientboundSystemChatPacket(Component content, boolean overlay) && !overlay) {
            if (Events.ON_GAME_MESSAGE.invoke(gameMessageEvent -> gameMessageEvent.onGameMessage(content))) {
                return;
            }
        }

        // Not a double-fire with ClientConnectionMixin#channelRead0: that one sees the ClientboundBundlePacket
        // wrapper itself on the netty thread, never these unwrapped sub-packets. This site fires once per
        // sub-packet, on the main thread, right before it is handed to its real listener — the safe place for
        // handlers that touch client state. A standalone (non-bundled) packet only ever goes through the other site.
        Events.ON_PACKET.invoke(packetEvent -> packetEvent.onPacket(packet));
        original.call(packet, listener);
    }


    @Inject(method = "setTitleText", at = @At("HEAD"), cancellable = true)
    private void onTitle(ClientboundSetTitleTextPacket packet, CallbackInfo ci) {
        Component text = packet.text();
        if (text != null) {
            fishmod.features.dungeon.SimonSaysTracker.onTitle(text.getString());

            if (fishmod.features.dungeon.f7.TitleHider.shouldHideTitle(text) || fishmod.features.dungeon.f7.DeviceNotifier.disableTitles(text)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "handleSystemChat", at = @At("HEAD"), cancellable = true)
    private void onGameMessage(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (!packet.overlay()) {
            if (Events.ON_GAME_MESSAGE.invoke(gameMessageEvent -> gameMessageEvent.onGameMessage(packet.content()))) {
                ci.cancel();
            }
        }
    }
}