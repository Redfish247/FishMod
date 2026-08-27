package fishmod.features.dungeon

import fishmod.utils.Location
import fishmod.utils.config.values.Dungeons
import fishmod.utils.events.Events
import net.minecraft.client.Minecraft
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Revives blade-addons' `enableAutoRequeue`: once a dungeon run ends, automatically sends
 * `/instancerequeue` so the party rolls straight into a fresh instance. Fires once per run,
 * ~1.5s after [Events.ON_RUN_END] so it lands after the completion screen settles, and only while
 * still inside the dungeon. Hypixel ignores it for non-leaders (one rejection line, no spam loop).
 */
object AutoRequeue {

    @Volatile private var firedThisRun = false

    @JvmStatic
    fun init() {
        Events.ON_RUN_END.register {
            if (Dungeons.enableAutoRequeue && !firedThisRun && Location.inDungeon()) {
                firedThisRun = true
                CompletableFuture.delayedExecutor(1500, TimeUnit.MILLISECONDS).execute {
                    Minecraft.getInstance().execute {
                        val mc = Minecraft.getInstance()
                        if (Dungeons.enableAutoRequeue && Location.inDungeon() && mc.connection != null) {
                            mc.connection!!.sendCommand("instancerequeue")
                        }
                    }
                }
            }
            false
        }
        Events.ON_WORLD_CHANGE.register { firedThisRun = false; false }
        Events.ON_LOCATION_CHANGE.register { _ -> firedThisRun = false; false }
    }
}
