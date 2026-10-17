package fishmod.features.kuudra

import fishmod.features.croesus.CroesusPrices
import fishmod.features.croesus.CroesusRewardParser
import fishmod.features.mining.Mining
import fishmod.features.mining.MiningHuds
import fishmod.mixin.accessors.HandledScreenAccessor
import fishmod.utils.FishMsg
import fishmod.utils.debug.FishDiag
import fishmod.utils.rendering.DrawEvents
import fishmod.features.kuudra.KuudraSettings as S
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack

// Paid/Free chest value + profit, Vesuvius/Croesus opened highlight, chest counter, perk click blocker
object KuudraChests {

    private val CHEST_TITLE = Regex("^(Free|Paid) Chest$")
    private val AMOUNT = Regex("^(.+?) x(\\d+)$")
    private val COINS = Regex("^([\\d,]+) Coins$")

    private class Parsed(val slot: Int, val lines: List<String>, val value: Double, val cost: Double)
    private var parsed: Parsed? = null
    private var claimed = false

    fun init() {
        ScreenEvents.AFTER_INIT.register(ScreenEvents.AfterInit { _, scr, _, _ ->
            if (scr !is AbstractContainerScreen<*>) return@AfterInit
            parsed = null; claimed = false
            val title = scr.title.string
            if (CHEST_TITLE.matches(title)) {
                ScreenEvents.afterExtract(scr).register(ScreenEvents.AfterExtract { _, ctx, _, _, _ ->
                    if (!(S.kuudraChestsMaster && S.kuudraChestValue)) return@AfterExtract
                    FishDiag.guard("KuudraChests.1", "chest value failed") {
                        if (parsed == null) parsed = parse(scr)
                        val p = parsed ?: return@guard
                        MiningHuds.draw(ctx, p.lines, S.kuudraChestValueX, S.kuudraChestValueY, S.kuudraChestValueScale)
                    }
                })
            }
            if (title == "Perk Menu" || CHEST_TITLE.matches(title)) {
                ScreenMouseEvents.allowMouseClick(scr).register(ScreenMouseEvents.AllowMouseClick { s, _ ->
                    val slot = (s as HandledScreenAccessor).`fishmod$getHoveredSlot`() ?: return@AllowMouseClick true
                    if (slot.container === Minecraft.getInstance().player?.inventory) return@AllowMouseClick true
                    if (title == "Perk Menu") return@AllowMouseClick !blockedPerk(slot.item)
                    if (title == "Paid Chest") onClaimClick(scr, slot.index)
                    true
                })
            }
        })

        DrawEvents.INVENTORY_SLOT_AFTER.register { ctx, stack, x, y ->
            if (!(S.kuudraChestsMaster && S.kuudraOpenedHighlight) || stack.isEmpty) return@register
            val t = Minecraft.getInstance().screen?.title?.string ?: return@register
            if (!t.contains("Vesuvius") && !t.contains("Croesus")) return@register
            val slot = DrawEvents.currentSlot ?: return@register
            if (slot.container === Minecraft.getInstance().player?.inventory) return@register
            val lore = lore(stack)
            val col = when {
                lore.any { it.equals("No more chests to open!", true) } -> 0x80FF3333.toInt()
                lore.any { it.startsWith("Opened Chest") } -> 0x80FFAA00.toInt()
                else -> return@register
            }
            ctx.fill(x, y, x + 16, y + 16, col)
        }

        MiningHuds.reg("Kuudra Chest Value", "kuudra_chest_value", 150, 60,
            { S.kuudraChestValueX }, { S.kuudraChestValueX = it }, { S.kuudraChestValueY }, { S.kuudraChestValueY = it },
            { S.kuudraChestValueScale }, { S.kuudraChestValueScale = it }, { (S.kuudraChestsMaster && S.kuudraChestValue) }) { emptyList() }
    }

    private fun lore(s: ItemStack): List<String> = s.get(DataComponents.LORE)?.lines()?.map { Mining.strip(it.string).trim() } ?: emptyList()

    private fun price(name: String): Double {
        val m = AMOUNT.find(name)
        val base = m?.groupValues?.get(1) ?: name
        val qty = m?.groupValues?.get(2)?.toIntOrNull() ?: 1
        val r = CroesusRewardParser.parseLine(base)
        if (r[0] == "false") return 0.0
        val q = if (r[0].startsWith("ESSENCE_")) (r[1].toIntOrNull() ?: qty) else qty
        return CroesusPrices.price(r[0]) * q
    }

    private fun parse(scr: AbstractContainerScreen<*>): Parsed? {
        CroesusPrices.refreshIfStale()
        val slots = scr.menu.slots
        for (i in 0 until minOf(54, slots.size)) {
            val st = slots[i].item
            if (st.isEmpty) continue
            val lore = lore(st)
            val ci = lore.indexOf("Contents")
            if (ci < 0) continue
            val out = arrayListOf("§6§l${scr.title.string} Value")
            var value = 0.0; var cost = 0.0; var inCost = false
            for (l in lore.drop(ci + 1)) {
                if (l.isEmpty()) continue
                if (l == "Cost") { inCost = true; continue }
                if (l.startsWith("Click") || l.startsWith("Already") || l.startsWith("Can't")) break
                if (inCost) {
                    cost += COINS.find(l)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: if (l == "FREE") 0.0 else price(l)
                    continue
                }
                val v = price(l)
                value += v
                out += "§a${Mining.short(v)}	§f$l"
            }
            out += "§7Cost: §c${Mining.short(cost)}"
            val profit = value - cost
            out += "§eProfit: " + (if (profit >= 0) "§a" else "§c") + Mining.short(profit)
            return Parsed(i, out, value, cost)
        }
        return null
    }

    private fun onClaimClick(scr: AbstractContainerScreen<*>, idx: Int) {
        if (!(S.kuudraChestsMaster && S.kuudraProfit) || claimed) return
        val p = parsed ?: parse(scr) ?: return
        if (idx != p.slot) return
        claimed = true
        S.kuudraProfitTotal += p.value - p.cost
        S.kuudraProfitChests++
        FishMsg.send("§6Kuudra chest: " + (if (p.value - p.cost >= 0) "§a" else "§c") + Mining.short(p.value - p.cost))
    }

    private fun blockedPerk(st: ItemStack): Boolean {
        if (!S.kuudraBlockPerks || st.isEmpty) return false
        val n = Mining.strip(st.hoverName.string)
        return S.kuudraBlockPerksList.split(',').map { it.trim() }.any { it.isNotEmpty() && n.contains(it, true) }
    }

    fun onRunEnd() {
        if (!(S.kuudraChestsMaster && S.kuudraChestCounter)) return
        S.kuudraChestCount++
        if (S.kuudraChestCount >= S.kuudraChestCap) {
            Kuudra.alert("§c§lKuudra chests full", "§7${S.kuudraChestCount}/${S.kuudraChestCap} - claim at Vesuvius")
            if (S.kuudraChestCapParty) Kuudra.party("[FishMod] My Kuudra chests are full (${S.kuudraChestCount}/${S.kuudraChestCap}), need to claim")
        }
    }
}
