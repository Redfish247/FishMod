package fishmod.features.kuudra

import fishmod.features.dungeon.AutoRequeue
import fishmod.utils.debug.FishDiag
import net.minecraft.client.Minecraft
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import fishmod.features.kuudra.KuudraSettings as S

// Same idea as dungeon AutoRequeue: one /joininstance for the tier you just ran, skipped when the party changed or someone said !dt
object KuudraRequeue {

    private val TIERS = arrayOf("normal", "hot", "burning", "fiery", "infernal")
    private var fired = false

    fun reset() { fired = false }

    fun onRunEnd() {
        if (!S.kuudraRequeue || fired || AutoRequeue.partyChanged || AutoRequeue.dtSkip) return
        fired = true
        val cmd = if (Kuudra.tier in 1..TIERS.size) "joininstance kuudra_" + TIERS[Kuudra.tier - 1] else "instancerequeue"
        FishDiag.check(Kuudra.tier in 1..TIERS.size, "KuudraRequeue.2") { "unknown kuudra tier ${Kuudra.tier}, falling back to instancerequeue" }
        CompletableFuture.delayedExecutor(S.kuudraRequeueDelayMs.coerceIn(0, 30000).toLong(), TimeUnit.MILLISECONDS).execute {
            Minecraft.getInstance().execute {
                val mc = Minecraft.getInstance()
                if (S.kuudraRequeue && !AutoRequeue.partyChanged && !AutoRequeue.dtSkip && mc.connection != null && Kuudra.inKuudra()) {
                    FishDiag.guard("KuudraRequeue.1", "kuudra requeue command '$cmd' failed") { mc.connection!!.sendCommand(cmd) }
                }
            }
        }
    }
}
