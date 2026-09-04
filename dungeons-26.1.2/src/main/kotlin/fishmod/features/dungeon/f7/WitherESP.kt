package fishmod.features.dungeon.f7

import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.dungeon.Phase
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.boss.wither.WitherBoss

/**
 * Highlights the F7 Wither boss. Colour follows the phase — Maxor / Storm / Goldor / Necron. Box
 * outline around every non-invisible [WitherBoss] (FishMod has no per-entity glow hook, so a
 * bounding-box outline stands in). Off during P5.
 */
object WitherESP {

    @JvmStatic
    fun init() {
        RenderingEvents.GIZMO.register { _ ->
            if (!FishSettings.witherEspEnabled || !Location.inDungeon() || !Phase.inBoss() || Phase.inP5()) return@register
            val level = Minecraft.getInstance().level ?: return@register
            val stroke = color()
            for (e in level.entitiesForRendering()) {
                if (e !is WitherBoss || e.isInvisible || !e.isAlive) continue
                // Skip the player's Witherborn minion (full Wither armor set bonus). Hypixel pins
                // those at invulnerableTicks == 800 and renders them shrunk — a real boss wither's
                // box is ~3.5 tall.
                if (e.invulnerableTicks == 800 || e.boundingBox.ysize < 2.0) continue
                RenderUtils.gizmoBox(e.boundingBox, 0, stroke)
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
