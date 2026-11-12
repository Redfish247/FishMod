package fishmod.features.garden

import fishmod.utils.data.ItemTools
import net.minecraft.client.Minecraft

// Held farming tool -> crop, plus per-crop optimal speed/angles (values from SkyHanni memory, unverified)
object GardenCrops {

    enum class Crop(val display: String, val color: String, val speed: Int, val yaw: Double, val pitch: Double) {
        WHEAT("Wheat", "§6", 93, -90.0, 3.0), CARROT("Carrot", "§6", 93, -90.0, 3.0), POTATO("Potato", "§e", 93, -90.0, 3.0),
        NETHER_WART("Nether Wart", "§4", 93, -90.0, 3.0), PUMPKIN("Pumpkin", "§6", 155, -180.0, -45.0), MELON("Melon", "§a", 155, -180.0, -58.0),
        COCOA("Cocoa Beans", "§6", 155, -90.0, -45.0), SUGAR_CANE("Sugar Cane", "§a", 328, 45.0, 0.0), CACTUS("Cactus", "§2", 464, -90.0, 0.0),
        MUSHROOM("Mushroom", "§c", 233, -115.0, 0.0),
        // Speed/angles for the three flowers are a guess until confirmed in game
        SUNFLOWER("Sunflower", "§e", 155, -90.0, 0.0), MOONFLOWER("Moonflower", "§9", 155, -90.0, 0.0), WILD_ROSE("Wild Rose", "§c", 155, -90.0, 0.0),
    }

    // Tool id fragments, checked in order
    private val TOOLS = listOf(
        "WHEAT" to Crop.WHEAT, "CARROT" to Crop.CARROT, "POTATO" to Crop.POTATO, "WARTS" to Crop.NETHER_WART, "NETHER" to Crop.NETHER_WART,
        "PUMPKIN" to Crop.PUMPKIN, "MELON" to Crop.MELON, "COCO" to Crop.COCOA, "CANE" to Crop.SUGAR_CANE, "CACTUS" to Crop.CACTUS,
        "FUNGI" to Crop.MUSHROOM, "MUSHROOM" to Crop.MUSHROOM,
        "SUNFLOWER" to Crop.SUNFLOWER, "MOONFLOWER" to Crop.MOONFLOWER, "WILD_ROSE" to Crop.WILD_ROSE,
    )

    fun heldCrop(): Crop? {
        val id = Minecraft.getInstance().player?.mainHandItem?.let { ItemTools.getId(it) } ?: return null
        return TOOLS.firstOrNull { id.contains(it.first) }?.second
    }

    fun holdingFarmingTool() = heldCrop() != null
}
