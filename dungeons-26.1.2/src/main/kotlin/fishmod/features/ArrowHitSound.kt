package fishmod.features

import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import fishmod.utils.sound.SoundManager
import net.minecraft.sounds.SoundEvents

/**
 * Plays a configurable cue when one of *your* arrows lands a hit on a living entity (mob or player),
 * ported from NoammAddons' ArrowHitSound.
 *
 * The trigger is [onArrowHitMob], driven from `AbstractArrowMixin#onHitEntity` — the vanilla
 * `entity.arrow.hit` sound also fires on block hits, so a sound-event match can't tell "hit a mob"
 * from "hit a wall". The [Events.ON_SOUND] handler here only exists to optionally mute the vanilla
 * hit tick.
 */
object ArrowHitSound {

    private val ARROW_HIT_IDS = setOf(
        SoundEvents.ARROW_HIT_PLAYER.location,
        SoundEvents.ARROW_HIT.location,
    )

    @JvmStatic
    fun init() {
        Events.ON_SOUND.register { event, _, _ ->
            FishSettings.arrowHitSoundEnabled &&
                FishSettings.arrowHitSoundSuppress &&
                event.location in ARROW_HIT_IDS
        }
    }

    /** Called from the mixin when the local player's arrow hits a LivingEntity. */
    @JvmStatic
    fun onArrowHitMob() {
        if (!FishSettings.arrowHitSoundEnabled) return
        SoundManager.play(
            SoundManager.preset(FishSettings.arrowHitSoundName),
            FishSettings.arrowHitSoundVolume.coerceIn(0, 100) / 100f,
            FishSettings.arrowHitSoundPitch.toFloat().coerceIn(0f, 2f),
            "arrowHit", 40,
        )
    }
}
