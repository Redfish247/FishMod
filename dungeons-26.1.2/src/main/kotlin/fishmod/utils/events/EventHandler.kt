package fishmod.utils.events

import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Predicate

class EventHandler<T> {

    private val listeners = CopyOnWriteArrayList<T>()

    fun register(listener: T) {
        listeners.add(listener)
    }

    fun isEmpty(): Boolean = listeners.isEmpty()

    fun invoke(action: Predicate<T>): Boolean {
        if (listeners.isEmpty()) return false
        for (listener in listeners) {
            if (action.test(listener)) {
                return true
            }
        }
        return false
    }
}
