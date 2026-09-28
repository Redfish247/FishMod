package fishmod.features.diana

import fishmod.utils.events.Events
import net.minecraft.core.BlockPos
import org.slf4j.LoggerFactory

// Only active with -Dfishmod.dianaTest=true: datapack chat markers stand in for spade use and block clicks
object DianaTest {

    private val LOG = LoggerFactory.getLogger("FishMod/DianaTest")
    private val CLICK = Regex("""^\[fmtest] click (-?\d+) (-?\d+) (-?\d+)$""")

    fun init() {
        LOG.info("Diana test mode on")
        Events.ON_GAME_MESSAGE.register { text ->
            val s = text.string
            if (!s.startsWith("[fmtest]")) return@register false
            when {
                s == "[fmtest] spade" -> SpadeGuess.onSpadeUse()
                s == "[fmtest] dump" -> dump()
                s == "[fmtest] clear" -> DianaWaypoints.clearAll()
                else -> CLICK.matchEntire(s)?.let { m ->
                    Diana.onBlockClick(BlockPos(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()))
                }
            }
            LOG.info("marker: {}", s)
            false
        }
    }

    fun dump() {
        LOG.info("waypoints ({}): chains={} hasSpade={} holding={}", DianaWaypoints.list.size, BurrowDetector.activeChains, Diana.hasSpade, Diana.holdingSpade)
        for (w in DianaWaypoints.list) LOG.info("  {} {} {} label='{}' dug={} warp={}", w.type, w.burrowType, w.pos.toShortString(), w.label, w.timesDug, w.warpHint)
    }
}
