package fishmod.features.garden

import fishmod.utils.events.FishEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.minecraft.world.InteractionResult
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import fishmod.features.garden.GardenExtraSettings as X

// Client-side crop break counter: blocks/sec (5s) and crops/min per type (60s)
object GardenBps {

    private val CROPS: Map<Block, String> = mapOf(
        Blocks.WHEAT to "Wheat", Blocks.CARROTS to "Carrot", Blocks.POTATOES to "Potato", Blocks.PUMPKIN to "Pumpkin",
        Blocks.MELON to "Melon", Blocks.RED_MUSHROOM to "Mushroom", Blocks.BROWN_MUSHROOM to "Mushroom",
        Blocks.COCOA to "Cocoa", Blocks.CACTUS to "Cactus", Blocks.SUGAR_CANE to "Sugar Cane", Blocks.NETHER_WART to "Nether Wart",
    )
    private val breaks = ArrayDeque<Pair<Long, String>>()

    private fun prune(now: Long) { while (breaks.isNotEmpty() && now - breaks.first().first > 60_000L) breaks.removeFirst() }

    private fun lines(): List<String> {
        val now = System.currentTimeMillis(); prune(now)
        val c = Garden.legacy(X.gardenBpsColor)
        val bps = breaks.count { now - it.first <= 5_000L } / 5.0
        val out = ArrayList<String>()
        if (X.gardenBps) out += "${c}BPS §f${String.format("%.1f", bps)}"
        if (X.gardenCropSpeed) breaks.groupingBy { it.second }.eachCount().entries.sortedByDescending { it.value }
            .forEach { out += "${c}${it.key} §f${it.value}/min" }
        return out
    }

    fun init() {
        AttackBlockCallback.EVENT.register(AttackBlockCallback { player, level, _, pos, _ ->
            if (level.isClientSide && (X.gardenBps || X.gardenCropSpeed) && Garden.inGarden() && player === net.minecraft.client.Minecraft.getInstance().player) {
                CROPS[level.getBlockState(pos).block]?.let { breaks.addLast(System.currentTimeMillis() to it); prune(System.currentTimeMillis()) }
            }
            InteractionResult.PASS
        })
        FishEvents.worldChange.register { breaks.clear(); false }
        Garden.hud("Farming Speed", "garden_bps", 90, 30,
            { X.gardenBpsHudX }, { X.gardenBpsHudX = it }, { X.gardenBpsHudY }, { X.gardenBpsHudY = it },
            { X.gardenBpsHudScale }, { X.gardenBpsHudScale = it },
            { (X.gardenBps || X.gardenCropSpeed) && Garden.inGarden() }, { X.gardenBps || X.gardenCropSpeed },
            example = { listOf("§6BPS §f19.8", "§6Wheat §f1180/min") }) { lines() }
    }
}
