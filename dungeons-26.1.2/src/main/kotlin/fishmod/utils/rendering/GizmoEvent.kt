package fishmod.utils.rendering

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext

/**
 * A handler that emits vanilla `net.minecraft.gizmos.Gizmos` for occluded world highlights. Invoked
 * from [RenderingEvents.GIZMO] during [net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.BEFORE_GIZMOS],
 * the only window where `Gizmos.*` calls are legal.
 */
fun interface GizmoEvent {
    fun emit(context: LevelRenderContext)
}
