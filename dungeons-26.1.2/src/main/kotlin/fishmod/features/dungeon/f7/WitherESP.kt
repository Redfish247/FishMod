package fishmod.features.dungeon.f7

import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.Phase
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.boss.wither.WitherBoss

/**
 * Highlights the F7 Wither boss. Colour follows the phase — Maxor / Storm / Goldor / Necron. Box
 * outline around every non-invisible [WitherBoss] (FishMod has no per-entity glow hook, so a
 * bounding-box outline stands in). Off during P5.
 */
object WitherESP {

    // The boss entity resolves rarely, so the entity scan runs on a tick interval instead of every
    // render frame (mirrors StarredMobHighlight's SCAN_INTERVAL_TICKS pattern), and the render
    // callback just reads the cached result.
    private const val SCAN_INTERVAL_TICKS = 5
    private var scanCounter = 0
    private var cachedWithers: List<WitherBoss> = emptyList()

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { tick() })
        RenderingEvents.GIZMO.register { _ ->
            if (!active()) return@register
            val stroke = color()
            for (wither in cachedWithers) {
                if (wither.isAlive) RenderUtils.gizmoBox(wither.boundingBox, 0, stroke)
            }
        }
    }

    private fun active(): Boolean =
        FishSettings.witherEspEnabled && Location.inDungeon() && Phase.inBoss() && !Phase.inP5()

    private fun tick() {
        if (!active()) { cachedWithers = emptyList(); scanCounter = 0; return }
        scanCounter++
        if (scanCounter < SCAN_INTERVAL_TICKS) return
        scanCounter = 0
        val level = Minecraft.getInstance().level ?: return
        cachedWithers = level.entitiesForRendering().filterIsInstance<WitherBoss>().filter {
            !it.isInvisible && it.isAlive && it.invulnerableTicks != 800 && it.boundingBox.ysize >= 2.0
        }
    }

    private fun color(): Int = when {
        Phase.inP1() -> FishSettings.witherEspMaxorColor
        Phase.inP2() -> FishSettings.witherEspStormColor
        Phase.inP3() -> FishSettings.witherEspGoldorColor
        else -> FishSettings.witherEspNecronColor
    }
}
