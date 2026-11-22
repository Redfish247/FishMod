package fishmod.cosmetic

import fishmod.utils.config.values.FishSettings
import net.minecraft.network.chat.Component
import net.minecraft.util.Util
import java.util.function.Supplier

class NameDecorCache {
    private class Entry(val input: Component, val output: Component, val at: Long)

    private val entries = HashMap<Any, Entry>()

    init { fishmod.utils.events.FishEvents.worldChange.register { entries.clear(); false } }

    fun get(key: Any, input: Component, compute: Supplier<Component>): Component {
        val now = Util.getMillis()
        val ttl = if (FishSettings.prestigeColorsAnimated) 100L else 500L
        val e = entries[key]
        if (e != null && now - e.at < ttl && (e.input === input || e.input == input)) return e.output
        val out = compute.get()
        if (entries.size > 512) entries.clear()
        entries[key] = Entry(input, out, now)
        return out
    }

    companion object {
        @JvmField val NAMETAG = NameDecorCache()
        @JvmField val TAB = NameDecorCache()
    }
}
