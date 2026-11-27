package fishmod.utils.rendering

import fishmod.utils.debug.FishDiag
import java.util.function.Consumer

class SimpleHandler<T> {

    private val listeners = ArrayList<T>()

    fun register(listener: T) {
        listeners.add(listener)
    }

    fun invoke(action: Consumer<T>) {
        if (listeners.isEmpty()) return
        for (listener in listeners) {
            try {
                action.accept(listener)
            } catch (t: Throwable) {
                FishDiag.fail("SimpleHandler.1", "render listener ${listener?.javaClass?.name} threw", t)
            }
        }
    }

    fun size(): Int = listeners.size

    fun isEmpty(): Boolean = listeners.isEmpty()
}
