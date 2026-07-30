package fishmod.utils.rendering

import java.util.function.Consumer

class DrawHandler<T> {

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

}
