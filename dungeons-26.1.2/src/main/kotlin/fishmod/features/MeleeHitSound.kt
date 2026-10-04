package fishmod.features

import fishmod.utils.config.values.FishSettings
import fishmod.utils.debug.FishDiag
import fishmod.utils.sound.SoundManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand

// Arrow Hit Sound for melee: plays only when the server registers the hit
object MeleeHitSound {

    private const val CONFIRM_MS = 600L
    private var lastHitMs = 0L
    private var target: LivingEntity? = null
    private var swingMs = 0L
    private var lastHurt = 0
    private val seenSplashes = HashSet<Int>()
    // Hypixel's damage numbers, e.g. "1,234" or "✧12,345✧"
    private val SPLASH = Regex("""^\D{0,3}[\d,.]+[kKmM]?\D{0,3}$""")

    fun init() {
        AttackEntityCallback.EVENT.register { player, level, _, entity, _ ->
            if (level.isClientSide) lastHitMs = System.currentTimeMillis()
            if (level.isClientSide && FishSettings.meleeHitSoundEnabled && entity is LivingEntity && !entity.isDeadOrDying) {
                target = entity
                lastHurt = entity.hurtTime
                // Numbers already floating belong to earlier swings
                level.getEntitiesOfClass(ArmorStand::class.java, entity.boundingBox.inflate(3.0)).forEach { seenSplashes.add(it.id) }
                swingMs = System.currentTimeMillis()
            }
            InteractionResult.PASS
        }
        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            val t = target ?: return@register
            val now = System.currentTimeMillis()
            // A new hit resets hurtTime upward; otherwise it only counts down
            val rose = t.hurtTime > lastHurt
            lastHurt = t.hurtTime
            // Hypixel sends damage packets even for blocked swings, so only a restarted hurt flash counts
            if (rose || damageSplash(t)) {
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

    // A fresh damage number next to the target means the hit landed, even when ferocity keeps the hurt flash pinned
    private fun damageSplash(t: LivingEntity): Boolean {
        val level = t.level()
        var found = false
        for (e in level.getEntitiesOfClass(ArmorStand::class.java, t.boundingBox.inflate(3.0))) {
            if (e.tickCount > 10 || e.id in seenSplashes) continue
            val name = e.customName?.string?.replace(Regex("§."), "") ?: continue
            if (SPLASH.matches(name.trim())) { seenSplashes.add(e.id); found = true }
        }
        if (seenSplashes.size > 256) seenSplashes.clear()
        return found
    }

    // Vanilla player.attack.* sounds that land right after one of our swings
    @JvmStatic
    fun onLocalSound(instance: SoundInstance): Boolean {
        if (!FishSettings.meleeHitSoundEnabled || !FishSettings.meleeHitSoundSuppress) return false
        if (!instance.identifier.path.startsWith("entity.player.attack.")) return false
        return System.currentTimeMillis() - lastHitMs < 1000
    }
}
