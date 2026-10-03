package fishmod.features

import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import fishmod.utils.sound.SoundManager
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity

// Arrow Hit Sound for melee: plays on every left-click hit on a living entity
object MeleeHitSound {

    private var lastHitMs = 0L

    fun init() {
        AttackEntityCallback.EVENT.register { player, level, _, entity, _ ->
            if (level.isClientSide && FishSettings.meleeHitSoundEnabled && entity is LivingEntity) {
                lastHitMs = System.currentTimeMillis()
                FishDiag.guard("MeleeHitSound.1", "melee hit sound '${FishSettings.meleeHitSoundName}' failed") {
                    SoundManager.play2D(
                        SoundManager.preset(FishSettings.meleeHitSoundName),
                        FishSettings.meleeHitSoundVolume.coerceIn(0, 500) / 100f,
                        FishSettings.meleeHitSoundPitch.toFloat().coerceIn(0f, 2f),
                        "meleeHit", 40,
                    )
                }
            }
            InteractionResult.PASS
        }
    }

    // Vanilla player.attack.* sounds that land right after our own hit
    @JvmStatic
    fun onLocalSound(instance: SoundInstance): Boolean {
        if (!FishSettings.meleeHitSoundEnabled || !FishSettings.meleeHitSoundSuppress) return false
        if (!instance.identifier.path.startsWith("entity.player.attack.")) return false
        return System.currentTimeMillis() - lastHitMs < 1000
    }
}
