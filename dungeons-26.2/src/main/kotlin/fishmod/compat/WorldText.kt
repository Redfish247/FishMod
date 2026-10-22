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
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.abs

// 26.2 side. Late mode (Visual.lateWorldText): the label is projected to the screen and drawn in the HUD, after water/glass/leaves.
// Otherwise text goes to the submit collector, which drains before translucent terrain.
object WorldText {
    @JvmField var collector: SubmitNodeCollector? = null

    private class Item(val rel: Vector3f, val size: Float, val text: Component, val argb: Int, val shadow: Boolean)

    private var pending = ArrayList<Item>()
    private var shown: List<Item> = emptyList()
    private val fwd = Vector3f(); private val up = Vector3f(); private val left = Vector3f()
    private val proj = Matrix4f()

    @JvmStatic
    fun init() {
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("fishmod", "world_text")) { g, _ ->
            if (shown.isEmpty()) return@addFirst
            try {
                val font = Minecraft.getInstance().font
                val w = g.guiWidth().toFloat(); val h = g.guiHeight().toFloat()
                // Project with the camera's own axes; the stored view/projection matrices put labels off in pitch
                val f = abs(proj.m11()) * 0.5f * h
                for (it in shown) {
                    val z = it.rel.dot(fwd)
                    if (z <= 0.05f) continue
                    val sx = w / 2 - it.rel.dot(left) / z * f
                    val sy = h / 2 - it.rel.dot(up) / z * f
                    val s = f / z * it.size
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
        proj.set(cam.projectionMatrix)
        val c = Minecraft.getInstance().cam
        fwd.set(c.forwardVector()); up.set(c.upVector()); left.set(c.leftVector())
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
            pending += Item(rel, size, text, argb, shadow)
            return
        }
        val c = collector ?: return
        c.submitText(ps, -font.width(text) / 2f, 0f, text.visualOrderText, shadow, Font.DisplayMode.SEE_THROUGH, 15728880, argb, 0, 0)
    }

    fun flush() {}
}
