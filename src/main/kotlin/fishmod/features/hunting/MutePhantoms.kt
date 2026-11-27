package fishmod.features.hunting

import fishmod.utils.SkyblockLocation
import fishmod.utils.events.FishEvents
import fishmod.features.fishing.FishingWorldSettings as W

object MutePhantoms {
    fun init() {
        FishEvents.sound.register { snd, _, _ ->
            W.mutePhantoms && snd.location.path.startsWith("entity.phantom") &&
                (SkyblockLocation.`in`(SkyblockLocation.GALATEA) || SkyblockLocation.`in`(SkyblockLocation.MOONGLADE_MARSH) || SkyblockLocation.`in`(SkyblockLocation.SAFARI))
        }
    }
}
