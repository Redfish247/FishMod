package fishmod.utils.events

import fishmod.utils.TextStrip
import net.minecraft.network.chat.Component

// Last chat Component's string and colour-stripped string, computed once for all chat listeners.
object ChatText {
    private class Entry(val c: Component, val raw: String) {
        val plain: String by lazy(LazyThreadSafetyMode.NONE) { TextStrip.plain(raw) }
    }

    @Volatile private var last: Entry? = null

    private fun entry(c: Component): Entry {
        val e = last
        if (e != null && e.c === c) return e
        return Entry(c, c.string).also { last = it }
    }

    // Same as c.string.
    @JvmStatic fun raw(c: Component): String = entry(c).raw

    // Same as TextStrip.plain(c.string) (strips "§x" codes).
    @JvmStatic fun plain(c: Component): String = entry(c).plain
}

val Component.chatRaw: String get() = ChatText.raw(this)
val Component.chatPlain: String get() = ChatText.plain(this)
