package fishmod.features.diana

import fishmod.utils.Misc
import fishmod.utils.events.Events
import fishmod.utils.sound.SoundManager
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.floor

// Registers nearby burrows from their particles and tracks digging progress from chat
object BurrowDetector {

    private val DUG = Regex("""^You (.*?) Griffin [Bb]urrow(.*?) \((\d+)/(\d+)\)$""")
    private val CHAIN_DONE = Regex("""^You finished the Griffin burrow chain!.*""")
    private val FIRST_DIG = Regex(""".*You (?:just )?dug out(?!.*\(\d+/\d+\)$).*""")
    private val DEATH = Regex("""^ ☠ You .+""")
    private val TREASURE_WORDS = listOf("Griffin Feather", " coins!", "Mythos Fragment", "Braided Griffin Feather", "Myth the Fish")

    private val chains = ArrayDeque<Long>()

    val activeChains: Int get() { prune(); return chains.size }

    fun reset() { chains.clear() }

    fun init() {
        Events.ON_PARTICLE.register { p ->
            if (Diana.inHub() && DianaSettings.dianaGuessing && DianaSettings.dianaBurrowDetection) onParticle(p)
            false
        }
        Events.ON_SOUND.register { snd, _, _ -> onServerSound(snd.location.toString()) }
        Events.ON_GAME_MESSAGE.register { text ->
            if (Diana.inHub()) {
                val s = text.string.replace(Regex("§."), "")
                if (DianaSettings.dianaBurrowDugSound) dugSound(s)
                if (DianaSettings.dianaGuessing) onChat(s)
            }
            false
        }
    }

    private fun isDigLine(s: String) = DUG.matches(s) || CHAIN_DONE.matches(s) || (FIRST_DIG.matches(s) && !s.contains("Griffin Burrow"))

    // ---- Hypixel's dig ding (arrow.hit_player, pitch climbs per dig) arrives the instant you dig, before the chat line ----
    private const val DIG_DING = "minecraft:entity.arrow.hit_player"

    private fun onServerSound(id: String): Boolean {
        if (id != DIG_DING || !Diana.inHub()) return false
        // Not gated on the spade: after a mob kill you hold your weapon and Hypixel still dings; only chat triggers our sound
        return DianaSettings.dianaMuteHypixelDug
    }

    // Covers every dig line incl. the (4/4) chain end; debounced so loot lines on the same dig don't double up
    private fun dugSound(s: String) {
        if (isDigLine(s)) playDug()
    }

    private fun playDug() {
        var vol = DianaSettings.dianaBurrowDugVolume.coerceIn(0, 500) / 100f
        val snd = SoundManager.preset(DianaSettings.dianaBurrowDugSoundName)
        if (!SoundManager.play(snd, minOf(vol, 1f), key = "diana_dug", debounceMs = 2000)) return
        vol -= 1f
        while (vol > 0.01f) { SoundManager.play(snd, minOf(vol, 1f)); vol -= 1f }
    }

    private fun near(a: Double, b: Double) = abs(a - b) < 0.005

    private fun offsets(p: ClientboundLevelParticlesPacket, x: Double, y: Double, z: Double) =
        near(p.xDist.toDouble(), x) && near(p.yDist.toDouble(), y) && near(p.zDist.toDouble(), z)

    private fun onParticle(p: ClientboundLevelParticlesPacket) {
        val type = p.particle.type
        val speed = p.maxSpeed.toDouble()
        val pos = BlockPos(floor(p.x).toInt(), floor(p.y).toInt() - 1, floor(p.z).toInt())
        val kind = when {
            type === ParticleTypes.ENCHANTED_HIT && p.count == 4 && near(speed, 0.01) && offsets(p, 0.5, 0.1, 0.5) -> BurrowType.START
            type === ParticleTypes.CRIT && p.count == 3 && near(speed, 0.01) && offsets(p, 0.5, 0.1, 0.5) -> BurrowType.MOB
            type === ParticleTypes.DRIPPING_LAVA && p.count == 2 && near(speed, 0.01) && offsets(p, 0.35, 0.1, 0.35) -> BurrowType.TREASURE
            type === ParticleTypes.LARGE_SMOKE && near(speed, 0.01) && offsets(p, 0.0, 0.0, 0.0) -> { onDugOut(pos); null }
            else -> null
        } ?: return
        if (DianaWaypoints.removedRecently(pos)) return
        register(pos, kind)
    }

