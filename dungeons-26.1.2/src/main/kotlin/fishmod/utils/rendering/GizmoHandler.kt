package fishmod.utils.rendering

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import java.util.function.Consumer

/**
 * A handler that emits vanilla `net.minecraft.gizmos.Gizmos` for occluded world highlights. Invoked
 * from [RenderingEvents.GIZMO] during [net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.BEFORE_GIZMOS],
 * the only window where `Gizmos.*` calls are legal.
 */
fun interface GizmoEvent {
    fun emit(context: LevelRenderContext)
}

class GizmoHandler {

    private val listeners = ArrayList<GizmoEvent>()

    fun register(listener: GizmoEvent) {
        listeners.add(listener)
    }

    fun invoke(action: Consumer<GizmoEvent>) {
        if (listeners.isEmpty()) return
        for (listener in listeners) {
            action.accept(listener)
        }
    }

    fun size(): Int = listeners.size
}
