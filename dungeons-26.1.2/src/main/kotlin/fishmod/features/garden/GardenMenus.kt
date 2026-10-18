package fishmod.features.garden

import fishmod.utils.rendering.DrawEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import fishmod.features.garden.GardenExtraSettings as X

// Composter upgrade prices, Stereo Harmony, Greenhouse status, Anita shop, farming toolkit icons (lore/title formats are guesses)
object GardenMenus {

    private var tick = 0

    // ---- Composter upgrades ----
    private val COST = Regex("""^([\d,]+(?:\.\d+)?)\s+(.+?)$""")
    private var cheapest: ItemStack? = null

    private class Cost(val coins: Double, val raw: Double, val parts: List<Pair<Double, String>>)

    private fun costOf(lore: List<String>): Cost? {
        val i = lore.indexOfFirst { it.trim().startsWith("Cost", true) || it.trim().startsWith("Upgrade Cost", true) }
        if (i < 0) return null
        val parts = ArrayList<Pair<Double, String>>()
        val inline = lore[i].substringAfter(':', "").trim()
        if (inline.isNotEmpty()) COST.find(inline)?.let { parts += GardenExtras.num(it.groupValues[1]) to it.groupValues[2] }
        for (l in lore.drop(i + 1)) {
            val t = l.trim()
            if (t.isEmpty()) break
            COST.find(t)?.let { parts += GardenExtras.num(it.groupValues[1]) to it.groupValues[2] }
        }
        if (parts.isEmpty()) return null
        val coins = parts.sumOf { (n, name) -> n * Garden.priceOf(name) }
        return Cost(coins, parts.sumOf { it.first }, parts)
    }

    private fun pickCheapest() {
        val costs = GardenExtras.slots().mapNotNull { s -> costOf(Garden.lore(s.item))?.let { s.item to it } }
        val priced = costs.filter { it.second.coins > 0 }
        cheapest = (if (priced.size == costs.size) priced.minByOrNull { it.second.coins } else costs.minByOrNull { it.second.raw })?.first
    }

    // ---- Stereo Harmony ----
    private val PEST_CROP = linkedMapOf(
        "fly" to "Wheat", "cricket" to "Carrot", "locust" to "Potato", "rat" to "Pumpkin", "earthworm" to "Melon",
        "slug" to "Mushroom", "moth" to "Cocoa Beans", "mite" to "Cactus", "mosquito" to "Sugar Cane", "beetle" to "Nether Wart",
    )
    private val VINYL_PEST = mapOf(
        "pretty fly" to "fly", "cricket choir" to "cricket", "rodent revolution" to "rat", "earthlings" to "earthworm",
        "slow and groovy" to "slug", "wings of harmony" to "moth", "buzzin' beats" to "mosquito", "cicada symphony" to "beetle",
    )

    private fun pestOf(name: String, lore: List<String>): String? =
        VINYL_PEST.entries.firstOrNull { name.contains(it.key, true) }?.value
            ?: PEST_CROP.keys.firstOrNull { p -> lore.any { Regex("\\b$p\\b", RegexOption.IGNORE_CASE).containsMatchIn(it) } }

    private fun isVinyl(name: String, lore: List<String>) = VINYL_PEST.keys.any { name.contains(it, true) } || lore.any { it.contains("vinyl", true) }

    private fun scanStereo() {
        val vinyls = GardenExtras.slots().filter { isVinyl(GardenExtras.name(it.item), Garden.lore(it.item)) }
        if (vinyls.isEmpty()) return
        val sel = vinyls.firstOrNull { s -> Garden.lore(s.item).any { it.contains("SELECTED", true) || it.contains("Currently playing", true) } }
        X.gardenStereoActive = sel?.let { GardenExtras.name(it.item) } ?: ""
    }

    private fun stereoLines(): List<String> {
        val n = X.gardenStereoActive
        val pest = pestOf(n, emptyList())
        val crop = pest?.let { PEST_CROP[it] }
        val out = arrayListOf(Garden.legacy(X.gardenStereoColor) + "§lStereo Harmony", "§f$n")
        if (pest != null) out += "§7Pest: §f${pest.replaceFirstChar { it.uppercase() }}" + (crop?.let { " §7| Crop: §a$it" } ?: "")
        return out
    }

