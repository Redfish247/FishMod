package fishmod.utils.rendering

import java.util.function.Consumer

class RenderHandler {

    private val listeners = ArrayList<RenderingEvent>()

    fun register(listener: RenderingEvent) {
        listeners.add(listener)
    }

    fun invoke(action: Consumer<RenderingEvent>) {
        if (listeners.isEmpty()) return
        for (listener in listeners) {
            action.accept(listener)
        }
    }

}
