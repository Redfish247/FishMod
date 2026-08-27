package fishmod.features.dungeon

import fishmod.utils.config.values.Dungeons
import fishmod.utils.config.values.FishSettings
import fishmod.utils.events.Events
import net.minecraft.client.Minecraft
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Revives blade-addons' `enableAutoRequeue` (logic ported from Odin's DungeonRequeue): when the
 * end-of-run "> EXTRA STATS <" header prints, send `/instancerequeue` after a short delay so the
 * party rolls straight into a fresh instance.
 *
 * Guarded by [disabled], which latches on any party breakup / leader change (a requeue would fail
 * or split the group) and clears on world load.
 */
object AutoRequeue {

    // 29 spaces then the header — Hypixel's exact end-screen divider line.
    private val EXTRA_STATS: Pattern = Pattern.compile(" {29}> EXTRA STATS <")
    private val BREAKUP: Pattern = Pattern.compile(
        "^(?:You have been kicked from the party|You left the party|The party was disbanded|" +
            "The party was transferred to |.+ has disbanded the party|.+ has been removed from the party|" +
            "You are not currently in a party)"
    )
    private val COLOR = Regex("§.")

    @Volatile private var disabled = false

    @JvmStatic
    fun init() {
        Events.ON_GAME_MESSAGE.register { text ->
            val s = COLOR.replace(text.string, "")
            when {
                BREAKUP.matcher(s).find() -> disabled = true
                Dungeons.enableAutoRequeue && !disabled && EXTRA_STATS.matcher(s).find() -> {
                    val delay = FishSettings.autoRequeueDelayMs.coerceIn(0, 15000).toLong()
                    CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS).execute {
                        Minecraft.getInstance().execute {
                            val mc = Minecraft.getInstance()
                            if (Dungeons.enableAutoRequeue && !disabled && mc.connection != null) {
                                mc.connection!!.sendCommand("instancerequeue")
                            }
                        }
                    }
                }
            }
            false
        }
        Events.ON_WORLD_CHANGE.register { disabled = false; false }
    }
}
