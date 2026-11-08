package fishmod.features.fishing

import fishmod.utils.SkyblockLocation
import net.minecraft.client.Minecraft

// Shared fishing state: master gate, area gating, init of all fishing parts.
object Fishing {
    // Worlds Feesh treats as fishing worlds (UNVERIFIED vs our location packet names).
    private val FISHING_WORLDS = setOf(
        SkyblockLocation.BACKWATER_BAYOU, SkyblockLocation.SPIDERS_DEN, SkyblockLocation.HUB, SkyblockLocation.CRYSTAL_HOLLOWS,
        SkyblockLocation.DWARVEN_MINES, SkyblockLocation.JERRYS_WORKSHOP, SkyblockLocation.THE_PARK, SkyblockLocation.THE_FARMING_ISLANDS,
        SkyblockLocation.MOONGLADE_MARSH, SkyblockLocation.LOTUS_ATOLL, SkyblockLocation.TORRHUS_CANYON, SkyblockLocation.CRIMSON_ISLE,
        SkyblockLocation.PRIVATE_ISLAND,
    )

    // Master toggle only; cheapest check, call first.
    @JvmStatic fun enabled(): Boolean = FishingSettings.fishing

    // Master toggle on and on SkyBlock in a fishing world (or the any-area override).
    @JvmStatic fun active(): Boolean {
        if (!FishingSettings.fishing) return false
        if (FishingSettings.fishingAnyArea) return true
        return SkyblockLocation.inSkyblock() && SkyblockLocation.getCurrentLocation() in FISHING_WORLDS
    }

    @JvmStatic fun location(): SkyblockLocation = SkyblockLocation.getCurrentLocation()

    // True in the given world, or anywhere with the any-area override.
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
