package fishmod.utils.events

import java.util.function.Predicate

/** Registered listeners for an event; a listener returning true cancels the event. */
class EventHandler<T> {

    private val listeners = ArrayList<T>()

    fun register(listener: T) {
        listeners.add(listener)
    }

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
