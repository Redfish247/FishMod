package fishmod.utils.rendering

import java.util.function.Consumer

/** Generic listener-list handler: register(T) + invoke(Consumer<T>) + size(). Replaces the
 *  near-identical bespoke GizmoHandler/RenderHandler/DrawHandler classes that each hand-rolled
 *  the same ~15 lines. */
class SimpleHandler<T> {

    private val listeners = ArrayList<T>()

    fun register(listener: T) {
        listeners.add(listener)
    }

    fun invoke(action: Consumer<T>) {
        if (listeners.isEmpty()) return
        for (listener in listeners) {
            action.accept(listener)
        }
    }

    fun size(): Int = listeners.size
}
