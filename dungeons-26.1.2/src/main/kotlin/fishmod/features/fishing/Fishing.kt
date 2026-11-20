package fishmod.features.fishing

import fishmod.utils.SkyblockLocation
import net.minecraft.client.Minecraft
import net.minecraft.world.item.FishingRodItem

object Fishing {
    private val FISHING_WORLDS = setOf(
        SkyblockLocation.BACKWATER_BAYOU, SkyblockLocation.SPIDERS_DEN, SkyblockLocation.HUB, SkyblockLocation.CRYSTAL_HOLLOWS,
        SkyblockLocation.DWARVEN_MINES, SkyblockLocation.JERRYS_WORKSHOP, SkyblockLocation.THE_PARK, SkyblockLocation.THE_FARMING_ISLANDS,
        SkyblockLocation.MOONGLADE_MARSH, SkyblockLocation.LOTUS_ATOLL, SkyblockLocation.TORRHUS_CANYON, SkyblockLocation.CRIMSON_ISLE,
        SkyblockLocation.PRIVATE_ISLAND,
    )

    @JvmStatic fun enabled(): Boolean = FishingSettings.fishing

    @JvmStatic fun active(): Boolean {
        if (!FishingSettings.fishing || !SkyblockLocation.inSkyblock() || SkyblockLocation.inDungeon()) return false
        if (FishingSettings.fishingAnyArea) return true
        return SkyblockLocation.getCurrentLocation() in FISHING_WORLDS
    }

    // Trackers only count in Skyblock, outside dungeons, with a fishing rod in hand
    @JvmStatic fun holdingRod(): Boolean {
        val p = Minecraft.getInstance().player ?: return false
        return p.mainHandItem.item is FishingRodItem || p.offhandItem.item is FishingRodItem
    }

    @JvmStatic fun trackable(): Boolean =
        SkyblockLocation.inSkyblock() && !SkyblockLocation.inDungeon() && holdingRod()

    @JvmStatic fun location(): SkyblockLocation = SkyblockLocation.getCurrentLocation()

    @JvmStatic fun inArea(loc: SkyblockLocation): Boolean = FishingSettings.fishingAnyArea || location() == loc

    @JvmStatic fun ownName(): String? = Minecraft.getInstance().user.name

    @JvmStatic
    fun init() {
        FishingEvents.init()
        FishingAlerts.init()
        FishingTrackers.init()
        FishingWorld.init()
        BaitHud.init()
        ShardTracker.init()
    }
}
