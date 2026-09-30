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
        var cancelled = false
        for (listener in listeners) {
            try {
                if (action.test(listener)) cancelled = true
            } catch (t: Throwable) {
                fishmod.utils.debug.FishDiag.fail("Event." + (listener?.javaClass?.name?.substringAfterLast('.') ?: "?"), "listener threw", t)
            }
        }
        return cancelled
    }
}
