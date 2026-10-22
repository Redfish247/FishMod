package fishmod.compat

import com.mojang.blaze3d.vertex.PoseStack
import fishmod.utils.config.values.Visual
import fishmod.utils.debug.FishDiag
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f

// 26.2 side. Late mode (Visual.lateWorldText): the label is projected to the screen and drawn in the HUD, after water/glass/leaves.
// Otherwise text goes to the submit collector, which drains before translucent terrain.
object WorldText {
    @JvmField var collector: SubmitNodeCollector? = null

    private class Item(val rel: Vector3f, val world: Vec3, val size: Float, val text: Component, val argb: Int, val shadow: Boolean)

    private var pending = ArrayList<Item>()
    private var shown: List<Item> = emptyList()
    private val view = Matrix4f()
    private val proj = Matrix4f()

    @JvmStatic
    fun init() {
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("fishmod", "world_text")) { g, _ ->
            if (shown.isEmpty()) return@addFirst
            try {
                val font = Minecraft.getInstance().font
                val w = g.guiWidth().toFloat(); val h = g.guiHeight().toFloat()
                val v = Vector4f()
                for (it in shown) {
                    v.set(it.rel.x, it.rel.y, it.rel.z, 1f)
                    v.mul(view).mul(proj)
                    if (v.w <= 0.05f) continue
                    // Vanilla's own projection (locator bar uses it), so labels track the world at any pitch
                    val sp = Minecraft.getInstance().gameRenderer.projectPointToScreen(it.world)
                    val sx = (sp.x.toFloat() * 0.5f + 0.5f) * w
                    val sy = (0.5f - sp.y.toFloat() * 0.5f) * h
                    val s = proj.m11() * 0.5f * h / v.w * it.size
                    if (s < 0.05f || s > 40f) continue
                    g.pose().pushMatrix()
                    g.pose().translate(sx, sy)
                    g.pose().scale(s, s)
                    g.text(font, it.text, -font.width(it.text) / 2, 0, it.argb, it.shadow)
                    g.pose().popMatrix()
                }
            } catch (t: Throwable) {
                FishDiag.fail("WorldText.1", "world text hud draw failed", t)
            }
        }
    }

    // Called once per frame from the collect pass: freezes this frame's labels with its camera
    @JvmStatic
    fun commit(cam: CameraRenderState) {
        view.set(cam.viewRotationMatrix); proj.set(cam.projectionMatrix)
        shown = pending
        pending = ArrayList()
    }

    // `ps` is already translated, rotated and scaled; text is centred on x = 0.
    fun draw(ps: PoseStack, text: Component, argb: Int, shadow: Boolean) {
        val font = Minecraft.getInstance().font
        if (Visual.lateWorldText) {
            val m = ps.last().pose()
            val rel = m.transformPosition(Vector3f())
            val size = m.transformDirection(Vector3f(1f, 0f, 0f)).length()
            // Gizmo mode hands us world coords (identity stack); the collect pass hands camera-relative ones
            val cam = Minecraft.getInstance().cam.position()
            val p = Vec3(rel.x.toDouble(), rel.y.toDouble(), rel.z.toDouble())
            val world = if (Visual.gizmoOverlays) p else cam.add(p)
            val camRel = world.subtract(cam)
            pending += Item(Vector3f(camRel.x.toFloat(), camRel.y.toFloat(), camRel.z.toFloat()), world, size, text, argb, shadow)
            return
        }
        val c = collector ?: return
        c.submitText(ps, -font.width(text) / 2f, 0f, text.visualOrderText, shadow, Font.DisplayMode.SEE_THROUGH, 15728880, argb, 0, 0)
    }

    fun flush() {}
}
