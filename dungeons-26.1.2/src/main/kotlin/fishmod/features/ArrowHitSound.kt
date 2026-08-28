package fishmod.features

import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import fishmod.utils.sound.SoundManager
import net.minecraft.sounds.SoundEvents

/**
 * Plays a configurable cue when one of your arrows lands a hit on a player/mob (ported from
 * NoammAddons' ArrowHitSound — the `entity.arrow.hit_player` sound). Optionally suppresses the
 * vanilla hit tick.
 */
object ArrowHitSound {

    // Compare by id, not identity — the sound-engine instance is not the SoundEvents.* constant.
    private val ARROW_HITS = setOf(
        SoundEvents.ARROW_HIT_PLAYER.location,
        SoundEvents.ARROW_HIT.location,
    )

    @JvmStatic
    fun init() {
        Events.ON_SOUND.register { event, _, _ ->
            if (!FishSettings.arrowHitSoundEnabled) return@register false
            if (event.location !in ARROW_HITS) return@register false
            SoundManager.play(
                SoundManager.preset(FishSettings.arrowHitSoundName),
                FishSettings.arrowHitSoundVolume.coerceIn(0, 100) / 100f,
                FishSettings.arrowHitSoundPitch.toFloat().coerceIn(0f, 2f),
                "arrowHit", 40,
            )
            FishSettings.arrowHitSoundSuppress
        }
    }
}
