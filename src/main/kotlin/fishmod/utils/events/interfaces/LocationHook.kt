package fishmod.utils.events.interfaces

import fishmod.utils.SkyblockLocation

fun interface LocationHook {
    fun onLocationChange(newLocation: SkyblockLocation): Boolean
}
