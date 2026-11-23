package fishmod.utils.sound

import fishmod.utils.config.values.FishSettings
import net.minecraft.client.resources.sounds.SoundInstance

// Sound Manager: per sound-id volume rules (0 = muted), stored as "id=pct;id=pct" in FishSettings.soundRules.
object SoundRules {
    @JvmField var editing: String = ""
    private var parsedFrom: String? = null
    private var parsed: Map<String, Int> = emptyMap()

    private fun map(): Map<String, Int> {
        val raw = FishSettings.soundRules
        if (raw === parsedFrom || raw == parsedFrom) return parsed
        val m = LinkedHashMap<String, Int>()
        for (part in raw.split(';')) {
            val i = part.lastIndexOf('=')
            if (i <= 0) continue
            val v = part.substring(i + 1).toIntOrNull() ?: continue
            m[part.substring(0, i)] = v.coerceIn(0, 500)
        }
        parsedFrom = raw; parsed = m
        return m
    }

    fun rules(): Map<String, Int> = map()

    fun volumeFor(id: String): Int? = map()[id]

    @JvmStatic fun multiplier(instance: SoundInstance): Float {
        val m = map()
        if (m.isEmpty()) return 1f
        return (m[instance.identifier.toString()] ?: return 1f) / 100f
    }

    @JvmStatic fun muted(instance: SoundInstance): Boolean {
        val m = map()
        if (m.isEmpty()) return false
        return m[instance.identifier.toString()] == 0
    }

    fun set(id: String, pct: Int?) {
        if (id.isBlank()) return
        val m = LinkedHashMap(map())
        if (pct == null) m.remove(id) else m[id] = pct.coerceIn(0, 500)
        FishSettings.soundRules = m.entries.joinToString(";") { "${it.key}=${it.value}" }
    }
}