    // ---- Greenhouse ----
    private var greenhouse: List<String> = emptyList()

    private fun greenhouseLines(): List<String> {
        val good = Garden.legacy(X.gardenGreenhouseGoodColor); val bad = Garden.legacy(X.gardenGreenhouseBadColor)
        val out = arrayListOf(good + "§lGreenhouse")
        for (l in greenhouse) {
            val v = l.substringAfter(':', "").trim()
            val c = when {
                l.contains("Harvestable", true) -> if (v.firstOrNull()?.isDigit() == true && !v.startsWith("0")) good else "§7"
                l.contains("Water", true) -> if (v.contains("empty", true) || v.contains("low", true) || v.startsWith("0")) bad else good
                else -> "§7"
            }
            out += c + l
        }
        return out
    }

    // ---- Anita shop ----
    private val MEDAL = Regex("""([\d,]+)\s+(Bronze|Silver|Gold)\s+Medals?""", RegexOption.IGNORE_CASE)
    private val ITEM_NAME = Regex("""^(?:(\d+)x\s+)?(.+?)(?:\s+x(\d+))?$""")
    private var anitaBest: ItemStack? = null

    private fun goldCost(lore: List<String>): Double? =
        lore.firstNotNullOfOrNull { l -> MEDAL.find(l)?.takeIf { it.groupValues[2].equals("Gold", true) } }?.let { GardenExtras.num(it.groupValues[1]) }

    // Coins per gold medal from the item's bazaar price
    private fun anitaRate(stack: ItemStack): Double? {
        val c = goldCost(Garden.lore(stack)) ?: return null
        if (c <= 0) return null
        val m = ITEM_NAME.find(GardenExtras.name(stack)) ?: return null
        val amt = (m.groupValues[1].ifEmpty { m.groupValues[3] }).toIntOrNull() ?: 1
        val p = Garden.priceOf(m.groupValues[2])
        return if (p > 0) p * amt / c else null
    }

    private fun pickAnita() {
        anitaBest = GardenExtras.slots().mapNotNull { s -> anitaRate(s.item)?.let { s.item to it } }.maxByOrNull { it.second }?.first
    }

    // Slot already shows the crop (pumpkin/melon come as block variants), so no badge on top
    private fun sameCrop(crop: net.minecraft.world.item.Item, held: net.minecraft.world.item.Item): Boolean {
        if (crop == held) return true

        return when (crop) {
            net.minecraft.world.item.Items.PUMPKIN -> held == net.minecraft.world.item.Items.CARVED_PUMPKIN || held == net.minecraft.world.item.Items.JACK_O_LANTERN
            net.minecraft.world.item.Items.MELON_SLICE -> held == net.minecraft.world.item.Items.MELON
            else -> false
        }
    }

    private fun toolkit() = GardenExtras.inMenu("Toolkit", "Farming Tools")

