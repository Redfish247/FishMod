package fishmod.features.fishing

import fishmod.utils.SkyblockLocation
import net.minecraft.client.Minecraft

object Fishing {
    private val FISHING_WORLDS = setOf(
        SkyblockLocation.BACKWATER_BAYOU, SkyblockLocation.SPIDERS_DEN, SkyblockLocation.HUB, SkyblockLocation.CRYSTAL_HOLLOWS,
        SkyblockLocation.DWARVEN_MINES, SkyblockLocation.JERRYS_WORKSHOP, SkyblockLocation.THE_PARK, SkyblockLocation.THE_FARMING_ISLANDS,
        SkyblockLocation.MOONGLADE_MARSH, SkyblockLocation.LOTUS_ATOLL, SkyblockLocation.TORRHUS_CANYON, SkyblockLocation.CRIMSON_ISLE,
        SkyblockLocation.PRIVATE_ISLAND,
    )

    @JvmStatic fun enabled(): Boolean = FishingSettings.fishing

    @JvmStatic fun active(): Boolean {
        if (!FishingSettings.fishing) return false
        if (FishingSettings.fishingAnyArea) return true
        return SkyblockLocation.inSkyblock() && SkyblockLocation.getCurrentLocation() in FISHING_WORLDS
    }

    @JvmStatic fun location(): SkyblockLocation = SkyblockLocation.getCurrentLocation()

    @JvmStatic fun inArea(loc: SkyblockLocation): Boolean = FishingSettings.fishingAnyArea || location() == loc

    @JvmStatic fun ownName(): String? = Minecraft.getInstance().user.name

    @JvmStatic
    fun init() {
        FishingEvents.init()
        FishingAlerts.init()
        FishingTrackers.init()
        FishingWorld.init()
    }
}