    fun register(pos: BlockPos, kind: BurrowType) {
        val existing = DianaWaypoints.at(pos, WpType.BURROW)
        if (existing != null) {
            // Particles refine the type once a mob/treasure burrow shows its real signature
            if (existing.burrowType != kind && kind != BurrowType.START) { existing.burrowType = kind; existing.label = kind.label }
            return
        }
        val w = Waypoint(pos, WpType.BURROW, kind.label).also { it.burrowType = kind }
        for (o in DianaWaypoints.list.filter { it.pos == pos }) {
            if (o.type == WpType.ARROW || o.type == WpType.SUB || o.type == WpType.GUESS) { w.carryFrom(o); DianaWaypoints.remove(o) }
        }
        ArrowGuess.onBurrowAt(pos)
        DianaWaypoints.add(w)
    }

    // Smoke puff = burrow fully dug
    private fun onDugOut(pos: BlockPos) {
        DianaWaypoints.markRemoved(pos)
        DianaWaypoints.removeAt(pos, WpType.BURROW, WpType.GUESS, WpType.ARROW, WpType.SUB)
        ArrowGuess.onBurrowAt(pos)
    }

    private fun prune() {
        val now = System.currentTimeMillis()
        while (chains.isNotEmpty() && chains.peekFirst() < now) chains.pollFirst()
    }

    private fun onChat(s: String) {
        if (s.contains("Griffin")) DianaTest.log("burrow chat: '$s'")
        // The chain-finished line also ends in (4/4), so it has to be checked before the dig pattern
        if (CHAIN_DONE.matches(s)) {
            prune(); chains.pollFirst()
            refresh(death = false, expected = 2, type = null)
            val me = Diana.player()?.position()
            DianaTest.log("chain end: title=${DianaSettings.dianaChainEndTitle} near=${me?.let { p -> DianaWaypoints.targets().count { it.distTo(p) <= 90 } }}")
            if (DianaSettings.dianaChainEndTitle && me != null && DianaWaypoints.targets().none { it.distTo(me) <= 90 })
                Misc.forceTitle(Component.literal("§eUse Spade!"), Component.empty(), 2000)
            return
        }
        DUG.matchEntire(s)?.let { m ->
            val cur = m.groupValues[3].toIntOrNull() ?: 0
            val max = m.groupValues[4].toIntOrNull() ?: 0
            prune()
            val expiry = System.currentTimeMillis() + 30 * 60_000L
            if (cur == 1) chains.addLast(expiry) else if (chains.isNotEmpty()) { chains.pollFirst(); chains.addLast(expiry) }
            if (cur != max) ArrowGuess.onBurrowDug()
            refresh(death = false, expected = 2, type = null)
            return
        }
        if (FIRST_DIG.matches(s) && !s.contains("Griffin Burrow")) {
            val type = if (TREASURE_WORDS.any { s.contains(it) }) BurrowType.TREASURE else BurrowType.MOB
            refresh(death = false, expected = 1, type = type)
            return
        }
        if (DEATH.matches(s)) refresh(death = true, expected = 1, type = null)
    }

    // Updates the waypoint the player last clicked after a dig result
    private fun refresh(death: Boolean, expected: Int, type: BurrowType?) {
        val pos = Diana.lastClickedWaypoint ?: return
        val w = DianaWaypoints.findDiggable(pos) ?: return
        if (!death && w.burrowType != BurrowType.START) w.timesDug = maxOf(w.timesDug + 1, expected)
        val remove = (!death && (w.burrowType == BurrowType.START || w.timesDug >= 2)) ||
            (death && w.timesDug >= 1 && w.burrowType == BurrowType.MOB)
        if (remove) {
            if (death) { prune(); chains.pollFirst() }
            DianaWaypoints.markRemoved(w.pos)
            DianaWaypoints.removeAt(w.pos, WpType.BURROW, WpType.GUESS, WpType.ARROW, WpType.SUB)
            // Keep lastClickedWaypoint: the next arrow spawns here and filters on it (as SBO does)
            ArrowGuess.onBurrowAt(w.pos)
            return
        }
        if (type != null && w.type != WpType.BURROW) {
            DianaWaypoints.remove(w)
            ArrowGuess.onBurrowAt(w.pos)
            DianaWaypoints.add(Waypoint(w.pos, WpType.BURROW, type.label).also { it.burrowType = type; it.timesDug = expected; it.clicked = true })
        }
    }
}
