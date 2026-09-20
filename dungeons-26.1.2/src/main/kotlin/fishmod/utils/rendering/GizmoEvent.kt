package fishmod.utils.rendering

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext

fun interface GizmoEvent {
    fun emit(context: LevelRenderContext)
}
