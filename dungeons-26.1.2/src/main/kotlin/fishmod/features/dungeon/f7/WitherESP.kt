package fishmod.features.dungeon.f7

import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.Phase
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.boss.wither.WitherBoss

/**
 * Highlights the F7 Wither boss (ported from NoammAddons' WitherESP). Colour follows the phase —
 * Maxor / Storm / Goldor / Necron. Box outline around every non-invisible [WitherBoss] (FishMod has
 * no per-entity glow hook, so a bounding-box outline stands in). Off during P5.
 */
object WitherESP {

    @JvmStatic
    fun init() {
        RenderingEvents.OUTLINE_ENTITY.register { _, matrices, consumer ->
            if (!FishSettings.witherEspEnabled || !Location.inDungeon() || !Phase.inBoss() || Phase.inP5()) return@register
            val mc = Minecraft.getInstance()
            val level = mc.level ?: return@register
            val rgba = RenderUtils.toFloats(color())
            for (e in level.entitiesForRendering()) {
                if (e is WitherBoss && !e.isInvisible && e.isAlive) {
                    RenderUtils.renderOutline(matrices, consumer, e.boundingBox, rgba)
                }
            }
        }
    }

    private fun color(): Int = when {
        Phase.inP1() -> FishSettings.witherEspMaxorColor
        Phase.inP2() -> FishSettings.witherEspStormColor
        Phase.inP3() -> FishSettings.witherEspGoldorColor
        else -> FishSettings.witherEspNecronColor
    }
}
