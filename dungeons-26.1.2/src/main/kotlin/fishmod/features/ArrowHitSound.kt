package fishmod.features

import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import fishmod.utils.sound.SoundManager
import net.minecraft.client.Minecraft
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB

/**
 * Plays a configurable cue, in your ear, when an arrow lands a hit on a living entity (ported from
 * NoammAddons' ArrowHitSound).
 *
 * Trigger is the server-sent `entity.arrow.hit` sound packet (reliable — the client's own
 * `onHitEntity` fires only ~half the time on Hypixel), gated to "there's a mob at the sound's
 * position" so block hits don't count. Driven from [onArrowHitSoundAt] in ClientPlayNetworkHandlerMixin.
 */
object ArrowHitSound {

    private val ARROW_HIT_IDS = setOf(
        SoundEvents.ARROW_HIT_PLAYER.location,
        SoundEvents.ARROW_HIT.location,
    )

    @JvmStatic
    fun init() {
        // Optionally mute the vanilla hit tick.
        Events.ON_SOUND.register { event, _, _ ->
            FishSettings.arrowHitSoundEnabled &&
                FishSettings.arrowHitSoundSuppress &&
                event.location in ARROW_HIT_IDS
        }
    }

    /** Called from the sound-packet mixin for every positioned sound. */
    @JvmStatic
    fun onArrowHitSoundAt(soundId: net.minecraft.resources.Identifier, x: Double, y: Double, z: Double) {
        if (!FishSettings.arrowHitSoundEnabled) return
        if (soundId != SoundEvents.ARROW_HIT.location && soundId != SoundEvents.ARROW_HIT_PLAYER.location) return

        val level = Minecraft.getInstance().level ?: return
        val box = AABB(x - 2.0, y - 2.0, z - 2.0, x + 2.0, y + 2.0, z + 2.0)
        val hitMob = level.getEntities(null as Entity?, box) { e ->
            e is LivingEntity && e !is Player && e.isAlive
        }.isNotEmpty()
        if (!hitMob) return

        SoundManager.play2D(
            SoundManager.preset(FishSettings.arrowHitSoundName),
            FishSettings.arrowHitSoundVolume.coerceIn(0, 500) / 100f,
            FishSettings.arrowHitSoundPitch.toFloat().coerceIn(0f, 2f),
            "arrowHit", 40,
        )
    }
}
