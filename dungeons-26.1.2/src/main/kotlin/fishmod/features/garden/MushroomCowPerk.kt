package fishmod.features.garden

import fishmod.compat.curScreen
import fishmod.features.PetHud
import fishmod.features.item.ItemRarity
import fishmod.utils.config.FishConfig
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.Events
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import fishmod.features.garden.GardenMilestoneSettings as S

// Mooshroom Cow pet perk (SkyHanni's logic): each crop broken with the pet at Rare+ adds mushroom collection with chance = pet level %.
// The counter is synced from the Crop Milestones menu, then grown by the expected value per break.
object MushroomCowPerk {

    private val TIERS = intArrayOf(
        30, 50, 80, 200, 350, 700, 1500, 2500, 3500, 5000, 6500, 8000, 10000, 20000, 35000, 50000, 75000, 100000, 175000, 250000,
        375000, 400000, 450000, 650000, 800000, 800000, 800000, 800000, 800000, 800000, 800000, 800000, 800000, 800000, 800000,
        800000, 800000, 800000, 800000, 800000, 800000, 800000, 800000, 800000, 800000, 800000
    )
    private val TOTAL = Regex("Total: ([\\d,]+)")
    private val CROP_WORDS = listOf("wheat", "carrots", "potatoes", "nether_wart", "pumpkin", "melon", "cocoa", "sugar_cane", "cactus", "sunflower", "moonflower", "rose")

    private var total = -1.0
    private val breaks = ArrayDeque<Long>()
    private var tick = 0
    private var dirty = false
    private var lastSave = 0L

    private fun petActive(): Boolean {
        val n = PetHud.activePetName() ?: return false
        return (n.contains("Mooshroom", true) || n.contains("Mushroom Cow", true)) && PetHud.activePetRarity().ordinal >= ItemRarity.RARE.ordinal
    }

    private fun isCropBlock(state: BlockState): Boolean {
        val id = BuiltInRegistries.BLOCK.getKey(state.block).path
        return !id.contains("stem") && !id.contains("mushroom") && CROP_WORDS.any { id.contains(it) }
    }

    fun init() {
        if (S.gardenMushroomTotal >= 0) total = S.gardenMushroomTotal.toDouble()
        Events.ON_PACKET.register { packet ->
            if (packet is ClientboundBlockUpdatePacket) try { onBlock(packet) } catch (e: Exception) { FishDiag.fail("MushroomCowPerk.1", "mushroom perk block handler threw", e) }
            false
        }
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player == null) return@register
            if (tick++ % 10 == 0) try { scanMenu() } catch (e: Exception) { FishDiag.fail("MushroomCowPerk.2", "mushroom milestone menu scan threw", e) }
            if (dirty && System.currentTimeMillis() - lastSave > 30_000) {
                S.gardenMushroomTotal = total.toLong(); FishConfig.manager.save(); dirty = false; lastSave = System.currentTimeMillis()
            }
        }
        Garden.hud("Mooshroom Cow Perk", "garden_mushroom", 150, 36,
            { S.gardenMushroomHudX }, { S.gardenMushroomHudX = it }, { S.gardenMushroomHudY }, { S.gardenMushroomHudY = it },
            { S.gardenMushroomHudScale }, { S.gardenMushroomHudScale = it },
            { S.gardenMushroom && Garden.inGarden() && petActive() }, { S.gardenMushroom }) { hudLines() }
    }

    private fun onBlock(p: ClientboundBlockUpdatePacket) {
        if (!S.gardenMushroom || !Garden.inGarden() || !petActive() || total < 0) return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val me = mc.player ?: return
        if (!p.blockState.`is`(Blocks.AIR) || me.blockPosition().distSqr(p.pos) > 64) return
        if (!isCropBlock(level.getBlockState(p.pos))) return
        total += PetHud.activePetLevel().coerceIn(0, 100) / 100.0
        dirty = true
        val now = System.currentTimeMillis()
        breaks.addLast(now)
        while (breaks.isNotEmpty() && now - breaks.first() > 60_000) breaks.removeFirst()
    }

    private fun scanMenu() {
        val scr = Minecraft.getInstance().curScreen as? AbstractContainerScreen<*> ?: return
        if (!Garden.strip(scr.title.string).contains("Crop Milestones")) return
        for (s in scr.menu.slots) {
            val lore = Garden.lore(s.item)
            if (lore.none { it.startsWith("Harvest Mushroom on") }) continue
            val m = lore.firstNotNullOfOrNull { TOTAL.find(it) } ?: continue
            total = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
            dirty = true
            return
        }
    }

    private fun hudLines(): List<String> {
        val c = Garden.legacy(S.gardenMushroomColor)
        if (total < 0) return listOf("$c§lMooshroom Cow Perk", "§cOpen §e/cropmilestones §cto sync")
        var rest = total.toLong()
        var tier = 0
        while (tier < TIERS.size && rest >= TIERS[tier]) { rest -= TIERS[tier]; tier++ }
        val step = if (tier < TIERS.size) TIERS[tier].toLong() else TIERS.last().toLong()
        if (tier >= TIERS.size) { val extra = rest / step; tier += extra.toInt(); rest -= extra * step }
        val out = arrayListOf(
            "$c§lMooshroom Cow Perk",
            "§7Mushroom Milestone §f$tier",
            "§e${"%,d".format(rest)}§8/§e${"%,d".format(step)} §8(${"%.1f".format(rest * 100.0 / step)}%)"
        )
        if (S.gardenMushroomEta && breaks.size >= 5) {
            val perSec = breaks.size / 60.0 * PetHud.activePetLevel().coerceIn(0, 100) / 100.0
            if (perSec > 0) out += "§7ETA: §b" + eta(((step - rest) / perSec).toLong())
        }
        return out
    }

    private fun eta(sec: Long): String = when {
        sec >= 3600 -> "${sec / 3600}h ${sec % 3600 / 60}m"
        sec >= 60 -> "${sec / 60}m ${sec % 60}s"
        else -> "${sec}s"
    }
}
