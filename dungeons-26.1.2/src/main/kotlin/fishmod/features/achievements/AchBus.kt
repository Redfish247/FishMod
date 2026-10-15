package fishmod.features.achievements

import fishmod.utils.debug.FishDiag
import java.util.concurrent.CopyOnWriteArrayList

// Typed gameplay events turned out of chat/scoreboard/trackers; achievements and trackers subscribe
sealed interface AchEvent {
    data class RunStarted(val floor: String?) : AchEvent
    data class RunEnded(val floor: String?, val seconds: Double, val score: Int, val deaths: Int, val secrets: Int, val solo: Boolean, val cleared: Int) : AchEvent
    data class Drop(val item: String, val count: Int, val value: Double, val rarity: String, val source: String) : AchEvent
    data class ChestOpened(val kind: String, val paid: Boolean, val value: Double) : AchEvent
    data class PuzzleDone(val failed: Boolean) : AchEvent
    data class TerminalDone(val own: Boolean, val objective: String) : AchEvent
    data class TerminalSection(val seconds: Double) : AchEvent
    data class BloodOpened(val seconds: Double) : AchEvent
    data object MimicKilled : AchEvent
    data object PrinceKilled : AchEvent
    data class KuudraEnded(val tier: Int, val ms: Long) : AchEvent
    data object SupplyDelivered : AchEvent
    data object BallistaBuilt : AchEvent
    data class SlayerKill(val type: String, val tier: Int) : AchEvent
    data class PersonalBest(val key: String, val improvedBy: Double) : AchEvent
    data class OwnDeath(val message: String) : AchEvent
    data object FeastStarted : AchEvent
    data object VisitorAccepted : AchEvent
    data object CommissionDone : AchEvent
    data class MarketFill(val kind: String) : AchEvent
    data class Unlocked(val id: String) : AchEvent
}

object AchBus {
    private val listeners = CopyOnWriteArrayList<(AchEvent) -> Unit>()

    fun on(l: (AchEvent) -> Unit) { listeners.add(l) }

    @JvmStatic
    fun post(e: AchEvent) {
        for (l in listeners) {
            try { l(e) } catch (t: Throwable) { FishDiag.fail("AchBus.1", "listener threw on ${e.javaClass.simpleName}", t) }
        }
    }
}
