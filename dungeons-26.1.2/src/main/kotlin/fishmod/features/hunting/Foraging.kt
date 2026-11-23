package fishmod.features.hunting

import fishmod.compat.curScreen
import fishmod.features.ProfitTrackerKit
import fishmod.features.dungeon.trackers.CroesusPrices
import fishmod.features.mining.Mining
import fishmod.features.mining.MiningHuds
import fishmod.utils.SkyblockLocation
import fishmod.utils.TextStrip
import fishmod.utils.data.ItemTools
import fishmod.utils.debug.FishDiag
import fishmod.utils.events.FishEvents
import fishmod.utils.networth.ItemsDb
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.tags.BlockTags
import net.minecraft.world.InteractionResult
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import fishmod.features.fishing.FishingWorldSettings as W

// Foraging: profit tracker (inventory + sack gains after chopping logs) and Treecapitator cooldown HUD
object Foraging {
    private val ISLANDS = setOf(SkyblockLocation.HUB, SkyblockLocation.THE_PARK, SkyblockLocation.GALATEA,
        SkyblockLocation.MOONGLADE_MARSH, SkyblockLocation.TORRHUS_CANYON, SkyblockLocation.LOTUS_ATOLL)
    // from Skyblocker (ItemCooldowns): axes with the tree-felling cooldown
    private val AXES = setOf("TREECAPITATOR_AXE", "JUNGLE_AXE", "FIG_AXE", "FIGSTONE_AXE")
    private val SACK_LINE = Regex("([+-])([\\d,]+) ([^(\n]+)")
    private const val GAIN_WINDOW_MS = 3_000L
    private const val SACK_WINDOW_MS = 35_000L

