package fishmod.features

import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.Events
import fishmod.utils.sound.SoundManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity

// Arrow Hit Sound for melee: plays only when the server registers the hit
object MeleeHitSound {

    private const val CONFIRM_MS = 600L
    private var lastHitMs = 0L
    private var target: LivingEntity? = null
    private var swingMs = 0L
    private var lastHurt = 0
    // Damage packets arrive off-thread; ferocity keeps hurtTime pinned so packets are the reliable signal
    @Volatile private var damagedId = -1
    @Volatile private var damagedMs = 0L

    fun init() {
        AttackEntityCallback.EVENT.register { player, level, _, entity, _ ->
            if (level.isClientSide) lastHitMs = System.currentTimeMillis()
            if (level.isClientSide && FishSettings.meleeHitSoundEnabled && entity is LivingEntity) {
                target = entity
                lastHurt = entity.hurtTime
                swingMs = System.currentTimeMillis()
            }
            InteractionResult.PASS
        }
        Events.ON_PACKET.register { packet ->
            if (FishSettings.meleeHitSoundEnabled) when (packet) {
                is ClientboundDamageEventPacket -> { damagedId = packet.entityId; damagedMs = System.currentTimeMillis() }
                is ClientboundHurtAnimationPacket -> { damagedId = packet.id; damagedMs = System.currentTimeMillis() }
            }
            false
        }
        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            val t = target ?: return@register
            val now = System.currentTimeMillis()
            val packetHit = damagedId == t.id && damagedMs >= swingMs
            // A new hit resets hurtTime upward; otherwise it only counts down
            val rose = t.hurtTime > lastHurt
            lastHurt = t.hurtTime
            // A one-shot (e.g. ferocity) can remove the mob before the flash is seen
            if (packetHit || rose || (t.isDeadOrDying && now - swingMs < CONFIRM_MS)) {
                target = null
                FishDiag.guard("MeleeHitSound.1", "melee hit sound '${FishSettings.meleeHitSoundName}' failed") {
                    SoundManager.play2D(
                        SoundManager.preset(FishSettings.meleeHitSoundName),
                        FishSettings.meleeHitSoundVolume.coerceIn(0, 500) / 100f,
                        FishSettings.meleeHitSoundPitch.toFloat().coerceIn(0f, 2f),
                        "meleeHit", 40,
                    )
                }
            } else if (now - swingMs > CONFIRM_MS || t.isRemoved) target = null
        }
    }

    // Vanilla player.attack.* sounds that land right after one of our swings
    @JvmStatic
    fun onLocalSound(instance: SoundInstance): Boolean {
        if (!FishSettings.meleeHitSoundEnabled || !FishSettings.meleeHitSoundSuppress) return false
        if (!instance.identifier.path.startsWith("entity.player.attack.")) return false
        return System.currentTimeMillis() - lastHitMs < 1000
    }
}
