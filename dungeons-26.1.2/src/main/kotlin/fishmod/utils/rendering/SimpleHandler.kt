package fishmod.utils.rendering

import java.util.function.Consumer

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

    fun isEmpty(): Boolean = listeners.isEmpty()
}
