package fishmod.features

import fishmod.utils.dungeon.DungeonClass
import fishmod.utils.dungeon.Phase
import fishmod.utils.events.Events
import java.util.regex.Pattern

/**
 * Tracks Explosive Shot hits for the `.crit` / `!crit` command, split by phase: P1 (Maxor) hits
 * count as "crit", P2 (Storm) hits count as "storm kill". Only recorded while playing Archer.
 * Resets at the start of every run.
 */
object CritTracker {

    // Same line ExplosiveShot.kt parses: "Your Explosive Shot hit N enemy/enemies for D damage"
    private val PATTERN: Pattern = Pattern.compile(
        "Your Explosive Shot hit (\\d+) (?:enemy|enemies) for ([\\d,]+(?:\\.\\d+)?) damage"
    )

    private var lastCrit = 0.0
    private var critSum = 0.0
    private var critCount = 0

    private var lastStormKill = 0.0
    private var stormSum = 0.0
    private var stormCount = 0

    @JvmStatic
    fun init() {
        Events.ON_PHASE_CHANGE.register {
            if (Phase.runJustStarted()) reset()
            false
        }
        Events.ON_RUN_END.register {
            reset()
            false
        }
        Events.ON_GAME_MESSAGE.register { text -> onMessage(text.string) }
    }

    private fun reset() {
        lastCrit = 0.0; critSum = 0.0; critCount = 0
        lastStormKill = 0.0; stormSum = 0.0; stormCount = 0
    }

    private fun onMessage(s: String?): Boolean {
        if (s == null || !DungeonClass.isClass(DungeonClass.ARCHER)) return false
        if (s.indexOf("Explosive Shot") < 0) return false

        val m = PATTERN.matcher(s)
        if (!m.find()) return false

        val enemies: Int
        val total: Double
        try {
            enemies = m.group(1).toInt()
            total = m.group(2).replace(",", "").toDouble()
        } catch (e: NumberFormatException) {
            return false
        }
        if (enemies <= 0) return false

        val perEnemy = total / enemies
        when {
            Phase.inP1() -> { lastCrit = perEnemy; critSum += perEnemy; critCount++ }
            Phase.inP2() -> { lastStormKill = perEnemy; stormSum += perEnemy; stormCount++ }
        }
        return false // keep the original chat line
    }

    private fun avgCrit(): Double = if (critCount > 0) critSum / critCount else 0.0
    private fun avgStormKill(): Double = if (stormCount > 0) stormSum / stormCount else 0.0

    /** Whole numbers print with thousands separators; fractional values keep one decimal. */
    private fun formatDamage(v: Double): String {
        if (v <= 0.0) return "N/A"
        if (v == Math.floor(v) && !v.isInfinite()) return String.format("%,d", v.toLong())
        return String.format("%,.1f", v)
    }

    @JvmStatic
    fun buildMessage(): String {
        return "Crit: " + formatDamage(lastCrit) + " (avg " + formatDamage(avgCrit()) + ", " + critCount + ") | " +
            "Storm Kill: avg " + formatDamage(avgStormKill()) + " (" + stormCount + ")"
    }
}
