package fishmod.features

import fishmod.compat.cam
import fishmod.utils.config.values.Visual
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.RenderingEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector4f

// Armor-stand holograms: vanilla draws their nametag before translucent terrain, so leaves/glass/water paint over it.
// Projected to screen and drawn in the HUD instead, so nothing can cover it.
object HologramText {
    private class Line(val pos: Vec3, val text: Component)

    private val pending = ArrayList<Line>()
    private var shown: List<Line> = emptyList()
    private val view = Matrix4f()
    private val proj = Matrix4f()
    private var camPos = Vec3.ZERO

    @JvmStatic
    fun init() {
        // Level pass runs every frame: freeze this frame's lines and camera for the HUD
        RenderingEvents.NO_DEPTH_FILLED.register { ctx, _, _ ->
            val cam = ctx.levelState().cameraRenderState
            view.set(cam.viewRotationMatrix); proj.set(cam.projectionMatrix); camPos = cam.pos
            shown = ArrayList(pending)
            pending.clear()
        }
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("fishmod", "hologram_text")) { g, _ ->
            if (shown.isEmpty()) return@addFirst
            try {
                val font = Minecraft.getInstance().font
                val w = g.guiWidth().toFloat(); val h = g.guiHeight().toFloat()
                val v = Vector4f()
                for (l in shown) {
                    v.set((l.pos.x - camPos.x).toFloat(), (l.pos.y - camPos.y).toFloat(), (l.pos.z - camPos.z).toFloat(), 1f)
                    v.mul(view).mul(proj)
                    if (v.w <= 0.05f) continue
                    val sx = (v.x / v.w * 0.5f + 0.5f) * w
                    val sy = (0.5f - v.y / v.w * 0.5f) * h
                    val s = (proj.m11() * 0.5f * h / v.w * 0.025f).coerceIn(0.3f, 4f)
                    g.pose().pushMatrix()
                    g.pose().translate(sx, sy)
                    g.pose().scale(s, s)
                    val half = font.width(l.text) / 2
                    g.fill(-half - 1, -1, half + 1, 9, 0x40000000)
                    g.text(font, l.text, -half, 0, -1, true)
                    g.pose().popMatrix()
                }
            } catch (t: Throwable) {
                FishDiag.fail("HologramText.1", "hologram hud draw failed", t)
            }
        }
    }

    // True when foliage/glass/water (but no opaque block) sits between the camera and the label
    private fun blockedByTranslucent(to: Vec3): Boolean {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return false
        val from = mc.cam.position()
        val d = to.subtract(from)
        val len = d.length()
        if (len < 2.0 || len > 48.0) return false
        val step = d.scale(0.25 / len)
        val bp = net.minecraft.core.BlockPos.MutableBlockPos()
        var p = from
        var blocked = false
        for (i in 0 until (len / 0.25).toInt()) {
            p = p.add(step)
            bp.set(p.x, p.y, p.z)
            val st = level.getBlockState(bp)
            if (st.isAir) continue
            if (st.canOcclude() && st.isSolidRender()) return false
            blocked = true
        }
        return blocked
    }

    // Returns true when the line was taken over (caller hides the vanilla nametag)
    @JvmStatic
    fun take(base: Vec3, attachment: Vec3?, text: Component?): Boolean {
        if (!Visual.nametagsOnTop || text == null || attachment == null || pending.size >= 512) return false
        val pos = base.add(attachment)
        if (!blockedByTranslucent(pos)) return false
        pending += Line(pos, text)
        return true
    }
}
