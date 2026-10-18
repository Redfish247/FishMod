package fishmod.features

import fishmod.utils.config.values.Visual
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.RenderUtils
import fishmod.utils.rendering.RenderingEvents
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3

// Armor-stand holograms: vanilla draws their nametag before translucent terrain, so leaves/glass/water paint over it. Drawn here after terrain instead.
object HologramText {
    private class Line(val pos: Vec3, val text: Component)

    private val lines = ArrayList<Line>()

    @JvmStatic
    fun init() {
        RenderingEvents.NO_DEPTH_FILLED.register { ctx, ps, _ ->
            try {
                for (l in lines) RenderUtils.renderSeeThroughText(ctx, ps, l.text, l.pos, 0.025f, -1, false)
            } catch (t: Throwable) {
                FishDiag.fail("HologramText.1", "hologram text draw failed", t)
            } finally {
                lines.clear()
            }
        }
    }

    // Returns true when the line was taken over (caller hides the vanilla nametag)
    @JvmStatic
    fun take(x: Double, y: Double, z: Double, attachment: Vec3?, text: Component?): Boolean {
        if (!Visual.nametagsOnTop || text == null || attachment == null) return false
        lines += Line(Vec3(x + attachment.x, y + attachment.y, z + attachment.z), text)
        return true
    }
}
