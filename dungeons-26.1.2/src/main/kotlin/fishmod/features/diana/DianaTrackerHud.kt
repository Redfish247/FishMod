package fishmod.features.diana

import fishmod.features.FishHudEditor
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.resources.Identifier

// Loot / Mob / Stats / Magic Find HUDs for the Diana tracker
object DianaTrackerHud {

    private val LOOT_ORDER = listOf(
        "SHIMMERING_WOOL" to "§cShimmering Wool", "MANTI_CORE" to "§cManti-core", "KING_MINOS_SHARD" to "§cKing Minos Shard",
        "FATEFUL_STINGER" to "§dFateful Stinger", "CHIMERA" to "§dChimera", "BRAIN_FOOD" to "§5Brain Food",
        "MINOS_RELIC" to "§5Minos Relic", "SPHINX_SHARD" to "§5Sphinx Shard", "BRAIDED_GRIFFIN_FEATHER" to "§5Braided Griffin Feather",
        "DAEDALUS_STICK" to "§6Daedalus Stick", "MINOTAUR_SHARD" to "§6Minotaur Shard", "GRIFFIN_FEATHER" to "§6Griffin Feather",
        "MYTHOS_FRAGMENT" to "§6Mythos Fragment", "WASHED_UP_SOUVENIR" to "§6Washed-up Souvenir", "CRETAN_URN" to "§2Cretan Urn",
        "DWARF_TURTLE_SHELMET" to "§2Dwarf Turtle Shelmet", "CROCHET_TIGER_PLUSHIE" to "§2Crochet Tiger Plushie",
        "ANTIQUE_REMEDIES" to "§2Antique Remedies", "CRETAN_BULL_SHARD" to "§2Cretan Bull Shard", "HARPY_SHARD" to "§2Harpy Shard",
        "HILT_OF_REVELATIONS" to "§9Hilt of Revelations",
    )
    private val MOB_COLOR = mapOf(
        "KING_MINOS" to "§c", "MANTICORE" to "§c", "MINOS_INQUISITOR" to "§d", "SPHINX" to "§5",
        "MINOS_CHAMPION" to "§6", "MINOTAUR" to "§6", "MINOS_HUNTER" to "§9",
    )
    private val STATS = listOf(
        "MOBS_MINOS_INQUISITOR" to "Mobs since Inq", "MOBS_KING_MINOS" to "Mobs since King",
        "MOBS_MANTICORE" to "Mobs since Manti", "MOBS_SPHINX" to "Mobs since Sphinx",
        "CHIMERA" to "Inqs since Chimera", "CHIMERA_LS" to "LS Inqs since Chimera",
        "SHIMMERING_WOOL" to "Kings since Wool", "MANTI_CORE" to "Mantis since Core",
        "FATEFUL_STINGER" to "Mantis since Stinger", "BRAIN_FOOD" to "Sphinx since Food",
        "DAEDALUS_STICK" to "Minotaurs since Stick", "MINOS_RELIC" to "Champs since Relic",
    )

    private class Cache(var version: Int = -1, var at: Long = 0L, var lines: List<String> = emptyList())
    private val caches = HashMap<String, Cache>()

    fun init() {
        reg("Diana Loot Tracker", "diana_loot", 170, 250,
            { DianaSettings.dianaLootPosX }, { DianaSettings.dianaLootPosX = it },
            { DianaSettings.dianaLootPosY }, { DianaSettings.dianaLootPosY = it },
            { DianaSettings.dianaLootPosScale }, { DianaSettings.dianaLootPosScale = it },
            { DianaSettings.dianaLootTracker != "Off" }, ::lootLines)
        reg("Diana Mob Tracker", "diana_mobs", 160, 150,
            { DianaSettings.dianaMobPosX }, { DianaSettings.dianaMobPosX = it },
            { DianaSettings.dianaMobPosY }, { DianaSettings.dianaMobPosY = it },
            { DianaSettings.dianaMobPosScale }, { DianaSettings.dianaMobPosScale = it },
            { DianaSettings.dianaMobTracker != "Off" }, ::mobLines)
        reg("Diana Stats", "diana_stats", 140, 135,
            { DianaSettings.dianaStatsPosX }, { DianaSettings.dianaStatsPosX = it },
            { DianaSettings.dianaStatsPosY }, { DianaSettings.dianaStatsPosY = it },
            { DianaSettings.dianaStatsPosScale }, { DianaSettings.dianaStatsPosScale = it },
            { DianaSettings.dianaStatsTracker }, ::statLines)
        reg("Diana Magic Find", "diana_mf", 130, 80,
            { DianaSettings.dianaMfPosX }, { DianaSettings.dianaMfPosX = it },
            { DianaSettings.dianaMfPosY }, { DianaSettings.dianaMfPosY = it },
            { DianaSettings.dianaMfPosScale }, { DianaSettings.dianaMfPosScale = it },
            { DianaSettings.dianaMfTracker }, ::mfLines)
    }