    private fun tooltip(stack: ItemStack, lines: MutableList<Component>) {
        if (stack.isEmpty) return
        if (X.gardenCompUpgrades && GardenExtras.inMenu("Composter Upgrades")) {
            val c = costOf(Garden.lore(stack)) ?: return
            lines += Component.literal("")
            lines += Component.literal((if (stack === cheapest) "§a§l" else "§6§l") + "FishMod Upgrade Cost" + if (stack === cheapest) " §a(cheapest)" else "")
            for ((n, name) in c.parts) lines += Component.literal("§7$name: §f${GardenExtras.floorShort(n)}")
            if (c.coins > 0) lines += Component.literal("§7Coins: §6${GardenExtras.floorShort(c.coins)}")
        }
        if (X.gardenAnita && GardenExtras.inMenu("Anita")) {
            val name = GardenExtras.name(stack)
            if (name.contains("Farming Fortune", true)) {
                val cost = goldCost(Garden.lore(stack)) ?: return
                lines += Component.literal("")
                lines += Component.literal("§6§lFishMod Anita")
                lines += Component.literal("§7Per level: §6+2 ☘ §7for §e${GardenExtras.floorShort(cost)} §7gold medals")
                lines += Component.literal("§7Gold medals per ☘: §e${"%.1f".format(cost / 2.0)}")
            } else anitaRate(stack)?.let {
                lines += Component.literal("")
                lines += Component.literal((if (stack === anitaBest) "§a§l" else "§6§l") + "FishMod Medal Profit" + if (stack === anitaBest) " §a(best)" else "")
                lines += Component.literal("§7Coins per gold medal: §6${GardenExtras.floorShort(it)}")
            }
        }
    }

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            if (mc.player == null) return@register
            if (mc.screen == null) { cheapest = null; anitaBest = null }
            else if (tick % 5 == 0) {
                if (X.gardenCompUpgrades && GardenExtras.inMenu("Composter Upgrades")) pickCheapest()
                if (X.gardenAnita && GardenExtras.inMenu("Anita")) pickAnita()
                if (X.gardenStereo && GardenExtras.inMenu("Stereo Harmony")) scanStereo()
            }
            if (tick++ % 20 == 0) greenhouse = if (X.gardenGreenhouse && Garden.inGarden()) fishmod.utils.TabListCache.entries.map { it.stripped.trim() }
                .filter { it.contains("Harvestable", true) || (it.contains("Water", true) && it.contains(':')) } else emptyList()
        }
        ItemTooltipCallback.EVENT.register(ItemTooltipCallback { stack, _, _, lines -> tooltip(stack, lines) })
        DrawEvents.INVENTORY_SLOT_AFTER.register { ctx, stack, x, y ->
            if (stack.isEmpty) return@register
            if (X.gardenCompUpgrades && stack === cheapest && GardenExtras.inMenu("Composter Upgrades"))
                ctx.fill(x, y, x + 16, y + 16, fishmod.features.mining.Mining.alpha(X.gardenCompUpgradesColor, 45))
            if (X.gardenAnita && stack === anitaBest && GardenExtras.inMenu("Anita"))
                ctx.fill(x, y, x + 16, y + 16, fishmod.features.mining.Mining.alpha(X.gardenAnitaColor, 45))
            if (X.gardenStereo && GardenExtras.inMenu("Stereo Harmony")) {
                val n = GardenExtras.name(stack); val lore = Garden.lore(stack)
                if (isVinyl(n, lore)) pestOf(n, lore)?.let { p ->
                    GardenExtras.cropItem(PEST_CROP[p] ?: "")?.let { ctx.fill(x, y, x + 16, y + 16, 0xFF2B2B2B.toInt()); ctx.item(ItemStack(it), x, y) }
                }
            }
            if (X.gardenToolkitIcons && toolkit()) GardenExtras.cropItem(GardenExtras.name(stack))?.takeIf { !sameCrop(it, stack.item) }?.let { GardenExtras.badge(ctx, it, x, y) }
        }
        Garden.hud("Stereo Harmony", "garden_stereo", 130, 30,
            { X.gardenStereoHudX }, { X.gardenStereoHudX = it }, { X.gardenStereoHudY }, { X.gardenStereoHudY = it },
            { X.gardenStereoHudScale }, { X.gardenStereoHudScale = it },
            { X.gardenStereo && Garden.inGarden() && X.gardenStereoActive.isNotEmpty() }) { stereoLines() }
        Garden.hud("Greenhouse", "garden_greenhouse", 130, 30,
            { X.gardenGreenhouseHudX }, { X.gardenGreenhouseHudX = it }, { X.gardenGreenhouseHudY }, { X.gardenGreenhouseHudY = it },
            { X.gardenGreenhouseHudScale }, { X.gardenGreenhouseHudScale = it },
            { X.gardenGreenhouse && Garden.inGarden() && greenhouse.isNotEmpty() }) { greenhouseLines() }
    }
}
