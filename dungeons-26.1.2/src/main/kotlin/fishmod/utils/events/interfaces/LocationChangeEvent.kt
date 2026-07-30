package fishmod.utils.events.interfaces

import fishmod.utils.Location

fun interface LocationChangeEvent {
    fun onLocationChange(newLocation: Location): Boolean
}
