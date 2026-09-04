package fishmod.features

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import fishmod.utils.Location
import fishmod.utils.config.values.FishSettings
import fishmod.utils.data.ItemUtil
import fishmod.utils.events.Events
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.phys.AABB
import kotlin.math.abs

/**
 * Tac Timer. Tactical Insertion re-teleports you to where you cast it after 3s; Hypixel plays a
 * flint-and-steel sound at a fixed pitch (0.74603176) on cast.
 * We start a 60-tick countdown on that sound while holding the item, with an optional start
 * waypoint.
 */
object TacTimer {

    private const val NAME = "Tac Timer"
    private const val CAST_PITCH = 0.74603176f
    private val FLINT = SoundEvents.FLINTANDSTEEL_USE.location

    @Volatile private var ticks = 0
    @Volatile private var pos: BlockPos? = null

    @JvmStatic
    fun init() {
        FishHudEditor.register(
            NAME,
            { FishSettings.tacTimerHudX }, { v -> FishSettings.tacTimerHudX = v },
            { FishSettings.tacTimerHudY }, { v -> FishSettings.tacTimerHudY = v },
            70, 12,
            { FishSettings.tacTimerScale }, { v -> FishSettings.tacTimerScale = v }
        )

        Events.ON_SOUND.register { event, _, pitch ->
            if (!FishSettings.tacTimerEnabled || !Location.inSkyblock()) return@register false
            if (event.location != FLINT || abs(pitch - CAST_PITCH) > 1e-4f) return@register false
            val p = Minecraft.getInstance().player ?: return@register false
            if (ItemUtil.getId(p.mainHandItem) != "TACTICAL_INSERTION") return@register false
            ticks = 60
            pos = if (FishSettings.tacTimerWaypoint) p.blockPosition() else null
            false
        }

        Events.ON_SERVER_TICK.register {
            if (ticks > 0) ticks-- else pos = null
            false
        }

        Events.ON_WORLD_CHANGE.register { ticks = 0; pos = null; false }

        RenderingEvents.NO_DEPTH_FILLED.register { _, m, vc -> renderWaypoint(m, vc, fill = true) }
        RenderingEvents.NO_DEPTH_LINE.register { _, m, vc -> renderWaypoint(m, vc, fill = false) }
    }

    private fun renderWaypoint(matrices: PoseStack, vc: VertexConsumer, fill: Boolean) {
        if (!FishSettings.tacTimerEnabled || !FishSettings.tacTimerWaypoint || ticks <= 0) return
        val bp = pos ?: return
        val box = AABB(bp.x.toDouble(), bp.y.toDouble(), bp.z.toDouble(), bp.x + 1.0, bp.y + 1.0, bp.z + 1.0)
        val col = RenderUtils.toFloats(FishSettings.tacTimerColor)
        if (fill) RenderUtils.renderFilled(matrices, vc, box, floatArrayOf(col[0], col[1], col[2], col[3] * 0.25f))
        else RenderUtils.renderOutline(matrices, vc, box, col)
    }

    private fun label(t: Int): String {
        val left = (if (FishSettings.tacTimerReverse) 60 - t else t) / 20.0
        val color = when {
            t > 40 -> "§a"; t > 20 -> "§e"; else -> "§c"
        }
        val pre = if (FishSettings.tacTimerPrefix) "§5Tac: " else ""
        val suf = if (FishSettings.tacTimerSuffix) "s" else ""
        return "$pre$color${"%.1f".format(left)}$suf"
    }

    @JvmStatic
    fun renderHud(ctx: GuiGraphicsExtractor, tick: DeltaTracker) {
        if (!FishSettings.tacTimerEnabled || ticks <= 0) return
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui) return
        val sc = FishSettings.tacTimerScale.toFloat()
        ctx.pose().pushMatrix()
        ctx.pose().translate(FishSettings.tacTimerHudX.toFloat(), FishSettings.tacTimerHudY.toFloat())
        ctx.pose().scale(sc, sc)
        ctx.text(mc.font, label(ticks), 0, 0, -1, true)
        ctx.pose().popMatrix()
    }
}
