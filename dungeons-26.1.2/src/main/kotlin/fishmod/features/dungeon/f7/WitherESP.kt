package fishmod.features.dungeon.f7

import fishmod.utils.SkyblockLocation
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.BossPhase
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.boss.wither.WitherBoss
import fishmod.utils.debug.FishDiag

object WitherESP {

    private const val SCAN_INTERVAL_TICKS = 5
    private var scanCounter = 0
    private var cachedWithers: List<WitherBoss> = emptyList()

    @JvmStatic
    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick {
            try { tick() } catch (e: Exception) { FishDiag.fail("WitherESP.1", "wither ESP scan threw", e) }
        })
        RenderHooks.GIZMO.register { _ ->
            if (!active()) return@register
            try {
                val stroke = color()
                for (wither in cachedWithers) {
                    if (wither.isAlive) FishDraw.gizmoBox(wither.boundingBox, 0, stroke)
                }
            } catch (e: Exception) { FishDiag.fail("WitherESP.2", "wither ESP render threw (n=${cachedWithers.size})", e) }
        }
    }

    private fun active(): Boolean =
        FishSettings.witherEspEnabled && SkyblockLocation.inDungeon() && BossPhase.inBoss() && !BossPhase.inP5()

    private fun tick() {
        if (!active()) { cachedWithers = emptyList(); scanCounter = 0; return }
        scanCounter++
        if (scanCounter < SCAN_INTERVAL_TICKS) return
        scanCounter = 0
        val level = Minecraft.getInstance().level ?: return
        cachedWithers = level.entitiesForRendering().filterIsInstance<WitherBoss>().filter {
            !it.isInvisible && it.isAlive && it.invulnerableTicks != 800 && it.boundingBox.ysize >= 2.0
        }
        FishDiag.check(cachedWithers.size <= 6, "WitherESP.3") { "unexpected wither count ${cachedWithers.size}" }
    }

    private fun color(): Int = when {
        BossPhase.inP1() -> FishSettings.witherEspMaxorColor
        BossPhase.inP2() -> FishSettings.witherEspStormColor
        BossPhase.inP3() -> FishSettings.witherEspGoldorColor
        else -> FishSettings.witherEspNecronColor
    }
}