    private fun reg(
        name: String, id: String, w: Int, h: Int,
        gx: () -> Int, sx: (Int) -> Unit, gy: () -> Int, sy: (Int) -> Unit,
        gs: () -> Double, ss: (Double) -> Unit, on: () -> Boolean, lines: () -> List<String>,
    ) {
        FishHudEditor.register(name, gx, sx, gy, sy, w, h, gs, ss, on)
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("fishmod", id)) { ctx, _ ->
            if (!FishHudEditor.isOpen() && on() && Diana.active()) draw(ctx, cached(id, lines), gx(), gy(), gs())
        }
    }

    private fun cached(id: String, make: () -> List<String>): List<String> {
        val c = caches.getOrPut(id) { Cache() }
        val now = System.currentTimeMillis()
        if (c.version != DianaTracker.version || now - c.at > 1000) {
            c.version = DianaTracker.version; c.at = now; c.lines = make()
        }
        return c.lines
    }

    private fun draw(ctx: GuiGraphicsExtractor, lines: List<String>, x: Int, y: Int, scale: Double) {
        val mc = Minecraft.getInstance()
        if (mc.player == null || mc.options.hideGui || lines.isEmpty()) return
        val pose = ctx.pose()
        pose.pushMatrix()
        pose.translate(x.toFloat(), y.toFloat())
        pose.scale(scale.toFloat(), scale.toFloat())
        lines.forEachIndexed { i, l -> ctx.text(mc.font, l, 0, i * 10, -1, true) }
        pose.popMatrix()
    }

    private fun title(label: String, mode: String, t: DianaTracker.Tracker) =
        "§e§l$label §7(" + (if (mode == "Event" && t.year > 0) "Year ${t.year}" else mode) + ")"

    private fun lootLines(): List<String> {
        val mode = DianaSettings.dianaLootTracker
        val t = DianaTracker.tracker(mode) ?: return emptyList()
        val hide = DianaSettings.dianaHideUnobtained
        val out = ArrayList<String>()
        out += title("Diana Loot", mode, t)
        for ((k, label) in LOOT_ORDER) {
            val n = t.item(k); val ls = t.item(k + "_LS")
            if (hide && n + ls == 0L) continue
            val value = DianaTracker.priceOf(k) * (n + ls)
            out += "$label §f$n" + (if (ls > 0) " §7+$ls LS" else "") + (if (value > 0) " §6${DianaTracker.short(value)}" else "")
        }
        val burrows = t.item("TOTAL_BURROWS").toDouble()
        val profit = DianaTracker.profit(t)
        out += "§6Coins §f${DianaTracker.short(t.item("COINS").toDouble())}"
        out += "§7Burrows §f${"%,d".format(burrows.toLong())} §8(${"%.0f".format(DianaTracker.perHour(burrows, t))}/h)"
        out += "§7Playtime §f${DianaTracker.fmtTime(t.timeMs)}"
        out += "§eProfit §6${DianaTracker.short(profit)} §8(${DianaTracker.short(DianaTracker.perHour(profit, t))}/h)"
        return out
    }

    private fun mobLines(): List<String> {
        val mode = DianaSettings.dianaMobTracker
        val t = DianaTracker.tracker(mode) ?: return emptyList()
        val tot = t.mob("TOTAL_MOBS")
        val out = ArrayList<String>()
        out += title("Diana Mobs", mode, t)
        for (name in DianaTracker.MOBS) {
            val k = DianaTracker.key(name)
            val n = t.mob(k); val ls = t.mob(k + "_LS")
            if (DianaSettings.dianaHideUnobtained && n + ls == 0L) continue
            val pct = if (tot > 0) n * 100.0 / tot else 0.0
            out += "${MOB_COLOR[k] ?: "§a"}$name §f$n §7(${"%.2f".format(pct)}%)" + (if (ls > 0) " §8+$ls LS" else "")
        }
        out += "§7Total §f${"%,d".format(tot)} §8(${"%.0f".format(DianaTracker.perHour(tot.toDouble(), t))}/h)"
        return out
    }

    private fun statLines(): List<String> =
        listOf("§e§lDiana Stats") + STATS.map { (k, l) -> "§7$l: §f${DianaTracker.sinceCount(k)}" }

    private fun mfLines(): List<String> {
        val out = arrayListOf("§e§lHighest Magic Find")
        for (d in DianaTracker.DROPS) {
            if (d.source == null) continue
            val mf = DianaTracker.highestMf(d.key)
            if (DianaSettings.dianaHideUnobtained && mf == 0) continue
            out += "${d.color}${d.name}§7: §b$mf% ✯"
        }
        return out
    }
}
