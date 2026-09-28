package fishmod.features.diana

import fishmod.utils.events.Events
import net.minecraft.core.BlockPos
import org.slf4j.LoggerFactory

// Only active with -Dfishmod.dianaTest=true: datapack chat markers stand in for spade use and block clicks
object DianaTest {

    private val LOG = LoggerFactory.getLogger("FishMod/DianaTest")
    private val CLICK = Regex("""^\[fmtest] click (-?\d+) (-?\d+) (-?\d+)$""")

    fun log(msg: String) { if (Diana.testMode) LOG.info(msg) }

    fun init() {
        LOG.info("Diana test mode on")
        Events.ON_PARTICLE.register { p ->
            val t = p.particle.type
            if (t === net.minecraft.core.particles.ParticleTypes.DUST || t === net.minecraft.core.particles.ParticleTypes.DRIPPING_LAVA)
                LOG.info("particle {} count={} speed={} off={},{},{} at {},{},{}", net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.getKey(t), p.count, p.maxSpeed, p.xDist, p.yDist, p.zDist, p.x, p.y, p.z)
            false
        }
        Events.ON_GAME_MESSAGE.register { text ->
            val s = text.string
            if (!s.startsWith("[fmtest]")) return@register false
            when {
                s == "[fmtest] spade" -> SpadeGuess.onSpadeUse()
                s == "[fmtest] dump" -> dump()
                s == "[fmtest] clear" -> DianaWaypoints.clearAll()
                s == "[fmtest] enableall" -> enableAll()
                s == "[fmtest] mastersoff" -> with(DianaSettings) { dianaGuessing = false; dianaWarp = false; dianaRareMobs = false; dianaTracker = false; dianaAnnouncers = false }
                s == "[fmtest] masterson" -> with(DianaSettings) { dianaGuessing = true; dianaWarp = true; dianaRareMobs = true; dianaTracker = true; dianaAnnouncers = true }
                s == "[fmtest] pastevents" -> DianaTracker.openPastEvents()
                s == "[fmtest] warp" -> DianaWarp.warp(false)
                s.startsWith("[fmtest] subguess ") -> s.removePrefix("[fmtest] subguess ").trim().split(" ").map { it.toInt() }.chunked(3)
                    .map { BlockPos(it[0], it[1], it[2]) }.let { ArrowGuess.addGuess(it) }
                s == "[fmtest] openmenu" -> net.minecraft.client.Minecraft.getInstance().let { mc -> mc.schedule { mc.setScreen(fishmod.features.FishModScreen()) } }
                s == "[fmtest] closescreen" -> net.minecraft.client.Minecraft.getInstance().setScreen(null)
                s.startsWith("[fmtest] shot ") -> shot(s.removePrefix("[fmtest] shot ").trim())
                else -> CLICK.matchEntire(s)?.let { m ->
                    Diana.onBlockClick(BlockPos(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()))
                }
            }
            LOG.info("marker: {}", s)
            false
        }
    }

    // Delay a few frames so the tp/rotation and new waypoints are rendered
    private fun shot(name: String) {
        val mc = net.minecraft.client.Minecraft.getInstance()
        fishmod.utils.Scheduler.scheduleTask(Runnable {
            net.minecraft.client.Screenshot.grab(mc.gameDirectory, "diana_$name.png", mc.mainRenderTarget, 1) { LOG.info("shot {}", name) }
        }, 10)
    }

    // Turns on every default-off Diana option so one run exercises all of them
    private fun enableAll() {
        with(DianaSettings) {
            dianaSubGuessText = true; dianaBeaconBeam = true; dianaChainEndTitle = true
            dianaWarpTitle = true; dianaHighlightRareMobs = true; dianaNoShuriken = true
            dianaMobTracker = "Event"; dianaMfTracker = true; dianaTextShadow = true; dianaDynamicOpacity = true
            dianaInqSpawnText = "Inq spawned! {since} mobs, {chance}%"
            dianaMsgChimera = "&dCUSTOM CHIM {mf}% #{amount} ({percentage})"
        }
        LOG.info("all optional Diana features enabled")
    }

    fun dump() {
        LOG.info("waypoints ({}): chains={} hasSpade={} holding={}", DianaWaypoints.list.size, BurrowDetector.activeChains, Diana.hasSpade, Diana.holdingSpade)
        for (w in DianaWaypoints.list) LOG.info("  {} {} {} label='{}' dug={} warp={}", w.type, w.burrowType, w.pos.toShortString(), w.label, w.timesDug, w.warpHint)
    }
}
