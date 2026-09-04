package fishmod.utils.events

import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Predicate

/** Listener returning true cancels the event. */
// invoke may run off the main thread (netty: ON_SERVER_TICK/ON_GAME_MESSAGE/ON_PACKET); copy-on-write keeps register/invoke race-safe.
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