    private val items = LinkedHashMap<String, Long>()
    private val names = HashMap<String, String>()
    private var startMs = 0L
    private var activeMs = 0L
    private var lastGainMs = 0L
    private var lastChopMs = 0L
    private var cooldownEnd = 0L
    private var target: BlockPos? = null
    private var targetMs = 0L
    private var snap: Map<String, Int>? = null

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { mc -> FishDiag.guard("Foraging.1", "foraging tick threw") { tick(mc) } }
        FishEvents.chat.register { c ->
            if (W.foragingTracker && TextStrip.plain(c.string).startsWith("[Sacks]")) FishDiag.guard("Foraging.2", "foraging sack parse threw") { onSacks(c) }
            false
        }
        FishEvents.worldChange.register { snap = null; target = null; if (W.foragingTrackerResetOnSwap) reset(); false }
        // from Skyblocker (ItemCooldowns): right-click use also starts the cooldown
        UseItemCallback.EVENT.register(UseItemCallback { player, _, _ ->
            if (W.treecapTimer && heldAxe(player.mainHandItem.let { ItemTools.getId(it) })) startCooldown()
            InteractionResult.PASS
        })
        MiningHuds.reg("Foraging Tracker", "foraging_tracker", 150, 80,
            { W.foragingTrackerX }, { W.foragingTrackerX = it }, { W.foragingTrackerY }, { W.foragingTrackerY = it },
            { W.foragingTrackerScale }, { W.foragingTrackerScale = it },
            { W.foragingTracker && items.isNotEmpty() && onIsland() }, { W.foragingTracker },
            example = { listOf("§2§lForaging Profit", "§6380k §7(1.1M/h)\t§7| §fFig Log §f4,210", "§630k §7(90k/h)\t§7| §5Sweep Booster §f37",
                "§7Logs §f4,210 §7(§f12.6k§7/h)", "§7Drops §f37", "§7Playtime §f20m 4s", "§eProfit §6410k §7(1.2M/h)") }, lines = ::lines)
        MiningHuds.reg("Treecapitator Cooldown", "treecap_cooldown", 90, 10,
            { W.treecapX }, { W.treecapX = it }, { W.treecapY }, { W.treecapY = it },
            { W.treecapScale }, { W.treecapScale = it },
            { W.treecapTimer && heldAxe(Minecraft.getInstance().player?.mainHandItem?.let { ItemTools.getId(it) }) }, { W.treecapTimer },
            example = { listOf("§2Treecapitator §e0.4s") }) {
            val left = cooldownEnd - System.currentTimeMillis()
            listOf(if (left > 0) "§2Treecapitator §e" + String.format(java.util.Locale.ROOT, "%.1fs", left / 1000.0) else "§2Treecapitator §aReady")
        }
    }

    fun reset() { items.clear(); startMs = 0L; activeMs = 0L; lastGainMs = 0L }

    private fun onIsland() = ISLANDS.any { SkyblockLocation.`in`(it) }

    private fun heldAxe(id: String?) = id != null && id in AXES

    private fun startCooldown() {
        val now = System.currentTimeMillis()
        if (now >= cooldownEnd) cooldownEnd = now + W.treecapCooldownMs
    }

    private fun tick(mc: Minecraft) {
        val player = mc.player ?: return
        val level = mc.level ?: return
        if (!W.foragingTracker && !W.treecapTimer) return
        if (!onIsland()) { snap = null; return }
        val now = System.currentTimeMillis()
        // log chop = a log we were hitting turns into something else
        val t = target
        if (t != null && !level.getBlockState(t).`is`(BlockTags.LOGS)) {
            target = null
            if (now - targetMs < 1500) {
                lastChopMs = now
                if (W.treecapTimer && heldAxe(ItemTools.getId(player.mainHandItem))) startCooldown()
            }
        }
        if (mc.options.keyAttack.isDown) {
            val hit = mc.hitResult
            if (hit is BlockHitResult && hit.type == HitResult.Type.BLOCK && level.getBlockState(hit.blockPos).`is`(BlockTags.LOGS)) {
                target = hit.blockPos.immutable(); targetMs = now
            }
        }
        if (!W.foragingTracker) return
        if (mc.curScreen != null) { snap = null; return }
        val cur = HashMap<String, Int>()
        val inv = player.inventory
        for (i in 0 until 36) {
            val st = inv.getItem(i)
            if (st.isEmpty) continue
            val id = ItemTools.getId(st) ?: continue
            cur[id] = (cur[id] ?: 0) + st.count
            // hoverName string work only once per item id, not every tick
            if (id !in names) names[id] = TextStrip.plain(st.hoverName.string)
        }
        val prev = snap
        snap = cur
        if (prev == null || now - lastChopMs > GAIN_WINDOW_MS) return
        for ((id, n) in cur) { val d = n - (prev[id] ?: 0); if (d > 0) add(id, d.toLong()) }
    }

    // [Sacks] hover lists stocked items; count those that landed shortly after chopping
    private fun onSacks(text: Component) {
        if (!onIsland() || System.currentTimeMillis() - lastChopMs > SACK_WINDOW_MS) return
        val hovers = LinkedHashSet<String>()
        fun walk(c: Component) {
            (c.style.hoverEvent as? HoverEvent.ShowText)?.let { hovers += TextStrip.plain(it.value().string) }
            c.siblings.forEach(::walk)
        }
        walk(text)
        for (m in SACK_LINE.findAll(hovers.joinToString("\n"))) {
            if (m.groupValues[1] != "+") continue
            val n = m.groupValues[2].replace(",", "").toLongOrNull() ?: continue
            val name = m.groupValues[3].trim()
            val id = ItemsDb.idFor(name) ?: ItemsDb.idFor(name.trimStart { !it.isLetterOrDigit() }) ?: continue
            if (!ProfitTrackerKit.inSacks(id, "Foraging")) continue
            names.putIfAbsent(id, name.trimStart { !it.isLetterOrDigit() })
            add(id, n)
        }
    }

    private fun add(id: String, n: Long) {
        val now = System.currentTimeMillis()
        if (startMs == 0L) startMs = now
        // idle gaps over 60s don't count towards session time
        else activeMs += minOf(now - lastGainMs, 60_000L)
        lastGainMs = now
        items[id] = (items[id] ?: 0L) + n
    }

    private fun isLog(id: String) = id == "LOG" || id.startsWith("LOG:") || id.startsWith("LOG_") || id.endsWith("_LOG") || id.contains("_LOG:")

    private fun price(id: String): Double = CroesusPrices.bazaarPrice(id, true) ?: ItemsDb.npcSellPriceFor(id)

    private fun rate(v: Double) = ProfitTrackerKit.perHour(v, activeMs)

    private fun time(ms: Long): String { val s = ms / 1000; return if (s >= 3600) "${s / 3600}h ${s % 3600 / 60}m" else "${s / 60}m ${s % 60}s" }

    private fun lines(): List<String> {
        val logs = items.entries.filter { isLog(it.key) }.sumOf { it.value }
        val drops = items.entries.filter { !isLog(it.key) }.sumOf { it.value }
        val coins = items.entries.sumOf { price(it.key) * it.value }
        val out = arrayListOf("§2§lForaging Profit")
        // price each item once per render, not per sort comparison
        items.entries.map { Triple(it.key, it.value, price(it.key) * it.value) }.sortedByDescending { it.third }
            .take(W.foragingTrackerRows).forEach { (id, n, v) ->
                val value = if (v > 0) "§6" + Mining.short(v) + " §7(" + Mining.short(rate(v)) + "/h)" else ""
                out += value + "\t§7| " + (ProfitTrackerKit.rarityColor(id) ?: "§f") + (names[id] ?: id) + " §f" + "%,d".format(n)
            }
        out += "§7Logs §f" + "%,d".format(logs) + (if (activeMs < 60_000) "" else " §7(§f" + Mining.short(rate(logs.toDouble())) + "§7/h)")
        out += "§7Drops §f" + "%,d".format(drops)
        out += "§7Playtime §f" + time(activeMs)
        out += "§eProfit §6" + Mining.short(coins) + " §7(" + Mining.short(rate(coins)) + "/h)"
        return out
    }
}
