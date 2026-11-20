package fishmod.features.slayers

import fishmod.features.slayers.EndAlertSettings as S
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.FishDraw
import fishmod.utils.rendering.RenderHooks
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.phys.Vec3

// Shows the stack name and count above dropped items
object GroundItemNames {

    fun init() {
        RenderHooks.NO_DEPTH_LINE.register { ctx, ps, _ ->
            if (!S.groundItemNames) return@register
            val mc = Minecraft.getInstance()
            val level = mc.level ?: return@register
            val player = mc.player ?: return@register
            val r = S.groundItemRange.coerceIn(2, 64).toDouble()
            val scale = S.groundItemScale.toFloat()
            FishDiag.guard("GroundItemNames.1", "ground item label render failed") {
                for (e in level.entitiesForRendering()) {
                    if (e !is ItemEntity || e.distanceToSqr(player) > r * r) continue
                    val st = e.item
                    if (st.isEmpty) continue
                    val name = st.hoverName.copy()
                    if (st.count > 1) name.append(Component.literal(" §7x" + st.count))
                    FishDraw.renderText(ctx, ps, name, Vec3(e.x, e.y + 0.8, e.z), scale)
                }
            }
        }
    }
}
