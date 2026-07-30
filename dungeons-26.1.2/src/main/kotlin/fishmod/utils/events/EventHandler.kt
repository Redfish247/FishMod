package fishmod.utils.events

import java.util.function.Predicate

/**
 * This class handles some specified Event
 * to cancel the event if implemented return true
 * else return false
 */
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
