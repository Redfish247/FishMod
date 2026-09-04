package fishmod.features.dungeon.puzzles.odin

import fishmod.utils.config.values.FishSettings
import fishmod.utils.rendering.RenderUtils
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Thin shims mapping puzzle-solver draw calls onto FishMod's occluded-gizmo helpers. The `depth`
 * flag is ignored — every gizmo is terrain-occluded.
 *
 * `style`: 0 = Filled, 1 = Outline, 2 = Filled + Outline.
 */
object ORender {

    private fun outline(argb: Int) = 0xFF000000.toInt() or (argb and 0xFFFFFF)

    /** FishSettings' style string -> style index (0 Filled / 1 Outline / 2 both). */
    fun style(): Int = when (FishSettings.puzzleSolverStyle) {
        "Filled" -> 0
        "Outline" -> 1
        else -> 2
    }

    fun styledBox(box: AABB, argb: Int, style: Int) {
        val fill = if (style == 0 || style == 2) argb else 0
        val stroke = if (style == 1 || style == 2) outline(argb) else 0
        RenderUtils.gizmoBox(box, fill, stroke)
    }

    fun filledBox(box: AABB, argb: Int) = RenderUtils.gizmoBox(box, argb, 0)

    fun outlinedBox(box: AABB, argb: Int) = RenderUtils.gizmoBox(box, 0, outline(argb))

    fun line(points: List<Vec3>, argb: Int) {
        for (i in 1 until points.size) RenderUtils.gizmoLine(points[i - 1], points[i], argb)
    }

    fun line(a: Vec3, b: Vec3, argb: Int) = RenderUtils.gizmoLine(a, b, argb)

    fun text(str: String, pos: Vec3, scale: Float) =
        RenderUtils.gizmoText(Component.literal(str), pos, scale, -0x1)

    /** Pseudo beacon beam: a thin tall column from [base] upward. */
    fun beaconBeam(base: Vec3, argb: Int) {
        val a = 0x40 shl 24 or (argb and 0xFFFFFF)
        RenderUtils.gizmoBox(
            AABB(base.x + 0.35, base.y, base.z + 0.35, base.x + 0.65, base.y + 24.0, base.z + 0.65), a, 0,
        )
    }

    /**
     * Line from just in front of the camera to [target]. Starting exactly at the camera position
     * makes the near vertex project to a garbage screen location — offset one block along the look
     * vector so the line reads as coming from the crosshair.
     */
    fun tracer(target: Vec3, argb: Int) {
        val cam = Minecraft.getInstance().gameRenderer.mainCamera
        val start = cam.position().add(Vec3.directionFromRotation(cam.xRot(), cam.yRot()))
        RenderUtils.gizmoLine(start, target, argb)
    }

    fun withAlpha(argb: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f).toInt() and 0xFF
        return (a shl 24) or (argb and 0xFFFFFF)
    }
}
