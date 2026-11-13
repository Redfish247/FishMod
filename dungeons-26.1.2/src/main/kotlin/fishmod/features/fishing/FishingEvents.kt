package fishmod.features.fishing

import fishmod.utils.FishMsg
import fishmod.utils.SkyblockLocation
import fishmod.utils.events.FishEventBus
import fishmod.utils.events.FishEvents
import fishmod.utils.events.chatPlain

// Fan-out of fishing chat lines into typed events. Strings adapted from Feesh (Sleepy-Panda, Apache-2.0); UNVERIFIED in game.
class SeaCreatureCaught(
    val creature: SeaCreature,
    val count: Int,
    val cocoon: Boolean,
    val location: SkyblockLocation,
    val atMs: Long,
)

class DoubleHook(val atMs: Long)

fun interface SeaCreatureHook { fun onCaught(e: SeaCreatureCaught) }
fun interface RareDropHook { fun onDrop(e: RareDrop) }
fun interface DoubleHookHook { fun onDoubleHook(e: DoubleHook) }

object FishingEvents {
    @JvmField val seaCreature = FishEventBus<SeaCreatureHook>()
    @JvmField val rareDrop = FishEventBus<RareDropHook>()
    @JvmField val doubleHook = FishEventBus<DoubleHookHook>()

    private const val DOUBLE_HOOK = "It's a Double Hook!"
    private const val COCOON = "CAUGHT! You cocooned "
    private val COCOON_RE = Regex("""^CAUGHT! You cocooned (?:a|an) (.+)!$""")

    private const val DOUBLE_WINDOW_MS = 10_000L
    private var doubleAt = 0L

    @JvmStatic
    fun init() {
        FishEvents.chat.register { c ->
            // setting first, then plain string, then prefix tests, regex last
            if (!Fishing.active()) return@register false
            handle(c.chatPlain)
            false
        }
    }

    @JvmStatic
    @JvmOverloads
    fun handle(plain: String, now: Long = System.currentTimeMillis(), ownName: String? = null) {
        if (plain.isEmpty()) return
        if (plain.startsWith(DOUBLE_HOOK)) {
            doubleAt = now
            debug("Double Hook")
            val e = DoubleHook(now)
            doubleHook.invoke { it.onDoubleHook(e); false }
            return
        }
        if (plain.startsWith(COCOON)) {
            val name = COCOON_RE.matchEntire(plain)?.groupValues?.get(1) ?: return
            val sc = SeaCreatures.byName(name) ?: return
            fireCatch(sc, 1, true, now)
            return
        }
        val drop = RareDrops.parse(plain, ownName ?: Fishing.ownName())
        if (drop != null) {
            debug("Rare drop ${drop.kind} ${drop.itemName} price=${drop.price.toLong()} mf=${drop.magicFind}")
            rareDrop.invoke { it.onDrop(drop); false }
            return
        }
        val sc = SeaCreatures.match(plain, Fishing.location(), FishingSettings.fishingAnyArea) ?: return
        val double = !sc.noDouble && doubleAt != 0L && now - doubleAt <= DOUBLE_WINDOW_MS
        if (double) doubleAt = 0L
        fireCatch(sc, if (double) 2 else 1, false, now)
    }

    private fun fireCatch(sc: SeaCreature, count: Int, cocoon: Boolean, now: Long) {
        debug("Catch ${sc.name} x$count${if (cocoon) " (cocoon)" else ""}")
        val e = SeaCreatureCaught(sc, count, cocoon, Fishing.location(), now)
        seaCreature.invoke { it.onCaught(e); false }
    }

    private fun debug(msg: String) {
        if (FishingSettings.fishingDebug) FishMsg.send("§7[Fishing] $msg")
    }
}
