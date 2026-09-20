package fishmod.utils.sound

import fishmod.shaded.practicalconfig.data.SoundData
import fishmod.utils.Misc
import fishmod.utils.config.values.FishSettings
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents

object SoundManager {

    private val enabled: Boolean get() = FishSettings.soundMasterEnabled
    private val masterVol: Float get() = FishSettings.soundMasterVolume.coerceIn(0, 100) / 100f

    private val PRESETS: Map<String, Any> = linkedMapOf(
        "Note: Pling" to SoundEvents.NOTE_BLOCK_PLING,
        "Note: Harp" to SoundEvents.NOTE_BLOCK_HARP,
        "Note: Bell" to SoundEvents.NOTE_BLOCK_BELL,
        "Note: Bass" to SoundEvents.NOTE_BLOCK_BASS,
        "Blaze Hit" to SoundEvents.BLAZE_HURT,
        "Fire Ignite" to SoundEvents.FLINTANDSTEEL_USE,
        "Orb Pickup" to SoundEvents.EXPERIENCE_ORB_PICKUP,
        "Item Break" to SoundEvents.ITEM_BREAK,
        "Guardian Hit" to SoundEvents.GUARDIAN_HURT,
        "Anvil Land" to SoundEvents.ANVIL_LAND,
        "Amethyst" to SoundEvents.AMETHYST_BLOCK_CHIME,
        "Arrow Hit" to SoundEvents.ARROW_HIT,
        "Arrow Hit Player" to SoundEvents.ARROW_HIT_PLAYER,
        "Bow Shoot" to SoundEvents.ARROW_SHOOT,
        "Crit" to SoundEvents.PLAYER_ATTACK_CRIT,
        "Levelup" to SoundEvents.PLAYER_LEVELUP,
        "Villager Yes" to SoundEvents.VILLAGER_YES,
        "Dispenser" to SoundEvents.DISPENSER_DISPENSE,
        "Totem" to SoundEvents.TOTEM_USE,
    )

    @JvmStatic
    fun presetNames(): Array<String> = PRESETS.keys.toTypedArray()

    val allSoundIds: List<String> by lazy {
        BuiltInRegistries.SOUND_EVENT.keySet().map { it.toString() }.sorted()
    }

    val shortlist: List<String> = listOf(
        "minecraft:block.note_block.pling", "minecraft:block.note_block.harp",
        "minecraft:block.note_block.bell", "minecraft:block.note_block.bass",
        "minecraft:entity.arrow.hit", "minecraft:entity.arrow.hit_player",
        "minecraft:entity.experience_orb.pickup", "minecraft:entity.player.levelup",
        "minecraft:block.anvil.land", "minecraft:entity.item.break",
    )

    private fun resolve(v: Any?): SoundEvent = when (v) {
        is SoundEvent -> v
        is net.minecraft.core.Holder<*> -> v.value() as SoundEvent
        else -> SoundEvents.NOTE_BLOCK_PLING.value()
    }

    @JvmStatic
    fun preset(name: String?): SoundEvent {
        if (name.isNullOrBlank()) return SoundEvents.NOTE_BLOCK_PLING.value()
        PRESETS[name]?.let { return resolve(it) }
        val id = Identifier.tryParse(if (':' in name) name else "minecraft:$name")
        val fromRegistry: SoundEvent? =
            id?.let { BuiltInRegistries.SOUND_EVENT.getOptional(it).orElse(null) }
        return fromRegistry ?: SoundEvents.NOTE_BLOCK_PLING.value()
    }

    private val lastPlayed = java.util.concurrent.ConcurrentHashMap<String, Long>()

    @JvmStatic
    @JvmOverloads
    fun play(
        sound: SoundEvent,
        volume: Float = 1f,
        pitch: Float = 1f,
        key: String? = null,
        debounceMs: Long = 0L,
    ): Boolean {
        if (!enabled) return false
        val v = volume * masterVol
        if (v <= 0f) return false
        if (key != null && debounceMs > 0L) {
            val now = System.currentTimeMillis()
            val prev = lastPlayed[key]
            if (prev != null && now - prev < debounceMs) return false
            lastPlayed[key] = now
        }
        Misc.sendSound(sound, v, pitch)
        return true
    }

    @JvmStatic
    @JvmOverloads
    fun play(data: SoundData, key: String? = null, debounceMs: Long = 0L): Boolean =
        play(SoundEvent.createVariableRangeEvent(data.sound), data.volume, data.pitch, key, debounceMs)

    @JvmStatic
    @JvmOverloads
    fun play2D(
        sound: SoundEvent,
        volume: Float = 1f,
        pitch: Float = 1f,
        key: String? = null,
        debounceMs: Long = 0L,
    ): Boolean {
        if (!enabled) return false
        val v = volume * masterVol
        if (v <= 0f) return false
        if (key != null && debounceMs > 0L) {
            val now = System.currentTimeMillis()
            val prev = lastPlayed[key]
            if (prev != null && now - prev < debounceMs) return false
            lastPlayed[key] = now
        }
        Misc.sendSound2D(sound, v, pitch)
        return true
    }

    @JvmStatic
    @JvmOverloads
    fun ping(key: String? = null, debounceMs: Long = 0L): Boolean =
        play(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 2f, key, debounceMs)

    @JvmStatic
    @JvmOverloads
    fun alert(key: String? = null, debounceMs: Long = 0L): Boolean =
        play(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 0.5f, key, debounceMs)

    @JvmStatic
    fun reset() = lastPlayed.clear()
}
